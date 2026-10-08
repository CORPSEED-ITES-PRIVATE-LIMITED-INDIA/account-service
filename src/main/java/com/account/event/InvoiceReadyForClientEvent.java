package com.account.event;

/**
 * Published by InvoiceServiceImpl when an invoice is created or its
 * e-invoice is confirmed. The email itself is sent only after the
 * surrounding transaction commits (see InvoiceEmailEventListener).
 */
public record InvoiceReadyForClientEvent(Long invoiceId) {
}
