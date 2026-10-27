package dev.caioladeira.orderflow.catalog;

import dev.caioladeira.orderflow.shared.PageResponse;
import dev.caioladeira.orderflow.shared.PageSpec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {
    private final ProductService service;
    private final ProductStore products;

    public ProductController(ProductService service, ProductStore products) {
        this.service = service;
        this.products = products;
    }

    public record CreateProductRequest(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{2,40}") String sku,
            @NotBlank @Size(min = 3, max = 120) String name,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price) {}

    public record UpdateProductRequest(
            @NotBlank @Size(min = 3, max = 120) String name,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull Boolean active) {}

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cadastrar produto com saldo inicial zero")
    public ResponseEntity<Product> create(@Valid @RequestBody CreateProductRequest request) {
        Product product = service.create(request.sku(), request.name(), request.price());
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.id())).body(product);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualizar nome, preço e situação do produto")
    public Product update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return service.update(id, request.name(), request.price(), request.active());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar produto e seus saldos")
    public Product find(@PathVariable UUID id) { return products.find(id); }

    @GetMapping
    @Operation(summary = "Listar produtos por nome ou SKU")
    public PageResponse<Product> list(@RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return products.list(search, active, new PageSpec(page, size));
    }
}
