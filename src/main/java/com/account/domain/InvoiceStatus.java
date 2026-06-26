package com.account.domain;

public enum InvoiceStatus {
    GENERATED,              // invoice generated, GST e-invoice not confirmed yet
    E_INVOICE_CONFIRMED,    // GST e-invoice attachment/details confirmed
    CANCELLED,
    REFUNDED
}