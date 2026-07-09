



package com.account.service;

import com.account.domain.*;
import com.account.domain.invoice.Invoice;
import com.account.domain.status.InvoiceStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.dto.invoice.*;
import com.account.dto.taxation.TaxationReportDto;
import com.account.dto.taxation.TaxationReportRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
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

//	Invoice generateInvoiceForPayment(UnbilledInvoice unbilled, PaymentReceipt triggeringReceipt, User approver);


	Invoice generateInvoiceForPayment(
			UnbilledInvoice unbilled,
			PaymentReceipt receipt,
			User approver,
			BigDecimal tdsAmount
	);

	InvoiceReportDto invoiceReport(InvoiceSearchRequest request);

	InvoiceDetailDto getInvoiceByInvoiceNumber(String invoiceNumber, Long requestingUserId);

	TaxationReportDto taxationReport(TaxationReportRequest request);

	List<InvoiceSummaryDto> getInvoicesByUnbilled(
			Long userId,
			Long unbilledId,
			int page,
			int size
	);

	long countInvoicesByUnbilled(
			Long userId,
			Long unbilledId);


	List<InvoiceSummaryDto> getInvoiceReport(
			Long userId,
			Long createdByUserId,
			InvoiceStatus status,
			LocalDate fromDate,
			LocalDate toDate
	);

	long getInvoiceReportCount(
			Long userId,
			Long createdByUserId,
			InvoiceStatus status,
			LocalDate fromDate,
			LocalDate toDate
	);


	InvoiceDetailDto confirmEInvoiceAndCreateProject(Long invoiceId, ConfirmInvoiceEInvoiceRequestDto request);
}