package ua.shiningpr1sm.photosorter;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class Launcher {
    public static void main(String[] args) {
        ConfigManager.initConfig();
        String currentVer = ConfigManager.getInternalVersion();

        UpdateManager updateManager = new UpdateManager();
        UpdateManager.ReleaseInfo release = null;
        try {
            release = updateManager.fetchLatestRelease();
        } catch (Exception e) {
            System.err.println("Update check failed: " + e.getMessage());
        }

        if (release != null && updateManager.compareVersions(release.version(), currentVer) > 0) {
            String skippedVersion = ConfigManager.loadSkippedVersion();
            if (release.version().equals(skippedVersion)) {
                System.out.println("Version " + release.version() + " skipped by user");
            } else {
                String notesHtml = MarkdownUtil.toHtml(release.notesMarkdown());
                SwingUpdatePrompt.Choice choice = SwingUpdatePrompt.show(currentVer, release.version(), notesHtml);

                if (choice == SwingUpdatePrompt.Choice.UPDATE) {
                    try {
                        Path tempJar = Files.createTempFile("FileOrganizer-", ".jar");
                        updateManager.downloadRelease(release, tempJar);

                        String downloadedVer = updateManager.readJarVersion(tempJar);
                        if (downloadedVer == null || !downloadedVer.equals(release.version())) {
                            System.err.println("Downloaded JAR version mismatch, aborting update");
                            Files.deleteIfExists(tempJar);
                        } else {
                            System.out.println("Downloaded update v" + downloadedVer + ", restarting...");
                            UpdateApplier updateApplier = new UpdateApplier();
                            updateApplier.restartWithNewJar(tempJar);
                            return;
                        }
                    } catch (Exception e) {
                        System.err.println("Update failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else if (choice == SwingUpdatePrompt.Choice.SKIP) {
                    ConfigManager.saveSkippedVersion(release.version());
                }
            }
        }

        SwingUtilities.invokeLater(FileOrganizerSwing::new);
    }
}
