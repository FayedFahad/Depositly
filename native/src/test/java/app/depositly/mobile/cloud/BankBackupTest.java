package app.depositly.mobile.cloud;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;

public class BankBackupTest {
    private JSONObject account()throws Exception {
        return new JSONObject().put("id","bank1").put("bankId","ab-bank-plc").put("bankName","AB Bank PLC")
            .put("accountTitle","Sample Holder").put("accountNumber","00123456789").put("branchName","Main branch")
            .put("routingNumber","000123456").put("accountType","Savings");
    }
    private JSONObject backup(JSONArray accounts)throws Exception {
        return new JSONObject().put("format",DriveRepository.FORMAT).put("version",1)
            .put("data",new JSONObject().put("scope","all").put("assets",new JSONArray()).put("paid",new JSONObject()).put("bankAccounts",accounts));
    }
    private void rejected(JSONObject backup)throws Exception {
        try { DriveRepository.validate(backup.toString());fail("Invalid bank account was accepted"); }
        catch(java.io.IOException|JSONException expected){}
    }
    @Test public void bankIdentifiersRemainTextAndAccountTypeIsOptional()throws Exception {
        JSONObject account=account();JSONObject backup=backup(new JSONArray().put(account));
        DriveRepository.validate(backup.toString());
        JSONObject restored=new JSONObject(backup.toString()).getJSONObject("data").getJSONArray("bankAccounts").getJSONObject(0);
        assertEquals("00123456789",restored.getString("accountNumber"));assertEquals("000123456",restored.getString("routingNumber"));
        account.remove("accountType");DriveRepository.validate(backup(new JSONArray().put(account)).toString());
        account.remove("bankId");DriveRepository.validate(backup(new JSONArray().put(account)).toString());
        account.put("accountNumber","AB-001 234");DriveRepository.validate(backup(new JSONArray().put(account)).toString());
        JSONObject legacy=backup(new JSONArray());legacy.getJSONObject("data").remove("bankAccounts");DriveRepository.validate(legacy.toString());
    }
    @Test public void numericAndEmptyRequiredFieldsAreRejected()throws Exception {
        for(String field:new String[]{"bankName","accountTitle","accountNumber","branchName","routingNumber"}){
            for(Object value:new Object[]{123,"","   ",JSONObject.NULL})rejected(backup(new JSONArray().put(account().put(field,value))));
            JSONObject missing=account();missing.remove(field);rejected(backup(new JSONArray().put(missing)));
        }
    }
    @Test public void multilineAndControlCharactersAreRejected()throws Exception {
        for(String field:new String[]{"bankId","bankName","accountTitle","accountNumber","branchName","routingNumber","accountType"}){
            for(char control:new char[]{'\n','\r','\t',0,0x7f,0x85,0x2028,0x2029})
                rejected(backup(new JSONArray().put(account().put(field,"before"+control+"after"))));
        }
    }
    @Test public void duplicateIdsAndOversizedListsAreRejected()throws Exception {
        rejected(backup(new JSONArray().put(account()).put(account())));
        JSONArray accounts=new JSONArray();for(int i=0;i<501;i++)accounts.put(account().put("id","bank"+i));
        rejected(backup(accounts));
    }
    @Test public void nativeRegistryPreservesBankAccountsAcrossEditsAndDeletion()throws Exception {
        JSONObject original=backup(new JSONArray().put(account())),initial=SyncRegistry.create(original,"phone",1);
        JSONObject restored=SyncRegistry.backup(initial);DriveRepository.validate(restored.toString());
        assertTrue(SyncRegistry.equal(original.getJSONObject("data").getJSONArray("bankAccounts"),restored.getJSONObject("data").getJSONArray("bankAccounts")));
        assertEquals("00123456789",restored.getJSONObject("data").getJSONArray("bankAccounts").getJSONObject(0).getString("accountNumber"));
        JSONObject edited=backup(new JSONArray().put(account().put("branchName","New branch")));
        JSONObject changed=SyncRegistry.change(initial,original,edited,"phone",2),changedBackup=SyncRegistry.backup(changed);
        assertEquals("New branch",changedBackup.getJSONObject("data").getJSONArray("bankAccounts").getJSONObject(0).getString("branchName"));
        assertEquals("000123456",changedBackup.getJSONObject("data").getJSONArray("bankAccounts").getJSONObject(0).getString("routingNumber"));
        JSONObject deleted=SyncRegistry.change(changed,changedBackup,backup(new JSONArray()),"phone",3);
        JSONObject finalBackup=SyncRegistry.backup(SyncRegistry.merge(initial,changed,deleted));
        DriveRepository.validate(finalBackup.toString());assertEquals(0,finalBackup.getJSONObject("data").getJSONArray("bankAccounts").length());
    }
}
