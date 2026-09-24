package com.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when the current user does not have permission
 * to perform the requested operation (e.g., view/edit someone else's invoice).
 *
 * Returns HTTP 403 Forbidden by default.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccessDeniedException extends RuntimeException {

    private final String errorCode;
    private final String resourceType;      // Optional: e.g. "Invoice", "Company", "Estimate"
    private final Object resourceId;        // Optional: ID of the resource they tried to access
    private final String requiredPermission; // Optional: e.g. "VIEW_OWN_INVOICE", "APPROVE_COMPANY"

    // Basic constructor - most common usage
    public AccessDeniedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.resourceType = null;
        this.resourceId = null;
        this.requiredPermission = null;
    }

    // Detailed constructor - useful when you want to provide more context
    public AccessDeniedException(
            String message,
            String errorCode,
            String resourceType,
            Object resourceId,
            String requiredPermission
    ) {
        super(message);
        this.errorCode = errorCode;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.requiredPermission = requiredPermission;
    }

    // Most common convenience constructors

    public AccessDeniedException(String message, String errorCode, String resourceType, Object resourceId) {
        this(message, errorCode, resourceType, resourceId, null);
    }

    public AccessDeniedException(String message, String errorCode, String requiredPermission) {
        this(message, errorCode, null, null, requiredPermission);
    }

    // Getters
    public String getErrorCode() {
        return errorCode;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Object getResourceId() {
        return resourceId;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }
}