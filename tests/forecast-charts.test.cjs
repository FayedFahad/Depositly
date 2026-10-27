const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const source=process.env.DEPOSITLY_ANDROID_SOURCE||path.join(__dirname,'../app/assets/index.html'),html=fs.readFileSync(source,'utf8');
const annual={id:'simple',type:'FDR',name:'Simple annual deposit',ownerName:'Sample Holder',ownership:'self',principal:10000000,rate:10,tds:10,cycle:12,cycleUnit:'months',start:'2024-01-01',first:'2025-01-01',maturity:'2025-01-01',tenureCount:1,tenureUnit:'years',autoRenew:true,interestType:'simple'};
const compound={...annual,id:'compound',name:'Compound annual deposit',interestType:'compound'};
function app(assets=[]){
 const nodes=new Map(),get=id=>{if(!nodes.has(id))nodes.set(id,{innerHTML:'',textContent:'',value:'',style:{},dataset:{},open:false,setAttribute(key,value){this[key]=value;},insertAdjacentHTML(where,value){this.innerHTML=where==='beforeend'?this.innerHTML+value:value+this.innerHTML;},showModal(){this.open=true;},close(){this.open=false;}});return nodes.get(id);};
 class FixedDate extends Date{constructor(...args){super(...(args.length?args:[2026,9,26,12]));}}
 class FormData{constructor(form){this.entries=Object.entries(form.values||{});}[Symbol.iterator](){return this.entries[Symbol.iterator]();}}
 let saved=JSON.stringify({version:2,assets,paid:{},bankAccounts:[],dark:false,currency:'BDT',numberFormat:'south-asian'});
 const context={console,Date:FixedDate,Math,JSON,Number,String,Set,Map,Array,Object,Error,Blob,URL,FormData,setTimeout:()=>0,confirm:()=>true,scrollTo(){},document:{getElementById:get,querySelector:get,body:{dataset:{},classList:{toggle(){}}}},localStorage:{getItem(){throw Error('Android DOM storage disabled');},setItem(){throw Error('Android DOM storage disabled');}},Vault:{load:()=>saved,owner:()=> 'account',saveForAccount:(text,owner)=>{if(owner!=='account')return false;saved=text;return true;}}};
 context.window=context;vm.createContext(context);for(const script of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))vm.runInContext(script[1],context);
 return {context,get,run:code=>vm.runInContext(code,context),screen:()=>get('main').innerHTML,get saved(){return JSON.parse(saved);}};
}
const chartRows=screen=>[...screen.matchAll(/<tr><th scope="row">(\d+)<\/th><td>([^<]+)<\/td><\/tr>/g)].map(([,days,net])=>({days:Number(days),net}));
const estimate=(principal,days)=>{const gross=Math.round(principal*.1*days/360),tax=Math.round(gross*.1);return {gross,tax,net:gross-tax};};

const others={...annual,id:'others',name:'Excluded Others investment',ownership:'others',principal:1e12},future={...annual,id:'future',start:'2027-01-01',first:'2028-01-01',maturity:'2028-01-01'},expired={...annual,id:'expired',autoRenew:false};
const active=app([annual,compound,others,future,expired]),original=active.run('JSON.stringify(state.assets)');
assert.equal(active.run('currentInvestmentBalance(state.assets[1]).amount'),11881000);
for(const horizon of [30,90,180,360]){
 const simple=estimate(10000000,horizon),reinvested=estimate(11881000,horizon),net=simple.net+reinvested.net,periods=[...new Set(Array.from({length:13},(_,i)=>Math.round(horizon*i/12)))];
 active.context.input={gross:simple.gross+reinvested.gross,tax:simple.tax+reinvested.tax,net,points:periods.map(days=>({days,net:estimate(10000000,days).net+estimate(11881000,days).net}))};
 const screen=active.run('ForecastCharts.render({...input,formatMoney:money})');active.context.expectedNet=net;assert(screen.includes('<b>'+active.run('money(expectedNet)')+'</b>'),'The shared chart renderer formats the selected cumulative interest');
 assert.match(screen,/<h3>Net interest over time<\/h3>/);assert.match(screen,/<h3>Interest &amp; tax<\/h3>/);assert.match(screen,/role="img" aria-label="Projected net interest after /);assert.match(screen,/View chart values/);assert.match(screen,/role="list" aria-label="Interest and tax breakdown"/);
 assert.doesNotMatch(screen,/By investment|Excluded Others investment|Compound annual deposit|Current balances include net interest|Future capitalizations|No interim cash payouts/);
 assert.doesNotMatch(screen,/NaN|Infinity/);const points=chartRows(screen);assert.equal(points.length,13);assert.equal(points[0].days,0);assert.equal(points.at(-1).days,horizon);
 for(const point of points){active.context.expectedPoint=estimate(10000000,point.days).net+estimate(11881000,point.days).net;assert.equal(point.net,active.run('money(expectedPoint)'),'Chart values use the same per-investment rounding at day '+point.days);}
 active.context.breakdown={gross:simple.gross+reinvested.gross,tax:simple.tax+reinvested.tax,net};
 for(const label of ['Gross interest','TDS','Net interest']){const key={ 'Gross interest':'gross',TDS:'tax','Net interest':'net'}[label];assert(screen.includes('<span>'+label+'</span><b>'+active.run('money(breakdown.'+key+')')+'</b>'),'The chart labels '+label+' with its actual amount');}
}
assert.equal(active.run('JSON.stringify(state.assets)'),original,'Rendering shared charts never changes investment terms');assert.equal(active.saved.assets[1].principal,compound.principal);
active.run("go('Portfolio')");assert.doesNotMatch(active.screen(),/Current balances include net interest|Compound deposits have no interim cash payouts|Record the amount received for an accurate payment history/,'The removed explanatory portfolio note stays removed');

const emptyChart=active.run("ForecastCharts.render({emptyMessage:'Add an investment to see your forecast charts.'})");assert.match(emptyChart,/<h3>Charts<\/h3>/);assert.match(emptyChart,/Add an investment to see your forecast charts/);assert.doesNotMatch(emptyChart,/forecast-line-chart|forecast-bars|NaN|Infinity/);
const zeroChart=active.run('ForecastCharts.render({points:input.points.map(point=>({...point,net:0})),gross:0,tax:0,net:0,formatMoney:money})');assert.equal(chartRows(zeroChart).length,13);assert(chartRows(zeroChart).every(row=>row.net==='৳0'));assert.doesNotMatch(zeroChart,/NaN|Infinity/);
const unavailable=active.run('ForecastCharts.render({...input,gross:null,tax:null,net:null,formatMoney:money})');assert.match(unavailable,/Charts unavailable/);assert.doesNotMatch(unavailable,/forecast-line-chart|forecast-bars|NaN|Infinity/,'An invalid balance never becomes a false partial shared chart');

const form=app();form.run('assetForm()');assert.match(form.get('modal').innerHTML,/<label for="name">Investment Name<\/label>/);form.get('name').value='';form.run("selectBank('ab-bank-plc')");assert.equal(form.get('name').value,'','Selecting a bank leaves an empty investment name for manual input');assert.equal(form.get('bankName').value,'AB Bank PLC');form.get('name').value='My chosen deposit';form.run("selectBank('brac-bank-plc')");assert.equal(form.get('name').value,'My chosen deposit','Changing the bank preserves an entered name');assert.equal(form.get('bankName').value,'BRAC Bank PLC');

active.context.chartInput={points:[{days:0,net:0},{days:30,net:75000}],gross:83333,tax:8333,net:75000};
const escaped=active.run("ForecastCharts.render({...chartInput,formatMoney:n=>'<b>'+n+'&\"</b>'})");assert.doesNotMatch(escaped,/<b>75000&/);assert.match(escaped,/&lt;b&gt;75000&amp;&quot;&lt;\/b&gt;/);assert.match(escaped,/aria-label="Projected net interest after 30 days: &lt;b&gt;/);
const escapedEmpty=active.run("ForecastCharts.render({emptyMessage:'<script>Sample</script>'})");assert.match(escapedEmpty,/&lt;script&gt;Sample&lt;\/script&gt;/);assert.doesNotMatch(escapedEmpty,/<script>/);
for(const field of ['gross','tax','net']){active.context.badField=field;assert.match(active.run("ForecastCharts.render({...chartInput,[badField]:null,formatMoney:money})"),/Charts unavailable/);}

const websiteRoot=path.resolve(__dirname,'../../depositly-website'),webHelperPath=path.join(websiteRoot,'assets/forecast-charts.js');
if(fs.existsSync(webHelperPath)){
 const inline=html.match(/const ForecastCharts=\(\(\)=>\{[\s\S]*?\/\* End shared forecast chart renderer\. \*\//)?.[0].replace(/\/\* End shared forecast chart renderer\. \*\//,'').trim();assert(inline,'Phone contains the shared chart helper');
 const webHelper=fs.readFileSync(webHelperPath,'utf8').replace(/\r\n/g,'\n').replace(/\nexport const renderForecastCharts=ForecastCharts\.render;\s*$/,'').trim();assert.equal(inline.replace(/\r\n/g,'\n'),webHelper,'Phone and website use the same chart markup and value formatting');
 assert.equal(fs.readFileSync(path.join(__dirname,'../app/assets/forecast-charts.css'),'utf8').replace(/\r\n/g,'\n'),fs.readFileSync(path.join(websiteRoot,'assets/forecast-charts.css'),'utf8').replace(/\r\n/g,'\n'),'Phone and website share chart styles');
}
console.log('PASS: retained shared chart renderer values/breakdown and accessibility, empty/zero/unavailable states, escaped labels, manual investment names and cross-client helper/style parity.');
