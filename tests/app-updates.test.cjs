const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'../app/assets/index.html'),'utf8');
const elements=new Map(),get=id=>{
 if(!elements.has(id))elements.set(id,{innerHTML:'',textContent:'',value:'',style:{},dataset:{},open:false,
 setAttribute(k,v){this[k]=v},insertAdjacentHTML(where,h){this.innerHTML=where==='beforeend'?this.innerHTML+h:h+this.innerHTML},showModal(){this.open=true},close(){this.open=false}});
 return elements.get(id);
};
let saved='',status={available:true,phase:'idle',installedVersionName:'1.3.1 Build 7',installedVersionCode:215};
const calls=[];
const ctx={console,Date,Math,JSON,Number,String,Set,Array,Object,Error,Blob,URL,setTimeout:()=>0,confirm:()=>true,scrollTo(){},
 document:{title:'',getElementById:get,querySelector:s=>get(s),body:{dataset:{},classList:{toggle(){}}}},
 localStorage:{getItem:()=>saved,setItem:(k,v)=>saved=v},Vault:{load:()=>saved,save:s=>{saved=s;return true}},
 AppUpdates:{getStatus:()=>JSON.stringify(status),check(){calls.push('check');status={...status,phase:'checking'}},download(){calls.push('download');status={...status,phase:'downloading'}},install(){calls.push('install');status={...status,phase:'permission'}},cancel(){calls.push('cancel');status={...status,phase:'available',downloadedBytes:0}}}};
ctx.window=ctx;vm.createContext(ctx);
for(const script of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))vm.runInContext(script[1],ctx);
const run=code=>vm.runInContext(code,ctx),screen=()=>get('main').innerHTML,card=()=>get('appUpdates').innerHTML;
function notify(next){status={...status,...next};ctx.onAppUpdateStatus(JSON.stringify(status));}
run("go('Settings')");
assert.deepEqual(calls,['check'],'First Settings visit checks automatically');
assert.match(card(),/Checking for updates/);
run("go('Home');go('Settings');settings()");
assert.deepEqual(calls,['check'],'Re-renders and navigation do not start duplicate checks');
notify({phase:'current'});
assert.match(card(),/You’re up to date/);
assert.match(card(),/Installed: 1.3.1 Build 7/);
assert.match(card(),/Check for updates/);
const dataBefore=run('JSON.stringify(state)');
notify({phase:'available',versionName:'1.3.1 Build 8',versionCode:216,apkSize:3145728,totalBytes:3145728,releaseNotes:'New <img src=x onerror=alert(1)> & improvements'});
assert.match(card(),/Latest: 1.3.1 Build 8/);
assert.match(card(),/Download update · 3.0 MB/);
assert.match(card(),/Check for updates/,'An available update can be refreshed when a newer release appears');
assert.match(card(),/&lt;img/,'Release notes are escaped');
assert.doesNotMatch(card(),/<img src=x/);
run("requestAppUpdate('download')");
notify({phase:'downloading',downloadedBytes:1572864});
assert.match(card(),/1.5 MB of 3.0 MB/);
assert.match(card(),/max="3145728" value="1572864"/);
assert.match(card(),/Cancel download/);
run("go('Home')");const home=screen();
notify({phase:'ready'});
assert.equal(screen(),home,'Off-screen progress never replaces the active page');
run("go('Settings')");assert.match(screen(),/Install update/);
assert.match(screen(),/Check for updates/,'A saved download does not prevent checking for a newer release');
run("requestAppUpdate('install')");
assert.match(card(),/Allow installation/);
notify({phase:'installing'});assert.match(card(),/Open installer again/);
notify({phase:'error',message:'Connection lost. Check your internet connection and try again.'});
assert.match(card(),/Connection lost/);assert.match(card(),/Try again/);
run("requestAppUpdate('check');requestAppUpdate('download');requestAppUpdate('cancel')");
assert.equal(calls.at(-1),'cancel');assert.match(card(),/Download update/);
assert.equal(run('JSON.stringify(state)'),dataBefore,'Update actions do not change user records');
run("delete window.AppUpdates;appUpdateStatus={available:false,phase:'idle'};settings()");
assert.match(screen(),/App updates are available in the installed Android app/);
assert.doesNotMatch(run('appUpdateBody()'),/onclick=/,'Browser previews do not expose unusable native actions');
console.log('PASS: Settings update check, progress/cancel/retry, safe release notes, permission/install states, navigation and saved-record preservation.');
