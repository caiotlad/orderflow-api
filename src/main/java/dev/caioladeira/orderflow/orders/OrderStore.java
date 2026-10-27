package dev.caioladeira.orderflow.orders;

import dev.caioladeira.orderflow.shared.BusinessException;
import dev.caioladeira.orderflow.shared.PageResponse;
import dev.caioladeira.orderflow.shared.PageSpec;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderStore {
    private final JdbcTemplate jdbc;
    public OrderStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Summary(UUID id, String customerName, OrderStatus status, BigDecimal total,
            UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record Idempotency(String requestHash, String responseJson) {}

    public void lockRequest(UUID userId, UUID key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, userId + ":" + key);
    }

    public Optional<Idempotency> findRequest(UUID userId, UUID key) {
        return jdbc.query("SELECT request_hash, response_json FROM idempotency_requests WHERE user_id = ? AND request_key = ?",
                (rs, row) -> new Idempotency(rs.getString("request_hash"), rs.getString("response_json")), userId, key)
                .stream().findFirst();
    }

    public void saveRequest(UUID userId, UUID key, String hash, UUID orderId, String response) {
        jdbc.update("""
                INSERT INTO idempotency_requests (user_id, request_key, request_hash, order_id, response_json)
                VALUES (?, ?, ?, ?, ?)
                """, userId, key, hash, orderId, response);
    }

    public UUID insert(String customerName, UUID actor, List<OrderItem> items) {
        UUID id = UUID.randomUUID();
        BigDecimal total = items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        jdbc.update("INSERT INTO orders (id, customer_name, status, total, created_by) VALUES (?, ?, 'RESERVED', ?, ?)",
                id, customerName.strip(), total, actor);
        for (OrderItem item : items) {
            jdbc.update("""
                    INSERT INTO order_items (order_id, product_id, sku, product_name, quantity, unit_price)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, id, item.productId(), item.sku(), item.productName(), item.quantity(), item.unitPrice());
        }
        return id;
    }

    public OrderView find(UUID id) {
        Summary summary = header(id, false);
        return view(summary, items(id));
    }

    public OrderView lock(UUID id) {
        Summary summary = header(id, true);
        return view(summary, items(id));
    }

    public void changeStatus(UUID id, OrderStatus status) {
        jdbc.update("UPDATE orders SET status = ?, updated_at = clock_timestamp() WHERE id = ?", status.name(), id);
    }

    public PageResponse<Summary> list(OrderStatus status, PageSpec page) {
        String filter = status == null ? "" : " WHERE status = ?";
        Object[] countArgs = status == null ? new Object[]{} : new Object[]{status.name()};
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM orders" + filter, Long.class, countArgs);
        Object[] args = status == null ? new Object[]{page.size(), page.offset()}
                : new Object[]{status.name(), page.size(), page.offset()};
        var rows = jdbc.query("SELECT * FROM orders" + filter + " ORDER BY created_at DESC, id LIMIT ? OFFSET ?", this::map, args);
        return PageResponse.of(rows, page, count);
    }

    private Summary header(UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM orders WHERE id = ?" + (lock ? " FOR UPDATE" : ""), this::map, id)
                .stream().findFirst().orElseThrow(() -> BusinessException.missing("Pedido"));
    }

    private List<OrderItem> items(UUID id) {
        return jdbc.query("SELECT * FROM order_items WHERE order_id = ? ORDER BY product_id", (rs, row) -> {
            int quantity = rs.getInt("quantity");
            BigDecimal price = rs.getBigDecimal("unit_price");
            return new OrderItem(rs.getObject("product_id", UUID.class), rs.getString("sku"),
                    rs.getString("product_name"), quantity, price, price.multiply(BigDecimal.valueOf(quantity)));
        }, id);
    }

    private OrderView view(Summary header, List<OrderItem> items) {
        return new OrderView(header.id(), header.customerName(), header.status(), header.total(), header.createdBy(),
                header.createdAt(), header.updatedAt(), items);
    }

    private Summary map(ResultSet rs, int row) throws SQLException {
        return new Summary(rs.getObject("id", UUID.class), rs.getString("customer_name"),
                OrderStatus.valueOf(rs.getString("status")), rs.getBigDecimal("total"),
                rs.getObject("created_by", UUID.class), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
