
package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.company.GstRegistrationType;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateLineItem;
import com.account.domain.invoice.Invoice;
import com.account.domain.invoice.InvoiceLineItem;
import com.account.domain.ledger.*;
import com.account.domain.status.InvoiceStatus;
import com.account.domain.status.PaymentStatus;
import com.account.domain.status.TdsStatus;
import com.account.domain.status.UnbilledStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.dto.invoice.*;
import com.account.dto.ledger.AccountingVoucherEntryRequestDto;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.dto.operationService.OperationProjectPaymentTransactionDto;
import com.account.dto.operationService.OperationProjectRequestDto;
import com.account.dto.operationService.OperationProjectResponseDto;
import com.account.dto.taxation.*;
import com.account.exception.AccessDeniedException;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.feignClient.OperationFeignClient;
import com.account.repository.InvoiceRepository;
import com.account.repository.OrganizationRepository;
import com.account.repository.UserRepository;
import com.account.repository.ledger.LedgerGroupRepository;
import com.account.repository.ledger.LedgerMasterRepository;
import com.account.service.InvoiceService;
import com.account.service.ledger.AccountingVoucherService;
import com.account.util.DateTimeUtil;
import feign.FeignException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

	private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

	private final InvoiceRepository invoiceRepository;
	private final UserRepository userRepository;
	private final DateTimeUtil dateTimeUtil;
	private final OperationFeignClient operationFeignClient;


	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private final OrganizationRepository organizationRepository;

	private final AccountingVoucherService accountingVoucherService;
	private final LedgerMasterRepository ledgerMasterRepository;
	private final LedgerGroupRepository ledgerGroupRepository;


	/**
	 * Generates a Tax Invoice for a specific payment receipt.
	 * The invoice grand total will be EXACTLY equal to receipt.getAmount()
	 * Line items are prorated, and any minor rounding difference is adjusted on the last line.
	 */
	@Override
	@Transactional
	public Invoice generateInvoiceForPayment(
			UnbilledInvoice unbilled,
			PaymentReceipt receipt,
			User approver,
			BigDecimal tdsAmount
	) {

		if (unbilled == null) {
			throw new IllegalStateException(
					"Unbilled invoice is required for invoice generation"
			);
		}

		if (receipt == null) {
			throw new IllegalStateException(
					"Payment receipt is required for invoice generation"
			);
		}

		Estimate estimate = unbilled.getEstimate();

		if (estimate == null) {
			throw new IllegalStateException(
					"Estimate not found for Unbilled "
							+ unbilled.getUnbilledNumber()
			);
		}

		// =====================================================
		// GST REGISTRATION TYPE
		// =====================================================

		GstRegistrationType unbilledGstType =
				unbilled.getGstRegistrationType();

		GstRegistrationType unitGstType =
				unbilled.getUnit() != null
						? unbilled.getUnit().getGstRegistrationType()
						: null;

		/*
		 * INTERNATIONAL takes priority over an old/stale snapshot.
		 *
		 * Example:
		 * unbilled snapshot = REGISTERED
		 * current unit type = INTERNATIONAL
		 *
		 * The invoice must still be treated as INTERNATIONAL.
		 */
		boolean internationalTransaction =
				unbilledGstType == GstRegistrationType.INTERNATIONAL
						|| unitGstType == GstRegistrationType.INTERNATIONAL;

		GstRegistrationType gstRegistrationType =
				internationalTransaction
						? GstRegistrationType.INTERNATIONAL
						: unbilledGstType != null
						? unbilledGstType
						: unitGstType != null
						? unitGstType
						: GstRegistrationType.REGISTERED;

		boolean zeroRatedSupply =
				gstRegistrationType.isZeroRated();

		// =====================================================
		// BANK + TDS SETTLEMENT
		// =====================================================

		BigDecimal bankAmount = receipt.getAmount() == null
				? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
				: receipt.getAmount().setScale(2, RoundingMode.HALF_UP);

		BigDecimal requestedTdsAmount = tdsAmount == null
				? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
				: tdsAmount.setScale(2, RoundingMode.HALF_UP);

		/*
		 * INTERNATIONAL transactions must never include domestic TDS,
		 * even if a stale TDS value is passed by another internal caller.
		 */
		BigDecimal safeTdsAmount =
				internationalTransaction
						? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
						: requestedTdsAmount;

		/*
		 * Invoice settlement:
		 *
		 * Domestic/SEZ = Bank received + applicable TDS
		 * International = Bank received only
		 */
		BigDecimal exactGrandTotal = bankAmount
				.add(safeTdsAmount)
				.setScale(2, RoundingMode.HALF_UP);

		if (internationalTransaction
				&& requestedTdsAmount.compareTo(BigDecimal.ZERO) > 0) {

			log.warn(
					"Ignoring TDS for INTERNATIONAL invoice | receiptId={} | requestedTdsAmount={} | unbilled={}",
					receipt.getId(),
					requestedTdsAmount,
					unbilled.getUnbilledNumber()
			);
		}

		log.info(
				"Generating invoice | receiptId={} | gstRegistrationType={} | international={} | bankAmount={} | tdsAmount={} | grandTotal={} | unbilled={}",
				receipt.getId(),
				gstRegistrationType,
				internationalTransaction,
				bankAmount,
				safeTdsAmount,
				exactGrandTotal,
				unbilled.getUnbilledNumber()
		);

		// =====================================================
		// CREATE INVOICE
		// =====================================================

		Invoice invoice = new Invoice();

		invoice.setPublicUuid(UUID.randomUUID().toString());
		invoice.setInvoiceNumber(generateInvoiceNumber());

		invoice.setUnbilledInvoice(unbilled);
		invoice.setTriggeringPayment(receipt);

		invoice.setGstRegistrationType(gstRegistrationType);

		invoice.setInvoiceDate(LocalDate.now());
		invoice.setCurrency("INR");
		invoice.setStatus(InvoiceStatus.GENERATED);

		invoice.setCreatedBy(approver);
		invoice.setUpdatedBy(approver);
		invoice.setCreatedAt(LocalDateTime.now());

		invoice.setSolutionId(estimate.getSolutionId());
		invoice.setSolutionName(estimate.getSolutionName());

		Organization organization = organizationRepository
				.findTopOrganization()
				.orElseThrow(() -> new ResourceNotFoundException(
						"Organization details not found. Please configure organization profile before generating invoice.",
						"ORGANIZATION_NOT_FOUND"
				));

		copyOrganizationDetailsToInvoice(
				invoice,
				organization
		);

		/*
		 * SEZ and INTERNATIONAL are treated as IGST-classified
		 * zero-rated supplies.
		 */
		boolean igstApplicable =
				zeroRatedSupply
						|| isIgstApplicable(unbilled, organization);

		log.info(
				"Invoice GST treatment | unbilled={} | gstRegistrationType={} | zeroRated={} | igstApplicable={}",
				unbilled.getUnbilledNumber(),
				gstRegistrationType,
				zeroRatedSupply,
				igstApplicable
		);

		// =====================================================
		// BUYER GSTIN
		// =====================================================

		String buyerGstin = null;

		CompanyUnit unit = unbilled.getUnit();

		/*
		 * GSTIN is retained for:
		 * REGISTERED
		 * SEZ
		 *
		 * GSTIN is null for:
		 * UNREGISTERED
		 * INTERNATIONAL
		 */
		if (unit != null
				&& (
				gstRegistrationType == GstRegistrationType.REGISTERED
						|| gstRegistrationType == GstRegistrationType.SEZ
		)) {

			buyerGstin = unit.getGstNo();
		}

		invoice.setBuyerGstin(buyerGstin);
		invoice.setPlaceOfSupplyStateCode(
				estimate.getPlaceOfSupplyStateCode()
		);

		// =====================================================
		// CALCULATE PAYMENT RATIO
		// =====================================================

		BigDecimal totalUnbilled = unbilled.getTotalAmount() == null
				? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
				: unbilled.getTotalAmount()
				.setScale(2, RoundingMode.HALF_UP);

		BigDecimal ratio =
				totalUnbilled.compareTo(BigDecimal.ZERO) > 0
						? exactGrandTotal.divide(
						totalUnbilled,
						10,
						RoundingMode.HALF_UP
				)
						: BigDecimal.ZERO;

		List<EstimateLineItem> estimateLines =
				estimate.getLineItems();

		if (estimateLines == null || estimateLines.isEmpty()) {
			throw new IllegalStateException(
					"Estimate line items not found for estimate "
							+ estimate.getEstimateNumber()
			);
		}

		List<InvoiceLineItem> invoiceLines =
				new ArrayList<>();

		BigDecimal accumulatedWithGst =
				BigDecimal.ZERO.setScale(
						2,
						RoundingMode.HALF_UP
				);

		// =====================================================
		// CREATE INVOICE LINE ITEMS
		// =====================================================

		for (EstimateLineItem estimateLine : estimateLines) {

			InvoiceLineItem invoiceLine =
					new InvoiceLineItem();

			invoiceLine.setInvoice(invoice);

			invoiceLine.setSourceEstimateLineItemId(
					estimateLine.getId()
			);

			invoiceLine.setItemName(
					estimateLine.getItemName()
			);

			invoiceLine.setDescription(
					estimateLine.getDescription()
			);

			invoiceLine.setHsnSacCode(
					estimateLine.getHsnSacCode()
			);

			invoiceLine.setQuantity(
					estimateLine.getQuantity()
			);

			invoiceLine.setUnit(
					estimateLine.getUnit()
			);

			BigDecimal estimateUnitPrice =
					estimateLine.getUnitPriceExGst() != null
							? estimateLine.getUnitPriceExGst()
							: BigDecimal.ZERO;

			BigDecimal proratedUnitPrice =
					estimateUnitPrice
							.multiply(ratio)
							.setScale(
									2,
									RoundingMode.HALF_UP
							);

			invoiceLine.setUnitPriceExGst(
					proratedUnitPrice
			);

			/*
			 * Defensive enforcement:
			 *
			 * SEZ and INTERNATIONAL must always have zero GST,
			 * even if old estimate data contains 18%.
			 */
			BigDecimal effectiveGstRate =
					zeroRatedSupply
							? BigDecimal.ZERO
							: estimateLine.getGstRate() != null
							? estimateLine.getGstRate()
							: BigDecimal.ZERO;

			invoiceLine.setGstRate(
					effectiveGstRate.setScale(
							2,
							RoundingMode.HALF_UP
					)
			);

			/*
			 * Required for line-level GST breakup.
			 */
			invoiceLine.setIgstFlag(
					igstApplicable
			);

			invoiceLine.setDisplayOrder(
					estimateLine.getDisplayOrder()
			);

			invoiceLine.setCategoryCode(
					estimateLine.getCategoryCode()
			);

			invoiceLine.setFeeType(
					estimateLine.getFeeType()
			);

			invoiceLine.calculateLineTotals();

			invoiceLines.add(invoiceLine);

			accumulatedWithGst =
					accumulatedWithGst
							.add(invoiceLine.getLineTotalWithGst())
							.setScale(
									2,
									RoundingMode.HALF_UP
							);
		}

		// =====================================================
		// ROUNDING ADJUSTMENT
		// =====================================================

		BigDecimal difference =
				exactGrandTotal
						.subtract(accumulatedWithGst)
						.setScale(
								2,
								RoundingMode.HALF_UP
						);

		if (difference.abs().compareTo(
				new BigDecimal("1.00")
		) > 0) {

			log.warn(
					"Large invoice proration difference | difference={} | receiptId={}",
					difference,
					receipt.getId()
			);
		}

		if (difference.compareTo(BigDecimal.ZERO) != 0
				&& !invoiceLines.isEmpty()) {

			InvoiceLineItem lastLine =
					invoiceLines.get(
							invoiceLines.size() - 1
					);

			BigDecimal currentLineTotalWithGst =
					lastLine.getLineTotalWithGst() != null
							? lastLine.getLineTotalWithGst()
							.setScale(
									2,
									RoundingMode.HALF_UP
							)
							: BigDecimal.ZERO.setScale(
							2,
							RoundingMode.HALF_UP
					);

			BigDecimal targetLineTotalWithGst =
					currentLineTotalWithGst
							.add(difference)
							.setScale(
									2,
									RoundingMode.HALF_UP
							);

			BigDecimal lastLineGstRate =
					lastLine.getGstRate() != null
							? lastLine.getGstRate()
							: BigDecimal.ZERO;

			BigDecimal divisor =
					BigDecimal.ONE.add(
							lastLineGstRate.divide(
									BigDecimal.valueOf(100),
									6,
									RoundingMode.HALF_UP
							)
					);

			BigDecimal targetLineTotalExGst =
					targetLineTotalWithGst.divide(
							divisor,
							2,
							RoundingMode.HALF_UP
					);

			Integer quantity =
					lastLine.getQuantity() != null
							&& lastLine.getQuantity() > 0
							? lastLine.getQuantity()
							: 1;

			BigDecimal adjustedUnitPrice =
					targetLineTotalExGst.divide(
							BigDecimal.valueOf(quantity),
							2,
							RoundingMode.HALF_UP
					);

			lastLine.setUnitPriceExGst(
					adjustedUnitPrice
			);

			lastLine.calculateLineTotals();
		}

		invoice.setLineItems(invoiceLines);

		// =====================================================
		// HEADER TOTALS
		// =====================================================

		BigDecimal subTotalExGst =
				invoiceLines.stream()
						.map(InvoiceLineItem::getLineTotalExGst)
						.filter(Objects::nonNull)
						.reduce(
								BigDecimal.ZERO,
								BigDecimal::add
						)
						.setScale(
								2,
								RoundingMode.HALF_UP
						);

		BigDecimal totalGstAmount =
				invoiceLines.stream()
						.map(InvoiceLineItem::getGstAmount)
						.filter(Objects::nonNull)
						.reduce(
								BigDecimal.ZERO,
								BigDecimal::add
						)
						.setScale(
								2,
								RoundingMode.HALF_UP
						);

		invoice.setSubTotalExGst(
				subTotalExGst
		);

		invoice.setTotalGstAmount(
				totalGstAmount
		);

		BigDecimal totalGst =
				safeMoney(totalGstAmount);

		// =====================================================
		// HEADER GST BREAKUP
		// =====================================================

		if (zeroRatedSupply
				|| totalGst.compareTo(BigDecimal.ZERO) == 0) {

			invoice.setTotalGstAmount(
					BigDecimal.ZERO.setScale(
							2,
							RoundingMode.HALF_UP
					)
			);

			invoice.setCgstAmount(
					BigDecimal.ZERO.setScale(
							2,
							RoundingMode.HALF_UP
					)
			);

			invoice.setSgstAmount(
					BigDecimal.ZERO.setScale(
							2,
							RoundingMode.HALF_UP
					)
			);

			invoice.setIgstAmount(
					BigDecimal.ZERO.setScale(
							2,
							RoundingMode.HALF_UP
					)
			);

		} else if (igstApplicable) {

			invoice.setIgstAmount(totalGst);

			invoice.setCgstAmount(
					BigDecimal.ZERO.setScale(
							2,
							RoundingMode.HALF_UP
					)
			);

			invoice.setSgstAmount(
					BigDecimal.ZERO.setScale(
							2,
							RoundingMode.HALF_UP
					)
			);

		} else {

			BigDecimal cgstAmount =
					totalGst.divide(
							BigDecimal.valueOf(2),
							2,
							RoundingMode.HALF_UP
					);

			BigDecimal sgstAmount =
					totalGst
							.subtract(cgstAmount)
							.setScale(
									2,
									RoundingMode.HALF_UP
							);

			invoice.setCgstAmount(cgstAmount);
			invoice.setSgstAmount(sgstAmount);

			invoice.setIgstAmount(
					BigDecimal.ZERO.setScale(
							2,
							RoundingMode.HALF_UP
					)
			);
		}

		/*
		 * Grand total must exactly match settlement:
		 *
		 * Bank amount + TDS amount.
		 */
		invoice.setGrandTotal(
				exactGrandTotal
		);

		Invoice savedInvoice =
				invoiceRepository.save(invoice);

// =====================================================
// PROJECT CREATION FLOW BASED ON GST TYPE
// =====================================================

		if (gstRegistrationType
				.requiresEInvoiceConfirmation()) {

			/*
			 * REGISTERED and SEZ:
			 *
			 * Do not post the Sales Invoice voucher here.
			 * Do not create the Operation project here.
			 *
			 * Both actions will happen only after:
			 *
			 * POST /{invoiceId}/confirm-e-invoice
			 */
			savedInvoice.setStatus(
					InvoiceStatus.GENERATED
			);

			savedInvoice.setOperationSynced(false);
			savedInvoice.setOperationSyncedAt(null);

			savedInvoice =
					invoiceRepository.save(savedInvoice);

			log.info(
					"Invoice generated and waiting for e-invoice confirmation "
							+ "| invoice={} | gstRegistrationType={} "
							+ "| unbilled={}",
					savedInvoice.getInvoiceNumber(),
					gstRegistrationType,
					unbilled.getUnbilledNumber()
			);

		} else {

			/*
			 * UNREGISTERED and INTERNATIONAL:
			 *
			 * E-invoice confirmation is not applicable.
			 * Post Sales Invoice voucher and create/sync
			 * the Operation project immediately.
			 */
			postSalesInvoiceVoucher(
					savedInvoice,
					unbilled,
					approver
			);

			createOrSyncOperationProject(
					savedInvoice,
					approver
			);

			savedInvoice.setOperationSynced(true);
			savedInvoice.setOperationSyncedAt(
					LocalDateTime.now()
			);

			savedInvoice.setStatus(
					InvoiceStatus.FINALIZED_WITHOUT_E_INVOICE
			);

			savedInvoice =
					invoiceRepository.save(savedInvoice);

			log.info(
					"Invoice finalized without e-invoice and "
							+ "Operation project synced "
							+ "| invoice={} | gstRegistrationType={} "
							+ "| unbilled={}",
					savedInvoice.getInvoiceNumber(),
					gstRegistrationType,
					unbilled.getUnbilledNumber()
			);
		}

		log.info(
				"Invoice processing completed | invoice={} "
						+ "| gstRegistrationType={} | status={} "
						+ "| operationSynced={} | taxable={} "
						+ "| gst={} | grandTotal={} | lines={}",
				savedInvoice.getInvoiceNumber(),
				savedInvoice.getGstRegistrationType(),
				savedInvoice.getStatus(),
				savedInvoice.isOperationSynced(),
				savedInvoice.getSubTotalExGst(),
				savedInvoice.getTotalGstAmount(),
				savedInvoice.getGrandTotal(),
				invoiceLines.size()
		);

		return savedInvoice;
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


	private boolean isIgstApplicable(
			UnbilledInvoice unbilled,
			Organization organization
	) {

		GstRegistrationType gstRegistrationType =
				unbilled != null
						? unbilled.getEffectiveGstRegistrationType()
						: GstRegistrationType.REGISTERED;

		/*
		 * SEZ supplies and international/export supplies are
		 * classified as interstate/IGST supplies.
		 *
		 * Their GST amount remains zero because gstRate is forced to 0.
		 */
		if (gstRegistrationType == GstRegistrationType.SEZ
				|| gstRegistrationType == GstRegistrationType.INTERNATIONAL) {
			return true;
		}

		if (organization == null) {
			return true;
		}

		String organizationState =
				organization.getState();

		String unitState =
				unbilled != null
						&& unbilled.getUnit() != null
						? unbilled.getUnit().getState()
						: null;

		if (organizationState == null
				|| organizationState.trim().isEmpty()) {
			return true;
		}

		if (unitState == null
				|| unitState.trim().isEmpty()) {
			return true;
		}

		return !organizationState
				.trim()
				.equalsIgnoreCase(
						unitState.trim()
				);
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
		Estimate estimate = unbilled != null ? unbilled.getEstimate() : null;

		PaymentReceipt receipt = inv.getTriggeringPayment();

		Long paymentTypeId = null;
		String paymentTypeCode = null;

		if (receipt != null && receipt.getPaymentType() != null) {
			paymentTypeId = receipt.getPaymentType().getId();
			paymentTypeCode = receipt.getPaymentType().getCode();
		}

		GstRegistrationType gstRegistrationType =
				inv.getGstRegistrationType() != null
						? inv.getGstRegistrationType()
						: GstRegistrationType.REGISTERED;

		return InvoiceSummaryDto.builder()
				.id(inv.getId())
				.publicUuid(inv.getPublicUuid())
				.invoiceNumber(inv.getInvoiceNumber())

				.unbilledNumber(unbilled != null ? unbilled.getUnbilledNumber() : null)

				.estimateNumber(estimate != null ? estimate.getEstimateNumber() : null)
				.estimateId(estimate != null ? estimate.getId() : null)

				.paymentTypeId(paymentTypeId)
				.paymentTypeCode(paymentTypeCode)

				.solutionId(inv.getSolutionId())
				.solutionName(inv.getSolutionName())

				// Not available in pasted entity classes
				.solutionType(null)

				.companyName(unbilled != null && unbilled.getCompany() != null
						? unbilled.getCompany().getName()
						: null)

				.contactName(unbilled != null && unbilled.getContact() != null
						? unbilled.getContact().getName()
						: null)

				.gstRegistrationType(
						gstRegistrationType.name()
				)
				.gstApplicable(
						gstRegistrationType.isGstApplicable()
				)
				.zeroRatedSupply(
						gstRegistrationType.isZeroRated()
				)
				.invoiceDate(inv.getInvoiceDate())
				.grandTotal(inv.getGrandTotal())
				.totalGstAmount(inv.getTotalGstAmount())
				.cgstAmount(inv.getCgstAmount())
				.sgstAmount(inv.getSgstAmount())
				.igstAmount(inv.getIgstAmount())

				.irn(inv.getEInvoiceIrn())
				.status(inv.getStatus())

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


				.createdByName(inv.getCreatedBy() != null
						? inv.getCreatedBy().getFullName() != null
						? inv.getCreatedBy().getFullName()
						: inv.getCreatedBy().getEmail()
						: null)

				.createdAt(inv.getCreatedAt())
				.sentAt(inv.getEInvoiceConfirmedAt())

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

		GstRegistrationType gstRegistrationType =
				invoice.getGstRegistrationType() != null
						? invoice.getGstRegistrationType()
						: GstRegistrationType.REGISTERED;

		dto.setGstRegistrationType(
				gstRegistrationType.name()
		);

		dto.setGstApplicable(
				gstRegistrationType.isGstApplicable()
		);

		dto.setZeroRatedSupply(
				gstRegistrationType.isZeroRated()
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
		lineDto.setIgstFlag(
				li.isIgstFlag()
		);
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


	@Override
	@Transactional
	public InvoiceDetailDto confirmEInvoiceAndCreateProject(
			Long invoiceId,
			ConfirmInvoiceEInvoiceRequestDto request
	) {

		// =====================================================
		// 1. BASIC VALIDATION
		// =====================================================
		if (invoiceId == null) {
			throw new ValidationException(
					"Invoice ID is required",
					"ERR_INVOICE_ID_REQUIRED",
					"invoiceId"
			);
		}

		if (request == null) {
			throw new ValidationException(
					"E-invoice confirmation request is required",
					"ERR_E_INVOICE_REQUEST_REQUIRED",
					"request"
			);
		}

		if (request.getUserId() == null) {
			throw new ValidationException(
					"User ID is required",
					"ERR_USER_ID_REQUIRED",
					"userId"
			);
		}

		// =====================================================
		// 2. FETCH USER
		// =====================================================
		User confirmedBy =
				userRepository.findById(request.getUserId())
						.orElseThrow(() ->
								new ResourceNotFoundException(
										"User not found with ID: "
												+ request.getUserId(),
										"USER_NOT_FOUND",
										"User",
										request.getUserId()
								)
						);

		// =====================================================
		// 3. FETCH INVOICE
		// =====================================================
		Invoice invoice =
				invoiceRepository.findById(invoiceId)
						.orElseThrow(() ->
								new ResourceNotFoundException(
										"Invoice not found with ID: "
												+ invoiceId,
										"INVOICE_NOT_FOUND",
										"Invoice",
										invoiceId
								)
						);

		if (invoice.isCancelled()
				|| invoice.getStatus() == InvoiceStatus.CANCELLED) {

			throw new ValidationException(
					"Cancelled invoice cannot be confirmed",
					"ERR_CANCELLED_INVOICE_CONFIRM_NOT_ALLOWED",
					"invoiceId"
			);
		}

		if (invoice.getStatus()
				== InvoiceStatus.E_INVOICE_CONFIRMED) {

			throw new ValidationException(
					"GST e-invoice is already confirmed for this invoice",
					"ERR_E_INVOICE_ALREADY_CONFIRMED",
					"invoiceId"
			);
		}

		if (invoice.getStatus()
				== InvoiceStatus.FINALIZED_WITHOUT_E_INVOICE) {

			throw new ValidationException(
					"This invoice has already been finalized without e-invoice confirmation",
					"ERR_INVOICE_ALREADY_FINALIZED",
					"invoiceId"
			);
		}

		// =====================================================
		// 4. VALIDATE UNBILLED
		// =====================================================
		UnbilledInvoice unbilled =
				invoice.getUnbilledInvoice();

		if (unbilled == null) {
			throw new ValidationException(
					"Invoice is not linked with an unbilled invoice",
					"ERR_UNBILLED_NOT_LINKED",
					"invoiceId"
			);
		}

		if (unbilled.getStatus()
				!= UnbilledStatus.APPROVED) {

			throw new ValidationException(
					"GST e-invoice can be confirmed only after unbilled invoice approval",
					"ERR_UNBILLED_NOT_APPROVED",
					"unbilledId"
			);
		}

		// =====================================================
		// 5. GST REGISTRATION TYPE VALIDATION
		// =====================================================
		GstRegistrationType gstRegistrationType =
				resolveInvoiceGstRegistrationType(invoice);

		/*
		 * Only REGISTERED and SEZ invoices are allowed
		 * through the e-invoice confirmation endpoint.
		 */
		if (!gstRegistrationType
				.allowsEInvoiceConfirmation()) {

			throw new ValidationException(
					"GST e-invoice confirmation is not applicable for "
							+ gstRegistrationType
							+ " invoices. The Operation project must be "
							+ "created directly after invoice generation.",
					"ERR_E_INVOICE_NOT_ALLOWED_FOR_GST_TYPE",
					"invoiceId"
			);
		}

		// =====================================================
		// 6. REQUIRED E-INVOICE FIELDS
		// =====================================================
		if (!hasText(request.getEInvoiceAttachmentUrl())) {
			throw new ValidationException(
					"GST e-invoice attachment is required for "
							+ gstRegistrationType + " invoices",
					"ERR_E_INVOICE_ATTACHMENT_REQUIRED",
					"eInvoiceAttachmentUrl"
			);
		}

		if (!hasText(request.getEInvoiceIrn())) {
			throw new ValidationException(
					"GST e-invoice IRN is required for "
							+ gstRegistrationType + " invoices",
					"ERR_E_INVOICE_IRN_REQUIRED",
					"eInvoiceIrn"
			);
		}

		// =====================================================
		// 7. SAVE E-INVOICE DETAILS
		// =====================================================
		invoice.setEInvoiceAttachmentUrl(
				request.getEInvoiceAttachmentUrl().trim()
		);

		invoice.setEInvoiceIrn(
				request.getEInvoiceIrn().trim()
		);

		invoice.setEInvoiceAckNo(
				hasText(request.getEInvoiceAckNo())
						? request.getEInvoiceAckNo().trim()
						: null
		);

		invoice.setEInvoiceAckDate(
				request.getEInvoiceAckDate()
		);

		invoice.setEInvoiceConfirmedAt(
				LocalDateTime.now()
		);

		invoice.setEInvoiceConfirmedBy(
				confirmedBy
		);

		invoice.setUpdatedBy(
				confirmedBy
		);

		invoice.setStatus(
				InvoiceStatus.E_INVOICE_CONFIRMED
		);

		// Save confirmation before downstream processing.
		invoice = invoiceRepository.save(invoice);

		// =====================================================
		// 8. POST SALES INVOICE VOUCHER
		// =====================================================
		postSalesInvoiceVoucher(
				invoice,
				unbilled,
				confirmedBy
		);

		// =====================================================
		// 9. CREATE OR SYNC OPERATION PROJECT
		// =====================================================
		createOrSyncOperationProject(
				invoice,
				confirmedBy
		);

		invoice.setOperationSynced(true);
		invoice.setOperationSyncedAt(
				LocalDateTime.now()
		);

		Invoice savedInvoice =
				invoiceRepository.save(invoice);

		log.info(
				"GST e-invoice confirmed and Operation project synced "
						+ "| invoice={} | gstRegistrationType={} "
						+ "| unbilled={}",
				savedInvoice.getInvoiceNumber(),
				gstRegistrationType,
				unbilled.getUnbilledNumber()
		);

		return toDetailDto(savedInvoice);
	}

	private boolean hasText(String value) {
		return value != null
				&& !value.trim().isEmpty();
	}


	private GstRegistrationType resolveInvoiceGstRegistrationType(
			Invoice invoice
	) {
		if (invoice == null) {
			return GstRegistrationType.REGISTERED;
		}

		/*
		 * Invoice snapshot has first priority.
		 */
		if (invoice.getGstRegistrationType() != null) {
			return invoice.getGstRegistrationType();
		}

		UnbilledInvoice unbilled =
				invoice.getUnbilledInvoice();

		if (unbilled != null
				&& unbilled.getGstRegistrationType() != null) {

			return unbilled.getGstRegistrationType();
		}

		if (unbilled != null
				&& unbilled.getUnit() != null
				&& unbilled.getUnit().getGstRegistrationType() != null) {

			return unbilled
					.getUnit()
					.getGstRegistrationType();
		}

		return GstRegistrationType.REGISTERED;
	}

	private void createOrSyncOperationProject(
			Invoice invoice,
			User confirmedBy
	) {
		UnbilledInvoice unbilled = invoice.getUnbilledInvoice();
		Estimate estimate = unbilled.getEstimate();

		try {
			ResponseEntity<OperationProjectResponseDto> res =
					operationFeignClient.getProjectByUnbilledNumber(unbilled.getUnbilledNumber());

			if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
				OperationProjectResponseDto project = res.getBody();

				log.info(
						"Operation project already exists. Syncing payment | invoice={} | projectId={}",
						invoice.getInvoiceNumber(),
						project.getId()
				);

				syncPaymentToExistingOperationProject(unbilled, project, confirmedBy);

				invoice.setOperationProjectNo(project.getProjectNo());
				return;
			}

		} catch (FeignException ex) {
			if (ex.status() == 404) {
				log.info(
						"Operation project not found. Creating project after GST e-invoice confirmation | invoice={} | unbilled={}",
						invoice.getInvoiceNumber(),
						unbilled.getUnbilledNumber()
				);

				OperationProjectRequestDto projectDto =
						buildOperationProjectRequestDto(invoice, unbilled, estimate, confirmedBy);

				operationFeignClient.createProject(projectDto);

				invoice.setOperationProjectNo(projectDto.getProjectNo());

				log.info(
						"Operation project created successfully | projectNo={} | invoice={}",
						projectDto.getProjectNo(),
						invoice.getInvoiceNumber()
				);

				return;
			}

			log.error(
					"Operation service error while project sync | invoice={} | unbilled={} | status={} | message={}",
					invoice.getInvoiceNumber(),
					unbilled.getUnbilledNumber(),
					ex.status(),
					ex.getMessage()
			);

			throw ex;
		}
	}

	private void syncPaymentToExistingOperationProject(
			UnbilledInvoice unbilled,
			OperationProjectResponseDto project,
			User confirmedBy
	) {
		double accountReceived = unbilled.getReceivedAmount() != null
				? unbilled.getReceivedAmount().doubleValue()
				: 0.0;

		double operationPaid = project.getTotalAmount() - project.getDueAmount();

		double newPayment = accountReceived - operationPaid;

		if (newPayment <= 0) {
			log.info(
					"No new payment to sync | unbilled={} | accountReceived={} | operationPaid={}",
					unbilled.getUnbilledNumber(),
					accountReceived,
					operationPaid
			);
			return;
		}

		OperationProjectPaymentTransactionDto dto = new OperationProjectPaymentTransactionDto();
		dto.setAmount(newPayment);
		dto.setPaymentDate(new Date());
		dto.setCreatedBy(confirmedBy.getId());

		operationFeignClient.addPaymentTransaction(project.getUnbilledNumber(), dto);

		log.info(
				"Payment synced to existing Operation project | unbilled={} | amount={}",
				unbilled.getUnbilledNumber(),
				newPayment
		);
	}

	private OperationProjectRequestDto buildOperationProjectRequestDto(
			Invoice invoice,
			UnbilledInvoice unbilled,
			Estimate estimate,
			User confirmedBy
	) {
		PaymentReceipt receipt = invoice.getTriggeringPayment();

		if (receipt == null && unbilled.getPayments() != null) {
			receipt = unbilled.getPayments()
					.stream()
					.filter(p -> p.getStatus() == PaymentStatus.APPROVED)
					.filter(p -> !p.isCancelled())
					.max(
							Comparator
									.comparing(
											PaymentReceipt::getCreatedAt,
											Comparator.nullsFirst(Comparator.naturalOrder())
									)
									.thenComparing(
											PaymentReceipt::getId,
											Comparator.nullsFirst(Comparator.naturalOrder())
									)
					)
					.orElse(null);
		}

		OperationProjectRequestDto projectDto = new OperationProjectRequestDto();

		projectDto.setName(
				estimate != null && estimate.getSolutionName() != null
						? estimate.getSolutionName()
						: (
						unbilled.getCompany() != null
								? unbilled.getCompany().getName() + " - Project"
								: "Unnamed Project"
				)
		);

		projectDto.setProjectNo(generateProjectNumberForOperation());

		projectDto.setSalesPersonId(
				unbilled.getCreatedBy() != null ? unbilled.getCreatedBy().getId() : null
		);

		projectDto.setSalesPersonName(
				unbilled.getCreatedBy() != null
						? (
						unbilled.getCreatedBy().getFullName() != null
								? unbilled.getCreatedBy().getFullName()
								: unbilled.getCreatedBy().getEmail()
				)
						: null
		);

		projectDto.setProductId(estimate != null ? estimate.getSolutionId() : null);
		projectDto.setCompanyId(
				unbilled.getCompany() != null ? unbilled.getCompany().getId() : null
		);

		projectDto.setUnitId(
				unbilled.getUnit() != null ? unbilled.getUnit().getId() : null
		);

		projectDto.setUnbilledNumber(unbilled.getUnbilledNumber());
		projectDto.setEstimateNumber(
				estimate != null ? estimate.getEstimateNumber() : null
		);

		projectDto.setContactId(
				unbilled.getContact() != null ? unbilled.getContact().getId() : null
		);

		projectDto.setLeadId(estimate != null ? estimate.getLeadId() : null);

		projectDto.setDate(LocalDate.now());

		projectDto.setTotalAmount(
				unbilled.getTotalAmount() != null
						? unbilled.getTotalAmount().doubleValue()
						: 0.0
		);

		projectDto.setPaidAmount(
				unbilled.getReceivedAmount() != null
						? unbilled.getReceivedAmount().doubleValue()
						: 0.0
		);

		projectDto.setPaymentTypeId(
				receipt != null && receipt.getPaymentType() != null
						? receipt.getPaymentType().getId()
						: null
		);

		projectDto.setApprovedById(confirmedBy.getId());

		projectDto.setCreatedBy(
				unbilled.getCreatedBy() != null ? unbilled.getCreatedBy().getId() : confirmedBy.getId()
		);

		projectDto.setUpdatedBy(confirmedBy.getId());

		return projectDto;
	}

	private String generateProjectNumberForOperation() {
		String dateTimePart = LocalDateTime.now()
				.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

		long count = invoiceRepository.count() + 1;
		String sequence = String.format("%04d", count);

		return "PRJ-" + dateTimePart + "-" + sequence;
	}

	private void postSalesInvoiceVoucher(
			Invoice invoice,
			UnbilledInvoice unbilled,
			User approver
	) {
		if (invoice == null || invoice.getId() == null) {
			throw new ValidationException(
					"Saved invoice is required to post sales invoice voucher",
					"ERR_INVOICE_REQUIRED_FOR_VOUCHER",
					"invoice"
			);
		}

		if (unbilled == null) {
			throw new ValidationException(
					"Unbilled invoice is required to post sales invoice voucher",
					"ERR_UNBILLED_REQUIRED_FOR_VOUCHER",
					"unbilled"
			);
		}

		if (accountingVoucherService.existsPostedVoucher(
				VoucherType.SALES_INVOICE,
				VoucherSourceType.INVOICE,
				invoice.getId()
		)) {
			log.info(
					"Sales invoice voucher already posted. Skipping duplicate voucher creation | invoice={}",
					invoice.getInvoiceNumber()
			);
			return;
		}
		BigDecimal grandTotal = safeMoney(invoice.getGrandTotal());

		if (grandTotal.compareTo(BigDecimal.ZERO) <= 0) {
			log.info("Skipping sales invoice voucher for zero amount invoice: {}", invoice.getInvoiceNumber());
			return;
		}


		LedgerMaster customerLedger = getOrCreateCustomerLedger(unbilled, approver);

		LedgerMaster serviceIncomeLedger = getOrCreateSystemLedger(
				LedgerType.SERVICE_INCOME,
				LedgerGroupType.SALES_ACCOUNTS,
				"Service Income",
				DebitCredit.CREDIT,
				approver
		);

		LedgerMaster outputCgstLedger = getOrCreateSystemLedger(
				LedgerType.OUTPUT_CGST,
				LedgerGroupType.DUTIES_AND_TAXES,
				"Output CGST",
				DebitCredit.CREDIT,
				approver
		);

		LedgerMaster outputSgstLedger = getOrCreateSystemLedger(
				LedgerType.OUTPUT_SGST,
				LedgerGroupType.DUTIES_AND_TAXES,
				"Output SGST",
				DebitCredit.CREDIT,
				approver
		);

		LedgerMaster outputIgstLedger = getOrCreateSystemLedger(
				LedgerType.OUTPUT_IGST,
				LedgerGroupType.DUTIES_AND_TAXES,
				"Output IGST",
				DebitCredit.CREDIT,
				approver
		);

		BigDecimal cgstAmount = safeMoney(invoice.getCgstAmount());
		BigDecimal sgstAmount = safeMoney(invoice.getSgstAmount());
		BigDecimal igstAmount = safeMoney(invoice.getIgstAmount());

		/*
		 * Keep voucher balanced.
		 * Sales amount = grand total - GST ledgers.
		 */
		BigDecimal totalTaxAmount = cgstAmount
				.add(sgstAmount)
				.add(igstAmount)
				.setScale(2, RoundingMode.HALF_UP);

		BigDecimal serviceIncomeAmount = grandTotal
				.subtract(totalTaxAmount)
				.max(BigDecimal.ZERO)
				.setScale(2, RoundingMode.HALF_UP);

		List<AccountingVoucherEntryRequestDto> entries = new ArrayList<>();

		// Dr Customer Advance Ledger
		entries.add(
				buildVoucherEntry(
						customerLedger.getId(),
						grandTotal,
						BigDecimal.ZERO,
						"Customer ledger adjusted against invoice " + invoice.getInvoiceNumber()
				)
		);

		// Cr Service Income Ledger
		if (serviceIncomeAmount.compareTo(BigDecimal.ZERO) > 0) {
			entries.add(
					buildVoucherEntry(
							serviceIncomeLedger.getId(),
							BigDecimal.ZERO,
							serviceIncomeAmount,
							"Service income booked for invoice " + invoice.getInvoiceNumber()
					)
			);
		}

		// Cr Output CGST
		if (cgstAmount.compareTo(BigDecimal.ZERO) > 0) {
			entries.add(
					buildVoucherEntry(
							outputCgstLedger.getId(),
							BigDecimal.ZERO,
							cgstAmount,
							"Output CGST booked for invoice " + invoice.getInvoiceNumber()
					)
			);
		}

		// Cr Output SGST
		if (sgstAmount.compareTo(BigDecimal.ZERO) > 0) {
			entries.add(
					buildVoucherEntry(
							outputSgstLedger.getId(),
							BigDecimal.ZERO,
							sgstAmount,
							"Output SGST booked for invoice " + invoice.getInvoiceNumber()
					)
			);
		}

		// Cr Output IGST
		if (igstAmount.compareTo(BigDecimal.ZERO) > 0) {
			entries.add(
					buildVoucherEntry(
							outputIgstLedger.getId(),
							BigDecimal.ZERO,
							igstAmount,
							"Output IGST booked for invoice " + invoice.getInvoiceNumber()
					)
			);
		}

		AccountingVoucherRequestDto voucherRequest =
				AccountingVoucherRequestDto.builder()
						.voucherType(VoucherType.SALES_INVOICE)
						.voucherDate(invoice.getInvoiceDate() != null
								? invoice.getInvoiceDate()
								: LocalDate.now())
						.sourceType(VoucherSourceType.INVOICE)
						.sourceId(invoice.getId())
						.narration(
								"Sales invoice posted: "
										+ invoice.getInvoiceNumber()
										+ ", unbilled: "
										+ unbilled.getUnbilledNumber()
						)
						.entries(entries)
						.build();

		accountingVoucherService.createVoucher(voucherRequest);

		log.info(
				"Sales invoice voucher posted | invoice={} | Dr Customer Advance={} | Cr Service Income={} | CGST={} | SGST={} | IGST={}",
				invoice.getInvoiceNumber(),
				grandTotal,
				serviceIncomeAmount,
				cgstAmount,
				sgstAmount,
				igstAmount
		);
	}

	private AccountingVoucherEntryRequestDto buildVoucherEntry(
			Long ledgerId,
			BigDecimal debitAmount,
			BigDecimal creditAmount,
			String narration
	) {
		return AccountingVoucherEntryRequestDto.builder()
				.ledgerId(ledgerId)
				.debitAmount(safeMoney(debitAmount))
				.creditAmount(safeMoney(creditAmount))
				.narration(narration)
				.build();
	}


	private LedgerMaster getOrCreateSystemLedger(
			LedgerType ledgerType,
			LedgerGroupType ledgerGroupType,
			String ledgerName,
			DebitCredit balanceType,
			User createdBy
	) {
		Optional<LedgerMaster> existingLedger =
				ledgerMasterRepository.findByLedgerTypeAndDeletedFalse(ledgerType);

		if (existingLedger.isPresent()) {
			return existingLedger.get();
		}

		LedgerGroup ledgerGroup = ledgerGroupRepository
				.findByGroupTypeAndDeletedFalse(ledgerGroupType)
				.orElseThrow(() -> new ResourceNotFoundException(
						ledgerGroupType + " ledger group not found",
						ledgerGroupType + "_GROUP_NOT_FOUND"
				));

		LedgerMaster ledger = new LedgerMaster();

		ledger.setLedgerName(ledgerName);
		ledger.setLedgerCode(generateLedgerCode(ledgerType.name()));
		ledger.setLedgerType(ledgerType);
		ledger.setLedgerGroup(ledgerGroup);

		ledger.setOpeningBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
		ledger.setOpeningBalanceType(balanceType);

		ledger.setCurrentBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
		ledger.setCurrentBalanceType(balanceType);

		ledger.setSystemCreated(true);
		ledger.setActive(true);
		ledger.setDeleted(false);

		if (createdBy != null && createdBy.getId() != null) {
			ledger.setCreatedBy(createdBy);
			ledger.setUpdatedBy(createdBy);
		}

		return ledgerMasterRepository.save(ledger);
	}

	private String generateLedgerCode(String prefix) {
		String safePrefix = prefix == null || prefix.trim().isEmpty()
				? "SYS"
				: prefix.trim().replaceAll("[^A-Za-z0-9]", "-").toUpperCase();

		long sequence = ledgerMasterRepository.count() + 1;
		String ledgerCode;

		do {
			ledgerCode = String.format("LED-%s-%06d", safePrefix, sequence++);
		} while (ledgerMasterRepository.existsByLedgerCodeIgnoreCase(ledgerCode));

		return ledgerCode;
	}

	private LedgerMaster getOrCreateCustomerLedger(
			UnbilledInvoice unbilled,
			User createdBy
	) {
		if (unbilled == null) {
			throw new ValidationException(
					"Unbilled invoice is required to create customer ledger",
					"ERR_UNBILLED_REQUIRED_FOR_LEDGER",
					"unbilled"
			);
		}

		Company company = unbilled.getCompany();
		CompanyUnit unit = unbilled.getUnit();
		Contact contact = unbilled.getContact();

		if (company == null || company.getId() == null) {
			throw new ValidationException(
					"Company is required to create customer ledger",
					"ERR_COMPANY_REQUIRED_FOR_LEDGER",
					"companyId"
			);
		}

		Long companyId = company.getId();

		LedgerGroup sundryDebtorsGroup = ledgerGroupRepository
				.findByGroupTypeAndDeletedFalse(LedgerGroupType.SUNDRY_DEBTORS)
				.orElseThrow(() -> new ResourceNotFoundException(
						"Sundry Debtors ledger group not found",
						"SUNDRY_DEBTORS_GROUP_NOT_FOUND"
				));

		String companyName = company.getName() != null && !company.getName().trim().isEmpty()
				? company.getName().trim()
				: "Company-" + companyId;

		/*
		 * Only ONE ledger per company.
		 *
		 * If old CUSTOMER_ADVANCE ledger already exists,
		 * reuse it and convert it to CUSTOMER / SUNDRY_DEBTORS.
		 */
		List<LedgerMaster> existingLedgers =
				ledgerMasterRepository.findByCompanyIdAndLedgerTypeInAndDeletedFalse(
						companyId,
						List.of(
								LedgerType.CUSTOMER,
								LedgerType.CUSTOMER_ADVANCE
						)
				);

		if (existingLedgers != null && !existingLedgers.isEmpty()) {
			LedgerMaster ledger = existingLedgers.get(0);

			ledger.setLedgerType(LedgerType.CUSTOMER);
			ledger.setLedgerGroup(sundryDebtorsGroup);

			/*
			 * Ledger name should be company name only.
			 * Example: Nestle
			 */
			if (!ledgerMasterRepository.existsByLedgerNameIgnoreCaseAndIdNot(
					companyName,
					ledger.getId()
			)) {
				ledger.setLedgerName(companyName);
			}

			ledger.setCompany(company);

			if (unit != null && unit.getId() != null) {
				ledger.setUnit(unit);
				ledger.setGstNo(unit.getGstNo());
			}

			if (contact != null && contact.getId() != null) {
				ledger.setContact(contact);
			}

			ledger.setPanNo(company.getPanNo());
			ledger.setSystemCreated(true);
			ledger.setActive(true);
			ledger.setDeleted(false);

			if (createdBy != null && createdBy.getId() != null) {
				ledger.setUpdatedBy(createdBy);
			}

			return ledgerMasterRepository.save(ledger);
		}

		/*
		 * No existing CUSTOMER / CUSTOMER_ADVANCE ledger found,
		 * so create only one company ledger.
		 */
		LedgerMaster ledger = new LedgerMaster();

		ledger.setLedgerName(companyName);
		ledger.setLedgerCode(generateLedgerCode("CUST"));

		ledger.setLedgerType(LedgerType.CUSTOMER);
		ledger.setLedgerGroup(sundryDebtorsGroup);

		ledger.setCompany(company);

		if (unit != null && unit.getId() != null) {
			ledger.setUnit(unit);
			ledger.setGstNo(unit.getGstNo());
		}

		if (contact != null && contact.getId() != null) {
			ledger.setContact(contact);
		}

		ledger.setPanNo(company.getPanNo());

		ledger.setOpeningBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
		ledger.setOpeningBalanceType(DebitCredit.DEBIT);

		ledger.setCurrentBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
		ledger.setCurrentBalanceType(DebitCredit.DEBIT);

		ledger.setSystemCreated(true);
		ledger.setActive(true);
		ledger.setDeleted(false);

		if (createdBy != null && createdBy.getId() != null) {
			ledger.setCreatedBy(createdBy);
			ledger.setUpdatedBy(createdBy);
		}

		return ledgerMasterRepository.save(ledger);
	}




}