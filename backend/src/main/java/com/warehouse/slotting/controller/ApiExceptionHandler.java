package com.warehouse.slotting.controller;

import com.warehouse.slotting.exception.UseCaseException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(UseCaseException.class)
    public void handleUseCaseError(UseCaseException error, HttpServletResponse response) throws IOException {
        HttpStatus status = switch (error.kind()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        response.sendError(status.value(), error.getMessage());
    }
}
