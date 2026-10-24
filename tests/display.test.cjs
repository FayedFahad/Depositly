const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const root=path.resolve(__dirname,'..');
const html=fs.readFileSync(path.join(root,'app/assets/index.html'),'utf8');
const releaseVersion=fs.readFileSync(path.join(root,'app/apktool.yml'),'utf8').match(/versionName:\s*([^\r\n]+)/)[1].trim();
const elements=new Map(),get=id=>{
 if(!elements.has(id))elements.set(id,{innerHTML:'',textContent:'',value:'',style:{},dataset:{},open:false,
 setAttribute(k,v){this[k]=v},insertAdjacentHTML(where,h){this.innerHTML=where==='beforeend'?this.innerHTML+h:h+this.innerHTML},showModal(){this.open=true},close(){this.open=false}});
 return elements.get(id);
};
class TestDate extends Date{constructor(...args){super(...(args.length?args:[2026,9,13,12]));}}
const sample={id:'monthly',type:'FDR',name:'Monthly deposit',ownerName:'Owner',ownership:'self',principal:20000000,rate:11.77,tds:10,cycle:1,cycleUnit:'months',start:'2026-09-13',first:'2026-10-13',maturity:'2029-09-13',reference:'001234',notes:'Keep exact rate and account'};
let saved=JSON.stringify({version:2,assets:[sample,{...sample,id:'other',name:'Other deposit',ownership:'others',ownerName:'Family',principal:25000000}],paid:{},dark:true,currency:'BDT',numberFormat:'south-asian'});
const themeCalls=[],classes=new Map();
const ctx={console,Date:TestDate,Math,JSON,Number,String,Set,Array,Object,Error,Blob,URL,setTimeout:()=>0,confirm:()=>true,scrollTo(){},
 document:{title:'',getElementById:get,querySelector:s=>get(s),body:{dataset:{},classList:{toggle(k,v){classes.set(k,v)}}}},
 localStorage:{getItem:()=>saved,setItem:(k,v)=>{saved=v}},Vault:{load:()=>saved,save:s=>{saved=s;return true},setTheme:dark=>themeCalls.push(dark)}};
ctx.window=ctx;vm.createContext(ctx);
for(const m of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))vm.runInContext(m[1],ctx);
const run=s=>vm.runInContext(s,ctx),screen=()=>get('main').innerHTML;
assert.equal(themeCalls.at(-1),true,'Startup restores the saved dark theme through Android bridge');
assert.equal(classes.get('dark'),true);
assert.equal(ctx.document.title,'Depositly');
assert.equal(get('meta[name="theme-color"]').content,'#101b18');
assert.match(screen(),/<strong>৳2,00,000<\/strong>/,'Home includes Self principal only');
for(const [paisa,expected] of [[0,'৳0'],[1,'৳1'],[99,'৳1'],[100,'৳1'],[101,'৳2'],[196167,'৳1,962'],[10000000,'৳1,00,000'],[10000001,'৳1,00,001'],[15246701,'৳1,52,468']]){
 assert.equal(run(`money(${paisa})`),expected);
 assert.equal(run(`compactMoney(${paisa})`),expected,'Compact views must not misstate lakh amounts');
}
run("state.currency='USD';state.numberFormat='international'");
assert.equal(run('money(10000001)'),'$100,001');
run("state.currency='BDT';state.numberFormat='south-asian';go('Settings')");
assert.match(screen(),/Made by Greenstone Solutions LLC/);
assert(screen().includes(releaseVersion),'Settings reports the packaged version');
assert.match(screen(),/Dark theme active/);
assert.match(html,/<button id="settings" aria-label="Settings" onclick="go\('Settings'\)"/);
assert.doesNotMatch(get('settings').innerHTML,/M12 3v2m0 14v2/,'The old sun path is gone');
run('commit({...state,dark:false});render()');
assert.equal(themeCalls.at(-1),false,'Turning dark mode off also updates native bars');
assert.equal(get('meta[name="theme-color"]').content,'#f6f7f3');
assert.match(screen(),/Light theme active/);
run('commit({...state,dark:true});render()');
assert.equal(themeCalls.at(-1),true);
// Calendar uses whole monetary units for both owners and preserves the separate color.
run("month='2026-10-01';selectedDay='2026-10-13';go('Calendar')");
assert.match(screen(),/<span class="day-amount">৳1,<wbr>766<\/span>/);
assert.match(screen(),/<span class="day-amount others-payment">৳2,<wbr>207<sup>O<\/sup><\/span>/);
assert.doesNotMatch(screen(),/৳[\d,]+\.\d/);
run("go('Payments');paymentDetail('monthly:2026-10-13')");
assert.match(get('modal').innerHTML,/৳1,766/);
assert.doesNotMatch(get('modal').innerHTML,/৳[\d,]+\.\d/);
// Whole-unit presentation must not round stored receipt history or contractual rates.
run("state.paid['monthly:2026-10-13']={amount:176550,date:'2026-10-13'};commit(state);globalThis.exported=Transfer.exportData(state)");
assert.equal(run("exported.paid['monthly:2026-10-13'].amount"),176550);
assert.equal(run('exported.assets.find(a=>a.id===\'monthly\').rate'),11.77);
assert.equal(run('Transfer.migrate(exported).assets.find(a=>a.id===\'monthly\').reference'),'001234');
run('globalThis.csv=Transfer.instrumentCSV(state);globalThis.imported=Transfer.readInstrumentCSV(csv)');
assert.doesNotMatch(ctx.csv,/Received amount|Scheduled date|Received date/);
assert.equal(run('imported.assets.find(a=>a.id===\'monthly\').rate'),11.77);
assert.equal(run("FDR.schedule(imported.assets.find(a=>a.id==='monthly'),'2026-10-13')[0].net"),176550);
// Preview and summaries share whole-unit formatting; percentage terms retain their precision.
run("assetForm('monthly');go('Yields')");
assert.doesNotMatch(get('modal').innerHTML,/৳[\d,]+\.\d/);
assert.doesNotMatch(screen(),/৳[\d,]+\.\d/);
// Restored data survives relaunch. Old/native-less browser previews remain supported.
assert.equal(JSON.parse(saved).dark,true);
run("delete Vault.setTheme;go('Settings')");
assert.match(screen(),/Made by Greenstone Solutions LLC/);
console.log('PASS: upward whole-unit amounts; both comma systems and currency; Settings gear; native theme notifications; Self/Others calendar; previews/payments/forecasts; exact rates/receipts and instrument-only CSV import.');
