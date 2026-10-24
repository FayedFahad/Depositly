package app.depositly.mobile.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import app.depositly.mobile.reminders.ReminderEngine;
import java.security.MessageDigest;
import java.util.UUID;
import org.json.*;

/** Atomic local commits, isolated by verified Google subject. Network never holds this lock. */
public final class RegistryStorage {
    private RegistryStorage() {}
    public static String owner(Context c) { return c.getSharedPreferences("google-drive-backup",0).getString("sub",""); }
    private static SharedPreferences prefs(Context c,String sub) {
        if(sub.isEmpty())return c.getSharedPreferences("vault",0);
        try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(sub.getBytes("UTF-8"));StringBuilder key=new StringBuilder("registry.");for(byte b:bytes)key.append(String.format("%02x",b));return c.getSharedPreferences(key.toString(),0); }
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static synchronized void connect(Context c,String sub,String email)throws Exception {
        SharedPreferences linked=c.getSharedPreferences("google-drive-backup",0),p=prefs(c,sub);
        // Upgrade only the previously linked account; never copy another account's vault.
        if(!p.contains("data")&&(sub.equals(linked.getString("sub",""))||!linked.getBoolean("everLinked",false)&&linked.getString("sub","").isEmpty())) {
            String data=c.getSharedPreferences("vault",0).getString("data","");
            if(!data.isEmpty()&&!p.edit().putString("data",data).putBoolean("legacy",true).commit())throw new Exception("Could not migrate local records.");
            if(sub.equals(linked.getString("sub",""))){
                String pending=linked.getString("payload","");
                if(linked.getBoolean("dirty",false)&&!pending.isEmpty()){
                    DriveRepository.validate(pending);JSONObject doc=SyncRegistry.create(new JSONObject(pending),actor(c,sub),System.currentTimeMillis());
                    if(!p.edit().putString("doc",doc.toString()).putString("data",SyncRegistry.backup(doc).getJSONObject("data").toString()).putBoolean("dirty",true).putBoolean("legacyPending",true).commit())throw new Exception("Could not migrate pending edits.");
                }
                // Its scoped copy is durable before removing the old account's signed-out view.
                if(!data.isEmpty())c.getSharedPreferences("vault",0).edit().remove("data").commit();
            }
        }
        if(!linked.edit().putString("sub",sub).putString("email",email).putString("readySub",sub).putBoolean("everLinked",true).commit())throw new Exception("Could not save account.");
        JSONObject doc=document(c,sub);ReminderEngine.restoreSettings(c,doc==null?new JSONObject().put("enabled",false).put("time","09:00"):SyncRegistry.backup(doc).getJSONObject("notificationSettings"));
    }
    public static synchronized String load(Context c){return prefs(c,owner(c)).getString("data","");}
    static JSONObject data(String json)throws Exception {
        if(json.isEmpty())return new JSONObject().put("version",2).put("assets",new JSONArray()).put("paid",new JSONObject()).put("dark",false).put("currency","BDT").put("numberFormat","south-asian").put("defaultDepositView","card");
        return new JSONObject(json);
    }
    static JSONObject wrap(Context c,JSONObject state)throws Exception {
        JSONObject value=new JSONObject(state.toString());value.remove("exportedAt");
        value.put("format","bd.fdr.vault.entries").put("formatVersion",1).put("minReaderVersion",5).put("version",1).put("scope","all");
        JSONArray assets=value.getJSONArray("assets");for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);if("NSC".equals(a.optString("type")))a.put("type","Sanchaypatra");}
        JSONObject n=new JSONObject(ReminderEngine.status(c));
        return new JSONObject().put("format",DriveRepository.FORMAT).put("version",1).put("data",value).put("notificationSettings",new JSONObject().put("enabled",n.optBoolean("enabled")).put("time",n.optString("time","09:00")));
    }
    static String actor(Context c,String sub)throws Exception {
        SharedPreferences p=prefs(c,sub);String actor=p.getString("actor","");
        if(actor.isEmpty()){actor=UUID.randomUUID().toString();if(!p.edit().putString("actor",actor).commit())throw new Exception("Could not save device revision.");}return actor;
    }
    public static synchronized boolean save(Context c,String json,String expected,String baseline) {
        try {
            if(!owner(c).equals(expected))return false;
            SharedPreferences p=prefs(c,expected);JSONObject next=data(json),before=data(baseline);
            if(expected.isEmpty())return p.edit().putString("data",json).commit();
            JSONObject doc=document(c,expected);
            if(doc==null)doc=SyncRegistry.create(wrap(c,data(p.getString("data",""))),actor(c,expected),0);
            doc=SyncRegistry.change(doc,wrap(c,before),wrap(c,next),actor(c,expected),System.currentTimeMillis());
            persist(c,expected,doc,heads(c,expected),true);RegistrySyncJob.schedule(c);return true;
        }catch(Exception e){return false;}
    }
    public static synchronized void payload(Context c,String payload,String sub)throws Exception {
        if(!owner(c).equals(sub)||sub.isEmpty())return;
        DriveRepository.validate(payload);JSONObject after=new JSONObject(payload),doc=document(c,sub);
        if(doc==null)doc=SyncRegistry.create(wrap(c,data(load(c))),actor(c,sub),0);
        // Financial fields already commit through Vault; only reminder preferences come via this callback.
        JSONObject before=SyncRegistry.backup(doc);after.put("data",before.getJSONObject("data"));
        JSONObject changed=SyncRegistry.change(doc,before,after,actor(c,sub),System.currentTimeMillis());
        if(!SyncRegistry.equal(doc,changed))persist(c,sub,changed,heads(c,sub),true);
    }
    static synchronized JSONObject document(Context c,String sub)throws Exception {
        String value=prefs(c,sub).getString("doc","");return value.isEmpty()?null:SyncRegistry.validate(new JSONObject(value));
    }
    static synchronized JSONArray heads(Context c,String sub)throws Exception{return new JSONArray(prefs(c,sub).getString("heads","[]"));}
    static synchronized boolean dirty(Context c,String sub){return prefs(c,sub).getBoolean("dirty",false);}
    static synchronized boolean legacyPending(Context c,String sub){return prefs(c,sub).getBoolean("legacyPending",false);}
    static synchronized JSONObject seed(Context c,String sub)throws Exception {
        SharedPreferences p=prefs(c,sub);return wrap(c,data(p.getString("data","")));
    }
    static synchronized void persist(Context c,String sub,JSONObject doc,JSONArray heads,boolean dirty)throws Exception {
        if(!owner(c).equals(sub))throw new Exception("Account changed.");
        JSONObject backup=SyncRegistry.backup(doc);DriveRepository.validate(backup.toString());
        if(!prefs(c,sub).edit().putString("doc",doc.toString()).putString("data",backup.getJSONObject("data").toString()).putString("heads",heads.toString()).putBoolean("dirty",dirty).putBoolean("legacyPending",heads.length()==0&&legacyPending(c,sub)).commit())throw new Exception("Could not save synchronized records.");
        ReminderEngine.restoreSettings(c,backup.getJSONObject("notificationSettings"));
    }
    static synchronized void restore(Context c,String sub,JSONObject backup)throws Exception {
        DriveRepository.validate(backup.toString());JSONObject doc=document(c,sub);if(doc==null)doc=SyncRegistry.create(seed(c,sub),actor(c,sub),0);
        JSONObject changed=SyncRegistry.change(doc,SyncRegistry.backup(doc),backup,actor(c,sub),System.currentTimeMillis());persist(c,sub,changed,heads(c,sub),true);
    }
}
