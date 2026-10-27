package dev.caioladeira.orderflow;

import dev.caioladeira.orderflow.auth.*;
import dev.caioladeira.orderflow.catalog.*;
import dev.caioladeira.orderflow.inventory.*;
import dev.caioladeira.orderflow.orders.*;
import dev.caioladeira.orderflow.shared.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderFlowIT extends PostgresSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductService productService;
    @Autowired ProductStore products;
    @Autowired InventoryService inventory;
    @Autowired OrderService orders;
    @Autowired OrderStore orderStore;
    @Autowired UserStore users;
    @Autowired UserService userService;
    @Autowired AuthService auth;
    UUID admin;
    String token;

    @BeforeEach
    void resetInventory() {
        jdbc.execute("TRUNCATE idempotency_requests, stock_movements, order_items, orders, products");
        admin = users.findByUsername("admin").orElseThrow().id();
        token = auth.login("admin", "test-admin-password").accessToken();
    }

    @Test
    void loginIssuesARealTokenAndProtectsResources() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test-admin-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString()).andExpect(jsonPath("$.expiresIn").value(3600));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void operatorCanCreateOrdersButCannotManageProductsStockOrUsers() throws Exception {
        var operator = users.findByUsername("operator").map(UserStore.StoredUser::view)
                .orElseGet(() -> userService.create("operator", "operator-password", UserRole.OPERATOR));
        String operatorToken = auth.login("operator", "operator-password").accessToken();
        String authorization = "Bearer " + operatorToken;
        Product product = stocked(3);
        mvc.perform(post("/api/v1/products").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"NEW-01\",\"name\":\"Produto\",\"price\":10}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/products/" + product.id() + "/stock-movements").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"INBOUND\",\"quantity\":1,\"reason\":\"Reposição\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"other\",\"password\":\"other-password\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders").header("Authorization", authorization)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request(product, 1))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.createdBy").value(operator.id().toString()));
    }

    @Test
    void adminCreatesUsersWithNormalizedUniqueNames() throws Exception {
        String name = "User-" + UUID.randomUUID().toString().substring(0, 8);
        String body = mapper.writeValueAsString(new UserController.CreateUserRequest(name, "safe-test-password", UserRole.OPERATOR));
        mvc.perform(post("/api/v1/users").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value(name.toLowerCase()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/v1/users").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
        var stored = users.findByUsername(name).orElseThrow();
        assertThat(stored.passwordHash()).startsWith("$2").doesNotContain("safe-test-password");
    }

    @Test
    void validatesBodiesKeysAndPagination() throws Exception {
        mvc.perform(post("/api/v1/products").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"A\",\"name\":\" \",\"price\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/products").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"AB\",\"name\":\"Produto\",\"price\":10,\"onHand\":999}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"Cliente\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders").header("Authorization", bearer()).header("Idempotency-Key", "bad-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerName\":\"Cliente\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products?page=-1").header("Authorization", bearer())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products?size=101").header("Authorization", bearer())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products/" + UUID.randomUUID()).header("Authorization", bearer())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/orders").header("Authorization", bearer()).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerName\":\"Cliente\",\"items\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void productAndStockEndpointsSupportTheCompleteFlow() throws Exception {
        String created = mvc.perform(post("/api/v1/products").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"cafe-01\",\"name\":\"Café torrado\",\"price\":25.90}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sku").value("CAFE-01"))
                .andExpect(jsonPath("$.available").value(0)).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(created).path("id").asText());
        mvc.perform(post("/api/v1/products/" + id + "/stock-movements").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"INBOUND\",\"quantity\":10,\"reason\":\"Compra inicial\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.onHandDelta").value(10));
        mvc.perform(get("/api/v1/products?search=caf&active=true&size=1").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].available").value(10));
        mvc.perform(post("/api/v1/products").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"CAFE-01\",\"name\":\"Duplicado\",\"price\":25.90}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SKU_EXISTS"));
        mvc.perform(get("/api/v1/products/" + id + "/stock-movements").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void reserveConfirmAndRepeatConfirmationDoNotDoubleConsumeStock() throws Exception {
        Product product = stocked(10);
        UUID key = UUID.randomUUID();
        String response = mvc.perform(post("/api/v1/orders").header("Authorization", bearer()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request(product, 3))))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("RESERVED")).andExpect(jsonPath("$.total").value(38.70))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(response).path("id").asText());
        assertThat(products.find(product.id()).balance()).isEqualTo(new StockBalance(10, 3));
        mvc.perform(post("/api/v1/orders/" + id + "/confirm").header("Authorization", bearer())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/orders/" + id + "/confirm").header("Authorization", bearer())).andExpect(status().isOk());
        assertThat(products.find(product.id()).balance()).isEqualTo(new StockBalance(7, 0));
        assertThat(count("stock_movements")).isEqualTo(3);
        mvc.perform(post("/api/v1/orders/" + id + "/cancel").header("Authorization", bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
        mvc.perform(get("/api/v1/orders?status=CONFIRMED").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cancelReleasesStockOnlyOnceAndCannotBeConfirmedAfterward() {
        Product product = stocked(4);
        var order = orders.create(request(product, 3), UUID.randomUUID(), admin).order();
        orders.cancel(order.id(), admin);
        orders.cancel(order.id(), admin);
        assertThat(products.find(product.id()).balance()).isEqualTo(new StockBalance(4, 0));
        assertThat(count("stock_movements")).isEqualTo(3);
        assertThatThrownBy(() -> orders.confirm(order.id(), admin)).isInstanceOf(BusinessException.class);
    }

    @Test
    void retryReturnsTheOriginalResponseEvenAfterConfirmation() throws Exception {
        Product product = stocked(4);
        UUID key = UUID.randomUUID();
        CreateOrder request = request(product, 2);
        var original = orders.create(request, key, admin);
        orders.confirm(original.order().id(), admin);
        var replay = orders.create(request, key, admin);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.order()).isEqualTo(original.order());
        assertThat(orderStore.find(original.order().id()).status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(count("orders")).isEqualTo(1);
        assertThat(products.find(product.id()).onHand()).isEqualTo(2);
        mvc.perform(post("/api/v1/orders").header("Authorization", bearer()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(original.order().id().toString()))
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void reusingAKeyWithDifferentContentFailsWithoutChangingStock() {
        Product product = stocked(5);
        UUID key = UUID.randomUUID();
        orders.create(request(product, 1), key, admin);
        assertThatThrownBy(() -> orders.create(request(product, 2), key, admin))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThat(products.find(product.id()).reserved()).isEqualTo(1);
        assertThat(count("orders")).isEqualTo(1);
    }

    @Test
    void insufficientItemPreventsTheWholeOrderAndDoesNotConsumeTheKey() {
        Product first = stocked(5);
        Product empty = stocked(0);
        UUID key = UUID.randomUUID();
        CreateOrder request = new CreateOrder("Cliente", List.of(new CreateOrder.Item(first.id(), 2), new CreateOrder.Item(empty.id(), 1)));
        assertThatThrownBy(() -> orders.create(request, key, admin)).isInstanceOf(BusinessException.class);
        assertThat(products.find(first.id()).reserved()).isZero();
        assertThat(count("orders")).isZero();
        assertThat(count("idempotency_requests")).isZero();
        inventory.adjust(empty.id(), InventoryService.AdjustmentType.INBOUND, 1, "Reposição", admin);
        assertThat(orders.create(request, key, admin).replayed()).isFalse();
    }

    @Test
    void databaseFailureRollsBackOrderReservationAndIdempotencyTogether() {
        Product product = stocked(5);
        jdbc.execute("ALTER TABLE stock_movements ADD CONSTRAINT test_reject_reservations CHECK (type <> 'RESERVATION')");
        try {
            assertThatThrownBy(() -> orders.create(request(product, 2), UUID.randomUUID(), admin))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(products.find(product.id()).balance()).isEqualTo(new StockBalance(5, 0));
            assertThat(count("orders")).isZero();
            assertThat(count("order_items")).isZero();
            assertThat(count("idempotency_requests")).isZero();
            assertThat(count("stock_movements")).isEqualTo(1);
        } finally {
            jdbc.execute("ALTER TABLE stock_movements DROP CONSTRAINT test_reject_reservations");
        }
    }

    @Test
    void ordersPreserveProductNameAndPriceAndUseExactDecimalTotals() {
        Product product = productService.create("DECIMAL-01", "Produto original", new BigDecimal("0.10"));
        inventory.adjust(product.id(), InventoryService.AdjustmentType.INBOUND, 3, "Saldo inicial", admin);
        var order = orders.create(request(product, 3), UUID.randomUUID(), admin).order();
        productService.update(product.id(), "Nome alterado", new BigDecimal("999.99"), true);
        var loaded = orderStore.find(order.id());
        assertThat(loaded.total()).isEqualByComparingTo("0.30");
        assertThat(loaded.items().getFirst().productName()).isEqualTo("Produto original");
        assertThat(loaded.items().getFirst().unitPrice()).isEqualByComparingTo("0.10");
    }

    @Test
    void inactiveProductsAndWithdrawalsOfReservedStockAreRejected() {
        Product product = stocked(3);
        orders.create(request(product, 2), UUID.randomUUID(), admin);
        assertThatThrownBy(() -> inventory.adjust(product.id(), InventoryService.AdjustmentType.OUTBOUND, 2, "Ajuste", admin))
                .isInstanceOf(BusinessException.class);
        productService.update(product.id(), product.name(), product.price(), false);
        assertThatThrownBy(() -> orders.create(request(product, 1), UUID.randomUUID(), admin))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("INACTIVE_PRODUCT"));
    }

    @Test
    void onlyOneConcurrentPurchaseCanReserveTheLastUnit() throws Exception {
        Product product = stocked(1);
        var outcomes = concurrent(
                () -> tryReserve(request(product, 1), UUID.randomUUID()),
                () -> tryReserve(request(product, 1), UUID.randomUUID()));
        assertThat(outcomes).containsExactlyInAnyOrder("CREATED", "INSUFFICIENT_STOCK");
        assertThat(products.find(product.id()).balance()).isEqualTo(new StockBalance(1, 1));
        assertThat(count("orders")).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(2);
    }

    @Test
    void simultaneousRetriesCreateOneOrderAndOneReservation() throws Exception {
        Product product = stocked(3);
        UUID key = UUID.randomUUID();
        var outcomes = concurrent(
                () -> orders.create(request(product, 1), key, admin),
                () -> orders.create(request(product, 1), key, admin));
        assertThat(outcomes.get(0).order()).isEqualTo(outcomes.get(1).order());
        assertThat(outcomes).extracting(OrderService.Creation::replayed).containsExactlyInAnyOrder(false, true);
        assertThat(count("orders")).isEqualTo(1);
        assertThat(products.find(product.id()).reserved()).isEqualTo(1);
    }

    @Test
    void oppositeItemOrdersDoNotDeadlock() throws Exception {
        Product first = stocked(5);
        Product second = stocked(5);
        CreateOrder one = new CreateOrder("Primeiro", List.of(new CreateOrder.Item(first.id(), 1), new CreateOrder.Item(second.id(), 1)));
        CreateOrder two = new CreateOrder("Segundo", List.of(new CreateOrder.Item(second.id(), 1), new CreateOrder.Item(first.id(), 1)));
        var outcomes = concurrent(() -> orders.create(one, UUID.randomUUID(), admin), () -> orders.create(two, UUID.randomUUID(), admin));
        assertThat(outcomes).hasSize(2);
        assertThat(products.find(first.id()).reserved()).isEqualTo(2);
        assertThat(products.find(second.id()).reserved()).isEqualTo(2);
    }

    @Test
    void confirmationAndCancellationRaceHasOneTerminalOutcome() throws Exception {
        Product product = stocked(5);
        UUID id = orders.create(request(product, 2), UUID.randomUUID(), admin).order().id();
        var outcomes = concurrent(
                () -> tryTransition(id, true),
                () -> tryTransition(id, false));
        assertThat(outcomes).contains("INVALID_ORDER_STATUS");
        var finalOrder = orderStore.find(id);
        assertThat(finalOrder.status()).isIn(OrderStatus.CONFIRMED, OrderStatus.CANCELED);
        assertThat(products.find(product.id()).balance()).isEqualTo(
                finalOrder.status() == OrderStatus.CONFIRMED ? new StockBalance(3, 0) : new StockBalance(5, 0));
        assertThat(count("stock_movements")).isEqualTo(3);
    }

    @Test
    void openApiAndDatabaseHealthAreAvailable() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/orders']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    private String tryTransition(UUID id, boolean confirm) {
        try {
            return (confirm ? orders.confirm(id, admin) : orders.cancel(id, admin)).status().name();
        } catch (BusinessException exception) {
            return exception.code();
        }
    }

    private String tryReserve(CreateOrder request, UUID key) {
        try {
            orders.create(request, key, admin);
            return "CREATED";
        } catch (BusinessException exception) {
            return exception.code();
        }
    }

    private <T> List<T> concurrent(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<T> one = executor.submit(() -> { ready.countDown(); start.await(5, TimeUnit.SECONDS); return first.call(); });
            Future<T> two = executor.submit(() -> { ready.countDown(); start.await(5, TimeUnit.SECONDS); return second.call(); });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        }
    }

    private Product stocked(int quantity) {
        Product product = productService.create("SKU-" + UUID.randomUUID(), "Produto de teste", new BigDecimal("12.90"));
        if (quantity > 0) inventory.adjust(product.id(), InventoryService.AdjustmentType.INBOUND, quantity, "Saldo inicial", admin);
        return product;
    }

    private CreateOrder request(Product product, int quantity) {
        return new CreateOrder("Cliente de teste", List.of(new CreateOrder.Item(product.id(), quantity)));
    }

    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private String bearer() { return "Bearer " + token; }
}
