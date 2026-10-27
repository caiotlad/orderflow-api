package dev.caioladeira.orderflow.inventory;

import dev.caioladeira.orderflow.shared.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StockBalanceTest {
    @Test
    void reserveKeepsPhysicalStockAndReducesAvailability() {
        var result = new StockBalance(10, 2).reserve(3);
        assertThat(result).isEqualTo(new StockBalance(10, 5));
        assertThat(result.available()).isEqualTo(5);
    }

    @Test
    void cannotReserveMoreThanAvailable() {
        assertThatThrownBy(() -> new StockBalance(10, 8).reserve(3))
                .isInstanceOf(BusinessException.class).hasMessageContaining("insuficiente");
    }

    @Test
    void cancellationReleasesTheReservation() {
        assertThat(new StockBalance(10, 4).release(4)).isEqualTo(new StockBalance(10, 0));
    }

    @Test
    void confirmationConsumesPhysicalAndReservedStock() {
        assertThat(new StockBalance(10, 4).sell(4)).isEqualTo(new StockBalance(6, 0));
    }

    @Test
    void manualWithdrawalCannotConsumeReservedUnits() {
        assertThatThrownBy(() -> new StockBalance(10, 9).withdraw(2)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsInvalidBalancesAndQuantities() {
        assertThatThrownBy(() -> new StockBalance(1, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockBalance(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockBalance(1, 0).reserve(0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new StockBalance(1, 0).receive(-1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new StockBalance(1, 0).release(1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new StockBalance(1, 0).sell(1)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsOverflowWithoutWrappingTheBalance() {
        assertThatThrownBy(() -> new StockBalance(Long.MAX_VALUE, 0).receive(1))
                .isInstanceOf(BusinessException.class).hasMessageContaining("limite");
    }

    @Test
    void receiptAndWithdrawalPreserveExistingReservations() {
        assertThat(new StockBalance(10, 3).receive(5)).isEqualTo(new StockBalance(15, 3));
        assertThat(new StockBalance(10, 3).withdraw(5)).isEqualTo(new StockBalance(5, 3));
    }
}
