import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class PhotoSorterSwing {
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
    private File previousFolder;
    private File[] images;
    private int currentIndex = 0;
    private final JLabel imageLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();
    private final JPanel folderButtonPanel = new JPanel(new WrapLayout());
    private final List<File> folders = new ArrayList<>();
    private final int frameHeight = 750;
    private final int frameWidth = 900;
    private Path configFilePath;

    private static class MoveAction {
        final File movedFile;
        final boolean wasDelete;
        final boolean wasSkip;
        final Path backupPath;

        public MoveAction(File movedFile, boolean wasDelete, boolean wasSkip, Path backupPath) {
            this.movedFile = movedFile;
            this.wasDelete = wasDelete;
            this.wasSkip = wasSkip;
            this.backupPath = backupPath;
        }
    }

    public PhotoSorterSwing() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Failed to set Look and Feel: " + e.getMessage());
        }
        UIManager.put("Button.minimumWidth", 120);
        UIManager.put("Button.minimumHeight", 40);
        UIManager.put("Button.margin", new Insets(10, 20, 10, 20));

        boolean pathsAreValid;
        File configFile = getConfigFilePath().toFile();

        if (!configFile.exists()) {
            if (!promptForInitialFolders()) {
                System.exit(0);
            }
            pathsAreValid = true;
        } else {
            loadPathsFromConfig();
            if (sourceFolder != null && sourceFolder.isDirectory() && destinationFolder != null && destinationFolder.isDirectory()) {
                pathsAreValid = true;
            } else {
                JOptionPane.showMessageDialog(null, "The source folder or destination folder cannot be found.\n" +
                        "Please select the folders again.", "Configuration error", JOptionPane.ERROR_MESSAGE);
                if (!promptForInitialFolders()) {
                    System.exit(0);
                }
                pathsAreValid = true;
            }
        }
        if (pathsAreValid) {
            initializeApplication();
        } else {
            System.err.println("The application could not be started due to incorrect paths.");
            System.exit(1);
        }
    }

    private void initializeApplication() {
        rootFolder = destinationFolder;
        currentFolder = destinationFolder;
        previousFolder = null;

        List<File> imageList = new ArrayList<>();
        File[] allFiles = sourceFolder.listFiles((dir, name) -> name.toLowerCase().matches(".*\\.(jpg|png|jpeg)$"));

        if (allFiles != null) {
            Collections.addAll(imageList, allFiles);
        }
        images = imageList.toArray(new File[0]);
        Arrays.sort(images);

        if (images.length == 0) {
            JOptionPane.showMessageDialog(null, "No images found in the source folder.");
        }

        mainFrame = new JFrame("Photo Sorter");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        mainFrame.setBounds(
                (int) ((screenSize.getWidth() / 2) - (frameWidth / 2.0)),
                (int) ((screenSize.getHeight() / 2) - (frameHeight / 2.0)),
                frameWidth,
                frameHeight
        );

        try {
            Image icon = ImageIO.read(Objects.requireNonNull(PhotoSorterSwing.class.getResource("/project_icon.png")));
            mainFrame.setIconImage(icon);
        } catch (IOException e) {
            e.printStackTrace();
        }

        setupUIComponents();
        setupKeyBindings();
        updateImage();
        loadFolders(destinationFolder);
        printFolderTree(destinationFolder, "");
        updateFrameTitle();
        mainFrame.setVisible(true);
    }

    private Path getConfigFilePath() {
        if (configFilePath == null) {
            String appDataPath = System.getenv("APPDATA");
            if (appDataPath == null || appDataPath.isEmpty()) {
                appDataPath = System.getProperty("user.home");
            }
            Path configDir = Paths.get(appDataPath, "PhotoSorter");
            try {
                if (!Files.exists(configDir)) {
                    Files.createDirectories(configDir);
                }
            } catch (IOException e) {
                System.err.println("Unable to create configuration directory: " + configDir);
                e.printStackTrace();
                return Paths.get("folders.txt");
            }
            configFilePath = configDir.resolve("folders.txt");
        }
        return configFilePath;
    }

    private void savePathsToConfig(File source, File destination) {
        Path configFile = getConfigFilePath();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile.toFile()))) {
            writer.write("FROM: " + source.getAbsolutePath());
            writer.newLine();
            writer.write("TO: " + destination.getAbsolutePath());
            writer.newLine();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error saving configuration: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadPathsFromConfig() {
        File configFile = getConfigFilePath().toFile();
        if (!configFile.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("FROM:")) {
                    sourceFolder = new File(line.substring(5).trim());
                } else if (line.startsWith("TO:")) {
                    destinationFolder = new File(line.substring(3).trim());
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error reading configuration file: " + e.getMessage());
        }
    }

    private File chooseDirectory(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private boolean promptForInitialFolders() {
        JOptionPane.showMessageDialog(null, "Welcome! Please select the source folder and the destination folder.", "Initial setup", JOptionPane.INFORMATION_MESSAGE);

        File source = null;
        while (source == null) {
            source = chooseDirectory("Select the source folder (Where to sort from)");
            if (source == null) {
                int result = JOptionPane.showConfirmDialog(null, "The program requires a source folder to run. Do you want to exit?", "Confirmation of exit", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) return false;
            }
        }
        sourceFolder = source;

        File destination = null;
        while (destination == null) {
            destination = chooseDirectory("Select the destination folder (Where to sort)");
            if (destination == null) {
                int result = JOptionPane.showConfirmDialog(null, "The program requires a destination folder to run. Do you want to exit?", "Confirmation of exit", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) return false;
            }
        }
        destinationFolder = destination;
        savePathsToConfig(sourceFolder, destinationFolder);
        return true;
    }

    private void changeFolder(boolean isSource) {
        String title = isSource ? "Select a new source folder" : "Select a new destination folder";
        File newFolder = chooseDirectory(title);

        if (newFolder != null && newFolder.isDirectory()) {
            if (isSource) {
                sourceFolder = newFolder;
            } else {
                destinationFolder = newFolder;
            }
            savePathsToConfig(sourceFolder, destinationFolder);
            JOptionPane.showMessageDialog(mainFrame, "The path to the folder has been updated. The application will restart.", "Restart", JOptionPane.INFORMATION_MESSAGE);

            mainFrame.dispose();
            SwingUtilities.invokeLater(PhotoSorterSwing::new);
        }
    }

    private void setupUIComponents() {
        JButton selectSourceButton = new JButton("Select Source");
        JButton selectDestButton = new JButton("Select Destination");
        selectSourceButton.addActionListener(_ -> changeFolder(true));
        selectDestButton.addActionListener(_ -> changeFolder(false));

        JButton undoButton = new JButton("Undo (X)");
        JButton moveButton = new JButton("Move (C)");
        JButton createFolderButton = new JButton("Create Folder");
        JButton backButton = new JButton("Back (Z)");
        JButton deleteButton = new JButton("Del");
        JButton skipButton = new JButton("Skip (V)");
        JButton cropButton = new JButton("Crop");
        JButton undoCropButton = new JButton("Undo Crop");

        backButton.addActionListener(_ -> { goBack(); mainFrame.requestFocusInWindow(); });
        undoButton.addActionListener(_ -> { undoMove(); mainFrame.requestFocusInWindow(); });
        moveButton.addActionListener(_ -> { moveToSelectedFolder(); mainFrame.requestFocusInWindow(); });
        createFolderButton.addActionListener(_ -> { createNewFolder(); mainFrame.requestFocusInWindow(); });
        deleteButton.addActionListener(_ -> { deletePhoto(); mainFrame.requestFocusInWindow(); });
        skipButton.addActionListener(_ -> { skipPhoto(); mainFrame.requestFocusInWindow(); });
        cropButton.addActionListener(_ -> { cropPhoto(); mainFrame.requestFocusInWindow(); });
        undoCropButton.addActionListener(_ -> { undoCrop(); mainFrame.requestFocusInWindow(); });

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
        loadFolderButtons();

        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 14f));
        statusLabel.setForeground(new Color(220, 50, 50));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(statusLabel, BorderLayout.NORTH);
        centerPanel.add(imageLabel, BorderLayout.CENTER);

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(folderButtonPanel, BorderLayout.SOUTH);
        mainFrame.add(mainPanel);
    }

    private void updateFrameTitle() {
        if (mainFrame == null) return;
        String fileName = (currentIndex < images.length && images[currentIndex] != null) ? images[currentIndex].getName() : "No image selected";
        String currentPath = currentFolder != null ? currentFolder.getAbsolutePath() : "";
        int photosLeft = Math.max(0, images.length - currentIndex);
        mainFrame.setTitle("Photo Sorter | Photos Left: " + photosLeft + " | " + fileName + " | Current Folder: " + currentPath);
        statusLabel.setText(isCurrentPhotoCropped ? "[CROPPED]" : " ");
    }

    private void updateImage() {
        isCurrentPhotoCropped = false;
        if (images.length == 0) {
            imageLabel.setIcon(null);
            imageLabel.setText("No images found in the source folder. Please select another one.");
            if (mainFrame != null) mainFrame.setTitle("Photo Sorter | No images");
            return;
        }

        if (currentIndex >= images.length) {
            imageLabel.setIcon(null);
            imageLabel.setText("No more images to sort.");
            statusLabel.setText(" ");
            if (mainFrame != null) mainFrame.setTitle("Photo Sorter | Sorting complete");
            return;
        }

        while (currentIndex < images.length) {
            File file = images[currentIndex];
            if (Objects.isNull(file) || !file.exists()) {
                currentIndex++;
                continue;
            }
            try {
                BufferedImage originalImage = ImageIO.read(file);
                if (Objects.isNull(originalImage)) {
                    System.err.println("Could not read image file: " + file.getAbsolutePath());
                    currentIndex++;
                    continue;
                }
                int maxWidth = frameWidth - 50;
                int maxHeight = frameHeight - 200;
                int imgWidth = originalImage.getWidth();
                int imgHeight = originalImage.getHeight();
                double ratio = Math.min((double) maxWidth / imgWidth, (double) maxHeight / imgHeight);
                int newWidth = (int) (imgWidth * ratio);
                int newHeight = (int) (imgHeight * ratio);

                BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = scaledImage.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
                g2d.dispose();

                imageLabel.setHorizontalAlignment(JLabel.CENTER);
                imageLabel.setIcon(new ImageIcon(scaledImage));

                File backupFile = new File(file.getAbsolutePath() + ".bak");
                isCurrentPhotoCropped = backupFile.exists();
                updateFrameTitle();
                break;
            } catch (IOException e) {
                System.err.println("Error loading image " + file.getAbsolutePath() + ": " + e.getMessage());
                currentIndex++;
            }
        }

        if (currentIndex >= images.length) {
            imageLabel.setIcon(null);
            imageLabel.setText("No more images to sort.");
            statusLabel.setText(" ");
            if (mainFrame != null) mainFrame.setTitle("Photo Sorter | Sorting complete");
        }
    }

    private void setupKeyBindings() {
        InputMap inputMap = mainPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = mainPanel.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0), "BACK_ACTION");
        actionMap.put("BACK_ACTION", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { goBack(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, 0), "UNDO_ACTION");
        actionMap.put("UNDO_ACTION", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { undoMove(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0), "MOVE_ACTION");
        actionMap.put("MOVE_ACTION", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { moveToSelectedFolder(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, 0), "SKIP_ACTION");
        actionMap.put("SKIP_ACTION", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { skipPhoto(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, 0), "EMERGENCY_EXIT");
        actionMap.put("EMERGENCY_EXIT", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { System.exit(0); }
        });
    }

    private void recordNewActionAndNext(File sourceFile, File targetFile, boolean isDelete, boolean isSkip, Path backupPath) {
        if (!moveHistory.isEmpty()) {
            MoveAction previousAction = moveHistory.peek();
            if (Objects.nonNull(previousAction.backupPath) && Files.exists(previousAction.backupPath)) {
                try {
                    Files.delete(previousAction.backupPath);
                } catch (IOException e) {
                    System.err.println("Could not clean up stale backup file: " + previousAction.backupPath);
                }
            }
        }
        MoveAction newAction = new MoveAction(targetFile, isDelete, isSkip, backupPath);
        moveHistory.push(newAction);
        nextImage();
    }

    private void moveToSelectedFolder() {
        moveToFolder(currentFolder);
        currentFolder = rootFolder;
        previousFolder = null;
        loadFolders(rootFolder);
        updateFrameTitle();
    }

    private void moveToFolder(File destination) {
        if (currentIndex < images.length) {
            File sourceFile = images[currentIndex];
            if (Objects.isNull(sourceFile) || !sourceFile.exists()) {
                System.err.println("Source file does not exist at index " + currentIndex);
                nextImage();
                return;
            }

            File targetFile = new File(destination, sourceFile.getName());
            Path backupPath = null;
            File backupFileForCurrent = new File(sourceFile.getAbsolutePath() + ".bak");

            if (backupFileForCurrent.exists()) {
                backupPath = backupFileForCurrent.toPath();
            }
            try {
                Files.move(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                recordNewActionAndNext(sourceFile, targetFile, false, false, backupPath);
            } catch (IOException e) {
                System.err.println("Error moving file " + sourceFile.getName() + " to " + destination.getName() + ": " + e.getMessage());
                JOptionPane.showMessageDialog(mainFrame, "Failed to move file.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deletePhoto() {
        if (currentIndex < images.length) {
            File photoToDelete = images[currentIndex];
            if (Objects.isNull(photoToDelete) || !photoToDelete.exists()) {
                System.err.println("Photo to delete does not exist at index " + currentIndex);
                nextImage();
                return;
            }

            Path backupPath = null;
            File backupFileForCurrent = new File(photoToDelete.getAbsolutePath() + ".bak");
            if (backupFileForCurrent.exists()) {
                backupPath = backupFileForCurrent.toPath();
            }

            File binFile = moveToBin(photoToDelete);
            if (Objects.nonNull(binFile)) {
                recordNewActionAndNext(photoToDelete, binFile, true, false, backupPath);
            } else {
                JOptionPane.showMessageDialog(mainFrame, "Failed to move photo to bin.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void skipPhoto() {
        if (currentIndex < images.length) {
            File sourceFile = images[currentIndex];
            recordNewActionAndNext(sourceFile, sourceFile, false, true, null);
        }
    }

    private void undoMove() {
        if (moveHistory.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No actions to undo.", "Undo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        MoveAction actionToUndo = moveHistory.pop();
        File fileToMoveBack = actionToUndo.movedFile;

        if (Objects.isNull(fileToMoveBack) || !fileToMoveBack.exists() && !actionToUndo.wasSkip) {
            JOptionPane.showMessageDialog(mainFrame, "Original file for undo not found. Cannot undo.", "Undo Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean success = false;
        if (actionToUndo.wasSkip) {
            success = true;
        } else {
            File destinationInSource = new File(sourceFolder, fileToMoveBack.getName());
            try {
                Files.move(fileToMoveBack.toPath(), destinationInSource.toPath(), StandardCopyOption.REPLACE_EXISTING);

                if (Objects.nonNull(actionToUndo.backupPath) && Files.exists(actionToUndo.backupPath)) {
                    Files.copy(actionToUndo.backupPath, destinationInSource.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Files.delete(actionToUndo.backupPath);
                }
                if (actionToUndo.wasDelete) {
                    File deleteFolder = fileToMoveBack.getParentFile();
                    if (Objects.nonNull(deleteFolder) && deleteFolder.getName().startsWith("Delete_folder_")) {
                        File[] remainingFiles = deleteFolder.listFiles();
                        if (Objects.nonNull(remainingFiles) && remainingFiles.length == 0) {
                            Files.delete(deleteFolder.toPath());
                        }
                    }
                }
                success = true;
            } catch (IOException e) {
                System.err.println("Undo failed for " + fileToMoveBack.getName() + ": " + e.getMessage());
                JOptionPane.showMessageDialog(mainFrame, "Failed to undo move: " + e.getMessage(), "Undo Error", JOptionPane.ERROR_MESSAGE);
                moveHistory.push(actionToUndo);
            }
        }
        if (success) {
            currentIndex = Math.max(0, currentIndex - 1);
            updateImage();
        }
    }

    private void nextImage() {
        currentIndex++;
        updateImage();
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

    private void loadFolders(File parentFolder) {
        folders.clear();
        File[] folderArray = parentFolder.listFiles(File::isDirectory);
        if (Objects.nonNull(folderArray)) {
            Arrays.sort(folderArray);
            for (File folder : folderArray) {
                if (!folder.getName().startsWith("Delete_folder_") && !folder.getName().equals("Del")) {
                    folders.add(folder);
                }
            }
        }
        loadFolderButtons();
    }

    private void loadFolderButtons() {
        folderButtonPanel.removeAll();
        folderButtonPanel.setLayout(new GridLayout(0, calculateColumns(), 5, 5));
        for (File folder : folders) {
            JButton folderButton = new JButton(folder.getName());
            folderButton.addActionListener(e -> selectFolder(folder));
            folderButtonPanel.add(folderButton);
        }

        if (!currentFolder.equals(rootFolder)) {
            JButton upButton = new JButton("..");
            upButton.addActionListener(e -> goBack());
            folderButtonPanel.add(upButton, 0);
        }
        folderButtonPanel.revalidate();
        folderButtonPanel.repaint();
    }

    private void selectFolder(File folder) {
        previousFolder = currentFolder;
        currentFolder = folder;
        if (currentFolderHasFolders(currentFolder)) {
            loadFolders(currentFolder);
        } else {
            moveToFolder(currentFolder);
            currentFolder = Objects.requireNonNullElse(previousFolder, rootFolder);
            loadFolders(currentFolder);
        }
        updateFrameTitle();
    }

    private void goBack() {
        if (currentFolder.equals(rootFolder)) {
            JOptionPane.showMessageDialog(mainFrame, "Already at the root folder.", "Navigation", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File parent = currentFolder.getParentFile();
        if (Objects.nonNull(parent)) {
            currentFolder = parent;
            previousFolder = currentFolder.getParentFile();
            loadFolders(currentFolder);
        }
        updateFrameTitle();
    }

    private File moveToBin(File file) {
        try {
            File mainBinDir = new File(destinationFolder, "Del");
            if (!mainBinDir.exists() && !mainBinDir.mkdir()) {
                System.err.println("Failed to create main bin directory: " + mainBinDir.getAbsolutePath());
                return null;
            }

            File uniqueDeleteFolder = new File(mainBinDir, "Delete_folder_" + deleteIndex);
            if (!uniqueDeleteFolder.exists() && !uniqueDeleteFolder.mkdir()) {
                System.err.println("Failed to create unique delete directory: " + uniqueDeleteFolder.getAbsolutePath());
                return null;
            }

            File targetFile = new File(uniqueDeleteFolder, file.getName());
            Files.move(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return targetFile;
        } catch (IOException e) {
            System.err.println("Error moving " + file.getName() + " to bin: " + e.getMessage());
        }
        return null;
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    private void cropPhoto() {
        if (currentIndex < images.length) {
            File currentImageFile = images[currentIndex];
            if (Objects.isNull(currentImageFile) || !currentImageFile.exists()) {
                System.err.println("Image file not found for cropping at index " + currentIndex);
                return;
            }
            try {
                BufferedImage originalImage = ImageIO.read(currentImageFile);
                if (Objects.isNull(originalImage)) {
                    JOptionPane.showMessageDialog(mainFrame, "Could not read image for cropping.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                BufferedImage croppedImage = ImageCropDialog.showCropDialog(mainFrame, originalImage);
                if (Objects.nonNull(croppedImage)) {
                    Path originalPath = currentImageFile.toPath();
                    Path backupPath = new File(currentImageFile.getAbsolutePath() + ".bak").toPath();

                    if (!Files.exists(backupPath)) {
                        Files.copy(originalPath, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
                    }

                    String formatName = getFileExtension(currentImageFile);
                    if (formatName.isEmpty()) {
                        formatName = "png";
                    }
                    ImageIO.write(croppedImage, formatName, currentImageFile);
                    updateImage();
                }
            } catch (IOException ex) {
                System.err.println("Error during image cropping: " + ex.getMessage());
                JOptionPane.showMessageDialog(mainFrame, "Error during image cropping: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void undoCrop() {
        if (currentIndex >= images.length) return;
        File currentImageFile = images[currentIndex];
        if (Objects.isNull(currentImageFile) || !currentImageFile.exists()) {
            JOptionPane.showMessageDialog(mainFrame, "Current image file not found.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        File backupFile = new File(currentImageFile.getAbsolutePath() + ".bak");
        if (backupFile.exists()) {
            try {
                Path originalPath = currentImageFile.toPath();
                Path backupPath = backupFile.toPath();
                Files.copy(backupPath, originalPath, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(backupPath);
                updateImage();
                JOptionPane.showMessageDialog(mainFrame, "Crop operation undone.", "Undo Crop", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                System.err.println("Error undoing crop: " + ex.getMessage());
                JOptionPane.showMessageDialog(mainFrame, "Error undoing crop: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(mainFrame, "No crop operation to undo for this image.", "Undo Crop", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private boolean currentFolderHasFolders(File currentFolder) {
        File[] files = currentFolder.listFiles(File::isDirectory);
        if (Objects.nonNull(files)) {
            for (File file : files) {
                if (!file.getName().equals("Del") && !file.getName().startsWith("Delete_folder_")) {
                    return true;
                }
            }
        }
        return false;
    }

    public void printFolderTree(File folder, String prefix) {
        if (Objects.isNull(folder) || !folder.exists() || !folder.isDirectory()) {
            return;
        }

        File[] files = folder.listFiles();
        if (Objects.isNull(files)) return;
        List<File> directories = new ArrayList<>();
        List<File> imageFiles = new ArrayList<>();

        for (File file : files) {
            if (file.isDirectory()) {
                if (!file.getName().equals("Del") && !file.getName().startsWith("Delete_folder_")) {
                    directories.add(file);
                }
            } else {
                if (file.getName().toLowerCase().matches(".*\\.(jpg|png|jpeg)$")) {
                    imageFiles.add(file);
                }
            }
        }

        Collections.sort(directories);
        Collections.sort(imageFiles);

        for (int i = 0; i < directories.size(); i++) {
            File dir = directories.get(i);
            boolean isLast = (i == directories.size() - 1) && imageFiles.isEmpty();
            String connector = isLast ? "└── " : "├── ";
            System.out.println(prefix + connector + "[DIR] " + dir.getName());
            printFolderTree(dir, prefix + (isLast ? "    " : "│   "));
        }

        for (int i = 0; i < imageFiles.size(); i++) {
            File file = imageFiles.get(i);
            boolean isLast = (i == imageFiles.size() - 1);
            String connector = isLast ? "└── " : "├── ";
            System.out.println(prefix + connector + "[FILE] " + file.getName());
        }
    }

    private int calculateColumns() {
        int panelWidth = folderButtonPanel.getWidth();
        if (panelWidth == 0 && folderButtonPanel.getParent() != null) {
            panelWidth = folderButtonPanel.getParent().getWidth();
        }

        if (panelWidth == 0) panelWidth = 800;
        int buttonWidth = 120 + 5;
        return Math.max(1, panelWidth / buttonWidth);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(PhotoSorterSwing::new);
    }

    public class WrapLayout extends FlowLayout {
        public WrapLayout() {
            super(FlowLayout.LEFT, 10, 5);
        }
    }
}