# Roteiro de validação

Escrevi os testes para conferir regras observáveis e garantias de persistência, com PostgreSQL real.

| Cenário | Resultado esperado |
| --- | --- |
| Token ausente ou inválido | HTTP 401 |
| Login incorreto | HTTP 401 sem revelar se o usuário existe |
| Operador alterando produto, estoque ou usuários | HTTP 403 |
| SKU repetido com outras maiúsculas | HTTP 409 |
| Corpo inválido, campo desconhecido ou chave malformada | HTTP 400 |
| Página ou tamanho inválidos | HTTP 400 |
| Um item indisponível em pedido com vários produtos | Nenhum pedido ou reserva parcial |
| Falha ao gravar movimento depois de inserir o pedido | Rollback do pedido, itens, saldo e idempotência |
| Duas compras pela última unidade | Um pedido e um conflito |
| Reenvios simultâneos com a mesma chave | Um pedido e uma reserva |
| Mesma chave com outra quantidade | Conflito sem nova movimentação |
| Confirmação ou cancelamento repetidos | Efeito aplicado uma vez |
| Confirmação concorrente com cancelamento | Um estado final |
| Itens em ordens invertidas | Ambos os pedidos terminam sem bloqueio circular |
| Produto atualizado depois da reserva | Nome e preço originais preservados |
| Preço 0,10 e quantidade três | Total 0,30 |
| Produto inativo ou saída consumindo reserva | Operação recusada |
| Health check e OpenAPI | Banco saudável e contrato disponível |

O banco em `TEST_DB_URL` precisa ser descartável. A suíte limpa tabelas operacionais entre os testes.

`python scripts/demo.py` usa um login real e verifica o fluxo por HTTP. Cria seus próprios registros e disputa a última unidade com duas solicitações paralelas. O GitHub Actions executa o roteiro contra a imagem construída pelo Dockerfile.

Para conferir manualmente, use `requests/orderflow.http` ou o Swagger e compare o saldo com o histórico. O reenvio da criação preserva a resposta original; a consulta do pedido mostra o estado atual.

Os testes concorrentes cobrem disputas específicas em um banco compartilhado. Não são um benchmark de produção. Não atribuí números de desempenho sem medição dedicada.
