package app.depositly.mobile.cloud;

import app.depositly.mobile.MainActivity;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.*;

/** Immutable uploads keep the previous backup intact if a request fails halfway. */
public final class DriveRepository {
    public static final String FORMAT = "depositly-google-drive", FILE_NAME = "depositly-backup.json";
    public interface Transport { String request(String method, String path, String type, byte[] body) throws Exception; }
    private final Transport transport;
    public DriveRepository(Transport transport) { this.transport = transport; }
    public static String encode(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }
    public JSONObject user() throws Exception { return new JSONObject(transport.request("GET", "https://www.googleapis.com/oauth2/v3/userinfo", null, null)); }
    private JSONArray files() throws Exception {
        String query = "name = '" + FILE_NAME + "' and trashed = false";
        String path = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=100&orderBy=createdTime%20desc&fields=files(id,createdTime)&q=" + encode(query);
        return new JSONObject(transport.request("GET", path, null, null)).getJSONArray("files");
    }
    public String latest() throws Exception {
        JSONArray files = files();
        if (files.length() == 0) return null;
        String id = files.getJSONObject(0).getString("id");
        String value = transport.request("GET", "https://www.googleapis.com/drive/v3/files/" + encode(id) + "?alt=media", null, null);
        validate(value);
        return value;
    }
    public static void validate(String value) throws Exception {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > 20000000) throw new IOException("Backup is too large.");
        JSONObject backup = new JSONObject(value);
        if (!FORMAT.equals(backup.optString("format")) || backup.optInt("version") != 1 || !"all".equals(backup.getJSONObject("data").optString("scope")))
            throw new IOException("Unsupported Google Drive backup. Update Depositly or use a file backup.");
        backup.getJSONObject("data").getJSONArray("assets");
        backup.getJSONObject("data").getJSONObject("paid");
    }
    public void save(String payload) throws Exception {
        validate(payload);
        String boundary = "depositly_" + UUID.randomUUID().toString().replace("-", "");
        JSONObject meta = new JSONObject().put("name", FILE_NAME).put("mimeType", "application/json")
            .put("parents", new JSONArray().put("appDataFolder"));
        String multipart = "--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + meta +
            "\r\n--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + payload + "\r\n--" + boundary + "--\r\n";
        JSONObject created = new JSONObject(transport.request("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
            "multipart/related; boundary=" + boundary, multipart.getBytes(StandardCharsets.UTF_8)));
        if (created.optString("id").isEmpty()) throw new IOException("Google Drive did not confirm the backup.");
        // Retain ten complete snapshots. Cleanup failure never invalidates a successful upload.
        try {
            JSONArray files = files();
            for (int i = 10; i < files.length(); i++) transport.request("DELETE",
                "https://www.googleapis.com/drive/v3/files/" + encode(files.getJSONObject(i).getString("id")), null, null);
        } catch (Exception ignored) { }
    }
    public static final class HttpFailure extends IOException {
        public final int code;
        public HttpFailure(int code) {
            super(code == 401 ? "Google access expired. Sign in again." : code == 403 ?
                "Google Drive access was denied. Enable the Drive API and allow backup access, then try again." :
                code == 429 ? "Google Drive is busy. Your backup will retry." : "Google Drive could not be reached. Your data is safe on this phone.");
            this.code = code;
        }
    }
    public static Transport authenticated(String token) {
        return (method, path, type, body) -> {
            URL url = new URL(path);
            if (!"https".equals(url.getProtocol()) || !"www.googleapis.com".equals(url.getHost())) throw new IOException("Invalid Google endpoint");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
                connection.setRequestMethod(method);
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Accept", "application/json");
                if (body != null) {
                    connection.setDoOutput(true); connection.setFixedLengthStreamingMode(body.length);
                    connection.setRequestProperty("Content-Type", type);
                    try (OutputStream out = connection.getOutputStream()) { out.write(body); }
                }
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new HttpFailure(code);
                if (code == 204) return "";
                try (InputStream input = connection.getInputStream()) { return MainActivity.read(input); }
            } finally { connection.disconnect(); }
        };
    }
}
