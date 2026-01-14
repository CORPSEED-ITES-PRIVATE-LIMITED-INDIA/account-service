package com.account.service;

import com.account.domain.Invoice;
import com.account.domain.PaymentReceipt;
import com.account.domain.UnbilledInvoice;



public interface InvoiceService {

	Invoice generateInvoiceForPayment(UnbilledInvoice unbilledInvoice, PaymentReceipt triggeringPayment);


}