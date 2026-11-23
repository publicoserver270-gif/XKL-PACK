# Lojas de jogadores — XKL PACK

**Status: especificação aprovada; implementação ainda não iniciada.** Este documento é a referência funcional para desenvolver o módulo.

## Objetivo

Criar lojas de jogadores no próprio XKL PACK, inspiradas na experiência do Shopkeepers, sem depender do ShopkeepersAddon. Citizens será usado para a entidade comerciante quando a integração estiver validada; estoque, pagamentos, permissões, painel e persistência ficam sob controle do XKL PACK.

## Fluxo de abertura

1. O jogador compra um **Alvará Comercial** na loja administrativa do servidor.
2. Usa o alvará no bloco/local exato onde deseja abrir a loja.
3. Antes de consumir o alvará, o plugin valida que o jogador pode usar o local, que há espaço, que a loja não se sobrepõe a outra e que o local é aceito pela proteção de terrenos.
4. Após a validação, o alvará é consumido e a loja entra no estado PREPARING.
5. Começa uma espera de **2 minutos**. O estado e o horário final ficam persistidos no SQLite para sobreviver a reinicializações.
6. Ao terminar a espera, o NPC comerciante é criado/vinculado uma única vez e a loja passa ao estado ACTIVE.
7. Se o servidor reiniciar durante a espera, o sistema retoma usando o horário salvo; não reinicia os 2 minutos nem cria NPC duplicado.

O preço e a moeda de compra do alvará ainda não foram definidos. Não colocar preço presumido no catálogo; deixar a entrada administrativa pendente até decisão.

## Comerciante NPC

- Cada loja recebe automaticamente um nome próprio de pessoa, escolhido de uma lista interna.
- O nome deve ser único entre os comerciantes ativos e persistido. Não pode ser editado pelo dono nem por aliados.
- O nome fica visível acima da cabeça.
- O NPC deve andar/passear dentro da área delimitada da loja e nunca sair dela.
- O clique no NPC abre a interface de compra.
- O NPC não pode receber dano, ser empurrado, levado, removido por jogadores ou usado para contornar proteção.
- Em reinicializações, o XKL PACK reconcilia o NPC salvo com o Citizens e evita duplicatas.

## Área e proteção do terreno

- A loja tem uma área própria delimitada, vinculada ao ponto de criação e à proteção do terreno.
- O movimento do NPC fica restrito a essa área.
- O XKL PACK concede somente a autorização técnica mínima para o NPC circular e para as operações da loja.
- Não desativar nem ignorar a proteção geral do terreno.
- A integração deve validar a API e a versão do EasyClaim instalado. Se não houver integração segura, bloquear a criação/ativação e informar o motivo; nunca abrir o terreno como alternativa.
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
3. **Aliados** — convidar, remover e ajustar permissões.
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

1. Confirmar as APIs/versões de Citizens e EasyClaim e definir o limite físico da loja.
2. Implementar persistência, alvará autenticado e fluxo de preparação de 2 minutos.
3. Implementar criação/reconciliação do NPC, nome único e movimento delimitado.
4. Implementar baú ESTOQUE protegido e permissões de aliados.
5. Implementar catálogo, painel, preços e compra com moedas físicas.
6. Implementar cofre/retirada, histórico, recuperação e testes anti-duplicação.
7. Testar em servidor Paper 26.3 com Citizens e EasyClaim reais.

## Critérios de aceite

- Alvará inválido ou local protegido sem autorização não é consumido.
- Um alvará válido inicia exatamente 2 minutos de preparação.
- Reiniciar o servidor durante a espera não perde nem duplica a loja.
- NPC aparece com nome de pessoa único, permanente e visível.
- NPC passeia dentro dos limites e não sai da loja.
- Clicar no NPC abre a loja.
- Somente dono/aliados autorizados acessam estoque e painel.
- Venda usa moedas físicas autenticadas, retira o produto e credita o dinheiro físico da loja uma única vez.
- Coins virtuais, Gemas e itens falsos não compram produtos.
- A proteção do terreno continua ativa para todos os demais jogadores e ações.