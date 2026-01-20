package com.account.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -----------------------------
    // 404: Resource Not Found
    // -----------------------------
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ResourceNotFoundException ex) {
        Map<String, Object> body = baseBody(HttpStatus.NOT_FOUND, "Resource Not Found", ex.getMessage(), ex.getErrorCode());

        if (ex.getResourceName() != null) body.put("resource", ex.getResourceName());
        if (ex.getResourceId() != null) body.put("resourceId", ex.getResourceId());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // -----------------------------
    // 400: Your custom ValidationException
    // -----------------------------
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> handleValidationException(ValidationException ex) {
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "Validation Error", ex.getMessage(), ex.getErrorCode());
        if (ex.getField() != null) body.put("field", ex.getField());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // -----------------------------
    // 400: @Valid body validation errors
    // -----------------------------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "Validation Error", "Request validation failed", "VALIDATION_FAILED");

        List<Map<String, Object>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::mapFieldError)
                .collect(Collectors.toList());

        body.put("errors", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private Map<String, Object> mapFieldError(FieldError fe) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("field", fe.getField());
        err.put("rejectedValue", fe.getRejectedValue());
        err.put("message", fe.getDefaultMessage());
        return err;
    }

    // -----------------------------
    // 400: query param/path param validation (@Validated)
    // -----------------------------
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "Validation Error", "Constraint violation", "CONSTRAINT_VIOLATION");

        List<Map<String, Object>> errors = ex.getConstraintViolations().stream()
                .map(v -> {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("path", v.getPropertyPath().toString());
                    err.put("message", v.getMessage());
                    err.put("invalidValue", v.getInvalidValue());
                    return err;
                })
                .collect(Collectors.toList());

        body.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // -----------------------------
    // 400: IllegalArgumentException (bad client input)
    // -----------------------------
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), "INVALID_ARGUMENT");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // -----------------------------
    // 409: IllegalStateException (state conflict: approve again, etc.)
    // -----------------------------
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalState(IllegalStateException ex) {
        Map<String, Object> body = baseBody(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), "INVALID_STATE");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // -----------------------------
    // 500: fallback
    // -----------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);

        Map<String, Object> body = baseBody(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred",
                "ERR_INTERNAL"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // -----------------------------
    // Helper
    // -----------------------------
    private Map<String, Object> baseBody(HttpStatus status, String error, String message, String errorCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("errorCode", errorCode);
        return body;
    }

    // Add this method to GlobalExceptionHandler

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex) {
        Map<String, Object> body = baseBody(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                ex.getMessage(),
                ex.getErrorCode() != null ? ex.getErrorCode() : "ACCESS_DENIED"
        );

        // Optional rich details
        if (ex.getResourceType() != null) {
            body.put("resourceType", ex.getResourceType());
        }
        if (ex.getResourceId() != null) {
            body.put("resourceId", ex.getResourceId());
        }
        if (ex.getRequiredPermission() != null) {
            body.put("requiredPermission", ex.getRequiredPermission());
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }
}
