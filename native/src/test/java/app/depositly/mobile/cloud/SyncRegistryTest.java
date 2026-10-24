package app.depositly.mobile.cloud;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SyncRegistryTest {
    private JSONObject fixture()throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get("../tests/fixtures/registry.json")),StandardCharsets.UTF_8));}
    @Test public void nativeRegistersMatchJavaScriptGolden()throws Exception {
        JSONObject f=fixture(),base=f.getJSONObject("base"),initial=SyncRegistry.create(base,"seed",1);
        assertTrue(SyncRegistry.equal(initial,f.getJSONObject("initial")));
        JSONObject web=SyncRegistry.change(initial,base,f.getJSONObject("one"),"web",20),phone=SyncRegistry.change(initial,base,f.getJSONObject("two"),"phone",20);
        assertTrue(SyncRegistry.equal(web,f.getJSONObject("web")));assertTrue(SyncRegistry.equal(phone,f.getJSONObject("phone")));
        JSONObject merged=SyncRegistry.merge(web,phone);assertTrue(SyncRegistry.equal(merged,f.getJSONObject("merged")));assertTrue(SyncRegistry.equal(merged,SyncRegistry.merge(phone,web)));
        assertEquals("Website",SyncRegistry.backup(merged).getJSONObject("data").getJSONArray("assets").getJSONObject(0).getString("name"));
        assertEquals(2000000,SyncRegistry.backup(merged).getJSONObject("data").getJSONArray("assets").getJSONObject(0).getLong("principal"));
        JSONObject deleted=f.getJSONObject("deleted");assertEquals(0,SyncRegistry.backup(SyncRegistry.merge(deleted,phone)).getJSONObject("data").getJSONArray("assets").length());
        assertEquals(0,SyncRegistry.backup(deleted).getJSONObject("data").getJSONObject("paid").length());
        JSONObject restore=SyncRegistry.change(deleted,SyncRegistry.backup(deleted),base,"restore",22);assertTrue(SyncRegistry.equal(restore,f.getJSONObject("restored")));
    }
    @Test public void invalidClocksAndTombstonesAreRejected()throws Exception {
        for(Object value:new Object[]{-1,1.5,"2",9007199254740992d}){
            JSONObject doc=new JSONObject("{\"version\":1,\"entries\":{\"a/a\":{\"s\":[0,0,\"a\"],\"v\":true}}}");doc.getJSONObject("entries").getJSONObject("a/a").getJSONArray("s").put(0,value);
            try{SyncRegistry.validate(doc);fail("Invalid revision accepted");}catch(Exception expected){}
        }
    }
    @Test public void concurrentHeadsAreBothReadAndDatedUploadsNeverDeleteHistory()throws Exception {
        JSONObject f=fixture();List<String> methods=new ArrayList<>();
        // Empty financial rows keep this transport test independent of schedule validation.
        JSONObject payload=new JSONObject("{\"format\":\"depositly-google-drive\",\"version\":1,\"data\":{\"scope\":\"all\",\"assets\":[],\"paid\":{}}}");
        JSONObject doc=SyncRegistry.create(payload,"seed",1);String backup=SyncRegistry.backup(doc).toString();
        DriveRepository drive=new DriveRepository((method,path,type,body)->{
            methods.add(method);
            if(method.equals("POST")){String text=new String(body,StandardCharsets.UTF_8);assertTrue(text.contains("Depositly-"));assertTrue(text.contains("\"p0\":\"a\""));assertTrue(text.contains("\"p1\":\"b\""));return "{\"id\":\"new\"}";}
            if(path.contains("spaces="))return "{\"files\":[{\"id\":\"a\",\"name\":\"A\",\"appProperties\":{\"depositlyKind\":\"registry\"}},{\"id\":\"b\",\"name\":\"B\",\"appProperties\":{\"depositlyKind\":\"registry\"}}]}";
            return backup;
        });
        JSONObject remote=drive.registry(new JSONArray());assertEquals(2,remote.getJSONArray("heads").length());drive.saveVersion(remote.getJSONObject("backup"),remote.getJSONArray("heads"));assertFalse(methods.contains("DELETE"));
    }
}
