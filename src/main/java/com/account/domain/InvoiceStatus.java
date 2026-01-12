// 2. InvoiceStatus Enum
package com.account.domain;

public enum InvoiceStatus {
    GENERATED,             // Auto-created after unbilled approval
    SENT_TO_CLIENT,
    VIEWED,
    PAID,
    PARTIALLY_PAID,
    CANCELLED,
    CREDIT_NOTED
}