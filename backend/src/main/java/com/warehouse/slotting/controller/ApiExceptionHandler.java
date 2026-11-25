package com.warehouse.slotting.controller;
import com.warehouse.slotting.exception.UseCaseException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
@RestControllerAdvice
public class ApiExceptionHandler {
    public record BusinessError(OffsetDateTime timestamp, int status, String error, String path, String message) {}
    @ExceptionHandler(UseCaseException.class)
    public ResponseEntity<BusinessError> handleUseCaseError(UseCaseException error, HttpServletRequest request) {
        HttpStatus status = switch (error.kind()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(new BusinessError(OffsetDateTime.now(ZoneOffset.UTC), status.value(), status.getReasonPhrase(), request.getRequestURI(), error.getMessage()));
    }
}
