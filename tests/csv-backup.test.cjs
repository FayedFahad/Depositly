const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'../app/assets/index.html'),'utf8');
const scripts=[...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)];
const context=vm.createContext({console,Date,Math,JSON,Number,String,Set,Map,Array,Object,Error});
for(const script of scripts.slice(0,2))vm.runInContext(script[1],context);
const T=context.Transfer,plain=x=>JSON.parse(JSON.stringify(x)),equal=(a,b,message)=>assert.deepEqual(plain(a),plain(b),message);
const account={id:'bank1',bankId:'ab-bank-plc',bankName:'AB Bank PLC',accountTitle:'=Private Holder',accountNumber:'00123456789',branchName:'Main, "North"',routingNumber:'000123456',accountType:'Savings'};
const asset={id:'deposit',type:'FDR',interestType:'compound',name:'সঞ্চয়, "October"',ownership:'self',ownerName:'Me',principal:10000000,rate:10.25,tds:10,cycle:100,cycleUnit:'days',start:'2026-01-01',first:'2026-04-11',maturity:'2026-04-11',tenureCount:100,tenureUnit:'days',autoRenew:false,bankAccountId:account.id,reference:'000123',notes:"line one\r\nline two\n=SUM(A1)",csvColumns:{customLabel:'value with comma','quoted/header':'=SUM(A1)'}};
const receipt={amount:256250,date:'2026-04-12',notes:'Paid, "verified"',snapshot:{assetId:asset.id,name:asset.name,type:'FDR',scheduledDate:'2026-04-11',kind:'Interest',gross:284722,tax:28472,expected:256250,principal:0,bankAccountId:account.id,bankAccountSnapshot:{bankName:'Historical bank',accountTitle:'Original holder',accountNumber:'0009876'}}};
const returned={amount:10000000,date:'2026-04-13',notes:'Principal received',snapshot:{...receipt.snapshot,kind:'Principal return',gross:0,tax:0,expected:10000000,principal:10000000}};
const reminders={enabled:true,time:'07:45'};
const source=T.migrate({version:2,assets:[asset,{...asset,id:'nsc',type:'NSC',interestType:'simple',bankAccountId:undefined,ownership:'others',ownerName:'Other person'}],paid:{'deposit:2026-04-11':receipt,'deposit:principal':returned},bankAccounts:[account,{...account,id:'unlinked',accountNumber:'000555'}],dark:true,currency:'USD',numberFormat:'international',defaultDepositView:'grid',bankInformationView:'grid'});
const depositCSV=T.depositsCSV(source,reminders),bankCSV=T.bankAccountsCSV(source),deposits=T.readDepositsCSV(depositCSV),banks=T.readBankAccountsCSV(bankCSV);
assert.equal(T.csvKind(depositCSV),'deposits');assert.equal(T.csvKind(bankCSV),'banks');assert.equal(T.csvKind(T.instrumentCSV(source)),'csv');
for(const h of ['Deposit ID','Investment name','Principal','Annual interest rate %','TDS %','Opening date','Maturity date','Receipt ID','Received amount','Receipt receiving account number'])assert(depositCSV.split('\r\n')[0].includes('"'+h+'"'),h);
for(const h of ['Bank account ID','Bank name','Account title','Account number','Branch name','Routing number','Account type'])assert(bankCSV.split('\r\n')[0].includes('"'+h+'"'),h);
assert(!depositCSV.includes('Field path'));assert(!bankCSV.includes('Field path'));assert(!depositCSV.includes(account.accountNumber),'Current bank account details stay in the separate bank CSV');assert(!bankCSV.includes(asset.name),'Bank CSV has no deposits or receipts');
assert(bankCSV.includes('"00123456789"'));assert(bankCSV.includes('"000123456"'));assert(bankCSV.includes('"\'=Private Holder"'),'CSV neutralizes spreadsheet formulas');
equal(deposits.assets,source.assets,'Readable deposit rows restore all terms, leading-zero certificate references and extra columns');equal(deposits.paid,source.paid,'Multiple receipt rows restore amounts, dates, notes and historical receiving accounts');equal(deposits.bankAccounts,[]);equal(deposits.notificationSettings,reminders);
equal(banks.bankAccounts,source.bankAccounts);equal(banks.assets,[]);equal(banks.paid,{});assert.equal(banks.bankInformationView,'grid');
equal(T.readDepositsCSV(depositCSV.replace(/\r\n/g,'\n')).assets.map(a=>a.reference),source.assets.map(a=>a.reference));equal(T.readBankAccountsCSV(bankCSV.replace(/\r\n/g,'\n')).bankAccounts,source.bankAccounts);
for(const data of [{...source,assets:[],paid:{}},{version:2,assets:[],paid:{},bankAccounts:[],dark:false}]){assert.equal(T.readDepositsCSV(T.depositsCSV(data)).assets.length,0);equal(T.readBankAccountsCSV(T.bankAccountsCSV(data)).bankAccounts,data.bankAccounts);}
const many={...source,assets:Array.from({length:500},(_,i)=>({...asset,id:'deposit'+i,notes:'plain'})),paid:{}};assert.equal(T.readDepositsCSV(T.depositsCSV(many)).assets.length,500);
const clean=T.depositsCSV({...source,assets:source.assets.map(a=>({...a,notes:'plain'}))},reminders),lines=clean.split('\r\n'),bankLines=bankCSV.split('\r\n');
for(const damaged of [lines.slice(0,-1).join('\r\n'),clean+'\r\n'+lines[1],clean.replace('"Received amount"','"Wrong column"'),clean.replace('"2562.5"','"not money"')])assert.throws(()=>T.readDepositsCSV(damaged),'Malformed or incomplete deposit CSV is rejected');
for(const damaged of [bankLines.slice(0,-1).join('\r\n'),bankCSV+'\r\n'+bankLines[1],bankCSV.replace('"Routing number"','"Wrong column"'),bankCSV.replace('"Bank account ID"','"Bank name"'),bankCSV.replace(/,"2"(?=\r\n|$)/g,',"99"'),bankCSV.replace('"00123456789"','""')])assert.throws(()=>T.readBankAccountsCSV(damaged),'Malformed, future-version or incomplete bank CSV is rejected');
assert.throws(()=>T.readDepositsCSV(T.depositsCSV(source,{enabled:true,time:'25:00'})),/settings/);
function app(initial){
 const nodes=new Map(),get=id=>{if(!nodes.has(id))nodes.set(id,{innerHTML:'',textContent:'',value:'',style:{},dataset:{},open:false,setAttribute(){},insertAdjacentHTML(_,s){this.innerHTML+=s;},showModal(){this.open=true;},close(){this.open=false;}});return nodes.get(id);};
 let saved=JSON.stringify(initial),saveOK=true,confirmed=true;const exports=[],queued=[],notification={available:true,enabled:false,permissionGranted:true,time:'09:00'};
 const c={console,Date,Math,JSON,Number,String,Set,Map,Array,Object,Error,Blob,URL,setTimeout:()=>0,confirm:()=>confirmed,scrollTo(){},document:{getElementById:get,querySelector:get,body:{dataset:{},classList:{toggle(){}}}},localStorage:{getItem:()=>saved,setItem:(_,s)=>saved=s},Vault:{load:()=>saved,owner:()=> 'account',saveForAccount:s=>{if(!saveOK)return false;saved=s;return true;},exportFile:(...args)=>exports.push(args)},Reminders:{getStatus:()=>JSON.stringify(notification),setPlan:()=>true,setTime:t=>{notification.time=t;return true;},setEnabled:on=>notification.enabled=on,restoreSettings:s=>{Object.assign(notification,JSON.parse(s));return true;}},DriveBackup:{getStatus:()=>JSON.stringify({available:true,signedIn:true,ready:true,busy:false}),queueRegistryBackup:s=>queued.push(JSON.parse(s))}};
 c.window=c;vm.createContext(c);for(const script of scripts)vm.runInContext(script[1],c);
 return {run:s=>vm.runInContext(s,c),context:c,get,exports,notification,queued,get saved(){return JSON.parse(saved);},setSaveOK:v=>saveOK=v,setConfirm:v=>confirmed=v};
}
const empty={version:2,assets:[],paid:{},bankAccounts:[],dark:false,currency:'BDT'};
const a=app(empty);a.context.deposits=depositCSV;a.context.banks=bankCSV;
a.run('transferCenter()');for(const label of ['Export Deposits (CSV)','Export Bank Information (CSV)'])assert(a.get('modal').innerHTML.includes(label));assert(!/JSON|Field path|complete backup/.test(a.get('modal').innerHTML));
a.run('settings()');assert.match(a.get('main').innerHTML,/Separate CSV files/);
a.run('restoreData(deposits)');assert.match(a.get('modal').innerHTML,/Your saved Bank Information will be kept/);assert(!a.get('modal').innerHTML.includes('Bank accounts in file'));
a.setConfirm(false);a.run("applyImport('replace')");equal(a.saved.assets,[],'Cancellation keeps saved data');
a.setConfirm(true);a.setSaveOK(false);a.run("applyImport('replace')");equal(a.saved.assets,[]);assert.equal(a.notification.time,'09:00','Failed persistence never changes native reminder settings');
a.setSaveOK(true);a.run("applyImport('replace')");equal(a.saved.assets,deposits.assets);equal(a.saved.paid,deposits.paid);equal(a.saved.bankAccounts,[]);
for(const key of ['dark','currency','numberFormat','defaultDepositView'])equal(a.saved[key],deposits[key],key+' restored');assert.equal(a.notification.time,'07:45');assert.equal(a.notification.enabled,true);
const beforeBanks=plain(a.saved);a.run('restoreData(banks)');assert.match(a.get('modal').innerHTML,/Your deposits, receipts and other settings will be kept/);a.run("applyImport('replace')");equal(a.saved.bankAccounts,source.bankAccounts);assert.equal(a.saved.bankInformationView,'grid');for(const key of ['assets','paid','dark','currency','numberFormat','defaultDepositView','notificationSettings'])equal(a.saved[key],beforeBanks[key],key+' kept by bank-only replace');
a.run('exportEntries();exportBankInformation()');assert.equal(a.exports.length,2);assert.match(a.exports[0][1],/^depositly-deposits-.*\.csv$/);assert.match(a.exports[1][1],/^depositly-bank-information-.*\.csv$/);for(const file of a.exports)assert.equal(file[2],'text/csv');equal(T.readDepositsCSV(a.exports[0][0]).paid,source.paid);a.context.exportedBankText=a.exports[1][0];equal(a.run('readBankInformationCSV(exportedBankText).bankAccounts').sort((left,right)=>left.id.localeCompare(right.id)),[...source.bankAccounts].sort((left,right)=>left.id.localeCompare(right.id)));
// Either import order can restore the pair on a fresh install; existing banks survive deposit replacement.
const reverse=app(empty);reverse.context.deposits=depositCSV;reverse.context.banks=bankCSV;reverse.run("restoreData(banks);applyImport('replace');restoreData(deposits);applyImport('replace')");equal(reverse.saved.bankAccounts,source.bankAccounts);equal(reverse.saved.assets,deposits.assets);assert.equal(reverse.saved.bankInformationView,'grid');
// Empty files clear only their own category and protect banks used by deposits.
a.context.emptyDeposits=T.depositsCSV(empty);a.context.emptyBanks=T.bankAccountsCSV(empty);a.run("restoreData(emptyBanks);applyImport('replace')");equal(a.saved.bankAccounts,source.bankAccounts);assert.match(a.get('toast').textContent,/linked to a deposit/);
a.run("restoreData(emptyDeposits);applyImport('replace')");equal(a.saved.assets,[]);equal(a.saved.paid,{});equal(a.saved.bankAccounts,source.bankAccounts);a.run("restoreData(emptyBanks);applyImport('replace')");equal(a.saved.bankAccounts,[]);
const b=app({...source,assets:[{...asset,name:'Keep local name'}],paid:{'deposit:2026-04-11':{...receipt,amount:100}},bankAccounts:[{...account,accountTitle:'Keep local holder'}],dark:false,numberFormat:'south-asian'});b.context.deposits=depositCSV;b.context.banks=bankCSV;
b.run("restoreData(deposits);applyImport('merge');restoreData(banks);applyImport('merge')");assert.equal(b.saved.assets.length,2);assert.equal(b.saved.assets[0].name,'Keep local name');assert.equal(b.saved.paid['deposit:2026-04-11'].amount,100);assert.equal(b.saved.bankAccounts.length,2);assert.equal(b.saved.bankAccounts[0].accountTitle,'Keep local holder');assert.equal(b.saved.dark,false);assert.equal(b.notification.time,'09:00');
const merged=plain(b.saved);b.run("restoreData(deposits);applyImport('merge');restoreData(banks);applyImport('merge')");equal(b.saved,merged,'Repeated merge does not duplicate either category');
b.context.bad=bankLines.slice(0,-1).join('\r\n');b.run("restoreData(bad);applyImport('replace')");equal(b.saved,merged,'Rejected files never change saved data');
// Build 16 combined CSVs stay readable, but are no longer offered for export.
const legacyRows=[T.BACKUP_COLUMNS];function legacy(value,path=''){const type=value===null?'null':Array.isArray(value)?'array':typeof value,entries=['object','array'].includes(type)?Object.entries(value).filter(([,v])=>v!==undefined):[];legacyRows.push([1,path,type,['object','array'].includes(type)?entries.length:type==='null'?'':value]);for(const [k,v] of entries)legacy(v,path+'/'+k.replace(/~/g,'~0').replace(/\//g,'~1'));}legacy(T.exportData(source));const combined=T.csv(legacyRows);assert.equal(T.csvKind(combined),'backup-csv');equal(T.readBackupCSV(combined).bankAccounts,source.bankAccounts);
assert(!html.includes("format==='json'"));assert(!html.includes('function backupCSV('));
// Bank order is an optional extension around the unchanged shared Bank CSV format.
(async()=>{
 const orderBanks=[{...account,accountType:"'=literal",balance:12345},{...account,id:'legacy-order',bankName:'AB BANK LIMITED',accountTitle:"'Leading apostrophe",accountType:"'=literal",balance:12345},{...account,id:'city-order',bankId:'city-bank-plc',bankName:'City Bank PLC',accountTitle:'City holder',balance:0},{...account,id:'custom-order',bankId:undefined,bankName:'Custom, "Bank"',accountTitle:'Custom holder',balance:0}];
 delete orderBanks[1].bankId;
 const rankedState={...source,bankAccounts:orderBanks},ordered=app(rankedState),order=['bank:city-bank-plc','bank:ab-bank-plc'];
 const csvFor=ranks=>T.csv([[...T.BANK_COLUMNS,'Bank order'],...orderBanks.map((bank,index)=>[bank.id,bank.bankId,bank.bankName,bank.accountTitle,bank.accountNumber,bank.branchName,bank.routingNumber,bank.accountType,bank.balance/100,'grid',orderBanks.length,'USD',2,ranks[index]??''])]);
 const readNative=text=>{ordered.context.orderText=text;return ordered.run('readBankInformationCSV(orderText)');};
 const valid=csvFor([2,'',1,'']),parsed=readNative(valid);equal(parsed.bankInformationOrder,order);equal(parsed.bankAccounts,T.migrate(rankedState).bankAccounts,'Rank parsing preserves leading zeros, quotation and formula-escaped text exactly once');
 ordered.context.orderText=valid;ordered.run("restoreData(orderText);applyImport('replace');exportBankInformation()");const exported=ordered.exports.at(-1)[0];assert.match(exported,/"Bank order"/);equal(readNative(exported).bankInformationOrder,order,'App export keeps numbered bank order');
 const afterRankImport=plain(ordered.saved);
 for(const ranks of [[0,'',1,''],[501,'',1,''],[1.5,'',2,''],['001','',2,''],['=1+1','',2,''],[1,2,3,''],[1,1,1,'']]){
  assert.throws(()=>readNative(csvFor(ranks)),/Bank order|same bank/);
  ordered.context.orderText=csvFor(ranks);ordered.run("restoreData(orderText);applyImport('replace')");equal(ordered.saved,afterRankImport,'Invalid bank ranks never mutate the current workspace');
 }
 assert.throws(()=>readNative(valid.replace('"Bank order"','"Bank order","bank order"')),/duplicate Bank order/);
 assert.throws(()=>readNative(valid+'\r\n"unfinished'),/unfinished quoted/);
 equal(readNative(valid.replace(/\r\n/g,'\n')).bankInformationOrder,order);
 const legacy=readNative(T.bankAccountsCSV(rankedState));assert(!Object.hasOwn(legacy,'bankInformationOrder'),'Legacy Bank CSV has no supplied ordering preference');equal(readNative(csvFor([])).bankInformationOrder,[],'Blank Bank order explicitly resets alphabetical order');
 const webPath=path.resolve(__dirname,'../../depositly-website/assets/bank-order-csv.js');
 if(fs.existsSync(webPath)){
  const web=await import(require('node:url').pathToFileURL(webPath).href);
  equal(parsed,web.readBankInformationCSV(valid),'Native and website readers restore identical custom order and bank details');
  const state={...rankedState,bankInformationOrder:order};ordered.context.exportState=state;const nativeExport=ordered.run('exportBankInformationCSV(exportState)'),webExport=web.exportBankInformationCSV(state);
  assert.equal(nativeExport,webExport,'Native and website exports use the same ordering, numbered column and CSV escaping');equal(readNative(webExport),web.readBankInformationCSV(nativeExport),'Both clients import each other’s ordered CSV files');
 }
 console.log('PASS: separate readable Deposits/Bank CSVs, exact identifiers/receipts/history, category-safe restoration, numbered bank order and validation, unchanged legacy CSV compatibility, and native/website ordering parity.');
})().catch(error=>{console.error(error);process.exitCode=1;});
