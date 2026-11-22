# Especificação funcional — XKL PACK

Este documento consolida as decisões já definidas. Itens marcados como **pendentes** não devem ser implementados com valores presumidos.

## 1. Arquitetura

- Um único plugin e um único JAR.
- Sistemas separados em módulos internos, nunca em plugins independentes.
- Projeto genérico e configurável para diferentes servidores.
- Persistência inicial planejada em SQLite; suporte futuro a MySQL.
- Integrações externas opcionais, quando necessárias e compatíveis.
- Desenvolvimento paralelo aos plugins atuais; migração somente após backup, testes e validação.

## 2. Moedas e economia

### 2.1 Moedas independentes

Cada moeda possui identificador, nome, símbolo, casas decimais, saldo virtual, itens físicos, denominações, taxas e histórico próprios. Não há conversão automática entre moedas.

### 2.2 Coins

- Fontes autorizadas de emissão de coins: salário por meta profissional concluída e venda confirmada na loja do servidor `/sell`.
- O salário só pode ser pago enquanto o jogador estiver empregado em uma profissão.
- O pagamento depende de produtividade e metas de atividade, não apenas de tempo online.
- Exemplos de metas: minerador quebra uma quantidade configurada de blocos; caçador derrota uma quantidade configurada de criaturas.
- O salário é pago imediatamente após a conclusão de cada meta. O valor-base configurado da meta é multiplicado pelo multiplicador atual da profissão.

### 2.3 Gemas

- A única fonte de emissão de gemas são atividades de trabalhos/profissões.
- Gemas são usadas exclusivamente para comprar ferramentas.
- Não usar gemas em lojas administrativas comuns, blocos, armaduras ou outros produtos.

### 2.4 Dinheiro físico e virtual

- Dinheiro físico é representado por itens configuráveis (moedas/cédulas).
- Dinheiro virtual é mantido em saldo bancário.
- Lojas de jogadores aceitam somente dinheiro físico.
- Lojas administrativas aceitam somente saldo virtual.
- Transferência física ocorre pela entrega dos itens.
- Transferência virtual ocorre pelo sistema bancário/comando.
- Depósitos e saques podem ter taxas separadas, configuráveis, com valor e destino definidos pela administração.
- Não permitir saldo negativo; registrar transações e proteger operações contra duplicação/perda de itens.

## 3. Profissões, níveis e produtividade

- `/job` abre a interface de profissões, níveis, XP, atividades, recompensas e progresso.
- Profissões iniciais previstas: Minerador, Lenhador, Fazendeiro, Pescador e Caçador.
- As atividades válidas, metas, XP, recompensas e salários devem ser configuráveis.
- Gems são concedidas por atividades elegíveis das profissões.
- Coins são concedidas somente por salário, após as regras de produtividade.

## 4. Kits e ferramentas de profissão

- Ao entrar em uma profissão, o jogador recebe o kit correspondente.
- Ferramentas do kit podem ser movidas livremente entre slots do próprio inventário.
- Não podem ser dropadas, guardadas em baús, barris, shulkers ou transferidas para outros jogadores/inventários; bloquear também funis e meios alternativos.
- Ao sair da profissão, as ferramentas do kit devem desaparecer da posse do jogador.
- Kits evoluem automaticamente conforme o nível da profissão.
- A evolução substitui a ferramenta antiga por uma versão superior; não deve duplicar ferramentas.
- A ferramenta nova recebe os encantamentos previstos para o novo nível.
- A durabilidade deve ser preservada proporcionalmente durante a substituição.
- Ferramentas devem possuir identificação interna para reconhecer o kit/profissão e impedir falsificações/transferências.
- Limites de equipamentos especiais são desbloqueados por nível: progressão de espaços disponíveis e limite de equipamentos simultaneamente equipados/ativos.
- Exemplo ilustrativo para minerador, ainda configurável: madeira no nível 1, pedra no 5, ferro no 10, ouro no 15, diamante no 20 e netherita no 30.

**Pendente:** confirmar se o progresso/durabilidade do kit é guardado quando o jogador sai da profissão e volta posteriormente. A regra já confirmada é que o kit sai do inventário ao deixar a profissão.

## 5. Lojas, venda e mercado

### 5.1 `/loja`

- Catálogo administrativo em GUI.
- Categorias previstas: comidas, blocos de construção, pedras/tijolos, madeiras, vidros, redstone, ferramentas e materiais.
- Compras pagas exclusivamente com saldo virtual.
- Preços e categorias configuráveis.

### 5.2 `/sell`

- Abre uma interface/baú temporário onde o jogador coloca os itens para venda ao servidor.
- Mostrar prévia/valor e pedir confirmação.
- Só remover itens depois que a transação estiver garantida.
- Proteção contra duplicação e falhas de transação.
- Preços devem refletir etapas de processamento: produto processado vale mais que a matéria-prima anterior; pepitas valem exatamente 1/9 do lingote; blocos devem valer mais que os lingotes/barras equivalentes.
- Regra aplicável a minérios, madeira, agricultura, drops de criaturas, pedras e outros grupos.
- Totem da imortalidade (Totem of Undying) fica fora da lista de venda.
- Lista de itens e preços configurável; evitar itens que desequilibrem a economia.

**Regra confirmada:** vendas aprovadas em `/sell` podem emitir Coins. Essa é a única exceção à emissão salarial de Coins. `/sell` não pode emitir Gemas. A venda precisa registrar a transação e só concluir depois de validar os itens e o pagamento.

### 5.3 Lojas de jogadores e mercado

- Lojas de jogadores aceitam exclusivamente dinheiro físico e podem ser mantidas pelo Shopkeepers sem ShopkeepersAddon.
- Mercado/leilão de jogadores só será implementado após definição explícita do acesso e das regras; não criar comando adicional sem aprovação.

## 6. Banco, transferências e rankings

### 6.1 `/atm`

Interface de caixa eletrônico para:
- Consultar saldo e extrato.
- Depositar dinheiro físico.
- Sacar dinheiro virtual em itens físicos.
- Escolher moeda.
- Exibir taxas de depósito e saque.

### 6.2 `/pay`

- Transferência entre jogadores usando saldo virtual bancário.
- Exemplos previstos: `/pay Steve 500`, `/pay Steve 500 coins`, `/pay Steve 100 gems`.
- Não permitir saldo negativo.
- Taxas, limites e histórico configuráveis.
- Transferência física é feita pela entrega dos itens, não por um comando separado.

### 6.3 Rankings

- `/top`: ranking geral, incluindo jogadores offline.
- `/top online`: ranking apenas de jogadores online.
- Exibir moedas separadamente; nunca somar moedas diferentes sem conversão explícita.
- Considerar saldos físicos e virtuais de forma clara e consistente.

## 7. Comandos principais

Somente estes comandos principais foram solicitados:
- `/loja`
- `/sell`
- `/job`
- `/top` e `/top online`
- `/pay`
- `/atm`

Não criar comandos principais extras para jogadores sem autorização. Comandos administrativos e permissões serão definidos na implementação.

## 8. Pendências antes de fechar o comportamento

1. Forma e frequência exatas do pagamento salarial após cumprir metas.
2. Moeda/recompensa da venda em `/sell`, respeitando as fontes exclusivas de coins e gemas.
3. Se o progresso/durabilidade do kit é preservado ao sair e retornar à profissão.
4. Definição final das taxas, limites, denominações e destino das taxas.
5. Forma de acesso ao mercado de jogadores.
6. Versão exata da API Paper para compilar e testar contra o servidor-alvo.
