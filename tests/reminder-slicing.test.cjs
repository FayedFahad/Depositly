const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'../app/assets/index.html'),'utf8');
const base={id:'renew',type:'FDR',name:'Renewing deposit',ownerName:'Sample Holder',ownership:'self',principal:10000000,rate:10,tds:10,cycle:1,cycleUnit:'days',start:'2026-10-12',first:'2026-10-13',maturity:'2026-10-13',tenureCount:1,tenureUnit:'days',autoRenew:true,interestType:'simple'};
function app(assets=[base]){
 const pending=[],uploads=[],enabled=[],nodes=new Map();let owner='account-a',now='2026-10-13',timer=0,planOK=true,stored=JSON.stringify({version:2,assets,paid:{},bankAccounts:[],currency:'BDT',numberFormat:'south-asian'});
 const get=id=>{if(!nodes.has(id))nodes.set(id,{innerHTML:'',textContent:'',value:'',style:{},dataset:{},open:false,setAttribute(k,v){this[k]=v},insertAdjacentHTML(w,h){this.innerHTML=w==='beforeend'?this.innerHTML+h:h+this.innerHTML},showModal(){this.open=true},close(){this.open=false}});return nodes.get(id)};
 class Clock extends Date{constructor(...args){super(...(args.length?args:[now+'T12:00:00']));}}
 const status={available:true,enabled:false,permissionGranted:true,time:'09:00',nextDate:''};
 const ctx={console,Date:Clock,Math,JSON,Number,String,Set,Map,Array,Object,Error,Blob,URL,setTimeout(callback,delay=0){if(!delay)pending.push(callback);return ++timer},confirm:()=>true,scrollTo(){},document:{getElementById:get,querySelector:get,body:{dataset:{},classList:{toggle(){}}}},Vault:{load:()=>stored,owner:()=>owner,saveForAccount(text,expected){if(expected!==owner)return false;stored=text;return true}},Reminders:{getStatus:()=>JSON.stringify(status),setPlan(text){if(!planOK)return false;const plan=JSON.parse(text);uploads.push({owner,plan});status.nextDate=plan[0]?.date||'';return true},setEnabled(value){enabled.push({value,uploads:uploads.length});status.enabled=value},setTime:()=>true}};
 ctx.window=ctx;vm.createContext(ctx);for(const script of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))vm.runInContext(script[1],ctx);
 const run=code=>vm.runInContext(code,ctx),step=()=>{assert(pending.length,'A continuation was queued');pending.shift()()},flush=()=>{let turns=0;while(pending.length){assert(++turns<100000,'Generation completes without an unbounded continuation loop');step()}return turns};
 return {run,step,flush,pending,uploads,enabled,status,get,setPlanOK(value){planOK=value},setOwner(value){owner=value},setDay(value){now=value},setState(assets){stored=JSON.stringify({version:2,assets,paid:{},bankAccounts:[],currency:'BDT',numberFormat:'south-asian'})}};
}
const a=app();assert.equal(a.uploads.length,0,'Startup does not synchronously build or send the full plan');
a.run('togglePayoutReminders()');assert.equal(a.enabled.length,0,'Enabling waits for a complete fresh plan');
a.step();assert.equal(a.uploads.length,0,'A bounded row batch yields before the full daily schedule completes');
let interaction=false;a.pending.splice(0,0,()=>interaction=true);a.step();assert(interaction,'An interaction gets a turn during generation');
const turns=a.flush();assert(turns>100,'A long full-horizon plan is split across many task turns');
assert.equal(a.uploads.length,1);assert.equal(a.enabled.length,1);assert(a.enabled[0].value);assert.equal(a.enabled[0].uploads,1,'The native plan is installed before notifications are enabled');
const plan=a.uploads[0].plan;assert.equal(plan[0].date,'2026-10-13');assert.equal(plan.at(-1).date,'2199-12-31','Offline renewal reminders retain the full existing horizon');
assert.equal(plan.length,a.run("FDR.schedule(state.assets[0],'2199-12-31').filter(p=>p.date>=today()&&p.net>0).length"));
assert(plan.every(p=>p.count===1));a.run('syncReminderPlan()');assert.equal(a.pending.length,0,'Unchanged financial identity skips rebuilding');

const changed=app();changed.run('togglePayoutReminders()');changed.step();changed.run("commit({...state,assets:state.assets.map(a=>({...a,id:'fresh',name:'Fresh deposit',autoRenew:false}))})");changed.flush();
assert.equal(changed.uploads.length,1,'A superseded partial plan is never uploaded');assert(changed.uploads[0].plan.every(p=>p.details.includes('Fresh deposit')));assert.equal(changed.enabled.filter(e=>e.value).length,1,'Pending enable follows the latest data for the same account');

const cancelled=app();cancelled.run('togglePayoutReminders();togglePayoutReminders()');cancelled.flush();assert(!cancelled.enabled.some(e=>e.value),'Toggling again cancels a pending enable');

const switched=app();switched.run('togglePayoutReminders()');switched.step();switched.setOwner('account-b');switched.flush();assert.equal(switched.uploads.length,0,'No old-account plan is uploaded after native ownership changes');assert(!switched.enabled.some(e=>e.value));
switched.setState([{...base,id:'new-account',name:'New account deposit',autoRenew:false}]);switched.run("onRegistryChanged('account-b')");switched.flush();assert.equal(switched.uploads.length,1);assert.equal(switched.uploads[0].owner,'account-b');assert(switched.uploads[0].plan.every(p=>p.details.includes('New account deposit')));assert(!switched.enabled.some(e=>e.value),'An old-account enable request never enables the new account');

const midnight=app();midnight.run('togglePayoutReminders()');midnight.step();midnight.setDay('2026-10-15');midnight.flush();assert.equal(midnight.uploads.length,1,'A date rollover cancels the old-date plan');assert.equal(midnight.uploads[0].plan[0].date,'2026-10-15');assert(midnight.enabled.some(e=>e.value));

const failed=app();failed.setPlanOK(false);failed.run('togglePayoutReminders()');failed.flush();assert.equal(failed.uploads.length,0);assert(!failed.enabled.some(e=>e.value),'A native plan failure never enables notifications');assert.match(failed.get('toast').textContent,/Could not update payout reminders/);failed.setPlanOK(true);failed.run('togglePayoutReminders()');failed.flush();assert.equal(failed.uploads.length,1,'Failed generation can be retried');assert(failed.enabled.some(e=>e.value));

const maturity={...base,autoRenew:false,cycle:1,cycleUnit:'months',start:'2026-01-01',first:'2026-02-01',maturity:'2037-06-01',tenureCount:137,tenureUnit:'months'};
const terminal=app([maturity,{...maturity,id:'other',ownership:'others',ownerName:'Other owner'}]);terminal.flush();assert.equal(terminal.uploads[0].plan.at(-1).count,2,'Batch boundaries retain both interest and principal on the same maturity date');assert(terminal.uploads[0].plan.every(p=>!p.details.includes('Other owner')));
console.log('PASS: yielding bounded reminder batches, full2199 horizon, same-day terminal rows, fresh-plan enable ordering, edit/account/date cancellation, repeated-toggle cancellation and unchanged-data reuse.');
