# OrderFlow

Estou desenvolvendo uma API de pedidos e reserva de estoque para uma distribuidora. Escolhi esse problema porque o estoque precisa continuar consistente mesmo quando vários operadores trabalham ao mesmo tempo.

A primeira versão terá autenticação, perfis de administrador e operador, catálogo de produtos, movimentações de estoque e pedidos com vários itens.

## Planejamento da solução

Vou usar Java 21, Spring Boot, PostgreSQL e Flyway, organizando o código em módulos de autenticação, catálogo, estoque e pedidos. As consultas serão feitas com Spring JDBC para deixar explícitos os bloqueios e as transações.

A criação de um pedido deverá reservar todos os itens em uma única transação. Se faltar estoque de qualquer produto, nenhuma reserva poderá permanecer. A confirmação baixará o estoque físico e o cancelamento liberará a reserva.

Também vou exigir uma chave de idempotência na criação dos pedidos. Isso permitirá repetir uma solicitação após uma falha de comunicação sem criar outro pedido.

## Etapas

1. Preparar o projeto, o banco e as migrações.
2. Implementar autenticação e permissões.
3. Implementar catálogo e movimentações.
4. Implementar reserva, confirmação, cancelamento e idempotência.
5. Testar os fluxos com PostgreSQL real, incluindo operações simultâneas.
6. Documentar a API e preparar execução com Docker e integração contínua.

## Critério de conclusão

A demonstração principal será uma disputa pela última unidade: duas solicitações simultâneas e apenas uma reserva aprovada. Vou testar também o reenvio de pedidos, o rollback de pedidos com vários produtos e a devolução das reservas no cancelamento.
