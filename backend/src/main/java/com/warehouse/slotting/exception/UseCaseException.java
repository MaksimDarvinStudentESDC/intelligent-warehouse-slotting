package com.warehouse.slotting.exception;

public class UseCaseException extends RuntimeException {
    public enum Kind {
        INVALID_INPUT, NOT_FOUND, CONFLICT
    }

    private final Kind kind;

    public UseCaseException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
