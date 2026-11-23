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
- Dinheiro virtual é mantido em saldo próprio de Coins/Gemas. A GUI `/carteira` mostra os saldos virtuais e o histórico; não é o saco físico nem o ATM.
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
- Coins são concedidas por salário após as regras de produtividade e por vendas confirmadas em `/sell`.

## 4. Kits e ferramentas de profissão

- Ao entrar em uma profissão, o jogador recebe o kit correspondente.
- Ferramentas do kit podem ser movidas livremente entre slots do próprio inventário.
- Não podem ser dropadas, guardadas em baús, barris, shulkers ou transferidas para outros jogadores/inventários; bloquear também funis e meios alternativos.
- Ao sair da profissão, as ferramentas do kit são removidas do inventário e seu estado é guardado por profissão; ao voltar, o kit é restaurado.
- Kits evoluem automaticamente conforme o nível da profissão.
- A evolução substitui a ferramenta antiga por uma versão superior; não deve duplicar ferramentas.
- A ferramenta nova recebe os encantamentos previstos para o novo nível.
- A durabilidade deve ser preservada proporcionalmente durante a substituição.
- Ferramentas devem possuir identificação interna para reconhecer o kit/profissão e impedir falsificações/transferências.
- Limites de equipamentos especiais são desbloqueados por nível: progressão de espaços disponíveis e limite de equipamentos simultaneamente equipados/ativos.
- Exemplo ilustrativo para minerador, ainda configurável: madeira no nível 1, pedra no 5, ferro no 10, ouro no 15, diamante no 20 e netherita no 30.

**Implementação inicial:** o estado e a durabilidade do kit são guardados ao sair e restaurados ao retornar. A evolução automática está configurada nos níveis 1, 5, 10, 15, 20 e 30.

## 5. Lojas, venda e mercado

### 5.1 `/loja`

- Catálogo administrativo em GUI.
- Categorias previstas: comidas, blocos de construção, pedras/tijolos, madeiras, vidros, redstone, ferramentas e materiais.
- Compras pagas exclusivamente com saldo virtual; ferramentas usam Gemas e as demais categorias usam Coins.
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

### 5.3 Lojas de jogadores (sistema próprio XKL PACK)

- O sistema será um módulo próprio do XKL PACK, inspirado no fluxo do Shopkeepers; não dependerá do ShopkeepersAddon.
- Para abrir uma loja, o jogador compra um Alvará Comercial na loja administrativa do servidor. O preço/moeda do alvará ainda precisa ser definido; não presumir valor.
- O jogador usa o alvará no ponto exato em que deseja estabelecer a loja. O XKL PACK valida terreno, espaço, sobreposição e permissões antes de consumir o documento.
- Após a validação, inicia uma preparação de 2 minutos; ao final, o comerciante NPC chega/aparece no ponto da loja. O processo precisa persistir após reinicializações e não duplicar NPCs.
- Cada NPC recebe automaticamente um nome próprio de pessoa, único no servidor, exibido acima da cabeça. O nome é permanente e o dono não pode alterá-lo.
- O NPC circula livremente dentro da área delimitada da loja, sem sair dela, atravessar limites ou perder a interação de comércio.
- A loja possui um baú especial chamado ESTOQUE, vinculado à loja. O dono e os aliados autorizados podem abrir/gerenciar o baú; os produtos desse estoque abastecem as ofertas do NPC.
- A cada venda, os itens são removidos do estoque e o comprador paga exclusivamente com moedas físicas oficiais autenticadas pelo XKL PACK (PDC + assinatura HMAC). Nome/lore/material isolados nunca autenticam dinheiro.
- O pagamento recebido fica guardado como dinheiro físico da loja e pode ser retirado pelo dono/aliados com permissão. Não aceitar Coins virtuais nem Gemas nas transações entre jogadores.
- O painel de cada loja seguirá o padrão visual e de navegação do painel administrativo do XKL PACK, com seções de estoque, produtos/preços, aliados, dinheiro, aparência e configurações. A aparência permite configurar a loja, mas não alterar o nome próprio permanente do NPC.
- O proprietário poderá convidar/remover aliados e definir permissões específicas (abrir estoque, gerenciar produtos/preços e retirar dinheiro). O dono mantém controle total.
- A integração com proteção de terrenos deve autorizar somente as ações necessárias do NPC e do estoque, sem desproteger o terreno inteiro. O NPC não pode ser usado como brecha para quebrar, abrir ou modificar blocos protegidos.
- A integração inicial deve considerar Citizens e EasyClaim, presentes no servidor do usuário, mas as APIs/versões precisam ser verificadas antes de implementar o bypass de proteção e a navegação delimitada. Se uma API não estiver disponível, o módulo deve falhar de forma segura e informar no log, sem desativar a proteção.
- Mercado/leilão global de jogadores não está incluído nesta funcionalidade.

## 6. Banco, transferências e rankings

### 6.1 `/atm`

Interface de caixa eletrônico com GUI para:
- Depositar moedas físicas autenticadas e creditar Coins virtuais.
- Sacar Coins virtuais em moedas físicas, com denominações automáticas.
- Consultar extrato e taxas.
- Acesso de jogadores somente por NPC Citizens vinculado; `/atm` direto fica reservado à administração.
- A bolsa física é um item de couro separado, entregue no primeiro saque e aberta com botão direito; não é a carteira virtual.

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

Comandos principais para jogadores:
- `/carteira` — carteira virtual de Coins/Gemas e histórico.
- `/loja`
- `/sell`
- `/job`
- `/top` e `/top online`
- `/pay`
- `/atm`

Não criar comandos principais extras para jogadores sem autorização. Comandos administrativos usam permissões separadas; `/xkladmin` abre o painel de configuração e é restrito à administração.

## 8. Pendências antes de fechar o comportamento

1. Definir se os limites de ferramentas usam nível geral do jogador ou nível da profissão ativa.
2. Confirmar os valores finais de espaços para possuir ferramentas e quantidade equipada simultaneamente; os valores atuais são configuráveis e iniciais.
3. Definir o destino contábil das taxas bancárias.
4. Definir acesso e regras do mercado de jogadores, caso ainda seja desejado.
5. Concluir testes integrados em servidor Paper 26.3 com Citizens, EasyClaim e os inventários do servidor.
