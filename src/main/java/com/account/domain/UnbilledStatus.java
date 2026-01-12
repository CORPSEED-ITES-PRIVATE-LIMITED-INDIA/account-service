// 1. UnbilledStatus Enum
package com.account.domain;

public enum UnbilledStatus {
    PENDING_APPROVAL,      // Waiting for Accounts approval
    APPROVED,              // Approved → ready for invoicing
    PARTIALLY_PAID,
    FULLY_PAID,
    CANCELLED,
    REJECTED
}