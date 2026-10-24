package app.depositly.mobile.cloud;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.*;
import android.webkit.*;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.GoogleApiAvailability;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Tokens remain native. Routine synchronization never opens a dialog or sends a toast. */
public final class DriveBackupBridge {
    private static final int AUTHORIZE=2301;
    private final Activity activity;
    private final WebView web;
    private final AuthorizationClient client;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile String status="{}";
    private boolean busy,resolving,pageReady,dead,foreground=true;
    private int generation;
    private String message="";
    private String pendingAction="",pendingArgument="",pendingSub="";
    private final Runnable poll=new Runnable(){public void run(){start("sync","",false);if(!dead&&foreground)main.postDelayed(this,15000);}};
    public DriveBackupBridge(Activity activity,WebView web,Bundle saved){
        this.activity=activity;this.web=web;prefs=activity.getSharedPreferences("google-drive-backup",0);client=Identity.getAuthorizationClient(activity);
        try{if(!sub().isEmpty())RegistryStorage.connect(activity,sub(),prefs.getString("email",""));}catch(Exception e){message="Could not open your saved account. Free some storage and reopen Depositly.";}
        if(saved!=null&&saved.getBoolean("cloudResolving")){resolving=busy=true;}
        refresh();
    }
    private String sub(){return RegistryStorage.owner(activity);}
    private void emit(String function,String value){if(pageReady&&!dead)web.evaluateJavascript("window."+function+"&&window."+function+"("+JSONObject.quote(value)+")",null);}
    private void refresh(){try{status=new JSONObject().put("available",GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)==0).put("signedIn",!sub().isEmpty()).put("sub",sub()).put("email",prefs.getString("email","")).put("ready",!sub().isEmpty()).put("automatic",true).put("busy",busy).put("message",message).put("lastBackup",prefs.getLong("lastBackup",0)).toString();emit("onCloudStatus",status);}catch(Exception ignored){}}
    private void changed(){emit("onRegistryChanged",sub());}
    @JavascriptInterface public String getStatus(){return status;}
    @JavascriptInterface public void signIn(){main.post(()->start("connect","",true));}
    @JavascriptInterface public void restore(){main.post(()->start("sync","",false));}
    @JavascriptInterface public void history(String page){main.post(()->start("history",page,false));}
    @JavascriptInterface public void restoreVersion(String id){main.post(()->start("version",id,false));}
    @JavascriptInterface public void queueRegistryBackup(String payload,String expected){main.post(()->{try{if(!expected.equals(sub())||expected.isEmpty())return;RegistryStorage.payload(activity,payload,expected);RegistrySyncJob.schedule(activity);start("sync","",false);}catch(Exception e){fail("Could not save sync settings. Export a file backup and try again.");}});}
    @JavascriptInterface public void queueBackup(String payload){queueRegistryBackup(payload,sub());}
    @JavascriptInterface public void backupNow(String payload){queueRegistryBackup(payload,sub());}
    @JavascriptInterface public void signOut(){main.post(()->{synchronized(RegistryStorage.class){generation++;busy=resolving=false;prefs.edit().remove("sub").remove("email").remove("readySub").commit();}message="";refresh();changed();});}
    @JavascriptInterface public void disconnect(){main.post(()->{if(busy||sub().isEmpty())return;client.revokeAccess(RevokeAccessRequest.builder().setAccount(new Account(prefs.getString("email",""),"com.google")).setScopes(RegistrySyncJob.SCOPES).build()).addOnSuccessListener(activity,v->signOut()).addOnFailureListener(activity,e->fail("Could not remove Google access. Try again."));});}
    private void start(String action,String arg,boolean interactive){
        if(dead||(!interactive&&sub().isEmpty()))return;
        if(busy){if(action.equals("history")||action.equals("version")){pendingAction=action;pendingArgument=arg;pendingSub=sub();}return;}
        busy=true;refresh();final int run=generation;
        if(!interactive){final String expected=sub();worker.execute(()->{try{execute(RegistrySyncJob.authorize(activity,expected),expected,action,arg,run);}catch(Exception e){failed(e,run);}});return;}
        client.authorize(AuthorizationRequest.builder().setRequestedScopes(RegistrySyncJob.SCOPES).setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT).build()).addOnSuccessListener(activity,result->{
            if(dead||run!=generation)return;
            if(result.hasResolution()){try{resolving=true;activity.startIntentSenderForResult(result.getPendingIntent().getIntentSender(),AUTHORIZE,null,0,0,0);}catch(Exception e){fail("Could not open Google sign-in. Try again.");}}
            else authorized(result,run);
        }).addOnFailureListener(activity,e->fail("Google sign-in failed. Check your connection and try again."));
    }
    private void authorized(AuthorizationResult auth,int run){
        if(auth.getAccessToken()==null){fail("Allow Google Drive access to continue.");return;}
        worker.execute(()->{try{
            DriveRepository drive=new DriveRepository(DriveRepository.authenticated(auth.getAccessToken()));JSONObject user=drive.user();String next=user.getString("sub");
            if(!user.optBoolean("email_verified")||next.isEmpty())throw new Exception("Unverified Google account.");
            synchronized(RegistryStorage.class){if(dead||run!=generation)return;RegistryStorage.connect(activity,next,user.getString("email"));}
            main.post(()->{if(!dead&&run==generation){refresh();changed();}});
            execute(drive,next,"sync","",run);
        }catch(Exception e){failed(e,run);}});
    }
    private void execute(DriveRepository drive,String expected,String action,String arg,int run)throws Exception {
        if(dead||run!=generation||!expected.equals(sub()))return;
        JSONObject versions=null;
        if(action.equals("history"))versions=drive.versions(arg);
        else {
            if(action.equals("version")){RegistrySync.run(activity,drive,expected);JSONObject version=drive.version(arg);synchronized(RegistryStorage.class){if(dead||run!=generation||!expected.equals(sub()))return;RegistryStorage.restore(activity,expected,version.getJSONObject("backup"));}}
            RegistrySync.run(activity,drive,expected);
        }
        final JSONObject result=versions;main.post(()->{if(dead||run!=generation||!expected.equals(sub()))return;busy=false;message="";refresh();if(result!=null)emit("onCloudVersions",result.toString());else changed();drain();});
    }
    private void failed(Exception e,int run){main.post(()->{if(dead||run!=generation)return;fail(e instanceof DriveRepository.HttpFailure?e.getMessage():"Sync will retry when a connection is available. Sign in again if Google access has expired.");RegistrySyncJob.schedule(activity);});}
    private void drain(){String action=pendingAction,arg=pendingArgument,expected=pendingSub;pendingAction="";if(!action.isEmpty()&&expected.equals(sub()))start(action,arg,false);}
    private void fail(String value){busy=resolving=false;message=value;refresh();drain();}
    public boolean activityResult(int request,int result,Intent data){if(request!=AUTHORIZE)return false;resolving=false;if(result!=Activity.RESULT_OK||data==null){fail("Google sign-in was canceled.");return true;}try{authorized(client.getAuthorizationResultFromIntent(data),generation);}catch(Exception e){fail("Google sign-in did not complete. Try again.");}return true;}
    public void pageReady(){pageReady=true;refresh();changed();if(foreground)resume();}
    public void resume(){if(dead)return;foreground=true;main.removeCallbacks(poll);main.post(poll);RegistrySyncJob.schedule(activity);if(pageReady)changed();}
    public void pause(){foreground=false;main.removeCallbacks(poll);}
    public void saveInstanceState(Bundle state){state.putBoolean("cloudResolving",resolving);}
    public void destroy(){dead=true;generation++;main.removeCallbacksAndMessages(null);worker.shutdownNow();}
}
