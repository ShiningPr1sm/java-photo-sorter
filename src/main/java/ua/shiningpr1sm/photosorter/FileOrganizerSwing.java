package ua.shiningpr1sm.photosorter;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.media.*;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.*;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;

public class FileOrganizerSwing {
    private File sourceFolder;
    private File destinationFolder;
    private JFrame mainFrame;
    private JPanel mainPanel;
    private boolean isCurrentPhotoCropped = false;
    private final Random rand = new Random();
    private final String deleteIndex = String.format("%010d", rand.nextInt(1_000_000_000));
    private final Deque<MoveAction> moveHistory = new ArrayDeque<>();
    private File rootFolder;
    private File currentFolder;
    private File[] filesToSort;
    private int currentIndex = 0;
    private final String CURRENT_VERSION = ConfigManager.getInternalVersion();

    private final CardLayout previewCardLayout = new CardLayout();
    private final JPanel previewPanel = new JPanel(previewCardLayout);
    private final JLabel imageLabel = new JLabel();
    private final JTextArea textPreview = new JTextArea();

    private final JFXPanel jfxPanel;
    private MediaPlayer mediaPlayer;
    private MediaView mediaView;
    private Process currentFfmpegProcess;

    private final JPanel videoControlsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0)) {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
        }
        { setDoubleBuffered(true); }
    };
    
    private final JButton playPauseButton = new JButton("Play");
    private final JSlider volumeSlider = new JSlider(0, 100, 50);

    private final JLabel statusLabel = new JLabel();
    private final JPanel folderButtonPanel = new JPanel(new WrapLayout());
    private final List<File> folders = new ArrayList<>();

    private final JLabel fileSizeLabel = new JLabel();
    private final JLabel fileExtensionLabel = new JLabel();

    private JCheckBox compatibilityModeCheckbox;
    private javax.swing.Timer compatibilityTimer;
    private final List<BufferedImage> compatibilityFrames = new ArrayList<>();
    private int compFrameIndex = 0;
    private final File TEMP_FRAME_DIR;
    private Clip compatibilityClip;

    private PDDocument pdfDocument;
    private int currentPdfPage;
    private int totalPdfPages;
    private final JPanel pdfControlsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
    private final JButton pdfPrevButton = new JButton("<");
    private final JButton pdfNextButton = new JButton(">");
    private final JLabel pdfPageLabel = new JLabel();

    private static final String COMPANY_NAME = "ShiningPr1sm";
    private static final String APPDATA = System.getenv("APPDATA");
    private static final File SHARED_ROOT = new File(APPDATA, COMPANY_NAME);

    private static final File FFMPEG_DIR = new File(SHARED_ROOT, "FFmpeg");
    private static final File FFMPEG_EXE = new File(FFMPEG_DIR, "ffmpeg.exe");
    private static final String FFMPEG_ZIP_URL = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";

    private record MoveAction(File movedFile, boolean wasDelete, boolean wasSkip, Path backupPath) {
    }

    public FileOrganizerSwing() {
        TEMP_FRAME_DIR = new File(SHARED_ROOT, "temp_frames");
        if (!TEMP_FRAME_DIR.exists())
            TEMP_FRAME_DIR.mkdirs();

        jfxPanel = new JFXPanel();
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Failed to set Look and Feel: " + e.getMessage());
        }

        UIManager.put("Button.minimumWidth", 120);
        UIManager.put("Button.minimumHeight", 40);
        UIManager.put("Button.margin", new Insets(10, 20, 10, 20));
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));
        UIManager.put("CheckBox.focus", new Color(0, 0, 0, 0));
        Dimension videoButtonSize = new Dimension(120, 40);
        playPauseButton.setPreferredSize(videoButtonSize);

        CompletableFuture.runAsync(() -> {
            try {
                checkAndDownloadFFMPEG();
            } catch (IOException e) {
                System.err.println("FFmpeg setup failed: " + e.getMessage());
            }
        });
        loadConfigAndInitialize();
    }

    private void loadConfigAndInitialize() {
        File configFile = getConfigFilePath().toFile();
        if (!configFile.exists()) {
            if (promptForInitialFolders())
                System.exit(0);
        } else {
            loadPathsFromConfig();
            if (sourceFolder == null || !sourceFolder.isDirectory() || destinationFolder == null || !destinationFolder.isDirectory()) {
                JOptionPane.showMessageDialog(null, "Source or destination folders missing.", "Error", JOptionPane.ERROR_MESSAGE);
                if (promptForInitialFolders())
                    System.exit(0);
            }
        }
        initializeApplication();
    }

    private void initializeApplication() {
        rootFolder = destinationFolder;
        currentFolder = destinationFolder;
        File[] allFiles = sourceFolder.listFiles((dir, name) ->
                name.toLowerCase().matches(".*\\.(jpg|png|jpeg|ico|txt|md|gif|pdf|doc|docx|mp4|m4v|m4a|mov|avi|mkv|mp3|webp)$"));
        if (allFiles != null) {
            filesToSort = allFiles;
            Arrays.sort(filesToSort);
        } else {
            filesToSort = new File[0];
        }

        mainFrame = new JFrame();
        mainFrame.setTitle(String.format("Media Downloader  |  v%s", CURRENT_VERSION));
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setResizable(true);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int frameHeight = 880;
        int frameWidth = 1050;
        mainFrame.setBounds(
                (int) ((screenSize.getWidth() / 2) - (frameWidth / 2.0)),
                (int) ((screenSize.getHeight() / 2) - (frameHeight / 2.0)),
                frameWidth,
                frameHeight
        );

        try {
            Image icon = ImageIO.read(Objects.requireNonNull(FileOrganizerSwing.class.getResource("/project_icon.png")));
            mainFrame.setIconImage(icon);
        } catch (Exception ignored) {

        }
        setupUIComponents();
        setupKeyBindings();
        updatePreview();
        loadFolders(destinationFolder);
        mainFrame.setVisible(true);
    }

    private void setupUIComponents() {
        JButton selectSourceButton = new JButton("Select Source");
        JButton selectDestButton = new JButton("Select Destination");
        selectSourceButton.addActionListener(e -> changeFolder(true));
        selectDestButton.addActionListener(e -> changeFolder(false));

        JButton undoButton = new JButton("Undo (X)");
        JButton moveButton = new JButton("Move (C)");
        JButton createFolderButton = new JButton("Create Folder");
        JButton backButton = new JButton("Back (Z)");
        JButton deleteButton = new JButton("Del");
        JButton skipButton = new JButton("Skip (V)");
        JButton cropButton = new JButton("Crop");
        JButton undoCropButton = new JButton("Undo Crop");

        backButton.addActionListener(e -> goBack());
        undoButton.addActionListener(e -> undoMove());
        moveButton.addActionListener(e -> moveToSelectedFolder());
        createFolderButton.addActionListener(e -> createNewFolder());
        deleteButton.addActionListener(e -> deletePhoto());
        skipButton.addActionListener(e -> skipPhoto());
        cropButton.addActionListener(e -> cropPhoto());
        undoCropButton.addActionListener(e -> undoCrop());

        compatibilityModeCheckbox = new JCheckBox("Compatibility Mode (FFmpeg)");
        compatibilityModeCheckbox.setToolTipText("Use this if videos are not playing correctly.");
        compatibilityModeCheckbox.addActionListener(e -> updatePreview());

        compatibilityModeCheckbox.setFocusPainted(false);
        compatibilityModeCheckbox.setFocusable(false);

        JPanel controlPanel = new JPanel(new WrapLayout());
        controlPanel.add(selectSourceButton);
        controlPanel.add(selectDestButton);
        controlPanel.add(backButton);
        controlPanel.add(undoButton);
        controlPanel.add(moveButton);
        controlPanel.add(createFolderButton);
        controlPanel.add(skipButton);
        controlPanel.add(deleteButton);
        controlPanel.add(cropButton);
        controlPanel.add(undoCropButton);

        folderButtonPanel.setLayout(new GridLayout(0, calculateColumns(), 5, 5));

        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 14f));
        statusLabel.setForeground(new Color(220, 50, 50));

        setupPreviewPanel();

        JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 2));
        summaryPanel.add(fileSizeLabel);
        summaryPanel.add(fileExtensionLabel);

        JPanel topInfoPanel = new JPanel(new BorderLayout());
        topInfoPanel.add(statusLabel, BorderLayout.NORTH);
        topInfoPanel.add(summaryPanel, BorderLayout.CENTER);
        topInfoPanel.add(compatibilityModeCheckbox, BorderLayout.EAST);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(topInfoPanel, BorderLayout.NORTH);
        centerPanel.add(previewPanel, BorderLayout.CENTER);
        JPanel controlsWrapper = new JPanel(new CardLayout());
        controlsWrapper.add(videoControlsPanel, "VIDEO");
        controlsWrapper.add(pdfControlsPanel, "PDF");
        centerPanel.add(controlsWrapper, BorderLayout.SOUTH);

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(folderButtonPanel, BorderLayout.SOUTH);
        mainFrame.add(mainPanel);
    }

    private void setupPreviewPanel() {
        JScrollPane imageScrollPane = new JScrollPane(imageLabel);
        imageScrollPane.getVerticalScrollBar().setUnitIncrement(48);
        imageScrollPane.setBorder(null);
        previewPanel.add(imageScrollPane, "IMAGE");
        textPreview.setEditable(false);
        textPreview.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane textScrollPane = new JScrollPane(textPreview);
        textScrollPane.setBorder(null);
        previewPanel.add(textScrollPane, "TEXT");
        previewPanel.add(jfxPanel, "VIDEO");
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);

        jfxPanel.setOpaque(false);

        Platform.runLater(() -> {
            mediaView = new MediaView();
            javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(mediaView);
            root.setStyle("-fx-background-color: transparent;");
            javafx.scene.paint.Color transparentColor = javafx.scene.paint.Color.TRANSPARENT;
            Scene scene = new Scene(root, transparentColor);
            jfxPanel.setScene(scene);

            mediaView.fitWidthProperty().bind(jfxPanel.getScene().widthProperty());
            mediaView.fitHeightProperty().bind(jfxPanel.getScene().heightProperty());
            mediaView.setPreserveRatio(true);
        });

        styleVideoButton(playPauseButton);

        volumeSlider.addChangeListener(e -> {
            int value = volumeSlider.getValue();
            double volume = value / 100.0;
            if (mediaPlayer != null) {
                Platform.runLater(() -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.setVolume(volume);
                    }
                });
            }
            if (compatibilityClip != null) {
                setClipVolume(compatibilityClip, volume);
            }
            volumeSlider.getParent().repaint();
        });

        playPauseButton.addActionListener(e -> {
            if (compatibilityModeCheckbox.isSelected()) {
                if (compatibilityTimer != null) {
                    if (compatibilityTimer.isRunning()) {
                        compatibilityTimer.stop();
                        if (compatibilityClip != null) compatibilityClip.stop();
                    } else {
                        compatibilityTimer.start();
                        if (compatibilityClip != null) compatibilityClip.start();
                    }
                    playPauseButton.setText(compatibilityTimer.isRunning() ? "Pause" : "Play");
                }
            } else if (mediaPlayer != null) {
                Platform.runLater(() -> {
                    if (mediaPlayer.getRate() > 0 && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                        mediaPlayer.pause();
                    } else {
                        if (mediaPlayer.getCurrentTime().greaterThanOrEqualTo(mediaPlayer.getTotalDuration())) {
                            mediaPlayer.seek(javafx.util.Duration.ZERO);
                        }
                        mediaPlayer.play();
                    }
                });
            }
        });

        videoControlsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 0));
        videoControlsPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        volumeSlider.setPreferredSize(new Dimension(150, 30));
        volumeSlider.setBackground(videoControlsPanel.getBackground());
        volumeSlider.setFocusable(false);
        volumeSlider.setFocusable(false);
        volumeSlider.putClientProperty("JSlider.isFilled", Boolean.FALSE);
        UIManager.put("Slider.paintValue", Boolean.FALSE);
        volumeSlider.setPaintTicks(false);
        volumeSlider.setPaintLabels(false);
        volumeSlider.setPaintTrack(true);
        volumeSlider.setUI(new CustomSliderUI(volumeSlider));

        volumeSlider.setOpaque(true);
        volumeSlider.setDoubleBuffered(true);

        JLabel volLabel = new JLabel("Vol:");
        volLabel.setFont(new Font("Arial", Font.BOLD, 13));
        volLabel.setForeground(new Color(60, 60, 60));

        videoControlsPanel.add(playPauseButton);
        videoControlsPanel.add(volLabel);
        videoControlsPanel.add(volumeSlider);

        videoControlsPanel.setVisible(false);

        pdfControlsPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        stylePdfButton(pdfPrevButton);
        stylePdfButton(pdfNextButton);

        pdfPageLabel.setFont(new Font("Arial", Font.BOLD, 13));
        pdfPageLabel.setForeground(new Color(60, 60, 60));
        pdfPageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        pdfPrevButton.addActionListener(e -> {
            if (pdfDocument != null && currentPdfPage > 0) {
                currentPdfPage--;
                renderPdfPage();
            }
        });

        pdfNextButton.addActionListener(e -> {
            if (pdfDocument != null && currentPdfPage < totalPdfPages - 1) {
                currentPdfPage++;
                renderPdfPage();
            }
        });

        pdfControlsPanel.add(pdfPrevButton);
        pdfControlsPanel.add(pdfPageLabel);
        pdfControlsPanel.add(pdfNextButton);

        pdfControlsPanel.setVisible(false);
    }

    private void stylePdfButton(JButton button) {
        button.setPreferredSize(new Dimension(40, 35));
        button.setBackground(new Color(190, 185, 185));
        button.setForeground(Color.BLACK);
        button.setFont(new Font("Arial", Font.BOLD, 18));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(new Color(190, 170, 170));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(new Color(190, 185, 185));
            }
        });
    }

    private void styleVideoButton(JButton button) {
        button.setPreferredSize(new Dimension(100, 35));
        button.setBackground(new Color(190, 185, 185));
        button.setForeground(Color.BLACK);
        button.setFont(new Font("Arial", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(new Color(190, 170, 170));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(new Color(190, 185, 185));
            }
        });
    }

    private static class CustomSliderUI extends javax.swing.plaf.basic.BasicSliderUI {
        public CustomSliderUI(JSlider b) {
            super(b);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            paintTrack(g2d);
            paintThumb(g2d);
        }

        @Override
        protected void calculateTrackRect() {
            super.calculateTrackRect();
            int thumbHalf = thumbRect.width / 2;
            trackRect.x += thumbHalf;
            trackRect.width -= thumbHalf * 2;
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int trackHeight = 4;
            int trackY = trackRect.y + (trackRect.height - trackHeight) / 2;
            int thumbPos = thumbRect.x + thumbRect.width / 2;

            g2d.setColor(new Color(210, 210, 210));
            g2d.fillRoundRect(trackRect.x, trackY, trackRect.width, trackHeight, trackHeight, trackHeight);

            int fillWidth = thumbPos - trackRect.x;
            if (fillWidth > 0) {
                g2d.setColor(new Color(160, 155, 160));
                g2d.fillRoundRect(trackRect.x, trackY, fillWidth, trackHeight, trackHeight, trackHeight);
            }

            g2d.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = 14;
            int x = thumbRect.x + (thumbRect.width - size) / 2;
            int y = thumbRect.y + (thumbRect.height - size) / 2;

            g2d.setColor(new Color(0, 0, 0, 30));
            g2d.fillOval(x + 1, y + 1, size, size);

            g2d.setColor(new Color(190, 185, 185));
            g2d.fillOval(x, y, size, size);
            
            g2d.setColor(new Color(160, 155, 155));
            g2d.drawOval(x, y, size, size);

            g2d.dispose();
        }

        @Override
        public void paintFocus(Graphics g) {}
    }

    private void updatePreview() {
        isCurrentPhotoCropped = false;
        videoControlsPanel.setVisible(false);
        pdfControlsPanel.setVisible(false);
        stopPlayback();

        if (compatibilityModeCheckbox.isSelected() && !FFMPEG_EXE.exists()) {
            statusLabel.setText("FFmpeg is installing, please wait...");
        } else {
            statusLabel.setText(isCurrentPhotoCropped ? "[CROPPED]" : " ");
        }

        if (filesToSort.length == 0 || currentIndex >= filesToSort.length) {
            imageLabel.setIcon(null);
            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageLabel.setVerticalAlignment(SwingConstants.CENTER);
            imageLabel.setText("<html><div style='text-align:center; padding:80px 20px;'>" +
                    "<div style='font-size:64px; color:#4CAF50;'>\u2713</div>" +
                    "<div style='font-size:28px; color:#555; margin:15px 0; font-weight:bold;'>All files sorted!</div>" +
                    "<div style='font-size:14px; color:#999;'>Use Undo (X) to go back or select a new folder.</div>" +
                    "</div></html>");
            previewCardLayout.show(previewPanel, "IMAGE");
            updateFrameTitle();
            return;
        }

        File file = filesToSort[currentIndex];
        fileSizeLabel.setText("Size: " + formatFileSize(file.length()));
        fileExtensionLabel.setText("Type: " + getFileExtension(file).toUpperCase());

        String extension = getFileExtension(file);
        if (extension.matches("jpg|jpeg|png|webp|ico|gif")) {
            showImagePreview(file);
        } else if (extension.matches("txt|md|doc|docx")) {
            showTextPreview(file);
        } else if (extension.matches("mp4|m4v|m4a|mov|avi|mkv|mp3")) {
            if (compatibilityModeCheckbox.isSelected()) {
                showCompatibilityVideoPreview(file);
            } else {
                showVideoPreview(file);
            }
        } else if (extension.equals("pdf")) {
            showPdfPreview(file);
        } else {
            showUnsupportedPreview(file);
        }
        updateFrameTitle();
    }

    private void showVideoPreview(File file) {
        videoControlsPanel.setVisible(true);
        previewCardLayout.show(previewPanel, "VIDEO");

        Platform.runLater(() -> {
            try {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.dispose();
                }

                Media media = new Media(file.toURI().toString());
                mediaPlayer = new MediaPlayer(media);

                double currentVolume = volumeSlider.getValue() / 100.0;
                mediaPlayer.setVolume(currentVolume);

                mediaPlayer.setOnEndOfMedia(() -> {
                    mediaPlayer.seek(javafx.util.Duration.ZERO);
                    mediaPlayer.play();
                });

                mediaPlayer.statusProperty().addListener((obs, oldS, newS) ->
                        SwingUtilities.invokeLater(() ->
                                playPauseButton.setText(newS == MediaPlayer.Status.PLAYING ? "Pause" : "Play")
                        )
                );

                mediaView.setMediaPlayer(mediaPlayer);
                mediaPlayer.setAutoPlay(true);
                mediaPlayer.play();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> showUnsupportedPreview(file));
            }
        });
    }

    private void showCompatibilityVideoPreview(File file) {
        videoControlsPanel.setVisible(true);
        previewCardLayout.show(previewPanel, "IMAGE");

        if (!FFMPEG_EXE.exists()) {
            imageLabel.setText("FFmpeg is missing...");
            return;
        }

        imageLabel.setIcon(null);
        imageLabel.setText("<html><center><font size='5'>Processing video (FFmpeg)...</font></center></html>");
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        imageLabel.setText("Processing video (FFmpeg)...");
        playPauseButton.setEnabled(false);

        CompletableFuture.runAsync(() -> {
            try {
                clearTempFrames();
                compatibilityFrames.clear();
                File audioFile = new File(TEMP_FRAME_DIR, "audio.wav");

                int maxWidth = Math.max(400, previewPanel.getWidth() - 20);
                int maxHeight = Math.max(300, previewPanel.getHeight() - 20);

                ProcessBuilder pb = new ProcessBuilder(
                        FFMPEG_EXE.getAbsolutePath(),
                        "-i", file.getAbsolutePath(),
                        "-y",
                        "-vf", "fps=30,scale=w=" + maxWidth + ":h=" + maxHeight + ":force_original_aspect_ratio=decrease",
                        new File(TEMP_FRAME_DIR, "f_%04d.jpg").getAbsolutePath(),
                        "-vn", "-acodec", "pcm_s16le", "-ar", "44100", "-ac", "2",
                        audioFile.getAbsolutePath()
                );

                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

                currentFfmpegProcess = pb.start();

                boolean finished = currentFfmpegProcess.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

                if (!finished) {
                    currentFfmpegProcess.destroyForcibly();
                    throw new Exception("FFmpeg timeout");
                }

                File[] frames = TEMP_FRAME_DIR.listFiles((dir, name) -> name.endsWith(".jpg"));
                if (frames != null && frames.length > 0) {
                    Arrays.sort(frames);
                    int limit = Math.min(frames.length, 2000);
                    for (int i = 0; i < limit; i++) {
                        compatibilityFrames.add(ImageIO.read(frames[i]));
                    }
                }

                if (audioFile.exists()) {
                    AudioInputStream ais = AudioSystem.getAudioInputStream(audioFile);
                    compatibilityClip = AudioSystem.getClip();
                    compatibilityClip.open(ais);
                    double initialVolume = volumeSlider.getValue() / 100.0;
                    setClipVolume(compatibilityClip, initialVolume);
                }

                SwingUtilities.invokeLater(() -> {
                    if (currentIndex < filesToSort.length && filesToSort[currentIndex].equals(file)) {
                        if (compatibilityFrames.isEmpty()) {
                            imageLabel.setText("Could not extract frames.");
                        } else {
                            imageLabel.setText(null);
                            playPauseButton.setEnabled(true);
                            startCompatibilitySlideshow();
                        }
                    }
                });

            } catch (Exception e) {
                System.err.println("FFmpeg Task Error: " + e.getMessage());
                SwingUtilities.invokeLater(() -> imageLabel.setText("Error: " + e.getMessage()));
            } finally {
                currentFfmpegProcess = null;
            }
        });
    }

    private void startCompatibilitySlideshow() {
        compFrameIndex = 0;

        if (compatibilityTimer != null) compatibilityTimer.stop();

        compatibilityTimer = new javax.swing.Timer(33, e -> {
            if (compatibilityFrames.isEmpty()) return;

            if (compFrameIndex >= compatibilityFrames.size()) {
                compFrameIndex = 0;
            }

            imageLabel.setIcon(new ImageIcon(compatibilityFrames.get(compFrameIndex)));
            compFrameIndex++;
        });

        if (compatibilityClip != null) {
            compatibilityClip.setFramePosition(0);
            compatibilityClip.loop(Clip.LOOP_CONTINUOUSLY);
        }

        compatibilityTimer.start();
        playPauseButton.setText("Pause");
    }

    private void showPdfPreview(File file) {
        currentPdfPage = 0;
        totalPdfPages = 0;
        try {
            pdfDocument = org.apache.pdfbox.Loader.loadPDF(file);
            totalPdfPages = pdfDocument.getNumberOfPages();
            renderPdfPage();
            imageLabel.setHorizontalAlignment(SwingConstants.LEFT);
            imageLabel.setVerticalAlignment(SwingConstants.TOP);
            pdfControlsPanel.setVisible(true);
            previewCardLayout.show(previewPanel, "IMAGE");
        } catch (IOException e) {
            showUnsupportedPreview(file);
        }
    }

    private void renderPdfPage() {
        if (pdfDocument == null) return;
        try {
            org.apache.pdfbox.pdmodel.PDPage page = pdfDocument.getPage(currentPdfPage);
            org.apache.pdfbox.pdmodel.common.PDRectangle mediaBox = page.getMediaBox();
            float pageWidthPt = mediaBox.getWidth();
            int viewWidth = Math.max(400, previewPanel.getWidth() - 50);
            float dpi = viewWidth * 72f / pageWidthPt;
            dpi = Math.max(72, Math.min(dpi, 200));
            PDFRenderer renderer = new PDFRenderer(pdfDocument);
            BufferedImage pageImage = renderer.renderImageWithDPI(currentPdfPage, dpi);
            imageLabel.setIcon(new ImageIcon(pageImage));
            imageLabel.setText(null);
            pdfPageLabel.setText((currentPdfPage + 1) + " / " + totalPdfPages);
            pdfPrevButton.setEnabled(currentPdfPage > 0);
            pdfNextButton.setEnabled(currentPdfPage < totalPdfPages - 1);
        } catch (IOException e) {
            imageLabel.setText("Error rendering PDF page.");
        }
    }

    private void stopPlayback() {
        if (currentFfmpegProcess != null && currentFfmpegProcess.isAlive()) {
            currentFfmpegProcess.destroyForcibly();
            currentFfmpegProcess = null;
        }

        if (mediaPlayer != null) {
            Platform.runLater(() -> {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.dispose();
                    mediaPlayer = null;
                }
            });
        }

        if (compatibilityTimer != null) {
            compatibilityTimer.stop();
            compatibilityTimer = null;
        }

        if (compatibilityClip != null) {
            compatibilityClip.stop();
            compatibilityClip.close();
            compatibilityClip = null;
        }

        compatibilityFrames.clear();
        clearTempFrames();

        if (pdfDocument != null) {
            try {
                pdfDocument.close();
            } catch (IOException ignored) {
            }
            pdfDocument = null;
        }

        imageLabel.setIcon(null);
        imageLabel.setText("");

        playPauseButton.setText("Play");
    }

    private void clearTempFrames() {
        File[] files = TEMP_FRAME_DIR.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
    }

    private void setClipVolume(Clip clip, double volume) {
        if (clip != null && clip.isOpen()) {
            try {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float gain;
                if (volume <= 0.0) {
                    gain = gainControl.getMinimum();
                } else {
                    gain = (float) (Math.log10(volume) * 20.0);
                    gain = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), gain));
                }
                gainControl.setValue(gain);
            } catch (IllegalArgumentException e) {
                // Some audio formats may not support MASTER_GAIN
            }
        }
    }

    private void scheduleDeleteRetry(File file) {
        if (file.delete()) return;

        javax.swing.Timer retryTimer = new javax.swing.Timer(1000, null);
        retryTimer.addActionListener(e -> {
            if (file.delete() || !file.exists()) {
                retryTimer.stop();
            }
        });
        retryTimer.setRepeats(true);
        retryTimer.start();
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.##").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private void showImagePreview(File file) {
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);
        try {
            if (getFileExtension(file).equals("gif")) {
                ImageIcon gifIcon = new ImageIcon(file.getAbsolutePath());
                int maxWidth = Math.max(400, previewPanel.getWidth() - 50);
                int maxHeight = Math.max(300, previewPanel.getHeight() - 250);
                double ratio = Math.min((double) maxWidth / gifIcon.getIconWidth(),
                        (double) maxHeight / gifIcon.getIconHeight());
                int newWidth = (int) (gifIcon.getIconWidth() * ratio);
                int newHeight = (int) (gifIcon.getIconHeight() * ratio);
                Image scaled = gifIcon.getImage().getScaledInstance(newWidth, newHeight, Image.SCALE_DEFAULT);
                imageLabel.setText(null);
                imageLabel.setIcon(new ImageIcon(scaled));
                previewCardLayout.show(previewPanel, "IMAGE");
                return;
            }

            BufferedImage originalImage;

            if (getFileExtension(file).equals("webp") || getFileExtension(file).equals("ico")) {
                originalImage = readImageViaFfmpeg(file);
            } else {
                originalImage = ImageIO.read(file);
            }

            if (originalImage == null) {
                imageLabel.setText("Could not read image.");
                return;
            }

            int maxWidth = Math.max(400, previewPanel.getWidth() - 50);
            int maxHeight = Math.max(300, previewPanel.getHeight() - 250);
            double ratio = Math.min((double) maxWidth / originalImage.getWidth(), (double) maxHeight / originalImage.getHeight());
            int newWidth = (int) (originalImage.getWidth() * ratio);
            int newHeight = (int) (originalImage.getHeight() * ratio);

            Image scaledImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
            imageLabel.setText(null);
            imageLabel.setIcon(new ImageIcon(scaledImage));
            previewCardLayout.show(previewPanel, "IMAGE");
        } catch (Exception e) {
            System.err.println("Error loading image: " + e.getMessage());
        }
    }

    private BufferedImage readImageViaFfmpeg(File inputFile) {
        File tempPng = new File(TEMP_FRAME_DIR, "ffmpeg_conv_" + System.currentTimeMillis() + ".png");

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    FFMPEG_EXE.getAbsolutePath(),
                    "-i", inputFile.getAbsolutePath(),
                    "-y",
                    tempPng.getAbsolutePath()
            );

            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();

            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

            if (finished && tempPng.exists()) {
                BufferedImage img = ImageIO.read(tempPng);
                return img;
            }
        } catch (Exception e) {
            System.err.println("FFmpeg conversion failed for " + inputFile.getName() + ": " + e.getMessage());
        } finally {
            if (tempPng.exists()) {
                tempPng.delete();
            }
        }
        return null;
    }

    private void showTextPreview(File file) {
        try {
            String ext = getFileExtension(file);
            String text;
            if (ext.equals("docx")) {
                try (FileInputStream fis = new FileInputStream(file);
                     XWPFWordExtractor extractor = new XWPFWordExtractor(new XWPFDocument(fis))) {
                    text = extractor.getText();
                }
            } else if (ext.equals("doc")) {
                try (FileInputStream fis = new FileInputStream(file);
                     WordExtractor extractor = new WordExtractor(fis)) {
                    text = extractor.getText();
                }
            } else {
                text = Files.readString(file.toPath());
            }
            textPreview.setText(text);
            textPreview.setCaretPosition(0);
            previewCardLayout.show(previewPanel, "TEXT");
        } catch (Exception e) {
            nextFile();
        }
    }

    private void showUnsupportedPreview(File file) {
        imageLabel.setIcon(null);
        imageLabel.setText("Preview not available: " + file.getName());
        previewCardLayout.show(previewPanel, "IMAGE");
    }

    private void updateFrameTitle() {
        if (mainFrame == null)
            return;
        boolean done = currentIndex >= filesToSort.length;
        String fileName = done ? "Complete" : filesToSort[currentIndex].getName();
        int filesLeft = Math.max(0, filesToSort.length - currentIndex);
        mainFrame.setTitle("File Organizer  |  v" + CURRENT_VERSION + "  |  " + (done ? "Done" : "File left: " + filesLeft) + "  |  " + fileName);
        statusLabel.setText(isCurrentPhotoCropped ? "[CROPPED]" : " ");
    }

    private void setupKeyBindings() {
        InputMap im = mainPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = mainPanel.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0), "Z");
        am.put("Z", new AbstractAction() { public void actionPerformed(ActionEvent e) { goBack(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, 0), "X");
        am.put("X", new AbstractAction() { public void actionPerformed(ActionEvent e) { undoMove(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0), "C");
        am.put("C", new AbstractAction() { public void actionPerformed(ActionEvent e) { moveToSelectedFolder(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, 0), "V");
        am.put("V", new AbstractAction() { public void actionPerformed(ActionEvent e) { skipPhoto(); } });
    }

    private void recordNewActionAndNext(File targetFile, boolean isDelete, boolean isSkip, Path backupPath) {
        if (!moveHistory.isEmpty()) {
            MoveAction previousAction = moveHistory.peek();
            if (previousAction.backupPath != null && Files.exists(previousAction.backupPath)) {
                try { Files.delete(previousAction.backupPath); } catch (IOException ignored) {}
            }
        }
        moveHistory.push(new MoveAction(targetFile, isDelete, isSkip, backupPath));
        nextFile();
    }

    private void nextFile() {
        currentIndex++;
        updatePreview();
    }

    private void moveToSelectedFolder() {
        moveToFolder(currentFolder);
        currentFolder = rootFolder;
        loadFolders(rootFolder);
        updateFrameTitle();
    }

    private void moveToFolder(File destination) {
        if (currentIndex >= filesToSort.length)
            return;
        File sourceFile = filesToSort[currentIndex];
        File targetFile = new File(destination, sourceFile.getName());
        Path backupPath = null;
        File bkp = new File(sourceFile.getAbsolutePath() + ".bak");
        if (bkp.exists())
            backupPath = bkp.toPath();
        stopPlayback();
        try {
            Files.move(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                scheduleDeleteRetry(sourceFile);
            } catch (IOException e2) {
                JOptionPane.showMessageDialog(mainFrame, "Move failed.");
                return;
            }
        }
        recordNewActionAndNext(targetFile, false, false, backupPath);
    }

    private void deletePhoto() {
        if (currentIndex >= filesToSort.length)
            return;
        File file = filesToSort[currentIndex];
        stopPlayback();
        File binFile = moveToBin(file);
        if (binFile != null)
            recordNewActionAndNext(binFile, true, false, null);
    }

    private void skipPhoto() {
        if (currentIndex < filesToSort.length) {
            recordNewActionAndNext(filesToSort[currentIndex], false, true, null);
        }
    }

    private void undoMove() {
        if (moveHistory.isEmpty())
            return;
        stopPlayback();
        MoveAction action = moveHistory.pop();
        if (action.wasSkip) {
            currentIndex = Math.max(0, currentIndex - 1);
        } else {
            File sourceFile = new File(sourceFolder, action.movedFile.getName());
            try {
                Files.move(action.movedFile.toPath(), sourceFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                try {
                    Files.copy(action.movedFile.toPath(), sourceFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    scheduleDeleteRetry(action.movedFile);
                } catch (IOException e2) {
                    JOptionPane.showMessageDialog(mainFrame, "Undo failed.");
                    return;
                }
            }
            currentIndex = Math.max(0, currentIndex - 1);
        }
        updatePreview();
    }

    private void loadFolders(File parentFolder) {
        folders.clear();
        File[] folderArray = parentFolder.listFiles(File::isDirectory);
        if (folderArray != null) {
            Arrays.sort(folderArray);
            for (File f : folderArray) {
                if (!f.getName().startsWith("Delete_folder_") && !f.getName().equals("Del")) folders.add(f);
            }
        }
        loadFolderButtons();
    }

    private void loadFolderButtons() {
        folderButtonPanel.removeAll();
        for (File folder : folders) {
            JButton b = new JButton(folder.getName());
            b.addActionListener(e -> selectFolder(folder));
            folderButtonPanel.add(b);
        }
        if (!currentFolder.equals(rootFolder)) {
            JButton up = new JButton("..");
            up.addActionListener(e -> goBack());
            folderButtonPanel.add(up, 0);
        }
        folderButtonPanel.revalidate();
        folderButtonPanel.repaint();
    }

    private void selectFolder(File folder) {
        currentFolder = folder;
        if (currentFolderHasFolders(currentFolder)) {
            loadFolders(currentFolder);
        } else {
            moveToFolder(currentFolder);
            currentFolder = rootFolder;
            loadFolders(currentFolder);
        }
        updateFrameTitle();
    }

    private void goBack() {
        if (currentFolder.equals(rootFolder))
            return;
        File parent = currentFolder.getParentFile();
        if (parent != null) {
            currentFolder = parent;
            loadFolders(currentFolder);
        }
        updateFrameTitle();
    }

    private boolean currentFolderHasFolders(File f) {
        File[] list = f.listFiles(File::isDirectory);
        if (list == null)
            return false;
        for (File sub : list) {
            if (!sub.getName().equals("Del") && !sub.getName().startsWith("Delete_folder_"))
                return true;
        }
        return false;
    }

    private File moveToBin(File file) {
        try {
            File mainBinDir = new File(destinationFolder, "Del");
            if (!mainBinDir.exists()) mainBinDir.mkdir();
            File uniqueDeleteFolder = new File(mainBinDir, "Delete_folder_" + deleteIndex);
            if (!uniqueDeleteFolder.exists()) uniqueDeleteFolder.mkdir();
            File targetFile = new File(uniqueDeleteFolder, file.getName());
            try {
                Files.move(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                scheduleDeleteRetry(file);
            }
            return targetFile;
        } catch (IOException e) {
            return null;
        }
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return (lastDot > 0) ? name.substring(lastDot + 1).toLowerCase() : "";
    }

    private void cropPhoto() {
        if (currentIndex >= filesToSort.length)
            return;
        File file = filesToSort[currentIndex];
        if (!getFileExtension(file).matches("jpg|jpeg|png"))
            return;
        try {
            BufferedImage img = ImageIO.read(file);
            BufferedImage cropped = ImageCropDialog.showCropDialog(mainFrame, img);
            if (cropped != null) {
                Path bkp = Paths.get(file.getAbsolutePath() + ".bak");
                if (!Files.exists(bkp))
                    Files.copy(file.toPath(), bkp);
                ImageIO.write(cropped, getFileExtension(file), file);
                updatePreview();
            }
        } catch (Exception ignored) {

        }
    }

    private void undoCrop() {
        if (currentIndex >= filesToSort.length)
            return;
        File file = filesToSort[currentIndex];
        File bkp = new File(file.getAbsolutePath() + ".bak");
        if (bkp.exists()) {
            try {
                Files.copy(bkp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                bkp.delete();
                updatePreview();
            } catch (Exception ignored) {

            }
        }
    }

    private int calculateColumns() {
        int width = mainFrame.getWidth();
        return Math.max(1, width / 130);
    }

    private void changeFolder(boolean isSource) {
        File newFolder = chooseDirectory(isSource ? "Source" : "Dest");
        if (newFolder != null) {
            if (isSource)
                sourceFolder = newFolder;
            else
                destinationFolder = newFolder;
            savePathsToConfig(sourceFolder, destinationFolder);
            mainFrame.dispose();
            new FileOrganizerSwing();
        }
    }

    private Path getConfigFilePath() {
        Path configDir = Paths.get(APPDATA, "ShiningPr1sm/FileOrganizer");
        try {
            if (!Files.exists(configDir))
                Files.createDirectories(configDir);
        } catch (IOException ignored) {

        }
        return configDir.resolve("folders.txt");
    }

    private void savePathsToConfig(File source, File dest) {
        try (PrintWriter out = new PrintWriter(new FileWriter(getConfigFilePath().toFile()))) {
            out.println("FROM: " + source.getAbsolutePath());
            out.println("TO: " + dest.getAbsolutePath());
        } catch (IOException ignored) {

        }
    }

    private void loadPathsFromConfig() {
        try (BufferedReader br = new BufferedReader(new FileReader(getConfigFilePath().toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("FROM:"))
                    sourceFolder = new File(line.substring(5).trim());
                if (line.startsWith("TO:"))
                    destinationFolder = new File(line.substring(3).trim());
            }
        } catch (IOException ignored) {

        }
    }

    private File chooseDirectory(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(mainFrame) == JFileChooser.APPROVE_OPTION)
            return chooser.getSelectedFile();
        return null;
    }

    private void createNewFolder() {
        String folderName = JOptionPane.showInputDialog(mainFrame, "Enter new folder name: ");
        if (Objects.nonNull(folderName) && !folderName.trim().isEmpty()) {
            File newFolder = new File(currentFolder, folderName.trim());
            if (newFolder.mkdir()) {
                JOptionPane.showMessageDialog(mainFrame, "Folder '" + folderName + "' created.", "Success", JOptionPane.INFORMATION_MESSAGE);
                loadFolders(currentFolder);
            } else {
                JOptionPane.showMessageDialog(mainFrame, "Failed to create folder or folder already exists.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private boolean promptForInitialFolders() {
        sourceFolder = chooseDirectory("Select Source Folder");
        if (sourceFolder == null)
            return true;
        destinationFolder = chooseDirectory("Select Destination Folder");
        if (destinationFolder == null)
            return true;
        savePathsToConfig(sourceFolder, destinationFolder);
        return false;
    }

    private static void checkAndDownloadFFMPEG() throws IOException {
        System.out.println("Checking FFmpeg existence and version...");
        if (!FFMPEG_DIR.exists()) {
            FFMPEG_DIR.mkdirs();
            System.out.println("Created FFmpeg directory: " + FFMPEG_DIR.getAbsolutePath());
        }
        cleanupOldFfmpegExtracts();

        if (FFMPEG_EXE.exists()) {
            System.out.println("ffmpeg.exe already exists at: " + FFMPEG_EXE.getAbsolutePath());
            return;
        }

        System.out.println("FFmpeg not found, downloading zip from: " + FFMPEG_ZIP_URL);

        File zipFile = new File(FFMPEG_DIR, "ffmpeg.zip");
        try (InputStream in = new URL(FFMPEG_ZIP_URL).openStream();
             FileOutputStream out = new FileOutputStream(zipFile)) {
            in.transferTo(out);
            System.out.println("FFmpeg zip downloaded to: " + zipFile.getAbsolutePath());
        }

        System.out.println("Extracting FFmpeg from zip...");
        extractFfmpegFromZip(zipFile, FFMPEG_EXE);
        zipFile.delete();
        System.out.println("FFmpeg zip deleted.");

        if (!FFMPEG_EXE.exists()) {
            throw new IOException("ffmpeg.exe not found inside archive.");
        }
        FFMPEG_EXE.setExecutable(true, false);

        System.out.println("FFmpeg installed to: " + FFMPEG_EXE.getAbsolutePath());
    }

    private static void extractFfmpegFromZip(File zipFile, File outFile) throws IOException {
        try (ZipFile zf = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.isDirectory() && e.getName().toLowerCase().endsWith("ffmpeg.exe")) {
                    try (InputStream is = zf.getInputStream(e); FileOutputStream fos = new FileOutputStream(outFile)) {
                        is.transferTo(fos);
                    }
                    return;
                }
            }
        }
    }

    private static void cleanupOldFfmpegExtracts() {
        File[] files = FFMPEG_DIR.listFiles();
        if (files == null)
            return;
        for (File f : files) {
            if (!f.getName().equalsIgnoreCase("ffmpeg.exe") && !f.getName().equalsIgnoreCase("ffmpeg.zip"))
                f.delete();
        }
    }

    public static class WrapLayout extends FlowLayout {
        public WrapLayout() {
            super(FlowLayout.LEFT, 10, 5);
        }
    }
}