package com.account.domain.ledger;

public enum LedgerType {

    // Party ledgers
    CUSTOMER,
    CUSTOMER_ADVANCE,
    VENDOR,
    VENDOR_PAYABLE,

    // Cash / Bank
    BANK,
    CASH,
    PAYMENT_GATEWAY,

    // Income
    SALES,
    SERVICE_INCOME,

    // Taxes
    OUTPUT_IGST,
    OUTPUT_CGST,
    OUTPUT_SGST,
    TDS_RECEIVABLE,

    // Adjustments
    CREDIT_NOTE,
    REFUND_PAYABLE,
    ROUND_OFF,

    // Other
    EXPENSE,
    LIABILITY,
    ASSET
}