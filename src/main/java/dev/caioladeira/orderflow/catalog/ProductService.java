package dev.caioladeira.orderflow.catalog;

import dev.caioladeira.orderflow.shared.BusinessException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductStore products;
    public ProductService(ProductStore products) { this.products = products; }

    @Transactional
    public Product create(String sku, String name, BigDecimal price) {
        try {
            return products.insert(sku, name, price);
        } catch (DuplicateKeyException exception) {
            throw BusinessException.conflict("SKU_EXISTS", "Este SKU já está cadastrado.");
        }
    }

    @Transactional
    public Product update(UUID id, String name, BigDecimal price, boolean active) {
        products.lock(id);
        return products.update(id, name, price, active);
    }
}
