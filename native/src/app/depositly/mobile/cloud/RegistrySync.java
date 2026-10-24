package app.depositly.mobile.cloud;

import android.content.Context;
import org.json.*;

/** Serializes Drive operations while local edits continue under RegistryStorage's short lock. */
public final class RegistrySync {
    private RegistrySync() {}
    public static synchronized boolean run(Context c,DriveRepository drive,String sub)throws Exception {
        if(!sub.equals(RegistryStorage.owner(c)))return false;
        JSONObject remote=drive.registry(RegistryStorage.heads(c,sub));
        JSONObject sent;JSONArray heads=remote.getJSONArray("heads");boolean upload;
        synchronized(RegistryStorage.class){
            if(!sub.equals(RegistryStorage.owner(c)))return false;
            JSONObject local=RegistryStorage.document(c,sub),incoming=remote.optJSONObject("backup"),remoteDoc=incoming==null?null:SyncRegistry.fromBackup(incoming,new JSONObject());
            if(local!=null&&remoteDoc!=null&&RegistryStorage.legacyPending(c,sub))local=SyncRegistry.change(remoteDoc,incoming,SyncRegistry.backup(local),RegistryStorage.actor(c,sub),System.currentTimeMillis());
            if(local==null)local=remoteDoc!=null?remoteDoc:SyncRegistry.create(RegistryStorage.seed(c,sub),RegistryStorage.actor(c,sub),0);
            sent=SyncRegistry.merge(local,remoteDoc);
            upload=remoteDoc!=null?!SyncRegistry.equal(sent,remoteDoc)||heads.length()>1:RegistryStorage.dirty(c,sub)||heads.length()==0;
            RegistryStorage.persist(c,sub,sent,heads,upload);
        }
        if(upload){
            JSONObject file=drive.saveVersion(SyncRegistry.backup(sent),heads);
            synchronized(RegistryStorage.class){
                if(!sub.equals(RegistryStorage.owner(c)))return false;
                JSONObject current=RegistryStorage.document(c,sub);JSONArray next=new JSONArray().put(file.getString("id"));for(int i=24;i<heads.length();i++)next.put(heads.get(i));
                RegistryStorage.persist(c,sub,current,next,!SyncRegistry.equal(current,sent));
                c.getSharedPreferences("google-drive-backup",0).edit().putLong("lastBackup",System.currentTimeMillis()).apply();
                if(RegistryStorage.dirty(c,sub))RegistrySyncJob.schedule(c);
            }
        }
        return true;
    }
}
