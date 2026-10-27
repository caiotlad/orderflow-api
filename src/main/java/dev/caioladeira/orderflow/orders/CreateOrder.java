package dev.caioladeira.orderflow.orders;

import dev.caioladeira.orderflow.shared.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public record CreateOrder(
        @NotBlank @Size(min = 2, max = 120) String customerName,
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid Item> items) {
    public record Item(@NotNull UUID productId, @Min(1) @Max(10000) int quantity) {}

    public List<Item> sortedItems() {
        HashSet<UUID> seen = new HashSet<>();
        for (Item item : items) {
            if (!seen.add(item.productId())) {
                throw BusinessException.invalid("DUPLICATE_ITEM", "Informe cada produto uma única vez no pedido.");
            }
        }
        return items.stream().sorted(Comparator.comparing(item -> item.productId().toString())).toList();
    }

    public String fingerprint() {
        String customer = customerName.strip();
        StringBuilder value = new StringBuilder().append(customer.length()).append(':').append(customer);
        for (Item item : sortedItems()) {
            value.append('|').append(item.productId()).append(':').append(item.quantity());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível.", exception);
        }
    }
}
