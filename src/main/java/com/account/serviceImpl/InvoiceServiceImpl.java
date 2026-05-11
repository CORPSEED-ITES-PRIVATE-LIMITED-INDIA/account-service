package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateLineItem;
import com.account.dto.invoice.*;
import com.account.exception.AccessDeniedException;
import com.account.exception.ResourceNotFoundException;
import com.account.repository.InvoiceRepository;
import com.account.repository.OrganizationRepository;
import com.account.repository.UserRepository;
import com.account.service.InvoiceService;
import com.account.util.DateTimeUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

	private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

	private final InvoiceRepository invoiceRepository;
	private final UserRepository userRepository;
	private final DateTimeUtil dateTimeUtil;
	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private final OrganizationRepository organizationRepository;


	/**
	 * Generates a Tax Invoice for a specific payment receipt.
	 * The invoice grand total will be EXACTLY equal to receipt.getAmount()
	 * Line items are prorated, and any minor rounding difference is adjusted on the last line.
	 */
	public Invoice generateInvoiceForPayment(UnbilledInvoice unbilled, PaymentReceipt receipt, User approver) {

		Estimate estimate = unbilled.getEstimate();
		if (estimate == null) {
			throw new IllegalStateException("Estimate not found for Unbilled " + unbilled.getUnbilledNumber());
		}

		log.info("Generating invoice for PaymentReceipt {} | amount: ₹{} | unbilled: {}",
				receipt.getId(), receipt.getAmount(), unbilled.getUnbilledNumber());

		Invoice invoice = new Invoice();
		invoice.setPublicUuid(UUID.randomUUID().toString());

		// Generate unique invoice number (customize your format)
		invoice.setInvoiceNumber(generateInvoiceNumber());  // Implement this method as needed

		invoice.setUnbilledInvoice(unbilled);
		invoice.setTriggeringPayment(receipt);  // Critical: links exactly to this payment
		invoice.setInvoiceDate(LocalDate.now());
		invoice.setCurrency("INR");
		invoice.setStatus(InvoiceStatus.GENERATED);
		invoice.setCreatedBy(approver);
		invoice.setUpdatedBy(approver);
		invoice.setCreatedAt(LocalDateTime.now());

		// Copy key fields from estimate
		invoice.setSolutionId(estimate.getSolutionId());
		invoice.setSolutionName(estimate.getSolutionName());

		// Buyer GSTIN – always prefer unit-level if unit exists, fallback to null or company PAN if needed
		String buyerGstin = null;

		if (unbilled.getUnit() != null) {
			buyerGstin = unbilled.getUnit().getGstNo();  // This exists
		} else if (unbilled.getCompany() != null) {
			// Company has no GST field → either leave null or fallback to PAN (not ideal for GST invoice)
			buyerGstin = null;  // or unbilled.getCompany().getPanNo() if you want to show PAN instead
			// Recommendation: log warning if no GST found
			log.warn("No GSTIN found for Unbilled {} – Unit: {}, Company: {}",
					unbilled.getUnbilledNumber(),
					unbilled.getUnit() != null ? unbilled.getUnit().getUnitName() : "None",
					unbilled.getCompany().getName());
		}

		invoice.setBuyerGstin(buyerGstin);

		// Place of Supply (critical for CGST/SGST vs IGST)
		invoice.setPlaceOfSupplyStateCode(estimate.getPlaceOfSupplyStateCode());

		// Exact grand total = payment amount (no compromise)
		BigDecimal exactGrandTotal = receipt.getAmount().setScale(2, RoundingMode.HALF_UP);
		invoice.setGrandTotal(exactGrandTotal);

		// Proration ratio
		BigDecimal totalUnbilled = unbilled.getTotalAmount();
		BigDecimal ratio = totalUnbilled.compareTo(BigDecimal.ZERO) > 0
				? exactGrandTotal.divide(totalUnbilled, 10, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;

		List<InvoiceLineItem> invoiceLines = new ArrayList<>();
		BigDecimal accumulatedWithGst = BigDecimal.ZERO;

		int lineIndex = 0;
		List<EstimateLineItem> estimateLines = estimate.getLineItems();

		for (EstimateLineItem estLine : estimateLines) {
			InvoiceLineItem invLine = new InvoiceLineItem();
			invLine.setInvoice(invoice);
			invLine.setSourceEstimateLineItemId(estLine.getId());
			invLine.setItemName(estLine.getItemName());
			invLine.setDescription(estLine.getDescription());
			invLine.setHsnSacCode(estLine.getHsnSacCode());
			invLine.setQuantity(estLine.getQuantity());
			invLine.setUnit(estLine.getUnit());

			// Prorate unit price ex-GST
			BigDecimal proratedUnitPrice = estLine.getUnitPriceExGst()
					.multiply(ratio)
					.setScale(2, RoundingMode.HALF_UP);
			invLine.setUnitPriceExGst(proratedUnitPrice);

			invLine.setGstRate(estLine.getGstRate());
			invLine.setDisplayOrder(estLine.getDisplayOrder());
			invLine.setCategoryCode(estLine.getCategoryCode());
			invLine.setFeeType(estLine.getFeeType());

			// Calculate line totals (your @PrePersist logic will run, but we call manually for control)
			invLine.calculateLineTotals();

			invoiceLines.add(invLine);

			accumulatedWithGst = accumulatedWithGst.add(invLine.getLineTotalWithGst());
			lineIndex++;
		}

		// Fix rounding difference (almost always < 0.10 due to HALF_UP)
		BigDecimal difference = exactGrandTotal.subtract(accumulatedWithGst);

		if (difference.abs().compareTo(new BigDecimal("1.00")) > 0) {
			log.warn("Large proration difference detected: ₹{} for Payment {} - check data",
					difference, receipt.getId());
			// Optional: throw exception if too large
			// throw new IllegalStateException("Proration mismatch too large: " + difference);
		}

		if (!difference.equals(BigDecimal.ZERO) && !invoiceLines.isEmpty()) {
			// Adjust the LAST line
			InvoiceLineItem lastLine = invoiceLines.get(invoiceLines.size() - 1);

			// Add difference to line total with GST
			BigDecimal newLineTotalWithGst = lastLine.getLineTotalWithGst().add(difference);

			// Re-calculate backward to keep consistency (optional but good)
			// Here we just set and let calculateLineTotals re-compute GST if needed
			lastLine.setLineTotalWithGst(newLineTotalWithGst);

			// Re-run calculation to update GST fields proportionally (your method will handle)
			lastLine.calculateLineTotals();
		}

		invoice.setLineItems(invoiceLines);

		// Recalculate header totals from lines (safety net)
		invoice.setSubTotalExGst(
				invoiceLines.stream()
						.map(InvoiceLineItem::getLineTotalExGst)
						.reduce(BigDecimal.ZERO, BigDecimal::add)
		);

		invoice.setTotalGstAmount(
				invoiceLines.stream()
						.map(InvoiceLineItem::getGstAmount)
						.reduce(BigDecimal.ZERO, BigDecimal::add)
		);

		// CGST/SGST/IGST are already set per line in calculateLineTotals()
		// If you have global override logic, add it here


		// ==========================================
		// HEADER LEVEL GST SPLIT (IGST vs CGST/SGST)
		// ==========================================
		BigDecimal totalGst = safeMoney(invoice.getTotalGstAmount());
		boolean igstApplicable = isIgstApplicable(unbilled);

		if (igstApplicable) {
			invoice.setIgstAmount(totalGst);
			invoice.setCgstAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
			invoice.setSgstAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

			log.info("IGST applied for invoice {} | orgState != unitState or fallback condition met | totalGst={}",
					invoice.getInvoiceNumber(), totalGst);
		} else {
			BigDecimal halfGst = totalGst
					.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);

			invoice.setIgstAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
			invoice.setCgstAmount(halfGst);
			invoice.setSgstAmount(halfGst);

			log.info("CGST/SGST applied for invoice {} | orgState == unitState | cgst={} sgst={}",
					invoice.getInvoiceNumber(), halfGst, halfGst);
		}

		// Optional: Generate QR code if amount > threshold
		// if (invoice.getGrandTotal().compareTo(new BigDecimal("200000")) > 0) {
		//     invoice.setSignedQrCode(generateEInvoiceQrCode(invoice));
		// }

		// Save and return
		invoice = invoiceRepository.save(invoice);

		log.info("Invoice generated: {} | grandTotal: ₹{} | lines: {} | for payment: {}",
				invoice.getInvoiceNumber(), invoice.getGrandTotal(),
				invoiceLines.size(), receipt.getId());

		return invoice;
	}


	private boolean isIgstApplicable(UnbilledInvoice unbilled) {
		Optional<Organization> organizationOpt = organizationRepository.findById(1L);

		if (organizationOpt.isEmpty()) {
			log.warn("Organization not found. Defaulting invoice {} to IGST logic.",
					unbilled.getUnbilledNumber());
			return true;
		}

		Organization organization = organizationOpt.get();

		String orgState = organization.getState();
		String unitState = unbilled.getUnit() != null ? unbilled.getUnit().getState() : null;

		if (orgState == null || orgState.trim().isEmpty()) {
			log.warn("Organization state is blank. Defaulting invoice {} to IGST logic.",
					unbilled.getUnbilledNumber());
			return true;
		}

		if (unitState == null || unitState.trim().isEmpty()) {
			log.warn("Company unit/state missing for unbilled {}. Defaulting to IGST logic.",
					unbilled.getUnbilledNumber());
			return true;
		}

		return !orgState.trim().equalsIgnoreCase(unitState.trim());
	}

	private BigDecimal safeMoney(BigDecimal value) {
		return value == null
				? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
				: value.setScale(2, RoundingMode.HALF_UP);
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

		Page<Invoice> pageResult = invoiceRepository.findInvoicesAndIsCancelledFalse(status, createdByIdFilter, pageable);

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

		Long paymentTypeId = null;
		String paymentTypeCode = null;

		/*
		 * Set paymentTypeId/paymentTypeCode only when unbilled has approved received amount.
		 * receivedAmount = approved payment amount
		 * currentReceivedAmount = pending payment amount
		 */
		if (unbilled != null
				&& unbilled.getReceivedAmount() != null
				&& unbilled.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0) {

			if (unbilled.getPayments() != null && !unbilled.getPayments().isEmpty()) {
				PaymentReceipt receipt = unbilled.getPayments().get(0);

				if (receipt.getPaymentType() != null) {
					paymentTypeId = receipt.getPaymentType().getId();
					paymentTypeCode = receipt.getPaymentType().getCode();
				}
			}
		}

		return InvoiceSummaryDto.builder()
				.id(inv.getId())
				.publicUuid(inv.getPublicUuid())
				.invoiceNumber(inv.getInvoiceNumber())
				.unbilledNumber(unbilled != null ? unbilled.getUnbilledNumber() : null)
				.estimateNumber(estimate != null ? estimate.getEstimateNumber() : null)
				.estimateId(estimate != null ? estimate.getId() : null)

				.paymentTypeId(paymentTypeId)
				.paymentTypeCode(paymentTypeCode)

				.solutionId(estimate != null ? estimate.getSolutionId() : null)
				.solutionName(estimate != null ? estimate.getSolutionName() : null)

				.companyName(unbilled != null && unbilled.getCompany() != null
						? unbilled.getCompany().getName()
						: null)

				.contactName(unbilled != null && unbilled.getContact() != null
						? unbilled.getContact().getName()
						: null)

				.invoiceDate(inv.getInvoiceDate())
				.grandTotal(inv.getGrandTotal())
				.totalGstAmount(inv.getTotalGstAmount())
				.cgstAmount(inv.getCgstAmount())
				.sgstAmount(inv.getSgstAmount())
				.igstAmount(inv.getIgstAmount())
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



	@Override
	@Transactional(readOnly = true)
	public InvoiceReportDto invoiceReport(InvoiceSearchRequest request) {

		CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    /* ============================================================
       1️⃣ INVOICE AGGREGATION (Revenue Side)
       ============================================================ */

		CriteriaQuery<Tuple> invoiceQuery = cb.createTupleQuery();
		Root<Invoice> invoiceRoot = invoiceQuery.from(Invoice.class);
		Join<Invoice, UnbilledInvoice> invoiceUnbilled = invoiceRoot.join("unbilledInvoice", JoinType.LEFT);
		Join<UnbilledInvoice, Company> invoiceCompany = invoiceUnbilled.join("company", JoinType.LEFT);

		List<Predicate> invoicePredicates = buildPredicates(
				request, cb, invoiceRoot, invoiceUnbilled, invoiceCompany
		);

		Expression<Long> totalInvoicesExp = cb.count(invoiceRoot.get("id"));
		Expression<BigDecimal> totalRevenueExp =
				cb.coalesce(cb.sum(invoiceRoot.get("grandTotal")), BigDecimal.ZERO);
		Expression<BigDecimal> totalNetRevenueExp =
				cb.coalesce(cb.sum(invoiceRoot.get("subTotalExGst")), BigDecimal.ZERO);
		Expression<BigDecimal> totalGstExp =
				cb.coalesce(cb.sum(invoiceRoot.get("totalGstAmount")), BigDecimal.ZERO);

		Expression<BigDecimal> totalIgstExp =
				cb.coalesce(cb.sum(invoiceRoot.get("igstAmount")), BigDecimal.ZERO);
		Expression<BigDecimal> totalSgstExp =
				cb.coalesce(cb.sum(invoiceRoot.get("sgstAmount")), BigDecimal.ZERO);
		Expression<BigDecimal> totalCgstExp =
				cb.coalesce(cb.sum(invoiceRoot.get("cgstAmount")), BigDecimal.ZERO);

		invoiceQuery.multiselect(
				totalInvoicesExp.alias("totalInvoices"),
				totalRevenueExp.alias("totalRevenue"),
				totalNetRevenueExp.alias("totalNetRevenue"),
				totalGstExp.alias("totalGstCollected"),
				totalIgstExp.alias("totalIgstCollectedAmount"),
				totalSgstExp.alias("totalSgstCollectedAmount"),
				totalCgstExp.alias("totalCgstCollectedAmount")
		);

		invoiceQuery.where(cb.and(invoicePredicates.toArray(new Predicate[0])));

		Tuple invoiceResult = entityManager.createQuery(invoiceQuery).getSingleResult();

		Long totalInvoices = invoiceResult.get("totalInvoices", Long.class);
		BigDecimal totalRevenue = nz(invoiceResult.get("totalRevenue", BigDecimal.class));
		BigDecimal totalNetRevenue = nz(invoiceResult.get("totalNetRevenue", BigDecimal.class));
		BigDecimal totalGst = nz(invoiceResult.get("totalGstCollected", BigDecimal.class));

    /* ============================================================
       2️⃣ UNBILLED AGGREGATION (Due Side) – DISTINCT SAFE
       ============================================================ */

		CriteriaQuery<Tuple> unbilledQuery = cb.createTupleQuery();
		Root<UnbilledInvoice> unbilledRoot = unbilledQuery.from(UnbilledInvoice.class);
		Join<UnbilledInvoice, Invoice> invoiceJoin = unbilledRoot.join("taxInvoices", JoinType.LEFT);
		Join<UnbilledInvoice, Company> companyJoin = unbilledRoot.join("company", JoinType.LEFT);

		List<Predicate> unbilledPredicates = buildUnbilledPredicates(
				request, cb, unbilledRoot, invoiceJoin, companyJoin
		);

		Expression<BigDecimal> totalUnbilledExp =
				cb.coalesce(cb.sum(unbilledRoot.get("totalAmount")), BigDecimal.ZERO);
		Expression<BigDecimal> totalReceivedExp =
				cb.coalesce(cb.sum(unbilledRoot.get("receivedAmount")), BigDecimal.ZERO);
		Expression<BigDecimal> totalOutstandingExp =
				cb.coalesce(cb.sum(unbilledRoot.get("outstandingAmount")), BigDecimal.ZERO);

		unbilledQuery.multiselect(
				totalUnbilledExp.alias("totalUnbilledAmount"),

				totalReceivedExp.alias("totalReceivedAmount"),
				totalOutstandingExp.alias("totalOutstandingAmount")
		);

		unbilledQuery.where(cb.and(unbilledPredicates.toArray(new Predicate[0])));

		Tuple unbilledResult = entityManager.createQuery(unbilledQuery).getSingleResult();

		BigDecimal totalUnbilled = nz(unbilledResult.get("totalUnbilledAmount", BigDecimal.class));
		BigDecimal totalReceived = nz(unbilledResult.get("totalReceivedAmount", BigDecimal.class));
		BigDecimal totalOutstanding = nz(unbilledResult.get("totalOutstandingAmount", BigDecimal.class));

    /* ============================================================
       3️⃣ CALCULATE AVERAGE (BigDecimal SAFE)
       ============================================================ */

		BigDecimal averageInvoiceValue = (totalInvoices == null || totalInvoices == 0)
				? BigDecimal.ZERO
				: totalRevenue.divide(BigDecimal.valueOf(totalInvoices), 8, RoundingMode.HALF_UP);

		BigDecimal totalIgstCollectedAmount = nz(invoiceResult.get("totalIgstCollectedAmount", BigDecimal.class));
		BigDecimal totalSgstCollectedAmount = nz(invoiceResult.get("totalSgstCollectedAmount", BigDecimal.class));
		BigDecimal totalCgstCollectedAmount = nz(invoiceResult.get("totalCgstCollectedAmount", BigDecimal.class));

    /* ============================================================
       4️⃣ BUILD RESPONSE
       ============================================================ */

		return InvoiceReportDto.builder()
				.totalInvoices(totalInvoices == null ? 0L : totalInvoices)
				.totalRevenue(totalRevenue)
				.totalNetRevenue(totalNetRevenue)
				.totalGstCollected(totalGst)
				.averageInvoiceValue(averageInvoiceValue)
				.totalUnbilledAmount(totalUnbilled)
				.totalReceivedAmount(totalReceived)
				.totalOutstandingAmount(totalOutstanding)
				.totalIgstCollectedAmount(totalIgstCollectedAmount)
				.totalSgstCollectedAmount(totalSgstCollectedAmount)
				.totalCgstCollectedAmount(totalCgstCollectedAmount)
				.build();
	}

	private List<Predicate> buildPredicates(
			InvoiceSearchRequest request,
			CriteriaBuilder cb,
			Root<Invoice> root,
			Join<Invoice, UnbilledInvoice> unbilled,
			Join<UnbilledInvoice, Company> company
	) {

		List<Predicate> predicates = new ArrayList<>();

		if (request.getCreatedById() != null) {
			predicates.add(cb.equal(root.get("createdBy").get("id"), request.getCreatedById()));
		}

		if (request.getStatus() != null) {
			predicates.add(cb.equal(root.get("status"), request.getStatus()));
		}

		if (request.getFromInvoiceDate() != null) {
			predicates.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), request.getFromInvoiceDate()));
		}

		if (request.getToInvoiceDate() != null) {
			predicates.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), request.getToInvoiceDate()));
		}

		if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
			predicates.add(cb.like(
					cb.lower(company.get("name")),
					"%" + request.getCompanyName().toLowerCase() + "%"
			));
		}

		if (request.getSolutionId() != null) {
			predicates.add(cb.equal(root.get("solutionId"), request.getSolutionId()));
		}

		return predicates;
	}

	private List<Predicate> buildUnbilledPredicates(
			InvoiceSearchRequest request,
			CriteriaBuilder cb,
			Root<UnbilledInvoice> root,
			Join<UnbilledInvoice, Invoice> invoiceJoin,
			Join<UnbilledInvoice, Company> company
	) {

		List<Predicate> predicates = new ArrayList<>();

		if (request.getCreatedById() != null) {
			predicates.add(cb.equal(invoiceJoin.get("createdBy").get("id"), request.getCreatedById()));
		}

		if (request.getStatus() != null) {
			predicates.add(cb.equal(invoiceJoin.get("status"), request.getStatus()));
		}

		if (request.getFromInvoiceDate() != null) {
			predicates.add(cb.greaterThanOrEqualTo(invoiceJoin.get("invoiceDate"), request.getFromInvoiceDate()));
		}

		if (request.getToInvoiceDate() != null) {
			predicates.add(cb.lessThanOrEqualTo(invoiceJoin.get("invoiceDate"), request.getToInvoiceDate()));
		}

		if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
			predicates.add(cb.like(
					cb.lower(company.get("name")),
					"%" + request.getCompanyName().toLowerCase() + "%"
			));
		}

		if (request.getSolutionId() != null) {
			predicates.add(cb.equal(invoiceJoin.get("solutionId"), request.getSolutionId()));
		}

		return predicates;
	}
	private BigDecimal nz(BigDecimal val) {
		return val != null ? val : BigDecimal.ZERO;
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