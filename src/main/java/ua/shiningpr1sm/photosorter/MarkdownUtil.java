package ua.shiningpr1sm.photosorter;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

public class MarkdownUtil {

    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) return "<p>No release notes available.</p>";
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        return renderer.render(parser.parse(markdown));
    }
}
