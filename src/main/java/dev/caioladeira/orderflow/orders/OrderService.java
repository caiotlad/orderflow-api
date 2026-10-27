package dev.caioladeira.orderflow.orders;

import dev.caioladeira.orderflow.catalog.Product;
import dev.caioladeira.orderflow.catalog.ProductStore;
import dev.caioladeira.orderflow.inventory.MovementStore;
import dev.caioladeira.orderflow.inventory.MovementType;
import dev.caioladeira.orderflow.inventory.StockBalance;
import dev.caioladeira.orderflow.shared.BusinessException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderService {
    private final OrderStore orders;
    private final ProductStore products;
    private final MovementStore movements;
    private final ObjectMapper mapper;

    public OrderService(OrderStore orders, ProductStore products, MovementStore movements, ObjectMapper mapper) {
        this.orders = orders;
        this.products = products;
        this.movements = movements;
        this.mapper = mapper;
    }

    public record Creation(OrderView order, boolean replayed) {}
    private record Reservation(Product product, int quantity, StockBalance balance) {}

    @Transactional
    public Creation create(CreateOrder request, UUID key, UUID actor) {
        String hash = request.fingerprint();
        orders.lockRequest(actor, key);
        var previous = orders.findRequest(actor, key);
        if (previous.isPresent()) {
            if (!previous.get().requestHash().equals(hash)) {
                throw BusinessException.conflict("IDEMPOTENCY_CONFLICT", "Esta chave já foi usada com outro conteúdo.");
            }
            return new Creation(mapper.readValue(previous.get().responseJson(), OrderView.class), true);
        }

        List<Reservation> reservations = new ArrayList<>();
        List<OrderItem> items = new ArrayList<>();
        // Todos os fluxos bloqueiam produtos na mesma ordem para evitar espera circular.
        for (CreateOrder.Item item : request.sortedItems()) {
            Product product = products.lock(item.productId());
            if (!product.active()) {
                throw BusinessException.conflict("INACTIVE_PRODUCT", "O produto " + product.sku() + " está inativo.");
            }
            StockBalance balance = product.balance().reserve(item.quantity());
            reservations.add(new Reservation(product, item.quantity(), balance));
            items.add(new OrderItem(product.id(), product.sku(), product.name(), item.quantity(), product.price(),
                    product.price().multiply(BigDecimal.valueOf(item.quantity()))));
        }

        UUID orderId = orders.insert(request.customerName(), actor, items);
        for (Reservation reservation : reservations) {
            products.updateBalance(reservation.product().id(), reservation.balance());
            movements.insert(reservation.product().id(), orderId, MovementType.RESERVATION,
                    reservation.quantity(), 0, reservation.quantity(), "Reserva do pedido", actor);
        }
        OrderView order = orders.find(orderId);
        // A resposta original é persistida na mesma transação da reserva.
        orders.saveRequest(actor, key, hash, orderId, mapper.writeValueAsString(order));
        return new Creation(order, false);
    }

    @Transactional
    public OrderView confirm(UUID id, UUID actor) { return transition(id, OrderStatus.CONFIRMED, actor); }

    @Transactional
    public OrderView cancel(UUID id, UUID actor) { return transition(id, OrderStatus.CANCELED, actor); }

    private OrderView transition(UUID id, OrderStatus target, UUID actor) {
        OrderView order = orders.lock(id);
        if (order.status() == target) return order;
        if (order.status() != OrderStatus.RESERVED) {
            throw BusinessException.conflict("INVALID_ORDER_STATUS", "Apenas pedidos reservados podem ser confirmados ou cancelados.");
        }

        var sortedItems = order.items().stream()
                .sorted(Comparator.comparing(item -> item.productId().toString())).toList();
        for (OrderItem item : sortedItems) {
            Product product = products.lock(item.productId());
            boolean sale = target == OrderStatus.CONFIRMED;
            StockBalance balance = sale ? product.balance().sell(item.quantity()) : product.balance().release(item.quantity());
            products.updateBalance(product.id(), balance);
            movements.insert(product.id(), id, sale ? MovementType.SALE : MovementType.RELEASE,
                    item.quantity(), sale ? -item.quantity() : 0, -item.quantity(),
                    sale ? "Confirmação do pedido" : "Cancelamento do pedido", actor);
        }
        orders.changeStatus(id, target);
        return orders.find(id);
    }
}
