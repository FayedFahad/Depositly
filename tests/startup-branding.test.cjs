const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const html=fs.readFileSync(process.env.DEPOSITLY_ANDROID_SOURCE||path.join(__dirname,'../app/assets/index.html'),'utf8');
const header=html.match(/<header>([\s\S]*?)<\/header>/)?.[1];assert(header,'Static header exists before app scripts');
const logo=header.match(/<div class="logo" id="logo">([\s\S]*?)<\/div>/)?.[1],menu=header.match(/<button id="settings"[^>]*>([\s\S]*?)<\/button>/)?.[1];
const title=header.match(/<h1>([^<]+)<\/h1>/)?.[1],tagline=header.match(/<div class="sub">([^<]+)<\/div>/)?.[1];
assert(html.indexOf('<header>')<html.indexOf('<script'),'The final brand is available before calculation and bank-catalog scripts execute');
assert.equal(title,'Depositly');assert.equal(tagline,'A little more clarity');assert.match(logo,/<svg\b[^>]*viewBox="0 0 32 32"/);assert.match(logo,/M9 7h6a9 9 0 0 1 0 18H9V7Z/);assert.match(logo,/m12 18 4-4 3 2 5-6/);assert.match(menu,/M4 6h16M4 12h16M4 18h16/);
assert.doesNotMatch(header,/Personal investment tracker|M3 9l9-6 9 6/,'The initial header never contains retired branding');

// Model the real static header, then pause between every inline script. The old
// bank-icon assignment failed here even though the final render later repaired it.
const nodes=new Map(),branding=[];
function get(id){
 if(!nodes.has(id)){
  let content=id==='logo'?logo:id==='settings'?menu:'',text=id==='header h1'?title:id==='header .sub'?tagline:'';
  const node={value:'',open:false,style:{},dataset:{},setAttribute(key,value){this[key]=value;},insertAdjacentHTML(_,value){this.innerHTML+=value;},showModal(){this.open=true;},close(){this.open=false;},focus(){}};
  Object.defineProperty(node,'innerHTML',{get:()=>content,set:value=>{content=value;if(id==='logo')branding.push({field:'logo',value});}});
  Object.defineProperty(node,'textContent',{get:()=>text,set:value=>{text=value;if(id==='header h1'||id==='header .sub')branding.push({field:id,value});}});
  nodes.set(id,node);
 }
 return nodes.get(id);
}
let saved=JSON.stringify({version:2,assets:[],paid:{},bankAccounts:[],dark:false,currency:'BDT'});
const context={console,Date,Math,JSON,Number,String,Set,Map,Array,Object,Error,Blob,URL,setTimeout:()=>0,confirm:()=>true,scrollTo(){},document:{title:'Depositly',getElementById:get,querySelector:get,body:{dataset:{},classList:{toggle(){}}}},localStorage:{getItem(){throw Error('Android DOM storage disabled');},setItem(){throw Error('Android DOM storage disabled');}},Vault:{load:()=>saved,owner:()=> 'sample-account',saveForAccount:(value,owner)=>{if(owner!=='sample-account')return false;saved=value;return true;}}};
context.window=context;vm.createContext(context);
const scripts=[...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)];assert(scripts.length>=4,'The staged check includes scripts before and after the bank catalog');
for(const [index,script] of scripts.entries()){
 vm.runInContext(script[1],context);
 assert.equal(get('logo').innerHTML,logo,'The first-painted mark stays the same after startup script '+index);
 assert.equal(get('header h1').textContent,title);assert.equal(get('header .sub').textContent,tagline);
}
for(const item of branding){if(item.field==='logo')assert.equal(item.value,logo);else assert.equal(item.value,item.field==='header h1'?title:tagline);}
assert.equal(context.document.title,'Depositly');
const nav=get('nav').innerHTML;assert.match(nav,/<button[^>]*data-tab="BankInformation"[^>]*>[\s\S]*?<span>Bank<\/span><\/button>/);assert.doesNotMatch(nav,/<span>Bank Information<\/span>/,'Only the bottom-tab label is shortened');
vm.runInContext("go('BankInformation')",context);assert.match(get('main').innerHTML,/<h2>Bank Information<\/h2>/,'The page title keeps its full name');
vm.runInContext('openAppMenu()',context);assert.match(get('modal').innerHTML,/<b>Bank Information<\/b>/,'The menu title keeps its full name');
console.log('PASS: final Depositly mark, menu and tagline exist before scripts, remain unchanged through every startup stage, and Bank tab keeps full Bank Information page/menu titles.');
