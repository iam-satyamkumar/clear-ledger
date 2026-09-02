package com.clearledger.clear.ledger.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException exception) {

        ApiError error = new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                exception.getMessage());

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiError> handleValidation(
        MethodArgumentNotValidException exception) {

    String message = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("Request validation failed");

    ApiError error = new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            message);

    return ResponseEntity.badRequest().body(error);
}

@ExceptionHandler(MissingRequestHeaderException.class)
public ResponseEntity<ApiError> handleMissingHeader(
        MissingRequestHeaderException exception) {

    ApiError error = new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            exception.getHeaderName() + " header is required");

    return ResponseEntity.badRequest().body(error);
}

}