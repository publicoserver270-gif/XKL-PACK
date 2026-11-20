# XKL PACK

Plugin modular de economia para Minecraft, distribuído em **um único arquivo JAR**. Os sistemas ficam organizados em módulos internos e podem ser ativados/configurados sem instalar vários plugins separados.

> **Status:** início do desenvolvimento. Esta versão do repositório contém a especificação e a fundação do projeto; os sistemas de jogo ainda serão implementados e testados por etapas.

## Objetivos

- Economia com múltiplas moedas independentes.
- Dinheiro físico em itens e saldo virtual.
- Banco com depósitos, saques, taxas e extrato.
- Profissões com níveis, atividades, gemas e salários por produtividade.
- Kits de profissão com ferramentas que evoluem automaticamente.
- Loja administrativa, venda de itens e mercado de jogadores.
- Rankings e transferências bancárias.
- Configuração genérica para diferentes servidores, não apenas XKL Gamer.
- Compatibilidade Java e Bedrock sempre que a API do servidor permitir.

## Regras econômicas fundamentais

- **Coins:** única fonte de emissão é o salário.
- **Salário:** só é pago a quem está empregado e cumpre a meta de produtividade da profissão.
- **Gemas:** única fonte de emissão são as atividades das profissões.
- **Uso das gemas:** exclusivamente para comprar ferramentas.
- Moedas diferentes são independentes; não existe conversão automática.
- Lojas administrativas usam saldo virtual; lojas de jogadores usam dinheiro físico.

## Comandos principais planejados

- `/loja`
- `/sell`
- `/job`
- `/top` e `/top online`
- `/pay`
- `/atm`

Não serão adicionados comandos principais de jogador fora dessa lista sem decisão explícita.

## Documentação

Consulte [docs/ESPECIFICACAO.md](docs/ESPECIFICACAO.md) para as regras e decisões detalhadas.

## Compilação

Requisitos previstos para a fundação: JDK 21 e Maven 3.9+.

```bash
mvn clean package
```

O JAR será gerado em `target/`. Esta instrução só será considerada validada depois que o workflow de CI executar com sucesso.

## Desenvolvimento

O projeto será implementado em etapas, com testes e validação antes de substituir qualquer plugin/economia já usado em um servidor.
