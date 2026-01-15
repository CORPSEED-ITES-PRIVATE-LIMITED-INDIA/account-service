package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateLineItem;
import com.account.dto.invoice.InvoiceSummaryDto;
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

	// ────────────────────────────────────────────────
	// Generate invoice from a specific payment
	// ────────────────────────────────────────────────
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

			invLine.calculateLineTotals();  // assume this method exists in InvoiceLineItem

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

	// ────────────────────────────────────────────────
	// List / paginated invoices
	// ────────────────────────────────────────────────
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

	// ────────────────────────────────────────────────
	// Helpers
	// ────────────────────────────────────────────────
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


}