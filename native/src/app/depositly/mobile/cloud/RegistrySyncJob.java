package app.depositly.mobile.cloud;

import android.accounts.Account;
import android.app.job.*;
import android.content.*;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Quiet, persisted network retries; Android chooses background execution time. */
public final class RegistrySyncJob extends JobService {
    private static final int ONCE=7321,PERIODIC=7322;
    static final List<Scope> SCOPES=Arrays.asList(new Scope("openid"),new Scope("https://www.googleapis.com/auth/userinfo.email"),new Scope("https://www.googleapis.com/auth/userinfo.profile"),new Scope("https://www.googleapis.com/auth/drive.appdata"));
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final java.util.Set<Integer> stopped=ConcurrentHashMap.newKeySet();
    public static void schedule(Context c){
        if(RegistryStorage.owner(c).isEmpty())return;
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);ComponentName component=new ComponentName(c,RegistrySyncJob.class);
        scheduler.schedule(new JobInfo.Builder(ONCE,component).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(500).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).setPersisted(true).build());
        if(scheduler.getPendingJob(PERIODIC)==null)scheduler.schedule(new JobInfo.Builder(PERIODIC,component).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).build());
    }
    static DriveRepository authorize(Context c,String sub)throws Exception {
        String email=c.getSharedPreferences("google-drive-backup",0).getString("email","");
        AuthorizationResult auth=Tasks.await(Identity.getAuthorizationClient(c).authorize(AuthorizationRequest.builder().setRequestedScopes(SCOPES).setAccount(new Account(email,"com.google")).build()),45,TimeUnit.SECONDS);
        if(auth.hasResolution()||auth.getAccessToken()==null)throw new Exception("Sign in again to resume sync.");
        DriveRepository.Transport transport=DriveRepository.authenticated(auth.getAccessToken());
        DriveRepository drive=new DriveRepository((method,path,type,body)->{try{return transport.request(method,path,type,body);}catch(DriveRepository.HttpFailure e){if(e.code==401)Identity.getAuthorizationClient(c).clearToken(ClearTokenRequest.builder().setToken(auth.getAccessToken()).build());throw e;}});JSONObject user=drive.user();
        if(!user.optBoolean("email_verified")||!sub.equals(user.optString("sub"))||!sub.equals(RegistryStorage.owner(c)))throw new Exception("Account changed.");
        return drive;
    }
    @Override public boolean onStartJob(JobParameters params){stopped.remove(params.getJobId());worker.execute(()->{boolean retry=false;try{String sub=RegistryStorage.owner(this);if(!sub.isEmpty())RegistrySync.run(this,authorize(this,sub),sub);}catch(Exception e){retry=true;}if(!stopped.contains(params.getJobId()))jobFinished(params,retry);});return true;}
    @Override public boolean onStopJob(JobParameters params){stopped.add(params.getJobId());return true;}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
