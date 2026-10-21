const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/Arnold/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../..');
const output = path.join(root, 'target/packaging-preview');
const fixture = JSON.parse(fs.readFileSync(path.join(root, 'target/packaging-browser-fixture.json'), 'utf8'));
const base = 'http://127.0.0.1:55185';
(async () => {
  const browser = await chromium.launch({headless:true, executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
  const context = await browser.newContext({viewport:{width:1440,height:1050},locale:'zh-CN'});
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  try {
    await page.goto(base + '/login');
    await page.locator('input[autocomplete="username"]').fill(fixture.username);
    await page.locator('input[autocomplete="current-password"]').fill('Packaging-Test-Only-2026!');
    const loginResponse = page.waitForResponse(response => response.url().includes('/api/auth/login') && response.request().method() === 'POST');
    await page.locator('button[type="submit"]').click();
    const response = await loginResponse;
    assert.equal(response.status(),200,await response.text());
    const login = await response.json();
    const api = async (endpoint, method='GET', body) => {
      const result = await context.request.fetch(base + endpoint, {method, data:body,
        headers:{Authorization:'Bearer '+login.token, 'Idempotency-Key':'browser-'+crypto.randomUUID()}});
      assert.ok(result.ok(), endpoint + ': ' + result.status() + ' ' + await result.text());
      return result.status() === 204 ? undefined : result.json();
    };
    // Restore this isolated acceptance fixture for a repeatable UI run; no business database is used.
    const initial = await api('/api/inventory/details/' + fixture.productSkuId);
    // Complete an interrupted acceptance count using the unchanged current balances.
    const previousCounts = await api('/api/stocktake/tasks?warehouseId=' + fixture.warehouseId);
    for (const previous of previousCounts.filter(task => ['COUNTING','REVIEWING'].includes(task.status))) {
      if (previous.status === 'REVIEWING') await api('/api/stocktake/tasks/'+previous.id+'/review','POST',{approved:false,comment:'Resume interrupted isolated acceptance run'});
      const previousItems = await api('/api/stocktake/tasks/'+previous.id+'/items');
      for (const item of previousItems) {
        const current = initial.find(row => row.batchId === item.batchId);
        assert.ok(current?.packaging, 'Interrupted test fixture must retain known packaging');
        await api('/api/stocktake/tasks/'+previous.id+'/items/'+item.id+'/count','POST',{countedQty:current.quantity,packaging:current.packaging});
      }
      await api('/api/stocktake/tasks/'+previous.id+'/finish','POST');
      await api('/api/stocktake/tasks/'+previous.id+'/review','POST',{approved:true,comment:'Complete interrupted isolated acceptance run without quantity changes'});
    }
    const refreshed = await api('/api/inventory/details/' + fixture.productSkuId);
    const sources = refreshed.filter(row => row.quantity > 0);
    assert.equal(sources.reduce((sum,row)=>sum+row.quantity,0),29);
    await api('/api/inventory/packaging/arrange','POST',{mode:'ARRANGE',
      sources:sources.map(row=>({batchId:row.batchId,version:row.version})),
      results:[{locationId:fixture.locationA,packaging:{packSize:12,sealedBoxes:1,openedQuantities:[9,8]}}]});
    await page.goto(base + '/inventory?search=' + fixture.skuCode);
    await page.getByText(fixture.skuCode, {exact:true}).waitFor();
    await page.screenshot({path:path.join(output,'01-inventory.png'),fullPage:true});
    await page.getByRole('button',{name:'查看批次'}).click();
    const drawer = page.locator('.ant-drawer-content').last();
    await drawer.locator('tbody tr[data-row-key] .ant-checkbox-wrapper').first().click();
    await drawer.getByRole('button',{name:'整理 / 开箱 / 移位',exact:true}).click();
    let modal = page.locator('.ant-modal[role="dialog"]').last();
    await modal.getByRole('spinbutton',{name:'拆箱实际数量 1',exact:true}).fill('17');
    await modal.getByRole('button',{name:'移除',exact:true}).last().click();
    await modal.getByText('超过标准箱规，按实物记录',{exact:true}).waitFor();
    await modal.getByRole('button',{name:'保 存',exact:true}).click({trial:true});
    await page.screenshot({path:path.join(output,'02-merge-17.png'),fullPage:true});
    let saved = page.waitForResponse(r=>r.url().endsWith('/api/inventory/packaging/arrange')&&r.request().method()==='POST');
    await modal.getByRole('button',{name:'保 存',exact:true}).click();
    assert.equal((await saved).status(),200);
    const merged = await api('/api/inventory/packaging/'+fixture.batchId);
    assert.deepEqual(merged.packaging,{packSize:12,sealedBoxes:1,openedQuantities:[17]});
    console.log('PASS: UI merge saved as one opened balance of 17; sealed box unchanged.');
    await modal.waitFor({state:'hidden'});
    await drawer.locator('tbody tr[data-row-key] .ant-checkbox-wrapper').first().click();
    await drawer.getByRole('button',{name:'整理 / 开箱 / 移位',exact:true}).click();
    modal = page.locator('.ant-modal[role="dialog"]').last();
    await modal.getByRole('spinbutton',{name:'拆箱实际数量 1',exact:true}).fill('9');
    await modal.getByRole('button',{name:'增加目标库位',exact:true}).click();
    const locations = await api('/api/locations/warehouse/'+fixture.warehouseId);
    const destination = locations.find(row=>row.id===fixture.locationB);
    await modal.locator('.ant-select-selector').last().click();
    await page.locator('.ant-select-item-option-content').filter({hasText:destination.locationCode}).last().click();
    await modal.getByRole('button',{name:'增加一个拆箱',exact:true}).last().click();
    await modal.getByRole('spinbutton',{name:'拆箱实际数量 1',exact:true}).last().fill('8');
    saved = page.waitForResponse(r=>r.url().endsWith('/api/inventory/packaging/arrange')&&r.request().method()==='POST');
    await modal.getByRole('button',{name:'保 存',exact:true}).click();
    assert.equal((await saved).status(),200);
    await modal.waitFor({state:'hidden'});
    await drawer.getByText(destination.locationCode,{exact:true}).waitFor();
    await drawer.getByText('8袋',{exact:false}).last().scrollIntoViewIfNeeded();
    await page.screenshot({path:path.join(output,'03-two-locations.png'),fullPage:true});
    const split = (await api('/api/inventory/details/'+fixture.productSkuId)).filter(row=>row.quantity>0);
    assert.equal(split.length,2);
    assert.deepEqual(split.find(row=>row.locationId===fixture.locationB).packaging,{packSize:12,sealedBoxes:0,openedQuantities:[8]});
    const task = await api('/api/stocktake/tasks','POST',{warehouseId:fixture.warehouseId,cycleType:'ADHOC'});
    await api('/api/stocktake/tasks/'+task.id+'/start','POST');
    const items = await api('/api/stocktake/tasks/'+task.id+'/items');
    await page.goto(base+'/inventory-governance/stocktake');
    const taskRow = page.locator('.ant-table-tbody tr').filter({hasText:task.taskNo}).first();
    await taskRow.getByRole('button',{name:/查\s*看/}).click();
    for (const item of items) {
      const row = page.locator('.ant-drawer-content tbody tr').filter({hasText:item.locationCode}).first();
      await row.getByRole('button',{name:'录入数量',exact:true}).click();
      const count = page.locator('.ant-modal[role="dialog"]').last();
      await count.getByRole('spinbutton',{name:'未拆整箱数',exact:true}).fill(item.locationId===fixture.locationA?'1':'0');
      const balances = item.locationId===fixture.locationA?[4,5]:[8];
      for (let i=0;i<balances.length;i++) {
        await count.getByRole('button',{name:'增加一个拆箱',exact:true}).click();
        await count.getByRole('spinbutton',{name:'拆箱实际数量 '+(i+1),exact:true}).fill(String(balances[i]));
      }
      await page.screenshot({path:path.join(output,'04-count-'+item.locationId+'.png'),fullPage:true});
      const counted = page.waitForResponse(r=>r.url().endsWith('/items/'+item.id+'/count'));
      await count.getByRole('button',{name:'保存实盘数',exact:true}).click();
      assert.equal((await counted).status(),200);
      await count.waitFor({state:'hidden'});
    }
    await api('/api/stocktake/tasks/'+task.id+'/finish','POST');
    const reviewedItems = await api('/api/stocktake/tasks/'+task.id+'/review-items');
    assert.ok(reviewedItems.every(item=>item.differenceQty===0));
    await api('/api/stocktake/tasks/'+task.id+'/review','POST',{approved:true,comment:'实际页面验收：只改变拆箱构成，总数不变'});
    const after = (await api('/api/inventory/details/'+fixture.productSkuId)).filter(row=>row.quantity>0);
    assert.deepEqual(after.find(row=>row.locationId===fixture.locationA).packaging,{packSize:12,sealedBoxes:1,openedQuantities:[4,5]});
    assert.equal(after.reduce((sum,row)=>sum+row.quantity,0),29);
    await page.goto(base+'/inventory?search='+fixture.skuCode);
    await page.getByRole('button',{name:'查看批次'}).click();
    await page.locator('.ant-drawer-content').last().getByText('4袋、5袋',{exact:false}).waitFor();
    await page.locator('.ant-drawer-content').last().getByText('4袋、5袋',{exact:false}).scrollIntoViewIfNeeded();
    await page.screenshot({path:path.join(output,'05-count-result.png'),fullPage:true});
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(output,'browser-result.json'),JSON.stringify({passed:true,sku:fixture.skuCode,taskId:task.id,
      checks:['login','merge 9+8 into 17','split locations 21+8','blind count actual opened balances','review zero quantity difference','final total 29'],after},null,2));
    console.log('PASS: real browser login, merge, split locations, blind count and review; final quantity 29.');
  } catch (error) {
    await page.screenshot({path:path.join(output,'browser-failure.png'),fullPage:true}).catch(()=>{});
    fs.writeFileSync(path.join(output,'browser-dom.txt'),await page.locator('body').innerText().catch(()=> ''));
    throw error;
  } finally {await browser.close();}
})().catch(error=>{console.error(String(error.message).replace(/Bearer\s+[^\s'"]+/g,'Bearer [redacted]'));process.exitCode=1;});
