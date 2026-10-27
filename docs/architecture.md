# Decisões de implementação

## Coordenação no PostgreSQL

Usei o banco como fonte de verdade do saldo. Um bloqueio em memória não protegeria duas instâncias atendendo requisições.

Os serviços abrem transações com `@Transactional`. O isolamento é `READ COMMITTED`, com bloqueio das linhas modificadas. A segunda transação espera e lê o saldo atualizado antes de reservar.

Todos os caminhos de alteração do saldo passam por `ProductStore.lock`. Atualizações de nome, preço e situação também obtêm esse bloqueio.

## Sequência dos bloqueios

Na criação, primeiro bloqueio a chave de idempotência e depois os produtos, ordenados pela representação textual do UUID.

Nas transições, primeiro bloqueio o pedido e depois os produtos na mesma ordem. Cada transição modifica apenas seu pedido. A criação não tenta bloquear outro pedido existente. Movimentações manuais envolvem um produto.

Isso evita que pedidos com itens em ordens diferentes adquiram bloqueios em sequência inversa. Uma espera maior que dez segundos resulta em conflito e permite nova tentativa.

## Idempotência

A chave definitiva é o par usuário/UUID. Um hash de 64 bits serve apenas para coordenar o advisory lock. Uma colisão nesse hash apenas serializaria operações adicionais; a consulta continua usando a chave completa.

O conteúdo normalizado recebe um fingerprint SHA-256. Depois do bloqueio, conteúdo igual devolve a resposta original; conteúdo diferente gera conflito. Pedido e resposta são persistidos na mesma transação. Uma falha desfaz tudo e permite tentar a chave novamente.

## Saldo e histórico

O saldo atual fica em `products`. Cada alteração gera um movimento com deltas físico e reservado, motivo, usuário e pedido. Atualização e movimento ficam na mesma transação.

As constraints protegem o saldo e os estados válidos. A validação HTTP verifica formatos e limites. Preço e total usam tipos decimais. SKU, nome e preço são copiados para os itens para preservar o histórico.

Não permito editar itens de pedidos existentes. Para trocar o conteúdo, é necessário cancelar e criar outro pedido com nova chave.

## Segurança

As senhas usam BCrypt, respeitando o limite de 72 bytes UTF-8. O login não distingue usuário inexistente de senha incorreta.

O JWT usa HS256, emissor definido e validade de uma hora. O Spring Security valida assinatura, emissor e tempo. Os perfis controlam as operações administrativas.

Não há cadastro público. O primeiro administrador é criado no bootstrap, que também usa bloqueio transacional para coordenar inicializações simultâneas.

A autenticação é stateless e não usa cookies de sessão. O Compose publica em localhost. Uma exposição externa precisará de HTTPS, credenciais próprias e controles de entrada do ambiente.

## Alternativas

Considerei controle otimista com versionamento. Preferi bloquear antes de validar e modificar o saldo porque a disputa por estoque é central neste domínio.

Mantive um backend. Separar catálogo, estoque e pedidos em processos independentes exigiria outro desenho de consistência, com um custo que não ajuda o recorte da primeira versão.
