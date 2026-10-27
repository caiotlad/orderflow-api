package dev.caioladeira.orderflow.inventory;

import dev.caioladeira.orderflow.shared.BusinessException;

public record StockBalance(long onHand, long reserved) {
    public StockBalance {
        if (onHand < 0 || reserved < 0 || reserved > onHand) {
            throw new IllegalArgumentException("Saldo de estoque inválido.");
        }
    }

    public long available() { return onHand - reserved; }

    public StockBalance reserve(int quantity) {
        requireAvailable(quantity);
        return new StockBalance(onHand, reserved + quantity);
    }

    public StockBalance release(int quantity) {
        requireReserved(quantity);
        return new StockBalance(onHand, reserved - quantity);
    }

    public StockBalance sell(int quantity) {
        requireReserved(quantity);
        return new StockBalance(onHand - quantity, reserved - quantity);
    }

    public StockBalance receive(int quantity) {
        requirePositive(quantity);
        try {
            return new StockBalance(Math.addExact(onHand, quantity), reserved);
        } catch (ArithmeticException exception) {
            throw BusinessException.conflict("STOCK_LIMIT", "O saldo ultrapassa o limite suportado.");
        }
    }

    public StockBalance withdraw(int quantity) {
        requireAvailable(quantity);
        return new StockBalance(onHand - quantity, reserved);
    }

    private void requireAvailable(int quantity) {
        requirePositive(quantity);
        if (available() < quantity) {
            throw BusinessException.conflict("INSUFFICIENT_STOCK", "Estoque disponível insuficiente.");
        }
    }

    private void requireReserved(int quantity) {
        requirePositive(quantity);
        if (reserved < quantity) {
            throw BusinessException.conflict("INVALID_RESERVATION", "Quantidade superior ao estoque reservado.");
        }
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw BusinessException.invalid("INVALID_QUANTITY", "A quantidade deve ser positiva.");
        }
    }
}
