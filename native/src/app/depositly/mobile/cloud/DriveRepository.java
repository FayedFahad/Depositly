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
        if(backup.has("sync"))SyncRegistry.validate(backup.getJSONObject("sync"));
        JSONObject reminders=backup.optJSONObject("notificationSettings");
        if(reminders!=null&&(!(reminders.get("enabled") instanceof Boolean)||!reminders.getString("time").matches("([01]\\d|2[0-3]):[0-5]\\d")))throw new IOException("Invalid reminder settings.");
        JSONArray assets=backup.getJSONObject("data").getJSONArray("assets");java.util.Set<String> ids=new java.util.HashSet<>();
        if(assets.length()>500)throw new IOException("Too many investments.");
        for(int i=0;i<assets.length();i++){
            JSONObject a=assets.getJSONObject(i);String id=a.getString("id");double principal=a.getDouble("principal"),rate=a.getDouble("rate"),tds=a.getDouble("tds");
            if(!id.matches("[A-Za-z0-9_-]{1,100}")||!ids.add(id)||a.getString("name").trim().isEmpty()||a.getString("name").length()>120||!java.util.Arrays.asList("FDR","NSC","Sanchaypatra","National Savings Certificate").contains(a.optString("type"))||principal<=0||principal>1e13||principal!=Math.floor(principal)||!Double.isFinite(rate)||rate<0||rate>100||!Double.isFinite(tds)||tds<0||tds>100)throw new IOException("Invalid investment values.");
            java.time.LocalDate start=java.time.LocalDate.parse(a.getString("start")),first=java.time.LocalDate.parse(a.getString("first")),end=java.time.LocalDate.parse(a.getString("maturity"));
            if(!start.isBefore(first)||first.isAfter(end))throw new IOException("Invalid investment dates.");
        }
        JSONObject paid=backup.getJSONObject("data").getJSONObject("paid");java.util.Iterator<String> keys=paid.keys();
        while(keys.hasNext()){
            String key=keys.next();JSONObject receipt=paid.getJSONObject(key);double amount=receipt.getDouble("amount");
            if(!key.matches("[A-Za-z0-9_-]{1,100}:(?:\\d{4}-\\d{2}-\\d{2}(?::final)?|principal)")||!ids.contains(key.split(":")[0])||!Double.isFinite(amount)||amount<0||amount>1e13||amount!=Math.floor(amount)||receipt.optString("notes").length()>500)throw new IOException("Invalid receipt values.");
            java.time.LocalDate.parse(receipt.getString("date"));JSONObject snapshot=receipt.optJSONObject("snapshot");if(snapshot!=null&&!key.split(":")[0].equals(snapshot.optString("assetId")))throw new IOException("Invalid receipt history.");
        }
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
    private static final String REGISTRY_QUERY = "(name = 'depositly-backup.json' or appProperties has { key='depositlyKind' and value='registry' }) and trashed = false";
    private static final String FILE_FIELDS = "id,name,createdTime,size,appProperties";
    private static boolean registryFile(JSONObject file) {
        return FILE_NAME.equals(file.optString("name")) || file.optJSONObject("appProperties") != null && "registry".equals(file.optJSONObject("appProperties").optString("depositlyKind"));
    }
    private static void checkFile(JSONObject file) throws Exception {
        if (!file.optString("id").matches("[A-Za-z0-9_-]{1,256}") || !registryFile(file) || file.optLong("size") > 20000000) throw new IOException("Invalid backup version.");
    }
    public JSONObject versions(String pageToken) throws Exception {
        String path="https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=1000&orderBy=createdTime%20desc&fields="+encode("nextPageToken,files("+FILE_FIELDS+")")+"&q="+encode(REGISTRY_QUERY);
        if(pageToken!=null&&!pageToken.isEmpty())path+="&pageToken="+encode(pageToken);
        JSONObject result=new JSONObject(transport.request("GET",path,null,null));JSONArray files=result.getJSONArray("files");
        for(int i=0;i<files.length();i++)checkFile(files.getJSONObject(i));return result;
    }
    public JSONObject version(String id) throws Exception {
        if(id==null||!id.matches("[A-Za-z0-9_-]{1,256}"))throw new IOException("Invalid backup version.");
        JSONObject file=new JSONObject(transport.request("GET","https://www.googleapis.com/drive/v3/files/"+encode(id)+"?fields="+encode(FILE_FIELDS),null,null));checkFile(file);
        JSONObject backup=new JSONObject(transport.request("GET","https://www.googleapis.com/drive/v3/files/"+encode(id)+"?alt=media",null,null));validate(backup.toString());
        return new JSONObject().put("file",file).put("backup",backup);
    }
    public JSONObject registry(JSONArray known) throws Exception {
        java.util.List<JSONObject> files=new java.util.ArrayList<>();String page="";
        do{JSONObject result=versions(page);JSONArray list=result.getJSONArray("files");for(int i=0;i<list.length();i++)files.add(list.getJSONObject(i));page=result.optString("nextPageToken");if(files.size()>100000)throw new IOException("Too many backup versions.");}while(!page.isEmpty());
        java.util.Set<String> parents=new java.util.HashSet<>();JSONObject legacy=null;
        for(JSONObject file:files){JSONObject props=file.optJSONObject("appProperties");if(props!=null){java.util.Iterator<String> keys=props.keys();while(keys.hasNext()){String key=keys.next();if(key.matches("p\\d+"))parents.add(props.getString(key));}}
            if(FILE_NAME.equals(file.optString("name"))&&(legacy==null||file.optString("createdTime").compareTo(legacy.optString("createdTime"))>0||file.optString("createdTime").equals(legacy.optString("createdTime"))&&file.optString("id").compareTo(legacy.optString("id"))>0))legacy=file;
        }
        java.util.List<JSONObject> heads=new java.util.ArrayList<>();for(JSONObject file:files)if(!parents.contains(file.getString("id"))&&(!FILE_NAME.equals(file.optString("name"))||file==legacy))heads.add(file);
        java.util.Collections.sort(heads,(a,b)->a.optString("id").compareTo(b.optString("id")));if(heads.size()>128)throw new IOException("Too many simultaneous registry versions.");
        JSONArray ids=new JSONArray();for(JSONObject file:heads)ids.put(file.getString("id"));
        if(SyncRegistry.equal(ids,known))return new JSONObject().put("unchanged",true).put("heads",ids);
        JSONObject doc=null;for(JSONObject file:heads){JSONObject backup=new JSONObject(transport.request("GET","https://www.googleapis.com/drive/v3/files/"+encode(file.getString("id"))+"?alt=media",null,null));validate(backup.toString());doc=SyncRegistry.merge(doc,SyncRegistry.fromBackup(backup,file));}
        return new JSONObject().put("heads",ids).put("backup",doc==null?JSONObject.NULL:SyncRegistry.backup(doc));
    }
    public JSONObject saveVersion(JSONObject backup,JSONArray parents) throws Exception {
        backup=SyncRegistry.backup(SyncRegistry.validate(backup.getJSONObject("sync")));validate(backup.toString());
        String date=java.time.Instant.now().toString().replace(':','-').replace('.','-'),boundary="depositly_"+UUID.randomUUID().toString();
        JSONObject props=new JSONObject().put("depositlyKind","registry").put("source","Android");
        for(int i=0;i<Math.min(24,parents.length());i++){String id=parents.getString(i);if(id.matches("[A-Za-z0-9_-]{1,119}"))props.put("p"+i,id);}
        JSONObject meta=new JSONObject().put("name","Depositly-"+date+"-"+UUID.randomUUID().toString().substring(0,8)+".json").put("mimeType","application/json").put("parents",new JSONArray().put("appDataFolder")).put("appProperties",props);
        String body="--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+meta+"\r\n--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+backup+"\r\n--"+boundary+"--\r\n";
        JSONObject file=new JSONObject(transport.request("POST","https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields="+encode(FILE_FIELDS),"multipart/related; boundary="+boundary,body.getBytes(StandardCharsets.UTF_8)));
        if(!file.optString("id").matches("[A-Za-z0-9_-]{1,256}"))throw new IOException("Drive did not confirm the backup version.");
        return file;
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
