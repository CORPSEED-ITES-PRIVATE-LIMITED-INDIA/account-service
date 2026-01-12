package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateLineItem;
import com.account.repository.InvoiceLineItemRepository;
import com.account.repository.InvoiceRepository;
import com.account.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

	private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

	private final InvoiceRepository invoiceRepository;
	private final InvoiceLineItemRepository invoiceLineItemRepository; // if separate repo needed


	/**
	 * Generates a tax invoice for one specific payment amount.
	 * Proportionally copies line items from estimate and calculates GST.
	 */
	@Override
	@Transactional
	public Invoice generateInvoiceForPayment(UnbilledInvoice unbilledInvoice, PaymentReceipt triggeringPayment) {

		if (unbilledInvoice == null || triggeringPayment == null) {
			throw new IllegalArgumentException("UnbilledInvoice and PaymentReceipt are required");
		}

		// Get original estimate
		Estimate estimate = unbilledInvoice.getEstimate();

		// Calculate proportion (payment amount / total unbilled amount)
		BigDecimal proportion = triggeringPayment.getAmount()
				.divide(unbilledInvoice.getTotalAmount(), 6, BigDecimal.ROUND_HALF_UP);

		log.info("Generating invoice for payment {} | proportion: {}",
				triggeringPayment.getId(), proportion);

		// Create new Invoice
		Invoice invoice = new Invoice();
		invoice.setUnbilledInvoice(unbilledInvoice);
		invoice.setTriggeringPayment(triggeringPayment);
		invoice.setInvoiceNumber(generateInvoiceNumber());
		invoice.setPublicUuid(java.util.UUID.randomUUID().toString());
		invoice.setInvoiceDate(LocalDate.now());
		invoice.setCurrency("INR");
		invoice.setStatus(InvoiceStatus.GENERATED);
		invoice.setPlaceOfSupplyStateCode(estimate.getPlaceOfSupplyStateCode()); // copy from estimate

		// Copy & scale line items
		List<InvoiceLineItem> invoiceLines = new ArrayList<>();

		BigDecimal invoiceSubTotalExGst = BigDecimal.ZERO;
		BigDecimal invoiceTotalGst = BigDecimal.ZERO;

		for (EstimateLineItem estLine : estimate.getLineItems()) {
			InvoiceLineItem invLine = new InvoiceLineItem();

			// Copy snapshot
			invLine.setInvoice(invoice);
			invLine.setSourceEstimateLineItemId(estLine.getId());
			invLine.setItemName(estLine.getItemName());
			invLine.setDescription(estLine.getDescription());
			invLine.setHsnSacCode(estLine.getHsnSacCode());
			invLine.setUnit(estLine.getUnit());
			invLine.setCategoryCode(estLine.getCategoryCode());
			invLine.setFeeType(estLine.getFeeType());
			invLine.setDisplayOrder(estLine.getDisplayOrder());

			// Scale quantity & prices proportionally
			invLine.setQuantity(estLine.getQuantity()); // usually keep full qty, scale amounts
			invLine.setUnitPriceExGst(estLine.getUnitPriceExGst().multiply(proportion));
			invLine.setGstRate(estLine.getGstRate());

			// Let the entity's @PrePersist calculate totals
			invLine.calculateLineTotals();

			invoiceLines.add(invLine);

			invoiceSubTotalExGst = invoiceSubTotalExGst.add(invLine.getLineTotalExGst());
			invoiceTotalGst = invoiceTotalGst.add(invLine.getGstAmount());
		}

		// Set invoice totals
		invoice.setSubTotalExGst(invoiceSubTotalExGst);
		invoice.setTotalGstAmount(invoiceTotalGst);
		invoice.setGrandTotal(invoiceSubTotalExGst.add(invoiceTotalGst));

		// GST breakup (copy from unbilled or recalculate)
		invoice.setCgstAmount(invoiceTotalGst.divide(BigDecimal.valueOf(2), 2, BigDecimal.ROUND_HALF_UP));
		invoice.setSgstAmount(invoice.getCgstAmount());
		invoice.setIgstAmount(BigDecimal.ZERO); // adjust based on placeOfSupply

		// Save invoice + lines (cascade)
		invoice.setLineItems(invoiceLines);
		invoice = invoiceRepository.save(invoice);

		log.info("Generated tax invoice: {} | amount: {} | for payment: {}",
				invoice.getInvoiceNumber(), invoice.getGrandTotal(), triggeringPayment.getId());

		return invoice;
	}

	private String generateInvoiceNumber() {
		// Simple example - improve with financial year + sequence in production
		long count = invoiceRepository.count() + 1;
		return String.format("INV-%d-%08d", LocalDate.now().getYear(), count);
	}
}