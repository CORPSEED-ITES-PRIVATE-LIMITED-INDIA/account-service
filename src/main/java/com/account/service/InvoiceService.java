package com.account.service;

import com.account.domain.*;
import com.account.dto.invoice.InvoiceDetailDto;
import com.account.dto.invoice.InvoiceReportDto;
import com.account.dto.invoice.InvoiceSearchRequest;
import com.account.dto.invoice.InvoiceSummaryDto;
import com.account.dto.taxation.TaxationReportDto;
import com.account.dto.taxation.TaxationReportRequest;

import java.util.List;

public interface InvoiceService {

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

	Invoice generateInvoiceForPayment(UnbilledInvoice unbilled, PaymentReceipt triggeringReceipt, User approver);

	InvoiceReportDto invoiceReport(InvoiceSearchRequest request);

	InvoiceDetailDto getInvoiceByInvoiceNumber(String invoiceNumber, Long requestingUserId);

	TaxationReportDto taxationReport(TaxationReportRequest request);
}