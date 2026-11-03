const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const html=fs.readFileSync(process.env.DEPOSITLY_ANDROID_SOURCE||path.join(__dirname,'../app/assets/index.html'),'utf8');
const engine=vm.createContext({});vm.runInContext([...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)][0][1],engine);const F=engine.FDR;
const copy=value=>JSON.parse(JSON.stringify(value)),order=(a,b)=>a.date.localeCompare(b.date)||a.id.localeCompare(b.id);
const base={id:'simple',type:'FDR',name:'Simple annual deposit',ownerName:'Sample Holder',ownership:'self',principal:10000000,rate:10,tds:10,cycle:1,cycleUnit:'months',start:'2024-01-01',first:'2024-02-01',maturity:'2025-01-01',tenureCount:1,tenureUnit:'years',autoRenew:true,interestType:'simple'};

// Differential checks retain the incumbent full schedule as the financial oracle.
let seed=1729;const random=n=>{seed=(seed*1664525+1013904223)>>>0;return seed%n;};
for(let j=0;j<600;j++){
 const start=['2024-01-31','2023-02-28','2024-02-29','2025-12-31','2020-06-15'][random(5)],unit=['days','months','years'][random(3)],term=unit==='days'?1+random(110):1+random(4),maturity=unit==='days'?F.days(start,term):F.months(start,term*(unit==='years'?12:1)),cycleUnit=random(2)?'months':'days',cycle=cycleUnit==='months'?1+random(13):1+random(150),standard=cycleUnit==='days'?F.days(start,cycle):F.months(start,cycle),first=standard>maturity?maturity:standard,a={...base,start,maturity,first,cycle,cycleUnit,tenureCount:term,tenureUnit:unit,autoRenew:!!random(2)};
 if(a.autoRenew&&random(3)===0)a.renewalStop=F.renewalDate(a,1+random(5));
 const through=F.months(start,36+random(60)),query=F.scheduleQuery(a),old=copy(F.schedule(a,through));
 for(let k=0;k<4;k++){
  const from=k===0?'1900-01-01':F.days(start,random(1800)),to=k===0?through:F.days(from,random(500)),bound=to<through?to:through,expected=old.filter(p=>p.date>=from&&p.date<=bound).sort(order);
  assert.equal(query.count(from,bound),expected.length,'Exact count for fixture '+j+', range '+k);
  assert.deepEqual(copy(query.range(from,bound)),expected,'Amounts/IDs/dates match the original engine');
  assert.deepEqual(copy(query.range(from,bound,3)),expected.slice(0,3),'Lazy first page matches the full schedule');
 }
}
for(const a of [base,{...base,type:'NSC'}, {...base,interestType:'compound'}, {...base,interestType:'compound',renewalStop:'2027-01-01'}, {...base,autoRenew:false,maturity:'2099-01-01',tenureCount:75}]){
 const old=copy(F.schedule(a,'2199-12-31')).sort(order),query=F.scheduleQuery(a);assert.equal(query.count('1900-01-01','2199-12-31'),old.length);assert.deepEqual(copy(query.range('1900-01-01','2199-12-31')),old);
}

// Use the existing UI harness; its date is fixed at 2026-10-26.
const helper=fs.readFileSync(path.join(__dirname,'home-forecast.test.cjs'),'utf8').split('const forecastNet=')[0],sandbox={require,__dirname,console,Blob,URL,process:{env:process.env}};
vm.runInNewContext(helper+'\nthis.helper={app};',sandbox);
const app=sandbox.helper.app([]);app.context.fixture=base;
app.run("state.assets=Array.from({length:500},(_,i)=>({...fixture,id:'deposit'+i,name:'Deposit '+i}));state.paid={};cashQueryCache=null;globalThis.generatedRows=0;globalThis.originalQuery=FDR.scheduleQuery;FDR.scheduleQuery=function(a){const q=originalQuery(a);return {count:q.count,range(...args){const rows=q.range(...args);generatedRows+=rows.length;return rows;}};};");
const rows=code=>{app.run('generatedRows=0');app.run(code);return app.run('generatedRows');};
assert(rows('overview()')<4000,'Home500 bounds event objects instead of generating a million payouts');
assert(rows("paymentFilter='Upcoming';payments()")<4000,'Payments500 bounds generated rows to the first page');
assert.match(app.screen(),/Show more payments/);assert.equal((app.screen().match(/class="listbutton payment"/g)||[]).length,50);
assert(rows("calendar()")<2000,'Calendar generates only the displayed month');
assert(rows("detail('deposit0')")<=50,'Long detail schedules render only their first page');assert.match(app.get('modal').innerHTML,/2111 entries/);assert.match(app.get('modal').innerHTML,/Show more payments/);
app.get('modal').scrollTop=388;app.run("loadMoreDetail('deposit0')");assert.equal((app.get('modal').innerHTML.match(/class="schedule-row"/g)||[]).length,100);assert.equal(app.get('modal').scrollTop,388,'Loading the next detail page preserves the reading position');
assert.equal(app.run("queryCashEvents(state.assets,{status:'unpaid',limit:0}).total"),F.schedule(base,'2199-12-31').length*500,'The exact future count is retained without generating rows');
assert.equal(app.run("cashEventById('deposit0:2199-12-01').date"),'2199-12-01','Distant payment details remain reachable');

// A received principal retains its ID after maturity edits. Full-horizon views use the current
// scheduled event; an earlier Calendar month retains the historical snapshot until that event exists.
const edited={...base,id:'edited',autoRenew:false,maturity:'2027-01-01',tenureCount:3},receipt={amount:10000000,date:'2025-01-03',snapshot:{assetId:'edited',name:'Original name',type:'FDR',scheduledDate:'2025-01-01',kind:'Principal return',gross:0,tax:0,expected:10000000,principal:10000000}};
app.context.edited=edited;app.context.receipt=receipt;app.run("state.assets=[edited];state.paid={'edited:principal':receipt};cashQueryCache=null");
assert.equal(app.run("cashEventById('edited:principal').date"),'2027-01-01');
app.run("state.paid={};");assert.equal(app.run("cashEventById('edited:principal').date"),'2027-01-01','An unpaid principal return remains reachable from the payment list');app.run("paymentDetail('edited:principal')");assert.equal(app.get('modal').open,true,'Opening an unpaid principal return shows its receipt form');assert.match(app.get('modal').innerHTML,/Principal return/);app.run("closeModal();state.paid={'edited:principal':receipt};");
assert.equal(app.run("queryCashEvents(state.assets,{from:'2026-10-26',status:'unpaid',limit:50}).total"),3);
assert.equal(app.run("queryCashEvents(state.assets,{from:'2025-01-01',through:'2025-01-31',scheduleThrough:'2025-02-01',status:'paid'}).list[0].date"),'2025-01-01');
assert.equal(app.run("queryCashEvents(state.assets,{status:'paid'}).total"),1,'The principal receipt is never duplicated after a maturity edit');
assert.equal(app.run("queryCashEvents(state.assets,{from:'2025-01-01',through:'2025-01-31',status:'paid'}).total"),0,'Full-horizon filters use the current scheduled date');

const corrected={...base,id:'corrected',start:'2026-10-01',cycle:100,cycleUnit:'days',first:'2027-01-09',maturity:'2027-01-09',tenureCount:100,tenureUnit:'days'},oldQuarter=F.legacySchedule({...corrected,cycle:3,cycleUnit:'months',first:'2027-01-01'},'2027-07-28').find(p=>p.date==='2027-01-01');
for(const amount of [0,oldQuarter.net,999999999]){
 const oldReceipt={amount,date:'2027-05-01',snapshot:{assetId:corrected.id,name:'Historical investment name',type:'FDR',scheduledDate:oldQuarter.date,kind:oldQuarter.kind,gross:oldQuarter.gross,tax:oldQuarter.tax,expected:oldQuarter.net,principal:0}};
 app.context.corrected=corrected;app.context.oldReceipt=oldReceipt;app.run("state.assets=[corrected];state.paid={'corrected:2027-01-01':oldReceipt};");
 for(const through of ['2027-01-05','2027-01-09','2027-07-28','2199-12-31']){
  app.context.bound=through;const old=copy(app.run("investmentCashEvents(state.assets[0],bound).sort(cashDateOrder)")),actual=copy(app.run("queryCashEvents(state.assets,{through:bound,scheduleThrough:bound})"));
  assert.equal(actual.total,old.length,'Reconciled exact count includes retained history and removes fully credited interest');assert.deepEqual(actual.list,old,'Partial/full/zero legacy credits match the full cash-event oracle');
  app.context.expectedFirst=old.slice(0,3);assert.deepEqual(copy(app.run("queryCashEvents(state.assets,{through:bound,scheduleThrough:bound,limit:3}).list")),old.slice(0,3));
 }
}
app.context.edited=edited;app.run("state.assets=[edited];state.paid={'edited:2024-02-01':{amount:75000,date:'2024-02-01'}};");assert.equal(app.run("queryCashEvents(state.assets,{status:'paid'}).total"),1,'Snapshot-less legacy receipts still settle the matching current event');

app.run("globalThis.legacyCalls=0;globalThis.originalLegacySchedule=FDR.legacySchedule;FDR.legacySchedule=function(...args){legacyCalls++;return originalLegacySchedule(...args);};globalThis.importAssets=Array.from({length:500},(_,i)=>({...fixture,id:'import'+i,autoRenew:false,maturity:'2099-01-01',tenureCount:75}));globalThis.importState={version:2,assets:importAssets,paid:{},currency:'BDT'};Transfer.migrate(importState);");
assert.equal(app.run('legacyCalls'),0,'Modern/empty receipt maps never enumerate legacy schedules on startup');
app.run("globalThis.legacyEntry=originalLegacySchedule(importAssets[0],'2024-02-01')[0];importState.paid={[legacyEntry.id]:{amount:legacyEntry.net,date:legacyEntry.date,snapshot:Transfer.snapshot(legacyEntry)}};Transfer.migrate(importState);");
assert.equal(app.run('legacyCalls'),0,'Complete snapshots bypass inference while retaining normal validation');
app.run("delete importState.paid[legacyEntry.id].snapshot;globalThis.inferred=Transfer.migrate(importState);");assert.equal(app.run('legacyCalls'),1,'Only the asset with an incomplete old receipt builds its legacy inference schedule');assert.deepEqual(copy(app.run('inferred.paid[legacyEntry.id].snapshot')),copy(app.run('Transfer.snapshot(legacyEntry)')),'Legacy receipt inference preserves the original snapshot amounts and dates');

// Immutable edits, receipt updates, account replacement, and a changed local day invalidate indexing.
app.run('globalThis.previousIndex=cashQueryIndex();state.assets=state.assets.map(a=>({...a,rate:11}));');assert.equal(app.run('cashQueryIndex()===previousIndex'),false);
app.run('previousIndex=cashQueryIndex();state.paid={};');assert.equal(app.run('cashQueryIndex()===previousIndex'),false);
app.run("previousIndex=cashQueryIndex();cashQueryCache.day='1900-01-01';");assert.equal(app.run('cashQueryIndex()===previousIndex'),false);
app.run("state.assets=[{...fixture,id:'replacement',ownership:'others',ownerName:'Someone else'}];state.paid={};");assert.equal(app.run("queryCashEvents(state.assets.filter(isSelf),{limit:0}).total"),0);
console.log('PASS:600 differential recurrence fixtures, exact counts and lazy pages, bounded500-deposit screens, distant lookup, maturity-edited receipts, and financial/day/account cache invalidation.');
