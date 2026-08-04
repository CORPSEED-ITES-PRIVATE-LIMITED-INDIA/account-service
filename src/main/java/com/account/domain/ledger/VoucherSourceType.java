package com.account.domain.ledger;

public enum VoucherSourceType {

    PAYMENT_RECEIPT,
    INVOICE,
    CREDIT_NOTE,
    REFUND,
    MANUAL,

    /** Legacy project-expense source. */
    PROJECT_EXPENSE,

    /** Entry A: Dr receiving bank/cash, Cr client government-fee advance. */
    PROJECT_EXPENSE_CLIENT_RECEIPT,

    /**
     * Entry B for client funding: Dr client advance, Cr government-fee payable.
     * Company funding: Dr government-fee expense, Cr government-fee payable.
     */
    PROJECT_EXPENSE_GOVT_FEE_ACCRUAL,

    PROCUREMENT_VENDOR_INVOICE,
    PROCUREMENT_VENDOR_PAYMENT,
    VENDOR_OPENING_BALANCE
}
