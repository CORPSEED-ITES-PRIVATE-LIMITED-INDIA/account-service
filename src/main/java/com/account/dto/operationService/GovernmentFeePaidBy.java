package com.account.dto.operationService;

public enum GovernmentFeePaidBy {

    /*
     * Company funds the government fee.
     */
    COMPANY,

    /*
     * Client deposited money into a company bank/cash account.
     */
    CLIENT_TO_COMPANY,

    /*
     * Client paid the government portal directly.
     */
    CLIENT_DIRECT,

    /*
     * Legacy value representing direct client payment.
     */
    @Deprecated
    CLIENT
}