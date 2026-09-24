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
    TDS_PAYABLE,

    // Expenses
    EXPENSE,

    // Assets / Liabilities
    ASSET,
    LIABILITY,

    // Loans / Capital
    LOAN,
    CAPITAL,

    // Other
    INVESTMENT,
    STOCK,
    SUSPENSE,
    BRANCH,

    // Adjustments
    SALES_RETURN,
    REFUND_PAYABLE,
    ROUND_OFF,

    // =====================================================
    // GOVERNMENT FEE
    // =====================================================

    /**
     * Liability when client has already deposited money
     * with Corpseed specifically for government fee.
     */
    GOVERNMENT_FEE_CLIENT_ADVANCE,

    /**
     * Asset when Corpseed funds a government fee on behalf
     * of a client and the amount must be recovered later.
     */
    GOVERNMENT_FEE_RECEIVABLE,

    /**
     * Expense only when Corpseed itself ultimately bears
     * the government fee and it is not recoverable.
     */
    GOVERNMENT_FEE_EXPENSE,

    /**
     * Liability created when the amount becomes payable
     * to the government.
     */
    GOVERNMENT_FEE_PAYABLE
}