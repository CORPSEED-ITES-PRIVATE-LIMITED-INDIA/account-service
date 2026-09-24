package com.account.dto.operationService;

public enum GovernmentFeePaidBy {

    COMPANY,
    CLIENT_TO_COMPANY,
    CLIENT_DIRECT,

    /** Legacy value representing a direct client payment. */
    @Deprecated
    CLIENT
}
