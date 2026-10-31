package app.depositly.mobile.cloud;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;

public class BankBalanceBackupTest {
    private JSONObject account()throws Exception {
        return new JSONObject().put("id","bank-one").put("bankName","Sample Bank").put("accountTitle","Sample Holder")
            .put("accountNumber","00012345").put("branchName","Main").put("routingNumber","00098765");
    }
    private JSONObject backup(JSONObject account)throws Exception {
        return new JSONObject().put("format",DriveRepository.FORMAT).put("version",1).put("data",new JSONObject()
            .put("format","bd.fdr.vault.entries").put("formatVersion",1).put("minReaderVersion",5).put("version",1).put("scope","all")
            .put("assets",new JSONArray()).put("paid",new JSONObject()).put("currency","BDT").put("bankAccounts",new JSONArray().put(account)));
    }
    private void rejected(JSONObject account)throws Exception {
        try{DriveRepository.validate(backup(account).toString());fail("Invalid bank balance accepted");}catch(java.io.IOException|JSONException expected){}
    }
    @Test public void legacyZeroAndMaximumMoneyRemainValid()throws Exception {
        DriveRepository.validate(backup(account()).toString());
        DriveRepository.validate(backup(account().put("balance",0)).toString());
        DriveRepository.validate(backup(account().put("balance",10000000000000L)).toString());
    }
    @Test public void balancesRejectInvalidTypesRangesAndFractions()throws Exception {
        for(Object value:new Object[]{-1,0.5,10000000000001L,"100",true,JSONObject.NULL})rejected(account().put("balance",value));
    }
    @Test public void registryEditAndRestorePreserveMoney()throws Exception {
        JSONObject before=backup(account()),after=backup(account().put("balance",1234567));
        JSONObject initial=SyncRegistry.create(before,"seed",1),edited=SyncRegistry.change(initial,before,after,"phone",2);
        JSONObject restored=SyncRegistry.backup(SyncRegistry.merge(initial,edited)),row=restored.getJSONObject("data").getJSONArray("bankAccounts").getJSONObject(0);
        assertEquals(1234567,row.getLong("balance"));assertEquals("00012345",row.getString("accountNumber"));
        DriveRepository.validate(restored.toString());
    }
}
