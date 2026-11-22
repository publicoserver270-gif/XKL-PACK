# Análise de riscos técnicos — XKL PACK

Este documento acompanha a implementação. Uma compilação bem-sucedida comprova apenas que o código compila; não comprova que todos os fluxos estejam seguros dentro de um servidor Minecraft real.

## Riscos identificados e tratamento

| Risco | Impacto | Tratamento atual / próximo passo |
|---|---|---|
| Saldo negativo ou transferência concorrente | Perda ou criação indevida de saldo | Transferências usam transação SQL e validam saldo antes do débito. Testar concorrência com jogadores reais e carga automatizada. |
| Precisão decimal inconsistente | Diferenças de saldo e arredondamento | BigDecimal e validação de casas decimais por moeda; não usar double/float para dinheiro. |
| Identificador de moeda com caixa diferente | Consulta ou gravação no saldo errado | IDs normalizados para minúsculas antes das operações; identificadores inválidos são rejeitados. |
| Emissão indevida de coins/gemas | Inflação da economia | Regras documentadas: coins apenas por salário; gemas apenas por atividade profissional. Ainda falta restringir a emissão por um serviço de profissões autorizado e testado. |
| Banco de dados bloqueando a thread principal | Travamentos durante picos | A implementação inicial usa SQLite síncrono e uma conexão compartilhada protegida por lock. Antes de produção, migrar operações de I/O para execução assíncrona com fila/serialização de transações e retorno seguro à thread principal. |
| Perda/duplicação de itens em depósito, saque ou venda | Exploit econômico | ATM, dinheiro físico, `/loja` e `/sell` possuem fluxos básicos. Ainda faltam testes de falha/crash e um journal de recuperação entre inventário Bukkit e SQLite. |
| Kits transferidos por inventários alternativos | Perda de item exclusivo e exploits | Kits já possuem PDC de vínculo, bloqueio de drop/transferência, proteção contra baús, shift-click, drag, funis e recuperação na morte. Ainda validar shulkers, inventários de plugins e EasyClaim no servidor real. |
| Compatibilidade com servidor Paper 26.3 | Falha de inicialização ou eventos incompatíveis | O projeto agora compila com Java 25 e Paper API 26.3; ainda precisa concluir CI e teste real no servidor-alvo. Não instalar no servidor principal. |
| Migração de economia antiga | Saldos incorretos ou duplicados | Não migrar automaticamente nem desligar Vault/Mineconomy. Criar importação com backup, relatório, validação e modo somente leitura antes da troca. |
| Ranking incompleto | Jogadores offline ausentes | Atualmente o perfil só é criado quando ocorre uma operação econômica. Implementar sincronização de nomes/perfis no login e importação antes de tratar o ranking como definitivo. |

## Decisões seguras adotadas

- Manter SQLite como armazenamento inicial e transações SQL para operações econômicas.
- Usar BigDecimal, nunca ponto flutuante binário, para quantias.
- Rejeitar valores não positivos e valores com precisão acima da moeda.
- Não somar moedas diferentes no mesmo ranking.
- Não emitir coins/gemas por comandos comuns; coins são emitidas por salários e vendas confirmadas, gemas somente por atividades profissionais.
- ATM e profissões possuem implementação inicial; não tratar como produção até testes de integração. `/loja` e `/sell` já possuem implementação inicial e precisam de testes integrados antes de produção; a Picareta Escavadora 3x3 precisa ser validada com EasyClaim.
- Manter o plugin em paralelo com os plugins atuais até a migração ser aprovada.

## Critérios mínimos antes de produção

1. Testes unitários de transferência, precisão, moedas desconhecidas, saldo insuficiente, rollback e limites dos multiplicadores salariais.
2. Testes de concorrência e recuperação após encerramento inesperado.
3. Testes de exploit de inventário para dinheiro físico e kits.
4. Teste em servidor descartável com a mesma versão/API do servidor-alvo.
5. Backup restaurável e plano de migração dos saldos.
6. Verificação do JAR final e das dependências empacotadas.
