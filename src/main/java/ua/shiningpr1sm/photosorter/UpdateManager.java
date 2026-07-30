package ua.shiningpr1sm.photosorter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class UpdateManager {

    private static final String API_URL = "https://api.github.com/repos/ShiningPr1sm/File-Organizer/releases/latest";

    private final HttpClient client;

    public UpdateManager() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public record ReleaseInfo(String version, String notesMarkdown, String downloadUrl) {}

    public ReleaseInfo fetchLatestRelease() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) return null;

        String body = response.body();

        String version = null;
        int tagIdx = body.indexOf("\"tag_name\":");
        if (tagIdx != -1) {
            int start = body.indexOf("\"", tagIdx + 11) + 1;
            int end = body.indexOf("\"", start);
            version = body.substring(start, end).replaceFirst("^v", "");
        }

        String notes = null;
        int bodyIdx = body.indexOf("\"body\":");
        if (bodyIdx != -1) {
            int start = body.indexOf("\"", bodyIdx + 7) + 1;
            int end = body.indexOf("\"", start);
            if (end > start) {
                notes = body.substring(start, end)
                        .replace("\\r\\n", "\n")
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"");
            }
        }

        if (version == null) return null;

        String downloadUrl = null;
        int assetsIdx = body.indexOf("\"assets\":");
        if (assetsIdx != -1) {
            int start = body.indexOf("[", assetsIdx + 8);
            if (start != -1) {
                int end = body.indexOf("]", start);
                if (end > start) {
                    String assetsSection = body.substring(start, end + 1);
                    int jarIdx = assetsSection.indexOf("\".jar\"");
                    if (jarIdx != -1) {
                        int urlIdx = assetsSection.lastIndexOf("\"browser_download_url\":", jarIdx);
                        if (urlIdx != -1) {
                            int urlStart = assetsSection.indexOf("\"", urlIdx + 22) + 1;
                            int urlEnd = assetsSection.indexOf("\"", urlStart);
                            if (urlEnd > urlStart) {
                                downloadUrl = assetsSection.substring(urlStart, urlEnd)
                                        .replace("\\/", "/");
                            }
                        }
                    }
                }
            }
        }

        return new ReleaseInfo(version, notes, downloadUrl);
    }

    public int compareVersions(String v1, String v2) {
        String[] a = v1.split("\\.");
        String[] b = v2.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    public void downloadRelease(ReleaseInfo release, Path target) throws IOException, InterruptedException {
        if (release.downloadUrl() == null) {
            throw new IOException("No download URL available in release data");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(release.downloadUrl()))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));

        if (response.statusCode() != 200) {
            throw new IOException("Download failed with code: " + response.statusCode());
        }
    }

    public String readJarVersion(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry("project.properties");
            if (entry == null) return null;
            try (InputStream is = jar.getInputStream(entry)) {
                Properties props = new Properties();
                props.load(is);
                return props.getProperty("app.version");
            }
        } catch (Exception e) {
            return null;
        }
    }
}
