const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const html=fs.readFileSync(process.env.DEPOSITLY_ANDROID_SOURCE||path.join(__dirname,'../app/assets/index.html'),'utf8');
const annual={id:'simple',type:'FDR',name:'Simple annual deposit',ownerName:'Sample Holder',ownership:'self',principal:10000000,rate:10,tds:10,cycle:12,cycleUnit:'months',start:'2024-01-01',first:'2025-01-01',maturity:'2025-01-01',tenureCount:1,tenureUnit:'years',autoRenew:true,interestType:'simple'};
const compound={...annual,id:'compound',name:'Compound annual deposit',interestType:'compound'};
const bank={id:'bank',bankId:'ab-bank-plc',bankName:'AB Bank PLC',accountTitle:'Sample Holder',accountNumber:'000123456789',branchName:'Main branch',routingNumber:'000012345',accountType:'Savings'};
const copy=value=>JSON.parse(JSON.stringify(value));
function app(assets=[],extra={}){
 const nodes=new Map(),scrolls=[];let mainWrites=0,saved=JSON.stringify({version:2,assets,paid:{},bankAccounts:[bank],dark:false,currency:'BDT',numberFormat:'south-asian',...extra});
 const forecastSection=/<section\b[^>]*id="homeForecast"[^>]*>([\s\S]*?)<\/section>/;
 const get=id=>{
  if(!nodes.has(id)){
   let content='';const node={id,textContent:'',value:'',style:{},dataset:{},open:false,setAttribute(key,value){this[key]=value;},insertAdjacentHTML(where,value){this.innerHTML=where==='beforeend'?this.innerHTML+value:value+this.innerHTML;},showModal(){this.open=true;},close(){this.open=false;}};
   Object.defineProperty(node,'innerHTML',{get(){if(id==='main'&&nodes.has('homeForecast'))return content.replace(forecastSection,(whole,body)=>whole.replace(body,nodes.get('homeForecast').innerHTML));return content;},set(value){content=value;if(id==='main'){mainWrites++;const found=forecastSection.exec(value);if(found)get('homeForecast').innerHTML=found[1];}}});nodes.set(id,node);
  }
  return nodes.get(id);
 };
 class FixedDate extends Date{constructor(...args){super(...(args.length?args:[2026,9,26,12]));}}
 const context={console,Date:FixedDate,Math,JSON,Number,String,Set,Map,Array,Object,Error,Blob,URL,setTimeout:()=>0,confirm:()=>true,scrollTo:(...args)=>scrolls.push(args),document:{getElementById:get,querySelector:get,body:{dataset:{},classList:{toggle(){}}}},localStorage:{getItem(){throw Error('Android DOM storage disabled');},setItem(){throw Error('Android DOM storage disabled');}},Vault:{load:()=>saved,owner:()=> 'account',saveForAccount:(text,owner)=>{if(owner!=='account')return false;saved=text;return true;}}};
 context.window=context;vm.createContext(context);for(const script of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))vm.runInContext(script[1],context);
 return {get,context,run:code=>vm.runInContext(code,context),screen:()=>get('main').innerHTML,forecast:()=>get('homeForecast').innerHTML,scrolls,get mainWrites(){return mainWrites;},get saved(){return JSON.parse(saved);}};
}
const forecastNet=app=>app.forecast().match(/<strong[^>]*>([^<]+)<\/strong>/)?.[1];
const estimate=(principal,days)=>{const gross=Math.round(principal*.1*days/360),tax=Math.round(gross*.1);return {gross,tax,net:gross-tax};};
const others={...annual,id:'others',name:'Excluded Others investment',ownership:'others',principal:1e12},future={...annual,id:'future',start:'2027-01-01',first:'2028-01-01',maturity:'2028-01-01'},expired={...annual,id:'expired',autoRenew:false};
const a=app([annual,compound,others,future,expired]),before=a.run('JSON.stringify(state)'),savedBefore=copy(a.saved);
const nav=a.get('nav').innerHTML,buttons=[...nav.matchAll(/<button\b[^>]*>[\s\S]*?<\/button>/g)].map(match=>match[0]);
assert.equal(buttons.length,5,'Bottom navigation remains five clear destinations');
for(const [index,route,label] of [[0,'Overview','Home'],[1,'Portfolio','Deposits'],[2,'Calendar','Calendar'],[3,'Payments','Payments'],[4,'BankInformation','Bank']]){assert(buttons[index].includes("go('"+route+"')"));assert.match(buttons[index],new RegExp('<span>'+label+'<\\/span>'));}
assert(!nav.includes("go('Yields')"));assert(!nav.includes('>Forecast<'),'The Forecast tab is removed');
assert.equal(a.run('tab'),'Overview');assert(a.screen().indexOf('id="homeForecast"')<a.screen().indexOf('<h3>Coming up next</h3>'),'The requested forecast portion precedes Coming up next');
assert.match(a.screen(),/<section\b[^>]*id="homeForecast"[^>]*aria-label="Interest forecast"/);
assert.doesNotMatch(a.forecast(),/forecast-line-chart|forecast-bars|View chart values|By investment|Net interest over time/,'Home contains the pictured selector and summary rather than charts or a deposit list');
for(const horizon of [30,90,180,360]){
 const writes=a.mainWrites,scrolls=a.scrolls.length;a.context.period=horizon;a.run('setForecastHorizon(period)');
 const simple=estimate(10000000,horizon),reinvested=estimate(11881000,horizon),gross=simple.gross+reinvested.gross,tax=simple.tax+reinvested.tax,net=simple.net+reinvested.net;
 a.context.expectedNet=net;assert.equal(forecastNet(a),a.run('money(expectedNet)'),'Forecast uses only active Self current balances at '+horizon+' days');
 assert.match(a.forecast(),new RegExp('Estimated net interest[^<]*'+horizon+' days'));
 for(const [label,amount] of [['Gross interest',gross],['TDS',tax]]){a.context.expectedAmount=amount;assert(a.forecast().includes('>'+label+'</span><b>'+a.run('money(expectedAmount)')+'</b>'),label+' retains its exact aggregate calculation');}
 for(const period of [30,90,180,360])assert.match(a.forecast(),new RegExp('aria-pressed="'+(period===horizon)+'"[^>]*onclick="setForecastHorizon\\('+period+'\\)"'),'The period control communicates the saved runtime selection');
 assert.equal(a.run('horizon'),horizon);assert.equal(a.run('tab'),'Overview');assert.equal(a.mainWrites,writes,'Changing the forecast only replaces its own portion');assert.equal(a.scrolls.length,scrolls,'Changing the forecast preserves Home scroll position');assert.doesNotMatch(a.forecast(),/NaN|Infinity/);
}
for(const invalid of [0,1,31,365,'30',null,NaN]){a.context.invalid=invalid;const previous=a.forecast();a.run('setForecastHorizon(invalid)');assert.equal(a.run('horizon'),360);assert.equal(a.forecast(),previous,'Unsupported period values leave the selection and amounts unchanged');}
assert.equal(a.run('JSON.stringify(state)'),before,'Forecast controls never change deposits, receipts, banks or preferences');assert.deepEqual(a.saved,savedBefore,'Forecast periods never cause a financial storage write');
a.run("go('BankInformation')");assert.equal(a.run('tab'),'BankInformation');assert.match(a.get('nav').innerHTML,/<button[^>]*onclick="go\('BankInformation'\)"[^>]*aria-current="page"/);assert.match(a.screen(),/Bank Information/);assert.match(a.screen(),/000123456789/);
assert.equal(a.run('handleBack()'),true);assert.equal(a.run('tab'),'Overview');assert.equal(a.run('horizon'),360);assert.equal(forecastNet(a),'৳19,693','Returning Home keeps the selected forecast period');assert.equal(a.run('handleBack()'),false,'Home still delegates double-back exit to native Android');
a.run("go('Yields')");assert.equal(a.run('tab'),'Overview','Old Forecast links resolve to Home');assert.doesNotMatch(a.screen(),/forecast-line-chart|forecast-bars/);
a.run('yields()');assert.equal(a.run('tab'),'Overview','Legacy direct forecast callbacks resolve to Home');assert.equal(a.run('JSON.stringify(state)'),before);
const noActive=app([others,future,expired]);assert.equal(forecastNet(noActive),'৳0');assert.match(noActive.forecast(),/Estimated net interest[^<]*30 days/);assert.doesNotMatch(noActive.forecast(),/NaN|Infinity/);
const empty=app();assert.equal(forecastNet(empty),'৳0');assert.match(empty.screen(),/id="homeForecast"/);assert(empty.screen().indexOf('id="homeForecast"')<empty.screen().indexOf('Make room for peace of mind'),'An empty Home still has the zero-value forecast before onboarding');
const zero=app([{...annual,rate:0}]);assert.equal(forecastNet(zero),'৳0');zero.run('setForecastHorizon(360)');assert.equal(forecastNet(zero),'৳0');
const overflow=app([{...compound,principal:1e13},annual]);assert.equal(forecastNet(overflow),'Unavailable','An invalid balance never becomes a false partial forecast');assert.match(overflow.screen(),/Current balance unavailable/);overflow.run('setForecastHorizon(360)');assert.equal(forecastNet(overflow),'Unavailable');assert.doesNotMatch(overflow.forecast(),/NaN|Infinity/);
console.log('PASS: five-tab bank navigation, Home forecast placement and four period calculations, current compound/Self-only totals, local updates without scroll/storage changes, compatibility/back navigation and empty/zero/overflow states.');
