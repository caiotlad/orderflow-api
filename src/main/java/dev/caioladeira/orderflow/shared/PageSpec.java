package dev.caioladeira.orderflow.shared;

public record PageSpec(int page, int size) {
    public PageSpec {
        if (page < 0 || page > 1000000 || size < 1 || size > 100) {
            throw BusinessException.invalid("INVALID_PAGE", "Página deve estar entre 0 e 1000000 e tamanho entre 1 e 100.");
        }
    }
    public long offset() { return (long) page * size; }
}
