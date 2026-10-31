const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const html=fs.readFileSync(process.env.DEPOSITLY_ANDROID_SOURCE||path.join(__dirname,'../app/assets/index.html'),'utf8');
const copy=value=>JSON.parse(JSON.stringify(value));
// Snapshot of the 1.3.1 hero's surrounding markup: only its total and breakdown change.
const baselineHeroSkeleton="<div class=\"card hero\"><div class=\"row\"><span class=\"hero-kicker\">${icon('Portfolio')} Your current balance</span><span class=\"hero-chip\">${active.length} active</span></div><strong>${balanceMoney(total)}</strong>{{balance breakdown}}<button class=\"hero-action\" onclick=\"assetForm()\">${icon('Plus')} Add an investment</button><div class=\"hero-foot\">${icon('Shield')} ${active.some(a=>a.interestType==='compound')?'Includes net interest reinvested at past renewal maturities':'Your principal and interest, tracked separately'}</div></div>";
function heroSkeleton(source){const overview=source.slice(source.indexOf('function overview(){')),match=overview.match(/`(<div class="card hero(?: current-balance)?">[\s\S]*?)`\+/);assert(match,'The current balance remains in its original hero');return match[1].replace('card hero current-balance','card hero').replace(/<div class="stats(?: balance-breakdown)?">[\s\S]*?(?=<button class="hero-action")/,'{{balance breakdown}}');}
assert.equal(heroSkeleton(html),baselineHeroSkeleton,'The existing hero heading, active count, action and footer markup remain unchanged');
const annual={id:'simple',type:'FDR',name:'Annual deposit',ownerName:'Me',ownership:'self',principal:10000000,rate:10,tds:10,cycle:12,cycleUnit:'months',start:'2024-01-01',first:'2025-01-01',maturity:'2025-01-01',tenureCount:1,tenureUnit:'years',autoRenew:true,interestType:'simple'};
const compound={...annual,id:'compound',name:'Compound deposit',interestType:'compound'};
const nsc={...annual,id:'nsc',type:'NSC',name:'Savings certificate',principal:5000000,start:'2026-01-01',first:'2027-01-01',maturity:'2029-01-01',tenureCount:3,autoRenew:false};
const bank={id:'bank',bankId:'ab-bank-plc',bankName:'AB Bank PLC',accountTitle:'Sample Holder',accountNumber:'000123456789',branchName:'Main branch',routingNumber:'000012345',accountType:'Savings',balance:3000000};
const receipt={amount:900000,date:'2025-01-01',notes:'Historical receipt',snapshot:{assetId:'simple',name:annual.name,type:'FDR',scheduledDate:'2025-01-01',kind:'Interest',gross:1000000,tax:100000,expected:900000,principal:0}};
function app(extra={}){
 const nodes=new Map(),get=id=>{if(!nodes.has(id))nodes.set(id,{id,innerHTML:'',textContent:'',value:'',style:{},dataset:{},open:false,setAttribute(key,value){this[key]=value;},insertAdjacentHTML(_,value){this.innerHTML+=value;},showModal(){this.open=true;},close(){this.open=false;},focus(){}});return nodes.get(id);};
 let saved=JSON.stringify({version:2,assets:[],paid:{},bankAccounts:[],dark:false,currency:'BDT',numberFormat:'south-asian',...extra}),saveOK=true,saves=0;
 class FixedDate extends Date{constructor(...args){super(...(args.length?args:[2026,9,26,12]));}}
 class FormData{constructor(form){this.values=Object.entries(form.values||{});}[Symbol.iterator](){return this.values[Symbol.iterator]();}}
 const context={console,Date:FixedDate,Math,JSON,Number,String,Set,Map,Array,Object,Error,Blob,URL,FormData,setTimeout:()=>0,clearTimeout(){},confirm:()=>true,scrollTo(){},document:{getElementById:get,querySelector:get,body:{dataset:{},classList:{toggle(){}}}},localStorage:{getItem(){throw Error('Android DOM storage disabled');},setItem(){throw Error('Android DOM storage disabled');}},Vault:{load:()=>saved,owner:()=> 'account',saveForAccount:(text,owner)=>{if(!saveOK||owner!=='account')return false;saved=text;saves++;return true;}}};
 context.window=context;vm.createContext(context);for(const script of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))vm.runInContext(script[1],context);
 return {get,context,run:source=>vm.runInContext(source,context),screen:()=>get('main').innerHTML,setSaveOK:value=>saveOK=value,get saved(){return JSON.parse(saved);},get saves(){return saves;},submitAccount(values){const form=get('bankAccountForm');form.values=values;form.onsubmit({target:form,preventDefault(){}});},submitBalance(value){get('bankBalance').value=value;const form=get('bankBalanceForm');form.onsubmit({target:form,preventDefault(){}});}};
}
const hero=app=>app.screen().match(/<div class="card hero current-balance">([\s\S]*?)<\/strong>/)?.[1].match(/<strong>([^<]*)$/)?.[1];
const forecast=app=>app.screen().match(/<section id="homeForecast"[\s\S]*?<\/section>/)?.[0];
const others={...annual,id:'others',ownership:'others',ownerName:'Relative',principal:1000000000000};
const future={...annual,id:'future',start:'2027-01-01',first:'2028-01-01',maturity:'2028-01-01'};
const expired={...annual,id:'expired',autoRenew:false};
const second={...bank,id:'bank-two',balance:12345};
const legacy={...bank,id:'legacy'};delete legacy.balance;
const a=app({assets:[annual,compound,nsc,others,future,expired],bankAccounts:[bank,second,legacy],paid:{'simple:2025-01-01':receipt}});
assert.equal(a.run('bankBalanceTotal()'),3012345);
assert.equal(hero(a),'৳2,98,934','Home combines only active Self deposits, reinvested net compound interest and saved bank balances');
const categories=[...a.screen().matchAll(/<div><span class="sub">([^<]+)<\/span><b[^>]*>([^<]+)<\/b><\/div>/g)].slice(0,3).map(match=>[match[1],match[2]]);
assert.deepEqual(categories,[['Fixed deposits','৳2,18,810'],['Savings certificates','৳50,000'],['Bank','৳30,124']],'The three balance categories remain in the requested order');
assert.match(a.screen(),/bank accounts 10 percent of current balance/,'Allocation includes the bank balance');
assert.doesNotMatch(a.screen(),/NaN|Infinity/);
const withoutBanks=app({assets:[annual,compound,nsc,others,future,expired]});
assert.equal(forecast(a),forecast(withoutBanks),'Bank balances do not accrue deposit forecast interest');
const initialAssets=copy(a.saved.assets),initialPaid=copy(a.saved.paid),initialAccount=copy(a.saved.bankAccounts[0]);
a.run("go('BankInformation')");
assert.match(a.screen(),/data-bank-field="balance"/);assert.match(a.screen(),/Update balance for Sample Holder/);
for(const view of ['card','grid']){a.run("setBankInformationView('"+view+"')");assert.equal((a.screen().match(/data-bank-field="balance"/g)||[]).length,3,'Each '+view+' account displays its balance');}
a.run("bankBalanceForm('bank')");assert(a.get('modal').open);assert.match(a.get('modal').innerHTML,/Current balance \(BDT\)/);assert.match(a.get('modal').innerHTML,/value="30000"/);
const beforeInvalid=copy(a.saved),savesBeforeInvalid=a.saves;
for(const value of ['', '-1', '1.001', '1e3', 'NaN', 'Infinity', '100000000000.01']){
 a.submitBalance(value);assert.deepEqual(a.saved,beforeInvalid,'Invalid balance '+value+' leaves all data unchanged');assert(a.get('modal').open);assert.match(a.get('bankBalanceError').textContent,/Enter a balance/);
}
assert.equal(a.saves,savesBeforeInvalid,'Invalid balances never reach storage');
a.setSaveOK(false);a.submitBalance('123.45');assert.deepEqual(a.saved,beforeInvalid);assert(a.get('modal').open);assert.match(a.get('toast').textContent,/Changes could not be saved/);
a.setSaveOK(true);a.submitBalance('123.45');assert(!a.get('modal').open);assert.equal(a.saved.bankAccounts.find(account=>account.id==='bank').balance,12345);
assert.deepEqual(a.saved.assets,initialAssets);assert.deepEqual(a.saved.paid,initialPaid,'A manual balance update never rewrites payment receipts');
assert.deepEqual(a.saved.bankAccounts.find(account=>account.id==='bank'),{...initialAccount,balance:12345},'Updating balance preserves account identifiers');
assert.deepEqual(a.saved.bankAccounts.map(account=>account.id),['bank','bank-two','legacy'],'Updating balance preserves record order');
a.run("go('Overview')");assert.equal(hero(a),'৳2,69,057');assert.equal(forecast(a),forecast(withoutBanks),'Changing a bank balance leaves the forecast unchanged');
const reload=app(a.saved);assert.equal(hero(reload),hero(a),'Bank balances survive Android Vault reload');
a.run("bankBalanceForm('bank')");assert.match(a.get('modal').innerHTML,/value="123\.45"/,'The exact stored decimal amount reopens for editing');a.submitBalance('0');assert.equal(a.saved.bankAccounts.find(account=>account.id==='bank').balance,0,'Zero is a valid saved balance');
a.run("bankBalanceForm('legacy')");assert.match(a.get('modal').innerHTML,/value="0"/,'An account without a legacy balance starts at zero');a.submitBalance('100000000000');assert.equal(a.saved.bankAccounts.find(account=>account.id==='legacy').balance,10000000000000,'The maximum balance saves exactly');

const forms=app({assets:[annual],bankAccounts:[{...bank,balance:25005}],paid:{'simple:2025-01-01':receipt}});
forms.run("go('BankInformation');bankAccountForm('bank')");assert.match(forms.get('modal').innerHTML,/id="balance"[^>]*value="250\.05"/);
const values={bankId:'ab-bank-plc',accountTitle:'Edited Holder',accountNumber:'000123456789',branchName:'Edited branch',routingNumber:'000012345',accountType:'Current',balance:'501.05'};
forms.setSaveOK(false);forms.submitAccount(values);assert.equal(forms.saved.bankAccounts[0].balance,25005);assert.equal(forms.saved.bankAccounts[0].accountTitle,bank.accountTitle);assert(forms.get('modal').open);
forms.setSaveOK(true);forms.submitAccount({...values,balance:'-1'});assert.equal(forms.saved.bankAccounts[0].balance,25005);assert.match(forms.get('bankAccountError').textContent,/Enter a balance/);
forms.submitAccount(values);assert.equal(forms.saved.bankAccounts[0].balance,50105);assert.equal(forms.saved.bankAccounts[0].accountTitle,'Edited Holder');assert.deepEqual(forms.saved.paid,{'simple:2025-01-01':receipt});
forms.run('bankAccountForm()');forms.submitAccount({...values,accountTitle:'New Holder',balance:'0.29'});assert.equal(forms.saved.bankAccounts.length,2);assert.equal(forms.saved.bankAccounts[1].balance,29,'New account balances keep exact minor units');assert.notEqual(forms.saved.bankAccounts[1].id,'bank');
assert.doesNotMatch(forms.run("bankAccountText(state.bankAccounts[0])"),/501|Balance|balance/,'Sharing account identifiers does not disclose its balance');
const deletedTarget=app({bankAccounts:[bank]});deletedTarget.run("bankBalanceForm('bank');commit({...state,bankAccounts:[]})");const savesAfterDeletion=deletedTarget.saves;deletedTarget.submitBalance('123.45');assert.equal(deletedTarget.saves,savesAfterDeletion,'A balance form whose account was removed never reports a successful storage write');assert.deepEqual(deletedTarget.saved.bankAccounts,[]);assert.doesNotMatch(deletedTarget.get('toast').textContent,/balance updated/i);
const paidToBank=app({assets:[{...annual,bankAccountId:'bank'}],bankAccounts:[bank]});const beforeReceiptTotal=hero(paidToBank);paidToBank.run("paymentDetail('simple:2026-01-01')");paidToBank.get('amount').value='9000';paidToBank.get('receiptNote').value='Already included in my account balance';paidToBank.get('receipt').onsubmit({preventDefault(){}});assert.equal(paidToBank.saved.paid['simple:2026-01-01'].amount,900000);assert.equal(paidToBank.saved.paid['simple:2026-01-01'].snapshot.bankAccountId,'bank');assert.deepEqual(paidToBank.saved.bankAccounts,[bank],'Recording a linked payout does not credit a manually entered bank balance again');assert.equal(hero(paidToBank),beforeReceiptTotal,'Recording a simple-interest receipt does not inflate the combined current balance');paidToBank.run("removeReceipt('simple:2026-01-01')");assert.deepEqual(paidToBank.saved.bankAccounts,[bank],'Undoing a receipt does not debit a manually entered bank balance');

const onlyBanks=app({bankAccounts:[{...bank,balance:12345}]});assert.equal(hero(onlyBanks),'৳124');assert.match(onlyBanks.screen(),/>Your mix<\/h3>/);assert.match(onlyBanks.screen(),/bank accounts 100 percent of current balance/);assert.doesNotMatch(onlyBanks.screen(),/Make room for peace of mind/);assert.match(forecast(onlyBanks),/<strong>৳0<\/strong>/);
const empty=app();assert.equal(hero(empty),'৳0');assert.equal(empty.run('bankBalanceTotal()'),0);
const manyBanks=app({bankAccounts:Array.from({length:500},(_,index)=>({...bank,id:'maximum'+index,balance:10000000000000}))});assert.equal(manyBanks.run('bankBalanceTotal()'),5000000000000000,'The full supported bank directory sums exact safe minor-unit integers');assert.equal(hero(manyBanks),manyBanks.run('money(5000000000000000)'));assert.doesNotMatch(manyBanks.screen(),/NaN|Infinity/);
const unavailable=app({assets:[{...compound,principal:10000000000000}],bankAccounts:[bank]});assert.equal(hero(unavailable),'Unavailable','An uncomputable deposit balance never becomes a false partial bank total');assert.doesNotMatch(unavailable.screen(),/NaN|Infinity/);

// Category replacement must not reinterpret retained money in a different currency.
const importer=app({assets:[annual],bankAccounts:[bank]});
importer.context.incoming={version:2,assets:[],paid:{},bankAccounts:[{...bank,balance:9900}],currency:'USD',dark:false};
importer.run('restoreData(Transfer.bankAccountsCSV(incoming))');const beforeBankReplace=copy(importer.saved);importer.run("applyImport('replace')");assert.deepEqual(importer.saved,beforeBankReplace,'A USD bank replacement cannot relabel retained BDT deposits');assert.match(importer.get('toast').textContent,/currenc/i);
importer.context.incoming={version:2,assets:[{...annual,id:'usd-deposit'}],paid:{},bankAccounts:[],currency:'USD',dark:false};
importer.run('restoreData(Transfer.depositsCSV(incoming))');const beforeDepositReplace=copy(importer.saved);importer.run("applyImport('replace')");assert.deepEqual(importer.saved,beforeDepositReplace,'A USD deposit replacement cannot relabel retained BDT bank balances');assert.match(importer.get('toast').textContent,/currenc/i);
importer.context.incoming={version:2,assets:[],paid:{},bankAccounts:[],currency:'USD',dark:false};importer.run("restoreData(Transfer.depositsCSV(incoming));applyImport('replace')");assert.deepEqual(importer.saved,beforeDepositReplace,'Even an empty USD deposit backup cannot change the currency of retained BDT bank balances');
const emptyImporter=app();emptyImporter.context.incoming={version:2,assets:[],paid:{},bankAccounts:[{...bank,balance:9900}],currency:'USD',dark:false};emptyImporter.run("restoreData(Transfer.bankAccountsCSV(incoming));applyImport('replace')");assert.equal(emptyImporter.saved.currency,'USD','Bank-only restore adopts its currency when there are no retained deposits');assert.equal(emptyImporter.saved.bankAccounts[0].balance,9900);assert.equal(hero(emptyImporter),'$99');
const zeroBankImporter=app({assets:[annual],bankAccounts:[{...bank,balance:0}]});zeroBankImporter.context.incoming={version:2,assets:[],paid:{},bankAccounts:[{...bank,balance:0}],currency:'USD',dark:false};const beforeZeroBank=copy(zeroBankImporter.saved);zeroBankImporter.run("restoreData(Transfer.bankAccountsCSV(incoming));applyImport('replace')");assert.deepEqual(zeroBankImporter.saved,beforeZeroBank,'Explicit zero balances still retain the backup currency contract when deposits are kept');zeroBankImporter.context.incoming={version:2,assets:[],paid:{},bankAccounts:[],currency:'USD',dark:false};zeroBankImporter.run("restoreData(Transfer.depositsCSV(incoming));applyImport('replace')");assert.deepEqual(zeroBankImporter.saved,beforeZeroBank,'An empty foreign-currency deposit restore cannot relabel a saved explicit zero bank balance');
const emptyZeroImporter=app();emptyZeroImporter.context.incoming={version:2,assets:[],paid:{},bankAccounts:[{...bank,balance:0}],currency:'USD',dark:false};emptyZeroImporter.run("restoreData(Transfer.bankAccountsCSV(incoming));applyImport('replace')");assert.equal(emptyZeroImporter.saved.currency,'USD','A bank-only zero balance restore still adopts its stated currency');assert.equal(emptyZeroImporter.saved.bankAccounts[0].balance,0);assert.equal(hero(emptyZeroImporter),'$0');
const legacyCurrency=app({assets:[annual],currency:'USD'});legacyCurrency.context.incoming={version:2,assets:[],paid:{},bankAccounts:[legacy],dark:false,currency:'BDT'};legacyCurrency.run("restoreData(Transfer.bankAccountsCSV(incoming));applyImport('replace')");assert.equal(legacyCurrency.saved.currency,'USD','Legacy accounts with no stored balance do not relabel retained deposits');assert.equal(legacyCurrency.saved.bankAccounts[0].balance,undefined);
console.log('PASS: three-way current totals, Self/compound/bank-only balances, unchanged forecasts/receipts, precise create/edit/update validation and save failures, legacy accounts, persistence and currency-safe category restoration.');
