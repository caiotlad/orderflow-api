package dev.caioladeira.orderflow.inventory;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StockMovement(UUID id, UUID productId, UUID orderId, MovementType type, int quantity,
        long onHandDelta, long reservedDelta, String reason, UUID createdBy, OffsetDateTime createdAt) {}
