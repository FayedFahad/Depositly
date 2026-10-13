const {chromium}=require('/opt/codex/runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('fs'),assert=require('assert');
(async()=>{
const browser=await chromium.launch({executablePath:process.cwd()+'/tools/headless/chrome-headless-shell-linux64/chrome-headless-shell',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
const context=await browser.newContext({viewport:{width:393,height:852},deviceScaleFactor:1});
const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('dialog',async d=>d.accept());
await page.goto('file://'+process.cwd()+'/build/depositly/assets/index.html');
await page.waitForTimeout(300);await page.screenshot({path:'build/empty-home.png',fullPage:true});
assert.equal(await page.title(),'Depositly');
await page.getByRole('button',{name:'Add an investment',exact:true}).click();
await page.locator('#ownerName').fill('Sample Owner');
await page.getByRole('button',{name:'Choose Bangladesh bank'}).click();
await page.locator('#bankSearch').fill('Mutual Trust');
await page.locator('.bank-option').first().click();
await page.locator('#name').fill('Monthly income');
await page.locator('#reference').fill('0012345678');
await page.locator('#principal').fill('700000');
await page.locator('#payoutChoice').selectOption('1');
await page.locator('#start').fill('2026-10-01');
await page.locator('#tenureChoice').selectOption('1year');
await page.locator('#rate').fill('10');
await page.locator('#tds').fill('15');
await page.waitForTimeout(300);await page.screenshot({path:'build/add-form.png',fullPage:true});
await page.getByRole('button',{name:'Save investment',exact:true}).click();
assert.equal(await page.evaluate(()=>state.assets.length),1);
assert.equal(await page.evaluate(()=>state.assets[0].reference),'0012345678');
const base=await page.evaluate(()=>({...state.assets[0]}));
await page.evaluate(base=>{
const savings={...base,id:'sampleSavings',type:'NSC',name:'Family savings',bankId:'',bankName:'Savings certificate',principal:100000000,rate:11.8,tds:10,cycle:3,cycleUnit:'months',first:'2026-11-01',start:'2026-08-01',maturity:'2029-08-01',tenureCount:3,tenureUnit:'years'};
const other={...base,id:'sampleOthers',name:'Other owner deposit',ownerName:'Other Owner',ownership:'others',principal:25000000};
commit({...state,assets:[base,savings,other]});render();
},base);
await page.reload();assert.equal(await page.evaluate(()=>state.assets.length),3);
await page.waitForTimeout(300);await page.screenshot({path:'build/home.png',fullPage:true});
for (const [tabName,file] of [['Deposits','deposits'],['Calendar','calendar'],['Payments','payments'],['Forecast','forecast']]){
await page.getByRole('button',{name:tabName,exact:true}).click();
assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true,tabName+' horizontal overflow');
await page.waitForTimeout(300);await page.screenshot({path:'build/'+file+'.png',fullPage:true});
}
await page.evaluate(()=>{go('Payments');paymentFilter='Upcoming';payments();});
await page.locator('.payment').first().click();
await page.locator('#amount').fill('4900');await page.locator('#received').fill('2026-10-12');
await page.getByRole('button',{name:'Mark as received',exact:true}).click();
assert.equal(await page.evaluate(()=>Object.keys(state.paid).length),1);
await page.evaluate(()=>{window.Vault={save:(v)=>{localStorage.setItem('fdr-vault',v);return true},exportFile:(data,name,mime)=>window.lastExport={data,name,mime}};exportEntries(undefined,'json');});
const exported=await page.evaluate(()=>lastExport);assert(exported.name.startsWith('depositly-'));
await page.evaluate(data=>{restoreData(data);},exported.data);
assert(await page.getByRole('heading',{name:'Review import'}).isVisible());
await page.getByRole('button',{name:'Merge entries',exact:true}).click();
assert.equal(await page.evaluate(()=>state.assets.length),3);
await page.evaluate(()=>{delete window.Vault;exportEntries=exportEntries;});
await page.evaluate(()=>{go('Portfolio');});
await page.locator('.holding').first().click();
await page.getByRole('button',{name:'Edit',exact:true}).click();
await page.locator('#notes').fill('Edited successfully');
await page.getByRole('button',{name:'Save investment',exact:true}).click();
assert.equal(await page.evaluate(()=>state.assets.some(a=>a.notes==='Edited successfully')),true);
await page.getByRole('button',{name:'Settings',exact:true}).click();
await page.getByRole('button',{name:'Turn on',exact:true}).click();
await page.getByRole('button',{name:'Home',exact:true}).click();
await page.waitForTimeout(300);await page.screenshot({path:'build/dark-home.png',fullPage:true});
for(const width of [320,360,393,430,768]){
await page.setViewportSize({width,height:852});
for (const tab of ['Overview','Portfolio','Calendar','Payments','Yields','Settings']){
await page.evaluate(tab=>go(tab),tab);
assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),`${tab} overflow at ${width}`);
}
}
await page.setViewportSize({width:393,height:852});await page.evaluate(()=>{commit({...state,dark:false});go('Calendar');month='2026-11-01';selectedDay='2026-11-01';calendar();});assert(await page.locator('.day-amount').count()>0);await page.waitForTimeout(300);await page.screenshot({path:'build/calendar-populated.png',fullPage:true});assert.deepEqual(errors,[]);fs.writeFileSync('build/ui-test-result.json',JSON.stringify({passed:true,checks:['empty and populated home','create deposit via bank picker','leading-zero account number','persistence after reload','navigation','calendar','mark payment received','JSON export and duplicate-safe merge','edit deposit','dark mode','no horizontal overflow at 320/360/393/430/768px','no JavaScript runtime errors']},null,2));
console.log('All UI tests passed');await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
