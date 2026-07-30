package ua.shiningpr1sm.photosorter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UpdateApplier {

    public void restartWithNewJar(Path tempJar) throws IOException, InterruptedException {
        Path currentJarPath;
        try {
            currentJarPath = Paths.get(
                    Launcher.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI()
            ).toAbsolutePath();
        } catch (Exception e) {
            throw new IOException("Could not locate current JAR path", e);
        }

        Path scriptPath = currentJarPath.getParent().resolve("update.bat");

        String script = String.format(
                "@echo off\n" +
                "echo Updating FileOrganizer...\n" +
                "timeout /t 2 /nobreak > nul\n" +
                ":loop\n" +
                "del /f \"%s\"\n" +
                "if exist \"%s\" (\n" +
                "  timeout /t 1 > nul\n" +
                "  goto loop\n" +
                ")\n" +
                "move /y \"%s\" \"%s\"\n" +
                "start javaw -jar \"%s\"\n" +
                "del \"%~f0\"\n",
                currentJarPath, currentJarPath,
                tempJar.toAbsolutePath(), currentJarPath,
                currentJarPath
        );

        Files.writeString(scriptPath, script);

        new ProcessBuilder("cmd", "/c", "start", scriptPath.toString()).start();

        System.exit(0);
    }
}
