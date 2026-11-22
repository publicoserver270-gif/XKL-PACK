# XKL PACK

Plugin modular de economia para Minecraft, distribuído em **um único arquivo JAR**. Os sistemas ficam organizados em módulos internos e podem ser ativados/configurados sem instalar vários plugins separados.

> **Status:** desenvolvimento. Economia virtual, transferências, ranking, profissões, salários, ATM, moedas físicas autenticadas por HMAC e saco físico persistente já possuem código. Lojas `/loja` e `/sell`, kits evolutivos, limites de equipamentos e painel administrativo ainda não estão implementados; os módulos não devem ser considerados prontos para produção.

## O que já está no código

- Persistência SQLite para perfis, saldos, histórico de transações, progresso profissional e conteúdo dos sacos físicos.\n- ATM com depósito/saque, extrato, taxas e acesso de jogadores por NPC Citizens vinculado; comando direto reservado à administração.\n- Moedas físicas com assinatura HMAC e saco de couro que guarda apenas moedas autenticadas.\n- Profissões com progresso de XP, metas salariais independentes por profissão e recompensas de gemas.
- Moedas independentes configuráveis (coins e gems por padrão).
- Transferência bancária virtual pelo comando `/pay` (destinatário precisa estar online nesta primeira versão).
- Ranking geral e de jogadores online por moeda: `/top` e `/top online`.
- Regras de emissão separadas no serviço: coins pelo fluxo de salário e pelo fluxo de vendas; gemas pelo fluxo de atividade profissional.
- Débito de gemas reservado ao fluxo de compra de ferramentas.
- Compilação automatizada com GitHub Actions (Java 21 + Maven).

## Regras econômicas fundamentais

- **Coins:** fontes autorizadas de emissão são salários por metas de produção profissional e vendas confirmadas na loja `/sell`.
- **Salário:** só é pago a quem está empregado e cumpre a meta de produtividade da profissão.
- **Gemas:** única fonte de emissão são as atividades das profissões.
- **Uso das gemas:** exclusivamente para comprar ferramentas.
- Moedas diferentes são independentes; não existe conversão automática.
- Lojas administrativas usam saldo virtual; lojas de jogadores usam dinheiro físico.

## Comandos implementados nesta etapa

- `/pay <jogador> <valor> [coins|gems]`
- `/top [coins|gems]`
- `/top online [coins|gems]`

Os demais comandos planejados ainda não estão implementados. Não serão adicionados comandos principais de jogador fora da lista aprovada sem decisão explícita.

## Documentação

- [Especificação funcional](docs/ESPECIFICACAO.md)
- [Economia — implementação inicial](docs/ECONOMIA.md)

## Compilação

Requisitos: JDK 21 e Maven 3.9+.

```bash
mvn clean package
```

O JAR será gerado em `target/`. A validação real depende da execução bem-sucedida do workflow de CI.

## Desenvolvimento

O projeto será implementado em etapas, com testes e validação antes de substituir qualquer plugin/economia já usado em um servidor.
