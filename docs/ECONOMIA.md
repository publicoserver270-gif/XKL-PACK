# Economia virtual — primeira implementação

Este documento descreve o que está implementado no primeiro estágio, sem confundir funcionalidades planejadas com funcionalidades prontas.

## Persistência

O armazenamento usa SQLite no arquivo configurado em `database.sqlite-file` (padrão: `plugins/XKL-PACK/data/xklpack.db`). As tabelas guardam perfis, saldos por moeda e histórico de transações. Valores monetários são armazenados como texto decimal para evitar erros de ponto flutuante.

## Moedas

As moedas são definidas em `economy.currencies`. Cada moeda possui nome, símbolo, casas decimais e opção de habilitar transferências. A configuração inicial contém:

- `coins`: moeda virtual do salário.
- `gems`: moeda de recompensa por atividades profissionais.

Não existe conversão automática entre moedas.

## Regras de emissão e uso no serviço

- O método `paySalary` é o caminho de emissão de coins.
- O método `rewardProfessionActivity` é o caminho de emissão de gemas.
- O método `spendGemsForTool` é o caminho de débito de gemas para ferramentas.
- Transferências apenas movem saldo existente entre jogadores; não criam moeda.
- Não existe método genérico de crédito público no serviço.

Os módulos de profissões e kits ainda serão conectados a esses métodos. Até lá, não há pagamento automático de salários nem recompensa automática por atividade.

## Comandos disponíveis

### Transferência

`/pay <jogador> <valor> [coins|gems]`

- O destinatário precisa estar online nesta etapa.
- Se a moeda for omitida, será usada coins.
- Não permite transferir para si mesmo ou ficar com saldo negativo.
- A transferência e o registro no histórico são gravados na mesma transação SQLite.

### Ranking

- `/top [coins|gems]`: ranking geral dos jogadores registrados.
- `/top online [coins|gems]`: filtra os jogadores online.

Os saldos de moedas diferentes nunca são somados.

## Ainda não implementado

- GUI do banco e dinheiro físico (depósito/saque).
- Taxas e denominações físicas.
- Integração de profissões com metas de produtividade e pagamento salarial.
- Recompensas de gemas por atividade.
- Compra de ferramentas com gemas.
- GUI de loja, venda de itens e mercado de jogadores.
- Extrato acessível dentro do jogo.
- Comandos para administração e configuração.

O comando `/sell` permanece sem pagamento definido. Não será configurado para pagar coins ou gemas até que a regra de recompensa seja decidida, pois isso violaria as fontes exclusivas de emissão já estabelecidas.
