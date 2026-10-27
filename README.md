# OrderFlow

[![CI](https://github.com/caiotlad/orderflow-api/actions/workflows/ci.yml/badge.svg)](https://github.com/caiotlad/orderflow-api/actions/workflows/ci.yml)

Desenvolvi o OrderFlow para trabalhar um problema de backend que me interessa: manter pedidos e estoque consistentes quando vários operadores usam o sistema ao mesmo tempo.

A API representa uma distribuidora com um depósito. Criar um pedido reserva os produtos; confirmar baixa o estoque físico; cancelar libera a reserva. Se qualquer item não puder ser atendido, o pedido inteiro é recusado.

## Funcionalidades

- Autenticação JWT e perfis de administrador e operador.
- Cadastro, consulta, atualização e inativação de produtos.
- Entradas e saídas manuais, com motivo e identificação do usuário.
- Pedidos com vários itens e preços preservados na criação.
- Reserva transacional, confirmação, cancelamento e idempotência.
- Filtros, paginação, histórico de movimentos e erros padronizados.
- OpenAPI, Docker Compose e testes com PostgreSQL real.

## Tecnologias e organização

Usei Java 21, Spring Boot 4, Spring Security, Spring JDBC, PostgreSQL 17 e Flyway. Os testes usam JUnit, AssertJ, MockMvc e Testcontainers. O Maven Wrapper fixa a versão do Maven.

| Pacote | Responsabilidade |
| --- | --- |
| `auth` | Usuários, login e permissões |
| `catalog` | Produtos e consultas |
| `inventory` | Saldos e movimentações |
| `orders` | Reserva, ciclo de vida e idempotência |
| `shared` | Paginação, erros e documentação |

Escolhi Spring JDBC para deixar consultas e bloqueios explícitos. Mantive um único backend, organizado por funcionalidade.

## Executar

É necessário ter Docker e Docker Compose funcionando. A primeira execução precisa de internet para baixar imagens e dependências.

Linux/macOS:

~~~bash
cp .env.example .env
docker compose up --build -d
~~~

PowerShell:

~~~powershell
Copy-Item .env.example .env
docker compose up --build -d
~~~

O exemplo contém credenciais locais. Substitua senhas e chave JWT antes de expor o serviço em outro ambiente. O Compose publica as portas somente em `127.0.0.1`.

| Endereço | Uso |
| --- | --- |
| `http://localhost:8080/actuator/health` | Saúde da aplicação e banco |
| `http://localhost:8080/swagger-ui.html` | Documentação interativa |
| `http://localhost:8080/v3/api-docs` | Contrato OpenAPI |

Use `docker compose logs -f app` para acompanhar a inicialização e `docker compose down` para parar mantendo o banco.

O primeiro administrador usa `ORDERFLOW_BOOTSTRAP_USERNAME` e `ORDERFLOW_BOOTSTRAP_PASSWORD`. Ele só é criado quando a tabela de usuários está vazia. Alterar essas variáveis depois não troca a senha de um usuário existente.

### Demonstração

Com a aplicação disponível:

~~~bash
python scripts/demo.py
~~~

O script pede a senha do administrador e usa apenas a biblioteca padrão do Python 3. Ele verifica reserva, reenvio, confirmação, cancelamento e duas compras simultâneas pela última unidade. O resultado esperado dessa disputa é HTTP 201 para uma solicitação e HTTP 409 para a outra.

O script cria seus próprios produtos e deixa os registros no banco para consulta. A reserva vencedora da disputa é cancelada no final.

Deixei também [requests/orderflow.http](requests/orderflow.http). No Swagger, faça login em `/api/v1/auth/token`, copie `accessToken` e informe o token em **Authorize**.

### Executar com Java

Com Java 21 ou superior e PostgreSQL disponível, configure as variáveis abaixo e execute `./mvnw spring-boot:run` ou, no Windows, `.\mvnw.cmd spring-boot:run`.

| Variável | Uso |
| --- | --- |
| `DB_URL` | JDBC; padrão `jdbc:postgresql://localhost:5432/orderflow` |
| `DB_USERNAME` | Usuário; padrão `orderflow` |
| `DB_PASSWORD` | Senha do banco |
| `ORDERFLOW_JWT_SECRET` | Chave com pelo menos 32 bytes UTF-8 |
| `ORDERFLOW_BOOTSTRAP_USERNAME` | Primeiro administrador |
| `ORDERFLOW_BOOTSTRAP_PASSWORD` | Mínimo 12 caracteres e máximo 72 bytes UTF-8 |
| `PORT` | Porta; padrão `8080` |

O Maven não carrega `.env` automaticamente. No Windows, [scripts/run-local.ps1](scripts/run-local.ps1) faz essa configuração. Primeiro execute `docker compose up -d db` e depois `.\scripts\run-local.ps1`.

## Endpoints

Todos os caminhos usam o prefixo `/api/v1`.

| Método e caminho | Permissão | Uso |
| --- | --- | --- |
| `POST /auth/token` | Público | Login |
| `GET /auth/me` | Autenticado | Próprio usuário |
| `POST /users` | Administrador | Criar usuário |
| `GET /users/{id}` | Administrador | Consultar usuário |
| `POST /products` | Administrador | Criar produto |
| `PUT /products/{id}` | Administrador | Alterar nome, preço e situação |
| `GET /products` | Autenticado | Filtros `search`, `active`, `page` e `size` |
| `GET /products/{id}` | Autenticado | Produto e saldos |
| `POST /products/{id}/stock-movements` | Administrador | Entrada ou saída |
| `GET /products/{id}/stock-movements` | Autenticado | Histórico |
| `POST /orders` | Autenticado | Criar e reservar |
| `GET /orders` | Autenticado | Filtros `status`, `page` e `size` |
| `GET /orders/{id}` | Autenticado | Pedido e itens |
| `POST /orders/{id}/confirm` | Autenticado | Confirmar |
| `POST /orders/{id}/cancel` | Autenticado | Cancelar |

Os perfis representam uma equipe interna e consultam todos os pedidos. Não há separação por empresa. A paginação começa em zero, tem tamanho padrão 20 e aceita até 100 registros por página. O SKU é único, convertido para maiúsculas e imutável.

## Estoque e pedidos

`onHand` é o estoque físico, `reserved` é o comprometido e `available` é `onHand - reserved`. O banco impede saldos negativos e reservas maiores que o estoque físico.

| Operação de `q` unidades | Físico | Reservado |
| --- | --- | --- |
| Entrada | Soma `q` | Mantém |
| Saída manual | Subtrai `q` disponíveis | Mantém |
| Reserva | Mantém | Soma `q` |
| Confirmação | Subtrai `q` | Subtrai `q` |
| Cancelamento | Mantém | Subtrai `q` |

Todo pedido começa em `RESERVED` e pode passar para `CONFIRMED` ou `CANCELED`. Esses dois estados são finais. Repetir a confirmação de um pedido confirmado, ou o cancelamento de um pedido cancelado, não movimenta o estoque novamente.

Uso `SELECT ... FOR UPDATE`, sempre na mesma ordem de identificador dos produtos. Pedido, itens, saldo, movimentos e idempotência ficam na mesma transação. O nome, SKU e preço são copiados para o pedido; alterações no catálogo não modificam seu histórico. Os valores usam `BigDecimal` e colunas decimais, em reais.

### Idempotência

`POST /orders` exige `Idempotency-Key` com UUID, vinculado ao usuário. A mesma chave e conteúdo devolvem HTTP 201, o corpo original e `Idempotency-Replayed: true`. Trocar cliente, produto ou quantidade mantendo a chave resulta em HTTP 409.

A ordem dos itens e espaços nas extremidades do nome do cliente não mudam o conteúdo considerado. Um advisory lock transacional no PostgreSQL protege reenvios simultâneos, inclusive entre instâncias da aplicação.

A resposta original é preservada depois de confirmar ou cancelar. Para ver o estado atual, use `GET /orders/{id}`. Solicitações que falham não consomem a chave. Os registros não expiram automaticamente.

## Testes

Com Java e Docker:

~~~bash
./mvnw verify
~~~

No Windows, use `.\mvnw.cmd verify`. A execução inclui testes unitários e de integração. Testcontainers sobe PostgreSQL quando `TEST_DB_URL` não está definido. Para usar outro banco, configure `TEST_DB_URL`, `TEST_DB_USERNAME` e `TEST_DB_PASSWORD`.

**Use um banco exclusivo para testes: a suíte limpa as tabelas de produtos e pedidos.**

Os testes cobrem concorrência pela última unidade, reenvios simultâneos, confirmação disputando com cancelamento, itens em ordens invertidas, preço histórico, falta de estoque e rollback após falha de persistência.

Os relatórios ficam em `target/surefire-reports` e `target/failsafe-reports`; a cobertura, em `target/site/jacoco/index.html`. O CI também constrói a imagem Docker e executa a demonstração HTTP contra ela.

## Limites e evolução

Mantive um depósito e uma moeda para concentrar o trabalho na consistência. A confirmação é a baixa definitiva; devoluções e estornos precisariam de outro fluxo. As reservas são liberadas por cancelamento, sem expiração automática. Os tokens duram uma hora e não há revogação individual.

Quero avaliar a expiração de reservas e a separação entre confirmação e expedição caso o domínio passe a exigir acompanhamento de entrega. Essas mudanças precisam preservar o histórico e as garantias do estoque.

Detalhei as decisões em [docs/architecture.md](docs/architecture.md) e a conferência em [docs/validation.md](docs/validation.md).
