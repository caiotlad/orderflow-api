package dev.caioladeira.orderflow.orders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderView(UUID id, String customerName, OrderStatus status, BigDecimal total,
        UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt, List<OrderItem> items) {
    public OrderView { items = List.copyOf(items); }
}
