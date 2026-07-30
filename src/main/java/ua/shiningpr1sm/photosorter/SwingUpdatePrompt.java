package ua.shiningpr1sm.photosorter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class SwingUpdatePrompt {

    public enum Choice { UPDATE, SKIP, CANCEL }

    public static Choice show(String currentVersion, String newVersion, String notesHtml) {
        Choice[] result = { Choice.CANCEL };

        JDialog dialog = new JDialog();
        dialog.setTitle("Update Available");
        dialog.setModal(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel header = new JLabel("New Update Available", SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 18));
        header.setForeground(new Color(26, 115, 232));

        JLabel versionLabel = new JLabel(currentVersion + "  \u2192  " + newVersion, SwingConstants.CENTER);
        versionLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        versionLabel.setForeground(new Color(80, 80, 80));

        JLabel whatsNewLabel = new JLabel("What's new?");
        whatsNewLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        whatsNewLabel.setBorder(new EmptyBorder(8, 0, 4, 0));

        String styledHtml = "<html><body style='font-family: Segoe UI, sans-serif; font-size: 13px; margin: 4px; color: #333;'>"
                + notesHtml
                + "</body></html>";

        JEditorPane editorPane = new JEditorPane("text/html", styledHtml);
        editorPane.setEditable(false);
        editorPane.setOpaque(false);
        editorPane.setCaret(new javax.swing.text.DefaultCaret() {
            @Override public void paint(Graphics g) {}
            @Override public boolean isVisible() { return false; }
        });
        editorPane.setHighlighter(null);

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setPreferredSize(new Dimension(460, 280));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        JButton updateButton = new JButton("Update Now");
        updateButton.setBackground(new Color(26, 115, 232));
        updateButton.setForeground(Color.WHITE);
        updateButton.setFocusPainted(false);
        updateButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        updateButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton skipButton = new JButton("Skip this version");
        skipButton.setBackground(new Color(240, 240, 240));
        skipButton.setFocusPainted(false);
        skipButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        skipButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        updateButton.addActionListener(e -> {
            result[0] = Choice.UPDATE;
            dialog.dispose();
        });
        skipButton.addActionListener(e -> {
            result[0] = Choice.SKIP;
            dialog.dispose();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttonPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        buttonPanel.add(skipButton);
        buttonPanel.add(updateButton);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        whatsNewLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        topPanel.add(header);
        topPanel.add(versionLabel);
        topPanel.add(whatsNewLabel);

        root.add(topPanel, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.CENTER);
        root.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);

        return result[0];
    }
}
