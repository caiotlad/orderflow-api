package dev.caioladeira.orderflow.catalog;

import dev.caioladeira.orderflow.inventory.StockBalance;
import dev.caioladeira.orderflow.shared.BusinessException;
import dev.caioladeira.orderflow.shared.PageResponse;
import dev.caioladeira.orderflow.shared.PageSpec;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductStore {
    private final JdbcTemplate jdbc;
    public ProductStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Product find(UUID id) {
        return jdbc.query("SELECT * FROM products WHERE id = ?", this::map, id).stream()
                .findFirst().orElseThrow(() -> BusinessException.missing("Produto"));
    }

    public Product lock(UUID id) {
        return jdbc.query("SELECT * FROM products WHERE id = ? FOR UPDATE", this::map, id).stream()
                .findFirst().orElseThrow(() -> BusinessException.missing("Produto"));
    }

    public Product insert(String sku, String name, BigDecimal price) {
        return jdbc.queryForObject("""
                INSERT INTO products (id, sku, name, price) VALUES (?, ?, ?, ?) RETURNING *
                """, this::map, UUID.randomUUID(), sku.strip().toUpperCase(Locale.ROOT), name.strip(), price);
    }

    public Product update(UUID id, String name, BigDecimal price, boolean active) {
        return jdbc.queryForObject("""
                UPDATE products SET name = ?, price = ?, active = ?, updated_at = clock_timestamp()
                WHERE id = ? RETURNING *
                """, this::map, name.strip(), price, active, id);
    }

    public void updateBalance(UUID id, StockBalance balance) {
        jdbc.update("UPDATE products SET on_hand = ?, reserved = ?, updated_at = clock_timestamp() WHERE id = ?",
                balance.onHand(), balance.reserved(), id);
    }

    public PageResponse<Product> list(String search, Boolean active, PageSpec page) {
        String text = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        String filter = " WHERE (strpos(lower(name), ?) > 0 OR strpos(lower(sku), ?) > 0)";
        if (active != null) filter += " AND active = " + (active ? "TRUE" : "FALSE");
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM products" + filter, Long.class, text, text);
        var products = jdbc.query("SELECT * FROM products" + filter + " ORDER BY created_at DESC, id LIMIT ? OFFSET ?",
                this::map, text, text, page.size(), page.offset());
        return PageResponse.of(products, page, count);
    }

    private Product map(ResultSet rs, int row) throws SQLException {
        long onHand = rs.getLong("on_hand");
        long reserved = rs.getLong("reserved");
        return new Product(rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("name"),
                rs.getBigDecimal("price"), rs.getBoolean("active"), onHand, reserved, onHand - reserved,
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }
}
