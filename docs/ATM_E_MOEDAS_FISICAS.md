# ATM e moedas físicas do XKL PACK

## Menu principal

O comando `/atm` abre uma GUI com quatro opções:

1. **Depositar** — recolhe todas as moedas físicas autenticadas do inventário.
2. **Sacar** — o jogador digita um valor inteiro no chat; `cancelar` interrompe.
3. **Extrato** — mostra as últimas sete movimentações do ATM.
4. **Taxas** — mostra as taxas configuradas.

O jogador escolhe o valor do saque, mas não escolhe as denominações. O sistema calcula automaticamente as moedas.

## Denominações oficiais

| Item | Valor |
|---|---:|
| Pepita de cobre (`copper_nugget`) | 1 Coin |
| Pepita de ferro (`iron_nugget`) | 5 Coins |
| Pepita de ouro (`gold_nugget`) | 10 Coins |
| Esmeralda (`emerald`) | 50 Coins |
| Diamante (`diamond`) | 100 Coins |
| Netherite (`netherite_ingot`) | 200 Coins |

A denominação Netherite usa lingote de netherite como item-base. A pepita de cobre usa o identificador informado para o servidor (`copper_nugget`). O material precisa existir na API/material registry do servidor alvo.

## Aparência e autenticidade

- Moedas geradas pelo ATM recebem nome personalizado, valor na lore e brilho.
- O PDC do XKL PACK identifica a moeda e a denominação.
- Nome, lore e brilho não são prova de autenticidade.
- Itens sem PDC válido ou com material diferente da denominação são recusados.
- **Limite de segurança atual:** o PDC autentica o formato do item, mas ainda não há número de série individual nem livro-razão de emissão para detectar cópias de uma moeda legítima. Não tratar isso como proteção completa contra duplicação por exploits externos.

## Saque automático

O cálculo prioriza as maiores denominações: netherite, diamante, esmeralda, ouro, ferro e cobre.

Exemplo sem taxa: 356 Coins = 1 netherite (200) + 1 diamante (100) + 1 esmeralda (50) + 1 pepita de ferro (5) + 1 pepita de cobre (1).

A taxa de saque é descontada do valor debitado. O valor líquido entregue é arredondado para baixo até um número inteiro de Coins que possa ser representado pelas moedas físicas.

## Taxas iniciais

- Depósito: 0%.
- Saque: 2%.

Os valores ficam em `plugins/XKL-PACK/bank/settings.yml`.

## Segurança e transações

- O saldo virtual e as moedas físicas são representações separadas de Coins.
- O depósito só aceita itens com PDC válido.
- O saque verifica saldo e espaço no inventário antes de debitar.
- Depósitos e saques são registrados na tabela de transações do banco SQLite.
- Fechar a GUI não executa uma operação.
- Ações da GUI são processadas no servidor e os cliques no inventário são cancelados.

## Estado da implementação

**Programado no repositório:** comando `/atm`, GUI de quatro opções, depósito de todas as moedas válidas, saque por valor digitado no chat, cálculo automático de denominações, consulta das últimas movimentações e visualização das taxas.

**Ainda pendente:** depósito por quantidade personalizada, serialização/controle anti-cópia robusto, testes automatizados de transações e teste real em servidor Paper 26.3. A implementação foi enviada ao GitHub, mas ainda não há confirmação de compilação bem-sucedida para esta versão.


## Decisões adicionais confirmadas

- O saque físico será entregue dentro de um saco de couro normal do Minecraft, exclusivo para dinheiro do XKL PACK.
- O saco terá conteúdo próprio e abrirá uma GUI personalizada ao clicar com o botão direito. Não será um recipiente genérico para itens.
- A GUI do saco permitirá visualizar, guardar e retirar somente moedas físicas autenticadas.
- O jogador poderá carregar dinheiro físico sem limite global; esse dinheiro poderá ser perdido normalmente ao morrer.
- Os caixas eletrônicos existirão somente em locais específicos, vinculados a NPCs do Citizens por comando administrativo. O jogador acessará o ATM clicando no NPC cadastrado.
- O extrato exibirá somente movimentações dos últimos 7 dias.

## Correção técnica aplicada nesta etapa

- O saque agora simula a entrega em um inventário temporário antes de debitar o saldo. A validação considera os materiais e os limites reais de pilha, evitando a estimativa simplificada de espaço.
- O extrato passou a filtrar movimentações com idade máxima de 7 dias.

## Limitações ainda abertas

- A GUI do saco de couro ainda precisa ser implementada.
- O vínculo administrativo de NPCs Citizens ainda precisa ser implementado.
- A assinatura HMAC e o segredo persistente de autenticação ainda precisam ser implementados.
- A assinatura autentica a origem/configuração de uma moeda, mas uma cópia byte a byte de uma moeda legítima continua sendo indistinguível de sua original. Para detectar cópias de moedas empilháveis seria necessário um modelo de unidades serializadas, que conflita com o empilhamento normal. O sistema deve proteger transações e impedir duplicações causadas pelo próprio plugin, sem prometer detectar toda cópia externa de item.
