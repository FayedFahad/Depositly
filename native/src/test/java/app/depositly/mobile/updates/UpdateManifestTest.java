package app.depositly.mobile.updates;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateManifestTest {
    private JSONObject manifest() throws Exception {
        return new JSONObject().put("schemaVersion", 1).put("package", UpdateManifest.PACKAGE)
            .put("versionName", "1.3.1 Build 7").put("versionCode", 215).put("apkSize", 4)
            .put("apkUrl", "https://github.com/FayedFahad/Depositly/releases/download/v1.3.1-build-7/Depositly-1.3.1-build-7.apk")
            .put("apkSha256", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
            .put("certificateSha256", UpdateManifest.CERTIFICATE).put("releaseNotes", "Settings → App updates");
    }
    private void rejected(JSONObject value) throws Exception {
        try { new UpdateManifest(value.toString()); fail("Unsafe manifest was accepted: " + value); }
        catch (IOException | IllegalArgumentException expected) {}
    }
    @Test public void comparesActualAndroidCodesAndRetainsTheManifestForRecovery() throws Exception {
        UpdateManifest update = new UpdateManifest(manifest().toString());
        assertTrue(update.newerThan(214));
        assertFalse(update.newerThan(215));
        assertFalse(update.newerThan(216));
        assertEquals(215, new UpdateManifest(update.toJson()).versionCode);
        assertEquals("Settings → App updates", update.releaseNotes);
    }
    @Test public void rejectsWrongPackagesSignersMutableUrlsAndUntrustedHosts() throws Exception {
        rejected(manifest().put("package", "other.app"));
        rejected(manifest().put("certificateSha256", "0".repeat(64)));
        rejected(manifest().put("schemaVersion", 2));
        String original = manifest().getString("apkUrl");
        for (String address : new String[]{original.replace("https:", "http:"), original.replace("github.com", "github.com.evil.test"),
            original.replace("github.com", "evil@github.com"), original + "?target=other", original + "#other", original.replace("/v1.3.1-build-7/", "/latest/"),
            original.replace("Depositly-1.3.1-build-7.apk", "Depositly-1.3.1-build-8.apk"), original.replace("github.com", "github.com:443")}) {
            rejected(manifest().put("apkUrl", address));
        }
    }
    @Test public void rejectsOversizedIncompleteOrMalformedReleaseMetadata() throws Exception {
        rejected(manifest().put("apkSize", 0));
        rejected(manifest().put("apkSize", UpdateManifest.MAX_APK_BYTES + 1));
        rejected(manifest().put("versionCode", 0));
        rejected(manifest().put("versionCode", 2100000001L));
        rejected(manifest().put("versionCode", 215.5));
        rejected(manifest().put("versionCode", "215"));
        rejected(manifest().put("apkSize", 4.5));
        rejected(manifest().put("schemaVersion", 1.1));
        rejected(manifest().put("apkSha256", "not-a-checksum"));
        rejected(manifest().put("versionName", "surprise release"));
    }
    @Test public void hashesExactBytesWhileCopyingAndReportsProgress() throws Exception {
        byte[] bytes = "বাংলা update bytes".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream copied = new ByteArrayOutputStream();
        long[] progress = {0};
        String digest = UpdateManifest.digest(new ByteArrayInputStream(bytes), copied, bytes.length, () -> false, count -> progress[0] = count);
        assertArrayEquals(bytes, copied.toByteArray());
        assertEquals(bytes.length, progress[0]);
        assertEquals(UpdateManifest.hex(MessageDigest.getInstance("SHA-256").digest(bytes)), digest);
    }
    @Test public void stopsOversizedTruncatedAndCanceledStreamsBeforeTheyBecomeInstallable() throws Exception {
        for (long expected : new long[]{2, 6}) {
            try { UpdateManifest.digest(new ByteArrayInputStream(new byte[4]), new ByteArrayOutputStream(), expected, () -> false, null); fail("Wrong download length accepted"); }
            catch (IOException correct) {}
        }
        AtomicBoolean canceled = new AtomicBoolean();
        ByteArrayOutputStream copied = new ByteArrayOutputStream();
        try {
            UpdateManifest.digest(new ByteArrayInputStream(new byte[100000]), copied, 100000, canceled::get, count -> canceled.set(true));
            fail("Canceled download accepted");
        } catch (IOException correct) { assertTrue(copied.size() <= 32768); }
    }
}
