const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const nodes=new Map(),get=id=>{
 if(!nodes.has(id))nodes.set(id,{innerHTML:'',textContent:'',value:'',style:{},dataset:{},open:false,setAttribute(k,v){this[k]=v},insertAdjacentHTML(w,h){this.innerHTML=w==='beforeend'?this.innerHTML+h:h+this.innerHTML},showModal(){this.open=true},close(){this.open=false}});
 return nodes.get(id);
};
class FixedDate extends Date{constructor(...args){super(...(args.length?args:[2026,9,13,12]));}}
const a={id:'self',type:'FDR',name:'A deposit',ownerName:'Me',ownership:'self',principal:20000000,rate:11.77,tds:10,cycle:1,cycleUnit:'months',start:'2026-09-13',first:'2026-10-13',maturity:'2026-12-13'};
let stored=JSON.stringify({version:2,assets:[a,{...a,id:'other',name:'Other owner',ownership:'others',ownerName:'Family'}],paid:{},dark:false,currency:'BDT',numberFormat:'south-asian'});
let plan=[],uploads=0,tests=0,exported=null,status={available:true,enabled:false,permissionGranted:true,time:'09:00',nextDate:''};
const ctx={console,Date:FixedDate,Math,JSON,Number,String,Set,Array,Object,Error,Blob,URL,setTimeout:()=>0,confirm:()=>true,scrollTo(){},document:{getElementById:get,querySelector:get,body:{dataset:{},classList:{toggle(){}}}},localStorage:{getItem:()=>stored,setItem:(k,v)=>stored=v},Vault:{load:()=>stored,save:s=>{stored=s;return true},exportFile:(data,name,mime)=>exported={data,name,mime},restore(){}},Reminders:{
 getStatus:()=>JSON.stringify(status),setPlan:s=>{plan=JSON.parse(s);uploads++;status.nextDate=plan[0]?.date||'';return true},setEnabled:on=>{status.enabled=on},setTime:time=>{if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(time))return false;status.time=time;return true},testNotification:()=>tests++,openSystemSettings(){}
}};
ctx.window=ctx;vm.createContext(ctx);
const html=fs.readFileSync(path.join(__dirname,'../app/assets/index.html'),'utf8');
for(const m of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))vm.runInContext(m[1],ctx);
const run=s=>vm.runInContext(s,ctx),screen=()=>get('main').innerHTML;
assert.equal(plan[0].date,'2026-10-13');assert.equal(plan[0].total,'৳1,766');
assert(plan.every(p=>!p.details.includes('Other owner')),'Others are excluded from reminders');
assert.equal(plan.find(p=>p.date==='2026-12-13').count,2,'Maturity combines interest and principal');
assert(plan.every(p=>p.date>='2026-10-13'));
const firstUpload=uploads;run('syncReminderPlan()');assert.equal(uploads,firstUpload,'Unchanged data avoids rescheduling');
run("go('Settings')");
for(const label of ['Appearance','Currency & numbers','Notifications','Data management','Import, export & backup','Made by Greenstone Solutions LLC'])assert(screen().includes(label),label);
assert.doesNotMatch(screen(),/biometric|\bPIN\b/i);
assert.match(screen(),/role="switch" aria-label="Payout reminders" aria-checked="false"/);
run('togglePayoutReminders()');assert.equal(status.enabled,true);
assert.match(screen(),/Next reminder: 13 OCT, 2026/);
run("setReminderTime('14:30')");assert.equal(status.time,'14:30');
run("setReminderTime('99:99')");assert.equal(status.time,'14:30','Invalid input does not change reminder time');
run('testPayoutReminder()');assert.equal(tests,1);
status.permissionGranted=false;run('onReminderStatus(Reminders.getStatus())');
assert.match(screen(),/Notifications are blocked/);run('testPayoutReminder()');assert.equal(tests,1);
status.permissionGranted=true;run('onReminderStatus(Reminders.getStatus())');
run("commit({...state,paid:{'self:2026-10-13':{amount:176550,date:'2026-10-13'}}})");
assert.equal(plan[0].date,'2026-11-13','Received payment removes its pending reminder');
run("commit({...state,assets:[...state.assets,{...state.assets[0],id:'another',name:'B deposit'}]})");
assert.equal(plan[0].date,'2026-10-13','Adding a new unpaid same-day investment refreshes the plan');
assert.equal(plan[0].count,1);
run("commit({...state,paid:{}})");assert.equal(plan[0].count,2);assert.equal(plan[0].total,'৳3,531','Combined totals round once from full precision');
run("updateDisplayPreference('currency','USD')");assert.equal(plan[0].total,'$3,531','Reminder display follows currency settings');
run("commit({...state,assets:state.assets.filter(a=>a.ownership==='others')})");assert.equal(plan.length,0,'Deleting Self records clears the reminder plan');
run('togglePayoutReminders()');assert.equal(status.enabled,false);
// Reminders keep renewing using the existing 100-day engine, even without reopening the app.
run("commit({...state,assets:[{...state.assets[0],id:'renew',name:'Renewal',ownership:'self',start:'2026-07-05',first:'2026-10-13',maturity:'2026-10-13',cycle:100,cycleUnit:'days',autoRenew:true}],paid:{}})");
assert(plan.some(p=>p.date.startsWith('2027-')));assert(plan.every(p=>!p.details.includes('Principal maturity')));
run("openReminderDate('2027-01-21')");assert.equal(run('tab'),'Calendar');assert.equal(run('selectedDay'),'2027-01-21');
const data=run('JSON.stringify(state)');run('exportEntries(undefined,"json")');assert(exported.name.endsWith('.json'));assert.equal(run('JSON.stringify(state)'),data);
assert(!JSON.parse(exported.data).reminderPlan,'Device notification plan is not added to financial backups');
run("chooseCurrency()");assert.match(get('modal').innerHTML,/id="displayCurrency"/);
run('delete window.Reminders;go("Settings")');assert.match(screen(),/Available in the installed Android app/);
console.log('PASS: grouped Settings, notification controls/blocked permission, test alert, Self-only groups, receipts/deletions/import-related commits, currency, renewable terms, notification deep links and unchanged backups.');
