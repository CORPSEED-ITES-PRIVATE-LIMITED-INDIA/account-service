package com.account.domain.status;

public enum InvoiceStatus {

    /**
     * Invoice generated, waiting for e-invoice confirmation.
     * Used for REGISTERED and SEZ.
     */
    GENERATED,

    /**
     * E-invoice confirmed.
     * Used for REGISTERED and SEZ.
     */
    E_INVOICE_CONFIRMED,

    /**
     * E-invoice is not applicable and project has been
     * created/synced directly.
     *
     * Used for UNREGISTERED and INTERNATIONAL.
     */
    FINALIZED_WITHOUT_E_INVOICE,

    CANCELLED,
    REFUNDED
}