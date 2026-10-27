const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const html=fs.readFileSync(process.env.DEPOSITLY_ANDROID_SOURCE||path.join(__dirname,'../app/assets/index.html'),'utf8');
const equal=(a,b,message)=>assert.deepEqual(JSON.parse(JSON.stringify(a)),b,message);
const asset={id:'deposit',type:'FDR',name:'Savings',ownership:'self',ownerName:'Me',principal:100000,rate:10,tds:10,cycle:1,cycleUnit:'months',start:'2026-01-01',first:'2026-02-01',maturity:'2027-01-01'};
function app(initial={version:2,assets:[],paid:{},dark:false,currency:'BDT'}){
 const nodes=new Map(),get=id=>{if(!nodes.has(id))nodes.set(id,{innerHTML:'',textContent:'',value:'',style:{},dataset:{},open:false,setAttribute(k,v){this[k]=v;},insertAdjacentHTML(_,value){this.innerHTML+=value;},showModal(){this.open=true;},close(){this.open=false;}});return nodes.get(id);};
 let saved=JSON.stringify(initial),saveOK=true,confirmed=true;
 const queued=[],shared=[],notification={available:true,enabled:false,permissionGranted:true,time:'09:00'};
 class FormData {constructor(form){this.values=Object.entries(form.values||{});}[Symbol.iterator](){return this.values[Symbol.iterator]();}}
 const context={console,Date,Math,JSON,Number,String,Set,Map,Array,Object,Error,Blob,URL,FormData,setTimeout:()=>0,confirm:()=>confirmed,scrollTo(){},document:{getElementById:get,querySelector:get,body:{dataset:{},classList:{toggle(){}}}},localStorage:{getItem(){throw Error('Android DOM storage disabled');},setItem(){throw Error('Android DOM storage disabled');}},Vault:{load:()=>saved,owner:()=> 'account',saveForAccount:(text,sub)=>{if(!saveOK||sub!=='account')return false;saved=text;return true;},shareText:text=>shared.push(text)},Reminders:{getStatus:()=>JSON.stringify(notification),setPlan:()=>true},DriveBackup:{getStatus:()=>JSON.stringify({available:true,signedIn:true,ready:true,busy:false}),queueRegistryBackup:(text,sub)=>queued.push({backup:JSON.parse(text),sub})}};
 context.window=context;vm.createContext(context);for(const script of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))vm.runInContext(script[1],context);
 return {run:source=>vm.runInContext(source,context),get,context,queued,shared,setSaveOK:value=>saveOK=value,setConfirm:value=>confirmed=value,get saved(){return JSON.parse(saved);},submit(values){const form=get('bankAccountForm');form.values=values;form.onsubmit({target:form,preventDefault(){}});}};
}

(async()=>{
 const a=app();
 assert.match(html,/<button id="settings"[^>]*aria-label="Menu"[^>]*onclick="openAppMenu\(\)"/,'Hamburger opens the app menu');
 a.run('openAppMenu()');assert(a.get('modal').open);assert.match(a.get('modal').innerHTML,/<b>Bank Information<\/b>/);assert.match(a.get('modal').innerHTML,/go\('BankInformation'\)/);assert.match(a.get('modal').innerHTML,/go\('Settings'\)/);
 assert.equal(a.run('handleBack()'),true,'Back consumes the menu');assert(!a.get('modal').open);assert.equal(a.run('handleBack()'),false,'Home delegates back to the native exit handler');
 a.run("go('BankInformation')");assert.match(a.get('main').innerHTML,/<h2>Bank Information<\/h2>/);assert.match(a.get('main').innerHTML,/aria-label="Bank Information view"/);assert.match(a.get('main').innerHTML,/Your bank details belong here/);assert.equal(a.run('tab'),'BankInformation');
 a.run('bankAccountForm()');const formHTML=a.get('modal').innerHTML;
 assert.match(formHTML,/<h2>Add Bank Information<\/h2>/);assert.match(formHTML,/aria-label="Close Bank Information form"/);
 assert.match(formHTML,/<select id="accountBankId" name="bankId" required/,'Bank choice is a native required dropdown');assert.match(formHTML,/AB Bank PLC/);
 for(const field of ['accountTitle','accountNumber','branchName','routingNumber'])assert.match(formHTML,new RegExp('<input[^>]*id="'+field+'"[^>]*required'),field+' is required');
 const typeInput=formHTML.match(/<input[^>]*id="accountType"[^>]*>/)[0];assert.match(typeInput,/type="text"/);assert(!/\brequired\b/.test(typeInput),'Account type remains optional free text');
 const values={bankId:'ab-bank-plc',accountTitle:'  Sample Holder  ',accountNumber:'00123456789',branchName:'  Main branch  ',routingNumber:'000123456',accountType:''};
 for(const field of ['bankId','accountTitle','accountNumber','branchName','routingNumber']){a.submit({...values,[field]:'   '});assert.equal(a.run('state.bankAccounts.length'),0);assert(a.get('modal').open);assert.match(a.get('bankAccountError').textContent,/Choose a bank and complete/);}
 a.setSaveOK(false);a.submit(values);assert.equal(a.run('state.bankAccounts.length'),0);assert(a.get('modal').open);assert.match(a.get('toast').textContent,/Changes could not be saved/);assert.equal(a.queued.length,0,'Failed local writes do not queue cloud backup');
 a.setSaveOK(true);a.submit(values);assert.equal(a.run('state.bankAccounts.length'),1);assert(!a.get('modal').open);
 const account=a.saved.bankAccounts[0];assert.equal(account.accountTitle,'Sample Holder');assert.equal(account.branchName,'Main branch');assert.equal(account.accountNumber,'00123456789');assert.equal(account.routingNumber,'000123456');assert.equal(account.accountType,'');
 assert.match(a.get('main').innerHTML,/class="bank-logo"/,'Saved accounts show their bank symbol');assert.match(a.get('main').innerHTML,/00123456789/);assert.match(a.get('main').innerHTML,/Share account for Sample Holder/);assert(!a.get('main').innerHTML.includes('<dt>Account Type</dt>'));
 const reload=app(a.saved);equal(reload.run('state.bankAccounts'),[account],'Accounts survive Android Vault reload with DOM storage disabled');
 assert.equal(a.queued.length,1);equal(a.queued[0].backup.data.bankAccounts,[account]);assert.equal(a.queued[0].sub,'account');equal(a.run('JSON.parse(cloudPayload()).data.bankAccounts'),[account]);
 const expected='Account Title: Sample Holder\nBank Name: AB Bank PLC\nBranch Name: Main branch\nAccount Number: 00123456789\nRouting Number: 000123456';
 assert.equal(a.run('bankAccountText(state.bankAccounts[0])'),expected);await a.run('shareBankAccount(state.bankAccounts[0].id)');equal(a.shared,[expected],'Android share receives exactly the five requested lines');
 a.run('bankAccountDetail(state.bankAccounts[0].id)');assert.match(a.get('modal').innerHTML,/<h2>Bank Information<\/h2>/);
 delete a.context.Vault.shareText;await a.run('shareBankAccount(state.bankAccounts[0].id)');assert.match(a.get('modal').innerHTML,/<h2>Share Bank Information<\/h2>/);
 a.run('bankAccountForm(state.bankAccounts[0].id)');assert.match(a.get('modal').innerHTML,/Edit Bank Information/);a.submit({...values,accountTitle:'Edited Holder',accountType:'Savings'});
 assert.equal(a.run('state.bankAccounts.length'),1);assert.equal(a.saved.bankAccounts[0].id,account.id,'Editing keeps the account ID');assert.equal(a.saved.bankAccounts[0].accountTitle,'Edited Holder');assert.match(a.get('main').innerHTML,/<span class="bank-account-type">Savings<\/span>/,'Cards show the optional account type as a compact chip');a.run('bankAccountDetail(state.bankAccounts[0].id)');assert.match(a.get('modal').innerHTML,/<dt>Account Type<\/dt><dd>Savings<\/dd>/,'Account details still label the optional type explicitly');a.run('closeModal()');
 a.run('bankAccountForm(state.bankAccounts[0].id)');a.setSaveOK(false);a.submit({...values,accountTitle:'Unsaved Holder'});assert.equal(a.saved.bankAccounts[0].accountTitle,'Edited Holder');assert(a.get('modal').open);a.setSaveOK(true);
 assert.equal(a.run('handleBack()'),true,'Back closes the form before navigation');assert.equal(a.run('tab'),'BankInformation');assert.equal(a.run('handleBack()'),true,'Back from bank information returns home');assert.equal(a.run('tab'),'Overview');assert.equal(a.run('handleBack()'),false);
 a.run("go('BankInformation');bankAccountForm();$('bankPicker').showModal()");assert.equal(a.run('handleBack()'),true);assert(!a.get('bankPicker').open);assert(a.get('modal').open,'Bank chooser closes before its parent form');a.run('handleBack();handleBack()');assert.equal(a.run('handleBack()'),false);
 a.context.deposit=asset;a.run('commit({...state,assets:[deposit]})');const banks=a.saved.bankAccounts;
 a.run("restoreData(Transfer.instrumentCSV(state));applyImport('replace')");equal(a.saved.bankAccounts,banks,'CSV replace preserves bank information');
 a.run("restoreData(JSON.stringify(Transfer.exportData(state,[deposit.id])));applyImport('replace')");equal(a.saved.bankAccounts,banks,'Selected-deposit JSON replace preserves bank information');
 const restored={...account,id:'restored-bank',accountTitle:'Restored Holder',accountType:'Current'};
 a.context.restoredBank=restored;a.run("restoreData(JSON.stringify(Transfer.exportData({...state,bankAccounts:[restoredBank]})));applyImport('replace')");equal(a.saved.bankAccounts,[restored],'Full JSON replacement restores the backed-up bank list');assert.match(a.get('toast').textContent,/Entries replaced/);
 a.run("go('BankInformation')");a.setConfirm(false);a.run('deleteBankAccount(state.bankAccounts[0].id)');equal(a.saved.bankAccounts,[restored],'Delete cancellation keeps the record');a.setConfirm(true);a.setSaveOK(false);a.run('deleteBankAccount(state.bankAccounts[0].id)');equal(a.saved.bankAccounts,[restored],'Delete save failure keeps the record');a.setSaveOK(true);a.run('deleteBankAccount(state.bankAccounts[0].id)');equal(a.saved.bankAccounts,[]);assert.match(a.get('main').innerHTML,/Your bank details belong here/);
 console.log('PASS: bank menu and cards, required dropdown/form fields, create/edit/delete, save failures, optional type, leading zeros, exact native share text, back navigation, CSV/selected preservation, full JSON restore and cloud inclusion.');
})().catch(error=>{console.error(error);process.exitCode=1;});
