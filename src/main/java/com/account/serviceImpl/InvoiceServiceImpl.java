package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateLineItem;
import com.account.dto.invoice.*;
import com.account.dto.taxation.*;
import com.account.exception.AccessDeniedException;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
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

		Organization organization = organizationRepository.findTopOrganization()
				.orElseThrow(() -> new ResourceNotFoundException(
						"Organization details not found. Please configure organization profile before generating invoice.",
						"ORGANIZATION_NOT_FOUND"
				));

		copyOrganizationDetailsToInvoice(invoice, organization);


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
		boolean igstApplicable = isIgstApplicable(unbilled, organization);
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

	private void copyOrganizationDetailsToInvoice(Invoice invoice, Organization organization) {
		if (invoice == null || organization == null) {
			return;
		}

		invoice.setOrganizationName(organization.getName());
		invoice.setOrganizationAddressLine1(organization.getAddressLine1());
		invoice.setOrganizationAddressLine2(organization.getAddressLine2());
		invoice.setOrganizationCity(organization.getCity());
		invoice.setOrganizationState(organization.getState());
		invoice.setOrganizationCountry(organization.getCountry());
		invoice.setOrganizationPinCode(organization.getPinCode());
		invoice.setOrganizationGstNo(organization.getGstNo());
		invoice.setOrganizationPanNo(organization.getPanNo());
		invoice.setOrganizationCinNumber(organization.getCinNumber());
		invoice.setOrganizationEmail(organization.getEmail());
		invoice.setOrganizationPhone(organization.getPhone());
		invoice.setOrganizationWebsite(organization.getWebsite());
		invoice.setOrganizationLogoUrl(organization.getLogoUrl());
	}


	private boolean isIgstApplicable(UnbilledInvoice unbilled, Organization organization) {
		if (organization == null) {
			log.warn("Organization not found. Defaulting invoice {} to IGST logic.",
					unbilled.getUnbilledNumber());
			return true;
		}

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

				.organizationName(inv.getOrganizationName())
				.organizationAddressLine1(inv.getOrganizationAddressLine1())
				.organizationAddressLine2(inv.getOrganizationAddressLine2())
				.organizationCity(inv.getOrganizationCity())
				.organizationState(inv.getOrganizationState())
				.organizationCountry(inv.getOrganizationCountry())
				.organizationPinCode(inv.getOrganizationPinCode())
				.organizationGstNo(inv.getOrganizationGstNo())
				.organizationPanNo(inv.getOrganizationPanNo())
				.organizationCinNumber(inv.getOrganizationCinNumber())
				.organizationEmail(inv.getOrganizationEmail())
				.organizationPhone(inv.getOrganizationPhone())
				.organizationWebsite(inv.getOrganizationWebsite())
				.organizationLogoUrl(inv.getOrganizationLogoUrl())

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
	public InvoiceDetailDto getInvoiceByInvoiceNumber(String invoiceNumber, Long requestingUserId) {
		log.info("Fetching invoice by invoiceNumber: {} | requestedByUser={}", invoiceNumber, requestingUserId);

		if (requestingUserId == null || requestingUserId <= 0) {
			throw new ValidationException("Invalid requestingUserId", "ERR_INVALID_REQUESTING_USER", "requestingUserId");
		}

		if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
			throw new ValidationException("Invoice number is required", "ERR_INVALID_INVOICE_NUMBER", "invoiceNumber");
		}

		// Validate user exists
		if (!userRepository.existsById(requestingUserId)) {
			throw new ResourceNotFoundException("User not found", "USER_NOT_FOUND");
		}

		Invoice invoice = invoiceRepository
				.findByInvoiceNumberAndIsCancelledFalse(invoiceNumber.trim())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Invoice not found with number: " + invoiceNumber,
						"INVOICE_NOT_FOUND"
				));

		// Security check: Only creator can view
		if (invoice.getCreatedBy() == null ||
				!invoice.getCreatedBy().getId().equals(requestingUserId)) {
			throw new AccessDeniedException(
					"You are not authorized to view this invoice",
					"ACCESS_DENIED_INVOICE"
			);
		}

		log.info("Invoice found | number={} | id={}", invoice.getInvoiceNumber(), invoice.getId());

		return toDetailDto(invoice);
	}


	@Override
	@Transactional(readOnly = true)
	public InvoiceReportDto invoiceReport(InvoiceSearchRequest request) {

		CriteriaBuilder cb = entityManager.getCriteriaBuilder();


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

		dto.setOrganizationName(invoice.getOrganizationName());
		dto.setOrganizationAddressLine1(invoice.getOrganizationAddressLine1());
		dto.setOrganizationAddressLine2(invoice.getOrganizationAddressLine2());
		dto.setOrganizationCity(invoice.getOrganizationCity());
		dto.setOrganizationState(invoice.getOrganizationState());
		dto.setOrganizationCountry(invoice.getOrganizationCountry());
		dto.setOrganizationPinCode(invoice.getOrganizationPinCode());
		dto.setOrganizationGstNo(invoice.getOrganizationGstNo());
		dto.setOrganizationPanNo(invoice.getOrganizationPanNo());
		dto.setOrganizationCinNumber(invoice.getOrganizationCinNumber());
		dto.setOrganizationEmail(invoice.getOrganizationEmail());
		dto.setOrganizationPhone(invoice.getOrganizationPhone());
		dto.setOrganizationWebsite(invoice.getOrganizationWebsite());
		dto.setOrganizationLogoUrl(invoice.getOrganizationLogoUrl());

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

	@Override
	@Transactional(readOnly = true)
	public TaxationReportDto taxationReport(TaxationReportRequest request) {

		if (request == null) {
			throw new ValidationException(
					"Taxation report request is required",
					"ERR_TAXATION_REPORT_REQUEST_REQUIRED",
					"request"
			);
		}

		if (request.getType() == null) {
			throw new ValidationException(
					"Taxation report type is required. Allowed values: GST, TDS",
					"ERR_TAXATION_REPORT_TYPE_REQUIRED",
					"type"
			);
		}

		if (request.getType() == TaxationReportType.GST) {
			return TaxationReportDto.builder()
					.type(TaxationReportType.GST)
					.gstReport(buildGstTaxationReport(request))
					.tdsReport(null)
					.build();
		}

		if (request.getType() == TaxationReportType.TDS) {
			return TaxationReportDto.builder()
					.type(TaxationReportType.TDS)
					.gstReport(null)
					.tdsReport(buildTdsTaxationReport(request))
					.build();
		}

		throw new ValidationException(
				"Unsupported taxation report type: " + request.getType(),
				"ERR_UNSUPPORTED_TAXATION_REPORT_TYPE",
				"type"
		);
	}
	private GstTaxationReportDto buildGstTaxationReport(TaxationReportRequest request) {

		CriteriaBuilder cb = entityManager.getCriteriaBuilder();

		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<Invoice> invoiceRoot = query.from(Invoice.class);

		Join<Invoice, UnbilledInvoice> unbilledJoin =
				invoiceRoot.join("unbilledInvoice", JoinType.LEFT);

		Join<UnbilledInvoice, Company> companyJoin =
				unbilledJoin.join("company", JoinType.LEFT);

		List<Predicate> predicates = buildGstTaxationPredicates(
				request,
				cb,
				invoiceRoot,
				unbilledJoin,
				companyJoin
		);

		Expression<Long> totalInvoicesExp =
				cb.count(invoiceRoot.get("id"));

		Expression<BigDecimal> totalTaxableAmountExp =
				cb.coalesce(cb.sum(invoiceRoot.get("subTotalExGst")), BigDecimal.ZERO);

		Expression<BigDecimal> totalInvoiceAmountExp =
				cb.coalesce(cb.sum(invoiceRoot.get("grandTotal")), BigDecimal.ZERO);

		Expression<BigDecimal> totalGstCollectedExp =
				cb.coalesce(cb.sum(invoiceRoot.get("totalGstAmount")), BigDecimal.ZERO);

		Expression<BigDecimal> totalCgstCollectedExp =
				cb.coalesce(cb.sum(invoiceRoot.get("cgstAmount")), BigDecimal.ZERO);

		Expression<BigDecimal> totalSgstCollectedExp =
				cb.coalesce(cb.sum(invoiceRoot.get("sgstAmount")), BigDecimal.ZERO);

		Expression<BigDecimal> totalIgstCollectedExp =
				cb.coalesce(cb.sum(invoiceRoot.get("igstAmount")), BigDecimal.ZERO);

		query.multiselect(
				totalInvoicesExp.alias("totalInvoices"),
				totalTaxableAmountExp.alias("totalTaxableAmount"),
				totalInvoiceAmountExp.alias("totalInvoiceAmount"),
				totalGstCollectedExp.alias("totalGstCollected"),
				totalCgstCollectedExp.alias("totalCgstCollected"),
				totalSgstCollectedExp.alias("totalSgstCollected"),
				totalIgstCollectedExp.alias("totalIgstCollected")
		);

		query.where(cb.and(predicates.toArray(new Predicate[0])));

		Tuple result = entityManager.createQuery(query).getSingleResult();

		Long totalInvoices = result.get("totalInvoices", Long.class);

		BigDecimal totalTaxableAmount = safeMoney(result.get("totalTaxableAmount", BigDecimal.class));
		BigDecimal totalInvoiceAmount = safeMoney(result.get("totalInvoiceAmount", BigDecimal.class));
		BigDecimal totalGstCollected = safeMoney(result.get("totalGstCollected", BigDecimal.class));
		BigDecimal totalCgstCollected = safeMoney(result.get("totalCgstCollected", BigDecimal.class));
		BigDecimal totalSgstCollected = safeMoney(result.get("totalSgstCollected", BigDecimal.class));
		BigDecimal totalIgstCollected = safeMoney(result.get("totalIgstCollected", BigDecimal.class));

		BigDecimal averageGstPerInvoice =
				totalInvoices == null || totalInvoices == 0
						? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
						: totalGstCollected.divide(
						BigDecimal.valueOf(totalInvoices),
						2,
						RoundingMode.HALF_UP
				);

		BigDecimal averageInvoiceValue =
				totalInvoices == null || totalInvoices == 0
						? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
						: totalInvoiceAmount.divide(
						BigDecimal.valueOf(totalInvoices),
						2,
						RoundingMode.HALF_UP
				);

		return GstTaxationReportDto.builder()
				.totalInvoices(totalInvoices == null ? 0L : totalInvoices)
				.totalTaxableAmount(totalTaxableAmount)
				.totalInvoiceAmount(totalInvoiceAmount)
				.totalGstCollected(totalGstCollected)
				.totalCgstCollected(totalCgstCollected)
				.totalSgstCollected(totalSgstCollected)
				.totalIgstCollected(totalIgstCollected)
				.averageGstPerInvoice(averageGstPerInvoice)
				.averageInvoiceValue(averageInvoiceValue)
				.build();
	}

	private TdsTaxationReportDto buildTdsTaxationReport(TaxationReportRequest request) {

		CriteriaBuilder cb = entityManager.getCriteriaBuilder();

		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<TdsRegistration> tdsRoot = query.from(TdsRegistration.class);

		Join<TdsRegistration, Company> companyJoin =
				tdsRoot.join("company", JoinType.LEFT);

		Join<TdsRegistration, UnbilledInvoice> unbilledJoin =
				tdsRoot.join("unbilledInvoice", JoinType.LEFT);

		Join<TdsRegistration, Estimate> estimateJoin =
				tdsRoot.join("estimate", JoinType.LEFT);

		List<Predicate> predicates = buildTdsTaxationPredicates(
				request,
				cb,
				tdsRoot,
				companyJoin,
				unbilledJoin,
				estimateJoin
		);

		Expression<Long> totalTdsCountExp =
				cb.count(tdsRoot.get("id"));

		Expression<BigDecimal> totalTaxableAmountExp =
				cb.coalesce(cb.sum(tdsRoot.get("taxableAmount")), BigDecimal.ZERO);

		Expression<BigDecimal> totalTdsAmountExp =
				cb.coalesce(cb.sum(tdsRoot.get("tdsAmount")), BigDecimal.ZERO);

		Expression<BigDecimal> pendingTdsAmountExp =
				cb.coalesce(
						cb.sum(
								cb.<BigDecimal>selectCase()
										.when(
												cb.equal(tdsRoot.get("status"), TdsStatus.PENDING),
												tdsRoot.get("tdsAmount")
										)
										.otherwise(BigDecimal.ZERO)
						),
						BigDecimal.ZERO
				);

		Expression<BigDecimal> approvedTdsAmountExp =
				cb.coalesce(
						cb.sum(
								cb.<BigDecimal>selectCase()
										.when(
												cb.equal(tdsRoot.get("status"), TdsStatus.APPROVED),
												tdsRoot.get("tdsAmount")
										)
										.otherwise(BigDecimal.ZERO)
						),
						BigDecimal.ZERO
				);

		Expression<Long> pendingTdsCountExp =
				cb.sum(
						cb.<Long>selectCase()
								.when(cb.equal(tdsRoot.get("status"), TdsStatus.PENDING), 1L)
								.otherwise(0L)
				);

		Expression<Long> approvedTdsCountExp =
				cb.sum(
						cb.<Long>selectCase()
								.when(cb.equal(tdsRoot.get("status"), TdsStatus.APPROVED), 1L)
								.otherwise(0L)
				);

		query.multiselect(
				totalTdsCountExp.alias("totalTdsRegistrations"),
				totalTaxableAmountExp.alias("totalTaxableAmount"),
				totalTdsAmountExp.alias("totalTdsAmount"),
				pendingTdsAmountExp.alias("pendingTdsAmount"),
				approvedTdsAmountExp.alias("approvedTdsAmount"),
				pendingTdsCountExp.alias("pendingTdsCount"),
				approvedTdsCountExp.alias("approvedTdsCount")
		);

		query.where(cb.and(predicates.toArray(new Predicate[0])));

		Tuple result = entityManager.createQuery(query).getSingleResult();

		Long totalTdsRegistrations = result.get("totalTdsRegistrations", Long.class);

		BigDecimal totalTaxableAmount = safeMoney(result.get("totalTaxableAmount", BigDecimal.class));
		BigDecimal totalTdsAmount = safeMoney(result.get("totalTdsAmount", BigDecimal.class));
		BigDecimal pendingTdsAmount = safeMoney(result.get("pendingTdsAmount", BigDecimal.class));
		BigDecimal approvedTdsAmount = safeMoney(result.get("approvedTdsAmount", BigDecimal.class));

		Long pendingTdsCount = result.get("pendingTdsCount", Long.class);
		Long approvedTdsCount = result.get("approvedTdsCount", Long.class);

		BigDecimal averageTdsAmount =
				totalTdsRegistrations == null || totalTdsRegistrations == 0
						? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
						: totalTdsAmount.divide(
						BigDecimal.valueOf(totalTdsRegistrations),
						2,
						RoundingMode.HALF_UP
				);

		return TdsTaxationReportDto.builder()
				.totalTdsRegistrations(totalTdsRegistrations == null ? 0L : totalTdsRegistrations)
				.totalTaxableAmount(totalTaxableAmount)
				.totalTdsAmount(totalTdsAmount)
				.pendingTdsAmount(pendingTdsAmount)
				.approvedTdsAmount(approvedTdsAmount)
				.pendingTdsCount(pendingTdsCount == null ? 0L : pendingTdsCount)
				.approvedTdsCount(approvedTdsCount == null ? 0L : approvedTdsCount)
				.averageTdsAmount(averageTdsAmount)
				.build();
	}


	private List<Predicate> buildGstTaxationPredicates(
			TaxationReportRequest request,
			CriteriaBuilder cb,
			Root<Invoice> invoiceRoot,
			Join<Invoice, UnbilledInvoice> unbilledJoin,
			Join<UnbilledInvoice, Company> companyJoin
	) {
		List<Predicate> predicates = new ArrayList<>();

		predicates.add(cb.isFalse(invoiceRoot.get("isCancelled")));

		if (request.getCreatedById() != null) {
			predicates.add(cb.equal(invoiceRoot.get("createdBy").get("id"), request.getCreatedById()));
		}

		if (request.getStatus() != null) {
			predicates.add(cb.equal(invoiceRoot.get("status"), request.getStatus()));
		}

		if (request.getFromInvoiceDate() != null) {
			predicates.add(cb.greaterThanOrEqualTo(
					invoiceRoot.get("invoiceDate"),
					request.getFromInvoiceDate()
			));
		}

		if (request.getToInvoiceDate() != null) {
			predicates.add(cb.lessThanOrEqualTo(
					invoiceRoot.get("invoiceDate"),
					request.getToInvoiceDate()
			));
		}

		if (request.getFromCreatedDate() != null) {
			predicates.add(cb.greaterThanOrEqualTo(
					invoiceRoot.get("createdAt"),
					request.getFromCreatedDate().atStartOfDay()
			));
		}

		if (request.getToCreatedDate() != null) {
			predicates.add(cb.lessThanOrEqualTo(
					invoiceRoot.get("createdAt"),
					request.getToCreatedDate().atTime(23, 59, 59)
			));
		}

		if (request.getCompanyId() != null) {
			predicates.add(cb.equal(companyJoin.get("id"), request.getCompanyId()));
		}

		if (request.getCompanyName() != null && !request.getCompanyName().trim().isEmpty()) {
			predicates.add(cb.like(
					cb.lower(companyJoin.get("name")),
					"%" + request.getCompanyName().trim().toLowerCase() + "%"
			));
		}

		if (request.getSolutionId() != null) {
			predicates.add(cb.equal(invoiceRoot.get("solutionId"), request.getSolutionId()));
		}

		if (request.getMinAmount() != null) {
			predicates.add(cb.greaterThanOrEqualTo(
					invoiceRoot.get("grandTotal"),
					request.getMinAmount()
			));
		}

		if (request.getMaxAmount() != null) {
			predicates.add(cb.lessThanOrEqualTo(
					invoiceRoot.get("grandTotal"),
					request.getMaxAmount()
			));
		}

		if (Boolean.TRUE.equals(request.getIncludeGstOnly())) {
			predicates.add(cb.greaterThan(
					invoiceRoot.get("totalGstAmount"),
					BigDecimal.ZERO
			));
		}

		if (request.getCurrency() != null && !request.getCurrency().trim().isEmpty()) {
			predicates.add(cb.equal(
					cb.upper(invoiceRoot.get("currency")),
					request.getCurrency().trim().toUpperCase()
			));
		}

		if (Boolean.TRUE.equals(request.getOnlyWithOutstanding())) {
			predicates.add(cb.greaterThan(
					unbilledJoin.get("outstandingAmount"),
					BigDecimal.ZERO
			));
		}

		return predicates;
	}

	@Override
	@Transactional(readOnly = true)
	public List<InvoiceSummaryDto> getInvoicesByUnbilled(
			Long userId,
			Long unbilledId,
			int page,
			int size
	) {
		if (userId == null || userId <= 0) {
			throw new ValidationException(
					"Valid userId is required",
					"ERR_INVALID_USER_ID",
					"userId"
			);
		}

		User requestingUser = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"User not found with ID: " + userId,
						"USER_NOT_FOUND",
						"User",
						userId
				));

		boolean isAdmin = isAdminUser(requestingUser);

		Long visibleUserId = isAdmin ? null : userId;

		Pageable pageable = PageRequest.of(
				page,
				size,
				Sort.by(Sort.Direction.DESC, "createdAt")
		);

		Page<Invoice> pageResult = invoiceRepository.findInvoicesByUnbilledAndUserAccess(
				visibleUserId,
				unbilledId,
				pageable
		);

		return pageResult.getContent()
				.stream()
				.map(this::toSummaryDto)
				.collect(Collectors.toList());
	}


	@Override
	@Transactional(readOnly = true)
	public long countInvoicesByUnbilled(
			Long userId,
			Long unbilledId
	) {
		if (userId == null || userId <= 0) {
			throw new ValidationException(
					"Valid userId is required",
					"ERR_INVALID_USER_ID",
					"userId"
			);
		}

		User requestingUser = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"User not found with ID: " + userId,
						"USER_NOT_FOUND",
						"User",
						userId
				));

		boolean isAdmin = isAdminUser(requestingUser);

		Long visibleUserId = isAdmin ? null : userId;

		return invoiceRepository.countInvoicesByUnbilledAndUserAccess(
				visibleUserId,
				unbilledId
		);
	}


	private boolean isAdminUser(User user) {
		return user.getUserRole() != null
				&& user.getUserRole()
				.stream()
				.anyMatch(role ->
						role != null
								&& role.getName() != null
								&& "ADMIN".equalsIgnoreCase(role.getName())
				);
	}

	private List<Predicate> buildTdsTaxationPredicates(
			TaxationReportRequest request,
			CriteriaBuilder cb,
			Root<TdsRegistration> tdsRoot,
			Join<TdsRegistration, Company> companyJoin,
			Join<TdsRegistration, UnbilledInvoice> unbilledJoin,
			Join<TdsRegistration, Estimate> estimateJoin
	) {
		List<Predicate> predicates = new ArrayList<>();

		predicates.add(cb.isFalse(tdsRoot.get("isDeleted")));

		if (request.getCreatedById() != null) {
			predicates.add(cb.equal(tdsRoot.get("createdBy").get("id"), request.getCreatedById()));
		}

		if (request.getTdsStatus() != null) {
			predicates.add(cb.equal(tdsRoot.get("status"), request.getTdsStatus()));
		}

		/*
		 * For TDS, invoiceDate does not exist directly.
		 * So fromInvoiceDate/toInvoiceDate are treated as TDS createdAt date filters.
		 */
		if (request.getFromInvoiceDate() != null) {
			predicates.add(cb.greaterThanOrEqualTo(
					tdsRoot.get("createdAt"),
					request.getFromInvoiceDate().atStartOfDay()
			));
		}

		if (request.getToInvoiceDate() != null) {
			predicates.add(cb.lessThanOrEqualTo(
					tdsRoot.get("createdAt"),
					request.getToInvoiceDate().atTime(23, 59, 59)
			));
		}

		if (request.getFromCreatedDate() != null) {
			predicates.add(cb.greaterThanOrEqualTo(
					tdsRoot.get("createdAt"),
					request.getFromCreatedDate().atStartOfDay()
			));
		}

		if (request.getToCreatedDate() != null) {
			predicates.add(cb.lessThanOrEqualTo(
					tdsRoot.get("createdAt"),
					request.getToCreatedDate().atTime(23, 59, 59)
			));
		}

		if (request.getCompanyId() != null) {
			predicates.add(cb.equal(companyJoin.get("id"), request.getCompanyId()));
		}

		if (request.getCompanyName() != null && !request.getCompanyName().trim().isEmpty()) {
			predicates.add(cb.like(
					cb.lower(companyJoin.get("name")),
					"%" + request.getCompanyName().trim().toLowerCase() + "%"
			));
		}

		/*
		 * This assumes Estimate has solutionId.
		 * If your Estimate entity does not have solutionId, remove this block
		 * or replace it with the correct field.
		 */
		if (request.getSolutionId() != null) {
			predicates.add(cb.equal(estimateJoin.get("solutionId"), request.getSolutionId()));
		}

		if (request.getMinAmount() != null) {
			predicates.add(cb.greaterThanOrEqualTo(
					tdsRoot.get("tdsAmount"),
					request.getMinAmount()
			));
		}

		if (request.getMaxAmount() != null) {
			predicates.add(cb.lessThanOrEqualTo(
					tdsRoot.get("tdsAmount"),
					request.getMaxAmount()
			));
		}

		if (Boolean.TRUE.equals(request.getOnlyWithOutstanding())) {
			predicates.add(cb.greaterThan(
					unbilledJoin.get("outstandingAmount"),
					BigDecimal.ZERO
			));
		}

		return predicates;
	}

	@Override
	@Transactional(readOnly = true)
	public List<InvoiceSummaryDto> getInvoiceReport(
			Long userId,
			Long createdByUserId,
			InvoiceStatus status,
			LocalDate fromDate,
			LocalDate toDate
	) {
		if (userId == null || userId <= 0) {
			throw new ValidationException(
					"Valid userId is required",
					"ERR_INVALID_USER_ID",
					"userId"
			);
		}

		if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
			throw new IllegalArgumentException("fromDate cannot be after toDate");
		}

		Long visibleUserId = hasUnrestrictedInvoiceReportAccess(userId)
				? null
				: userId;

		List<Invoice> invoices = invoiceRepository.findInvoiceReport(
				visibleUserId,
				createdByUserId,
				status,
				fromDate,
				toDate
		);

		long totalCount = invoices.size();

		return invoices
				.stream()
				.map(invoice -> {
					InvoiceSummaryDto dto = toSummaryDto(invoice);
					dto.setSearchCount(totalCount);
					return dto;
				})
				.collect(Collectors.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public long getInvoiceReportCount(
			Long userId,
			Long createdByUserId,
			InvoiceStatus status,
			LocalDate fromDate,
			LocalDate toDate
	) {
		if (userId == null || userId <= 0) {
			throw new ValidationException(
					"Valid userId is required",
					"ERR_INVALID_USER_ID",
					"userId"
			);
		}

		if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
			throw new IllegalArgumentException("fromDate cannot be after toDate");
		}

		Long visibleUserId = hasUnrestrictedInvoiceReportAccess(userId)
				? null
				: userId;

		return invoiceRepository.countInvoiceReport(
				visibleUserId,
				createdByUserId,
				status,
				fromDate,
				toDate
		);
	}

	private boolean hasUnrestrictedInvoiceReportAccess(Long userId) {
		User user = userRepository.findById(userId).orElse(null);

		if (user == null || !user.isActive() || user.isDeleted()) {
			return false;
		}

		return belongsToAccountsDepartment(user) || isAdminUser(user);
	}

	private boolean belongsToAccountsDepartment(User user) {
		return user.getDepartment() != null
				&& (
				"ACCOUNT".equalsIgnoreCase(user.getDepartment().trim())
						|| "ACCOUNTS".equalsIgnoreCase(user.getDepartment().trim())
		);
	}



}