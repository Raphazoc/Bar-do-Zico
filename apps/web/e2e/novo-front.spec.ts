import {test,expect,type Page,type APIRequestContext} from '@playwright/test';

test('servidor fechado explica autorização sem mostrar login ou dados anteriores',async({page})=>{
  await page.route('**/api/me',r=>r.fulfill({status:401,contentType:'application/json',body:JSON.stringify({error:'Entre no sistema para continuar.'})}));
  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('Este servidor exige autorização de acesso');
  await expect(page.locator('input[type=password],input[type=email]')).toHaveCount(0);
  await expect(page.getByRole('heading',{name:'Mesas',exact:true})).toHaveCount(0);
  await page.unroute('**/api/me');
  await page.getByRole('button',{name:'Tentar novamente',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Mesas',exact:true})).toBeVisible();
  await page.route('**/api/state',r=>r.fulfill({status:401,contentType:'application/json',body:'{"error":"Acesso expirado"}'}));
  await expect(page.getByRole('alert')).toContainText('Este servidor exige autorização de acesso',{timeout:12000});
  await expect(page.locator('.table-grid')).toHaveCount(0);
});

async function open(page:Page){await page.goto('/');await expect(page.getByRole('heading',{name:'Mesas',exact:true})).toBeVisible();}
async function nav(page:Page,label:string){await expect(page.locator('.app-header')).toBeVisible();const menu=page.getByRole('button',{name:'Abrir menu',exact:true});if(await menu.isVisible())await menu.click();await page.getByRole('navigation',{name:'Navegação principal'}).getByRole('button',{name:label,exact:true}).click();}
async function apiCommand(client:APIRequestContext,operation:string,body:Record<string,unknown>){expect(process.env.BAR_TEST_DATABASE).toBe('true');const response=await client.post('/api/commands/'+operation,{data:body,headers:{'X-Bar-Request':'1','Idempotency-Key':crypto.randomUUID()}});expect(response.status(),await response.text()).toBe(200);return response.json();}
async function command(page:Page,operation:string,body:Record<string,unknown>){return apiCommand(page.request,operation,body);}
async function fixture(page:Page,suffix:string){const area=await command(page,'area.save',{name:'Salão '+suffix}),number=Date.now().toString().slice(-9),table=await command(page,'table.save',{number,areaId:area.id}),category=await command(page,'category.save',{name:'Bebidas '+suffix}),product=await command(page,'product.save',{name:'Bebida '+suffix,categoryId:category.id,price:'10.00',destination:'BAR',stockControlled:true,stockQuantity:'10',stockMinimum:'2'});await page.reload();await expect(page.getByRole('heading',{name:'Mesas',exact:true})).toBeVisible();return {number,table,category,product,name:'Bebida '+suffix};}
async function width(page:Page){expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();}

test('abre direto sem login nem recursos externos',async({page},info)=>{
  const failures:string[]=[],external:string[]=[];page.on('pageerror',e=>failures.push(e.message));await page.route('**/*',r=>{if(new URL(r.request().url()).hostname==='127.0.0.1')return r.continue();external.push(r.request().url());return r.abort();});await open(page);await expect(page.locator('input[type=password],input[type=email]')).toHaveCount(0);await expect(page.getByRole('button',{name:'Entrar',exact:true})).toHaveCount(0);await width(page);await page.screenshot({path:'test-results/'+info.project.name+'-mesas.png',fullPage:true});expect(failures).toEqual([]);expect(external).toEqual([]);
});
test('cinco áreas e menu móvel cabem na tela',async({page},info)=>{
  await open(page);for(const [label,file] of [['Caixa','caixa'],['Funcionários','funcionarios'],['Estoque','estoque'],['Cardápio','cardapio'],['Mesas','mesas']]){await nav(page,label);await expect(page.getByRole('heading',{name:label,exact:true})).toBeVisible();await width(page);await page.screenshot({path:'test-results/'+info.project.name+'-'+file+'.png',fullPage:true});}const menu=page.getByRole('button',{name:'Abrir menu',exact:true});if(await menu.isVisible()){await menu.click();await page.keyboard.press('Escape');await expect(menu).toHaveAttribute('aria-expanded','false');}
});
test('mesa é excluída permanentemente e seu número pode ser reutilizado',async({page},info)=>{
  await open(page);const number='Nova-'+Date.now().toString().slice(-8);await page.getByRole('button',{name:'Adicionar mesa',exact:true}).click();let dialog=page.getByRole('dialog');await dialog.getByLabel('Número ou nome da mesa').fill(number);await dialog.getByRole('button',{name:'Salvar mesa',exact:true}).click();let card=page.getByRole('region',{name:'Mesa '+number,exact:true});await expect(card).toBeVisible();await card.getByRole('button',{name:'Editar mesa '+number,exact:true}).click();dialog=page.getByRole('dialog');const changed=number+'a';await dialog.getByLabel('Número ou nome da mesa').fill(changed);await dialog.getByRole('button',{name:'Salvar alterações',exact:true}).click();card=page.getByRole('region',{name:'Mesa '+changed,exact:true});await card.getByRole('button',{name:'Excluir mesa '+changed,exact:true}).click();dialog=page.getByRole('dialog');await expect(dialog.getByRole('button',{name:'Cancelar',exact:true})).toBeFocused();await page.screenshot({path:'test-results/'+info.project.name+'-excluir.png',fullPage:false});await page.keyboard.press('Escape');await expect(card).toBeVisible();await card.getByRole('button',{name:'Excluir mesa '+changed,exact:true}).click();await dialog.getByRole('button',{name:'Sim, excluir mesa',exact:true}).click();await expect(card).toHaveCount(0);await expect(page.getByText(/Mesas excluídas/)).toHaveCount(0);await page.getByRole('button',{name:'Adicionar mesa',exact:true}).click();dialog=page.getByRole('dialog');await dialog.getByLabel('Número ou nome da mesa').fill(changed);await dialog.getByRole('button',{name:'Salvar mesa',exact:true}).click();await expect(card).toBeVisible();await page.reload();await expect(card).toHaveCount(1);
});
test('filtros não abrem conta e mesa ocupada fica protegida',async({page},info)=>{
  await open(page);const f=await fixture(page,'Filtro '+info.project.name+' '+Date.now());await command(page,'account.open',{tableId:f.table.id});await page.reload();await page.getByLabel('Buscar mesa',{exact:true}).fill(f.number);await page.getByRole('combobox',{name:'Mostrar',exact:true}).selectOption('OCUPADA');const card=page.getByRole('region',{name:'Mesa '+f.number,exact:true});await expect(card).toBeVisible();await expect(card.getByRole('button',{name:'Excluir mesa '+f.number,exact:true})).toBeDisabled();await page.getByRole('combobox',{name:'Mostrar',exact:true}).selectOption('LIVRE');await expect(card).toHaveCount(0);
});
test('pedido dá baixa no estoque e caixa recebe com troco e fecha',async({page},info)=>{
  await open(page);const f=await fixture(page,'Fluxo '+info.project.name+' '+Date.now());const terminal=await command(page,'terminal.save',{name:'Fluxo '+info.project.name+' '+Date.now()});await page.reload();await page.getByRole('region',{name:'Mesa '+f.number,exact:true}).getByRole('button',{name:'Abrir atendimento',exact:true}).click();await expect(page.getByRole('heading',{name:'Mesa '+f.number,exact:true})).toBeVisible();await page.getByRole('button',{name:'Adicionar produto',exact:true}).click();const product=page.getByRole('dialog');await product.getByRole('combobox',{name:'Produto',exact:true}).selectOption(f.product.id);await product.getByLabel('Quantidade',{exact:true}).fill('2');await product.getByRole('button',{name:'Adicionar à mesa',exact:true}).click();await expect(page.getByRole('heading',{name:'Novo pedido',exact:true})).toHaveCount(0);await expect(page.locator('.total-line')).toContainText('20,00');await page.screenshot({path:'test-results/'+info.project.name+'-atendimento.png',fullPage:true});await expect(page.getByRole('button',{name:'Acompanhar pedido',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Enviar pedido',exact:true})).toHaveCount(0);await page.getByRole('button',{name:'Ir para o caixa',exact:true}).click();await page.getByRole('button',{name:'Abrir caixa',exact:true}).click();const opening=page.getByRole('dialog');if(await opening.getByLabel('Nome do caixa',{exact:true}).count())await opening.getByLabel('Nome do caixa',{exact:true}).fill('Caixa teste');else await opening.getByRole('combobox',{name:'Caixa',exact:true}).selectOption(terminal.id);await opening.getByLabel('Dinheiro inicial (R$)',{exact:true}).fill('50');await opening.getByRole('button',{name:'Começar caixa',exact:true}).click();await page.getByLabel('Dinheiro recebido (R$)',{exact:true}).fill('50');await expect(page.locator('.change')).toContainText('30,00');await page.getByRole('button',{name:'Receber e finalizar mesa',exact:true}).click();await expect(page.getByRole('dialog',{name:'Comprovante simples',exact:true})).toBeVisible();await page.getByRole('button',{name:'Fechar comprovante',exact:true}).click();await page.getByRole('button',{name:'Fechamento de caixa',exact:true}).click();for(const method of ['Dinheiro','Pix','Débito','Crédito','Voucher','Outro'])await page.getByLabel(method+' contado (R$)',{exact:true}).fill(method==='Dinheiro'?'70':'0');await page.getByRole('button',{name:'Fechar caixa',exact:true}).click();await expect(page.getByText('Caixa fechado',{exact:true})).toBeVisible();await expect(page.getByRole('region',{name:'Resumo do caixa selecionado',exact:true})).toContainText('Dinheiro previsto no fechamento');await nav(page,'Estoque');await page.getByLabel('Buscar produto',{exact:true}).fill(f.name);await expect(page.getByRole('row').filter({has:page.getByText(f.name,{exact:true})})).toContainText('8');await width(page);
});
test('funcionário sem credenciais pode ser cadastrado editado e ativado',async({page},info)=>{
  await open(page);await nav(page,'Funcionários');await page.getByRole('button',{name:'+ Cadastrar funcionário',exact:true}).click();let dialog=page.getByRole('dialog');await expect(dialog.locator('input[type=password],input[type=email]')).toHaveCount(0);const name='Atendente '+info.project.name+' '+Date.now();await dialog.getByLabel('Nome do funcionário').fill(name);await dialog.getByRole('combobox',{name:'Função',exact:true}).selectOption('base:ATENDIMENTO');await dialog.getByRole('button',{name:'Salvar funcionário',exact:true}).click();let row=page.getByRole('row').filter({has:page.getByText(name,{exact:true})});await expect(row).toBeVisible();await row.getByRole('button',{name:'Editar',exact:true}).click();dialog=page.getByRole('dialog');await dialog.getByLabel('Nome do funcionário').fill(name+' corrigido');await dialog.getByRole('button',{name:'Salvar funcionário',exact:true}).click();row=page.getByRole('row').filter({has:page.getByText(name+' corrigido',{exact:true})});await row.getByRole('button',{name:'Desativar',exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'Confirmar',exact:true}).click();await expect(row).toContainText('Inativo');await row.getByRole('button',{name:'Ativar',exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'Confirmar',exact:true}).click();await expect(row).toContainText('Ativo');
});
test('produto estoque mínimo movimentação e cardápio usam o mesmo cadastro',async({page},info)=>{
  await open(page);await nav(page,'Estoque');await page.getByRole('button',{name:'+ Cadastrar produto',exact:true}).click();const product=page.getByRole('dialog'),name='Produto manual '+info.project.name+' '+Date.now();await product.getByLabel('Nome do produto').fill(name);await product.getByLabel('Preço (R$)',{exact:true}).fill('12.50');await product.getByLabel('Quantidade inicial').fill('5');await product.getByLabel('Estoque mínimo').fill('8');await product.getByRole('button',{name:'Salvar produto',exact:true}).click();await page.getByLabel('Buscar produto',{exact:true}).fill(name);const row=page.getByRole('row').filter({has:page.getByText(name,{exact:true})});await expect(row).toContainText('Estoque baixo');await page.getByRole('button',{name:'Registrar entrada',exact:true}).click();const movement=page.getByRole('dialog');await movement.getByRole('combobox',{name:'Produto',exact:true}).selectOption({label:name});await movement.getByLabel('Quantidade',{exact:true}).fill('10');await movement.getByLabel('Motivo',{exact:true}).fill('Reposição');await movement.getByRole('button',{name:'Salvar movimentação',exact:true}).click();await expect(row).toContainText('15');await page.getByRole('button',{name:'Movimentações',exact:true}).click();await expect(page.getByRole('row').filter({has:page.getByText(name,{exact:true})}).filter({has:page.getByText('Reposição',{exact:true})})).toBeVisible();await nav(page,'Cardápio');await page.getByLabel('Buscar produto',{exact:true}).fill(name);const card=page.locator('.product-card').filter({has:page.getByRole('heading',{name,exact:true})});await expect(card).toContainText('15 unidades');await card.getByRole('button',{name:'Tornar indisponível',exact:true}).click();await expect(card.getByText('Indisponível',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Indisponíveis',exact:true}).click();await expect(card).toBeVisible();await width(page);
});
test('perfis permitem editar as permissões',async({page},info)=>{
  await open(page);await nav(page,'Funcionários');await page.getByRole('button',{name:'Perfis de acesso',exact:true}).click();await page.getByRole('button',{name:'Criar perfil',exact:true}).click();const dialog=page.getByRole('dialog'),name='Consulta '+info.project.name+' '+Date.now();await dialog.getByLabel('Nome do perfil').fill(name);await dialog.getByLabel('Ver vendas e relatórios',{exact:true}).check();await dialog.getByRole('button',{name:'Salvar perfil',exact:true}).click();await expect(page.getByRole('dialog').getByText(name,{exact:true})).toBeVisible();
});
test('queda de conexão bloqueia envios e permite reconectar',async({page,context})=>{
  await open(page);await context.setOffline(true);await expect(page.getByRole('button',{name:'Reconectar',exact:true})).toBeVisible({timeout:25000});await context.setOffline(false);await page.getByRole('button',{name:'Reconectar',exact:true}).click();await expect(page.getByText('● Conectado ao bar',{exact:true})).toBeVisible();
});
test('resposta perdida confere cadastro sem duplicar',async({page})=>{
  await open(page);await page.getByRole('button',{name:'Adicionar mesa',exact:true}).click();const number='Reenvio-'+Date.now().toString().slice(-7),dialog=page.getByRole('dialog');await dialog.getByLabel('Número ou nome da mesa').fill(number);let lost=false;await page.route('**/api/commands/table.save',async route=>{if(!lost){lost=true;await route.fetch();return route.abort();}return route.continue();});await dialog.getByRole('button',{name:'Salvar mesa',exact:true}).click();await expect(page.getByRole('button',{name:'Conferir envio',exact:true})).toBeVisible();await page.keyboard.press('Escape');await page.getByRole('button',{name:'Conferir envio',exact:true}).click();await expect(page.getByRole('button',{name:'Conferir envio',exact:true})).toHaveCount(0);await expect(page.getByRole('region',{name:'Mesa '+number,exact:true})).toHaveCount(1);
});
test('configurações mostram QR e endereço para Android',async({page})=>{
  await open(page);const menu=page.getByRole('button',{name:'Abrir menu',exact:true});if(await menu.isVisible())await menu.click();await page.getByRole('button',{name:'Configurações',exact:true}).filter({visible:true}).click();await expect(page.getByRole('heading',{name:'Acesso pelo Android',exact:true})).toBeVisible();await expect(page.getByRole('img',{name:'QR Code do endereço local do sistema'})).toBeVisible();await width(page);
});

test('excluir produto remove do estoque cardápio e seleção, preservando movimentações',async({page},info)=>{
  await open(page);const f=await fixture(page,'Excluir '+info.project.name+' '+Date.now());await expect(page.getByRole('button',{name:'Pedidos para preparar',exact:true})).toHaveCount(0);await nav(page,'Estoque');await page.getByLabel('Buscar produto',{exact:true}).fill(f.name);let row=page.getByRole('row').filter({has:page.getByText(f.name,{exact:true})});await row.getByRole('button',{name:'Excluir produto '+f.name,exact:true}).click();let dialog=page.getByRole('dialog');await expect(dialog.getByRole('button',{name:'Cancelar',exact:true})).toBeFocused();await page.screenshot({path:'test-results/'+info.project.name+'-excluir-produto.png',fullPage:false});await dialog.getByRole('button',{name:'Cancelar',exact:true}).click();await expect(row).toBeVisible();await row.getByRole('button',{name:'Excluir produto '+f.name,exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'Sim, excluir produto',exact:true}).click();await expect(row).toHaveCount(0);await page.reload();await nav(page,'Estoque');await page.getByLabel('Buscar produto',{exact:true}).fill(f.name);await expect(page.getByRole('row').filter({has:page.getByText(f.name,{exact:true})})).toHaveCount(0);await page.getByRole('button',{name:'Movimentações',exact:true}).click();await expect(page.getByRole('row').filter({has:page.getByText(f.name,{exact:true})})).toContainText('Quantidade inicial');await nav(page,'Cardápio');await page.getByLabel('Buscar produto',{exact:true}).fill(f.name);await expect(page.getByRole('heading',{name:f.name,exact:true})).toHaveCount(0);await nav(page,'Mesas');await page.getByRole('region',{name:'Mesa '+f.number,exact:true}).getByRole('button',{name:'Abrir atendimento',exact:true}).click();await page.getByRole('button',{name:'Adicionar produto',exact:true}).click();await expect(page.getByRole('dialog').locator('option[value="'+f.product.id+'"]')).toHaveCount(0);
});
test('adição direta com resposta perdida não duplica consumo e permite remover',async({page},info)=>{
  await open(page);const f=await fixture(page,'Direto '+info.project.name+' '+Date.now());await page.getByRole('region',{name:'Mesa '+f.number,exact:true}).getByRole('button',{name:'Abrir atendimento',exact:true}).click();await page.getByRole('button',{name:'Adicionar produto',exact:true}).click();let dialog=page.getByRole('dialog');await dialog.getByRole('combobox',{name:'Produto',exact:true}).selectOption(f.product.id);let lost=false;await page.route('**/api/commands/order.create',async route=>{if(!lost){lost=true;await route.fetch();return route.abort();}return route.continue();});await dialog.getByRole('button',{name:'Adicionar à mesa',exact:true}).click();await expect(page.getByRole('button',{name:'Conferir envio',exact:true})).toBeVisible();await page.reload();await page.getByRole('button',{name:'Conferir envio',exact:true}).click();await expect(page.getByRole('button',{name:'Conferir envio',exact:true})).toHaveCount(0);await page.getByRole('region',{name:'Mesa '+f.number,exact:true}).getByRole('button',{name:'Ver consumo',exact:true}).click();await expect(page.locator('.total-line')).toContainText('10,00');await expect(page.locator('.consumption-list>div')).toHaveCount(1);await page.getByRole('button',{name:'Remover '+f.name,exact:true}).click();dialog=page.getByRole('dialog');await dialog.getByRole('button',{name:'Confirmar remoção',exact:true}).click();await expect(page.locator('.total-line')).toContainText('0,00');await nav(page,'Estoque');await page.getByLabel('Buscar produto',{exact:true}).fill(f.name);await expect(page.getByRole('row').filter({has:page.getByText(f.name,{exact:true})})).toContainText('10');
});

test('excluir local transfere uma mesa ocupada sem alterar consumo',async({page},info)=>{
 await open(page);const f=await fixture(page,'Transferir '+info.project.name+' '+Date.now());const account=await command(page,'account.open',{tableId:f.table.id});await command(page,'order.create',{accountId:account.id,items:[{productId:f.product.id,quantity:'2'}]});const destination=await command(page,'area.save',{name:'Destino '+info.project.name+' '+Date.now()});await page.reload();await expect(page.getByRole('heading',{name:'Mesas',exact:true})).toBeVisible();await page.getByText('Locais do bar',{exact:true}).first().click();const state=await page.request.get('/api/state').then(r=>r.json());const sourceArea=state.areas.find((a:any)=>a.id===state.tables.find((t:any)=>t.id===f.table.id).area_id);
 await page.getByRole('button',{name:'Excluir local '+sourceArea.name,exact:true}).click();let dialog=page.getByRole('dialog');await expect(dialog).toContainText('1 mesa será transferida');await dialog.getByRole('button',{name:'Cancelar',exact:true}).click();await page.getByRole('button',{name:'Excluir local '+sourceArea.name,exact:true}).click();dialog=page.getByRole('dialog');await dialog.getByRole('combobox',{name:'Transferir mesas para',exact:true}).selectOption(destination.id);await dialog.getByRole('button',{name:'Sim, excluir local',exact:true}).click();await expect(page.getByRole('button',{name:'Excluir local '+sourceArea.name,exact:true})).toHaveCount(0);const card=page.getByRole('region',{name:'Mesa '+f.number,exact:true});await expect(card).toContainText('20,00');await card.getByRole('button',{name:'Ver consumo',exact:true}).click();await expect(page.locator('.total-line')).toContainText('20,00');
});
test('funcionário excluído sai da lista e da identificação do aparelho',async({page},info)=>{
 await open(page);const name='Excluir atendente '+info.project.name+' '+Date.now();await command(page,'staff.save',{name,role:'ATENDIMENTO'});await page.reload();await nav(page,'Funcionários');await page.getByLabel('Buscar funcionário',{exact:true}).fill(name);const row=page.getByRole('row').filter({has:page.getByText(name,{exact:true})});await row.getByRole('button',{name:'Excluir funcionário '+name,exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'Cancelar',exact:true}).click();await expect(row).toBeVisible();await row.getByRole('button',{name:'Excluir funcionário '+name,exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'Sim, excluir funcionário',exact:true}).click();await expect(row).toHaveCount(0);await page.reload();await nav(page,'Funcionários');await page.getByLabel('Buscar funcionário',{exact:true}).fill(name);await expect(page.getByRole('row').filter({has:page.getByText(name,{exact:true})})).toHaveCount(0);const menu=page.getByRole('button',{name:'Abrir menu',exact:true});if(await menu.isVisible())await menu.click();await page.getByRole('button',{name:'Configurações',exact:true}).filter({visible:true}).click();const me=await page.request.get('/api/me').then(r=>r.json());if(me.deviceAccess)await expect(page.getByRole('combobox',{name:'Funcionário',exact:true})).toHaveCount(0);else await expect(page.getByRole('combobox',{name:'Funcionário',exact:true}).getByRole('option',{name,exact:true})).toHaveCount(0);
});
test('saldo distingue dinheiro e vendas e lembra ocultação após reabrir',async({page},info)=>{
 await open(page);const f=await fixture(page,'Saldo '+info.project.name+' '+Date.now());const account=await command(page,'account.open',{tableId:f.table.id});await command(page,'order.create',{accountId:account.id,items:[{productId:f.product.id,quantity:'5'}]});const terminal=await command(page,'terminal.save',{name:'Saldo '+Date.now()}),cash=await command(page,'cash.open',{terminalId:terminal.id,openingAmount:'50'});await command(page,'payment.receive',{sessionId:cash.id,accountId:account.id,method:'DINHEIRO',amount:'10',tendered:'20'});await command(page,'payment.receive',{sessionId:cash.id,accountId:account.id,method:'PIX',amount:'20'});await page.reload();await nav(page,'Caixa');await page.getByRole('combobox',{name:'Caixa em uso',exact:true}).selectOption(cash.id);const summary=page.getByRole('region',{name:'Resumo do caixa selecionado',exact:true});await expect(summary).toContainText('60,00');await expect(summary).toContainText('30,00');await command(page,'cash.move',{sessionId:cash.id,kind:'SUPRIMENTO',amount:'5',reason:'Entrada de teste'});await expect(summary).toContainText('65,00');await summary.getByRole('button',{name:'Ocultar valores',exact:true}).click();await expect(summary).not.toContainText('65,00');await expect(summary).toContainText('••••');await page.reload();await nav(page,'Caixa');await page.getByRole('combobox',{name:'Caixa em uso',exact:true}).selectOption(cash.id);await expect(summary.getByRole('button',{name:'Mostrar valores',exact:true})).toBeVisible();await expect(summary).not.toContainText('65,00');await summary.getByRole('button',{name:'Mostrar valores',exact:true}).click();await expect(summary).toContainText('65,00');await page.screenshot({path:'test-results/'+info.project.name+'-saldo-v16.png',fullPage:true});
});
test('vendas mostram duas utilizações da mesma mesa e auditoria fica em configurações',async({page},info)=>{
 await open(page);const f=await fixture(page,'Vendas '+info.project.name+' '+Date.now());const terminal=await command(page,'terminal.save',{name:'Vendas '+Date.now()}),cash=await command(page,'cash.open',{terminalId:terminal.id,openingAmount:'0'});
 for(const quantity of ['2','1']){const account=await command(page,'account.open',{tableId:f.table.id});await command(page,'order.create',{accountId:account.id,items:[{productId:f.product.id,quantity}]});await command(page,'payment.receive',{sessionId:cash.id,accountId:account.id,method:'PIX',amount:String(Number(quantity)*10)});}
 await command(page,'table.delete',{tableId:f.table.id});await page.reload();await nav(page,'Caixa');await page.getByRole('button',{name:'Vendas',exact:true}).click();await expect(page.getByRole('heading',{name:'Vendas por mesa',exact:true})).toBeVisible();const rows=page.getByRole('row').filter({has:page.getByText('Mesa '+f.number,{exact:true})});await expect(rows).toHaveCount(2);await expect(rows.first()).toContainText('10,00');await expect(rows.last()).toContainText('20,00');await expect(page.getByText('Histórico de operações',{exact:true})).toHaveCount(0);await expect(page.getByRole('combobox',{name:'Caixa em uso',exact:true})).toHaveCount(0);await page.evaluate(()=>{(document.activeElement as HTMLElement)?.blur();window.scrollTo(0,0);});await page.screenshot({path:'test-results/'+info.project.name+'-vendas-v16.png',fullPage:true});const menu=page.getByRole('button',{name:'Abrir menu',exact:true});if(await menu.isVisible())await menu.click();await page.getByRole('button',{name:'Configurações',exact:true}).filter({visible:true}).click();await page.getByText('Histórico de alterações',{exact:true}).click();await page.getByRole('button',{name:'Consultar histórico',exact:true}).click();await expect(page.getByText('Pagamento registrado',{exact:true}).first()).toBeVisible();await expect(page.getByText('payment.receive',{exact:true})).toHaveCount(0);await expect(page.getByText('staff.save',{exact:true})).toHaveCount(0);
});
test('permissões agrupadas e navegação não cobrem o conteúdo',async({page},info)=>{
 await open(page);await nav(page,'Funcionários');await page.getByRole('button',{name:'Perfis de acesso',exact:true}).click();await page.getByRole('button',{name:'Criar perfil',exact:true}).click();let dialog=page.getByRole('dialog');for(const name of ['Mesas e atendimento','Caixa','Estoque e cardápio','Administração'])await expect(dialog.getByRole('group',{name,exact:true})).toBeVisible();await expect(dialog.getByText('Atualizar preparo e entrega',{exact:true})).toHaveCount(0);await page.screenshot({path:'test-results/'+info.project.name+'-permissoes-v16.png',fullPage:false});await page.keyboard.press('Escape');await nav(page,'Caixa');await page.getByRole('button',{name:'Vendas',exact:true}).click();await expect(page.getByRole('heading',{name:'Vendas por mesa',exact:true})).toBeVisible();const geometry=await page.evaluate(()=>{const shell=document.querySelector('.navigation-shell')!.getBoundingClientRect(),heading=document.querySelector('.workspace h1')!.getBoundingClientRect();return {bottom:shell.bottom,top:heading.top};});expect(geometry.top).toBeGreaterThanOrEqual(geometry.bottom);await page.getByLabel('Data inicial',{exact:true}).evaluate(el=>el.scrollIntoView({block:'start'}));const box=await page.getByLabel('Data inicial',{exact:true}).boundingBox();const bottom=await page.locator('.navigation-shell').evaluate(el=>el.getBoundingClientRect().bottom);expect(box!.y).toBeGreaterThanOrEqual(bottom);const labelTop=await page.getByLabel('Data inicial',{exact:true}).evaluate(el=>el.closest('label')!.getBoundingClientRect().top);expect(labelTop).toBeGreaterThanOrEqual(bottom);const menu=page.getByRole('button',{name:'Abrir menu',exact:true});if(await menu.isVisible())await menu.click();await page.getByRole('button',{name:'Configurações',exact:true}).filter({visible:true}).click();const title=await page.getByRole('heading',{name:'Configurações',exact:true}).boundingBox();const header=await page.locator('.navigation-shell').evaluate(el=>el.getBoundingClientRect().bottom);expect(title!.y).toBeGreaterThanOrEqual(header);await width(page);
});

test('editar permissões mantém códigos antigos ocultos sem exibi-los',async({page},info)=>{
 await open(page);const name='Perfil legado '+info.project.name+' '+Date.now(),role=await command(page,'role.save',{name,permissions:['production.update','device.revoke','report.view']});await page.reload();await nav(page,'Funcionários');await page.getByRole('button',{name:'Perfis de acesso',exact:true}).click();await page.getByRole('dialog').getByRole('row').filter({has:page.getByText(name,{exact:true})}).getByRole('button',{name:'Editar permissões',exact:true}).click();const dialog=page.getByRole('dialog');await expect(dialog.getByLabel('Ver vendas e relatórios',{exact:true})).toBeChecked();await expect(dialog.getByText('Atualizar preparo e entrega',{exact:true})).toHaveCount(0);await dialog.getByLabel('Registrar entradas, saídas e ajustes de estoque',{exact:true}).check();await dialog.getByRole('button',{name:'Salvar perfil',exact:true}).click();const state=await page.request.get('/api/state').then(r=>r.json());expect(state.rolePermissions.filter((p:any)=>p.role_id===role.id).map((p:any)=>p.permission)).toEqual(expect.arrayContaining(['production.update','device.revoke','report.view','stock.adjust']));
});

test('computador administra e um caixa novo começa com zero reais',async({page},info)=>{
  await open(page);
  const me=await page.request.get('/api/me').then(r=>r.json());
  expect(me.deviceAccess).toBe(true);expect(me.accessMode).toBe('ADMINISTRADOR');
  await expect(page.getByText('Computador administrador',{exact:true})).toBeVisible();
  const terminal=await command(page,'terminal.save',{name:'Zero '+info.project.name+' '+Date.now()});
  await page.reload();await nav(page,'Caixa');await page.getByRole('button',{name:'Abrir caixa',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'Abrir caixa',exact:true});
  await dialog.getByRole('combobox',{name:'Caixa',exact:true}).selectOption(terminal.id);
  await expect(dialog.getByLabel('Dinheiro inicial (R$)',{exact:true})).toHaveValue('0');
  await dialog.getByRole('button',{name:'Começar caixa',exact:true}).click();
  await expect(dialog).toHaveCount(0);
  const state=await page.request.get('/api/state').then(r=>r.json()),sessions=state.sessions.filter((s:any)=>s.terminal_id===terminal.id);
  expect(sessions).toHaveLength(1);expect(Number(sessions[0].opening_amount)).toBe(0);
  await expect(page.getByRole('combobox',{name:'Caixa em uso',exact:true})).toHaveValue(sessions[0].id);
  await expect(page.getByRole('region',{name:'Resumo do caixa selecionado',exact:true})).toContainText('0,00');
});

test('caixa já aberto permite continuar sem mudar o dinheiro inicial',async({page},info)=>{
  await open(page);
  const name='Aberto '+info.project.name+' '+Date.now(),terminal=await command(page,'terminal.save',{name}),cash=await command(page,'cash.open',{terminalId:terminal.id,openingAmount:'37'});
  await page.reload();await nav(page,'Caixa');
  await page.getByRole('button',{name:/^(Abrir caixa|Conferir caixa aberto)$/}).click();
  const dialog=page.getByRole('dialog');
  await expect(dialog.locator('select option[value="'+terminal.id+'"]')).toHaveCount(0);
  const existing=dialog.locator('.cart-line').filter({has:page.getByText(name,{exact:true})});
  await existing.getByRole('button',{name:'Usar caixa aberto',exact:true}).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole('combobox',{name:'Caixa em uso',exact:true})).toHaveValue(cash.id);
  const state=await page.request.get('/api/state').then(r=>r.json()),sessions=state.sessions.filter((s:any)=>s.terminal_id===terminal.id);
  expect(sessions).toHaveLength(1);expect(Number(sessions[0].opening_amount)).toBe(37);
});

test('abertura simultânea explica caixa já aberto e conserva a sessão vencedora',async({page,playwright,baseURL},info)=>{
  await open(page);
  const administrator=await playwright.request.newContext({baseURL,userAgent:'BarDoZicoDesktopE2E/0.2.3'});
  try{
    const name='Concorrente '+info.project.name+' '+Date.now(),terminal=await apiCommand(administrator,'terminal.save',{name});
    await page.reload();await nav(page,'Caixa');await page.getByRole('button',{name:'Abrir caixa',exact:true}).click();
    const dialog=page.getByRole('dialog');await dialog.getByRole('combobox',{name:'Caixa',exact:true}).selectOption(terminal.id);
    await expect(dialog.getByLabel('Dinheiro inicial (R$)',{exact:true})).toHaveValue('0');
    let winner:any;
    await page.route('**/api/commands/cash.open',async route=>{
      if(!winner&&route.request().postDataJSON().terminalId===terminal.id)winner=await apiCommand(administrator,'cash.open',{terminalId:terminal.id,openingAmount:'29'});
      await route.continue();
    });
    await dialog.getByRole('button',{name:'Começar caixa',exact:true}).click();
    await expect(dialog.getByRole('alert')).toContainText('Este caixa já está aberto');
    await expect(dialog).not.toContainText('A operação conflita');
    await dialog.locator('.cart-line').filter({has:page.getByText(name,{exact:true})}).getByRole('button',{name:'Usar caixa aberto',exact:true}).click();
    await expect(page.getByRole('combobox',{name:'Caixa em uso',exact:true})).toHaveValue(winner.id);
    const state=await administrator.get('/api/state').then(r=>r.json()),sessions=state.sessions.filter((s:any)=>s.terminal_id===terminal.id);
    expect(sessions).toHaveLength(1);expect(Number(sessions[0].opening_amount)).toBe(29);
  }finally{await administrator.dispose();}
});

test.describe('APK exclusivo de atendimento',()=>{
  test.use({userAgent:'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36 BarDoZicoAndroid/0.2.3'});
  test('atende mesa e consulta cardápio enquanto a administração permanece no PC',async({page,playwright,baseURL},info)=>{
    const administrator=await playwright.request.newContext({baseURL,userAgent:'BarDoZicoDesktopE2E/0.2.3'});
    try{
      const suffix=info.project.name+' '+Date.now(),number='APK-'+Date.now().toString().slice(-8);
      const area=await apiCommand(administrator,'area.save',{name:'Atendimento '+suffix});
      const table=await apiCommand(administrator,'table.save',{number,areaId:area.id});
      const category=await apiCommand(administrator,'category.save',{name:'Celular '+suffix});
      const name='Bebida APK '+suffix,product=await apiCommand(administrator,'product.save',{name,categoryId:category.id,price:'10',destination:'SEM_PREPARO',stockControlled:true,stockQuantity:'10',stockMinimum:'2'});
      await page.route('**/api/me',route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({error:'Servidor temporariamente indisponível.'})}));
      await page.goto('/');
      await expect(page.getByRole('heading',{name:'Não foi possível abrir o serviço do bar',exact:true})).toBeVisible();
      await expect(page.getByRole('alert')).toContainText('Servidor temporariamente indisponível.');
      const recoverySettings=page.getByRole('link',{name:'Configurações',exact:true});
      await expect(recoverySettings).toBeVisible();await expect(recoverySettings).toHaveAttribute('href','bardozico://settings');
      await expect(page.getByRole('navigation')).toHaveCount(0);
      for(const name of ['Caixa','Estoque','Funcionários','Configurações','Voltar à gestão do bar'])await expect(page.getByRole('button',{name,exact:true})).toHaveCount(0);
      await page.unroute('**/api/me');await page.getByRole('button',{name:'Tentar novamente',exact:true}).click();
      await expect(page.getByRole('heading',{name:'Mesas',exact:true})).toBeVisible();
      const me=await page.request.get('/api/me').then(r=>r.json());
      expect(me.deviceAccess).toBe(true);expect(me.accessMode).toBe('ATENDIMENTO');
      expect(me.permissions.sort()).toEqual(['account.open','order.create']);
      await expect(page.locator('.main-nav > button')).toHaveText(['Mesas','Cardápio']);
      const settingsLink=page.locator('.main-nav > a');
      await expect(settingsLink).toHaveText('Configurações');await expect(settingsLink).toHaveAttribute('href','bardozico://settings');
      const menuButton=page.getByRole('button',{name:'Abrir menu',exact:true});
      if(await menuButton.isVisible()){
        await menuButton.click();await expect(settingsLink).toBeVisible();
        await settingsLink.focus();await page.keyboard.press('Tab');await expect(page.getByRole('navigation').getByRole('button',{name:'Mesas',exact:true})).toBeFocused();
        await page.keyboard.press('Shift+Tab');await expect(settingsLink).toBeFocused();
        await page.keyboard.press('Escape');await expect(menuButton).toBeFocused();
      }
      for(const name of ['Caixa','Estoque','Funcionários','Configurações','Voltar à gestão do bar','Adicionar mesa'])await expect(page.getByRole('button',{name,exact:true})).toHaveCount(0);
      await expect(page.getByText('Celular de atendimento',{exact:true})).toBeVisible();
      const denied=await page.request.post('/api/commands/terminal.save',{data:{name:'Não deve criar'},headers:{'X-Bar-Request':'1','Idempotency-Key':crypto.randomUUID()}});
      expect(denied.status()).toBe(403);
      await page.getByLabel('Buscar mesa',{exact:true}).fill(number);
      await page.getByRole('region',{name:'Mesa '+number,exact:true}).getByRole('button',{name:'Abrir mesa',exact:true}).click();
      await page.getByRole('button',{name:'Adicionar produto',exact:true}).click();
      const dialog=page.getByRole('dialog');await dialog.getByRole('combobox',{name:'Produto',exact:true}).selectOption(product.id);
      await dialog.getByLabel('Quantidade',{exact:true}).fill('2');await dialog.getByRole('button',{name:'Adicionar à mesa',exact:true}).click();
      await expect(page.locator('.total-line')).toContainText('20,00');
      await expect(page.getByRole('button',{name:'Ir para o caixa',exact:true})).toHaveCount(0);
      await expect(page.getByRole('button',{name:'Remover '+name,exact:true})).toHaveCount(0);
      const desktopState=await administrator.get('/api/state').then(r=>r.json()),account=desktopState.accounts.find((a:any)=>a.table_id===table.id&&a.status==='ABERTA');
      expect(Number(account.total)).toBe(20);
      const desktopDetails=await administrator.get('/api/accounts/'+account.id).then(r=>r.json());
      expect(desktopDetails.items).toHaveLength(1);expect(Number(desktopDetails.items[0].quantity)).toBe(2);
      expect(Number(desktopState.products.find((p:any)=>p.id===product.id).stock_quantity)).toBe(8);
      const mobileState=await page.request.get('/api/state').then(r=>r.json());
      for(const key of ['users','payments','sessions','cashSummaries','stockMovements','rolePermissions'])expect(mobileState[key]||[]).toHaveLength(0);
      await nav(page,'Cardápio');await page.getByLabel('Buscar produto',{exact:true}).fill(name);
      const card=page.locator('.product-card').filter({has:page.getByRole('heading',{name,exact:true})});
      await expect(card).toContainText('10,00');await expect(card).toContainText('Disponível');
      await expect(card).not.toContainText('Em estoque');await expect(card).not.toContainText('Quantidade não controlada');await expect(card.getByRole('button')).toHaveCount(0);
      await width(page);await page.screenshot({path:'test-results/'+info.project.name+'-apk-atendimento.png',fullPage:true});
    }finally{await administrator.dispose();}
  });
  test('consumo atualiza sem mudar total e desaparece quando o PC encerra',async({page,playwright,baseURL},info)=>{
    const administrator=await playwright.request.newContext({baseURL,userAgent:'BarDoZicoDesktopE2E/0.2.3'});
    try{
      const suffix=info.project.name+' '+Date.now(),number='Zero-'+Date.now().toString().slice(-8);
      const area=await apiCommand(administrator,'area.save',{name:'Atualização '+suffix});
      const table=await apiCommand(administrator,'table.save',{number,areaId:area.id});
      const category=await apiCommand(administrator,'category.save',{name:'Cortesias '+suffix});
      const initialName='Cortesia inicial '+suffix,initialProduct=await apiCommand(administrator,'product.save',{name:initialName,categoryId:category.id,price:'0',destination:'SEM_PREPARO',stockControlled:false,stockQuantity:'0',stockMinimum:'0'});
      const name='Cortesia '+suffix,product=await apiCommand(administrator,'product.save',{name,categoryId:category.id,price:'0',destination:'SEM_PREPARO',stockControlled:false,stockQuantity:'0',stockMinimum:'0'});
      const account=await apiCommand(administrator,'account.open',{tableId:table.id});
      await apiCommand(administrator,'order.create',{accountId:account.id,items:[{productId:initialProduct.id,quantity:'1'}]});
      await open(page);
      const me=await page.request.get('/api/me').then(r=>r.json());expect(me.accessMode).toBe('ATENDIMENTO');
      await page.getByLabel('Buscar mesa',{exact:true}).fill(number);
      const initialDetails=page.waitForResponse(response=>new URL(response.url()).pathname.startsWith('/api/accounts/')&&response.request().method()==='GET');
      await page.getByRole('region',{name:'Mesa '+number,exact:true}).getByRole('button',{name:'Ver consumo',exact:true}).click();
      expect((await (await initialDetails).json()).items).toHaveLength(1);
      await expect(page.getByRole('heading',{name:'Mesa '+number,exact:true})).toBeVisible();
      await expect(page.locator('.consumption-list')).toContainText(initialName);
      await expect(page.locator('.consumption-list>div')).toHaveCount(1);
      const beforeState=await page.request.get('/api/state').then(r=>r.json()),before=beforeState.accounts.find((a:any)=>a.table_id===table.id);
      await apiCommand(administrator,'order.create',{accountId:before.id,items:[{productId:product.id,quantity:'1'}]});
      await expect(page.locator('.consumption-list')).toContainText(name,{timeout:12000});
      await expect(page.locator('.consumption-list>div')).toHaveCount(2);
      await expect(page.locator('.total-line')).toContainText('0,00');
      const afterState=await page.request.get('/api/state').then(r=>r.json()),after=afterState.accounts.find((a:any)=>a.id===before.id);
      expect(after).toEqual(before);
      await apiCommand(administrator,'account.close',{accountId:before.id});
      await expect(page.getByRole('heading',{name:'Atendimento encerrado',exact:true})).toBeVisible({timeout:12000});
      await expect(page.getByText('O consumo deste atendimento não está mais aberto para alterações.',{exact:true})).toBeVisible();
      await expect(page.locator('.consumption-list')).toHaveCount(0);
      await expect(page.getByRole('button',{name:'Adicionar produto',exact:true})).toHaveCount(0);
      await page.getByRole('button',{name:'← Voltar às mesas',exact:true}).click();
      await expect(page.getByRole('region',{name:'Mesa '+number,exact:true}).getByRole('button',{name:'Abrir mesa',exact:true})).toBeEnabled();
    }finally{await administrator.dispose();}
  });
});
