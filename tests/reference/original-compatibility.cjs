const fs=require('fs'),vm=require('vm'),assert=require('assert');
function engine(p){let s=fs.readFileSync(p,'utf8'),blocks=[...s.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(x=>x[1]),ctx=vm.createContext({});blocks.slice(0,2).forEach(x=>vm.runInContext(x,ctx));return ctx;}
const old=engine('original/assets/index.html'),app=engine('build/depositly/assets/index.html');
for(const start of ['2024-02-29','2026-01-31','2026-10-12'])for(const cycle of [1,3,6,12,100]){
const first=cycle===100?app.FDR.days(start,100):app.FDR.months(start,cycle),a={id:'test',type:'FDR',name:'Test deposit',ownerName:'Test',ownership:'self',principal:70000000,rate:10,tds:15,cycle,start,first,maturity:app.FDR.months(start,36)};
assert.equal(JSON.stringify(old.FDR.schedule(a,a.maturity)),JSON.stringify(app.FDR.schedule(a,a.maturity)));
const state={version:2,assets:[a],paid:{},dark:false,currency:'BDT',numberFormat:'south-asian'};
let json=JSON.parse(JSON.stringify(old.Transfer.exportData(state)));let imported=app.Transfer.migrate(json);assert.equal(imported.assets[0].principal,a.principal);
let csv=old.Transfer.instrumentCSV(state),csvState=app.Transfer.readInstrumentCSV(csv);assert.equal(csvState.assets[0].principal,a.principal);
assert(old.Transfer.migrate(JSON.parse(JSON.stringify(app.Transfer.exportData(imported)))));
}
assert.equal(app.FDR.amount({principal:70000000,rate:10,tds:15},30).net,495833);
console.log('15 schedule cases unchanged; legacy JSON and CSV import, JSON backward compatibility and known interest calculation passed.');
