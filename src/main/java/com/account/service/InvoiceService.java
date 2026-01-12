package com.account.service;

import com.account.domain.Invoice;
import com.account.domain.PaymentReceipt;
import com.account.domain.UnbilledInvoice;

import java.util.List;
import java.util.Map;

public interface InvoiceService {

	Invoice generateInvoiceForPayment(UnbilledInvoice unbilledInvoice, PaymentReceipt triggeringPayment);


}