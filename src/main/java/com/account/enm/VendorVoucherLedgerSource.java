package com.account.enm;

public enum VendorVoucherLedgerSource {

    /*
     * Account Service will use the vendor ledger created or
     * resolved during vendor synchronization.
     *
     * ledgerId is not required from Operation Service.
     */
    VENDOR_LEDGER,

    /*
     * Operation Service must send an existing Account Service
     * ledger ID, such as:
     *
     * Bank ledger
     * TDS Payable ledger
     * Expense ledger
     * Input GST ledger
     */
    EXISTING_LEDGER
}