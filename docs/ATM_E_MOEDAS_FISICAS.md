# ATM e moedas físicas do XKL PACK

## Menu principal

O comando público será `/atm`. A interface terá quatro opções:

1. **Depositar** — depositar todas as moedas físicas válidas ou informar uma quantidade.
2. **Sacar** — informar um valor predefinido ou digitar um valor personalizado.
3. **Extrato** — consultar depósitos, saques e taxas.
4. **Taxas** — consultar as tarifas atuais.

O jogador escolhe a operação e o valor, nunca as denominações individuais.

## Denominações oficiais

| Item | Valor |
|---|---:|
| Pepita de cobre (`copper_nugget`) | 1 Coin |
| Pepita de ferro (`iron_nugget`) | 5 Coins |
| Pepita de ouro (`gold_nugget`) | 10 Coins |
| Esmeralda (`emerald`) | 50 Coins |
| Diamante (`diamond`) | 100 Coins |
| Netherite (`netherite_ingot`) | 200 Coins |

A denominação Netherite usa lingote de netherite como item-base. A pepita de cobre usa o identificador informado pelo administrador do servidor (`copper_nugget`); a disponibilidade precisa ser validada na API/material registry do servidor alvo.

## Aparência e autenticidade

- Todas as moedas terão brilho de encantamento.
- Nome personalizado e valor visível na lore.
- Cada moeda receberá PersistentDataContainer (PDC) com namespace do XKL PACK, identificador da denominação e valor.
- Nome, lore e brilho não são prova de autenticidade.
- Itens sem PDC válido não poderão ser depositados.
- Itens com valor/denominação adulterados serão recusados.

## Saque automático

O sistema calcula a menor quantidade possível de moedas, priorizando a maior denominação:
netherite, diamante, esmeralda, ouro, ferro e cobre.

Exemplo: 356 Coins = 1 netherite (200) + 1 diamante (100) + 1 esmeralda (50) + 1 pepita de ferro (5) + 1 pepita de cobre (1).

## Taxas iniciais

- Depósito: 0%.
- Saque: 2%.

Ambas serão editáveis em `bank/settings.yml`.

## Regras financeiras e segurança

- Saldo virtual e moedas físicas são representações separadas de Coins.
- Depósito remove moedas físicas autenticadas e credita Coins virtuais.
- Saque debita Coins virtuais e entrega moedas físicas.
- O saque só deve ser confirmado se houver espaço suficiente no inventário.
- Extrato registra valor bruto, taxa, valor líquido, operação e data.
- Transações devem ser protegidas contra clique duplo, reentrada e falhas.
- Fechar o menu ou cancelar uma confirmação não pode consumir itens nem saldo.
- Moedas físicas não podem ser criadas por renomear itens em bigorna ou usar comandos comuns.

## Estado

Este documento e os arquivos YAML registram a especificação aprovada. A implementação Java do comando, GUI, PDC, transações e extrato ainda precisa ser concluída e testada antes de considerar o ATM funcional.
