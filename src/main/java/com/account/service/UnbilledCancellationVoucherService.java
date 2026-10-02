package com.account.service;

import com.account.domain.User;
import com.account.domain.unbilled.UnbilledInvoice;

public interface UnbilledCancellationVoucherService {

    /**
     * Posts the CREDIT_NOTE voucher for the received amount (no CreditNote entity).
     * Dr Sales Return, Dr Output GST, Cr Customer.
     * Must run inside the admin-approval transaction.
     */
    void postCreditNoteVoucher(UnbilledInvoice unbilled, User admin);
}