package app.depositly.mobile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppDocumentTest {
    @Test public void packagedStylesAreInlinedInOrderWithoutChangingTheirContents() throws Exception {
        Path assets = Paths.get("../app/assets");
        String document = AppDocument.load(name -> new String(Files.readAllBytes(assets.resolve(name)), StandardCharsets.UTF_8));
        int previous = -1;
        for (String name : Arrays.asList("forecast-charts.css", "bank-information.css", "home-forecast.css", "deposit-accounts.css", "redesign.css")) {
            String css = new String(Files.readAllBytes(assets.resolve(name)), StandardCharsets.UTF_8);
            int position = document.indexOf("<style data-app-stylesheet=\"" + name + "\">" + css + "</style>");
            assertTrue("Missing or reordered stylesheet: " + name, position > previous);
            assertFalse(document.contains("href=\"" + name + "\""));
            previous = position;
        }
        assertTrue(document.contains("function holdings()"));
    }

    @Test public void onlySimplePackagedCssNamesAreOpenedAndReplacementTextStaysLiteral() throws Exception {
        List<String> requested = new ArrayList<>();
        String css = "/* $1 \\ literal */ body{color:green}";
        String document = AppDocument.load(name -> {
            requested.add(name);
            return name.equals("index.html") ? "<link rel=\"stylesheet\" href=\"local.css\"><link rel=\"stylesheet\" href=\"../private.css\"><link rel=\"stylesheet\" href=\"https://example.com/remote.css\">" : css;
        });
        assertEquals(Arrays.asList("index.html", "local.css"), requested);
        assertTrue(document.contains(css));
        assertTrue(document.contains("href=\"../private.css\""));
        assertTrue(document.contains("href=\"https://example.com/remote.css\""));
    }

    @Test public void missingPackagedStylesFailInsteadOfSilentlyShowingAnUnstyledApp() throws Exception {
        try {
            AppDocument.load(name -> {
                if (name.equals("index.html")) return "<link rel=\"stylesheet\" href=\"missing.css\">";
                throw new IOException("Missing asset");
            });
            fail("A missing packaged stylesheet was ignored");
        } catch (IOException expected) {
            assertEquals("Missing asset", expected.getMessage());
        }
    }
}
