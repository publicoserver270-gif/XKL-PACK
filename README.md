# XKL PACK

Plugin modular de economia para Minecraft, distribuído em **um único arquivo JAR**. Os sistemas ficam organizados em módulos internos e podem ser ativados/configurados sem instalar vários plugins separados.

> **Status:** desenvolvimento. Economia virtual, transferências, ranking, profissões, salários, ATM, moedas físicas autenticadas por HMAC, saco físico persistente, `/loja`, `/sell` e Picareta Escavadora 3x3 já possuem código. Kits profissionais evolutivos e painel administrativo já possuem implementação inicial. O limite de ferramentas por nível ainda depende da definição final das regras; os módulos ainda precisam de testes integrados antes de produção.

## O que já está no código

- Persistência SQLite para perfis, saldos, histórico de transações, progresso profissional e conteúdo dos sacos físicos.
- ATM com depósito/saque, extrato, taxas e acesso de jogadores por NPC Citizens vinculado; comando direto reservado à administração.
- Moedas físicas com assinatura HMAC e saco de couro que guarda apenas moedas autenticadas.
- Profissões com progresso de XP, metas salariais independentes por profissão, recompensas de gemas e kits automáticos por nível.
- Kits profissionais vinculados ao jogador, com evolução automática, durabilidade proporcional e bloqueio de transferência.
- Carteira virtual `/carteira` separada do saco físico e do ATM; painel administrativo `/xkladmin` para editar salários, metas, taxas bancárias, valores das moedas físicas, preços de compra/venda e materiais dos kits.
- Moedas independentes configuráveis (coins e gems por padrão).
- Transferência bancária virtual pelo comando `/pay` (destinatário precisa estar online nesta primeira versão).
- Ranking geral e de jogadores online por moeda: `/top` e `/top online`.
- Regras de emissão separadas no serviço: coins pelo fluxo de salário e pelo fluxo de vendas; gemas pelo fluxo de atividade profissional.
- Débito de gemas reservado ao fluxo de compra de ferramentas.
- Compilação automatizada com GitHub Actions (Java 25 + Paper API 26.3).

## Catálogos das lojas

- `/sell`: 585 materiais configurados, sem Totem da Imortalidade.
- `/loja`: 135 produtos, incluindo 86 itens de construção importados do arquivo enviado, além de minérios, ingredientes, drops, redstone, variados e ferramentas.
- Ferramentas são compradas com Gemas; as demais categorias usam Coins.
- As categorias de armas e armaduras estão criadas, mas ainda aguardam os preços aprovados. O autocrafter também aguarda preço.

## Regras econômicas fundamentais

- **Coins:** fontes autorizadas de emissão são salários por metas de produção profissional e vendas confirmadas na loja `/sell`.
- **Salário:** só é pago a quem está empregado e cumpre a meta de produtividade da profissão.
- **Gemas:** única fonte de emissão são as atividades das profissões.
- **Uso das gemas:** exclusivamente para comprar ferramentas.
- Moedas diferentes são independentes; não existe conversão automática.
- Lojas administrativas usam saldo virtual; lojas de jogadores usam dinheiro físico.

## Comandos implementados no código atual

- `/loja` — loja administrativa com categorias configuráveis.
- `/sell` — loja de vendas com prévia, confirmação e catálogo de 585 materiais.
- `/carteira` — saldo virtual de Coins/Gemas e histórico de transações; não é o saco físico nem o ATM.
- `/job` — seleção e status das profissões.
- `/pay <jogador> <valor> [coins|gems]`.
- `/top [coins|gems]` e `/top online [coins|gems]`.
- `/atm` — acesso direto reservado à administração; jogadores usam NPC Citizens vinculado.
- `/xkladmin` — painel administrativo para editar salários, taxas, valores das moedas físicas, preços das lojas e materiais dos kits.
- `/xklatm npc link|unlink|list <id>` e `/xklpouch give <jogador>` — administração.

## Documentação

- [Especificação funcional](docs/ESPECIFICACAO.md)
- [Economia — implementação inicial](docs/ECONOMIA.md)

## Compilação

Requisitos: JDK 25 e Maven 3.9+.

```bash
mvn clean package
```

O JAR será gerado em `target/`. A validação real depende da execução bem-sucedida do workflow de CI.

## Desenvolvimento

O projeto será implementado em etapas, com testes e validação antes de substituir qualquer plugin/economia já usado em um servidor.
