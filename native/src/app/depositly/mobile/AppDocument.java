package app.depositly.mobile;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Includes packaged styles without allowing the WebView to fetch subresources. */
public final class AppDocument {
    private static final Pattern STYLESHEET = Pattern.compile(
        "<link\\s+rel=\"stylesheet\"\\s+href=\"([A-Za-z0-9_-]+\\.css)\"\\s*/?>");

    private AppDocument() {}

    public interface AssetReader { String read(String name) throws IOException; }

    public static String load(AssetReader assets) throws IOException {
        Matcher links = STYLESHEET.matcher(assets.read("index.html"));
        StringBuffer document = new StringBuffer();
        while (links.find()) {
            String name = links.group(1);
            links.appendReplacement(document, Matcher.quoteReplacement(
                "<style data-app-stylesheet=\"" + name + "\">" + assets.read(name) + "</style>"));
        }
        links.appendTail(document);
        return document.toString();
    }
}
