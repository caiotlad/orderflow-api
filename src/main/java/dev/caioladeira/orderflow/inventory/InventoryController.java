package dev.caioladeira.orderflow.inventory;

import dev.caioladeira.orderflow.catalog.ProductStore;
import dev.caioladeira.orderflow.shared.PageResponse;
import dev.caioladeira.orderflow.shared.PageSpec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/{productId}/stock-movements")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {
    private final InventoryService inventory;
    private final MovementStore movements;
    private final ProductStore products;

    public InventoryController(InventoryService inventory, MovementStore movements, ProductStore products) {
        this.inventory = inventory;
        this.movements = movements;
        this.products = products;
    }

    public record AdjustmentRequest(
            @NotNull InventoryService.AdjustmentType type,
            @Min(1) @Max(1000000) int quantity,
            @NotBlank @Size(min = 3, max = 250) String reason) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar entrada ou saída manual de estoque")
    public StockMovement adjust(@PathVariable UUID productId, @Valid @RequestBody AdjustmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return inventory.adjust(productId, request.type(), request.quantity(), request.reason(), UUID.fromString(jwt.getSubject()));
    }

    @GetMapping
    @Operation(summary = "Consultar histórico de movimentações")
    public PageResponse<StockMovement> list(@PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        products.find(productId);
        return movements.list(productId, new PageSpec(page, size));
    }
}
