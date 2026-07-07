package com.account.domain.ledger;

public enum LedgerType {

    // Party ledgers
    CUSTOMER,
    CUSTOMER_ADVANCE,
    SUPPLIER,
    VENDOR,
    VENDOR_PAYABLE,

    // Cash / Bank
    BANK,
    CASH,
    PAYMENT_GATEWAY,

    // Income / Sales
    SALES,
    SERVICE_INCOME,
    INCOME,

    // Purchase
    PURCHASE,

    // Taxes
    TAX,
    OUTPUT_IGST,
    OUTPUT_CGST,
    OUTPUT_SGST,

    INPUT_IGST,
    INPUT_CGST,
    INPUT_SGST,
    TDS_RECEIVABLE,

    // Expenses
    EXPENSE,

    // Assets / Liabilities
    ASSET,
    LIABILITY,

    // Loans / Capital
    LOAN,
    CAPITAL,

    // Other Tally-style types
    INVESTMENT,
    STOCK,
    SUSPENSE,
    BRANCH,

    // Adjustments
    SALES_RETURN,
    REFUND_PAYABLE,
    ROUND_OFF
}