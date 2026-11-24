# Lojas de jogadores — XKL PACK

**Status: implementação funcional em andamento — ainda não homologada.** Já existem: compra de alvarás; reserva da área com espera de 2 minutos; comerciante Villager nativo do Paper com nome permanente e único; baú ESTOQUE protegido; catálogo de produtos abastecido pelo baú; pagamento com moedas físicas autenticadas e troco; cofre físico por valor; painel básico; edição de preços; abrir/fechar loja; aliados com permissões separadas; validação da área inteira no WorldGuard. Ainda faltam validação de build/testes, recuperação transacional contra queda no instante exato da compra/retirada e adaptadores para plugins de proteção diferentes do WorldGuard. Nesses plugins sem adaptador, a criação é bloqueada por segurança.

## Objetivo

Criar lojas de jogadores no próprio XKL PACK, inspiradas na experiência do Shopkeepers, sem depender do ShopkeepersAddon. O comerciante é um Villager nativo do Paper, sem dependência do Citizens; estoque, pagamentos, permissões, painel e persistência ficam sob controle do XKL PACK.

## Fluxo de abertura

1. O jogador abre a loja própria com `/alvaras` e compra um dos seis alvarás comerciais disponíveis. A loja usa Coins do saldo virtual; nomes, preços, ícones, áreas e disponibilidade são configuráveis em `permits.yml`.
2. Usa o alvará no bloco/local exato onde deseja abrir a loja.
3. Antes de consumir o alvará, o plugin valida que o jogador pode usar o local, que há espaço, que a loja não se sobrepõe a outra e que o local é aceito pela proteção de terrenos.
4. Após a validação, o alvará é consumido e a loja entra no estado PREPARING.
5. Começa uma espera de **2 minutos**. O estado e o horário final ficam persistidos no SQLite para sobreviver a reinicializações.
6. Ao terminar a espera, o NPC comerciante é criado/vinculado uma única vez e a loja passa ao estado ACTIVE.
7. Se o servidor reiniciar durante a espera, o sistema retoma usando o horário salvo; não reinicia os 2 minutos nem cria NPC duplicado.

O catálogo contém seis opções configuráveis em `permits.yml`: Básico (7x7; 1.000 Coins), Comercial (11x11; 2.500 Coins), Premium (17x17; 5.000 Coins), Empresarial (25x25; 10.000 Coins), Territorial (64x64; 100.000 Coins) e Imperial (128x128; 1.000.000 Coins). Os dois últimos foram adicionados como alvarás de grande porte. Preços e dimensões são valores iniciais editáveis, não valores fixos no código.

## Comerciante NPC

- Cada loja recebe automaticamente um nome próprio de pessoa, escolhido de uma lista interna.
- O nome deve ser único entre os comerciantes ativos e persistido. Não pode ser editado pelo dono nem por aliados.
- O nome fica visível acima da cabeça.
- O NPC deve andar/passear dentro da área delimitada da loja e nunca sair dela.
- O clique no NPC abre a interface de compra, que consulta as ofertas e o estoque físico do baú ESTOQUE.
- O NPC não pode receber dano, ser empurrado, levado, removido por jogadores ou usado para contornar proteção.
- Em reinicializações, o XKL PACK reconcilia o NPC salvo com o Citizens e evita duplicatas.

## Área e proteção do terreno

- A loja tem uma área própria delimitada, vinculada ao ponto de criação e à proteção do terreno.
- O movimento do NPC fica restrito a essa área.
- O XKL PACK concede somente a autorização técnica mínima para o NPC circular e para as operações da loja.
- Não desativar nem ignorar a proteção geral do terreno.
- Antes de aceitar o alvará, verificar a área quadrada INTEIRA correspondente ao tamanho comprado (inclusive os limites), e não apenas o bloco clicado ou o centro. Consultar todas as regiões/claims protegidos que cruzem ou estejam dentro dessa área.
- Se qualquer parte da área estiver protegida por outro jogador, recusar a criação. A única exceção é quando o proprietário de cada área protegida envolvida for exatamente o jogador que está usando o alvará. Não permitir que um claim de terceiro seja invadido, mesmo que a maior parte da área esteja livre ou pertença ao comprador.
- Se a área cruzar claims de mais de um proprietário, recusar quando algum deles não for o próprio comprador. Claims do próprio comprador podem ser utilizados; áreas sem proteção também podem ser utilizadas, desde que não haja conflito com outra loja.
- A verificação deve acontecer antes de consumir o alvará ou cobrar qualquer valor adicional. Em caso de recusa, manter o item no inventário e informar que existe terreno protegido de outro jogador na área.
- O XKL PACK não exige EasyClaim. Deve integrar-se às APIs dos plugins de proteção presentes no servidor. Se um plugin de proteção estiver instalado e não houver integração capaz de identificar com segurança o dono da região, bloquear a criação naquela área (fail closed); nunca ignorar ou desativar a proteção externa.
- A área, o baú e o NPC permanecem vinculados à mesma loja e ao mesmo proprietário.

## Baú ESTOQUE

- Cada loja recebe um baú especial com identificação persistente ESTOQUE.
- O baú é vinculado à loja, não apenas ao nome ou localização visual.
- Só o dono e aliados com permissão de estoque podem abrir e organizar seu conteúdo.
- Outros jogadores não podem abrir, quebrar, mover, explodir, acessar por funil ou retirar conteúdo.
- Os produtos cadastrados no NPC são retirados desse estoque no momento de uma venda confirmada.
- Se não houver quantidade suficiente, a venda é recusada sem cobrar o comprador.
- O estoque é protegido contra duplicação em reinicializações, explosões, pistões e movimentação de blocos.

## Produtos, preços e compras

- O dono cadastra produtos usando itens do estoque.
- Define o preço por unidade/pacote em moedas físicas XKL e a quantidade por oferta.
- O painel permite editar/remover ofertas, respeitando o estoque disponível.
- O comprador visualiza item, quantidade e preço antes de confirmar.
- O pagamento aceita exclusivamente moedas físicas XKL autenticadas com PDC e assinatura HMAC pelo serviço bancário do plugin.
- Moedas comuns, falsificadas, Coins virtuais e Gemas são rejeitadas.
- A transação deve ser atômica: validar pagamento e estoque, remover os itens e entregar o produto sem perda ou duplicação em caso de erro.
- O dinheiro recebido fica em um cofre/estoque físico da loja, associado à loja. O dono ou aliado autorizado pode retirar.
- Registrar histórico de vendas, produtos, quantidades, valores e envolvidos.

## Dono e aliados

- O proprietário tem controle total e não pode ser removido da própria loja.
- O dono pode convidar/remover aliados.
- Permissões separadas: abrir/organizar o baú ESTOQUE; cadastrar/editar/remover produtos e preços; retirar dinheiro físico; abrir/fechar a loja e gerenciar configurações.
- Não permitir que aliados transfiram propriedade ou alterem o nome permanente do NPC.
- Todas as ações administrativas validam a UUID do jogador, não apenas o nome.

## Painel da loja

Seguir o padrão visual, navegação por categorias e submenus do /xkladmin, mas com dados e permissões da loja selecionada.

Categorias:
1. **Estoque** — abrir/consultar o baú e ver quantidades.
2. **Produtos e preços** — cadastrar produtos, quantidade por oferta e preço.
3. **Aliados** — convidar, remover e ajustar permissões separadas para estoque, produtos, retirada e configuração.
4. **Dinheiro** — saldo físico guardado, histórico e retirada.
5. **Aparência** — configurações visuais da loja; não permite renomear o NPC.
6. **Configurações** — abrir/fechar, área e opções permitidas.

## Persistência e segurança

- Persistir loja, proprietário, mundo/coordenadas, área, estado da preparação, horário de conclusão, NPC ID, nome próprio, baú, aliados, permissões, ofertas, estoque e dinheiro recebido.
- Usar transações SQLite e identificadores persistentes.
- Validar moedas pelo serviço oficial de autenticação do XKL PACK; nunca confiar em nome/lore.
- Prevenir duplicação por clique duplo, lag, reinicialização, falha de entrega e eventos repetidos.
- Se uma loja for removida, tratar NPC, estoque, ofertas e dinheiro de forma segura; não apagar valores ou itens sem confirmação explícita.
- O nome do NPC é permanente, único e não editável.
- Nenhum erro de integração deve desativar a proteção de terrenos.

## Dependências e ordem de implementação

1. Implementar verificadores/adaptadores para os plugins de proteção instalados e validar a propriedade de toda a área do alvará; a loja não depende do EasyClaim. Se não for possível identificar o proprietário de uma proteção, recusar a criação.
2. Implementar persistência, alvará autenticado e fluxo de preparação de 2 minutos.
3. Implementar criação/reconciliação do NPC, nome único e movimento delimitado.
4. Implementar baú ESTOQUE protegido e permissões de aliados.
5. Implementar catálogo, painel, preços e compra com moedas físicas.
6. Implementar cofre/retirada, histórico, recuperação e testes anti-duplicação.
7. Testar em servidor Paper 26.3 com Citizens e com os plugins de proteção que o servidor realmente utilizar.

## Critérios de aceite

- Alvará inválido ou área que contenha proteção de terceiros não é consumido.
- A verificação percorre a área completa (7x7, 11x11, 17x17, 25x25, 64x64 ou 128x128), não apenas o ponto central.
- Claims do próprio jogador são permitidos; qualquer claim de outro jogador dentro da área bloqueia a criação.
- Quando uma proteção existente não puder ser consultada com segurança, a criação é bloqueada.
- Um alvará válido inicia exatamente 2 minutos de preparação.
- Reiniciar o servidor durante a espera não perde nem duplica a loja.
- NPC aparece com nome de pessoa único, permanente e visível.
- NPC passeia dentro dos limites e não sai da loja.
- Clicar no NPC abre a loja.
- Somente dono/aliados autorizados acessam estoque e painel.
- Venda usa moedas físicas autenticadas, retira o produto e credita o dinheiro físico da loja uma única vez.
- Coins virtuais, Gemas e itens falsos não compram produtos.
- A proteção do terreno continua ativa para todos os demais jogadores e ações.

## Comandos disponíveis na implementação atual

- `/alvaras` — comprar alvará com Coins virtuais.
- `/lojaplayer lojas` — listar IDs das próprias lojas.
- `/lojaplayer painel <id>` — abrir painel da loja.
- Segure os itens e use `/lojaplayer produto adicionar <id> <quantidade> <preço>` — cadastrar oferta e guardar os itens no baú ESTOQUE.
- `/lojaplayer produto listar <id>` — listar ofertas.
- `/lojaplayer produto preco <id> <produto-id> <novo-preço>` — alterar preço.
- `/lojaplayer produto remover <id> <produto-id>` — remover oferta; os itens continuam no baú.
- `/lojaplayer cofre <id> saldo` — consultar o valor físico guardado.
- `/lojaplayer cofre <id> retirar` — retirar moedas físicas autenticadas.
- `/lojaplayer vendas <id> [limite]` — consultar o histórico recente.
- `/lojaplayer loja <id> abrir|fechar` — controlar o catálogo.
- `/lojaplayer aliado adicionar <id> <jogador>` e `remover` — administrar aliados.
- `/lojaplayer aliado permissao <id> <jogador> <estoque|produtos|retirar|configurar> <on|off>` — ajustar cada autorização.

Ao adicionar um aliado, todas as permissões começam desligadas. O proprietário deve liberar explicitamente cada categoria necessária.

## Endurecimento adicional (23/11/2026)

- Alvarás agora recebem um número de série UUID no PDC. A reserva registra esse número em coluna única no SQLite; cópias do mesmo alvará não conseguem abrir uma segunda loja.
- A remoção do alvará usa o item que ainda está na mão principal e só acontece depois que a reserva persistente é confirmada.
- A ativação recupera NPCs e baús ESTOQUE que já tenham sido criados antes de uma interrupção, evitando duplicação durante a retomada.
- Lojas ativas e fechadas passam por reconciliação periódica: se o comerciante ou o baú protegido desaparecer, o runtime tenta recriá-lo e preserva o estado aberto/fechado.
- O tamanho de área é limitado a 128 blocos por lado, e o crédito no cofre recusa operações que causariam estouro do inteiro de 64 bits.
- Falhas de geração do Villager e de persistência do baú são tratadas sem consumir o alvará nem deixar a ativação marcada como concluída.

### Riscos que ainda exigem homologação

- A transação entre SQLite e inventários Bukkit não é atomicamente recuperável em caso de queda exatamente durante compra, cadastro de produto ou retirada do cofre. O sistema valida espaço e estoque antes de alterar dados e falha fechado quando encontra inconsistência, mas ainda precisa de um diário de operações/entrega pendente para garantia de recuperação após crash.
- Somente WorldGuard possui validação de propriedade implementada. GriefPrevention, Lands, Towny, Residence, EasyClaim, DyClaim, RedProtect, PlotSquared, GriefDefender e SuperiorSkyblock2 bloqueiam a criação enquanto não houver adaptador seguro.
- A reserva comercial não substitui um sistema completo de proteção de construção para todo o terreno da loja. O baú ESTOQUE é protegido; a proteção de todos os blocos do perímetro precisa de política própria de construção/permissões.
- Nenhuma build ou teste foi executado nesta etapa; compatibilidade com a API Paper configurada deve ser confirmada antes de produção.
