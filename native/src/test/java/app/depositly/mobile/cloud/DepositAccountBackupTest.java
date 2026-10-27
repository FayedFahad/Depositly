package app.depositly.mobile.cloud;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;

public class DepositAccountBackupTest {
    private JSONObject asset()throws Exception {
        return new JSONObject().put("id","deposit").put("type","FDR").put("name","Sample deposit").put("principal",10000000)
            .put("rate",10).put("tds",10).put("cycle",12).put("cycleUnit","months").put("start","2026-01-01")
            .put("first","2027-01-01").put("maturity","2027-01-01").put("tenureCount",1).put("tenureUnit","years").put("autoRenew",false);
    }
    private JSONObject snapshot()throws Exception {
        return new JSONObject().put("assetId","deposit").put("name","Sample deposit").put("type","FDR").put("scheduledDate","2027-01-01")
            .put("kind","Interest").put("gross",1000000).put("tax",100000).put("expected",900000).put("principal",0)
            .put("bankAccountId","bank-alpha").put("bankAccountSnapshot",new JSONObject().put("bankName","Sample Bank").put("accountTitle","Sample Holder").put("accountNumber","000001234567"));
    }
    private JSONObject backup(JSONObject asset,int reader)throws Exception {
        return new JSONObject().put("format",DriveRepository.FORMAT).put("version",1).put("data",new JSONObject()
            .put("format","bd.fdr.vault.entries").put("formatVersion",1).put("minReaderVersion",reader).put("version",1).put("scope","all")
            .put("assets",new JSONArray().put(asset)).put("paid",new JSONObject()).put("bankAccounts",new JSONArray()));
    }
    private JSONObject withReceipt(JSONObject backup,JSONObject snapshot)throws Exception {
        backup.getJSONObject("data").getJSONObject("paid").put("deposit:2027-01-01",new JSONObject().put("amount",900000).put("date","2027-01-01").put("notes","Original receipt").put("snapshot",snapshot));return backup;
    }
    private void rejected(JSONObject backup)throws Exception {
        try{DriveRepository.validate(backup.toString());fail("Invalid linked-account backup accepted");}catch(java.io.IOException|JSONException expected){}
    }
    @Test public void linksAndReceiptHistoryRequireReaderSevenWhileLegacyRemainsReadable()throws Exception {
        DriveRepository.validate(backup(asset(),5).toString());
        JSONObject linked=backup(asset().put("bankAccountId","bank-alpha"),7);DriveRepository.validate(linked.toString());
        assertEquals(0,linked.getJSONObject("data").getJSONArray("bankAccounts").length());
        rejected(backup(asset().put("bankAccountId","bank-alpha"),6));
        JSONObject missing=backup(asset().put("bankAccountId","bank-alpha"),7);missing.getJSONObject("data").remove("minReaderVersion");rejected(missing);
        rejected(backup(asset(),8));
        DriveRepository.validate(withReceipt(backup(asset(),7),snapshot()).toString());
        rejected(withReceipt(backup(asset(),6),snapshot()));
        JSONObject idOnly=snapshot();idOnly.remove("bankAccountSnapshot");DriveRepository.validate(withReceipt(backup(asset(),7),idOnly).toString());
    }
    @Test public void linkIdsAndReceivingHistoryRejectMalformedSingleLineFields()throws Exception {
        for(Object id:new Object[]{"", "bad/id", "with space", 7, true, JSONObject.NULL, new String(new char[101]).replace('\0','a')}){
            rejected(backup(asset().put("bankAccountId",id),7));rejected(withReceipt(backup(asset(),7),snapshot().put("bankAccountId",id)));
        }
        JSONObject orphan=snapshot();orphan.remove("bankAccountId");rejected(withReceipt(backup(asset(),7),orphan));
        rejected(withReceipt(backup(asset(),7),snapshot().put("bankAccountSnapshot",JSONObject.NULL)));
        for(String field:new String[]{"bankName","accountTitle","accountNumber"})for(Object value:new Object[]{"", "  ", "bad\nline", 9, true, JSONObject.NULL}){
            JSONObject history=snapshot();history.getJSONObject("bankAccountSnapshot").put(field,value);rejected(withReceipt(backup(asset(),7),history));
        }
        JSONObject longNumber=snapshot();longNumber.getJSONObject("bankAccountSnapshot").put("accountNumber",new String(new char[101]).replace('\0','0'));rejected(withReceipt(backup(asset(),7),longNumber));
    }
    @Test public void concurrentNamesKeepLinksAndLiveHistoryCannotBeDowngraded()throws Exception {
        JSONObject before=backup(asset(),5),after=backup(asset().put("bankAccountId","bank-alpha"),7),initial=SyncRegistry.create(before,"seed",1);
        JSONObject assigned=SyncRegistry.change(initial,before,after,"phone",10),renamed=backup(asset().put("name","Updated name"),5);
        JSONObject merged=SyncRegistry.merge(assigned,SyncRegistry.change(initial,before,renamed,"web",11));
        merged.getJSONObject("entries").put("p/minReaderVersion",new JSONObject().put("s",new JSONArray().put(99).put(0).put("older-client")).put("v",5));
        JSONObject restored=SyncRegistry.backup(merged),row=restored.getJSONObject("data").getJSONArray("assets").getJSONObject(0);
        assertEquals("Updated name",row.getString("name"));assertEquals("bank-alpha",row.getString("bankAccountId"));assertEquals(7,restored.getJSONObject("data").getInt("minReaderVersion"));DriveRepository.validate(restored.toString());
        JSONObject history=withReceipt(backup(asset(),7),snapshot()),historyDoc=SyncRegistry.create(history,"phone",1);
        historyDoc.getJSONObject("entries").put("p/minReaderVersion",new JSONObject().put("s",new JSONArray().put(99).put(0).put("older-client")).put("v",5));
        JSONObject kept=SyncRegistry.backup(historyDoc);assertEquals(7,kept.getJSONObject("data").getInt("minReaderVersion"));
        assertEquals("000001234567",kept.getJSONObject("data").getJSONObject("paid").getJSONObject("deposit:2027-01-01").getJSONObject("snapshot").getJSONObject("bankAccountSnapshot").getString("accountNumber"));
        DriveRepository.validate(kept.toString());
        JSONObject empty=backup(asset(),5),removed=SyncRegistry.change(historyDoc,kept,empty,"phone",100);
        assertEquals(5,SyncRegistry.backup(removed).getJSONObject("data").getInt("minReaderVersion"));
    }
}
