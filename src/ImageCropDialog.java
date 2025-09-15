import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageCropDialog extends JDialog {
    private BufferedImage originalImage;
    private BufferedImage croppedImageResult;
    private CropPanel cropPanel;

    public ImageCropDialog(Frame owner, BufferedImage image) {
        super(owner, "Обрезать изображение", true);
        this.originalImage = image;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout());

        cropPanel = new CropPanel(originalImage);
        JScrollPane scrollPane = new JScrollPane(cropPanel);

        // Устанавливаем предпочтительный размер для JScrollPane
        // Он будет соответствовать масштабированному изображению,
        // которое уже учитывает размеры экрана
        scrollPane.setPreferredSize(cropPanel.getPreferredSize());

        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cropButton = new JButton("Обрезать");
        JButton cancelButton = new JButton("Отмена");

        cropButton.addActionListener(e -> {
            croppedImageResult = cropPanel.getCroppedImage();
            dispose();
        });

        cancelButton.addActionListener(e -> {
            croppedImageResult = null;
            dispose();
        });

        buttonPanel.add(cropButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    public static BufferedImage showCropDialog(JFrame parent, BufferedImage image) {
        ImageCropDialog dialog = new ImageCropDialog(parent, image);
        dialog.setVisible(true);
        return dialog.croppedImageResult;
    }
}