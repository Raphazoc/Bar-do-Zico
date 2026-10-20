import { test, expect, type Page } from '@playwright/test';

async function login(page:Page,username=process.env.BAR_E2E_EMAIL!,password=process.env.BAR_E2E_PASSWORD!){
  await page.goto('/');
  await page.getByLabel('Usuário ou e-mail',{exact:true}).fill(username);
  await page.getByLabel('Senha',{exact:true}).fill(password);
  await page.getByRole('button',{name:'Entrar',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Mesas do bar',exact:true})).toBeVisible();
}

async function command(page:Page,operation:string,body:Record<string,string>){
  expect(process.env.BAR_TEST_DATABASE,'Cadastros de teste exigem banco descartável.').toBe('true');
  const response=await page.request.post('/api/commands/'+operation,{data:body,headers:{'X-Bar-Request':'1','Idempotency-Key':crypto.randomUUID()}});
  expect(response.status()).toBe(200);return response.json();
}

async function navigate(page:Page,name:string){
  const nav=page.getByRole('navigation',{name:'Navegação principal',exact:true});
  const openMenu=page.getByRole('button',{name:'Abrir menu',exact:true,includeHidden:true});
  await openMenu.waitFor({state:'attached'});
  if(await openMenu.isVisible())await openMenu.click();
  await nav.getByRole('button',{name,exact:true}).click();
}

test('login permite conferir a senha e cabe na tela sem carregar recursos externos',async({page},info)=>{
  const failures:string[]=[];page.on('pageerror',error=>failures.push(error.message));
  const external:string[]=[];
  await page.route('**/*',route=>{
    if(new URL(route.request().url()).hostname==='127.0.0.1')return route.continue();
    external.push(route.request().url());return route.abort();
  });
  await page.goto('/');
  const password=page.getByLabel('Senha',{exact:true});
  await password.fill('Senha de demonstração');
  await expect(password).toHaveAttribute('type','password');
  await page.getByRole('button',{name:'Mostrar senha',exact:true}).click();
  await expect(password).toHaveAttribute('type','text');
  await expect(password).toHaveValue('Senha de demonstração');
  await page.getByRole('button',{name:'Ocultar senha',exact:true}).click();
  await expect(password).toHaveAttribute('type','password');
  await password.clear();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
  await page.screenshot({path:'test-results/'+info.project.name+'-login.png',fullPage:true});
  expect(external).toEqual([]);expect(failures).toEqual([]);
});

test('navegação indica a tela atual e o menu móvel fecha ao escolher uma tela ou usar Escape',async({page},info)=>{
  await login(page);
  const nav=page.getByRole('navigation',{name:'Navegação principal',exact:true,includeHidden:true});
  const mobile=await page.getByRole('button',{name:'Abrir menu',exact:true}).isVisible();
  if(mobile){
    await page.getByRole('button',{name:'Abrir menu',exact:true}).click();
    await expect(page.getByRole('button',{name:'Fechar menu',exact:true})).toHaveAttribute('aria-expanded','true');
    await page.keyboard.press('Escape');
    await expect(page.getByRole('button',{name:'Abrir menu',exact:true})).toHaveAttribute('aria-expanded','false');
  }
  for(const [tab,heading] of [['Mesas e cardápio','Mesas e cardápio'],['Cozinha e bar','Pedidos para preparar'],['Caixa','Caixa e recebimentos'],['Vendas','Vendas do bar'],['Funcionários','Funcionários'],['Meu acesso','Meu acesso'],['Mesas','Mesas do bar']]){
    await navigate(page,tab);
    await expect(page.getByRole('heading',{name:heading,exact:true})).toBeVisible();
    await expect(nav.getByRole('button',{name:tab,exact:true,includeHidden:true})).toHaveAttribute('aria-current','page');
    if(mobile)await expect(page.getByRole('button',{name:'Abrir menu',exact:true})).toHaveAttribute('aria-expanded','false');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
  }
  await page.screenshot({path:'test-results/'+info.project.name+'-salao.png',fullPage:true});
});

test('busca e filtros combinam área e estado sem abrir uma conta',async({page},info)=>{
  expect(process.env.BAR_TEST_DATABASE,'Cadastros de teste exigem banco descartável.').toBe('true');
  await login(page);
  const suffix=info.project.name+'-'+Date.now();
  const firstArea=await command(page,'area.save',{name:'Filtro '+suffix});
  const otherArea=await command(page,'area.save',{name:'Outra área '+suffix});
  const firstNumber='Livre-'+Date.now().toString().slice(-8);
  const occupiedNumber='Uso-'+Date.now().toString().slice(-8);
  const otherNumber='Outra-'+Date.now().toString().slice(-8);
  await command(page,'table.save',{areaId:firstArea.id,number:firstNumber});
  const occupied=await command(page,'table.save',{areaId:firstArea.id,number:occupiedNumber});
  await command(page,'table.save',{areaId:otherArea.id,number:otherNumber});
  await command(page,'account.open',{tableId:occupied.id});
  await page.reload();
  await expect(page.getByRole('heading',{name:'Mesas do bar',exact:true})).toBeVisible();
  const cards=page.locator('button.table-card');
  await expect(cards.filter({has:page.locator('strong',{hasText:firstNumber})})).toBeVisible();
  const writes:string[]=[];
  page.on('request',request=>{if(request.method()==='POST'&&request.url().includes('/api/commands/'))writes.push(request.url());});
  await page.getByLabel('Local das mesas',{exact:true}).selectOption({label:'Filtro '+suffix});
  await expect(cards).toHaveCount(2);
  await page.getByLabel('Estado das mesas',{exact:true}).selectOption({label:'Ocupadas'});
  await expect(cards).toHaveCount(1);await expect(cards.first().locator('strong')).toHaveText(occupiedNumber);
  await page.getByLabel('Buscar mesa',{exact:true}).fill(firstNumber);
  await expect(cards).toHaveCount(0);
  await page.getByLabel('Estado das mesas',{exact:true}).selectOption({label:'Livres'});
  await expect(cards).toHaveCount(1);await expect(cards.first().locator('strong')).toHaveText(firstNumber);
  await page.getByLabel('Buscar mesa',{exact:true}).clear();
  await page.getByLabel('Local das mesas',{exact:true}).selectOption({label:'Outra área '+suffix});
  await expect(cards).toHaveCount(1);await expect(cards.first().locator('strong')).toHaveText(otherNumber);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
  expect(writes).toEqual([]);
});

test('preparação mostra o QR local e orientação de backups',async({page})=>{
  await login(page);
  await navigate(page,'Configurações');
  await expect(page.getByRole('heading',{name:'Configurações do bar',exact:true})).toBeVisible();
  await expect(page.getByRole('heading',{name:'Cópias de segurança',exact:true})).toBeVisible();
  const qr=page.getByRole('img',{name:'QR Code do endereço local do sistema'});await expect(qr).toBeVisible();
  await expect.poll(()=>qr.evaluate((node:HTMLImageElement)=>node.complete&&node.naturalWidth>0)).toBeTruthy();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
});

test('identificação do ambiente aparece antes e depois do login',async({page})=>{
  const training=process.env.BAR_TRAINING==='true';
  await page.goto('/');await expect(page.getByRole('button',{name:'Entrar',exact:true})).toBeVisible();
  if(training)await expect(page.getByRole('note').getByText('TREINAMENTO',{exact:true})).toBeVisible();
  else await expect(page.getByRole('note')).toHaveCount(0);
  await page.getByLabel('Usuário ou e-mail',{exact:true}).fill(process.env.BAR_E2E_EMAIL!);await page.getByLabel('Senha',{exact:true}).fill(process.env.BAR_E2E_PASSWORD!);await page.getByRole('button',{name:'Entrar',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Mesas do bar',exact:true})).toBeVisible();
  if(training)await expect(page.getByRole('note').getByText('TREINAMENTO',{exact:true})).toBeVisible();
  const cookies=await page.context().cookies();
  expect(cookies.some(c=>c.name===(training?'BAR_TRAINING_SESSION':'BAR_SESSION'))).toBeTruthy();
});

test('cadastro manual, pedido, produção, recebimento e fechamento sem recursos externos',async({page},info)=>{
  const failures:string[]=[];page.on('pageerror',e=>failures.push(e.message));
  await page.route('**/*',route=>new URL(route.request().url()).hostname==='127.0.0.1'?route.continue():route.abort());
  const suffix=info.project.name+'-'+Date.now();
  await login(page);
  await navigate(page,'Mesas e cardápio');
  const panel=(name:string)=>page.locator('section.panel').filter({has:page.getByRole('heading',{name,exact:true})}).first();
  const area=panel('Locais do bar');await area.getByLabel('Nome do local',{exact:true}).fill('Salão '+suffix);await area.getByRole('button',{name:'Salvar local',exact:true}).click();await expect(area.getByRole('cell',{name:'Salão '+suffix,exact:true})).toBeVisible();
  const tables=panel('Mesas');await tables.getByRole('combobox',{name:'Local da mesa',exact:true}).selectOption({label:'Salão '+suffix});const number=Date.now().toString().slice(-9);await tables.getByLabel('Número ou nome da mesa').fill(number);await tables.getByRole('button',{name:'Salvar mesa',exact:true}).click();await expect(tables.getByRole('cell',{name:number,exact:true})).toBeVisible();
  const category=panel('Categorias do cardápio');await category.getByLabel('Nome da categoria').fill('Bebidas '+suffix);await category.getByRole('button',{name:'Salvar categoria',exact:true}).click();await expect(category.getByRole('cell',{name:'Bebidas '+suffix,exact:true})).toBeVisible();
  const product=panel('Produtos');await product.getByLabel('Nome do produto').fill('Bebida '+suffix);await product.getByRole('combobox',{name:'Categoria',exact:true}).selectOption({label:'Bebidas '+suffix});await product.getByLabel('Preço (R$)',{exact:true}).fill('10');await product.getByRole('combobox',{name:'Destino de preparo',exact:true}).selectOption('BAR');await product.getByRole('button',{name:'Salvar produto',exact:true}).click();await expect(product.getByRole('cell',{name:'Bebida '+suffix,exact:true})).toBeVisible();
  await navigate(page,'Mesas');await page.locator('button.table-card').filter({has:page.locator('strong',{hasText:number})}).click();await expect(page.getByRole('heading',{name:'Novo pedido',exact:true})).toBeVisible();
  await page.getByRole('combobox',{name:'Produto',exact:true}).selectOption({label:'Bebida '+suffix+' · R$ 10,00'});await page.getByLabel('Quantidade',{exact:true}).fill('2');await page.getByRole('button',{name:'Adicionar ao pedido',exact:true}).click();await page.getByRole('button',{name:'Enviar pedido (1 itens)',exact:true}).click();await expect(page.getByRole('cell',{name:'Bebida '+suffix,exact:true})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
  await page.screenshot({path:'test-results/'+info.project.name+'-atendimento.png',fullPage:true});
  await navigate(page,'Cozinha e bar');const item=page.locator('section.production-card').filter({hasText:'Bebida '+suffix});
  for(const status of ['Aceito','Em preparo','Pronto','Entregue'])await item.getByRole('button',{name:status,exact:true}).click();
  await expect(item).toHaveCount(0);
  await navigate(page,'Caixa');await page.getByText('Abrir caixa — começar o dia',{exact:true}).click();
  const terminals=panel('Caixas');await terminals.getByLabel('Nome do caixa').fill('Caixa '+suffix);await terminals.getByRole('button',{name:'Salvar caixa',exact:true}).click();await expect(terminals.getByRole('cell',{name:'Caixa '+suffix,exact:true})).toBeVisible();
  await page.getByRole('combobox',{name:'Caixa',exact:true}).selectOption({label:'Caixa '+suffix});await page.getByLabel('Dinheiro inicial (R$)').fill('50');await page.getByRole('button',{name:'Abrir caixa',exact:true}).click();
  const receive=panel('Receber pagamento');await receive.getByRole('combobox',{name:'Conta',exact:true}).selectOption({label:'Mesa '+number+' · saldo R$ 20,00'});await receive.getByRole('combobox',{name:'Forma',exact:true}).selectOption('DINHEIRO');await receive.getByLabel('Valor aplicado na conta (R$)').fill('20');await receive.getByLabel('Valor entregue (dinheiro; deixe vazio nas outras formas)').fill('50');await receive.getByRole('button',{name:'Confirmar recebimento',exact:true}).click();await expect(page.getByRole('heading',{name:'Comprovante simples',exact:true})).toBeVisible();
  const close=panel('Fechar caixa — terminar o dia');for(const method of ['Dinheiro','Pix','Débito','Crédito','Voucher','Outro'])await close.getByLabel(method+' contado (R$)',{exact:true}).fill(method==='Dinheiro'?'70':'0');await close.getByRole('button',{name:'Fechar caixa',exact:true}).click();await expect(page.getByRole('heading',{name:'Fechamentos registrados',exact:true})).toBeVisible();
  await navigate(page,'Vendas');await page.getByRole('button',{name:'Consultar período',exact:true}).click();await expect(page.getByRole('heading',{name:'Recebimentos no período',exact:true})).toBeVisible();
  expect(failures).toEqual([]);
});

test('perda de conexão bloqueia lançamentos e permite reconectar',async({page,context})=>{
  await login(page);
  await context.setOffline(true);await expect(page.getByText('● Sem conexão com o bar',{exact:true})).toBeVisible({timeout:22000});await context.setOffline(false);await page.getByRole('button',{name:'Reconectar',exact:true}).click();await expect(page.getByText('● Conectado ao bar',{exact:true})).toBeVisible();
});

test('resposta perdida é conferida com a mesma chave sem duplicar cadastro',async({page})=>{
  await login(page);await navigate(page,'Mesas e cardápio');
  const area=page.locator('section.panel').filter({has:page.getByRole('heading',{name:'Locais do bar',exact:true})}).first();
  const name='Envio conferido '+Date.now();await area.getByLabel('Nome do local',{exact:true}).fill(name);
  await page.route('**/api/commands/area.save',async route=>{await route.fetch();await route.abort();},{times:1});
  await area.getByRole('button',{name:'Salvar local',exact:true}).click();await expect(page.getByRole('button',{name:'Conferir envio',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Conferir envio',exact:true}).click();await expect(page.getByRole('button',{name:'Conferir envio',exact:true})).toHaveCount(0);
  await expect(area.getByRole('cell',{name,exact:true})).toHaveCount(1);
});

test('excluir mesa pede confirmação, cancelar preserva e mesa ocupada fica protegida',async({page},info)=>{
  await login(page);
  const suffix=info.project.name+'-'+Date.now();
  const area=await command(page,'area.save',{name:'Exclusão '+suffix});
  const freeNumber='Excluir-'+Date.now().toString().slice(-8);
  const occupiedNumber='Ocupada-'+Date.now().toString().slice(-8);
  await command(page,'table.save',{areaId:area.id,number:freeNumber});
  const occupied=await command(page,'table.save',{areaId:area.id,number:occupiedNumber});
  await command(page,'account.open',{tableId:occupied.id});
  await page.reload();
  await expect(page.getByRole('heading',{name:'Mesas do bar',exact:true})).toBeVisible();
  await navigate(page,'Mesas e cardápio');
  const tables=page.locator('section.panel').filter({has:page.getByRole('heading',{name:'Mesas',exact:true})}).first();
  const freeRow=tables.getByRole('row').filter({has:page.getByRole('cell',{name:freeNumber,exact:true})});
  const occupiedRow=tables.getByRole('row').filter({has:page.getByRole('cell',{name:occupiedNumber,exact:true})});
  const remove=freeRow.getByRole('button',{name:'Excluir mesa '+freeNumber,exact:true});
  await expect(remove).toBeVisible();
  await expect(occupiedRow.getByRole('button',{name:'Excluir mesa '+occupiedNumber,exact:true})).toBeDisabled();
  const deletions:string[]=[];
  page.on('request',request=>{if(request.method()==='POST'&&request.url().endsWith('/api/commands/table.delete'))deletions.push(request.url());});
  await remove.click();
  const dialog=page.getByRole('dialog',{name:'Excluir mesa '+freeNumber+'?',exact:true});
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button',{name:'Cancelar',exact:true})).toBeFocused();
  await page.screenshot({path:'test-results/'+info.project.name+'-excluir-mesa.png',fullPage:false});
  await dialog.getByRole('button',{name:'Cancelar',exact:true}).click();
  await expect(dialog).toHaveCount(0);await expect(freeRow).toBeVisible();await expect(remove).toBeFocused();
  await remove.click();await expect(dialog).toBeVisible();
  await page.keyboard.press('Escape');await expect(dialog).toHaveCount(0);await expect(remove).toBeFocused();
  expect(deletions).toEqual([]);
  await remove.click();
  const response=page.waitForResponse(r=>r.url().endsWith('/api/commands/table.delete')&&r.request().method()==='POST');
  await dialog.getByRole('button',{name:'Sim, excluir mesa',exact:true}).click();
  expect((await response).status()).toBe(200);
  await expect(dialog).toHaveCount(0);await expect(freeRow).toHaveCount(0);await expect(occupiedRow).toBeVisible();
  expect(deletions).toHaveLength(1);
  await navigate(page,'Mesas');
  await expect(page.locator('button.table-card').filter({has:page.locator('strong',{hasText:freeNumber})})).toHaveCount(0);
  await expect(page.locator('button.table-card').filter({has:page.locator('strong',{hasText:occupiedNumber})})).toBeVisible();
  await navigate(page,'Mesas e cardápio');
  const excluded=page.locator('details.panel').filter({has:page.locator('summary',{hasText:/^Mesas excluídas \(\d+\)$/})});
  await excluded.locator('summary').click();
  await excluded.getByRole('button',{name:'Recuperar mesa '+freeNumber,exact:true}).click();
  await expect(tables.getByRole('cell',{name:freeNumber,exact:true})).toBeVisible();
  await expect(excluded.getByRole('button',{name:'Recuperar mesa '+freeNumber,exact:true})).toHaveCount(0);
  await navigate(page,'Mesas');
  await expect(page.locator('button.table-card').filter({has:page.locator('strong',{hasText:freeNumber})})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
});

test('funcionário é cadastrado sem e-mail e entra com usuário e senha curta',async({page},info)=>{
  expect(process.env.BAR_TEST_DATABASE,'Cadastros de teste exigem banco descartável.').toBe('true');
  await login(page);
  const area=await command(page,'area.save',{name:'Atendimento '+info.project.name+' '+Date.now()});
  const tableNumber='Atender-'+Date.now().toString().slice(-8);
  await command(page,'table.save',{areaId:area.id,number:tableNumber});
  await navigate(page,'Funcionários');
  const staff=page.locator('section.panel').filter({has:page.getByRole('heading',{name:'Novo funcionário',exact:true})}).first();
  const name='Atendente '+info.project.name+' '+Date.now();
  const username='atendente_'+info.project.name[0]+'_'+Date.now();
  const password='4728';
  await expect(staff.getByLabel('E-mail',{exact:true})).toHaveCount(0);
  await staff.getByLabel('Nome do funcionário',{exact:true}).fill(name);
  await staff.getByLabel('Usuário',{exact:true}).fill(username);
  await staff.getByLabel('Senha de acesso',{exact:true}).fill(password);
  await staff.getByRole('combobox',{name:'Função',exact:true}).selectOption('ATENDIMENTO');
  await staff.scrollIntoViewIfNeeded();
  await page.screenshot({path:'test-results/'+info.project.name+'-novo-funcionario.png',fullPage:false});
  await staff.getByRole('button',{name:'Salvar funcionário',exact:true}).click();
  const registered=page.locator('section.panel').filter({has:page.getByRole('heading',{name:'Funcionários cadastrados',exact:true})}).first();
  await expect(registered.getByRole('cell',{name:username,exact:true})).toBeVisible();
  const logout=await page.request.post('/api/logout',{data:{},headers:{'X-Bar-Request':'1'}});
  expect(logout.status()).toBe(200);
  await login(page,username,password);
  const me=await page.request.get('/api/me');expect(me.status()).toBe(200);expect((await me.json()).name).toBe(name);
  await expect(page.getByRole('navigation',{name:'Navegação principal',exact:true,includeHidden:true}).getByRole('button',{name:'Funcionários',exact:true,includeHidden:true})).toHaveCount(0);
  const table=page.locator('button.table-card').filter({has:page.locator('strong',{hasText:tableNumber})});
  await expect(table).toBeEnabled();await table.click();
  await expect(page.getByRole('heading',{name:'Novo pedido',exact:true})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
});

test('expiração da sessão com menu móvel aberto libera a rolagem do login',async({page})=>{
  await login(page);
  const openMenu=page.getByRole('button',{name:'Abrir menu',exact:true});
  test.skip(!await openMenu.isVisible(),'Somente o menu do celular bloqueia a rolagem.');
  await openMenu.click();
  await expect.poll(()=>page.evaluate(()=>document.body.style.overflow)).toBe('hidden');
  await page.route('**/api/state',route=>route.fulfill({status:401,contentType:'application/json',body:JSON.stringify({error:'Sessão expirada.'})}),{times:1});
  await expect(page.getByRole('button',{name:'Entrar',exact:true})).toBeVisible({timeout:12000});
  await expect.poll(()=>page.evaluate(()=>document.body.style.overflow)).not.toBe('hidden');
});
