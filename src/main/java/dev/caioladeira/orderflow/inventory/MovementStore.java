package dev.caioladeira.orderflow.inventory;

import dev.caioladeira.orderflow.shared.PageResponse;
import dev.caioladeira.orderflow.shared.PageSpec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MovementStore {
    private final JdbcTemplate jdbc;
    public MovementStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public StockMovement insert(UUID productId, UUID orderId, MovementType type, int quantity,
            long onHandDelta, long reservedDelta, String reason, UUID actor) {
        return jdbc.queryForObject("""
                INSERT INTO stock_movements (id, product_id, order_id, type, quantity,
                    on_hand_delta, reserved_delta, reason, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING *
                """, this::map, UUID.randomUUID(), productId, orderId, type.name(), quantity,
                onHandDelta, reservedDelta, reason.strip(), actor);
    }

    public PageResponse<StockMovement> list(UUID productId, PageSpec page) {
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE product_id = ?", Long.class, productId);
        var rows = jdbc.query("""
                SELECT * FROM stock_movements WHERE product_id = ?
                ORDER BY created_at DESC, id LIMIT ? OFFSET ?
                """, this::map, productId, page.size(), page.offset());
        return PageResponse.of(rows, page, count);
    }

    private StockMovement map(ResultSet rs, int row) throws SQLException {
        return new StockMovement(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getObject("order_id", UUID.class), MovementType.valueOf(rs.getString("type")),
                rs.getInt("quantity"), rs.getLong("on_hand_delta"), rs.getLong("reserved_delta"),
                rs.getString("reason"), rs.getObject("created_by", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
