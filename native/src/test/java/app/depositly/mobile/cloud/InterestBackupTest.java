package app.depositly.mobile.cloud;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;

public class InterestBackupTest {
    private JSONObject asset()throws Exception {
        return new JSONObject().put("id","interest").put("type","FDR").put("name","Interest sample").put("principal",10000000)
            .put("rate",10).put("tds",10).put("cycle",12).put("cycleUnit","months").put("start","2026-01-01")
            .put("first","2027-01-01").put("maturity","2027-01-01").put("autoRenew",false).put("tenureCount",1).put("tenureUnit","years");
    }
    private JSONObject backup(JSONObject asset,int reader)throws Exception {
        return new JSONObject().put("format",DriveRepository.FORMAT).put("version",1)
            .put("data",new JSONObject().put("format","bd.fdr.vault.entries").put("formatVersion",1).put("minReaderVersion",reader)
                .put("version",1).put("scope","all").put("assets",new JSONArray().put(asset)).put("paid",new JSONObject()));
    }
    private void rejected(JSONObject backup)throws Exception {
        try { DriveRepository.validate(backup.toString());fail("Unsupported interest data was accepted"); }
        catch(java.io.IOException|JSONException expected){}
    }
    @Test public void simpleDefaultsAndCompoundReaderSixAreAccepted()throws Exception {
        DriveRepository.validate(backup(asset(),5).toString());
        DriveRepository.validate(backup(asset().put("interestType","simple"),5).toString());
        DriveRepository.validate(backup(asset().put("interestType","compound"),6).toString());
        JSONObject legacy=backup(asset(),5);legacy.getJSONObject("data").remove("minReaderVersion");DriveRepository.validate(legacy.toString());
    }
    @Test public void unsupportedTypesAndCompatibilityDowngradesAreRejected()throws Exception {
        for(Object type:new Object[]{"", "Simple", "variable", 1, true, JSONObject.NULL})rejected(backup(asset().put("interestType",type),6));
        rejected(backup(asset().put("interestType","compound"),5));
        JSONObject absent=backup(asset().put("interestType","compound"),6);absent.getJSONObject("data").remove("minReaderVersion");rejected(absent);
        for(Object reader:new Object[]{0,8,1.5,"6",JSONObject.NULL}){
            JSONObject invalid=backup(asset(),5);invalid.getJSONObject("data").put("minReaderVersion",reader);rejected(invalid);
        }
    }
    @Test public void compoundDepositsRequireExplicitTenureAndCalendarDatesMustMatch()throws Exception {
        JSONObject compound=asset().put("interestType","compound");
        for(String field:new String[]{"tenureCount","tenureUnit"}){
            JSONObject missing=new JSONObject(compound.toString());missing.remove(field);rejected(backup(missing,6));
        }
        for(Object count:new Object[]{0,-1,1.5,"1",true,JSONObject.NULL,36526})rejected(backup(new JSONObject(compound.toString()).put("tenureCount",count),6));
        for(Object unit:new Object[]{"","Years","weeks",1,true,JSONObject.NULL})rejected(backup(new JSONObject(compound.toString()).put("tenureUnit",unit),6));
        rejected(backup(new JSONObject(compound.toString()).put("tenureCount",2),6));
        JSONObject leap=asset().put("interestType","compound").put("start","2024-01-01").put("first","2025-01-01").put("maturity","2025-01-01").put("tenureCount",12).put("tenureUnit","months");
        DriveRepository.validate(backup(leap,6).toString());
        DriveRepository.validate(backup(new JSONObject(leap.toString()).put("tenureCount",1).put("tenureUnit","years"),6).toString());
        DriveRepository.validate(backup(new JSONObject(leap.toString()).put("tenureCount",366).put("tenureUnit","days"),6).toString());
        rejected(backup(new JSONObject(leap.toString()).put("tenureCount",365).put("tenureUnit","days"),6));
        JSONObject legacy=asset();legacy.remove("tenureCount");legacy.remove("tenureUnit");DriveRepository.validate(backup(legacy,5).toString());
    }
    @Test public void nativeRegistryKeepsInterestTypeAndScheduleAtomic()throws Exception {
        JSONObject before=backup(asset(),5),after=backup(asset().put("interestType","compound"),6);
        JSONObject initial=SyncRegistry.create(before,"seed",1),converted=SyncRegistry.change(initial,before,after,"phone",10);
        JSONObject entries=converted.getJSONObject("entries");
        for(String field:new String[]{"type","interestType","cycle","cycleUnit","start","first","maturity","autoRenew","renewalStop","tenureCount","tenureUnit"}){
            JSONArray stamp=entries.getJSONObject("a/interest/"+field).getJSONArray("s");
            assertEquals(10,stamp.getLong(0));assertEquals("phone",stamp.getString(2));
        }
        JSONObject renamed=backup(asset().put("name","New name"),5),concurrent=SyncRegistry.change(initial,before,renamed,"web",11);
        JSONObject merged=SyncRegistry.merge(converted,concurrent),restored=SyncRegistry.backup(merged);
        JSONObject asset=restored.getJSONObject("data").getJSONArray("assets").getJSONObject(0);
        assertEquals("compound",asset.getString("interestType"));assertEquals("New name",asset.getString("name"));
        assertEquals(6,restored.getJSONObject("data").getInt("minReaderVersion"));DriveRepository.validate(restored.toString());
    }
    @Test public void liveCompoundRecordsCannotHaveTheirReaderRequirementDowngraded()throws Exception {
        JSONObject document=SyncRegistry.create(backup(asset().put("interestType","compound"),6),"phone",1);
        document.getJSONObject("entries").put("p/minReaderVersion",new JSONObject().put("s",new JSONArray().put(99).put(0).put("older-reader")).put("v",5));
        JSONObject guarded=SyncRegistry.backup(document);assertEquals(6,guarded.getJSONObject("data").getInt("minReaderVersion"));DriveRepository.validate(guarded.toString());
        JSONObject empty=backup(asset(),5);empty.getJSONObject("data").put("assets",new JSONArray());
        JSONObject deleted=SyncRegistry.change(document,guarded,empty,"phone",100),finalBackup=SyncRegistry.backup(deleted);
        assertEquals(0,finalBackup.getJSONObject("data").getJSONArray("assets").length());assertEquals(5,finalBackup.getJSONObject("data").getInt("minReaderVersion"));
        DriveRepository.validate(finalBackup.toString());
    }
}
