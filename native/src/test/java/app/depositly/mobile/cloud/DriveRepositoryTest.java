package app.depositly.mobile.cloud;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DriveRepositoryTest {
    private final String backup = "{\"format\":\"depositly-google-drive\",\"version\":1,\"data\":{\"scope\":\"all\",\"assets\":[],\"paid\":{}}}";
    @Test public void emptyDriveDoesNotReadOrWriteAFile() throws Exception {
        List<String> methods=new ArrayList<>();
        DriveRepository repo=new DriveRepository((method,path,type,body)->{methods.add(method);assertTrue(path.contains("spaces=appDataFolder"));return "{\"files\":[]}";});
        assertNull(repo.latest());assertEquals(Collections.singletonList("GET"),methods);
    }
    @Test public void latestReadsNewestCompleteSnapshot() throws Exception {
        DriveRepository repo=new DriveRepository((method,path,type,body)->path.contains("spaces=") ? "{\"files\":[{\"id\":\"new\"},{\"id\":\"old\"}]}" : backup);
        assertEquals(backup,repo.latest());
    }
    @Test public void failedUploadNeverDeletesPreviousSnapshots() throws Exception {
        List<String> methods=new ArrayList<>();
        DriveRepository repo=new DriveRepository((method,path,type,body)->{methods.add(method);throw new IOException("offline");});
        try { repo.save(backup);fail(); } catch(IOException expected) {}
        assertEquals(Collections.singletonList("POST"),methods);
    }
    @Test public void cleanupKeepsTenBackupsAfterSuccessfulUpload() throws Exception {
        List<String> methods=new ArrayList<>(),deleted=new ArrayList<>();
        DriveRepository repo=new DriveRepository((method,path,type,body)->{
            methods.add(method);
            if(method.equals("POST")){String value=new String(body,StandardCharsets.UTF_8);assertTrue(value.contains("appDataFolder"));assertTrue(value.contains(backup));assertTrue(type.startsWith("multipart/related"));return "{\"id\":\"uploaded\"}";}
            if(method.equals("DELETE")){deleted.add(path);return "";}
            StringJoiner files=new StringJoiner(",");for(int i=0;i<12;i++)files.add("{\"id\":\"file"+i+"\"}");return "{\"files\":["+files+"]}";
        });
        repo.save(backup);assertEquals("POST",methods.get(0));assertEquals(2,deleted.size());assertTrue(deleted.get(0).endsWith("file10"));assertTrue(deleted.get(1).endsWith("file11"));
    }
    @Test public void unsupportedBackupsNeverUpload() throws Exception {
        DriveRepository repo=new DriveRepository((method,path,type,body)->{fail("No network call for invalid data");return "";});
        try { repo.save(backup.replace("\"version\":1","\"version\":2"));fail(); } catch(IOException expected) {}
    }
    @Test public void failedCleanupDoesNotInvalidateUpload() throws Exception {
        DriveRepository repo=new DriveRepository((method,path,type,body)->{if(method.equals("POST"))return "{\"id\":\"ok\"}";throw new IOException("cleanup failed");});repo.save(backup);
    }
}
