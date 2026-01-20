package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateLineItem;
import com.account.dto.invoice.InvoiceDetailDto;
import com.account.dto.invoice.InvoiceSummaryDto;
import com.account.exception.AccessDeniedException;
import com.account.exception.ResourceNotFoundException;
import com.account.repository.InvoiceRepository;
import com.account.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

	private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

	private final InvoiceRepository invoiceRepository;

	@Override
	@Transactional
	public Invoice generateInvoiceForPayment(UnbilledInvoice unbilledInvoice, PaymentReceipt triggeringPayment) {

		if (unbilledInvoice == null || triggeringPayment == null) {
			throw new IllegalArgumentException("UnbilledInvoice and PaymentReceipt are required");
		}

		Estimate estimate = unbilledInvoice.getEstimate();
		if (estimate == null) {
			throw new IllegalStateException("Estimate not found for unbilled invoice");
		}

		// Calculate proportion based on this payment vs total unbilled amount
		BigDecimal proportion = triggeringPayment.getAmount()
				.divide(unbilledInvoice.getTotalAmount(), 6, BigDecimal.ROUND_HALF_UP);

		log.info("Generating invoice for payment {} | proportion: {} | unbilled: {}",
				triggeringPayment.getId(), proportion, unbilledInvoice.getUnbilledNumber());

		Invoice invoice = new Invoice();
		invoice.setUnbilledInvoice(unbilledInvoice);
		invoice.setTriggeringPayment(triggeringPayment);
		invoice.setInvoiceNumber(generateInvoiceNumber());
		invoice.setPublicUuid(java.util.UUID.randomUUID().toString());
		invoice.setInvoiceDate(LocalDate.now());
		invoice.setCurrency("INR");
		invoice.setStatus(InvoiceStatus.GENERATED);
		invoice.setPlaceOfSupplyStateCode(estimate.getPlaceOfSupplyStateCode());

		// Copy and scale line items
		List<InvoiceLineItem> invoiceLines = new ArrayList<>();
		BigDecimal subTotalExGst = BigDecimal.ZERO;
		BigDecimal totalGst = BigDecimal.ZERO;

		for (EstimateLineItem estLine : estimate.getLineItems()) {
			InvoiceLineItem invLine = new InvoiceLineItem();
			invLine.setInvoice(invoice);
			invLine.setSourceEstimateLineItemId(estLine.getId());
			invLine.setItemName(estLine.getItemName());
			invLine.setDescription(estLine.getDescription());
			invLine.setHsnSacCode(estLine.getHsnSacCode());
			invLine.setUnit(estLine.getUnit());
			invLine.setCategoryCode(estLine.getCategoryCode());
			invLine.setFeeType(estLine.getFeeType());
			invLine.setDisplayOrder(estLine.getDisplayOrder());

			// Proportional scaling (quantity stays full, amounts scaled)
			invLine.setQuantity(estLine.getQuantity());
			invLine.setUnitPriceExGst(estLine.getUnitPriceExGst().multiply(proportion));
			invLine.setGstRate(estLine.getGstRate());

			invLine.calculateLineTotals(); // This method exists in InvoiceLineItem as public

			invoiceLines.add(invLine);

			subTotalExGst = subTotalExGst.add(invLine.getLineTotalExGst());
			totalGst = totalGst.add(invLine.getGstAmount());
		}

		// Set totals
		invoice.setSubTotalExGst(subTotalExGst);
		invoice.setTotalGstAmount(totalGst);
		invoice.setGrandTotal(subTotalExGst.add(totalGst));

		// Simple GST breakup (assuming intra-state - CGST + SGST)
		// In production → use placeOfSupply vs seller state to decide CGST/SGST vs IGST
		BigDecimal halfGst = totalGst.divide(BigDecimal.valueOf(2), 2, BigDecimal.ROUND_HALF_UP);
		invoice.setCgstAmount(halfGst);
		invoice.setSgstAmount(halfGst);
		invoice.setIgstAmount(BigDecimal.ZERO);

		invoice.setLineItems(invoiceLines);

		// Save (cascade saves line items)
		invoice = invoiceRepository.save(invoice);

		log.info("Generated invoice: {} | grandTotal: {} | lines: {} | for payment: {}",
				invoice.getInvoiceNumber(), invoice.getGrandTotal(), invoiceLines.size(), triggeringPayment.getId());

		return invoice;
	}

	@Override
	public List<InvoiceSummaryDto> getInvoicesList(Long createdById, InvoiceStatus status, int page, int size) {
		log.info("Fetching invoices list | createdById={}, status={}, page={}, size={}",
				createdById != null ? createdById : "all",
				status != null ? status : "all",
				page + 1, size);

		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

		Page<Invoice> pageResult = invoiceRepository.findInvoices(status, createdById, pageable);

		return pageResult.getContent().stream()
				.map(this::toSummaryDto)
				.collect(Collectors.toList());
	}

	@Override
	public long getInvoicesCount(Long createdById, InvoiceStatus status) {
		log.info("Counting invoices | createdById={}, status={}",
				createdById != null ? createdById : "all",
				status != null ? status : "all");

		return invoiceRepository.countInvoices(status, createdById);
	}

	private String generateInvoiceNumber() {
		// TODO: In production use financial year + sequence per year
		long count = invoiceRepository.count() + 1;
		int year = LocalDate.now().getYear();
		return String.format("INV-%d-%08d", year, count);
	}

	private InvoiceSummaryDto toSummaryDto(Invoice inv) {
		UnbilledInvoice unbilled = inv.getUnbilledInvoice();

		return InvoiceSummaryDto.builder()
				.id(inv.getId())
				.publicUuid(inv.getPublicUuid())
				.invoiceNumber(inv.getInvoiceNumber())
				.unbilledNumber(unbilled != null ? unbilled.getUnbilledNumber() : null)
				.estimateNumber(unbilled != null && unbilled.getEstimate() != null
						? unbilled.getEstimate().getEstimateNumber() : null)
				.companyName(unbilled != null && unbilled.getCompany() != null
						? unbilled.getCompany().getName() : null)
				.contactName(unbilled != null && unbilled.getContact() != null
						? unbilled.getContact().getName() : null)
				.invoiceDate(inv.getInvoiceDate())
				.grandTotal(inv.getGrandTotal())
				.totalGstAmount(inv.getTotalGstAmount())
				.irn(inv.getIrn())
				.status(inv.getStatus())
				.createdByName(inv.getCreatedBy() != null
						? (inv.getCreatedBy().getFullName() != null
						? inv.getCreatedBy().getFullName()
						: inv.getCreatedBy().getEmail())
						: null)
				.createdAt(inv.getCreatedAt())
				.build();
	}

	@Override
	public List<InvoiceSummaryDto> searchInvoices(
			String invoiceNumber,
			String companyName,
			int page,
			int size
	) {
		log.info("Searching invoices | invoiceNumber={}, companyName={}, page={}, size={}",
				invoiceNumber, companyName, page, size);

		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

		Page<Invoice> pageResult = invoiceRepository.searchInvoices(
				invoiceNumber != null && !invoiceNumber.trim().isEmpty() ? invoiceNumber.trim() : null,
				companyName != null && !companyName.trim().isEmpty() ? companyName.trim() : null,
				pageable
		);

		return pageResult.getContent().stream()
				.map(this::toSummaryDto)
				.collect(Collectors.toList());
	}

	@Override
	public long countSearchInvoices(String invoiceNumber, String companyName) {
		log.info("Counting search invoices | invoiceNumber={}, companyName={}",
				invoiceNumber, companyName);

		return invoiceRepository.countSearchInvoices(
				invoiceNumber != null && !invoiceNumber.trim().isEmpty() ? invoiceNumber.trim() : null,
				companyName != null && !companyName.trim().isEmpty() ? companyName.trim() : null
		);
	}

	@Override
	@Transactional(readOnly = true)
	public InvoiceDetailDto getInvoiceById(Long invoiceId, Long requestingUserId) {
		if (invoiceId == null || requestingUserId == null) {
			throw new IllegalArgumentException("Invoice ID and requesting user ID are required");
		}

		Invoice invoice = invoiceRepository.findById(invoiceId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"Invoice not found with ID: " + invoiceId,
						"INVOICE_NOT_FOUND",
						"Invoice",
						invoiceId
				));

		// Security check: only creator can view (you can extend with roles later)
		if (invoice.getCreatedBy() == null ||
				!invoice.getCreatedBy().getId().equals(requestingUserId)) {

			throw new AccessDeniedException(
					"You are not authorized to view this invoice",
					"ACCESS_DENIED_INVOICE"
			);
		}
		return toDetailDto(invoice);
	}

	private InvoiceDetailDto toDetailDto(Invoice invoice) {
		UnbilledInvoice unbilled = invoice.getUnbilledInvoice();
		Estimate estimate = unbilled != null ? unbilled.getEstimate() : null;

		List<InvoiceDetailDto.LineItemDto> lineItems = invoice.getLineItems().stream()
				.map(this::toLineItemDto)
				.sorted(java.util.Comparator.comparing(
						InvoiceDetailDto.LineItemDto::getDisplayOrder,
						java.util.Comparator.nullsLast(Integer::compareTo)))
				.collect(Collectors.toList());

		return InvoiceDetailDto.builder()
				.id(invoice.getId())
				.publicUuid(invoice.getPublicUuid())
				.invoiceNumber(invoice.getInvoiceNumber())
				.unbilledNumber(unbilled != null ? unbilled.getUnbilledNumber() : null)
				.estimateNumber(estimate != null ? estimate.getEstimateNumber() : null)
				.companyName(unbilled != null && unbilled.getCompany() != null
						? unbilled.getCompany().getName() : null)
				.contactName(unbilled != null && unbilled.getContact() != null
						? unbilled.getContact().getName() : null)
				.invoiceDate(invoice.getInvoiceDate())
				.currency(invoice.getCurrency())
				.status(invoice.getStatus())
				.irn(invoice.getIrn())
				.placeOfSupplyStateCode(invoice.getPlaceOfSupplyStateCode())
				.buyerGstin(invoice.getBuyerGstin())
				.sellerGstin(invoice.getSellerGstin())
				.subTotalExGst(invoice.getSubTotalExGst())
				.totalGstAmount(invoice.getTotalGstAmount())
				.cgstAmount(invoice.getCgstAmount())
				.sgstAmount(invoice.getSgstAmount())
				.igstAmount(invoice.getIgstAmount())
				.grandTotal(invoice.getGrandTotal())
				.createdByName(getUserDisplayName(invoice.getCreatedBy()))
				.createdAt(invoice.getCreatedAt())
				.updatedAt(invoice.getUpdatedAt())
				.lineItems(lineItems)
				.build();
	}

	private InvoiceDetailDto.LineItemDto toLineItemDto(InvoiceLineItem li) {
		return InvoiceDetailDto.LineItemDto.builder()
				.id(li.getId())
				.sourceEstimateLineItemId(li.getSourceEstimateLineItemId())
				.itemName(li.getItemName())
				.description(li.getDescription())
				.hsnSacCode(li.getHsnSacCode())
				.quantity(li.getQuantity())
				.unit(li.getUnit())
				.unitPriceExGst(li.getUnitPriceExGst())
				.lineTotalExGst(li.getLineTotalExGst())
				.gstRate(li.getGstRate())
				.gstAmount(li.getGstAmount())
				.lineTotalWithGst(li.getLineTotalWithGst())
				.cgstAmount(li.getCgstAmount())
				.sgstAmount(li.getSgstAmount())
				.igstAmount(li.getIgstAmount())
				.displayOrder(li.getDisplayOrder())
				.categoryCode(li.getCategoryCode())
				.feeType(li.getFeeType())
				.build();
	}

	private String getUserDisplayName(User user) {
		if (user == null) return null;
		return user.getFullName() != null ? user.getFullName() : user.getEmail();
	}

}