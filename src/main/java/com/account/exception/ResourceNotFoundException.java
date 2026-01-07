package com.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a requested resource is not found.
 * Returns HTTP 404 Not Found by default.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String errorCode;
    private final String resourceName;  // Optional: e.g., "Company", "Estimate"
    private final Object resourceId;    // Optional: ID that was not found

    public ResourceNotFoundException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.resourceName = null;
        this.resourceId = null;
    }

    public ResourceNotFoundException(String message, String errorCode, String resourceName, Object resourceId) {
        super(message);
        this.errorCode = errorCode;
        this.resourceName = resourceName;
        this.resourceId = resourceId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getResourceName() {
        return resourceName;
    }

    public Object getResourceId() {
        return resourceId;
    }
}