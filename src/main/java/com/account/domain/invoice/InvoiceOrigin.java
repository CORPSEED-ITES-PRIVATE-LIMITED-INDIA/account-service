package com.account.domain.invoice;

public enum InvoiceOrigin {

    /*
     * Existing flow:
     *
     * Payment registered
     * → UnbilledInvoice created
     * → Accounts approves payment
     * → Invoice generated
     */
    PAYMENT_APPROVAL,

    /*
     * New flow:
     *
     * Salesperson raises Advance Tax Invoice request
     * → Accounts approves request
     * → Invoice generated before payment
     */
    ADVANCE_TAX_INVOICE
}