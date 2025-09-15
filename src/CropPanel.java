import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;

public class CropPanel extends JPanel {
    private BufferedImage originalImage;
    private BufferedImage scaledImage;
    private double scaleFactor;

    private Rectangle originalCropRectangle;
    private Rectangle scaledCropRectangle;

    private Point startPointScaled;
    private boolean dragging;

    public CropPanel(BufferedImage image) {
        this.originalImage = image;

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int maxWidth = (int)(screenSize.width * 0.9);
        int maxHeight = (int)(screenSize.height * 0.9);

        if (originalImage.getWidth() > maxWidth || originalImage.getHeight() > maxHeight) {
            double scaleX = (double) maxWidth / originalImage.getWidth();
            double scaleY = (double) maxHeight / originalImage.getHeight();
            this.scaleFactor = Math.min(scaleX, scaleY);
        } else {
            this.scaleFactor = 1.0;
        }

        int scaledWidth = (int) (originalImage.getWidth() * scaleFactor);
        int scaledHeight = (int) (originalImage.getHeight() * scaleFactor);
        this.scaledImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = this.scaledImage.createGraphics();
        g2d.drawImage(originalImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH), 0, 0, null);
        g2d.dispose();

        setPreferredSize(new Dimension(scaledWidth, scaledHeight));
        setBackground(Color.DARK_GRAY);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int imgOffsetX = (getWidth() - scaledImage.getWidth()) / 2;
                int imgOffsetY = (getHeight() - scaledImage.getHeight()) / 2;
                int clickX = e.getX() - imgOffsetX;
                int clickY = e.getY() - imgOffsetY;

                if (clickX >= 0 && clickX < scaledImage.getWidth() &&
                        clickY >= 0 && clickY < scaledImage.getHeight()) {
                    startPointScaled = new Point(clickX, clickY);
                    dragging = true;
                    originalCropRectangle = null;
                    scaledCropRectangle = null;
                    repaint();
                } else {
                    startPointScaled = null;
                    dragging = false;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;
                if (startPointScaled != null) {
                    int imgOffsetX = (getWidth() - scaledImage.getWidth()) / 2;
                    int imgOffsetY = (getHeight() - scaledImage.getHeight()) / 2;
                    int currentXScaled = e.getX() - imgOffsetX;
                    int currentYScaled = e.getY() - imgOffsetY;

                    currentXScaled = Math.max(0, Math.min(currentXScaled, scaledImage.getWidth()));
                    currentYScaled = Math.max(0, Math.min(currentYScaled, scaledImage.getHeight()));

                    int xScaled = Math.min(startPointScaled.x, currentXScaled);
                    int yScaled = Math.min(startPointScaled.y, currentYScaled);
                    int widthScaled = Math.abs(startPointScaled.x - currentXScaled);
                    int heightScaled = Math.abs(startPointScaled.y - currentYScaled);

                    if (widthScaled > 0 && heightScaled > 0) {
                        scaledCropRectangle = new Rectangle(xScaled, yScaled, widthScaled, heightScaled);

                        originalCropRectangle = new Rectangle(
                                (int) (xScaled / scaleFactor),
                                (int) (yScaled / scaleFactor),
                                (int) (widthScaled / scaleFactor),
                                (int) (heightScaled / scaleFactor)
                        );
                        originalCropRectangle.x = Math.max(0, originalCropRectangle.x);
                        originalCropRectangle.y = Math.max(0, originalCropRectangle.y);
                        originalCropRectangle.width = Math.min(originalCropRectangle.width, originalImage.getWidth() - originalCropRectangle.x);
                        originalCropRectangle.height = Math.min(originalCropRectangle.height, originalImage.getHeight() - originalCropRectangle.y);
                    } else {
                        originalCropRectangle = null;
                        scaledCropRectangle = null;
                    }
                    startPointScaled = null;
                    repaint();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging && startPointScaled != null) {
                    int imgOffsetX = (getWidth() - scaledImage.getWidth()) / 2;
                    int imgOffsetY = (getHeight() - scaledImage.getHeight()) / 2;
                    int currentXScaled = e.getX() - imgOffsetX;
                    int currentYScaled = e.getY() - imgOffsetY;

                    currentXScaled = Math.max(0, Math.min(currentXScaled, scaledImage.getWidth()));
                    currentYScaled = Math.max(0, Math.min(currentYScaled, scaledImage.getHeight()));

                    int xDraw = Math.min(startPointScaled.x, currentXScaled);
                    int yDraw = Math.min(startPointScaled.y, currentYScaled);
                    int widthDraw = Math.abs(startPointScaled.x - currentXScaled);
                    int heightDraw = Math.abs(startPointScaled.y - currentYScaled);

                    scaledCropRectangle = new Rectangle(xDraw, yDraw, widthDraw, heightDraw);
                    repaint();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (scaledImage != null) {
            int imgOffsetX = (getWidth() - scaledImage.getWidth()) / 2;
            int imgOffsetY = (getHeight() - scaledImage.getHeight()) / 2;
            g.drawImage(scaledImage, imgOffsetX, imgOffsetY, this);

            if (scaledCropRectangle != null) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.translate(imgOffsetX, imgOffsetY);

                g2d.setColor(new Color(255, 0, 0, 150));
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRect(scaledCropRectangle.x, scaledCropRectangle.y, scaledCropRectangle.width, scaledCropRectangle.height);

                g2d.setColor(new Color(0, 0, 0, 100));
                g2d.fillRect(0, 0, scaledImage.getWidth(), scaledCropRectangle.y);
                g2d.fillRect(0, scaledCropRectangle.y + scaledCropRectangle.height, scaledImage.getWidth(), scaledImage.getHeight() - (scaledCropRectangle.y + scaledCropRectangle.height));
                g2d.fillRect(0, scaledCropRectangle.y, scaledCropRectangle.x, scaledCropRectangle.height);
                g2d.fillRect(scaledCropRectangle.x + scaledCropRectangle.width, scaledCropRectangle.y, scaledImage.getWidth() - (scaledCropRectangle.x + scaledCropRectangle.width), scaledCropRectangle.height);

                g2d.dispose();
            }
        }
    }

    public BufferedImage getCroppedImage() {
        if (originalCropRectangle == null || originalCropRectangle.isEmpty() || originalImage == null) {
            return originalImage;
        }
        try {
            int x = Math.max(0, Math.min(originalCropRectangle.x, originalImage.getWidth() - 1));
            int y = Math.max(0, Math.min(originalCropRectangle.y, originalImage.getHeight() - 1));
            int width = Math.min(originalCropRectangle.width, originalImage.getWidth() - x);
            int height = Math.min(originalCropRectangle.height, originalImage.getHeight() - y);

            if (width <= 0 || height <= 0) {
                return originalImage;
            }

            return originalImage.getSubimage(x, y, width, height);
        } catch (Exception e) {
            System.err.println("Error getting cropped image: " + e.getMessage());
            return originalImage;
        }
    }
}