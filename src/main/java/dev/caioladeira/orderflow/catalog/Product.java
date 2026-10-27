package dev.caioladeira.orderflow.catalog;

import dev.caioladeira.orderflow.inventory.StockBalance;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Product(UUID id, String sku, String name, BigDecimal price, boolean active,
        long onHand, long reserved, long available, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public StockBalance balance() { return new StockBalance(onHand, reserved); }
}
