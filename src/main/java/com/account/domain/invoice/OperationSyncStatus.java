package com.account.domain.invoice;

public enum OperationSyncStatus {
    NOT_REQUIRED,
    PENDING,
    SYNCED,
    RETRY_PENDING,
    FAILED
}
