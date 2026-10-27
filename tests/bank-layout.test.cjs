const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const source=process.env.DEPOSITLY_ANDROID_SOURCE||path.join(__dirname,'../app/assets/index.html');
const equal=(a,b,message)=>assert.deepEqual(JSON.parse(JSON.stringify(a)),b,message);
const asset={id:'deposit',type:'FDR',name:'Savings',ownership:'self',ownerName:'Me',principal:100000,rate:10,tds:10,cycle:1,cycleUnit:'months',start:'2026-01-01',first:'2026-02-01',maturity:'2027-01-01'};
function app(initial={version:2,assets:[],paid:{},dark:false,currency:'BDT'}){
 const html=fs.readFileSync(source,'utf8'),nodes=new Map(),copied=[],browserCopied=[],fallbackCopied=[];
 let saved=JSON.stringify(initial),saveOK=true,selectedNode=null;
 const node=()=>({innerHTML:'',textContent:'',value:'',style:{},dataset:{},open:false,setAttribute(key,value){this[key]=value;},insertAdjacentHTML(_,value){this.innerHTML+=value;},showModal(){this.open=true;},close(){this.open=false;},select(){selectedNode=this;},focus(){},remove(){}});
 const get=id=>{if(!nodes.has(id))nodes.set(id,node());return nodes.get(id);};
 class FormData{constructor(form){this.values=Object.entries(form.values||{});}[Symbol.iterator](){return this.values[Symbol.iterator]();}}
 const context={console,Date,Math,JSON,Number,String,Set,Map,Array,Object,Error,Blob,URL,FormData,setTimeout:()=>0,confirm:()=>true,scrollTo(){},document:{getElementById:get,querySelector:get,createElement:node,execCommand(command){if(command!=='copy'||!selectedNode)return false;fallbackCopied.push(selectedNode.value);return true;},body:{dataset:{},classList:{toggle(){}},appendChild(){},removeChild(){}}},localStorage:{getItem(){throw Error('Android DOM storage disabled');},setItem(){throw Error('Android DOM storage disabled');}},Vault:{load:()=>saved,owner:()=> 'account',saveForAccount:(text,sub)=>{if(!saveOK||sub!=='account')return false;saved=text;return true;},copyText:text=>{copied.push(text);return true;}}};
 context.window=context;vm.createContext(context);for(const script of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))vm.runInContext(script[1],context);
 return {run:code=>vm.runInContext(code,context),get,context,copied,browserCopied,fallbackCopied,setSaveOK:value=>saveOK=value,get saved(){return JSON.parse(saved);},useBrowserClipboard(){delete context.Vault.copyText;context.navigator={clipboard:{writeText:async text=>browserCopied.push(text)}};},useLegacyClipboard(){delete context.Vault.copyText;delete context.navigator;}};
}
function assertView(screen,view,count){
 assert.match(screen,new RegExp('\\bbank-account-layout-'+view+'\\b'));
 assert.equal((screen.match(/class="[^"]*\bbank-account-card\b[^"]*"/g)||[]).length,count,'Every account remains visible in '+view+' view');
 const buttons=screen.match(/<button\b[^>]*>/g)||[];
 for(const option of ['card','grid']){
  const button=buttons.find(value=>value.includes("setBankInformationView('"+option+"')"));
  assert(button,'View selector offers '+option);
  assert.match(button,new RegExp('aria-pressed="'+(option===view)+'"'));
 }
 assert(!buttons.some(value=>value.includes("setBankInformationView('list')")),'Compact view is no longer offered');
}

(async()=>{
 const a=app();
 assert.equal(a.run('bankInformationView()'),'card','Older state defaults to card view');
 const catalog=JSON.parse(a.run('JSON.stringify(BankCatalog)')),ab=catalog.find(bank=>bank.id==='ab-bank-plc');assert(ab);
 const aliasBank=catalog.find(bank=>bank.id!==ab.id&&bank.aliases?.length);
 const base={bankId:ab.id,bankName:ab.name,accountNumber:'000123456789',routingNumber:'000012345',accountType:'Savings'};
 const accounts=[
  {...base,id:'ab-z',accountTitle:'Zara',branchName:'North'},
  {...base,id:'ab-a-west',accountTitle:'Amina',branchName:'West'},
  {...base,id:'ab-a-east',accountTitle:'Amina',branchName:'East'},
  {...base,id:'ab-legacy',bankId:'',bankName:'  ab bank plc  ',accountTitle:'Legacy Holder',branchName:'Main'},
  {...base,id:'river-z',bankId:'',bankName:'River Bank',accountTitle:'Zara',branchName:'Main'},
  {...base,id:'river-a',bankId:'',bankName:'  RIVER BANK  ',accountTitle:'Amina',branchName:'Main'},
  {...base,id:'valley',bankId:'',bankName:'Valley Bank',accountTitle:'Ali',branchName:'Main'}
 ];
 if(aliasBank){accounts.push({...base,id:'alias-known',bankId:aliasBank.id,bankName:aliasBank.name,accountTitle:'Catalog Holder',branchName:'Main'},{...base,id:'alias-legacy',bankId:'',bankName:aliasBank.aliases[0],accountTitle:'Legacy Alias Holder',branchName:'Main'});}
 a.context.accounts=accounts;a.context.deposit=asset;a.run("commit({...state,assets:[deposit],bankAccounts:accounts});go('BankInformation')");
 const groups=JSON.parse(a.run('JSON.stringify(groupedBankAccounts())'));
 assert.equal(groups.length,aliasBank?4:3,'Known, normalized legacy and alias names group together while different unknown banks stay separate');
 const abGroup=groups.find(group=>group.bankId===ab.id);assert(abGroup);assert.equal(abGroup.name,ab.name);assert.equal(typeof abGroup.key,'string');
 equal(abGroup.accounts.map(account=>account.id),['ab-a-east','ab-a-west','ab-legacy','ab-z'],'Accounts sort by title and then branch within each bank');
 const riverGroup=groups.find(group=>group.accounts.some(account=>account.id==='river-a'));equal(riverGroup.accounts.map(account=>account.id),['river-a','river-z']);
 assert(!riverGroup.accounts.some(account=>account.id==='valley'),'Different unknown banks do not merge');
 if(aliasBank){const aliasGroup=groups.find(group=>group.bankId===aliasBank.id);assert(aliasGroup);equal(aliasGroup.accounts.map(account=>account.id),['alias-known','alias-legacy'],'Catalog aliases join their known bank group');}
 const order=groups.map(group=>group.name);equal(order,[...order].sort((left,right)=>left.localeCompare(right,'en',{numeric:true,sensitivity:'base'})),'Bank groups appear alphabetically');
 equal(a.run('state.bankAccounts'),accounts,'Grouping does not reorder stored records');
 const firstGroups=JSON.parse(a.run('JSON.stringify(groupedBankAccounts())'));a.run('bankInformation()');equal(a.run('groupedBankAccounts()'),firstGroups,'Rendering preserves stable group keys and ordering');
 assert.equal((a.get('main').innerHTML.match(/\bdata-bank-group(?:=|\s|>)/g)||[]).length,groups.length);
 assertView(a.get('main').innerHTML,'card',accounts.length);
 for(const view of ['grid','card','grid']){a.run("setBankInformationView('"+view+"')");assert.equal(a.run('bankInformationView()'),view);assert.equal(a.saved.bankInformationView,view);assertView(a.get('main').innerHTML,view,accounts.length);}
 a.run("go('Overview');go('Settings');go('BankInformation')");assert.equal(a.run('bankInformationView()'),'grid');assertView(a.get('main').innerHTML,'grid',accounts.length);
 const reload=app(a.saved);reload.run("go('BankInformation')");assert.equal(reload.run('bankInformationView()'),'grid');assertView(reload.get('main').innerHTML,'grid',accounts.length);
 const invalidStored=app({...a.saved,bankInformationView:'tiles'});assert.equal(invalidStored.run('bankInformationView()'),'card','Unknown stored view values fall back to card view');
 const retiredView=app({...a.saved,bankInformationView:'list'});retiredView.run("go('BankInformation')");assert.equal(retiredView.run('bankInformationView()'),'card','Previously saved Compact preference falls back to card view');assertView(retiredView.get('main').innerHTML,'card',accounts.length);
 a.setSaveOK(false);a.run("setBankInformationView('card')");assert.equal(a.run('bankInformationView()'),'grid');assert.equal(a.saved.bankInformationView,'grid');assert.match(a.get('toast').textContent,/Changes could not be saved/);a.setSaveOK(true);
 for(const value of ['list','tiles','',null,12]){a.context.invalidView=value;a.run('setBankInformationView(invalidView)');assert.equal(a.run('bankInformationView()'),'grid');assert.equal(a.saved.bankInformationView,'grid');}
 const persistedBeforeCopy=JSON.stringify(a.saved);
 await a.run("copyBankField('ab-a-east','accountNumber')");await a.run("copyBankField('ab-a-east','routingNumber')");equal(a.copied,['000123456789','000012345'],'Native copy preserves every leading zero');
 for(const [id,field] of [['missing','accountNumber'],['ab-a-east','accountTitle'],['ab-a-east','__proto__'],['ab-a-east','bankName']]){a.context.copyId=id;a.context.copyField=field;await a.run('copyBankField(copyId,copyField)');}assert.equal(a.copied.length,2,'Unknown accounts and fields never copy');
 a.run("bankAccountForm('ab-a-east')");a.get('accountNumber').value='0000000789';a.get('routingNumber').value='000000012';await a.run("copyBankFormField('accountNumber')");await a.run("copyBankFormField('routingNumber')");equal(a.copied.slice(2),['0000000789','000000012'],'Form copy uses the current unsaved values');
 await a.run("copyBankFormField('accountTitle')");a.get('accountNumber').value='   ';await a.run("copyBankFormField('accountNumber')");assert.equal(a.copied.length,4,'Invalid or empty form fields never copy');assert.equal(JSON.stringify(a.saved),persistedBeforeCopy,'Copying never changes saved account details or preferences');
 a.useBrowserClipboard();await a.run("copyBankField('ab-a-east','accountNumber')");equal(a.browserCopied,['000123456789'],'Browser Clipboard API receives the exact account identifier');
 a.useLegacyClipboard();await a.run("copyBankField('ab-a-east','routingNumber')");equal(a.fallbackCopied,['000012345'],'Legacy browser copy keeps the exact routing identifier');a.run('closeModal()');
 a.run("setBankInformationView('grid');globalThis.fullBackup=JSON.stringify(Transfer.exportData(state));setBankInformationView('card');restoreData(fullBackup);applyImport('replace')");assert.equal(a.saved.bankInformationView,'grid','Full JSON restore recovers the backed-up grid view');
 a.run("setBankInformationView('card');globalThis.fullBackup=JSON.stringify(Transfer.exportData(state));setBankInformationView('grid');restoreData(fullBackup);applyImport('replace')");assert.equal(a.saved.bankInformationView,'card','Full JSON restore recovers the backed-up card view');
 const cardReload=app(a.saved);cardReload.run("go('BankInformation')");assert.equal(cardReload.run('bankInformationView()'),'card','Card preference survives relaunch');assertView(cardReload.get('main').innerHTML,'card',accounts.length);
 a.run("globalThis.legacyBackup=Transfer.exportData(state);delete legacyBackup.bankInformationView;restoreData(JSON.stringify(legacyBackup));applyImport('replace')");assert.equal(a.saved.bankInformationView,'card','Older full JSON backups keep the current bank view');
 for(const view of ['tiles','list']){a.context.invalidView=view;a.run("restoreData(JSON.stringify({...Transfer.exportData(state),bankInformationView:invalidView}));applyImport('replace')");assert.equal(a.saved.bankInformationView,'card','Full JSON with an unsupported view keeps the current valid preference');}
 a.run("setBankInformationView('grid')");for(const view of ['list','tiles']){a.context.invalidView=view;a.run("restoreData(JSON.stringify({...Transfer.exportData(state),bankInformationView:invalidView}));applyImport('replace')");assert.equal(a.saved.bankInformationView,'grid','Legacy or unknown backup view preserves the selected grid preference');}a.run("setBankInformationView('card')");
 a.run("restoreData(Transfer.instrumentCSV(state));applyImport('replace')");assert.equal(a.saved.bankInformationView,'card','CSV replacement keeps the current bank view');
 a.run("globalThis.selectedBackup=JSON.stringify(Transfer.exportData({...state,bankInformationView:'grid'},[deposit.id]));restoreData(selectedBackup);applyImport('replace')");assert.equal(a.saved.bankInformationView,'card','Selected-deposit JSON replacement keeps the current bank view');
 console.log('PASS: bank grouping and aliases, stable alphabetical/account ordering, card/grid persistence and failures, retired Compact fallback, exact native/browser/form copying, and JSON/CSV preference preservation.');
})().catch(error=>{console.error(error);process.exitCode=1;});
