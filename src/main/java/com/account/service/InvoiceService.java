package com.account.service;

import com.account.domain.Invoice;
import com.account.domain.InvoiceStatus;
import com.account.domain.PaymentReceipt;
import com.account.domain.UnbilledInvoice;
import com.account.dto.invoice.InvoiceDetailDto;
import com.account.dto.invoice.InvoiceSummaryDto;

import java.util.List;

public interface InvoiceService {

	// Generation (called from PaymentService / approval flow)
	Invoice generateInvoiceForPayment(UnbilledInvoice unbilledInvoice, PaymentReceipt triggeringPayment);

	// Listing / querying
	List<InvoiceSummaryDto> getInvoicesList(
			Long createdById,
			InvoiceStatus status,
			int page,     // 0-based
			int size
	);

	long getInvoicesCount(Long createdById, InvoiceStatus status);


	List<InvoiceSummaryDto> searchInvoices(String invoiceNumber, String companyName, int i, int size);

	long countSearchInvoices(String invoiceNumber, String companyName);

	InvoiceDetailDto getInvoiceById(Long id, Long userId);

	// Optional future methods can be added here
	// Invoice getInvoiceDetail(Long id);
	// void updateInvoiceStatus(Long invoiceId, InvoiceStatus newStatus);
}