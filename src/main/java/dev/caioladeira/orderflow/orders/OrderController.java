package dev.caioladeira.orderflow.orders;

import dev.caioladeira.orderflow.shared.PageResponse;
import dev.caioladeira.orderflow.shared.PageSpec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    private final OrderService service;
    private final OrderStore orders;

    public OrderController(OrderService service, OrderStore orders) { this.service = service; this.orders = orders; }

    @PostMapping
    @Operation(summary = "Criar pedido e reservar todos os itens atomicamente",
            description = "Exige Idempotency-Key no formato UUID. Reenvios devolvem a resposta original.")
    public ResponseEntity<OrderView> create(@RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody CreateOrder request, @AuthenticationPrincipal Jwt jwt) {
        var result = service.create(request, key, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/v1/orders/" + result.order().id()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed())).body(result.order());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar pedido com itens e preços registrados na criação")
    public OrderView find(@PathVariable UUID id) { return orders.find(id); }

    @GetMapping
    @Operation(summary = "Listar pedidos com filtro por situação")
    public PageResponse<OrderStore.Summary> list(@RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return orders.list(status, new PageSpec(page, size));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirmar pedido reservado e baixar o estoque físico")
    public OrderView confirm(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.confirm(id, UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancelar pedido reservado e liberar o estoque")
    public OrderView cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(id, UUID.fromString(jwt.getSubject()));
    }
}
