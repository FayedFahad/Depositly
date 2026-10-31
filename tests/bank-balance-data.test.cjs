const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const html=fs.readFileSync(process.env.DEPOSITLY_ANDROID_SOURCE||path.join(__dirname,'../app/assets/index.html'),'utf8');
const context=vm.createContext({console,Date,Math,JSON,Number,String,Set,Map,Array,Object,Error});
for(const script of [...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)].slice(0,2))vm.runInContext(script[1],context);
const T=context.Transfer,S=require('../app/assets/sync-registry.js'),plain=x=>JSON.parse(JSON.stringify(x));
const account={id:'bank-one',bankId:'ab-bank-plc',bankName:'AB Bank PLC',accountTitle:'Sample Holder',accountNumber:'00012345',branchName:'Main',routingNumber:'00098765'};
const state=(bankAccounts=[],currency='BDT')=>T.migrate({version:2,assets:[],paid:{},bankAccounts,currency,dark:false});
const legacy=state([account]);assert.deepEqual(plain(legacy.bankAccounts),[account],'Legacy accounts remain readable without writing invented balance metadata');
for(const balance of [0,1,1234567,1e13])assert(T.validBankAccount({...account,balance}));
for(const balance of [-1,0.1,1e13+1,Infinity,NaN,'100',null,true]){
 assert(!T.validBankAccount({...account,balance}),'Reject invalid balance '+String(balance));
 assert.throws(()=>state([{...account,balance}]),/bank account/);
}
const accounts=[{...account,balance:1234567},{...account,id:'bank-two',balance:0},{...account,id:'bank-max',balance:1e13}],source=state(accounts,'USD');
assert.deepEqual(plain(T.migrate(JSON.parse(JSON.stringify(T.exportData(source)))).bankAccounts),accounts,'JSON restores exact minor-unit balances');
assert(!Object.hasOwn(T.exportData(source,[]),'bankAccounts'),'Selected deposit exports keep bank balances private');
const csv=T.bankAccountsCSV(source),restored=T.readBankAccountsCSV(csv);
assert(csv.split('\r\n')[0].includes('"Current balance"'));assert(csv.includes('"12345.67"'),'Readable CSV uses major currency units');
assert.deepEqual(plain(restored.bankAccounts),accounts);assert.equal(restored.currency,'USD');
const oldColumns=T.BANK_COLUMNS.filter(h=>!['Current balance','Currency'].includes(h));
const oldValues={'Bank account ID':account.id,'Bank ID':account.bankId,'Bank name':account.bankName,'Account title':account.accountTitle,'Account number':account.accountNumber,'Branch name':account.branchName,'Routing number':account.routingNumber,'Account type':'','Bank view':'card','Bank account count':1,'CSV backup version':2};
const oldCSV=T.csv([oldColumns,oldColumns.map(h=>oldValues[h])]);
assert.deepEqual(plain(T.readBankAccountsCSV(oldCSV).bankAccounts),[account],'Legacy bank CSV remains readable without balance or currency headers');
const one=state([{...account,balance:1234567}],'USD'),oneCSV=T.bankAccountsCSV(one);
for(const damaged of [oneCSV.replace('"12345.67"','"-1"'),oneCSV.replace('"12345.67"','"123.456"'),oneCSV.replace('"12345.67"','"100000000000.01"'),oneCSV.replace('"Current balance"','"Unknown balance"')])assert.throws(()=>T.readBankAccountsCSV(damaged));
const columns=T.BANK_COLUMNS,withoutCurrency=columns.filter(h=>h!=='Currency');
for(const balance of [0,100]){
 const values={...oldValues,'Current balance':balance};
 assert.throws(()=>T.readBankAccountsCSV(T.csv([withoutCurrency,withoutCurrency.map(h=>values[h])])),/Currency column/,'An explicit balance always needs its denomination');
}
const zero=state([{...account,balance:0}]);assert.equal(T.merge(legacy,zero).bankDuplicates,1,'Missing legacy balance and explicit zero are equivalent');
const balanceConflict=T.merge(one,state([{...account,balance:54321}],'USD'));
assert.equal(balanceConflict.bankConflicts,1);assert.equal(balanceConflict.state.bankAccounts[0].balance,1234567,'Merge keeps local balances when amounts conflict');
for(const balance of [0,100]){
 const foreign=state([{...account,id:'foreign',balance}],'BDT');
 assert.equal(T.merge(one,foreign,true).currencyMismatch,true);assert.throws(()=>T.merge(one,foreign),/Currencies differ/,'Bank-only monetary records enforce currency compatibility including zero');
}
assert.equal(T.merge(one,state([{...account,id:'legacy-no-money'}],'BDT')).currencyMismatch,false,'Legacy account identifiers without money do not invent a currency conflict');
assert.equal(T.merge(state([],'BDT'),one).state.currency,'USD','First monetary import adopts its saved denomination');
assert.equal(T.merge(state([],'BDT'),state([{...account,balance:0}],'USD')).state.currency,'USD','First explicit zero balance also retains its saved denomination');
assert.equal(T.merge(state([{...account,balance:0}],'USD'),state([{...account,id:'legacy'}],'BDT')).state.currency,'USD','Legacy identifiers never change a saved monetary denomination');
const wrap=data=>({format:'depositly-google-drive',version:1,data:T.exportData(data),notificationSettings:{enabled:false,time:'09:00'}});
const initial=S.create(wrap(legacy),'seed',1),updated=S.change(initial,wrap(legacy),wrap(source),'phone',2),cloud=S.backup(S.merge(initial,updated));
assert.deepEqual(plain(T.migrate(cloud.data).bankAccounts),accounts);assert.equal(cloud.data.currency,'USD','Cloud sync retains denomination with balances');
console.log('PASS: bank balance validation, legacy compatibility, exact JSON/CSV/cloud round trips, zero-balance currency safety, selected-export privacy and conflict-safe merges.');
