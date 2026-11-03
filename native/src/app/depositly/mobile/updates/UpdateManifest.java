package app.depositly.mobile.updates;

import java.io.*;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.Callable;
import org.json.JSONObject;

/** The public release contract. None of its URLs or signer values are trusted implicitly. */
public final class UpdateManifest {
    public static final String PACKAGE = "app.depositly.mobile";
    public static final String CERTIFICATE = "655445e0b1a199ef9ae90efa6e795d0800d36ef0ca725f5f87835d396796224d";
    public static final long MAX_APK_BYTES = 256L * 1024 * 1024;
    public final String versionName, apkUrl, apkSha256, releaseNotes;
    public final long versionCode, apkSize;
    private final String json;

    public UpdateManifest(String source) throws Exception {
        JSONObject value = new JSONObject(source);
        if (integer(value, "schemaVersion") != 1 || !PACKAGE.equals(value.getString("package"))) throw new IOException("Invalid update package");
        versionName = value.getString("versionName");
        versionCode = integer(value, "versionCode");
        apkSize = integer(value, "apkSize");
        apkUrl = value.getString("apkUrl");
        apkSha256 = value.getString("apkSha256").toLowerCase(Locale.ROOT);
        releaseNotes = value.optString("releaseNotes", "");
        if (versionName.isEmpty() || versionName.length() > 100 || versionCode < 1 || versionCode > 2100000000L
            || apkSize < 1 || apkSize > MAX_APK_BYTES || !apkSha256.matches("[a-f0-9]{64}")
            || !CERTIFICATE.equalsIgnoreCase(value.getString("certificateSha256")) || releaseNotes.length() > 32768) throw new IOException("Invalid update metadata");
        URI uri = URI.create(apkUrl);
        String slug = versionName.replace(" Build ", "-build-");
        String expected = "/FayedFahad/Depositly/releases/download/v" + slug + "/Depositly-" + slug + ".apk";
        if (!slug.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?-build-[0-9]+") || !"https".equals(uri.getScheme())
            || !"github.com".equals(uri.getHost()) || uri.getPort() != -1 || uri.getRawUserInfo() != null
            || uri.getRawQuery() != null || uri.getRawFragment() != null || !expected.equals(uri.getRawPath())) throw new IOException("Invalid update download URL");
        json = value.toString();
    }

    public String toJson() { return json; }
    public boolean newerThan(long installedCode) { return versionCode > installedCode; }

    private static long integer(JSONObject value, String key) throws Exception {
        Object number = value.get(key);
        if (!(number instanceof Number) || !number.toString().matches("[0-9]+")) throw new IOException("Invalid update number");
        try { return Long.parseLong(number.toString()); }
        catch (NumberFormatException e) { throw new IOException("Invalid update number", e); }
    }

    public static String digest(InputStream input, OutputStream output, long expectedSize, Callable<Boolean> canceled,
                                Progress progress) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[32768];
        long total = 0;
        for (int count; (count = input.read(buffer)) != -1;) {
            if (canceled.call() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Download canceled");
            total += count;
            if (total > expectedSize || total > MAX_APK_BYTES) throw new IOException("Update download size does not match");
            hash.update(buffer, 0, count);
            if (output != null) output.write(buffer, 0, count);
            if (progress != null) progress.bytes(total);
        }
        if (canceled.call() || total != expectedSize) throw new IOException("Update download is incomplete");
        return hex(hash.digest());
    }

    public interface Progress { void bytes(long value); }
    public static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) text.append(String.format(Locale.ROOT, "%02x", b & 255));
        return text.toString();
    }
}
