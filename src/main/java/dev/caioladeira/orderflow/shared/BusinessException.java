package dev.caioladeira.orderflow.shared;

public class BusinessException extends RuntimeException {
    public enum Kind { INVALID, NOT_FOUND, CONFLICT }
    private final Kind kind;
    private final String code;

    public BusinessException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() { return kind; }
    public String code() { return code; }

    public static BusinessException missing(String resource) {
        return new BusinessException(Kind.NOT_FOUND, "RESOURCE_NOT_FOUND", resource + " não encontrado.");
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(Kind.CONFLICT, code, message);
    }

    public static BusinessException invalid(String code, String message) {
        return new BusinessException(Kind.INVALID, code, message);
    }
}
