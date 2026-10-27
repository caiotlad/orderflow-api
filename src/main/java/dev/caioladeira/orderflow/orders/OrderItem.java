package dev.caioladeira.orderflow.orders;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItem(UUID productId, String sku, String productName, int quantity,
        BigDecimal unitPrice, BigDecimal subtotal) {}
