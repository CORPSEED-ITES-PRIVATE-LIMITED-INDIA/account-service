package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateLineItem;
import com.account.dto.invoice.InvoiceDetailDto;
import com.account.dto.invoice.InvoiceSummaryDto;
import com.account.exception.AccessDeniedException;
import com.account.exception.ResourceNotFoundException;
import com.account.repository.InvoiceRepository;
import com.account.repository.UserRepository;
import com.account.service.InvoiceService;
import com.account.util.DateTimeUtil;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

	private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

	private final InvoiceRepository invoiceRepository;
	private final UserRepository userRepository;
	private final DateTimeUtil dateTimeUtil;


	@Override
	@Transactional
	public Invoice generateInvoiceForPayment(UnbilledInvoice unbilledInvoice, PaymentReceipt triggeringPayment, User createdBy) {

		if (unbilledInvoice == null || triggeringPayment == null || createdBy == null) {
			throw new IllegalArgumentException("UnbilledInvoice, PaymentReceipt, and createdBy User are required");
		}

		Estimate estimate = unbilledInvoice.getEstimate();
		if (estimate == null) {
			throw new IllegalStateException("Estimate not found for unbilled invoice");
		}

		// Calculate proportion: this payment / total unbilled amount
		BigDecimal proportion = triggeringPayment.getAmount()
				.divide(unbilledInvoice.getTotalAmount(), 6, RoundingMode.HALF_UP);

		log.info("Generating invoice for payment {} | proportion: {} | unbilled: {} | estimate: {}",
				triggeringPayment.getId(), proportion, unbilledInvoice.getUnbilledNumber(),
				estimate.getEstimateNumber());

		Invoice invoice = new Invoice();
		invoice.setUnbilledInvoice(unbilledInvoice);
		invoice.setTriggeringPayment(triggeringPayment);
		invoice.setInvoiceNumber(generateInvoiceNumber());
		invoice.setPublicUuid(dateTimeUtil.generateUuid());
		invoice.setInvoiceDate(dateTimeUtil.nowLocalDate());
		invoice.setCurrency("INR");
		invoice.setStatus(InvoiceStatus.GENERATED);
		invoice.setPlaceOfSupplyStateCode(estimate.getPlaceOfSupplyStateCode());

		invoice.setCreatedBy(createdBy);

		// Buyer GSTIN – take from the UNIT (most accurate), fallback to any valid unit
		String buyerGstin = null;
		if (unbilledInvoice.getUnit() != null) {
			buyerGstin = unbilledInvoice.getUnit().getGstNo();
		}

		// Fallback: if no specific unit is linked, take first non-deleted unit with GSTIN
		if (buyerGstin == null || buyerGstin.trim().isEmpty()) {
			if (unbilledInvoice.getCompany() != null && unbilledInvoice.getCompany().getUnits() != null) {
				buyerGstin = unbilledInvoice.getCompany().getUnits().stream()
						.filter(unit -> !unit.isDeleted() && unit.getGstNo() != null && !unit.getGstNo().trim().isEmpty())
						.map(CompanyUnit::getGstNo)
						.findFirst()
						.orElse(null);
			}
		}

		if (buyerGstin != null && !buyerGstin.trim().isEmpty()) {
			invoice.setBuyerGstin(buyerGstin.trim());
		} else {
			log.warn("No valid buyer GSTIN found for unbilled invoice {} (Company: {}, Unit: {})",
					unbilledInvoice.getUnbilledNumber(),
					unbilledInvoice.getCompany() != null ? unbilledInvoice.getCompany().getName() : "N/A",
					unbilledInvoice.getUnit() != null ? unbilledInvoice.getUnit().getUnitName() : "N/A");
		}


		String sellerStateCode = "06"; // Example: Delhi = "07", Haryana = "06", Maharashtra = "27", etc.
		String placeOfSupply = invoice.getPlaceOfSupplyStateCode();
		boolean isIntraState = placeOfSupply != null && placeOfSupply.equals(sellerStateCode);

		// ─────────────────────────────────────────────────────────────
		// Line items – proportional copy + GST breakup
		// ─────────────────────────────────────────────────────────────
		List<InvoiceLineItem> invoiceLines = new ArrayList<>();
		BigDecimal subTotalExGst = BigDecimal.ZERO;
		BigDecimal totalGst = BigDecimal.ZERO;
		BigDecimal totalCgst = BigDecimal.ZERO;
		BigDecimal totalSgst = BigDecimal.ZERO;
		BigDecimal totalIgst = BigDecimal.ZERO;

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

			invLine.setQuantity(estLine.getQuantity());
			invLine.setUnitPriceExGst(estLine.getUnitPriceExGst().multiply(proportion));
			invLine.setGstRate(estLine.getGstRate());

			invLine.calculateLineTotals(); // Must recalculate totals after scaling

			// Apply correct GST split (CGST+SGST or IGST)
			BigDecimal gstAmount = invLine.getGstAmount() != null ? invLine.getGstAmount() : BigDecimal.ZERO;
			BigDecimal halfGst = gstAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

			if (isIntraState) {
				invLine.setCgstAmount(halfGst);
				invLine.setSgstAmount(halfGst);
				invLine.setIgstAmount(BigDecimal.ZERO);
			} else {
				invLine.setCgstAmount(BigDecimal.ZERO);
				invLine.setSgstAmount(BigDecimal.ZERO);
				invLine.setIgstAmount(gstAmount);
			}

			invoiceLines.add(invLine);

			subTotalExGst = subTotalExGst.add(invLine.getLineTotalExGst());
			totalGst = totalGst.add(gstAmount);
			totalCgst = totalCgst.add(invLine.getCgstAmount());
			totalSgst = totalSgst.add(invLine.getSgstAmount());
			totalIgst = totalIgst.add(invLine.getIgstAmount());
		}

		// Set header totals
		invoice.setSubTotalExGst(subTotalExGst.setScale(2, RoundingMode.HALF_UP));
		invoice.setTotalGstAmount(totalGst.setScale(2, RoundingMode.HALF_UP));
		invoice.setGrandTotal(invoice.getSubTotalExGst().add(invoice.getTotalGstAmount()));

		invoice.setCgstAmount(totalCgst.setScale(2, RoundingMode.HALF_UP));
		invoice.setSgstAmount(totalSgst.setScale(2, RoundingMode.HALF_UP));
		invoice.setIgstAmount(totalIgst.setScale(2, RoundingMode.HALF_UP));

		invoice.setLineItems(invoiceLines);

		invoice = invoiceRepository.save(invoice);

		log.info("Generated invoice: {} | grandTotal: {} | lines: {} | buyerGSTIN: {} | for payment: {}",
				invoice.getInvoiceNumber(),
				invoice.getGrandTotal(),
				invoiceLines.size(),
				invoice.getBuyerGstin(),
				triggeringPayment.getId());

		return invoice;
	}


	@Override
	public List<InvoiceSummaryDto> getInvoicesList(Long userId, InvoiceStatus status, int page, int size) {

		if (userId == null) {
			throw new IllegalArgumentException("userId is required");
		}

		User requestingUser = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"User not found with ID: " + userId,
						"USER_NOT_FOUND",
						"User",
						userId
				));

		boolean isAdmin = requestingUser.getUserRole() != null
				&& requestingUser.getUserRole().stream()
				.anyMatch(r -> r != null && "ADMIN".equalsIgnoreCase(r.getName()));

		boolean isAccountDept = requestingUser.getDepartment() != null
				&& "accounts".equalsIgnoreCase(requestingUser.getDepartment());

		// Authorization rules
		Long createdByIdFilter;
		if (isAdmin) {
			createdByIdFilter = null;
		} else if (isAccountDept) {
			createdByIdFilter = userId;
		} else {
			throw new AccessDeniedException("Not authorized to view invoices", "ACCESS_DENIED_INVOICE_LIST");
		}

		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

		Page<Invoice> pageResult = invoiceRepository.findInvoices(status, createdByIdFilter, pageable);

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
		Estimate estimate = (unbilled != null) ? unbilled.getEstimate() : null;

		return InvoiceSummaryDto.builder()
				.id(inv.getId())
				.publicUuid(inv.getPublicUuid())
				.invoiceNumber(inv.getInvoiceNumber())
				.unbilledNumber(unbilled != null ? unbilled.getUnbilledNumber() : null)
				.estimateNumber(estimate != null ? estimate.getEstimateNumber() : null)
				// ─── Added solution fields ────────────────────────────────
				.solutionId(estimate != null ? estimate.getSolutionId() : null)
				.solutionName(estimate != null ? estimate.getSolutionName() : null)
				// ───────────────────────────────────────────────────────────
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
		Estimate estimate = (unbilled != null) ? unbilled.getEstimate() : null;

		// Map and sort line items
		List<InvoiceDetailDto.LineItemDto> lineItems = invoice.getLineItems().stream()
				.map(this::toLineItemDto)
				.sorted(java.util.Comparator.comparing(
						InvoiceDetailDto.LineItemDto::getDisplayOrder,
						java.util.Comparator.nullsLast(Integer::compareTo)))
				.collect(Collectors.toList());

		// Create DTO instance with setters
		InvoiceDetailDto dto = new InvoiceDetailDto();

		dto.setId(invoice.getId());
		dto.setPublicUuid(invoice.getPublicUuid());
		dto.setInvoiceNumber(invoice.getInvoiceNumber());
		dto.setUnbilledNumber(unbilled != null ? unbilled.getUnbilledNumber() : null);
		dto.setEstimateNumber(estimate != null ? estimate.getEstimateNumber() : null);

		// Optional: also add to detail view if desired
		dto.setSolutionId(estimate != null ? estimate.getSolutionId() : null);
		dto.setSolutionName(estimate != null ? estimate.getSolutionName() : null);

		dto.setCompanyName(
				unbilled != null && unbilled.getCompany() != null
						? unbilled.getCompany().getName()
						: null
		);

		dto.setContactName(
				unbilled != null && unbilled.getContact() != null
						? unbilled.getContact().getName()
						: null
		);

		dto.setInvoiceDate(invoice.getInvoiceDate());
		dto.setCurrency(invoice.getCurrency());
		dto.setStatus(invoice.getStatus());
		dto.setIrn(invoice.getIrn());
		dto.setPlaceOfSupplyStateCode(invoice.getPlaceOfSupplyStateCode());
		dto.setBuyerGstin(invoice.getBuyerGstin());

		dto.setSubTotalExGst(invoice.getSubTotalExGst());
		dto.setTotalGstAmount(invoice.getTotalGstAmount());
		dto.setCgstAmount(invoice.getCgstAmount());
		dto.setSgstAmount(invoice.getSgstAmount());
		dto.setIgstAmount(invoice.getIgstAmount());
		dto.setGrandTotal(invoice.getGrandTotal());

		dto.setCreatedByName(getUserDisplayName(invoice.getCreatedBy()));
		dto.setCreatedAt(invoice.getCreatedAt());
		dto.setUpdatedAt(invoice.getUpdatedAt());

		dto.setLineItems(lineItems);

		return dto;
	}


	private InvoiceDetailDto.LineItemDto toLineItemDto(InvoiceLineItem li) {
		InvoiceDetailDto.LineItemDto lineDto = new InvoiceDetailDto.LineItemDto();

		lineDto.setId(li.getId());
		lineDto.setSourceEstimateLineItemId(li.getSourceEstimateLineItemId());
		lineDto.setItemName(li.getItemName());
		lineDto.setDescription(li.getDescription());
		lineDto.setHsnSacCode(li.getHsnSacCode());
		lineDto.setQuantity(li.getQuantity());
		lineDto.setUnit(li.getUnit());
		lineDto.setUnitPriceExGst(li.getUnitPriceExGst());
		lineDto.setLineTotalExGst(li.getLineTotalExGst());
		lineDto.setGstRate(li.getGstRate());
		lineDto.setGstAmount(li.getGstAmount());
		lineDto.setLineTotalWithGst(li.getLineTotalWithGst());
		lineDto.setCgstAmount(li.getCgstAmount());
		lineDto.setSgstAmount(li.getSgstAmount());
		lineDto.setIgstAmount(li.getIgstAmount());
		lineDto.setDisplayOrder(li.getDisplayOrder());
		lineDto.setCategoryCode(li.getCategoryCode());
		lineDto.setFeeType(li.getFeeType());

		return lineDto;
	}


	private String getUserDisplayName(User user) {
		if (user == null) return null;
		return user.getFullName() != null ? user.getFullName() : user.getEmail();
	}

}