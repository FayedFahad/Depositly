const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'../app/assets/index.html'),'utf8');
let stored=JSON.stringify({version:2,assets:[],paid:{},dark:false}),saveAllowed=true;
function launch(native=true){
 const nodes=new Map(),get=id=>{
  if(!nodes.has(id))nodes.set(id,{innerHTML:'',textContent:'',value:'',style:{},dataset:{},open:false,setAttribute(k,v){this[k]=v},insertAdjacentHTML(w,h){this.innerHTML=w==='beforeend'?this.innerHTML+h:h+this.innerHTML},showModal(){this.open=true},close(){this.open=false}});
  return nodes.get(id);
 };
 const blocked=()=>{throw new Error('DOM storage is disabled in Android WebView')};
 const ctx={console,Date,Math,JSON,Number,String,Set,Array,Object,Error,Blob,URL,setTimeout:()=>0,confirm:()=>true,scrollTo(){},document:{getElementById:get,querySelector:get,body:{dataset:{},classList:{toggle(){}}}},localStorage:native?{getItem:blocked,setItem:blocked}:{getItem:()=>stored,setItem:(k,v)=>{stored=v}}};
 if(native)ctx.Vault={load:()=>stored,save:s=>{if(!saveAllowed)return false;stored=s;return true}};
 ctx.window=ctx;vm.createContext(ctx);
 for(const m of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))vm.runInContext(m[1],ctx);
 return {run:s=>vm.runInContext(s,ctx),get};
}
let app=launch();
assert.equal(app.run('defaultHoldingView()'),'card','Older saved data defaults to card view');
app.run("go('Settings');setDefaultHoldingView('grid')");
assert.equal(JSON.parse(stored).defaultDepositView,'grid','Android stores the setting through Vault without DOM storage');
app=launch();app.run("go('Portfolio')");
assert.match(app.get('main').innerHTML,/id="holdings" class="holdings-grid"/,'Grid default survives relaunch');
app.run("setHoldingView('card');go('Overview');go('Portfolio')");
assert.equal(app.run('holdingView'),'grid','Temporary switch does not overwrite the default');
app.run("go('Settings')");saveAllowed=false;app.get('defaultDepositView').value='card';app.run("setDefaultHoldingView('card')");
assert.equal(app.run('defaultHoldingView()'),'grid','Failed native save leaves the preference unchanged');
assert.equal(app.get('defaultDepositView').value,'grid','Failed save resets the select');
assert.equal(JSON.parse(stored).defaultDepositView,'grid');saveAllowed=true;
app.run("setDefaultHoldingView('other')");assert.equal(app.run('defaultHoldingView()'),'grid');
app.run("setDefaultHoldingView('card')");app=launch();assert.equal(app.run('defaultHoldingView()'),'card');
app.run("setDefaultHoldingView('grid');globalThis.backupData=Transfer.exportData(state)");
assert.equal(app.run('Transfer.migrate(backupData).defaultDepositView'),'grid','JSON backup restores the view preference');
app.run("commit({...state,defaultDepositView:'card'});go('Overview');go('Portfolio')");
assert.equal(app.run('holdingView'),'card','Navigation reflects a restored preference');
app.run("setDefaultHoldingView('grid');restoreData(Transfer.instrumentCSV(state));applyImport('replace')");
assert.equal(app.run('defaultHoldingView()'),'grid','Instrument-only CSV replacement preserves the preference');
app=launch(false);app.run("go('Settings');setDefaultHoldingView('card')");app=launch(false);
assert.equal(app.run('defaultHoldingView()'),'card','Browser previews also persist through the main state');
console.log('PASS: Android Vault persistence with DOM storage disabled; relaunch, temporary overrides, save failures, invalid values, JSON restore, CSV preservation and browser persistence.');
