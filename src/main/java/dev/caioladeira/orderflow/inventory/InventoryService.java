package dev.caioladeira.orderflow.inventory;

import dev.caioladeira.orderflow.catalog.ProductStore;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    public enum AdjustmentType { INBOUND, OUTBOUND }
    private final ProductStore products;
    private final MovementStore movements;

    public InventoryService(ProductStore products, MovementStore movements) {
        this.products = products;
        this.movements = movements;
    }

    @Transactional
    public StockMovement adjust(UUID productId, AdjustmentType type, int quantity, String reason, UUID actor) {
        var product = products.lock(productId);
        var balance = product.balance();
        var updated = switch (type) {
            case INBOUND -> balance.receive(quantity);
            case OUTBOUND -> balance.withdraw(quantity);
        };
        products.updateBalance(productId, updated);
        return movements.insert(productId, null, MovementType.valueOf(type.name()), quantity,
                updated.onHand() - balance.onHand(), 0, reason, actor);
    }
}
