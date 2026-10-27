package dev.caioladeira.orderflow.orders;

import dev.caioladeira.orderflow.shared.BusinessException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CreateOrderTest {
    private final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void fingerprintIgnoresItemOrderAndSurroundingCustomerWhitespace() {
        var one = new CreateOrder("Cliente", List.of(new CreateOrder.Item(first, 1), new CreateOrder.Item(second, 2)));
        var two = new CreateOrder(" Cliente ", List.of(new CreateOrder.Item(second, 2), new CreateOrder.Item(first, 1)));
        assertThat(one.fingerprint()).isEqualTo(two.fingerprint());
    }

    @Test
    void fingerprintChangesWhenTheCustomerOrQuantityChanges() {
        var original = new CreateOrder("Cliente", List.of(new CreateOrder.Item(first, 1)));
        assertThat(original.fingerprint()).isNotEqualTo(
                new CreateOrder("Outro cliente", List.of(new CreateOrder.Item(first, 1))).fingerprint());
        assertThat(original.fingerprint()).isNotEqualTo(
                new CreateOrder("Cliente", List.of(new CreateOrder.Item(first, 2))).fingerprint());
    }

    @Test
    void duplicateProductsAreRejected() {
        var request = new CreateOrder("Cliente", List.of(new CreateOrder.Item(first, 1), new CreateOrder.Item(first, 2)));
        assertThatThrownBy(request::fingerprint).isInstanceOf(BusinessException.class);
    }
}
