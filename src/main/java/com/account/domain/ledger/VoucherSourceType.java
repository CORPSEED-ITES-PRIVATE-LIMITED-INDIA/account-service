package com.account.domain.ledger;

public enum VoucherSourceType {

    PAYMENT_RECEIPT,
    INVOICE,
    CREDIT_NOTE,
    REFUND,
    MANUAL,
    PROJECT_EXPENSE,

    PROCUREMENT_VENDOR_INVOICE,
    PROCUREMENT_VENDOR_PAYMENT,
    VENDOR_OPENING_BALANCE
}