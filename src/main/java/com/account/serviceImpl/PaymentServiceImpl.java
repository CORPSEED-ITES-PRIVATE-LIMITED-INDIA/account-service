
package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.company.GstRegistrationType;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateStatus;
import com.account.domain.invoice.Invoice;
import com.account.domain.invoice.InvoiceOrigin;
import com.account.domain.invoice.InvoicePaymentStatus;
import com.account.domain.ledger.*;
import com.account.domain.status.*;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.dto.ledger.AccountingVoucherEntryRequestDto;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.dto.operationService.*;
import com.account.dto.payment.*;
import com.account.dto.unbilled.UnbilledInvoiceApprovalRequestDto;
import com.account.dto.unbilled.UnbilledInvoiceApprovalResponseDto;
import com.account.dto.unbilled.UnbilledInvoiceDetailDto;
import com.account.dto.unbilled.UnbilledInvoiceSummaryDto;
import com.account.exception.AccessDeniedException;
import com.account.exception.ApprovalBlockedException;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.feignClient.OperationFeignClient;
import com.account.notification.NotificationPublisherService;
import com.account.notification.dto.NotificationCreateRequestDto;
import com.account.notification.dto.NotificationPriority;
import com.account.repository.*;
import com.account.repository.ledger.LedgerGroupRepository;
import com.account.repository.ledger.LedgerMasterRepository;
import com.account.service.InvoiceService;
import com.account.service.PaymentLegalVerificationService;
import com.account.service.PaymentService;
import com.account.service.ledger.AccountingVoucherService;
import com.account.util.DateTimeUtil;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final EstimateRepository estimateRepository;
    private final UnbilledInvoiceRepository unbilledInvoiceRepository;
    private final PaymentReceiptRepository paymentReceiptRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;
    private final DateTimeUtil dateTimeUtil;
    private final OperationFeignClient operationFeignClient;
    private final InvoiceRepository invoiceRepository;
    private final GovernmentFeeRepository governmentFeeRepository;
    private final TdsRegistrationRepository tdsRegistrationRepository;
    private final NotificationPublisherService notificationPublisherService;
    private final LedgerMasterRepository ledgerMasterRepository;
    private final AccountingVoucherService accountingVoucherService;
    private final LedgerGroupRepository ledgerGroupRepository;
    private final PaymentLegalVerificationService paymentLegalVerificationService;

    public PaymentServiceImpl(
            EstimateRepository estimateRepository,
            UnbilledInvoiceRepository unbilledInvoiceRepository,
            PaymentReceiptRepository paymentReceiptRepository,
            PaymentTypeRepository paymentTypeRepository,
            UserRepository userRepository,
            InvoiceService invoiceService,
            DateTimeUtil dateTimeUtil,
            OperationFeignClient operationFeignClient,
            InvoiceRepository invoiceRepository,
            GovernmentFeeRepository governmentFeeRepository,
            TdsRegistrationRepository tdsRegistrationRepository,
            NotificationPublisherService notificationPublisherService,
            LedgerMasterRepository ledgerMasterRepository,
            AccountingVoucherService accountingVoucherService,
            LedgerGroupRepository ledgerGroupRepository,
            PaymentLegalVerificationService paymentLegalVerificationService
    ) {
        this.estimateRepository = estimateRepository;
        this.unbilledInvoiceRepository = unbilledInvoiceRepository;
        this.paymentReceiptRepository = paymentReceiptRepository;
        this.paymentTypeRepository = paymentTypeRepository;
        this.userRepository = userRepository;
        this.invoiceService = invoiceService;
        this.dateTimeUtil = dateTimeUtil;
        this.operationFeignClient = operationFeignClient;
        this.invoiceRepository = invoiceRepository;
        this.governmentFeeRepository = governmentFeeRepository;
        this.tdsRegistrationRepository = tdsRegistrationRepository;
        this.notificationPublisherService = notificationPublisherService;
        this.ledgerMasterRepository = ledgerMasterRepository;
        this.accountingVoucherService = accountingVoucherService;
        this.ledgerGroupRepository = ledgerGroupRepository;
        this.paymentLegalVerificationService = paymentLegalVerificationService;
    }





    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRegistrationResponseDto registerPayment(
            PaymentRegistrationRequestDto request,
            Long salespersonUserId
    ) {

        if (request == null) {
            throw new ValidationException(
                    "Payment registration request is required",
                    "ERR_PAYMENT_REQUEST_REQUIRED",
                    "request"
            );
        }

        if (request.getEstimateId() == null) {
            throw new ValidationException(
                    "Estimate ID is required",
                    "ERR_ESTIMATE_ID_REQUIRED",
                    "estimateId"
            );
        }

        if (request.getPaymentTypeId() == null) {
            throw new ValidationException(
                    "Payment type ID is required",
                    "ERR_PAYMENT_TYPE_REQUIRED",
                    "paymentTypeId"
            );
        }

        if (salespersonUserId == null) {
            throw new ValidationException(
                    "Salesperson user ID is required",
                    "ERR_SALESPERSON_REQUIRED",
                    "salespersonUserId"
            );
        }

        log.info(
                "Registering payment | estimateId: {}, amount: {}, mode: {}, ref: {}, salespersonId: {}",
                request.getEstimateId(),
                request.getAmount(),
                request.getPaymentMode(),
                request.getTransactionReference(),
                salespersonUserId
        );

        // =====================================================
        // 1. BASIC VALIDATIONS
        // =====================================================
        if (request.getAmount() == null) {
            throw new ValidationException(
                    "Payment amount is required",
                    "ERR_AMOUNT_REQUIRED",
                    "amount"
            );
        }

        BigDecimal reqAmount = request.getAmount()
                .setScale(2, RoundingMode.HALF_UP);

        // =====================================================
        // 2. GOVERNMENT FEE VALIDATION
        // =====================================================
        validateGovernmentFeeRequest(request);

        // =====================================================
        // 3. FETCH AND VALIDATE ESTIMATE
        // =====================================================
        Estimate estimate = estimateRepository.findById(request.getEstimateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Estimate not found with ID: " + request.getEstimateId(),
                        "ESTIMATE_NOT_FOUND",
                        "Estimate",
                        request.getEstimateId()
                ));

        if (estimate.getStatus() == EstimateStatus.REJECTED) {
            throw new ValidationException(
                    "Cannot register payment against a REJECTED estimate. "
                            + "Estimate "
                            + estimate.getEstimateNumber()
                            + " has been rejected.",
                    "ERR_PAYMENT_ON_REJECTED_ESTIMATE",
                    "estimateId"
            );
        }

        // =====================================================
        // 4. COMPANY & UNIT APPROVAL CHECK
        // =====================================================
        Company company = estimate.getCompany();
        CompanyUnit unit = estimate.getUnit();

        // =====================================================
        // INTERNATIONAL TRANSACTION CHECK
        // =====================================================
        GstRegistrationType paymentGstRegistrationType =
                resolveGstRegistrationType(estimate, null);

        boolean internationalTransaction =
                paymentGstRegistrationType
                        == GstRegistrationType.INTERNATIONAL;

        /*
         * INTERNATIONAL transactions cannot use the domestic TDS workflow.
         *
         * This validation happens before:
         * - TDS calculation
         * - PaymentReceipt creation
         * - TdsRegistration creation
         * - Unbilled amount update
         */
        validateInternationalTdsRestriction(
                request,
                estimate,
                null
        );

        log.info(
                "Payment GST type resolved | estimateId={} | unitId={} | gstRegistrationType={} | international={}",
                estimate.getId(),
                unit != null ? unit.getId() : null,
                paymentGstRegistrationType,
                internationalTransaction
        );

        boolean companyApproved =
                isCompanyApprovedForPayment(company);

        boolean unitApproved =
                isUnitApprovedForPayment(unit);

        if (!companyApproved || !unitApproved) {

            String companyName =
                    company != null && company.getName() != null
                            ? company.getName()
                            : "N/A";

            String companyStatus =
                    company != null
                            && company.getOnboardingStatus() != null
                            ? company.getOnboardingStatus().name()
                            : "N/A";

            String unitName =
                    unit != null && unit.getUnitName() != null
                            ? unit.getUnitName()
                            : "N/A";

            String unitStatus =
                    unit != null
                            && unit.getOnboardingStatus() != null
                            ? unit.getOnboardingStatus().name()
                            : "N/A";

            throw new ValidationException(
                    "Payment registration is not allowed because Company and Company Unit "
                            + "must both be approved by Accounts. "
                            + "Company: " + companyName
                            + ", Company Status: " + companyStatus
                            + ", Company Approved: " + companyApproved
                            + ", Unit: " + unitName
                            + ", Unit Status: " + unitStatus
                            + ", Unit Approved: " + unitApproved,
                    "ERR_COMPANY_OR_UNIT_NOT_APPROVED_FOR_PAYMENT",
                    !companyApproved ? "companyId" : "unitId"
            );
        }

        // =====================================================
        // 5. FETCH SALESPERSON AND PAYMENT TYPE
        // =====================================================
        User salesperson = userRepository.findById(salespersonUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Salesperson not found with ID: " + salespersonUserId,
                        "USER_NOT_FOUND",
                        "User",
                        salespersonUserId
                ));

        PaymentType paymentType =
                paymentTypeRepository
                        .findById(request.getPaymentTypeId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Payment type not found with ID: "
                                        + request.getPaymentTypeId(),
                                "PAYMENT_TYPE_NOT_FOUND",
                                "PaymentType",
                                request.getPaymentTypeId()
                        ));

        String paymentTypeCode =
                paymentType.getCode() != null
                        ? paymentType.getCode()
                        .trim()
                        .toUpperCase()
                        : "";

        boolean isPurchaseOrder =
                "PURCHASE_ORDER".equals(paymentTypeCode);

        boolean isZeroAmountPurchaseOrder =
                isPurchaseOrder
                        && reqAmount.compareTo(BigDecimal.ZERO) == 0;

        /*
         * Validate the Purchase Order attachment before creating:
         * - UnbilledInvoice
         * - PaymentReceipt
         * - Legal verification request
         *
         * Non-PURCHASE_ORDER flows are unaffected.
         */
        validatePurchaseOrderRequest(
                request,
                isPurchaseOrder
        );

        // =====================================================
        // 6. VALIDATE BANK LEDGER
        // =====================================================
        LedgerMaster bankLedger =
                validateAndGetBankLedger(request, reqAmount);

        // Negative amount is never allowed.
        if (reqAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException(
                    "Payment amount cannot be negative",
                    "ERR_AMOUNT_NEGATIVE",
                    "amount"
            );
        }

        // Zero amount is allowed only for PURCHASE_ORDER.
        if (!isPurchaseOrder
                && reqAmount.compareTo(BigDecimal.ZERO) == 0) {

            throw new ValidationException(
                    "Payment amount must be greater than zero",
                    "ERR_AMOUNT_NOT_POSITIVE",
                    "amount"
            );
        }

        /*
         * For initial zero-value Purchase Order registration, no actual
         * payment has been received, so paymentDate may be absent.
         *
         * Every actual payment must contain a payment date.
         */
        if (!isZeroAmountPurchaseOrder
                && request.getPaymentDate() == null) {

            throw new ValidationException(
                    "Payment date is required",
                    "ERR_PAYMENT_DATE_REQUIRED",
                    "paymentDate"
            );
        }

        // =====================================================
        // 7. TDS VALIDATION
        // =====================================================
        if (isZeroAmountPurchaseOrder
                && Boolean.TRUE.equals(request.getTdsActive())) {

            throw new ValidationException(
                    "TDS cannot be registered during initial Purchase Order "
                            + "registration because no tax invoice/payment "
                            + "is generated yet",
                    "ERR_TDS_NOT_ALLOWED_ON_INITIAL_PO",
                    "tdsActive"
            );
        }

        validateTdsRequest(request, paymentType);

        // =====================================================
        // 8. EPR HANDLING
        // =====================================================
        boolean isProductRelated =
                isProductRelatedEstimate(estimate);

        if (isProductRelated) {
            validateEprFields(request);
        } else {
            request.setEprFinancialYear(null);
            request.setEprPortalRegistrationNumber(null);
            request.setEprCertificateOrInvoiceNumber(null);
        }

        // =====================================================
        // 8.1 ROUTE TO EXISTING ADVANCE TAX INVOICE
        // =====================================================
        /*
         * Initial zero-value PURCHASE_ORDER must continue through the
         * existing UnbilledInvoice workflow. Every actual payment first
         * checks for an active Advance Tax Invoice.
         */
        if (!isZeroAmountPurchaseOrder) {

            List<Invoice> activeAdvanceInvoices =
                    invoiceRepository.findActiveAdvanceInvoicesForUpdate(
                            estimate.getId(),
                            InvoiceOrigin.ADVANCE_TAX_INVOICE,
                            List.of(
                                    InvoicePaymentStatus.UNPAID,
                                    InvoicePaymentStatus.PARTIALLY_PAID
                            )
                    );

            if (!activeAdvanceInvoices.isEmpty()) {

                Invoice openAdvanceInvoice = activeAdvanceInvoices
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(invoice ->
                                safe2(invoice.getOutstandingAmount())
                                        .subtract(safe2(invoice.getPendingReceivedAmount()))
                                        .compareTo(BigDecimal.ZERO) > 0
                        )
                        .findFirst()
                        .orElseThrow(() -> new ValidationException(
                                "An Advance Tax Invoice exists for this estimate, but its complete outstanding amount is already reserved by pending payments. Please wait for Accounts approval or rejection.",
                                "ERR_ADVANCE_INVOICE_OUTSTANDING_ALREADY_RESERVED",
                                "estimateId"
                        ));

                return registerPaymentAgainstAdvanceInvoice(
                        request,
                        estimate,
                        openAdvanceInvoice,
                        salesperson,
                        paymentType,
                        bankLedger,
                        reqAmount,
                        isPurchaseOrder
                );
            }
        }

        // =====================================================
        // 9. FIND OR CREATE UNBILLED INVOICE
        // =====================================================
        UnbilledInvoice unbilled =
                unbilledInvoiceRepository
                        .findByEstimateAndIsCancelledFalse(estimate)
                        .orElse(null);

        boolean isFirstPayment = unbilled == null;

        /*
         * Defensive recheck using the existing UnbilledInvoice snapshot.
         *
         * This handles old records where the estimate or unit may have
         * changed after the unbilled invoice was created.
         */
        if (!isFirstPayment) {

            validateInternationalTdsRestriction(
                    request,
                    estimate,
                    unbilled
            );

            internationalTransaction =
                    isInternationalTransaction(
                            estimate,
                            unbilled
                    );

            paymentGstRegistrationType =
                    resolveGstRegistrationType(
                            estimate,
                            unbilled
                    );

            if (internationalTransaction) {
                unbilled.setTdsActive(false);
            }
        }

        if (isFirstPayment) {

            unbilled = new UnbilledInvoice();

            unbilled.setPublicUuid(
                    UUID.randomUUID().toString()
            );

            unbilled.setUnbilledNumber(
                    generateUnbilledNumber()
            );

            unbilled.setAdvanceInvoiceNumber(
                    generateAdvanceInvoiceNumber()
            );

            unbilled.setEstimate(estimate);
            unbilled.setCompany(estimate.getCompany());

            // =====================================================
            // UNIT + GST REGISTRATION TYPE SNAPSHOT
            // =====================================================
            unbilled.setUnit(unit);

            unbilled.setGstRegistrationType(
                    paymentGstRegistrationType
            );

            unbilled.setContact(estimate.getContact());
            unbilled.setCreatedAt(LocalDateTime.now());
            unbilled.setUpdatedAt(LocalDateTime.now());

            BigDecimal total =
                    estimate.getGrandTotal() != null
                            ? estimate.getGrandTotal()
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            )
                            : BigDecimal.ZERO.setScale(
                            2,
                            RoundingMode.HALF_UP
                    );

            unbilled.setTotalAmount(total);

            unbilled.setReceivedAmount(
                    BigDecimal.ZERO.setScale(
                            2,
                            RoundingMode.HALF_UP
                    )
            );

            unbilled.setCurrentReceivedAmount(
                    BigDecimal.ZERO.setScale(
                            2,
                            RoundingMode.HALF_UP
                    )
            );

            unbilled.setOutstandingAmount(total);

            unbilled.setStatus(
                    UnbilledStatus.PENDING_APPROVAL
            );

            unbilled.setCreatedBy(salesperson);

            unbilled.setApprovedBy(null);
            unbilled.setApprovedAt(null);
            unbilled.setApprovalRemarks(null);
            unbilled.setRejectionReason(null);

            unbilled.setGovernmentFeeActive(
                    Boolean.TRUE.equals(
                            request.getGovernmentFeeActive()
                    )
            );

            unbilled.setTdsActive(
                    !internationalTransaction
                            && Boolean.TRUE.equals(
                            request.getTdsActive()
                    )
            );

            unbilled =
                    unbilledInvoiceRepository.save(unbilled);

            log.info(
                    "Created new UnbilledInvoice {} for estimate {} | gstRegistrationType={}",
                    unbilled.getUnbilledNumber(),
                    estimate.getEstimateNumber(),
                    unbilled.getGstRegistrationType()
            );
        }

        // =====================================================
        // 10. GOVERNMENT FEE DUPLICATE CHECK
        // =====================================================
        if (Boolean.TRUE.equals(
                request.getGovernmentFeeActive()
        )) {

            if (!isFirstPayment) {

                Optional<GovernmentFee> existingByEstimate =
                        governmentFeeRepository
                                .findByEstimate(estimate);

                Optional<GovernmentFee> existingByUnbilled =
                        governmentFeeRepository
                                .findByUnbilledInvoice(unbilled);

                GovernmentFee existingGovernmentFee =
                        existingByUnbilled.orElse(
                                existingByEstimate.orElse(null)
                        );

                if (existingGovernmentFee != null) {

                    if (existingGovernmentFee.getStatus()
                            == GovernmentFeeStatus.PENDING) {

                        throw new ValidationException(
                                "Government fee is already registered and pending approval "
                                        + "for this estimate/unbilled invoice",
                                "ERR_GOV_FEE_ALREADY_PENDING",
                                "governmentFee"
                        );
                    }

                    if (existingGovernmentFee.getStatus()
                            == GovernmentFeeStatus.APPROVED) {

                        throw new ValidationException(
                                "Government fee is already approved for this "
                                        + "estimate/unbilled invoice and cannot be added again",
                                "ERR_GOV_FEE_ALREADY_APPROVED",
                                "governmentFee"
                        );
                    }

                    throw new ValidationException(
                            "Government fee already exists for this "
                                    + "estimate/unbilled invoice",
                            "ERR_GOV_FEE_ALREADY_EXISTS",
                            "governmentFee"
                    );
                }

                unbilled.setGovernmentFeeActive(true);
            }
        }

        // =====================================================
        // 11. PREVENT PAYMENT TYPE CHANGE AFTER FIRST PAYMENT
        // =====================================================
        paymentReceiptRepository
                .findTopByUnbilledInvoiceAndIsCancelledFalseOrderByIdAsc(
                        unbilled
                )
                .ifPresent(firstReceipt -> {

                    String firstCode =
                            firstReceipt.getPaymentType()
                                    .getCode()
                                    .trim()
                                    .toUpperCase();

                    String newCode =
                            paymentType.getCode()
                                    .trim()
                                    .toUpperCase();

                    if (!firstCode.equals(newCode)) {
                        throw new ValidationException(
                                "Payment type cannot be changed after first payment. "
                                        + "First type: " + firstCode,
                                "ERR_PAYMENT_TYPE_CHANGE_NOT_ALLOWED",
                                "paymentTypeId"
                        );
                    }
                });

        // =====================================================
        // 12. TDS CALCULATION
        // =====================================================
        /*
         * TDS is calculated on taxable value excluding GST.
         *
         * Settlement amount:
         * Bank amount + TDS amount
         */
        BigDecimal tdsAmountForThisRegistration =
                calculateTdsAmountIfRequired(
                        request,
                        estimate,
                        unbilled
                );

        // =====================================================
        // 12.1 SETTLEMENT VALIDATION BEFORE PAYMENT RULES
        // =====================================================
        BigDecimal outstandingBeforeThisPayment =
                safe2(unbilled.getOutstandingAmount());

        BigDecimal settlementAmountForThisRegistration =
                reqAmount
                        .add(tdsAmountForThisRegistration)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (settlementAmountForThisRegistration
                .compareTo(outstandingBeforeThisPayment) > 0) {

            throw new ValidationException(
                    "Payment settlement exceeds outstanding amount. "
                            + "Bank amount: ₹" + reqAmount
                            + ", TDS amount: ₹"
                            + tdsAmountForThisRegistration
                            + ", settlement amount: ₹"
                            + settlementAmountForThisRegistration
                            + ", outstanding amount: ₹"
                            + outstandingBeforeThisPayment
                            + ". Please reduce bank amount or do not apply TDS.",
                    "ERR_SETTLEMENT_EXCEEDS_OUTSTANDING",
                    "amount"
            );
        }

        // =====================================================
        // 13. VALIDATE PAYMENT RULES
        // =====================================================
        validatePaymentRules(
                paymentType,
                reqAmount,
                unbilled,
                request.getPaymentTermsDays(),
                tdsAmountForThisRegistration
        );

        // =====================================================
        // 15. PREVENT PAYMENT FROM EXCEEDING TOTAL AMOUNT
        // =====================================================
        BigDecimal approvedAmount =
                safe2(unbilled.getReceivedAmount());

        BigDecimal pendingAmount =
                safe2(unbilled.getCurrentReceivedAmount());

        BigDecimal totalAmount =
                safe2(unbilled.getTotalAmount());

        BigDecimal totalAfterThisRegistration =
                approvedAmount
                        .add(pendingAmount)
                        .add(settlementAmountForThisRegistration)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (totalAfterThisRegistration
                .compareTo(totalAmount) > 0) {

            BigDecimal remainingAllowed =
                    totalAmount
                            .subtract(
                                    approvedAmount.add(pendingAmount)
                            )
                            .max(BigDecimal.ZERO)
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal excessAmount =
                    totalAfterThisRegistration
                            .subtract(totalAmount)
                            .max(BigDecimal.ZERO)
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );

            throw new ValidationException(
                    String.format(
                            "Payment exceeds allowed amount. "
                                    + "Approved amount is ₹%s, "
                                    + "pending approval amount is ₹%s, "
                                    + "remaining payable amount is ₹%s, "
                                    + "current settlement amount is ₹%s, "
                                    + "and it exceeds by ₹%s.",
                            approvedAmount,
                            pendingAmount,
                            remainingAllowed,
                            settlementAmountForThisRegistration,
                            excessAmount
                    ),
                    "ERR_PAYMENT_EXCEEDS_TOTAL_AMOUNT",
                    "amount"
            );
        }

        // =====================================================
        // 16. CREATE PAYMENT RECEIPT
        // =====================================================

        /*
         * FIX:
         *
         * payment_receipt.payment_date is nullable=false.
         *
         * For a zero-value initial PURCHASE_ORDER there is no actual
         * payment date. In that case, the current date is stored as the
         * PO registration date.
         *
         * For every actual payment, the request payment date is used.
         */
        LocalDate effectivePaymentDate =
                request.getPaymentDate() != null
                        ? request.getPaymentDate()
                        : LocalDate.now();

        PaymentReceipt receipt = new PaymentReceipt();

        receipt.setUnbilledInvoice(unbilled);
        receipt.setPaymentType(paymentType);
        receipt.setAmount(reqAmount);
        receipt.setPaymentDate(effectivePaymentDate);
        receipt.setPaymentMode(request.getPaymentMode());
        receipt.setTransactionReference(
                request.getTransactionReference()
        );
        receipt.setRemarks(request.getRemarks());
        receipt.setReceivedBy(salesperson);
        receipt.setPaymentProof(request.getPaymentProof());
        receipt.setPaymentTermsDays(
                request.getPaymentTermsDays()
        );
        receipt.setPoNumber(request.getPoNumber());
        receipt.setPoAttachmentUrl(
                request.getPoAttachmentUrl()
        );

        if (request.getPaymentTermsDays() != null
                && request.getPaymentTermsDays() > 0) {

            receipt.setPaymentTerms(
                    "Net "
                            + request.getPaymentTermsDays()
                            + " Days"
            );

        } else {
            receipt.setPaymentTerms(
                    request.getPaymentTerms()
            );
        }

        // Bank ledger is saved for future voucher posting.
        receipt.setBankLedger(bankLedger);

        // EPR fields only for product-related estimates.
        receipt.setEprFinancialYear(
                request.getEprFinancialYear()
        );

        receipt.setEprPortalRegistrationNumber(
                request.getEprPortalRegistrationNumber()
        );

        receipt.setEprCertificateOrInvoiceNumber(
                request.getEprCertificateOrInvoiceNumber()
        );

        receipt.setStatus(PaymentStatus.PENDING);

        receipt = paymentReceiptRepository.save(receipt);

        createTdsIfRequired(
                request,
                estimate,
                unbilled,
                receipt,
                salesperson,
                tdsAmountForThisRegistration
        );

        log.info(
                "Created PaymentReceipt {} | amount: {} | paymentDate: {}",
                receipt.getId(),
                request.getAmount(),
                receipt.getPaymentDate()
        );

        // =====================================================
        // 17. CREATE LEGAL VERIFICATION FOR PURCHASE ORDER
        // =====================================================
        paymentLegalVerificationService
                .createIfPurchaseOrder(
                        receipt,
                        salesperson
                );

        // =====================================================
        // 18. CREATE GOVERNMENT FEE
        // =====================================================
        createGovernmentFeeIfRequired(
                request,
                estimate,
                unbilled,
                salesperson
        );

        // =====================================================
        // 19. UPDATE UNBILLED INVOICE TOTALS
        // =====================================================
        unbilled.applyPayment(
                settlementAmountForThisRegistration
        );

        unbilled.setStatus(
                UnbilledStatus.PENDING_APPROVAL
        );

        unbilledInvoiceRepository.save(unbilled);

        log.info(
                "Updated unbilled {} | received: {}, outstanding: {}, status: {}",
                unbilled.getUnbilledNumber(),
                unbilled.getReceivedAmount(),
                unbilled.getOutstandingAmount(),
                unbilled.getStatus()
        );

        // =====================================================
        // 20. UPDATE ESTIMATE STATUS
        // =====================================================
        estimate.setStatus(EstimateStatus.INITIATED);
        estimateRepository.save(estimate);

        // =====================================================
        // 21. PREPARE RESPONSE MESSAGE
        // =====================================================
        String message =
                isFirstPayment
                        ? "First payment registered. Unbilled created – "
                        + "awaiting Accounts approval"
                        : String.format(
                        "Additional payment of ₹%s registered. "
                                + "Total received: ₹%s / ₹%s. "
                                + "Awaiting approval.",
                        reqAmount,
                        unbilled.getReceivedAmount(),
                        unbilled.getTotalAmount()
                );

        if (Boolean.TRUE.equals(
                request.getGovernmentFeeActive()
        )) {
            message +=
                    " Government fee registered in full "
                            + "and awaiting Accounts approval.";
        }

        if (isPurchaseOrder) {
            message +=
                    " PO document sent to Legal department "
                            + "for verification.";
        }

        // =====================================================
        // 22. BUILD AND RETURN RESPONSE
        // =====================================================
        PaymentRegistrationResponseDto response =
                new PaymentRegistrationResponseDto();

        response.setPaymentReceiptId(receipt.getId());
        response.setUnbilledNumber(
                unbilled.getUnbilledNumber()
        );
        response.setUnbilledStatus(unbilled.getStatus());
        response.setMessage(message);

        pushPaymentRegisteredNotificationToAccountUsers(
                unbilled,
                receipt,
                estimate,
                salesperson
        );

        log.info(
                "Payment registration completed | estimateId={} | "
                        + "unbilledId={} | receiptId={} | firstPayment={} | "
                        + "settlementAmount={} | status={}",
                estimate.getId(),
                unbilled.getId(),
                receipt.getId(),
                isFirstPayment,
                settlementAmountForThisRegistration,
                unbilled.getStatus()
        );

        return response;
    }


    /**
     * Registers a PENDING PaymentReceipt against an already-generated
     * Advance Tax Invoice.
     *
     * This method intentionally does not:
     * - create an UnbilledInvoice
     * - generate another Invoice
     * - post a Receipt voucher
     * - update receivedAmount
     *
     * Receipt voucher and receivedAmount are handled only after Accounts approval.
     */
    private PaymentRegistrationResponseDto registerPaymentAgainstAdvanceInvoice(
            PaymentRegistrationRequestDto request,
            Estimate estimate,
            Invoice invoice,
            User salesperson,
            PaymentType paymentType,
            LedgerMaster bankLedger,
            BigDecimal requestBankAmount,
            boolean isPurchaseOrder
    ) {

        /*
         * IMPORTANT:
         * invoice must be selected using a PESSIMISTIC_WRITE repository query.
         * The Estimate should also be locked to prevent concurrent overpayment
         * registrations against different invoices of the same Estimate.
         */

        if (estimate == null || estimate.getId() == null) {
            throw new ValidationException(
                    "Estimate is required for payment registration",
                    "ERR_ESTIMATE_REQUIRED",
                    "estimateId"
            );
        }

        if (invoice == null || invoice.getId() == null) {
            throw new ValidationException(
                    "Advance Tax Invoice is required for payment registration",
                    "ERR_ADVANCE_INVOICE_REQUIRED",
                    "estimateId"
            );
        }

        if (invoice.getInvoiceOrigin() != InvoiceOrigin.ADVANCE_TAX_INVOICE) {
            throw new ValidationException(
                    "Selected Invoice is not an Advance Tax Invoice",
                    "ERR_INVALID_ADVANCE_INVOICE_ORIGIN",
                    "invoiceId"
            );
        }

        if (invoice.isCancelled()) {
            throw new ValidationException(
                    "Payment cannot be registered against a cancelled Invoice",
                    "ERR_PAYMENT_ON_CANCELLED_ADVANCE_INVOICE",
                    "invoiceId"
            );
        }

        if (invoice.getPaymentStatus() == InvoicePaymentStatus.PAID) {
            throw new ValidationException(
                    "Advance Tax Invoice is already fully paid",
                    "ERR_ADVANCE_INVOICE_ALREADY_PAID",
                    "invoiceId"
            );
        }

        if (invoice.getEstimate() == null
                || invoice.getEstimate().getId() == null
                || !Objects.equals(
                invoice.getEstimate().getId(),
                estimate.getId()
        )) {

            throw new ValidationException(
                    "Advance Tax Invoice is not linked with the requested Estimate",
                    "ERR_ADVANCE_INVOICE_ESTIMATE_MISMATCH",
                    "estimateId"
            );
        }

        /*
         * Purchase Order is currently an UnbilledInvoice-based flow.
         * Only actual customer receipts can be registered here.
         */
        if (isPurchaseOrder) {
            throw new ValidationException(
                    "PURCHASE_ORDER payment type cannot be used against an already-generated Advance Tax Invoice. "
                            + "Register the actual customer receipt using FULL, PARTIAL, or INSTALLMENT.",
                    "ERR_PO_NOT_ALLOWED_FOR_ADVANCE_INVOICE_PAYMENT",
                    "paymentTypeId"
            );
        }

        if (Boolean.TRUE.equals(request.getGovernmentFeeActive())
                || request.getGovernmentFee() != null) {

            throw new ValidationException(
                    "Government fee registration is not supported against an Advance Tax Invoice payment",
                    "ERR_GOV_FEE_NOT_SUPPORTED_FOR_ADVANCE_INVOICE",
                    "governmentFeeActive"
            );
        }

        BigDecimal bankAmount = safe2(requestBankAmount);

        if (bankAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Payment amount must be greater than zero for an Advance Tax Invoice",
                    "ERR_ADVANCE_INVOICE_PAYMENT_AMOUNT_INVALID",
                    "amount"
            );
        }

        if (request.getPaymentDate() == null) {
            throw new ValidationException(
                    "Payment date is required for an Advance Tax Invoice payment",
                    "ERR_PAYMENT_DATE_REQUIRED",
                    "paymentDate"
            );
        }

        if (paymentType == null || paymentType.getCode() == null) {
            throw new ValidationException(
                    "Payment type is required",
                    "ERR_PAYMENT_TYPE_REQUIRED",
                    "paymentTypeId"
            );
        }

        /*
         * Prevent payment-type change after the first receipt against
         * the Advance Tax Invoice.
         */
        paymentReceiptRepository
                .findTopByInvoiceAndIsCancelledFalseOrderByIdAsc(invoice)
                .ifPresent(firstReceipt -> {

                    String firstCode =
                            firstReceipt.getPaymentType() != null
                                    && firstReceipt.getPaymentType().getCode() != null
                                    ? firstReceipt.getPaymentType()
                                    .getCode()
                                    .trim()
                                    .toUpperCase()
                                    : "";

                    String newCode =
                            paymentType.getCode()
                                    .trim()
                                    .toUpperCase();

                    if (!firstCode.equals(newCode)) {
                        throw new ValidationException(
                                "Payment type cannot be changed after the first payment against this Advance Tax Invoice. "
                                        + "First payment type: " + firstCode,
                                "ERR_ADVANCE_INVOICE_PAYMENT_TYPE_CHANGE_NOT_ALLOWED",
                                "paymentTypeId"
                        );
                    }
                });

        /*
         * TDS is calculated against the Advance Invoice.
         */
        BigDecimal tdsAmount = safe2(
                calculateAdvanceInvoiceTdsAmountIfRequired(
                        request,
                        invoice
                )
        );

        BigDecimal settlementAmount = bankAmount
                .add(tdsAmount)
                .setScale(2, RoundingMode.HALF_UP);

        /*
         * ------------------------------------------------------------
         * STEP 1: Calculate the Advance Invoice's available outstanding.
         * ------------------------------------------------------------
         *
         * Example:
         *
         * Invoice total                = 2,500
         * Pending registered payment   = 0
         * Available Invoice amount     = 2,500
         */
        BigDecimal invoiceOutstanding =
                safe2(invoice.getOutstandingAmount());

        BigDecimal invoicePendingReceived =
                safe2(invoice.getPendingReceivedAmount());

        BigDecimal invoiceAvailableAmount =
                invoiceOutstanding
                        .subtract(invoicePendingReceived)
                        .max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);

        if (invoiceAvailableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "No available outstanding amount remains on Advance Tax Invoice "
                            + invoice.getInvoiceNumber()
                            + ". Existing pending payments have reserved its outstanding balance.",
                    "ERR_NO_AVAILABLE_ADVANCE_INVOICE_OUTSTANDING",
                    "amount"
            );
        }

        /*
         * ------------------------------------------------------------
         * STEP 2: Calculate Estimate-level payment availability.
         * ------------------------------------------------------------
         *
         * The complete payment is checked against the Estimate balance,
         * not against only this Advance Tax Invoice.
         *
         * Example:
         *
         * Estimate total               = 5,000
         * Previous registered payments = 0
         * Estimate available           = 5,000
         * Current receipt              = 5,000
         *
         * Therefore the receipt is allowed.
         */
        BigDecimal estimateAvailableAmount =
                calculateEstimateAvailableSettlement(
                        estimate
                );

        if (estimateAvailableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "No payment amount remains available against Estimate "
                            + estimate.getId(),
                    "ERR_NO_ESTIMATE_PAYMENT_BALANCE",
                    "amount"
            );
        }

        if (settlementAmount.compareTo(estimateAvailableAmount) > 0) {
            throw new ValidationException(
                    "Payment settlement exceeds the available Estimate balance. "
                            + "Bank amount: ₹" + bankAmount
                            + ", TDS amount: ₹" + tdsAmount
                            + ", settlement amount: ₹" + settlementAmount
                            + ", Estimate available amount: ₹"
                            + estimateAvailableAmount,
                    "ERR_PAYMENT_EXCEEDS_ESTIMATE_AVAILABLE_BALANCE",
                    "amount"
            );
        }

        validateAdvanceInvoiceReceiptPaymentType(
                paymentType,
                settlementAmount,
                estimateAvailableAmount
        );

        /*
         * ------------------------------------------------------------
         * STEP 3: Allocate the receipt.
         * ------------------------------------------------------------
         *
         * Only the amount required by this Invoice is allocated.
         * The remaining amount becomes customer advance/unallocated.
         *
         * Example:
         *
         * Receipt settlement       = 5,000
         * Invoice available        = 2,500
         *
         * Allocated to Invoice     = 2,500
         * Unallocated advance      = 2,500
         */
        BigDecimal allocatedAmount =
                settlementAmount
                        .min(invoiceAvailableAmount)
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal unallocatedAmount =
                settlementAmount
                        .subtract(allocatedAmount)
                        .max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);

        /*
         * TDS must be associated with an Invoice settlement.
         * It should not become an unapplied customer advance.
         */
        if (tdsAmount.compareTo(allocatedAmount) > 0) {
            throw new ValidationException(
                    "TDS amount cannot exceed the amount allocated to the Advance Tax Invoice. "
                            + "TDS amount: ₹" + tdsAmount
                            + ", Invoice allocated amount: ₹" + allocatedAmount,
                    "ERR_TDS_EXCEEDS_INVOICE_ALLOCATION",
                    "tds"
            );
        }

        /*
         * Bank allocation is whatever remains after allocating the full TDS.
         *
         * Example:
         *
         * Allocated total = 2,500
         * TDS             = 250
         * Allocated bank  = 2,250
         */
        BigDecimal allocatedBankAmount =
                allocatedAmount
                        .subtract(tdsAmount)
                        .max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal unallocatedBankAmount =
                bankAmount
                        .subtract(allocatedBankAmount)
                        .max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);

        /*
         * Defensive validation:
         *
         * Unallocated settlement should consist only of actual Bank/Cash
         * receipt because TDS cannot be maintained as a customer advance.
         */
        if (unallocatedAmount.compareTo(unallocatedBankAmount) != 0) {
            throw new ValidationException(
                    "Invalid payment allocation. TDS cannot remain unallocated.",
                    "ERR_INVALID_ADVANCE_PAYMENT_ALLOCATION",
                    "amount"
            );
        }

        PaymentReceipt receipt = new PaymentReceipt();

        receipt.setUnbilledInvoice(null);
        receipt.setInvoice(invoice);
        receipt.setPaymentType(paymentType);

        /*
         * amount stores the complete actual Bank/Cash amount received,
         * not only the portion allocated to the Invoice.
         */
        receipt.setAmount(bankAmount);

        /*
         * These fields must be added to PaymentReceipt.
         */
        receipt.setAllocatedAmount(allocatedAmount);
        receipt.setUnallocatedAmount(unallocatedAmount);

        receipt.setPaymentDate(request.getPaymentDate());
        receipt.setPaymentMode(request.getPaymentMode());
        receipt.setTransactionReference(
                request.getTransactionReference()
        );
        receipt.setRemarks(request.getRemarks());
        receipt.setReceivedBy(salesperson);
        receipt.setPaymentProof(request.getPaymentProof());
        receipt.setPaymentTermsDays(
                request.getPaymentTermsDays()
        );
        receipt.setPoNumber(request.getPoNumber());
        receipt.setPoAttachmentUrl(
                request.getPoAttachmentUrl()
        );

        if (request.getPaymentTermsDays() != null
                && request.getPaymentTermsDays() > 0) {

            receipt.setPaymentTerms(
                    "Net "
                            + request.getPaymentTermsDays()
                            + " Days"
            );

        } else {
            receipt.setPaymentTerms(
                    request.getPaymentTerms()
            );
        }

        receipt.setBankLedger(bankLedger);
        receipt.setEprFinancialYear(
                request.getEprFinancialYear()
        );
        receipt.setEprPortalRegistrationNumber(
                request.getEprPortalRegistrationNumber()
        );
        receipt.setEprCertificateOrInvoiceNumber(
                request.getEprCertificateOrInvoiceNumber()
        );
        receipt.setStatus(PaymentStatus.PENDING);

        receipt = paymentReceiptRepository.save(receipt);

        /*
         * Create TDS against this Invoice and receipt.
         *
         * The complete TDS is allocated because TDS cannot remain
         * as an unapplied customer advance.
         */
        createAdvanceInvoiceTdsIfRequired(
                request,
                estimate,
                invoice,
                receipt,
                salesperson,
                tdsAmount
        );

        /*
         * Reserve only the amount allocated to the Advance Invoice.
         *
         * Do not reserve the complete settlement.
         *
         * Example:
         *
         * Receipt                  = 5,000
         * Advance Invoice pending  = 2,500
         * Unallocated advance      = 2,500
         */
        invoice.setPendingReceivedAmount(
                invoicePendingReceived
                        .add(allocatedAmount)
                        .setScale(2, RoundingMode.HALF_UP)
        );

        /*
         * receivedAmount and outstandingAmount are changed only when
         * Accounts approves this receipt.
         */
        invoiceRepository.save(invoice);

        pushAdvanceInvoicePaymentRegisteredNotificationToAccountUsers(
                invoice,
                receipt,
                estimate,
                salesperson,
                settlementAmount
        );

        PaymentRegistrationResponseDto response =
                new PaymentRegistrationResponseDto();

        response.setPaymentReceiptId(receipt.getId());
        response.setUnbilledNumber(null);
        response.setUnbilledStatus(null);

        StringBuilder message = new StringBuilder();

        message.append("Payment of ₹")
                .append(bankAmount);

        if (tdsAmount.compareTo(BigDecimal.ZERO) > 0) {
            message.append(" with TDS of ₹")
                    .append(tdsAmount);
        }

        message.append(" registered against Advance Tax Invoice ")
                .append(invoice.getInvoiceNumber())
                .append(". Settlement amount ₹")
                .append(settlementAmount)
                .append(" is awaiting Accounts approval. ")
                .append("₹")
                .append(allocatedAmount)
                .append(" has been allocated to the Advance Tax Invoice.");

        if (unallocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            message.append(" Remaining ₹")
                    .append(unallocatedAmount)
                    .append(" will remain as an unapplied customer advance.");
        }

        message.append(
                " No Unbilled Invoice or new Tax Invoice was created."
        );

        response.setMessage(message.toString());

        log.info(
                "Advance Invoice payment registered | "
                        + "estimateId={} | invoiceId={} | invoiceNumber={} "
                        + "| receiptId={} | bankAmount={} | tdsAmount={} "
                        + "| settlementAmount={} | allocatedAmount={} "
                        + "| unallocatedAmount={} | invoiceAvailableBefore={} "
                        + "| estimateAvailableBefore={} | invoicePendingAfter={}",
                estimate.getId(),
                invoice.getId(),
                invoice.getInvoiceNumber(),
                receipt.getId(),
                bankAmount,
                tdsAmount,
                settlementAmount,
                allocatedAmount,
                unallocatedAmount,
                invoiceAvailableAmount,
                estimateAvailableAmount,
                invoice.getPendingReceivedAmount()
        );

        return response;
    }


    private void validateAdvanceInvoiceReceiptPaymentType(
            PaymentType paymentType,
            BigDecimal settlementAmount,
            BigDecimal estimateAvailableAmount
    ) {

        if (paymentType == null || paymentType.getCode() == null) {
            throw new ValidationException(
                    "Payment type is required",
                    "ERR_PAYMENT_TYPE_REQUIRED",
                    "paymentTypeId"
            );
        }


        String paymentTypeCode =
                paymentType.getCode()
                        .trim()
                        .toUpperCase();

        if (settlementAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Settlement amount must be greater than zero",
                    "ERR_SETTLEMENT_AMOUNT_INVALID",
                    "amount"
            );
        }

        if (settlementAmount.compareTo(estimateAvailableAmount) > 0) {
            throw new ValidationException(
                    "Settlement amount exceeds the available Estimate balance",
                    "ERR_PAYMENT_EXCEEDS_ESTIMATE_AVAILABLE_BALANCE",
                    "amount"
            );
        }

        switch (paymentTypeCode) {

            case "FULL" -> {

                if (settlementAmount.compareTo(
                        estimateAvailableAmount
                ) != 0) {

                    throw new ValidationException(
                            "For FULL payment, settlement amount must equal the available Estimate balance of ₹"
                                    + estimateAvailableAmount,
                            "ERR_FULL_PAYMENT_AMOUNT_MISMATCH",
                            "amount"
                    );
                }
            }

            case "PARTIAL", "INSTALLMENT" -> {
                // Any positive amount up to Estimate availability is allowed.
            }

            case "PURCHASE_ORDER" -> throw new ValidationException(
                    "PURCHASE_ORDER cannot be registered against an Advance Tax Invoice",
                    "ERR_PO_NOT_ALLOWED_FOR_ADVANCE_INVOICE_PAYMENT",
                    "paymentTypeId"
            );

            default -> throw new ValidationException(
                    "Unsupported payment type for Advance Tax Invoice payment: "
                            + paymentTypeCode,
                    "ERR_UNSUPPORTED_ADVANCE_INVOICE_PAYMENT_TYPE",
                    "paymentTypeId"
            );
        }
    }

    private BigDecimal calculateEstimateAvailableSettlement(
            Estimate estimate
    ) {

        if (estimate == null || estimate.getId() == null) {
            throw new ValidationException(
                    "Estimate is required",
                    "ERR_ESTIMATE_REQUIRED",
                    "estimateId"
            );
        }

        BigDecimal estimateGrandTotal =
                safe2(estimate.getGrandTotal());

        BigDecimal registeredBankAmount =
                safe2(
                        paymentReceiptRepository
                                .sumRegisteredBankAmountForEstimate(
                                        estimate.getId(),
                                        List.of(
                                                PaymentStatus.PENDING,
                                                PaymentStatus.APPROVED
                                        )
                                )
                );

        BigDecimal registeredTdsAmount =
                safe2(
                        tdsRegistrationRepository
                                .sumRegisteredTdsAmountForEstimate(
                                        estimate.getId(),
                                        List.of(
                                                TdsStatus.PENDING,
                                                TdsStatus.APPROVED
                                        )
                                )
                );

        BigDecimal registeredSettlement =
                registeredBankAmount
                        .add(registeredTdsAmount)
                        .setScale(2, RoundingMode.HALF_UP);

        return estimateGrandTotal
                .subtract(registeredSettlement)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }


    private BigDecimal getTotalActiveTdsAmountForInvoice(Invoice invoice) {

        if (invoice == null || invoice.getId() == null) {
            return BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );
        }

        if (invoice.getEffectiveGstRegistrationType()
                == GstRegistrationType.INTERNATIONAL) {

            return BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );
        }

        return tdsRegistrationRepository
                .findAllByInvoiceAndIsDeletedFalse(invoice)
                .stream()
                .filter(Objects::nonNull)
                .filter(tds ->
                        tds.getStatus() == TdsStatus.PENDING
                                || tds.getStatus() == TdsStatus.APPROVED
                )
                .map(TdsRegistration::getTdsAmount)
                .filter(Objects::nonNull)
                .map(this::safe2)
                .reduce(
                        BigDecimal.ZERO.setScale(
                                2,
                                RoundingMode.HALF_UP
                        ),
                        BigDecimal::add
                )
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void createAdvanceInvoiceTdsIfRequired(
            PaymentRegistrationRequestDto request,
            Estimate estimate,
            Invoice invoice,
            PaymentReceipt receipt,
            User salesperson,
            BigDecimal calculatedTdsAmount
    ) {

        if (!Boolean.TRUE.equals(request.getTdsActive())) {
            return;
        }

        if (invoice.getEffectiveGstRegistrationType()
                == GstRegistrationType.INTERNATIONAL) {

            throw new ValidationException(
                    "TDS is not applicable for INTERNATIONAL transactions",
                    "ERR_TDS_NOT_ALLOWED_FOR_INTERNATIONAL",
                    "tdsActive"
            );
        }

        if (request.getTds() == null
                || request.getTds().getTdsPercentage() == null) {

            throw new ValidationException(
                    "TDS details are required",
                    "ERR_TDS_DETAILS_REQUIRED",
                    "tds"
            );
        }

        BigDecimal tdsAmount = safe2(calculatedTdsAmount);
        BigDecimal tdsPercentage = safe2(
                request.getTds().getTdsPercentage()
        );

        if (tdsAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "TDS amount must be greater than zero",
                    "ERR_TDS_AMOUNT_INVALID",
                    "tds"
            );
        }

        tdsRegistrationRepository
                .findByPaymentReceiptAndIsDeletedFalse(receipt)
                .ifPresent(existing -> {
                    throw new ValidationException(
                            "TDS is already registered for payment receipt ID: "
                                    + receipt.getId(),
                            "ERR_TDS_ALREADY_EXISTS_FOR_PAYMENT",
                            "tds"
                    );
                });

        BigDecimal taxableAmount = tdsAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        tdsPercentage,
                        2,
                        RoundingMode.HALF_UP
                );

        BigDecimal invoiceTaxableAmount = safe2(
                invoice.getSubTotalExGst()
        );

        if (taxableAmount.compareTo(invoiceTaxableAmount) > 0) {
            throw new ValidationException(
                    "Current payment taxable amount cannot exceed the Advance Tax Invoice taxable amount",
                    "ERR_TDS_TAXABLE_AMOUNT_EXCEEDS_INVOICE",
                    "tds"
            );
        }

        TdsRegistration tds = new TdsRegistration();

        tds.setPublicUuid(UUID.randomUUID().toString());
        tds.setEstimate(estimate);
        tds.setCompany(estimate.getCompany());
        tds.setUnbilledInvoice(null);
        tds.setInvoice(invoice);
        tds.setPaymentReceipt(receipt);
        tds.setTdsPercentage(tdsPercentage);
        tds.setTaxableAmount(taxableAmount);
        tds.setTdsAmount(tdsAmount);
        tds.setTdsDate(null);
        tds.setStatus(TdsStatus.PENDING);
        tds.setDeleted(false);
        tds.setCreatedBy(salesperson);
        tds.setUpdatedBy(salesperson);

        tdsRegistrationRepository.save(tds);
    }




    private void pushAdvanceInvoicePaymentRegisteredNotificationToAccountUsers(
            Invoice invoice,
            PaymentReceipt receipt,
            Estimate estimate,
            User salesperson,
            BigDecimal settlementAmount
    ) {

        if (invoice == null || invoice.getId() == null
                || receipt == null || receipt.getId() == null) {
            return;
        }

        List<User> accountUsers = findAccountDepartmentApprovers();

        if (accountUsers == null || accountUsers.isEmpty()) {
            log.warn(
                    "Advance Invoice payment notification skipped because no active Accounts users were found | invoiceId={}",
                    invoice.getId()
            );
            return;
        }

        String salespersonName = getUserDisplayName2(salesperson);
        String invoiceNumber = invoice.getInvoiceNumber() != null
                ? invoice.getInvoiceNumber()
                : "INVOICE-" + invoice.getId();

        String estimateNumber = estimate != null
                && estimate.getEstimateNumber() != null
                ? estimate.getEstimateNumber()
                : "";

        String companyName = estimate != null
                && estimate.getCompany() != null
                && estimate.getCompany().getName() != null
                ? estimate.getCompany().getName()
                : "company";

        String bankAmount = safe2(receipt.getAmount()).toPlainString();
        String settlement = safe2(settlementAmount).toPlainString();

        for (User accountUser : accountUsers) {
            if (accountUser == null || accountUser.getId() == null) {
                continue;
            }

            notificationPublisherService.sendNotification(
                    NotificationCreateRequestDto.builder()
                            .receiverId(accountUser.getId())
                            .actorId(salesperson != null
                                    ? salesperson.getId()
                                    : null)
                            .actorName(salespersonName)
                            .module(NotificationCreateRequestDto.NotificationModule.PAYMENT)
                            .eventType(NotificationCreateRequestDto.NotificationEventType.PAYMENT_REGISTERED)
                            .referenceId(invoice.getId())
                            .referenceNumber(invoiceNumber)
                            .title("Advance Invoice Payment Approval Required")
                            .message(
                                    salespersonName
                                            + " registered bank payment of ₹"
                                            + bankAmount
                                            + " against Advance Tax Invoice "
                                            + invoiceNumber
                                            + " for " + companyName + "."
                            )
                            .redirectUrl("/account/invoices/" + invoice.getId())
                            .priority(NotificationPriority.HIGH)
                            .displayType(NotificationCreateRequestDto.NotificationDisplayType.WARNING)
                            .metadataJson(
                                    "{"
                                            + "\"invoiceId\":" + invoice.getId() + ","
                                            + "\"paymentReceiptId\":" + receipt.getId() + ","
                                            + "\"invoiceNumber\":\"" + escapeJson(invoiceNumber) + "\","
                                            + "\"estimateNumber\":\"" + escapeJson(estimateNumber) + "\","
                                            + "\"companyName\":\"" + escapeJson(companyName) + "\","
                                            + "\"bankAmount\":\"" + escapeJson(bankAmount) + "\","
                                            + "\"settlementAmount\":\"" + escapeJson(settlement) + "\","
                                            + "\"registeredBy\":\"" + escapeJson(salespersonName) + "\""
                                            + "}"
                            )
                            .build()
            );
        }
    }


    private BigDecimal calculateAdvanceInvoiceTdsAmountIfRequired(
            PaymentRegistrationRequestDto request,
            Invoice invoice
    ) {

        BigDecimal zero = BigDecimal.ZERO.setScale(
                2,
                RoundingMode.HALF_UP
        );

        if (invoice == null || invoice.getId() == null) {
            throw new ValidationException(
                    "Invoice is required for TDS calculation",
                    "ERR_ADVANCE_INVOICE_REQUIRED_FOR_TDS",
                    "invoiceId"
            );
        }

        boolean internationalTransaction =
                invoice.getEffectiveGstRegistrationType()
                        == GstRegistrationType.INTERNATIONAL;

        if (internationalTransaction) {
            if (Boolean.TRUE.equals(request.getTdsActive())
                    || request.getTds() != null) {

                throw new ValidationException(
                        "TDS is not applicable for INTERNATIONAL transactions",
                        "ERR_TDS_NOT_ALLOWED_FOR_INTERNATIONAL",
                        "tdsActive"
                );
            }
            return zero;
        }

        if (!Boolean.TRUE.equals(request.getTdsActive())) {
            return zero;
        }

        if (request.getTds() == null
                || request.getTds().getTdsPercentage() == null) {

            throw new ValidationException(
                    "TDS percentage is required when TDS is active",
                    "ERR_TDS_PERCENTAGE_REQUIRED",
                    "tds.tdsPercentage"
            );
        }

        BigDecimal bankAmount = safe2(request.getAmount());
        BigDecimal tdsPercentage = safe2(
                request.getTds().getTdsPercentage()
        );

        if (bankAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Payment amount must be greater than zero when TDS is active",
                    "ERR_TDS_PAYMENT_AMOUNT_REQUIRED",
                    "amount"
            );
        }

        if (tdsPercentage.compareTo(new BigDecimal("2.00")) != 0
                && tdsPercentage.compareTo(new BigDecimal("10.00")) != 0) {

            throw new ValidationException(
                    "TDS percentage must be either 2 or 10",
                    "ERR_INVALID_TDS_PERCENTAGE",
                    "tds.tdsPercentage"
            );
        }

        BigDecimal taxableAmount = safe2(invoice.getSubTotalExGst());

        if (taxableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Invoice taxable amount excluding GST is not available for TDS calculation",
                    "ERR_TDS_TAXABLE_AMOUNT_NOT_FOUND",
                    "tds"
            );
        }

        BigDecimal totalAllowedTds = taxableAmount
                .multiply(tdsPercentage)
                .divide(
                        BigDecimal.valueOf(100),
                        2,
                        RoundingMode.HALF_UP
                );

        BigDecimal alreadyUsedTds = getTotalActiveTdsAmountForInvoice(invoice);

        BigDecimal remainingTdsLimit = totalAllowedTds
                .subtract(alreadyUsedTds)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        if (remainingTdsLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "TDS limit is already exhausted for Advance Tax Invoice "
                            + invoice.getInvoiceNumber(),
                    "ERR_TDS_LIMIT_EXHAUSTED",
                    "tds"
            );
        }

        BigDecimal availableOutstanding = safe2(invoice.getOutstandingAmount())
                .subtract(safe2(invoice.getPendingReceivedAmount()))
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        if (availableOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "No available outstanding amount exists for TDS settlement",
                    "ERR_NO_OUTSTANDING_FOR_TDS",
                    "amount"
            );
        }

        remainingTdsLimit = remainingTdsLimit
                .min(availableOutstanding)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal remainingNetBankReceivable = availableOutstanding
                .subtract(remainingTdsLimit)
                .setScale(2, RoundingMode.HALF_UP);

        if (remainingNetBankReceivable.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Available outstanding cannot be settled with a positive bank amount under the selected TDS percentage",
                    "ERR_INVALID_NET_BANK_RECEIVABLE",
                    "amount"
            );
        }

        if (bankAmount.compareTo(remainingNetBankReceivable) > 0) {
            throw new ValidationException(
                    "Bank payment exceeds maximum receivable after TDS. Bank amount: ₹"
                            + bankAmount
                            + ", maximum bank amount: ₹" + remainingNetBankReceivable
                            + ", remaining TDS: ₹" + remainingTdsLimit,
                    "ERR_BANK_AMOUNT_EXCEEDS_NET_RECEIVABLE",
                    "amount"
            );
        }

        BigDecimal calculatedTds = bankAmount
                .multiply(remainingTdsLimit)
                .divide(
                        remainingNetBankReceivable,
                        2,
                        RoundingMode.HALF_UP
                )
                .min(remainingTdsLimit)
                .setScale(2, RoundingMode.HALF_UP);

        if (calculatedTds.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Calculated TDS amount must be greater than zero",
                    "ERR_TDS_AMOUNT_INVALID",
                    "tds"
            );
        }

        BigDecimal settlementAmount = bankAmount
                .add(calculatedTds)
                .setScale(2, RoundingMode.HALF_UP);

        if (settlementAmount.compareTo(availableOutstanding) > 0) {
            throw new ValidationException(
                    "Bank amount plus TDS exceeds available Invoice outstanding",
                    "ERR_TDS_SETTLEMENT_EXCEEDS_OUTSTANDING",
                    "amount"
            );
        }

        return calculatedTds;
    }


    private void validateAdvanceInvoicePaymentRules(
            PaymentType paymentType,
            BigDecimal bankAmount,
            BigDecimal tdsAmount,
            Invoice invoice,
            BigDecimal availableOutstanding
    ) {

        if (paymentType == null || paymentType.getCode() == null) {
            throw new ValidationException(
                    "Invalid payment type",
                    "ERR_PAYMENT_TYPE_INVALID",
                    "paymentTypeId"
            );
        }

        String code = paymentType.getCode().trim().toUpperCase();

        BigDecimal safeBankAmount = safe2(bankAmount);
        BigDecimal safeTdsAmount = safe2(tdsAmount);
        BigDecimal settlementAmount = safeBankAmount
                .add(safeTdsAmount)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal invoiceTotal = safe2(invoice.getGrandTotal());
        BigDecimal available = safe2(availableOutstanding);

        if (safeBankAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Bank/Cash/Payment Gateway amount must be greater than zero",
                    "ERR_AMOUNT_NOT_POSITIVE",
                    "amount"
            );
        }

        if (settlementAmount.compareTo(available) > 0) {
            throw new ValidationException(
                    "Settlement amount exceeds available Invoice outstanding",
                    "ERR_ADVANCE_INVOICE_SETTLEMENT_EXCEEDS_AVAILABLE",
                    "amount"
            );
        }

        if ("FULL".equals(code)) {
            if (settlementAmount.compareTo(available) != 0) {
                throw new ValidationException(
                        "FULL payment settlement must equal available outstanding amount ₹"
                                + available
                                + ". Current settlement is ₹" + settlementAmount,
                        "ERR_FULL_AMOUNT_MISMATCH",
                        "amount"
                );
            }
            return;
        }

        if ("PARTIAL".equals(code)) {

            BigDecimal halfInvoice = invoiceTotal
                    .multiply(new BigDecimal("0.50"))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal expected = available.compareTo(halfInvoice) < 0
                    ? available
                    : halfInvoice;

            if (settlementAmount.compareTo(expected) != 0) {
                throw new ValidationException(
                        "PARTIAL payment settlement must be ₹" + expected
                                + ". Bank amount: ₹" + safeBankAmount
                                + ", TDS amount: ₹" + safeTdsAmount
                                + ", settlement: ₹" + settlementAmount,
                        "ERR_PARTIAL_AMOUNT_MISMATCH",
                        "amount"
                );
            }
            return;
        }

        if ("INSTALLMENT".equals(code)) {
            return;
        }

        if ("PURCHASE_ORDER".equals(code)) {
            throw new ValidationException(
                    "PURCHASE_ORDER is not allowed against an already-generated Advance Tax Invoice",
                    "ERR_PO_NOT_ALLOWED_FOR_ADVANCE_INVOICE_PAYMENT",
                    "paymentTypeId"
            );
        }

        throw new ValidationException(
                "Unsupported payment type: " + paymentType.getCode(),
                "ERR_UNSUPPORTED_PAYMENT_TYPE",
                "paymentTypeId"
        );
    }




    private LedgerMaster validateAndGetBankLedger(
            PaymentRegistrationRequestDto request,
            BigDecimal reqAmount
    ) {
        log.debug("Validating bank ledger for payment registration | bankLedgerId={} | amount={}",
                request != null ? request.getBankLedgerId() : null,
                reqAmount);

        /*
         * If actual amount is coming from customer, bank ledger is required.
         *
         * For PURCHASE_ORDER with zero amount, bank ledger is not required
         * because no money is received yet.
         */
        if (reqAmount == null || reqAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        if (request.getBankLedgerId() == null) {
            throw new ValidationException(
                    "Bank ledger is required for payment registration",
                    "ERR_BANK_LEDGER_REQUIRED",
                    "bankLedgerId"
            );
        }


        LedgerMaster bankLedger = ledgerMasterRepository.findByIdAndDeletedFalse(request.getBankLedgerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bank ledger not found with ID: " + request.getBankLedgerId(),
                        "BANK_LEDGER_NOT_FOUND"
                ));

        if (!bankLedger.isActive()) {
            throw new ValidationException(
                    "Selected bank ledger is inactive",
                    "ERR_BANK_LEDGER_INACTIVE",
                    "bankLedgerId"
            );
        }

        if (
                bankLedger.getLedgerType() != LedgerType.BANK
                        && bankLedger.getLedgerType() != LedgerType.CASH
                        && bankLedger.getLedgerType() != LedgerType.PAYMENT_GATEWAY
        ) {
            throw new ValidationException(
                    "Selected ledger must be a BANK, CASH, or PAYMENT_GATEWAY ledger",
                    "ERR_INVALID_RECEIPT_LEDGER",
                    "bankLedgerId"
            );
        }

        log.debug("Bank ledger validated | ledgerId={} | ledgerName={} | ledgerType={}",
                bankLedger.getId(), bankLedger.getLedgerName(), bankLedger.getLedgerType());

        return bankLedger;
    }

    /**
     * Validates payment rules based on payment type and TDS settlement logic.
     *
     * CORE BUSINESS CONCEPT:
     * When TDS is active:
     *   - reqAmount      = Actual amount received in Bank
     *   - tdsAmount      = TDS amount deducted by customer (calculated separately)
     *   - settlementAmount = reqAmount + tdsAmount → This represents the total amount
     *                        the customer has actually paid against the invoice.
     *
     * Example:
     *   Bank Received = ₹216
     *   TDS Deducted  = ₹20
     *   Settlement    = ₹236 (This reduces the outstanding balance)
     */
    private void validatePaymentRules(
            PaymentType paymentType,
            BigDecimal reqAmount,
            UnbilledInvoice unbilled,
            Integer paymentTermsDays,
            BigDecimal tdsAmount
    ) {

        // =====================================================
        // 1. BASIC VALIDATION
        // =====================================================
        if (paymentType == null || paymentType.getCode() == null) {
            throw new ValidationException(
                    "Invalid payment type",
                    "ERR_PAYMENT_TYPE_INVALID",
                    "paymentTypeId"
            );
        }

        BigDecimal outstanding = safe2(unbilled.getOutstandingAmount());
        BigDecimal total = safe2(unbilled.getTotalAmount());
        BigDecimal safeReqAmount = safe2(reqAmount);
        BigDecimal safeTdsAmount = safe2(tdsAmount);

        // =====================================================
        // 2. CALCULATE SETTLEMENT AMOUNT (Bank + TDS)
        // =====================================================
        // This is the most important calculation in the payment flow.
        // The customer’s total liability is reduced by (Bank Amount + TDS Amount).
        BigDecimal settlementAmount = safeReqAmount
                .add(safeTdsAmount)
                .setScale(2, RoundingMode.HALF_UP);

        String code = paymentType.getCode().trim().toUpperCase();
        boolean isPurchaseOrder = "PURCHASE_ORDER".equals(code);

        log.debug(
                "Validating payment rules | paymentType={} | bankAmount={} | tdsAmount={} | settlementAmount={} | outstanding={} | total={}",
                code,
                safeReqAmount,
                safeTdsAmount,
                settlementAmount,
                outstanding,
                total
        );

        // =====================================================
        // 3. AMOUNT VALIDATION (Non Purchase Order)
        // =====================================================
        if (!isPurchaseOrder && safeReqAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Amount must be positive",
                    "ERR_AMOUNT_NOT_POSITIVE",
                    "amount"
            );
        }

        // =====================================================
        // 4. PURCHASE ORDER SPECIFIC VALIDATION
        // =====================================================
        // Purchase Order can have zero amount initially.
        // Payment terms (Net X Days) are mandatory for PO.
        if (isPurchaseOrder) {
            if (paymentTermsDays == null || paymentTermsDays < 0) {
                throw new ValidationException(
                        "Payment terms days is required for Purchase Order payment type",
                        "ERR_PAYMENT_TERMS_DAYS_REQUIRED",
                        "paymentTermsDays"
                );
            }
        }

        // =====================================================
        // 5. SETTLEMENT AMOUNT CANNOT EXCEED OUTSTANDING
        // =====================================================
        if (settlementAmount.compareTo(outstanding) > 0) {
            throw new ValidationException(
                    "Settlement amount is greater than outstanding amount. Bank amount: "
                            + safeReqAmount + ", TDS amount: " + safeTdsAmount
                            + ", Settlement amount: " + settlementAmount
                            + ", Outstanding: " + outstanding,
                    "ERR_AMOUNT_EXCEEDS_OUTSTANDING",
                    "amount"
            );
        }

        // =====================================================
        // 6. FULL PAYMENT VALIDATION
        // =====================================================
        // For FULL payment, settlement must exactly match the outstanding amount.
        if ("FULL".equals(code)) {
            if (settlementAmount.compareTo(outstanding) != 0) {
                throw new ValidationException(
                        "FULL payment settlement must equal outstanding amount. Bank amount: "
                                + safeReqAmount + ", TDS amount: " + safeTdsAmount
                                + ", Settlement amount: " + settlementAmount
                                + ", Outstanding: " + outstanding,
                        "ERR_FULL_AMOUNT_MISMATCH",
                        "amount"
                );
            }
            return;
        }

        // =====================================================
        // 7. PARTIAL PAYMENT VALIDATION
        // =====================================================
        // PARTIAL payment must be either:
        //   - 50% of total estimate amount, OR
        //   - Full outstanding (if outstanding is less than 50%)
        if ("PARTIAL".equals(code)) {

            BigDecimal half = total
                    .multiply(new BigDecimal("0.50"))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal expected = (outstanding.compareTo(half) < 0)
                    ? outstanding
                    : half;

            if (settlementAmount.compareTo(expected) != 0) {
                throw new ValidationException(
                        "PARTIAL payment settlement must be " + expected
                                + ". Bank amount: " + safeReqAmount
                                + ", TDS amount: " + safeTdsAmount
                                + ", Settlement amount: " + settlementAmount,
                        "ERR_PARTIAL_AMOUNT_MISMATCH",
                        "amount"
                );
            }
            return;
        }

        // =====================================================
        // 8. INSTALLMENT & PURCHASE ORDER
        // =====================================================
        // These payment types have flexible rules.
        // No strict settlement amount validation is applied here.
        if ("INSTALLMENT".equals(code) || "PURCHASE_ORDER".equals(code)) {
            return;
        }

        // =====================================================
        // 9. UNSUPPORTED PAYMENT TYPE
        // =====================================================
        throw new ValidationException(
                "Unsupported payment type: " + paymentType.getCode(),
                "ERR_UNSUPPORTED_PAYMENT_TYPE",
                "paymentTypeId"
        );
    }


    private void validateGovernmentFeeRequest(PaymentRegistrationRequestDto request) {
        log.debug("Validating government fee request | governmentFeeActive={}",
                request != null ? request.getGovernmentFeeActive() : null);

        if (Boolean.TRUE.equals(request.getGovernmentFeeActive())) {
            if (request.getGovernmentFee() == null) {
                throw new ValidationException(
                        "Government fee details are required when governmentFeeActive is true",
                        "ERR_GOV_FEE_DETAILS_REQUIRED",
                        "governmentFee"
                );
            }

            BigDecimal total = safe2(request.getGovernmentFee().getTotalAmount());
            BigDecimal received = safe2(request.getGovernmentFee().getReceivedAmount());

            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException(
                        "Government fee total amount must be positive",
                        "ERR_GOV_FEE_TOTAL_INVALID",
                        "governmentFee.totalAmount"
                );
            }

            if (received.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException(
                        "Government fee received amount must be positive",
                        "ERR_GOV_FEE_RECEIVED_INVALID",
                        "governmentFee.receivedAmount"
                );
            }

            if (total.compareTo(received) != 0) {
                throw new ValidationException(
                        "Government fee must be paid fully in one time. totalAmount and receivedAmount must be equal",
                        "ERR_GOV_FEE_MUST_BE_FULLY_PAID",
                        "governmentFee.receivedAmount"
                );
            }
        } else {
            if (request.getGovernmentFee() != null) {
                throw new ValidationException(
                        "Government fee details should not be sent when governmentFeeActive is false",
                        "ERR_GOV_FEE_NOT_ALLOWED",
                        "governmentFee"
                );
            }
        }
    }

    private void createGovernmentFeeIfRequired(
            PaymentRegistrationRequestDto request,
            Estimate estimate,
            UnbilledInvoice unbilled,
            User salesperson
    ) {
        if (!Boolean.TRUE.equals(request.getGovernmentFeeActive())) {
            log.debug("Government fee creation skipped | estimateId={} | unbilledId={}",
                    estimate != null ? estimate.getId() : null,
                    unbilled != null ? unbilled.getId() : null);
            return;
        }

        log.info("Creating government fee | estimateId={} | unbilledId={} | createdBy={}",
                estimate != null ? estimate.getId() : null,
                unbilled != null ? unbilled.getId() : null,
                salesperson != null ? salesperson.getId() : null);

        Optional<GovernmentFee> existingByEstimate = governmentFeeRepository.findByEstimate(estimate);
        Optional<GovernmentFee> existingByUnbilled = governmentFeeRepository.findByUnbilledInvoice(unbilled);

        GovernmentFee existingGovernmentFee = existingByUnbilled.orElse(existingByEstimate.orElse(null));

        if (existingGovernmentFee != null) {
            if (existingGovernmentFee.getStatus() == GovernmentFeeStatus.PENDING) {
                throw new ValidationException(
                        "Government fee is already registered and pending approval for this estimate/unbilled invoice",
                        "ERR_GOV_FEE_ALREADY_PENDING",
                        "governmentFee"
                );
            }

            if (existingGovernmentFee.getStatus() == GovernmentFeeStatus.APPROVED) {
                throw new ValidationException(
                        "Government fee is already approved for this estimate/unbilled invoice and cannot be added again",
                        "ERR_GOV_FEE_ALREADY_APPROVED",
                        "governmentFee"
                );
            }

            throw new ValidationException(
                    "Government fee already exists for this estimate/unbilled invoice",
                    "ERR_GOV_FEE_ALREADY_EXISTS",
                    "governmentFee"
            );
        }

        GovernmentFeeRequestDto govReq = request.getGovernmentFee();

        GovernmentFee governmentFee = new GovernmentFee();
        governmentFee.setPublicUuid(UUID.randomUUID().toString());
        governmentFee.setEstimate(estimate);
        governmentFee.setUnbilledInvoice(unbilled);
        governmentFee.setCompany(unbilled.getCompany());
        governmentFee.setUnit(unbilled.getUnit());
        governmentFee.setContact(unbilled.getContact());

        governmentFee.setFeeReferenceNumber(govReq.getFeeReferenceNumber());
        governmentFee.setDepartmentName(govReq.getDepartmentName());
        governmentFee.setFeeType(govReq.getFeeType());

        governmentFee.setTotalAmount(safe2(govReq.getTotalAmount()));
        governmentFee.setReceivedAmount(safe2(govReq.getReceivedAmount()));
        governmentFee.setOutstandingAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        governmentFee.setPaymentDate(govReq.getPaymentDate());
        governmentFee.setRemarks(govReq.getRemarks());

        governmentFee.setStatus(GovernmentFeeStatus.PENDING);
        governmentFee.setCreatedBy(salesperson);

        GovernmentFee savedGovernmentFee = governmentFeeRepository.save(governmentFee);

        unbilled.setGovernmentFeeActive(true);

        log.info("Government fee created | governmentFeeId={} | unbilledId={} | amount={}",
                savedGovernmentFee.getId(),
                unbilled.getId(),
                savedGovernmentFee.getTotalAmount());
    }

    private void validateTdsRequest(PaymentRegistrationRequestDto request, PaymentType paymentType) {


        boolean tdsActive = Boolean.TRUE.equals(request.getTdsActive());
        log.debug("Validating TDS request | tdsActive={} | paymentType={}",
                tdsActive,
                paymentType != null ? paymentType.getCode() : null);

        if (!tdsActive) {
            if (request.getTds() != null) {
                throw new ValidationException(
                        "TDS details should not be sent when tdsActive is false",
                        "ERR_TDS_NOT_ALLOWED",
                        "tds"
                );
            }
            return;
        }

        if (request.getTds() == null) {
            throw new ValidationException(
                    "TDS details are required when tdsActive is true",
                    "ERR_TDS_DETAILS_REQUIRED",
                    "tds"
            );
        }

        if (paymentType == null || paymentType.getCode() == null) {
            throw new ValidationException(
                    "Invalid payment type for TDS",
                    "ERR_TDS_PAYMENT_TYPE_INVALID",
                    "paymentTypeId"
            );
        }

        String paymentTypeCode = paymentType.getCode().trim().toUpperCase();

        if (
                !"FULL".equals(paymentTypeCode)
                        && !"PARTIAL".equals(paymentTypeCode)
                        && !"INSTALLMENT".equals(paymentTypeCode)
                        && !"PURCHASE_ORDER".equals(paymentTypeCode)
        ) {
            throw new ValidationException(
                    "TDS is not allowed for payment type: " + paymentTypeCode,
                    "ERR_TDS_NOT_ALLOWED_FOR_PAYMENT_TYPE",
                    "tds"
            );
        }

        if (request.getTds().getTdsPercentage() == null) {
            throw new ValidationException(
                    "TDS percentage is required",
                    "ERR_TDS_PERCENTAGE_REQUIRED",
                    "tds.tdsPercentage"
            );
        }

        BigDecimal tdsPercentage = safe2(request.getTds().getTdsPercentage());

        if (
                tdsPercentage.compareTo(new BigDecimal("2.00")) != 0
                        && tdsPercentage.compareTo(new BigDecimal("10.00")) != 0
        ) {
            throw new ValidationException(
                    "TDS percentage must be either 2 or 10",
                    "ERR_INVALID_TDS_PERCENTAGE",
                    "tds.tdsPercentage"
            );
        }

        log.debug("TDS request validated | paymentType={} | tdsPercentage={}", paymentTypeCode, tdsPercentage);
    }

    private void createTdsIfRequired(
            PaymentRegistrationRequestDto request,
            Estimate estimate,
            UnbilledInvoice unbilled,
            PaymentReceipt receipt,
            User salesperson,
            BigDecimal tdsAmount
    ) {
        // =====================================================
        // 1. BASIC VALIDATION
        // =====================================================

        if (request == null) {
            throw new ValidationException(
                    "Payment registration request is required for TDS registration",
                    "ERR_PAYMENT_REQUEST_REQUIRED_FOR_TDS",
                    "request"
            );
        }

        // =====================================================
        // 2. INTERNATIONAL TRANSACTION RESTRICTION
        // =====================================================


        // =====================================================
// 2. INTERNATIONAL TRANSACTION RESTRICTION
// =====================================================

        if (isInternationalTransaction(estimate, unbilled)) {

            /*
             * INTERNATIONAL transactions must never create
             * a domestic TDS registration.
             */
            if (unbilled != null) {
                unbilled.setTdsActive(false);
            }

            if (Boolean.TRUE.equals(request.getTdsActive())
                    || request.getTds() != null) {

                throw new ValidationException(
                        "TDS is not applicable for INTERNATIONAL transactions",
                        "ERR_TDS_NOT_ALLOWED_FOR_INTERNATIONAL",
                        "tdsActive"
                );
            }

            log.info(
                    "TDS creation skipped for INTERNATIONAL transaction | estimateId={} | unbilledId={} | paymentReceiptId={}",
                    estimate != null ? estimate.getId() : null,
                    unbilled != null ? unbilled.getId() : null,
                    receipt != null ? receipt.getId() : null
            );

            return;
        }

        // =====================================================
        // 3. TDS NOT ACTIVE
        // =====================================================

        if (!Boolean.TRUE.equals(request.getTdsActive())) {

            /*
             * When TDS is inactive, TDS details must not be supplied.
             */
            if (request.getTds() != null) {
                throw new ValidationException(
                        "TDS details should not be sent when tdsActive is false",
                        "ERR_TDS_NOT_ALLOWED",
                        "tds"
                );
            }

            return;
        }

        // =====================================================
        // 4. VALIDATE REQUIRED ENTITIES
        // =====================================================

        if (estimate == null || estimate.getId() == null) {
            throw new ValidationException(
                    "Estimate is required for TDS registration",
                    "ERR_ESTIMATE_REQUIRED_FOR_TDS",
                    "estimateId"
            );
        }

        if (estimate.getCompany() == null
                || estimate.getCompany().getId() == null) {

            throw new ValidationException(
                    "Company is required for TDS registration",
                    "ERR_COMPANY_REQUIRED_FOR_TDS",
                    "companyId"
            );
        }

        if (unbilled == null || unbilled.getId() == null) {
            throw new ValidationException(
                    "Unbilled invoice is required for TDS registration",
                    "ERR_UNBILLED_REQUIRED_FOR_TDS",
                    "unbilledId"
            );
        }

        if (receipt == null || receipt.getId() == null) {
            throw new ValidationException(
                    "Payment receipt is required for TDS registration",
                    "ERR_PAYMENT_RECEIPT_REQUIRED_FOR_TDS",
                    "paymentReceiptId"
            );
        }

        if (salesperson == null || salesperson.getId() == null) {
            throw new ValidationException(
                    "Salesperson is required for TDS registration",
                    "ERR_SALESPERSON_REQUIRED_FOR_TDS",
                    "salespersonUserId"
            );
        }

        // =====================================================
        // 5. VALIDATE TDS REQUEST
        // =====================================================

        if (request.getTds() == null) {
            throw new ValidationException(
                    "TDS details are required when TDS is active",
                    "ERR_TDS_DETAILS_REQUIRED",
                    "tds"
            );
        }

        if (request.getTds().getTdsPercentage() == null) {
            throw new ValidationException(
                    "TDS percentage is required",
                    "ERR_TDS_PERCENTAGE_REQUIRED",
                    "tds.tdsPercentage"
            );
        }

        BigDecimal tdsPercentage =
                safe2(request.getTds().getTdsPercentage());

        if (tdsPercentage.compareTo(new BigDecimal("2.00")) != 0
                && tdsPercentage.compareTo(new BigDecimal("10.00")) != 0) {

            throw new ValidationException(
                    "TDS percentage must be either 2 or 10",
                    "ERR_INVALID_TDS_PERCENTAGE",
                    "tds.tdsPercentage"
            );
        }

        BigDecimal safeTdsAmount =
                safe2(tdsAmount);

        if (safeTdsAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "TDS amount must be greater than zero when TDS is active",
                    "ERR_TDS_AMOUNT_INVALID",
                    "tds"
            );
        }

        // =====================================================
        // 6. PREVENT DUPLICATE TDS
        // =====================================================

        tdsRegistrationRepository
                .findByPaymentReceiptAndIsDeletedFalse(receipt)
                .ifPresent(existing -> {
                    throw new ValidationException(
                            "TDS is already registered for payment receipt ID: "
                                    + receipt.getId(),
                            "ERR_TDS_ALREADY_EXISTS_FOR_PAYMENT",
                            "tds"
                    );
                });

        // =====================================================
        // 7. CALCULATE CURRENT PAYMENT TAXABLE AMOUNT
        // =====================================================

        /*
         * Derive the taxable value represented by the TDS amount.
         *
         * Example:
         *
         * TDS amount = ₹500
         * TDS rate   = 2%
         *
         * Taxable amount = 500 × 100 / 2
         *                = ₹25,000
         */
        BigDecimal taxableAmount = safeTdsAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        tdsPercentage,
                        2,
                        RoundingMode.HALF_UP
                );

        if (taxableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Calculated taxable amount must be greater than zero",
                    "ERR_TDS_TAXABLE_AMOUNT_INVALID",
                    "tds"
            );
        }

        /*
         * Defensive validation against the complete estimate
         * taxable amount.
         */
        BigDecimal totalEstimateTaxableAmount =
                calculateTdsTaxableAmount(
                        estimate,
                        unbilled
                );

        if (totalEstimateTaxableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Estimate taxable amount is not available for TDS registration",
                    "ERR_TDS_TAXABLE_AMOUNT_NOT_FOUND",
                    "tds"
            );
        }

        if (taxableAmount.compareTo(totalEstimateTaxableAmount) > 0) {
            throw new ValidationException(
                    "Current payment taxable amount cannot exceed the estimate taxable amount",
                    "ERR_TDS_TAXABLE_AMOUNT_EXCEEDS_ESTIMATE",
                    "tds"
            );
        }

        // =====================================================
        // 8. CREATE TDS REGISTRATION
        // =====================================================

        TdsRegistration tds = new TdsRegistration();

        tds.setPublicUuid(
                UUID.randomUUID().toString()
        );

        tds.setEstimate(estimate);
        tds.setCompany(estimate.getCompany());
        tds.setUnbilledInvoice(unbilled);
        tds.setPaymentReceipt(receipt);

        tds.setTdsPercentage(tdsPercentage);
        tds.setTaxableAmount(taxableAmount);
        tds.setTdsAmount(safeTdsAmount);

        /*
         * TDS date is set during Accounts approval when the
         * corresponding invoice is generated.
         */
        tds.setTdsDate(null);

        tds.setStatus(TdsStatus.PENDING);
        tds.setDeleted(false);

        tds.setCreatedBy(salesperson);
        tds.setUpdatedBy(salesperson);

        TdsRegistration savedTds =
                tdsRegistrationRepository.save(tds);

        // =====================================================
        // 9. UPDATE UNBILLED TDS FLAG
        // =====================================================

        unbilled.setTdsActive(true);
        unbilled.setUpdatedAt(LocalDateTime.now());

        /*
         * Saving is optional if the caller saves unbilled later,
         * but explicitly saving here makes the method self-contained.
         */
        unbilledInvoiceRepository.save(unbilled);

        log.info(
                "TDS registered successfully | tdsId={} | estimateId={} | unbilledId={} "
                        + "| paymentReceiptId={} | gstRegistrationType={} | taxableAmount={} "
                        + "| percentage={} | tdsAmount={} | status={}",
                savedTds.getId(),
                estimate.getId(),
                unbilled.getId(),
                receipt.getId(),
                resolveGstRegistrationType(estimate, unbilled),
                taxableAmount,
                tdsPercentage,
                safeTdsAmount,
                savedTds.getStatus()
        );
    }

    private BigDecimal calculateTdsAmountIfRequired(
            PaymentRegistrationRequestDto request,
            Estimate estimate,
            UnbilledInvoice unbilled
    ) {

        final BigDecimal zero =
                BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        // =====================================================
        // 1. BASIC REQUEST VALIDATION
        // =====================================================
        if (request == null) {
            throw new ValidationException(
                    "Payment registration request is required",
                    "ERR_PAYMENT_REQUEST_REQUIRED",
                    "request"
            );
        }

        // =====================================================
        // 2. INTERNATIONAL TRANSACTION RESTRICTION
        // =====================================================
        if (isInternationalTransaction(estimate, unbilled)) {

            /*
             * Domestic TDS is not applicable to an
             * INTERNATIONAL transaction.
             */
            if (Boolean.TRUE.equals(request.getTdsActive())
                    || request.getTds() != null) {

                throw new ValidationException(
                        "TDS is not applicable for INTERNATIONAL transactions",
                        "ERR_TDS_NOT_ALLOWED_FOR_INTERNATIONAL",
                        "tdsActive"
                );
            }

            return zero;
        }

        // =====================================================
        // 3. TDS NOT ACTIVE
        // =====================================================
        if (!Boolean.TRUE.equals(request.getTdsActive())) {
            return zero;
        }

        // =====================================================
        // 4. VALIDATE TDS DETAILS
        // =====================================================
        if (request.getTds() == null) {
            throw new ValidationException(
                    "TDS details are required when TDS is active",
                    "ERR_TDS_DETAILS_REQUIRED",
                    "tds"
            );
        }

        if (request.getTds().getTdsPercentage() == null) {
            throw new ValidationException(
                    "TDS percentage is required when TDS is active",
                    "ERR_TDS_PERCENTAGE_REQUIRED",
                    "tds.tdsPercentage"
            );
        }

        if (request.getAmount() == null) {
            throw new ValidationException(
                    "Payment amount is required when TDS is active",
                    "ERR_TDS_PAYMENT_AMOUNT_REQUIRED",
                    "amount"
            );
        }

        BigDecimal paymentAmount =
                safe2(request.getAmount());

        BigDecimal tdsPercentage =
                safe2(
                        request.getTds()
                                .getTdsPercentage()
                );

        /*
         * paymentAmount is the actual amount received in the
         * selected Bank/Cash/Payment Gateway ledger.
         */
        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Payment amount must be greater than zero when TDS is active",
                    "ERR_TDS_PAYMENT_AMOUNT_REQUIRED",
                    "amount"
            );
        }

        // =====================================================
        // 5. VALIDATE TDS PERCENTAGE
        // =====================================================
        if (tdsPercentage.compareTo(new BigDecimal("2.00")) != 0
                && tdsPercentage.compareTo(new BigDecimal("10.00")) != 0) {

            throw new ValidationException(
                    "TDS percentage must be either 2 or 10",
                    "ERR_INVALID_TDS_PERCENTAGE",
                    "tds.tdsPercentage"
            );
        }

        // =====================================================
        // 6. CALCULATE TOTAL TAXABLE AMOUNT
        // =====================================================

        /*
         * TDS is calculated on taxable value excluding GST.
         */
        BigDecimal totalTaxableAmount =
                safe2(
                        calculateTdsTaxableAmount(
                                estimate,
                                unbilled
                        )
                );

        if (totalTaxableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Taxable amount excluding GST is not available for TDS calculation",
                    "ERR_TDS_TAXABLE_AMOUNT_NOT_FOUND",
                    "tds"
            );
        }

        // =====================================================
        // 7. RESOLVE TOTAL INVOICE AMOUNT
        // =====================================================
        BigDecimal totalInvoiceAmount;

        if (estimate != null
                && estimate.getGrandTotal() != null) {

            totalInvoiceAmount =
                    safe2(estimate.getGrandTotal());

        } else if (unbilled != null
                && unbilled.getTotalAmount() != null) {

            totalInvoiceAmount =
                    safe2(unbilled.getTotalAmount());

        } else {
            totalInvoiceAmount = zero;
        }

        if (totalInvoiceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Invoice total amount is not available for TDS calculation",
                    "ERR_TDS_TOTAL_AMOUNT_NOT_FOUND",
                    "tds"
            );
        }

        if (totalTaxableAmount.compareTo(totalInvoiceAmount) > 0) {
            throw new ValidationException(
                    "Taxable amount cannot be greater than invoice total amount",
                    "ERR_INVALID_TDS_TAXABLE_AMOUNT",
                    "tds"
            );
        }

        // =====================================================
        // 8. RESOLVE OUTSTANDING AMOUNT
        // =====================================================
        BigDecimal outstandingAmount =
                unbilled != null
                        && unbilled.getOutstandingAmount() != null
                        ? safe2(unbilled.getOutstandingAmount())
                        : totalInvoiceAmount;

        if (outstandingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "No outstanding amount is available for payment settlement",
                    "ERR_NO_OUTSTANDING_FOR_TDS",
                    "amount"
            );
        }

        // =====================================================
        // 9. CALCULATE TOTAL TDS LIMIT
        // =====================================================

        /*
         * Example:
         *
         * Taxable amount = ₹50,000
         * TDS percentage = 10%
         *
         * Total TDS allowed = ₹5,000
         */
        BigDecimal totalAllowedTds =
                totalTaxableAmount
                        .multiply(tdsPercentage)
                        .divide(
                                BigDecimal.valueOf(100),
                                2,
                                RoundingMode.HALF_UP
                        );

        if (totalAllowedTds.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Calculated total TDS limit must be greater than zero",
                    "ERR_TDS_TOTAL_LIMIT_INVALID",
                    "tds"
            );
        }

        // =====================================================
        // 10. CALCULATE USED AND REMAINING TDS
        // =====================================================
        BigDecimal alreadyUsedTds =
                safe2(
                        getTotalActiveTdsAmount(unbilled)
                );

        if (alreadyUsedTds.compareTo(totalAllowedTds) > 0) {
            throw new ValidationException(
                    "Previously registered TDS exceeds the total TDS allowed for this estimate. "
                            + "Total allowed TDS: ₹" + totalAllowedTds
                            + ", already registered TDS: ₹" + alreadyUsedTds,
                    "ERR_USED_TDS_EXCEEDS_ALLOWED_LIMIT",
                    "tds"
            );
        }

        BigDecimal remainingTdsLimit =
                totalAllowedTds
                        .subtract(alreadyUsedTds)
                        .max(BigDecimal.ZERO)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (remainingTdsLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "TDS limit is already exhausted for this estimate. "
                            + "Total allowed TDS is ₹" + totalAllowedTds
                            + " and already used TDS is ₹" + alreadyUsedTds,
                    "ERR_TDS_LIMIT_EXHAUSTED",
                    "tds"
            );
        }

        // =====================================================
        // 11. CALCULATE REMAINING NET BANK RECEIVABLE
        // =====================================================

        /*
         * Outstanding liability is settled through:
         *
         *     Bank amount + TDS amount
         *
         * Therefore:
         *
         *     Remaining net bank receivable
         *         = Outstanding amount - Remaining TDS
         *
         * Example:
         *
         * Outstanding amount     = ₹50,000
         * Remaining TDS          = ₹5,000
         * Net bank receivable    = ₹45,000
         */
        BigDecimal remainingNetBankReceivable =
                outstandingAmount
                        .subtract(remainingTdsLimit)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (remainingNetBankReceivable.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Remaining net bank receivable is invalid. "
                            + "Outstanding amount: ₹" + outstandingAmount
                            + ", remaining TDS limit: ₹" + remainingTdsLimit,
                    "ERR_INVALID_NET_BANK_RECEIVABLE",
                    "tds"
            );
        }

        /*
         * Actual bank receipt cannot be higher than the remaining
         * net bank receivable when TDS is being applied.
         */
        if (paymentAmount.compareTo(remainingNetBankReceivable) > 0) {
            throw new ValidationException(
                    "Bank payment amount exceeds the maximum amount receivable after TDS. "
                            + "Bank amount: ₹" + paymentAmount
                            + ", maximum bank receivable: ₹"
                            + remainingNetBankReceivable
                            + ", remaining TDS: ₹" + remainingTdsLimit
                            + ", outstanding amount: ₹" + outstandingAmount,
                    "ERR_BANK_AMOUNT_EXCEEDS_NET_RECEIVABLE",
                    "amount"
            );
        }

        // =====================================================
        // 12. CALCULATE TDS FOR CURRENT PAYMENT
        // =====================================================

        /*
         * TDS is allocated proportionately against the actual
         * bank receipt.
         *
         * Formula:
         *
         * Current TDS =
         *     Current Bank Amount
         *     × Remaining TDS
         *     ÷ Remaining Net Bank Receivable
         *
         * Full-payment example:
         *
         * Current bank amount            = ₹45,000
         * Remaining TDS                  = ₹5,000
         * Remaining net bank receivable  = ₹45,000
         *
         * Current TDS:
         *
         * ₹45,000 × ₹5,000 ÷ ₹45,000
         * = ₹5,000
         *
         * Settlement:
         *
         * ₹45,000 + ₹5,000
         * = ₹50,000
         */
        BigDecimal calculatedTds =
                paymentAmount
                        .multiply(remainingTdsLimit)
                        .divide(
                                remainingNetBankReceivable,
                                2,
                                RoundingMode.HALF_UP
                        );

        /*
         * Defensive cap for rounding differences.
         */
        calculatedTds =
                calculatedTds
                        .min(remainingTdsLimit)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (calculatedTds.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Calculated TDS amount must be greater than zero",
                    "ERR_TDS_AMOUNT_INVALID",
                    "tds"
            );
        }

        // =====================================================
        // 13. CALCULATE SETTLEMENT AMOUNT
        // =====================================================
        BigDecimal settlementAmount =
                paymentAmount
                        .add(calculatedTds)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        // =====================================================
        // 14. FINAL SETTLEMENT SAFETY CHECK
        // =====================================================
        if (settlementAmount.compareTo(outstandingAmount) > 0) {
            throw new ValidationException(
                    "Payment amount plus TDS amount exceeds the outstanding amount. "
                            + "Bank amount: ₹" + paymentAmount
                            + ", TDS amount: ₹" + calculatedTds
                            + ", settlement amount: ₹" + settlementAmount
                            + ", outstanding amount: ₹" + outstandingAmount,
                    "ERR_TDS_SETTLEMENT_EXCEEDS_OUTSTANDING",
                    "amount"
            );
        }

        log.info(
                "TDS calculated | estimateId={} | unbilledId={} "
                        + "| gstRegistrationType={} | taxableAmount={} "
                        + "| invoiceTotal={} | outstandingAmount={} "
                        + "| bankAmount={} | tdsPercentage={} "
                        + "| totalAllowedTds={} | alreadyUsedTds={} "
                        + "| remainingTdsLimit={} "
                        + "| remainingNetBankReceivable={} "
                        + "| calculatedTds={} | settlementAmount={}",
                estimate != null
                        ? estimate.getId()
                        : null,
                unbilled != null
                        ? unbilled.getId()
                        : null,
                resolveGstRegistrationType(
                        estimate,
                        unbilled
                ),
                totalTaxableAmount,
                totalInvoiceAmount,
                outstandingAmount,
                paymentAmount,
                tdsPercentage,
                totalAllowedTds,
                alreadyUsedTds,
                remainingTdsLimit,
                remainingNetBankReceivable,
                calculatedTds,
                settlementAmount
        );

        return calculatedTds;
    }





    private BigDecimal safe2(BigDecimal val) {
        return (val == null ? BigDecimal.ZERO : val).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public UnbilledInvoiceApprovalResponseDto updateUnbilledInvoiceStatus(
            Long unbilledId,
            UnbilledInvoiceApprovalRequestDto request) {

        if (request == null) {
            throw new ValidationException(
                    "Approval request is required",
                    "ERR_APPROVAL_REQUEST_REQUIRED",
                    "request"
            );
        }

        if (request.getApproverUserId() == null) {
            throw new ValidationException(
                    "Approver user ID is required",
                    "ERR_APPROVER_USER_REQUIRED",
                    "approverUserId"
            );
        }

        log.info(
                "Approving unbilled invoice | unbilledId: {}, approverId: {}",
                unbilledId,
                request.getApproverUserId()
        );

        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled invoice not found with ID: " + unbilledId,
                        "UNBILLED_NOT_FOUND",
                        "UnbilledInvoice",
                        unbilledId
                ));

        if (unbilled.getStatus() != UnbilledStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Only PENDING_APPROVAL unbilled invoices can be approved/rejected. Current status: "
                            + unbilled.getStatus()
            );
        }

        String approvalDecision = request.getApprovalRemarks() != null
                ? request.getApprovalRemarks().trim().toUpperCase()
                : "";

        if (!"APPROVED".equals(approvalDecision) && !"REJECTED".equals(approvalDecision)) {
            throw new ValidationException(
                    "Invalid approval decision. Allowed values are APPROVED or REJECTED",
                    "ERR_INVALID_APPROVAL_DECISION",
                    "approvalRemarks"
            );
        }

        log.info(
                "Unbilled approval decision received | unbilledId={} | unbilledNumber={} | decision={}",
                unbilled.getId(),
                unbilled.getUnbilledNumber(),
                approvalDecision
        );

        Company company = unbilled.getCompany();
        CompanyUnit unit = unbilled.getUnit();
        Estimate estimate = unbilled.getEstimate();

        if (estimate == null) {
            throw new ResourceNotFoundException(
                    "Estimate not found for unbilled invoice: " + unbilled.getUnbilledNumber(),
                    "ESTIMATE_NOT_FOUND"
            );
        }

        User approver = userRepository.findById(request.getApproverUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approver not found with ID: " + request.getApproverUserId(),
                        "USER_NOT_FOUND",
                        "User",
                        request.getApproverUserId()
                ));

        // Company & Unit must be approved for APPROVED flow only
        if ("APPROVED".equals(approvalDecision)) {
            boolean companyApproved = company != null
                    && !company.isDeleted()
                    && (
                    company.isAccountsApproved()
                            || company.getOnboardingStatus() == OnboardingStatus.APPROVED
            );

            boolean unitApproved = unit != null
                    && !unit.isDeleted()
                    && (
                    unit.isAccountsApproved()
                            || unit.getOnboardingStatus() == OnboardingStatus.APPROVED
            );

            if (!companyApproved || !unitApproved) {
                throw new ApprovalBlockedException(
                        "Cannot approve unbilled invoice. Company and Company Unit must both be approved before invoice approval.",
                        companyApproved,
                        unitApproved
                );
            }
        }

        /*
         * Important:
         * Declare this once before REJECTED and APPROVED flow.
         * This same pending payment list is used for approval/rejection.
         */
        List<PaymentReceipt> paymentsToApprove = unbilled.getPayments() == null
                ? List.of()
                : unbilled.getPayments()
                .stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING && !p.isCancelled())
                .toList();

        if (paymentsToApprove.isEmpty()) {
            throw new ValidationException(
                    "No pending payment found for approval/rejection",
                    "ERR_NO_PENDING_PAYMENT_FOUND",
                    "payments"
            );
        }

        log.info(
                "Pending payments found for decision | unbilledId={} | paymentCount={}",
                unbilled.getId(),
                paymentsToApprove.size()
        );

        boolean initialPurchaseOrderApproval = paymentsToApprove.stream()
                .anyMatch(payment ->
                        isPurchaseOrderPayment(payment)
                                && safe2(payment.getAmount()).compareTo(BigDecimal.ZERO) == 0
                );

        // ==================== REJECTED FLOW ====================
        if ("REJECTED".equals(approvalDecision)) {

            unbilled.setStatus(UnbilledStatus.REJECTED);

            paymentsToApprove.forEach(payment -> payment.setStatus(PaymentStatus.REJECTED));

            unbilled.setCurrentReceivedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

            governmentFeeRepository.findByUnbilledInvoice(unbilled).ifPresent(gf -> {
                if (gf.getStatus() == GovernmentFeeStatus.PENDING) {
                    governmentFeeRepository.delete(gf);
                    unbilled.setGovernmentFeeActive(false);
                }
            });

            /*
             * Rejected payment means pending TDS should be removed.
             * Do not approve TDS in rejected flow.
             */
            for (PaymentReceipt payment : paymentsToApprove) {
                tdsRegistrationRepository.findByPaymentReceiptAndIsDeletedFalse(payment)
                        .ifPresent(tds -> {
                            if (tds.getStatus() == TdsStatus.PENDING) {
                                tdsRegistrationRepository.delete(tds);
                            }
                        });
            }

            boolean hasAnyActiveTds =
                    !isInternationalTransaction(estimate, unbilled)
                            && tdsRegistrationRepository
                            .findAllByUnbilledInvoiceAndIsDeletedFalse(unbilled)
                            .stream()
                            .anyMatch(tds ->
                                    tds.getStatus() == TdsStatus.PENDING
                                            || tds.getStatus() == TdsStatus.APPROVED
                            );

            unbilled.setTdsActive(hasAnyActiveTds);

            unbilled.setApprovedBy(approver);
            unbilled.setApprovedAt(dateTimeUtil.nowLocalDateTime());
            unbilled.setApprovalRemarks(request.getApprovalRemarks());

            estimateRepository.save(estimate);
            unbilledInvoiceRepository.save(unbilled);

            pushPaymentApprovalDecisionNotificationToSalesperson(
                    unbilled,
                    estimate,
                    approver,
                    false,
                    request.getApprovalRemarks()
            );

            log.info("Unbilled {} rejected.", unbilled.getUnbilledNumber());

            UnbilledInvoiceApprovalResponseDto response = new UnbilledInvoiceApprovalResponseDto();

            response.setName(
                    estimate.getSolutionName() != null
                            ? estimate.getSolutionName()
                            : company != null
                            ? company.getName() + " - Project"
                            : "Unnamed Project"
            );
            response.setProjectNo(generateProjectNumber());
            response.setSalesPersonId(
                    unbilled.getCreatedBy() != null
                            ? unbilled.getCreatedBy().getId()
                            : null
            );
            response.setSalesPersonName(
                    unbilled.getCreatedBy() != null
                            ? (
                            unbilled.getCreatedBy().getFullName() != null
                                    ? unbilled.getCreatedBy().getFullName()
                                    : unbilled.getCreatedBy().getEmail()
                    )
                            : null
            );
            response.setProductId(estimate.getSolutionId());
            response.setCompanyId(company != null ? company.getId() : null);
            response.setCompanyUnitId(unit != null ? unit.getId() : null);
            response.setUnbilledNumber(unbilled.getUnbilledNumber());
            response.setAdvanceInvoiceNumber(unbilled.getAdvanceInvoiceNumber());
            response.setAdvanceInvoiceFlag(unbilled.isAdvanceInvoiceFlag());
            response.setEstimateNumber(estimate.getEstimateNumber());
            response.setContactId(
                    unbilled.getContact() != null
                            ? unbilled.getContact().getId()
                            : null
            );
            response.setLeadId(estimate.getLeadId());
            response.setDate(LocalDate.now());
            response.setTotalAmount(
                    unbilled.getTotalAmount() != null
                            ? unbilled.getTotalAmount().doubleValue()
                            : 0.0
            );
            response.setPaidAmount(
                    unbilled.getReceivedAmount() != null
                            ? unbilled.getReceivedAmount().doubleValue()
                            : 0.0
            );
            response.setPaymentTypeId(null);
            response.setApprovedById(approver.getId());
            response.setCreatedBy(
                    unbilled.getCreatedBy() != null
                            ? unbilled.getCreatedBy().getId()
                            : null
            );
            response.setUpdatedBy(approver.getId());

            if (unit != null) {
                response.setAddress(buildUnitAddress(unit));
                response.setCity(unit.getCity());
                response.setState(unit.getState());
                response.setCountry(unit.getCountry() != null ? unit.getCountry() : "India");
                response.setPrimaryPinCode(unit.getPinCode());
            }

            return response;
        }

        // ==================== APPROVED FLOW ====================

        unbilled.setStatus(UnbilledStatus.APPROVED);
        estimate.setStatus(EstimateStatus.APPROVED);

        paymentsToApprove.forEach(payment -> payment.setStatus(PaymentStatus.APPROVED));

        /*
         * Correct payment-wise TDS calculation:
         * Bank amount + TDS amount = settlement amount.
         */
        BigDecimal newlyApprovedBankAmount = paymentsToApprove.stream()
                .map(PaymentReceipt::getAmount)
                .filter(Objects::nonNull)
                .map(this::safe2)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);

        boolean internationalTransaction =
                isInternationalTransaction(
                        estimate,
                        unbilled
                );

        BigDecimal newlyApprovedTdsAmount =
                internationalTransaction
                        ? BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP
                )
                        : paymentsToApprove.stream()
                        .map(this::getPendingTdsAmountForPayment)
                        .reduce(
                                BigDecimal.ZERO.setScale(
                                        2,
                                        RoundingMode.HALF_UP
                                ),
                                BigDecimal::add
                        );

        if (internationalTransaction) {
            unbilled.setTdsActive(false);
        }


        BigDecimal newlyApprovedAmount = newlyApprovedBankAmount
                .add(newlyApprovedTdsAmount)
                .setScale(2, RoundingMode.HALF_UP);

        log.info(
                "Approving payments | unbilledId={} | bankAmount={} | tdsAmount={} | settlementAmount={}",
                unbilled.getId(),
                newlyApprovedBankAmount,
                newlyApprovedTdsAmount,
                newlyApprovedAmount
        );

        BigDecimal updatedReceived = safe2(unbilled.getReceivedAmount())
                .add(newlyApprovedAmount)
                .setScale(2, RoundingMode.HALF_UP);

        unbilled.setReceivedAmount(updatedReceived);
        unbilled.setCurrentReceivedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        unbilled.setOutstandingAmount(
                safe2(unbilled.getTotalAmount())
                        .subtract(updatedReceived)
                        .max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP)
        );

        /*
         * Post receipt voucher for actual received payments only.
         *
         * Initial PURCHASE_ORDER has amount = 0, so no voucher will be posted.
         *
         * Normal payments and later PO actual payments:
         * Dr Bank / Cash Ledger
         * Dr TDS Receivable, if applicable
         * Cr Customer Ledger
         */
        for (PaymentReceipt payment : paymentsToApprove) {

            if (safe2(payment.getAmount()).compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal tdsForVoucher =
                    internationalTransaction
                            ? BigDecimal.ZERO.setScale(
                            2,
                            RoundingMode.HALF_UP
                    )
                            : getPendingTdsAmountForPayment(payment);

            postReceiptVoucherForApprovedPayment(
                    unbilled,
                    payment,
                    approver,
                    tdsForVoucher
            );
        }
        /*
         * Generate invoice payment-wise.
         *
         * For initial PURCHASE_ORDER approval:
         * - amount = 0
         * - project should be created
         * - tax invoice should NOT be generated
         *
         * For normal payments and later PO actual payments:
         * - invoice generation remains same
         */
        for (PaymentReceipt payment : paymentsToApprove) {

            boolean skipInvoiceForInitialPurchaseOrder =
                    isPurchaseOrderPayment(payment)
                            && safe2(payment.getAmount()).compareTo(BigDecimal.ZERO) == 0;

            if (skipInvoiceForInitialPurchaseOrder) {
                log.info(
                        "Skipping tax invoice generation for initial PURCHASE_ORDER approval | unbilled={} | paymentReceiptId={}",
                        unbilled.getUnbilledNumber(),
                        payment.getId()
                );
                continue;
            }

            Invoice invoice = invoiceRepository
                    .findByTriggeringPaymentAndIsCancelledFalse(payment)
                    .orElse(null);

            if (invoice == null) {

                /*
                 * For later PURCHASE_ORDER actual payment:
                 * Tax invoice can be generated only after Operation says
                 * all non-Certification milestones are completed.
                 *
                 * Initial PO amount = 0 is already skipped above.
                 */
                if (isPurchaseOrderPayment(payment)) {
                    validatePoBillingEligibility(unbilled);
                }

                BigDecimal tdsForInvoice = getTdsAmountForPayment(payment);

                invoice = invoiceService.generateInvoiceForPayment(
                        unbilled,
                        payment,
                        approver,
                        tdsForInvoice
                );
            }

            approveTdsForPaymentAfterInvoiceCreated(
                    payment,
                    approver,
                    invoice
            );
        }

        governmentFeeRepository.findByUnbilledInvoice(unbilled).ifPresent(gf -> {
            if (gf.getStatus() == GovernmentFeeStatus.PENDING) {
                gf.setStatus(GovernmentFeeStatus.APPROVED);
                governmentFeeRepository.save(gf);
            }
        });

        boolean hasAnyActiveTds =
                !isInternationalTransaction(estimate, unbilled)
                        && tdsRegistrationRepository
                        .findAllByUnbilledInvoiceAndIsDeletedFalse(unbilled)
                        .stream()
                        .anyMatch(tds ->
                                tds.getStatus() == TdsStatus.PENDING
                                        || tds.getStatus() == TdsStatus.APPROVED
                        );

        unbilled.setTdsActive(hasAnyActiveTds);


        unbilled.setApprovedBy(approver);
        unbilled.setApprovedAt(dateTimeUtil.nowLocalDateTime());
        unbilled.setApprovalRemarks(request.getApprovalRemarks());

        estimateRepository.save(estimate);
        unbilledInvoiceRepository.save(unbilled);

        PaymentReceipt triggeringReceipt = paymentsToApprove.stream()
                .filter(p -> p.getCreatedAt() != null)
                .max(Comparator.comparing(PaymentReceipt::getCreatedAt))
                .orElse(paymentsToApprove.get(paymentsToApprove.size() - 1));

        pushPaymentApprovalDecisionNotificationToSalesperson(
                unbilled,
                estimate,
                approver,
                true,
                request.getApprovalRemarks()
        );

        UnbilledInvoiceApprovalResponseDto response = new UnbilledInvoiceApprovalResponseDto();

        response.setName(
                estimate.getSolutionName() != null
                        ? estimate.getSolutionName()
                        : company != null
                        ? company.getName() + " - Project"
                        : "Unnamed Project"
        );
        response.setProjectNo(generateProjectNumber());
        response.setSalesPersonId(
                unbilled.getCreatedBy() != null
                        ? unbilled.getCreatedBy().getId()
                        : null
        );
        response.setSalesPersonName(
                unbilled.getCreatedBy() != null
                        ? (
                        unbilled.getCreatedBy().getFullName() != null
                                ? unbilled.getCreatedBy().getFullName()
                                : unbilled.getCreatedBy().getEmail()
                )
                        : null
        );
        response.setProductId(estimate.getSolutionId());
        response.setCompanyId(company != null ? company.getId() : null);
        response.setCompanyUnitId(unit != null ? unit.getId() : null);
        response.setUnbilledNumber(unbilled.getUnbilledNumber());
        response.setAdvanceInvoiceNumber(unbilled.getAdvanceInvoiceNumber());
        response.setAdvanceInvoiceFlag(unbilled.isAdvanceInvoiceFlag());
        response.setEstimateNumber(estimate.getEstimateNumber());
        response.setContactId(
                unbilled.getContact() != null
                        ? unbilled.getContact().getId()
                        : null
        );
        response.setLeadId(estimate.getLeadId());
        response.setDate(LocalDate.now());
        response.setTotalAmount(
                unbilled.getTotalAmount() != null
                        ? unbilled.getTotalAmount().doubleValue()
                        : 0.0
        );
        response.setPaidAmount(
                unbilled.getReceivedAmount() != null
                        ? unbilled.getReceivedAmount().doubleValue()
                        : 0.0
        );
        response.setPaymentTypeId(
                triggeringReceipt != null && triggeringReceipt.getPaymentType() != null
                        ? triggeringReceipt.getPaymentType().getId()
                        : null
        );
        response.setApprovedById(approver.getId());
        response.setCreatedBy(
                unbilled.getCreatedBy() != null
                        ? unbilled.getCreatedBy().getId()
                        : null
        );
        response.setUpdatedBy(approver.getId());

        if (unit != null) {
            response.setAddress(buildUnitAddress(unit));
            response.setCity(unit.getCity());
            response.setState(unit.getState());
            response.setCountry(unit.getCountry() != null ? unit.getCountry() : "India");
            response.setPrimaryPinCode(unit.getPinCode());
        }

        /*
         * Initial PURCHASE_ORDER approval:
         * After Accounts approval, create Operation project.
         * Invoice and receipt voucher are skipped because amount = 0.
         */
        if (initialPurchaseOrderApproval) {
            createOperationProjectForPurchaseOrderIfNotExists(
                    unbilled,
                    estimate,
                    response
            );
        }

        log.info(
                "Unbilled approval completed | unbilledId={} | decision={} | receivedAmount={} | outstandingAmount={}",
                unbilled.getId(),
                approvalDecision,
                unbilled.getReceivedAmount(),
                unbilled.getOutstandingAmount()
        );

        return response;
    }




    private BigDecimal getPendingTdsAmountForPayment(
            PaymentReceipt paymentReceipt
    ) {
        if (paymentReceipt == null) {
            return BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );
        }

        UnbilledInvoice unbilled =
                paymentReceipt.getUnbilledInvoice();

        Estimate estimate =
                unbilled != null
                        ? unbilled.getEstimate()
                        : null;

        /*
         * Ignore old/invalid TDS rows linked with an
         * INTERNATIONAL payment.
         */
        if (isInternationalTransaction(estimate, unbilled)) {
            return BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );
        }

        return tdsRegistrationRepository
                .findByPaymentReceiptAndIsDeletedFalse(paymentReceipt)
                .filter(tds ->
                        tds.getStatus() == TdsStatus.PENDING
                )
                .map(TdsRegistration::getTdsAmount)
                .map(this::safe2)
                .orElse(
                        BigDecimal.ZERO.setScale(
                                2,
                                RoundingMode.HALF_UP
                        )
                );
    }




    private BigDecimal getTdsAmountForPayment(
            PaymentReceipt paymentReceipt
    ) {
        if (paymentReceipt == null) {
            return BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );
        }

        UnbilledInvoice unbilled =
                paymentReceipt.getUnbilledInvoice();

        Estimate estimate =
                unbilled != null
                        ? unbilled.getEstimate()
                        : null;

        if (isInternationalTransaction(estimate, unbilled)) {
            return BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );
        }

        return tdsRegistrationRepository
                .findByPaymentReceiptAndIsDeletedFalse(paymentReceipt)
                .filter(tds ->
                        tds.getStatus() == TdsStatus.PENDING
                                || tds.getStatus() == TdsStatus.APPROVED
                )
                .map(TdsRegistration::getTdsAmount)
                .map(this::safe2)
                .orElse(
                        BigDecimal.ZERO.setScale(
                                2,
                                RoundingMode.HALF_UP
                        )
                );
    }

    private String getUserDisplayName(User user) {
        if (user == null) return null;
        return user.getFullName() != null ? user.getFullName() : user.getEmail();
    }

    private String getUserDisplayName2(User user) {
        if (user == null) {
            return "System";
        }

        if (user.getFullName() != null && !user.getFullName().trim().isEmpty()) {
            return user.getFullName().trim();
        }

        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            return user.getEmail().trim();
        }

        return "User";
    }

    private UnbilledInvoiceSummaryDto mapToSummaryDto(UnbilledInvoice unbilled) {
        UnbilledInvoiceSummaryDto dto = new UnbilledInvoiceSummaryDto();

        dto.setId(unbilled.getId());
        dto.setUnbilledNumber(unbilled.getUnbilledNumber());

        dto.setAdvanceInvoiceNumber(unbilled.getAdvanceInvoiceNumber());
        dto.setAdvanceInvoiceFlag(unbilled.isAdvanceInvoiceFlag());

        Estimate estimate = unbilled.getEstimate();
        dto.setEstimateNumber(estimate != null ? estimate.getEstimateNumber() : null);
        dto.setEstimateId(estimate != null ? estimate.getId() : null);
        dto.setSolutionId(estimate != null ? estimate.getSolutionId() : null);
        dto.setSolutionName(estimate != null ? estimate.getSolutionName() : null);
        dto.setLeadId(estimate != null ? estimate.getLeadId() : null);

        Company company = unbilled.getCompany();
        dto.setCompanyName(company != null ? company.getName() : null);

        Contact contact = unbilled.getContact();
        dto.setContactName(contact != null ? contact.getName() : null);
        dto.setEmails(contact != null ? contact.getEmails() : null);
        dto.setContactNo(contact != null ? contact.getContactNo() : null);

        CompanyUnit unit = unbilled.getUnit();
        if (unit != null) {
            dto.setAddressLine1(unit.getAddressLine1());
            dto.setAddressLine2(unit.getAddressLine2());
            dto.setCity(unit.getCity());
            dto.setState(unit.getState());
            dto.setCountry(unit.getCountry() != null ? unit.getCountry() : "India");
            dto.setPinCode(unit.getPinCode());
            dto.setGstNo(unit.getGstNo());
        }

        if (unbilled.getReceivedAmount() != null
                && unbilled.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0) {

            if (unbilled.getPayments() != null && !unbilled.getPayments().isEmpty()) {
                PaymentReceipt receipt = unbilled.getPayments().get(0);

                if (receipt.getPaymentType() != null) {
                    dto.setPaymentTypeId(receipt.getPaymentType().getId());
                    dto.setPaymentTypeCode(receipt.getPaymentType().getCode());
                } else {
                    dto.setPaymentTypeId(null);
                    dto.setPaymentTypeCode(null);
                }
            } else {
                dto.setPaymentTypeId(null);
                dto.setPaymentTypeCode(null);
            }

        } else {
            dto.setPaymentTypeId(null);
            dto.setPaymentTypeCode(null);
        }


        dto.setTotalAmount(unbilled.getTotalAmount());
        dto.setReceivedAmount(unbilled.getReceivedAmount());
        dto.setCurrentReceivedAmount(unbilled.getCurrentReceivedAmount());
        dto.setOutstandingAmount(unbilled.getOutstandingAmount());
        dto.setGovernmentFeeActiveFlag(unbilled.isGovernmentFeeActive());
        boolean tdsAllowed =
                !isInternationalTransaction(
                        estimate,
                        unbilled
                );

        boolean tdsActiveForResponse =
                tdsAllowed
                        && unbilled.isTdsActive();

        dto.setTdsActiveFlag(tdsActiveForResponse);

        dto.setStatus(unbilled.getStatus());
        dto.setCreatedAt(unbilled.getCreatedAt());
        dto.setApprovedAt(unbilled.getApprovedAt());

        User createdBy = unbilled.getCreatedBy();
        dto.setCreatedByName(getUserDisplayName(createdBy));

        User approvedBy = unbilled.getApprovedBy();
        dto.setApprovedByName(getUserDisplayName(approvedBy));

        dto.setName(
                estimate != null && estimate.getSolutionName() != null
                        ? estimate.getSolutionName()
                        : (company != null ? company.getName() + " - Project" : "Unnamed Project")
        );
// ==================== TDS RESPONSE DTO ====================
        if (Boolean.TRUE.equals(unbilled.isTdsActive())) {
            List<TdsRegistration> tdsList =
                    tdsRegistrationRepository.findAllByUnbilledInvoiceAndIsDeletedFalse(unbilled);

            tdsList.stream()
                    .filter(tds -> tds.getStatus() == TdsStatus.PENDING || tds.getStatus() == TdsStatus.APPROVED)
                    .max(Comparator.comparing(
                            TdsRegistration::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ))
                    .ifPresentOrElse(
                            tds -> dto.setTdsResponseDto(mapToTdsResponseDtoForSummary(tds)),
                            () -> dto.setTdsResponseDto(null)
                    );
        } else {
            dto.setTdsResponseDto(null);
        }
// =========================================================

        return dto;
    }

    private TdsResponseDto mapToTdsResponseDtoForSummary(TdsRegistration tds) {
        if (tds == null) return null;

        return TdsResponseDto.builder()
                .id(tds.getId())
                .publicUuid(tds.getPublicUuid())
                .estimateId(tds.getEstimate() != null ? tds.getEstimate().getId() : null)
                .estimateNumber(tds.getEstimate() != null ? tds.getEstimate().getEstimateNumber() : null)
                .unbilledInvoiceId(tds.getUnbilledInvoice() != null ? tds.getUnbilledInvoice().getId() : null)
                .unbilledNumber(tds.getUnbilledInvoice() != null ? tds.getUnbilledInvoice().getUnbilledNumber() : null)
                .tdsPercentage(tds.getTdsPercentage())
                .taxableAmount(tds.getTaxableAmount())
                .tdsAmount(tds.getTdsAmount())
                .tdsDate(tds.getTdsDate())
                .status(tds.getStatus())
                .createdById(tds.getCreatedBy() != null ? tds.getCreatedBy().getId() : null)
                .createdByName(getUserDisplayName(tds.getCreatedBy()))
                .createdAt(tds.getCreatedAt())
                .updatedAt(tds.getUpdatedAt())
                .build();
    }



    private UnbilledInvoiceDetailDto mapToDetailDto(UnbilledInvoice unbilled) {
        Estimate estimate = unbilled.getEstimate();
        Company company = unbilled.getCompany();
        CompanyUnit unit = unbilled.getUnit();
        Contact contact = unbilled.getContact();

        String placeOfSupply = estimate != null ? estimate.getPlaceOfSupplyStateCode() : null;
        boolean isIntraState = "09".equals(placeOfSupply);

        List<UnbilledInvoiceDetailDto.LineItemDto> lineItemDtos = estimate != null && estimate.getLineItems() != null
                ? estimate.getLineItems().stream()
                .map(item -> {
                    BigDecimal gstAmount = item.getGstAmount() != null ? item.getGstAmount() : BigDecimal.ZERO;
                    BigDecimal halfGst = gstAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

                    BigDecimal cgstAmount = isIntraState ? halfGst : BigDecimal.ZERO;
                    BigDecimal sgstAmount = isIntraState ? halfGst : BigDecimal.ZERO;
                    BigDecimal igstAmount = isIntraState ? BigDecimal.ZERO : gstAmount;

                    UnbilledInvoiceDetailDto.LineItemDto lineDto = new UnbilledInvoiceDetailDto.LineItemDto();
                    lineDto.setId(item.getId());
                    lineDto.setSourceEstimateLineItemId(item.getId());
                    lineDto.setItemName(item.getItemName());
                    lineDto.setDescription(item.getDescription());
                    lineDto.setHsnSacCode(item.getHsnSacCode());
                    lineDto.setQuantity(item.getQuantity());
                    lineDto.setUnit(item.getUnit());
                    lineDto.setUnitPriceExGst(item.getUnitPriceExGst());
                    lineDto.setLineTotalExGst(item.getLineTotalExGst());
                    lineDto.setGstRate(item.getGstRate());
                    lineDto.setGstAmount(gstAmount);
                    lineDto.setLineTotalWithGst(
                            item.getLineTotalExGst() != null
                                    ? item.getLineTotalExGst().add(gstAmount)
                                    : gstAmount
                    );
                    lineDto.setCgstAmount(cgstAmount);
                    lineDto.setSgstAmount(sgstAmount);
                    lineDto.setIgstAmount(igstAmount);
                    lineDto.setDisplayOrder(item.getDisplayOrder());
                    lineDto.setCategoryCode(item.getCategoryCode());
                    lineDto.setFeeType(item.getFeeType());

                    return lineDto;
                })
                .collect(Collectors.toList())
                : new ArrayList<>();

        UnbilledInvoiceDetailDto dto = new UnbilledInvoiceDetailDto();

        dto.setId(unbilled.getId());
        dto.setPublicUuid(unbilled.getPublicUuid());
        dto.setUnbilledNumber(unbilled.getUnbilledNumber());
        dto.setAdvanceInvoiceNumber(unbilled.getAdvanceInvoiceNumber());
        dto.setAdvanceInvoiceFlag(unbilled.isAdvanceInvoiceFlag());
        dto.setEstimateNumber(estimate != null ? estimate.getEstimateNumber() : null);
        dto.setSolutionName(estimate != null ? estimate.getSolutionName() : null);
        dto.setSolutionType(estimate != null ? estimate.getSolutionType() : null);

        dto.setCompanyName(company != null ? company.getName() : null);
        dto.setContactName(contact != null ? contact.getName() : null);

        // NEW DETAILS
        dto.setEmail(contact != null ? contact.getEmails() : null);
        dto.setEmail(firstEmail(contact));

        dto.setGstNo(unit != null ? unit.getGstNo() : null);
        dto.setStateName(unit != null ? unit.getState() : null);
        dto.setStateCode(resolveStateCode(estimate, unit));
        dto.setAddress(buildUnitAddress(unit));

        dto.setInvoiceDate(unbilled.getCreatedAt() != null ? unbilled.getCreatedAt().toLocalDate() : null);
        dto.setCurrency(estimate != null ? estimate.getCurrency() : null);
        dto.setStatus(unbilled.getStatus());
        dto.setSubTotalExGst(estimate != null ? estimate.getSubTotalExGst() : null);
        dto.setTotalGstAmount(estimate != null ? estimate.getTotalGstAmount() : null);
        dto.setCgstAmount(estimate != null ? estimate.getCgstAmount() : null);
        dto.setSgstAmount(estimate != null ? estimate.getSgstAmount() : null);
        dto.setIgstAmount(estimate != null ? estimate.getIgstAmount() : null);
        dto.setGrandTotal(unbilled.getTotalAmount());
        dto.setReceivedAmount(unbilled.getReceivedAmount());
        dto.setCurrentReceivedAmount(unbilled.getCurrentReceivedAmount());
        dto.setOutstandingAmount(unbilled.getOutstandingAmount());
        dto.setTdsActiveFlag(
                !isInternationalTransaction(estimate, unbilled)
                        && unbilled.isTdsActive()
        );
        dto.setCreatedByName(getUserDisplayName(unbilled.getCreatedBy()));
        dto.setCreatedAt(unbilled.getCreatedAt());
        dto.setUpdatedAt(unbilled.getUpdatedAt());
        dto.setApprovedByName(getUserDisplayName(unbilled.getApprovedBy()));
        dto.setApprovedAt(unbilled.getApprovedAt());
        dto.setApprovalRemarks(unbilled.getApprovalRemarks());
        dto.setLineItems(lineItemDtos);

        return dto;
    }


    private String firstEmail(Contact contact) {
        if (contact == null || contact.getEmails() == null || contact.getEmails().trim().isEmpty()) {
            return null;
        }

        String emails = contact.getEmails().trim();

        if (emails.contains(",")) {
            return emails.split(",")[0].trim();
        }

        return emails;
    }


    private String resolveStateCode(Estimate estimate, CompanyUnit unit) {
        if (estimate != null
                && estimate.getPlaceOfSupplyStateCode() != null
                && !estimate.getPlaceOfSupplyStateCode().trim().isEmpty()) {
            return estimate.getPlaceOfSupplyStateCode().trim();
        }

        if (unit != null
                && unit.getGstNo() != null
                && unit.getGstNo().trim().length() >= 2) {
            return unit.getGstNo().trim().substring(0, 2);
        }

        return null;
    }

    private String buildUnitAddress(CompanyUnit unit) {
        if (unit == null) {
            return null;
        }

        List<String> parts = new ArrayList<>();

        addAddressPart(parts, unit.getAddressLine1());
        addAddressPart(parts, unit.getAddressLine2());
        addAddressPart(parts, unit.getCity());
        addAddressPart(parts, unit.getState());
        addAddressPart(parts, unit.getCountry());
        addAddressPart(parts, unit.getPinCode());

        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private void addAddressPart(List<String> parts, String value) {
        if (value != null && !value.trim().isEmpty()) {
            parts.add(value.trim());
        }
    }


    private void pushPaymentRegisteredNotificationToAccountUsers(
            UnbilledInvoice unbilled,
            PaymentReceipt receipt,
            Estimate estimate,
            User salesperson
    ) {
        if (unbilled == null || unbilled.getId() == null || receipt == null || receipt.getId() == null) {
            return;
        }

        List<User> accountUsers = findAccountDepartmentApprovers();

        if (accountUsers == null || accountUsers.isEmpty()) {
            log.warn(
                    "Payment registration notification skipped because no active account department users found | unbilledId={}",
                    unbilled.getId()
            );
            return;
        }

        String salespersonName = getUserDisplayName2(salesperson);
        String unbilledNumber = unbilled.getUnbilledNumber() != null
                ? unbilled.getUnbilledNumber()
                : "UNBILLED-" + unbilled.getId();

        String estimateNumber = estimate != null && estimate.getEstimateNumber() != null
                ? estimate.getEstimateNumber()
                : "";

        String companyName = unbilled.getCompany() != null && unbilled.getCompany().getName() != null
                ? unbilled.getCompany().getName()
                : "company";

        String amount = receipt.getAmount() != null
                ? receipt.getAmount().toPlainString()
                : "0.00";

        for (User accountUser : accountUsers) {
            if (accountUser == null || accountUser.getId() == null) {
                continue;
            }

            notificationPublisherService.sendNotification(
                    NotificationCreateRequestDto.builder()
                            .receiverId(accountUser.getId())
                            .actorId(salesperson != null ? salesperson.getId() : null)
                            .actorName(salespersonName)
                            .module(NotificationCreateRequestDto.NotificationModule.PAYMENT)
                            .eventType(NotificationCreateRequestDto.NotificationEventType.PAYMENT_REGISTERED)
                            .referenceId(unbilled.getId())
                            .referenceNumber(unbilledNumber)
                            .title("Payment Approval Required")
                            .message(salespersonName + " registered payment of ₹" + amount + " for " + companyName + ".")
                            .redirectUrl("/account/unbilled-invoices/" + unbilled.getId())
                            .priority(NotificationPriority.HIGH)
                            .displayType(NotificationCreateRequestDto.NotificationDisplayType.WARNING)
                            .metadataJson(
                                    "{"
                                            + "\"unbilledId\":" + unbilled.getId() + ","
                                            + "\"paymentReceiptId\":" + receipt.getId() + ","
                                            + "\"unbilledNumber\":\"" + escapeJson(unbilledNumber) + "\","
                                            + "\"estimateNumber\":\"" + escapeJson(estimateNumber) + "\","
                                            + "\"companyName\":\"" + escapeJson(companyName) + "\","
                                            + "\"amount\":\"" + escapeJson(amount) + "\","
                                            + "\"registeredBy\":\"" + escapeJson(salespersonName) + "\""
                                            + "}"
                            )
                            .build()
            );
        }
    }

    private void pushPaymentApprovalDecisionNotificationToSalesperson(
            UnbilledInvoice unbilled,
            Estimate estimate,
            User approver,
            boolean approved,
            String remarks
    ) {
        if (unbilled == null || unbilled.getId() == null) {
            return;
        }

        User salesperson = unbilled.getCreatedBy();

        if (salesperson == null || salesperson.getId() == null) {
            log.warn(
                    "Payment approval notification skipped because salesperson/createdBy not found | unbilledId={}",
                    unbilled.getId()
            );
            return;
        }

        String approverName = getUserDisplayName(approver);

        String unbilledNumber = unbilled.getUnbilledNumber() != null
                ? unbilled.getUnbilledNumber()
                : "UNBILLED-" + unbilled.getId();

        String estimateNumber = estimate != null && estimate.getEstimateNumber() != null
                ? estimate.getEstimateNumber()
                : "";

        String companyName = unbilled.getCompany() != null && unbilled.getCompany().getName() != null
                ? unbilled.getCompany().getName()
                : "company";

        String totalAmount = unbilled.getTotalAmount() != null
                ? unbilled.getTotalAmount().toPlainString()
                : "0.00";

        String receivedAmount = unbilled.getReceivedAmount() != null
                ? unbilled.getReceivedAmount().toPlainString()
                : "0.00";

        NotificationCreateRequestDto.NotificationEventType eventType = approved
                ? NotificationCreateRequestDto.NotificationEventType.PAYMENT_APPROVED
                : NotificationCreateRequestDto.NotificationEventType.PAYMENT_REJECTED;

        NotificationCreateRequestDto.NotificationDisplayType displayType = approved
                ? NotificationCreateRequestDto.NotificationDisplayType.SUCCESS
                : NotificationCreateRequestDto.NotificationDisplayType.DANGER;

        String title = approved
                ? "Payment Approved"
                : "Payment Rejected";

        String message = approved
                ? approverName + " approved payment for " + companyName + "."
                : approverName + " rejected payment for " + companyName + ".";

        notificationPublisherService.sendNotification(
                NotificationCreateRequestDto.builder()
                        .receiverId(salesperson.getId())
                        .actorId(approver != null ? approver.getId() : null)
                        .actorName(approverName)
                        .module(NotificationCreateRequestDto.NotificationModule.PAYMENT)
                        .eventType(eventType)
                        .referenceId(unbilled.getId())
                        .referenceNumber(unbilledNumber)
                        .title(title)
                        .message(message)
                        .redirectUrl("/account/unbilled-invoices/" + unbilled.getId())
                        .priority(NotificationPriority.HIGH)
                        .displayType(displayType)
                        .metadataJson(
                                "{"
                                        + "\"unbilledId\":" + unbilled.getId() + ","
                                        + "\"unbilledNumber\":\"" + escapeJson(unbilledNumber) + "\","
                                        + "\"estimateNumber\":\"" + escapeJson(estimateNumber) + "\","
                                        + "\"companyName\":\"" + escapeJson(companyName) + "\","
                                        + "\"totalAmount\":\"" + escapeJson(totalAmount) + "\","
                                        + "\"receivedAmount\":\"" + escapeJson(receivedAmount) + "\","
                                        + "\"approved\":" + approved + ","
                                        + "\"remarks\":\"" + escapeJson(remarks) + "\","
                                        + "\"actionBy\":\"" + escapeJson(approverName) + "\""
                                        + "}"
                        )
                        .build()
        );
    }

    private List<User> findAccountDepartmentApprovers() {
        List<User> users = new ArrayList<>();

        users.addAll(userRepository.findByDepartmentIgnoreCaseAndIsDeletedFalseAndIsActiveTrue("ACCOUNT"));

        if (users.isEmpty()) {
            users.addAll(userRepository.findByDepartmentIgnoreCaseAndIsDeletedFalseAndIsActiveTrue("ACCOUNTS"));
        }

        return users.stream()
                .filter(Objects::nonNull)
                .filter(user -> !user.isDeleted())
                .filter(User::isActive)
                .filter(user -> user.getId() != null)
                .distinct()
                .collect(Collectors.toList());
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    @Override
    public UnbilledInvoiceDetailDto getUnbilledInvoice(Long unBilledId, Long requestingUserId) {
        log.info("Fetching unbilled invoice by ID | unbilledId={} | requestingUserId={}", unBilledId, requestingUserId);

        if (unBilledId == null || requestingUserId == null) {
            throw new IllegalArgumentException("Invoice ID and requesting user ID are required");
        }

        UnbilledInvoice unbilledInvoice = unbilledInvoiceRepository.findById(unBilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled Invoice not found with ID: " + unBilledId,
                        "INVOICE_NOT_FOUND",
                        "UnbilledInvoice",
                        unBilledId
                ));

        if (unbilledInvoice.getCreatedBy() == null ||
                !unbilledInvoice.getCreatedBy().getId().equals(requestingUserId)) {
            throw new AccessDeniedException(
                    "You are not authorized to view this invoice",
                    "ACCESS_DENIED_INVOICE"
            );
        }

        log.debug("Unbilled invoice fetched | unbilledId={} | unbilledNumber={}",
                unbilledInvoice.getId(), unbilledInvoice.getUnbilledNumber());
        return mapToDetailDto(unbilledInvoice);
    }



    private boolean hasUnrestrictedUnbilledInvoiceAccess(Long userId) {
        if (userId == null) {
            return false;
        }

        User user = userRepository.findByIdAndNotDeleted(userId)
                .orElse(null);

        if (user == null || !user.isActive()) {
            return false;
        }

        return belongsToAccountsDepartment(user) || hasAdminRole(user);
    }

    private boolean belongsToAccountsDepartment(User user) {
        return user.getDepartment() != null
                && (
                "ACCOUNT".equalsIgnoreCase(user.getDepartment().trim())
                        || "ACCOUNTS".equalsIgnoreCase(user.getDepartment().trim())
        );
    }

    private boolean hasAdminRole(User user) {
        return user.getUserRole() != null
                && user.getUserRole().stream()
                .anyMatch(role ->
                        role != null
                                && !role.isDeleted()
                                && role.getName() != null
                                && "ADMIN".equalsIgnoreCase(role.getName().trim())
                );
    }





    @Override
    public long getUnbilledInvoicesCount(Long userId, UnbilledStatus status) {
        log.info("Counting unbilled invoices | userId={}, status={}",
                userId != null ? userId : "all",
                status != null ? status : "all");

        if (userId != null && status != null) {
            return unbilledInvoiceRepository.countByCreatedByIdOrApprovedByIdAndStatusAndIsCancelledFalse(userId, userId, status);
        } else if (userId != null) {
            return unbilledInvoiceRepository.countByCreatedByIdOrApprovedByIdAndIsCancelledFalse(userId, userId);
        } else if (status != null) {
            return unbilledInvoiceRepository.countByStatusAndIsCancelledFalse(status);
        } else {
            return unbilledInvoiceRepository.count();
        }
    }

    @Override
    public List<UnbilledInvoiceSummaryDto> searchUnbilledInvoices(
            String unbilledNumber,
            String companyName,
            String estimateNumber,
            int page,
            int size
    ) {
        log.info("Searching unbilled invoices | unbilledNumber={}, companyName={}, page={}, size={}",
                unbilledNumber, companyName, page, size);

//        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Pageable pageable = PageRequest.of(page, size);

        Page<UnbilledInvoice> pageResult = unbilledInvoiceRepository.searchUnbilledInvoicesAndIsCancelledFalse(
                unbilledNumber != null && !unbilledNumber.trim().isEmpty() ? unbilledNumber.trim() : null,
                companyName != null && !companyName.trim().isEmpty() ? companyName.trim() : null,
                estimateNumber != null && !estimateNumber.trim().isEmpty() ? estimateNumber.trim() : null,
                pageable
        );

        List<UnbilledInvoiceSummaryDto> dtos = pageResult.getContent().stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());

        long totalCount = unbilledInvoiceRepository.countSearchUnbilledInvoicesAndIsCancelledFalse(
                unbilledNumber != null && !unbilledNumber.trim().isEmpty() ? unbilledNumber.trim() : null,
                companyName != null && !companyName.trim().isEmpty() ? companyName.trim() : null,
                estimateNumber != null && !estimateNumber.trim().isEmpty() ? estimateNumber.trim() : null
        );

        for (UnbilledInvoiceSummaryDto dto : dtos) {
            dto.setSearchCount(totalCount);
        }

        log.info("Unbilled invoice search completed | resultCount={} | totalCount={}", dtos.size(), totalCount);

        return dtos;
    }
    @Override
    public long countSearchUnbilledInvoices(String unbilledNumber, String companyName) {
        log.info("Counting search unbilled invoices | unbilledNumber={}, companyName={}",
                unbilledNumber, companyName);

        return unbilledInvoiceRepository.countSearchUnbilledInvoicesAndIsCancelledFalse(
                unbilledNumber != null && !unbilledNumber.trim().isEmpty() ? unbilledNumber.trim() : null,
                companyName != null && !companyName.trim().isEmpty() ? companyName.trim() : null,
                null
        );
    }

    private boolean isProductRelatedEstimate(Estimate estimate) {
        if (estimate == null || estimate.getSolutionType() == null) {
            return false;
        }

        return estimate.getSolutionType().trim().equalsIgnoreCase("PRODUCT");
    }

    private void validateEprFields(PaymentRegistrationRequestDto request) {
        if (request.getEprFinancialYear() == null || request.getEprFinancialYear().trim().isEmpty()) {
            throw new ValidationException("EPR Financial Year is required (YYYY-YYYY)", "VALIDATION_FAILED", "eprFinancialYear");
        }
        if (request.getEprPortalRegistrationNumber() == null || request.getEprPortalRegistrationNumber().trim().isEmpty()) {
            throw new ValidationException("EPR Portal Registration Number is required", "VALIDATION_FAILED", "eprPortalRegistrationNumber");
        }
        if (!request.getEprFinancialYear().matches("\\d{4}-\\d{4}")) {
            throw new ValidationException("Invalid EPR Financial Year format. Use YYYY-YYYY", "VALIDATION_FAILED", "eprFinancialYear");
        }
    }

    private String generateUnbilledNumber() {
        long count = unbilledInvoiceRepository.count() + 1;
        int year = dateTimeUtil.nowLocalDateTime().getYear();
        return String.format("UNB-%d-%08d", year, count);
    }

    private String generateAdvanceInvoiceNumber() {
        long count = unbilledInvoiceRepository.count() + 1;
        int year = dateTimeUtil.nowLocalDateTime().getYear();
        return String.format("ADI-%d-%08d", year, count);
    }


    private String generateProjectNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        long count = unbilledInvoiceRepository.count() + 1;
        String sequence = String.format("%04d", count);

        return "PRJ-" + datePart + "-" + sequence;
    }




    @Override
    @Transactional
    public Page<OperationProjectActivityResponseDto> getExpences(Long userId, Long unbilledId, Pageable pageable) {

        log.info("Fetching expense activities | userId={} | unbilledId={}", userId, unbilledId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId,
                        "USER_NOT_FOUND",
                        "User",
                        userId
                ));

        // ===============================
        // FETCH UNBILLED
        // ===============================
        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled not found with ID: " + unbilledId,
                        "UNBILLED_NOT_FOUND",
                        "UnbilledInvoice",
                        unbilledId
                ));

        // ===============================
        // VALIDATION
        // ===============================
        if (unbilled.isCancelled()) {
            throw new IllegalStateException("Unbilled already cancelled");
        }

        // ===============================
        // FETCH PROJECT FROM OPERATION
        // ===============================
        OperationProjectResponseDto project;

        try {
            ResponseEntity<OperationProjectResponseDto> res =
                    operationFeignClient.getProjectByUnbilledNumber(unbilled.getUnbilledNumber());

            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                throw new RuntimeException("Failed to fetch project from operation service");
            }

            project = res.getBody();

        } catch (FeignException ex) {


            log.error("Error fetching project | unbilled={}",
                    unbilled.getUnbilledNumber()
            );

            throw new RuntimeException(ex);
        }

        // ===============================
        // FETCH EXPENSE ACTIVITIES
        // ===============================
        try {
            ResponseEntity<Page<OperationProjectActivityResponseDto>> activityRes =
                    operationFeignClient.getActivitiesByType(
                            project.getId(),
                            ActivityType.EXPENSE,
                            pageable
                    );

            if (!activityRes.getStatusCode().is2xxSuccessful() || activityRes.getBody() == null) {
                throw new RuntimeException("Failed to fetch expenses from operation service");
            }

            log.info("Expense activities fetched | projectId={} | unbilledId={}", project.getId(), unbilled.getId());
            return activityRes.getBody();

        } catch (FeignException ex) {


            log.error("Error fetching expenses..... ");

            throw new RuntimeException(ex);
        }
    }


    @Override
    @Transactional(readOnly = true)
    public UnbilledInvoiceDetailDto getUnbilledInvoiceByNumber(String unbilledNumber, Long requestingUserId) {
        log.info("Fetching unbilled invoice by number: {} | requestedByUser={}", unbilledNumber, requestingUserId);

        if (requestingUserId == null || requestingUserId <= 0) {
            throw new ValidationException("Invalid requestingUserId", "ERR_INVALID_REQUESTING_USER", "requestingUserId");
        }

        if (unbilledNumber == null || unbilledNumber.trim().isEmpty()) {
            throw new ValidationException("Unbilled number is required", "ERR_INVALID_UNBILLED_NUMBER", "unbilledNumber");
        }

        // Validate user exists
        if (!userRepository.existsById(requestingUserId)) {
            throw new ResourceNotFoundException("User not found", "USER_NOT_FOUND");
        }

        UnbilledInvoice unbilled = unbilledInvoiceRepository
                .findByUnbilledNumberAndIsCancelledFalse(unbilledNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled invoice not found with number: " + unbilledNumber,
                        "UNBILLED_NOT_FOUND"
                ));

        // Security: Only creator or approver can view
        if (!unbilled.getCreatedBy().getId().equals(requestingUserId) &&
                (unbilled.getApprovedBy() == null || !unbilled.getApprovedBy().getId().equals(requestingUserId))) {
            throw new AccessDeniedException("You are not authorized to view this unbilled invoice", "ACCESS_DENIED_UNBILLED");
        }

        log.info("Unbilled invoice found | number={} | id={}", unbilled.getUnbilledNumber(), unbilled.getId());

        return mapToDetailDto(unbilled);
    }

    @Override
    @Transactional
    public void approveExpense(Long userId, Long unbilledId, Long expenseId, String status) {

        log.info("Approving expense | userId={} | unbilledId={} | expenseId={} | status={}",
                userId, unbilledId, expenseId, status);

        // ===============================
        // VALIDATE USER
        // ===============================
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId,
                        "USER_NOT_FOUND",
                        "User",
                        userId
                ));

        // ===============================
        // FETCH UNBILLED
        // ===============================
        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled not found with ID: " + unbilledId,
                        "UNBILLED_NOT_FOUND",
                        "UnbilledInvoice",
                        unbilledId
                ));

        if (unbilled.isCancelled()) {
            throw new IllegalStateException("Unbilled already cancelled");
        }

        // ===============================
        // FETCH PROJECT FROM OPERATION
        // ===============================
        OperationProjectResponseDto project;

        try {
            ResponseEntity<OperationProjectResponseDto> res =
                    operationFeignClient.getProjectByUnbilledNumber(unbilled.getUnbilledNumber());

            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                throw new RuntimeException("Failed to fetch project from operation service");
            }

            project = res.getBody();

        } catch (FeignException ex) {


            log.error("Error fetching project, while approve expenses | unbilled={} ",unbilled.getUnbilledNumber());

            throw new RuntimeException(ex);
        }

        // ===============================
        // CALL APPROVE EXPENSE API
        // ===============================
        try {

            operationFeignClient.approveExpense(
                    project.getId(),
                    user.getId(),
                    expenseId,
                    status
            );

            log.info("Expense approved successfully | projectId={} expenseId={}",
                    project.getId(),
                    expenseId
            );

        } catch (FeignException ex) {


            log.error("Error approving expense | projectId={} expenseId={}",
                    project.getId(),
                    expenseId
            );

            throw new RuntimeException(ex);
        }
    }


    @Override
    @Transactional
    public UnbilledInvoiceDetailDto convertIntoADI(Long unbilledId,Long requestingUserId){


        log.info("Converting unbilled into advance invoice | unbilledId={} | requestingUserId={}", unbilledId, requestingUserId);

        if (requestingUserId == null || requestingUserId <= 0) {
            throw new ValidationException("Invalid requestingUserId", "ERR_INVALID_REQUESTING_USER", "requestingUserId");
        }

        // Basic security check
        if (!userRepository.existsById(requestingUserId)) {
            log.warn("User not found: userId={}", requestingUserId);
            throw new ResourceNotFoundException("User not found", "USER_NOT_FOUND");
        }

        if (unbilledId == null || unbilledId <= 0) {
            throw new ValidationException("Invalid unbilledId", "ERR_INVALID_UNBILLED_ID", "unbilledId");
        }

        // Fetch the estimate
        UnbilledInvoice unbilledInvoice = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> {
                    log.warn("Unbilled not found: id={}", unbilledId);
                    return new ResourceNotFoundException("Unbilled not found", "UNBILLED_NOT_FOUND");
                });


        unbilledInvoice.setAdvanceInvoiceFlag(true);
        unbilledInvoiceRepository.save(unbilledInvoice);

        log.info("Unbilled converted into advance invoice | unbilledId={} | advanceInvoiceNumber={}",
                unbilledInvoice.getId(), unbilledInvoice.getAdvanceInvoiceNumber());
        return mapToDetailDto(unbilledInvoice);
    }

    @Override
    @Transactional(readOnly = true)
    public GovernmentFeeResponseDto getGovernmentFee(Long unbilledId, Long estimateId) {

        log.info("Fetching government fee | unbilledId={} | estimateId={}", unbilledId, estimateId);

        if (unbilledId == null && estimateId == null) {
            throw new ValidationException(
                    "Either unbilledId or estimateId is required",
                    "ERR_GOV_FEE_FILTER_REQUIRED",
                    "unbilledId/estimateId"
            );
        }

        GovernmentFee governmentFee;

        if (unbilledId != null) {
            governmentFee = governmentFeeRepository.findByUnbilledInvoiceId(unbilledId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Government fee not found for unbilled invoice ID: " + unbilledId,
                            "GOVERNMENT_FEE_NOT_FOUND",
                            "GovernmentFee",
                            unbilledId
                    ));
        } else {
            governmentFee = governmentFeeRepository.findByEstimateId(estimateId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Government fee not found for estimate ID: " + estimateId,
                            "GOVERNMENT_FEE_NOT_FOUND",
                            "GovernmentFee",
                            estimateId
                    ));
        }

        log.debug("Government fee fetched | governmentFeeId={} | status={}", governmentFee.getId(), governmentFee.getStatus());
        return mapToGovernmentFeeResponseDto(governmentFee);
    }
    private GovernmentFeeResponseDto mapToGovernmentFeeResponseDto(GovernmentFee governmentFee) {
        return GovernmentFeeResponseDto.builder()
                .id(governmentFee.getId())
                .publicUuid(governmentFee.getPublicUuid())

                .estimateId(governmentFee.getEstimate() != null ? governmentFee.getEstimate().getId() : null)
                .estimateNumber(governmentFee.getEstimate() != null ? governmentFee.getEstimate().getEstimateNumber() : null)

                .unbilledInvoiceId(governmentFee.getUnbilledInvoice() != null ? governmentFee.getUnbilledInvoice().getId() : null)
                .unbilledNumber(governmentFee.getUnbilledInvoice() != null ? governmentFee.getUnbilledInvoice().getUnbilledNumber() : null)

                .companyId(governmentFee.getCompany() != null ? governmentFee.getCompany().getId() : null)
                .companyName(governmentFee.getCompany() != null ? governmentFee.getCompany().getName() : null)

                .unitId(governmentFee.getUnit() != null ? governmentFee.getUnit().getId() : null)
                .unitName(governmentFee.getUnit() != null ? governmentFee.getUnit().getUnitName() : null)

                .contactId(governmentFee.getContact() != null ? governmentFee.getContact().getId() : null)
                .contactName(governmentFee.getContact() != null ? governmentFee.getContact().getName() : null)

                .feeReferenceNumber(governmentFee.getFeeReferenceNumber())
                .departmentName(governmentFee.getDepartmentName())
                .feeType(governmentFee.getFeeType())

                .totalAmount(governmentFee.getTotalAmount())
                .receivedAmount(governmentFee.getReceivedAmount())
                .outstandingAmount(governmentFee.getOutstandingAmount())

                .paymentDate(governmentFee.getPaymentDate())
                .dueDate(governmentFee.getDueDate())

                .status(governmentFee.getStatus())
                .remarks(governmentFee.getRemarks())

                .createdById(governmentFee.getCreatedBy() != null ? governmentFee.getCreatedBy().getId() : null)
                .createdByName(governmentFee.getCreatedBy() != null
                        ? (governmentFee.getCreatedBy().getFullName() != null
                        ? governmentFee.getCreatedBy().getFullName()
                        : governmentFee.getCreatedBy().getEmail())
                        : null)

                .createdAt(governmentFee.getCreatedAt())
                .updatedAt(governmentFee.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TdsResponseDto getTds(
            Long unbilledId,
            Long estimateId
    ) {
        log.info(
                "Fetching TDS | unbilledId={} | estimateId={}",
                unbilledId,
                estimateId
        );

        if (unbilledId == null && estimateId == null) {
            throw new ValidationException(
                    "Either unbilledId or estimateId is required",
                    "ERR_TDS_FILTER_REQUIRED",
                    "unbilledId/estimateId"
            );
        }

        List<TdsRegistration> tdsList;

        if (unbilledId != null) {

            UnbilledInvoice unbilled =
                    unbilledInvoiceRepository
                            .findById(unbilledId)
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Unbilled invoice not found with ID: "
                                                    + unbilledId,
                                            "UNBILLED_NOT_FOUND",
                                            "UnbilledInvoice",
                                            unbilledId
                                    )
                            );

            if (isInternationalTransaction(
                    unbilled.getEstimate(),
                    unbilled
            )) {
                throw new ValidationException(
                        "TDS is not applicable for INTERNATIONAL transactions",
                        "ERR_TDS_NOT_ALLOWED_FOR_INTERNATIONAL",
                        "unbilledId"
                );
            }

            tdsList =
                    tdsRegistrationRepository
                            .findAllByUnbilledInvoiceAndIsDeletedFalse(
                                    unbilled
                            );

        } else {

            Estimate estimate =
                    estimateRepository
                            .findById(estimateId)
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Estimate not found with ID: "
                                                    + estimateId,
                                            "ESTIMATE_NOT_FOUND",
                                            "Estimate",
                                            estimateId
                                    )
                            );

            if (isInternationalTransaction(estimate, null)) {
                throw new ValidationException(
                        "TDS is not applicable for INTERNATIONAL transactions",
                        "ERR_TDS_NOT_ALLOWED_FOR_INTERNATIONAL",
                        "estimateId"
                );
            }

            tdsList =
                    tdsRegistrationRepository
                            .findAllByEstimateAndIsDeletedFalse(
                                    estimate
                            );
        }

        TdsRegistration latestTds = tdsList.stream()
                .filter(tds ->
                        tds.getStatus() == TdsStatus.PENDING
                                || tds.getStatus() == TdsStatus.APPROVED
                )
                .max(
                        Comparator.comparing(
                                TdsRegistration::getCreatedAt,
                                Comparator.nullsLast(
                                        Comparator.naturalOrder()
                                )
                        )
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "TDS not found",
                                "TDS_NOT_FOUND",
                                "TdsRegistration",
                                unbilledId != null
                                        ? unbilledId
                                        : estimateId
                        )
                );

        return mapToTdsResponseDto(latestTds);
    }



    private TdsResponseDto mapToTdsResponseDto(TdsRegistration tds) {
        return TdsResponseDto.builder()
                .id(tds.getId())
                .publicUuid(tds.getPublicUuid())

                .estimateId(tds.getEstimate() != null ? tds.getEstimate().getId() : null)
                .estimateNumber(tds.getEstimate() != null ? tds.getEstimate().getEstimateNumber() : null)

                .unbilledInvoiceId(tds.getUnbilledInvoice() != null ? tds.getUnbilledInvoice().getId() : null)
                .unbilledNumber(tds.getUnbilledInvoice() != null ? tds.getUnbilledInvoice().getUnbilledNumber() : null)

                .tdsPercentage(tds.getTdsPercentage())
                .taxableAmount(tds.getTaxableAmount())
                .tdsAmount(tds.getTdsAmount())

                .status(tds.getStatus())

                .createdById(tds.getCreatedBy() != null ? tds.getCreatedBy().getId() : null)
                .createdByName(tds.getCreatedBy() != null
                        ? (tds.getCreatedBy().getFullName() != null
                        ? tds.getCreatedBy().getFullName()
                        : tds.getCreatedBy().getEmail())
                        : null)

                .createdAt(tds.getCreatedAt())
                .updatedAt(tds.getUpdatedAt())
                .build();
    }


    private void postReceiptVoucherForApprovedPayment(
            UnbilledInvoice unbilled,
            PaymentReceipt paymentReceipt,
            User approver,
            BigDecimal tdsAmount
    ) {
        if (unbilled == null) {
            throw new ValidationException(
                    "Unbilled invoice is required for receipt voucher",
                    "ERR_UNBILLED_REQUIRED_FOR_RECEIPT_VOUCHER",
                    "unbilled"
            );
        }

        if (paymentReceipt == null || paymentReceipt.getId() == null) {
            throw new ValidationException(
                    "Payment receipt is required for receipt voucher",
                    "ERR_PAYMENT_RECEIPT_REQUIRED_FOR_VOUCHER",
                    "paymentReceipt"
            );
        }

        if (paymentReceipt.getBankLedger() == null
                || paymentReceipt.getBankLedger().getId() == null) {

            throw new ValidationException(
                    "Bank ledger is missing in payment receipt",
                    "ERR_PAYMENT_BANK_LEDGER_MISSING",
                    "bankLedgerId"
            );
        }

        /*
         * Prevent duplicate receipt voucher posting.
         */
        if (accountingVoucherService.existsPostedVoucher(
                VoucherType.RECEIPT,
                VoucherSourceType.PAYMENT_RECEIPT,
                paymentReceipt.getId()
        )) {
            log.info(
                    "Receipt voucher already posted. Skipping duplicate posting | paymentReceiptId={}",
                    paymentReceipt.getId()
            );
            return;
        }

        Estimate estimate = unbilled.getEstimate();

        boolean internationalTransaction =
                isInternationalTransaction(
                        estimate,
                        unbilled
                );

        LedgerMaster bankLedger =
                paymentReceipt.getBankLedger();

        LedgerMaster customerLedger =
                getOrCreateCustomerLedger(
                        unbilled,
                        approver
                );

        BigDecimal bankAmount =
                safe2(paymentReceipt.getAmount());

        /*
         * Critical rule:
         *
         * INTERNATIONAL:
         * - TDS amount must always be zero
         * - TDS_RECEIVABLE ledger must not be created
         * - Customer credit must equal only the bank amount
         */
        BigDecimal safeTdsAmount =
                internationalTransaction
                        ? BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP
                )
                        : safe2(tdsAmount);

        if (internationalTransaction) {
            unbilled.setTdsActive(false);

            log.info(
                    "TDS ledger posting disabled for INTERNATIONAL payment | "
                            + "unbilledId={} | paymentReceiptId={} | incomingTdsAmount={}",
                    unbilled.getId(),
                    paymentReceipt.getId(),
                    safe2(tdsAmount)
            );
        }

        BigDecimal customerCreditAmount = bankAmount
                .add(safeTdsAmount)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        List<AccountingVoucherEntryRequestDto> entries =
                new ArrayList<>();

        // =====================================================
        // DR BANK / CASH / PAYMENT GATEWAY
        // =====================================================

        entries.add(
                AccountingVoucherEntryRequestDto.builder()
                        .ledgerId(bankLedger.getId())
                        .debitAmount(bankAmount)
                        .creditAmount(
                                BigDecimal.ZERO.setScale(
                                        2,
                                        RoundingMode.HALF_UP
                                )
                        )
                        .narration(
                                "Payment received in "
                                        + bankLedger.getLedgerName()
                        )
                        .build()
        );

        // =====================================================
        // DR TDS RECEIVABLE — DOMESTIC ONLY
        // =====================================================

        if (!internationalTransaction
                && safeTdsAmount.compareTo(BigDecimal.ZERO) > 0) {

            LedgerMaster tdsReceivableLedger =
                    getOrCreateSystemLedger(
                            LedgerType.TDS_RECEIVABLE,
                            LedgerGroupType.DUTIES_AND_TAXES,
                            "TDS Receivable",
                            DebitCredit.DEBIT,
                            approver
                    );

            entries.add(
                    AccountingVoucherEntryRequestDto.builder()
                            .ledgerId(tdsReceivableLedger.getId())
                            .debitAmount(safeTdsAmount)
                            .creditAmount(
                                    BigDecimal.ZERO.setScale(
                                            2,
                                            RoundingMode.HALF_UP
                                    )
                            )
                            .narration(
                                    "TDS receivable booked for unbilled "
                                            + unbilled.getUnbilledNumber()
                            )
                            .build()
            );
        }

        // =====================================================
        // CR CUSTOMER LEDGER
        // =====================================================

        entries.add(
                AccountingVoucherEntryRequestDto.builder()
                        .ledgerId(customerLedger.getId())
                        .debitAmount(
                                BigDecimal.ZERO.setScale(
                                        2,
                                        RoundingMode.HALF_UP
                                )
                        )
                        .creditAmount(customerCreditAmount)
                        .narration(
                                internationalTransaction
                                        ? "International customer payment received"
                                        : "Payment received from customer"
                        )
                        .build()
        );

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto.builder()
                        .voucherType(VoucherType.RECEIPT)
                        .voucherDate(
                                paymentReceipt.getPaymentDate() != null
                                        ? paymentReceipt.getPaymentDate()
                                        : LocalDate.now()
                        )
                        .sourceType(
                                VoucherSourceType.PAYMENT_RECEIPT
                        )
                        .sourceId(paymentReceipt.getId())
                        .narration(
                                "Payment approved for unbilled: "
                                        + unbilled.getUnbilledNumber()
                                        + ", transaction ref: "
                                        + paymentReceipt.getTransactionReference()
                        )
                        .entries(entries)
                        .build();

        accountingVoucherService.createVoucher(voucherRequest);

        log.info(
                "Receipt voucher posted | paymentReceiptId={} | international={} "
                        + "| bankAmount={} | tdsAmount={} | customerCreditAmount={}",
                paymentReceipt.getId(),
                internationalTransaction,
                bankAmount,
                safeTdsAmount,
                customerCreditAmount
        );
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

        /*
         * Unit is mandatory because customer ledgers are maintained
         * separately for every company unit.
         */
        if (unit == null || unit.getId() == null) {
            throw new ValidationException(
                    "Company unit is required to create customer ledger",
                    "ERR_COMPANY_UNIT_REQUIRED_FOR_LEDGER",
                    "unitId"
            );
        }

        Long companyId = company.getId();
        Long unitId = unit.getId();

        /*
         * First search for the exact CUSTOMER ledger belonging to
         * this company and this unit.
         */
        Optional<LedgerMaster> existingCustomerLedger =
                ledgerMasterRepository
                        .findByCompanyIdAndUnitIdAndLedgerTypeAndDeletedFalse(
                                companyId,
                                unitId,
                                LedgerType.CUSTOMER
                        );

        if (existingCustomerLedger.isPresent()) {
            LedgerMaster ledger = existingCustomerLedger.get();

            refreshCustomerLedgerDetails(
                    ledger,
                    company,
                    unit,
                    contact,
                    createdBy
            );

            return ledgerMasterRepository.save(ledger);
        }

        /*
         * Backward compatibility:
         * Convert an old CUSTOMER_ADVANCE ledger only when it belongs
         * to the same company and the same unit.
         *
         * Never convert another unit's ledger.
         */
        Optional<LedgerMaster> existingAdvanceLedger =
                ledgerMasterRepository
                        .findByCompanyIdAndUnitIdAndLedgerTypeAndDeletedFalse(
                                companyId,
                                unitId,
                                LedgerType.CUSTOMER_ADVANCE
                        );

        LedgerMaster ledger =
                existingAdvanceLedger.orElseGet(LedgerMaster::new);

        LedgerGroup sundryDebtorsGroup =
                getOrCreateLedgerGroupByType(
                        LedgerGroupType.SUNDRY_DEBTORS
                );

        String companyName =
                company.getName() != null
                        && !company.getName().trim().isEmpty()
                        ? company.getName().trim()
                        : "Company-" + companyId;

        String unitName =
                unit.getUnitName() != null
                        && !unit.getUnitName().trim().isEmpty()
                        ? unit.getUnitName().trim()
                        : "Unit-" + unitId;

        String ledgerName =
                companyName + " - " + unitName;

        /*
         * Protect against another unrelated ledger having the same name.
         */
        boolean duplicateName;

        if (ledger.getId() == null) {
            duplicateName =
                    ledgerMasterRepository
                            .existsByLedgerNameIgnoreCase(ledgerName);
        } else {
            duplicateName =
                    ledgerMasterRepository
                            .existsByLedgerNameIgnoreCaseAndIdNot(
                                    ledgerName,
                                    ledger.getId()
                            );
        }

        if (duplicateName) {
            ledgerName =
                    companyName
                            + " - "
                            + unitName
                            + " ["
                            + unitId
                            + "]";
        }

        ledger.setLedgerName(ledgerName);

        if (ledger.getLedgerCode() == null
                || ledger.getLedgerCode().trim().isEmpty()) {
            ledger.setLedgerCode(
                    generateLedgerCode("CUST")
            );
        }

        ledger.setLedgerType(LedgerType.CUSTOMER);
        ledger.setLedgerGroup(sundryDebtorsGroup);

        ledger.setCompany(company);
        ledger.setUnit(unit);
        ledger.setContact(contact);

        ledger.setGstNo(unit.getGstNo());
        ledger.setPanNo(company.getPanNo());

        if (ledger.getOpeningBalance() == null) {
            ledger.setOpeningBalance(
                    BigDecimal.ZERO.setScale(
                            2,
                            RoundingMode.HALF_UP
                    )
            );
        }

        if (ledger.getOpeningBalanceType() == null) {
            ledger.setOpeningBalanceType(
                    DebitCredit.DEBIT
            );
        }

        if (ledger.getCurrentBalance() == null) {
            ledger.setCurrentBalance(
                    BigDecimal.ZERO.setScale(
                            2,
                            RoundingMode.HALF_UP
                    )
            );
        }

        if (ledger.getCurrentBalanceType() == null) {
            ledger.setCurrentBalanceType(
                    DebitCredit.DEBIT
            );
        }

        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);

        if (ledger.getId() == null && createdBy != null) {
            ledger.setCreatedBy(createdBy);
        }

        if (createdBy != null) {
            ledger.setUpdatedBy(createdBy);
        }

        LedgerMaster savedLedger =
                ledgerMasterRepository.save(ledger);

        log.info(
                "Unit-wise customer ledger resolved | companyId={} | unitId={} "
                        + "| ledgerId={} | ledgerName={}",
                companyId,
                unitId,
                savedLedger.getId(),
                savedLedger.getLedgerName()
        );

        return savedLedger;
    }

    private void refreshCustomerLedgerDetails(
            LedgerMaster ledger,
            Company company,
            CompanyUnit unit,
            Contact contact,
            User updatedBy
    ) {
        ledger.setCompany(company);
        ledger.setUnit(unit);
        ledger.setContact(contact);

        ledger.setGstNo(unit.getGstNo());
        ledger.setPanNo(company.getPanNo());

        ledger.setActive(true);
        ledger.setDeleted(false);
        ledger.setSystemCreated(true);

        if (updatedBy != null) {
            ledger.setUpdatedBy(updatedBy);
        }
    }

    private LedgerGroup getOrCreateLedgerGroupByType(LedgerGroupType groupType) {

        if (groupType == null) {
            throw new ValidationException(
                    "Ledger group type is required",
                    "ERR_LEDGER_GROUP_TYPE_REQUIRED",
                    "groupType"
            );
        }

        return ledgerGroupRepository.findByGroupTypeAndDeletedFalse(groupType)
                .map(existingGroup -> {
                    if (!existingGroup.isActive()) {
                        existingGroup.setActive(true);
                        LedgerGroup savedGroup = ledgerGroupRepository.save(existingGroup);
                        log.debug("Ledger group reactivated | groupType={} | groupId={}", groupType, savedGroup.getId());
                        return savedGroup;
                    }
                    return existingGroup;
                })
                .orElseGet(() -> {
                    LedgerGroup ledgerGroup = LedgerGroup.builder()
                            .name(formatGroupTypeLabel(groupType))
                            .groupType(groupType)
                            .description("System-created default ledger group")
                            .systemDefault(true)
                            .active(true)
                            .deleted(false)
                            .build();

                    LedgerGroup savedGroup = ledgerGroupRepository.save(ledgerGroup);
                    log.info("Ledger group created | groupType={} | groupId={}", groupType, savedGroup.getId());
                    return savedGroup;
                });
    }



    private String formatGroupTypeLabel(LedgerGroupType groupType) {

        if (groupType == null) {
            return null;
        }

        return Arrays.stream(groupType.name().toLowerCase().split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .reduce((first, second) -> first + " " + second)
                .orElse(groupType.name());
    }


    private String generateLedgerCode(String prefix) {
        long count = ledgerMasterRepository.count() + 1;
        return String.format("LED-%s-%06d", prefix, count);
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
            log.debug("System ledger reused | ledgerType={} | ledgerId={}",
                    ledgerType, existingLedger.get().getId());
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
        ledger.setLedgerCode(generateSystemLedgerCode(ledgerType));
        ledger.setLedgerType(ledgerType);
        ledger.setLedgerGroup(ledgerGroup);

        ledger.setOpeningBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        ledger.setOpeningBalanceType(balanceType);

        ledger.setCurrentBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        ledger.setCurrentBalanceType(balanceType);

        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);
        ledger.setCreatedBy(createdBy);
        ledger.setUpdatedBy(createdBy);

        LedgerMaster savedLedger = ledgerMasterRepository.save(ledger);
        log.info("System ledger created | ledgerType={} | ledgerId={} | ledgerName={}",
                ledgerType, savedLedger.getId(), savedLedger.getLedgerName());
        return savedLedger;
    }

    private String generateSystemLedgerCode(LedgerType ledgerType) {

        String prefix = switch (ledgerType) {
            case TDS_RECEIVABLE -> "LED-TDS-REC-";
            case OUTPUT_CGST -> "LED-OUT-CGST-";
            case OUTPUT_SGST -> "LED-OUT-SGST-";
            case OUTPUT_IGST -> "LED-OUT-IGST-";
            case INPUT_CGST -> "LED-IN-CGST-";
            case INPUT_SGST -> "LED-IN-SGST-";
            case INPUT_IGST -> "LED-IN-IGST-";
            default -> "LED-SYS-";
        };

        String code;

        do {
            code = prefix + System.currentTimeMillis();
        } while (ledgerMasterRepository.existsByLedgerCodeIgnoreCase(code));

        return code;
    }

    private BigDecimal calculateTdsTaxableAmount(Estimate estimate, UnbilledInvoice unbilled) {

        log.debug("Calculating TDS taxable amount | estimateId={} | unbilledId={}",
                estimate != null ? estimate.getId() : null,
                unbilled != null ? unbilled.getId() : null);

        /*
         * TDS must be calculated on taxable value excluding GST.
         *
         * Example:
         * Service value = 25,000
         * GST           = 4,500
         * Grand total   = 29,500
         *
         * TDS base      = 25,000
         */

        if (estimate != null
                && estimate.getSubTotalExGst() != null
                && safe2(estimate.getSubTotalExGst()).compareTo(BigDecimal.ZERO) > 0) {
            return safe2(estimate.getSubTotalExGst());
        }

        if (estimate != null
                && estimate.getLineItems() != null
                && !estimate.getLineItems().isEmpty()) {

            BigDecimal taxableFromLines = estimate.getLineItems()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(item -> item.getLineTotalExGst())
                    .filter(Objects::nonNull)
                    .map(this::safe2)
                    .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            if (taxableFromLines.compareTo(BigDecimal.ZERO) > 0) {
                return taxableFromLines;
            }
        }

        BigDecimal totalAmount = unbilled != null
                ? safe2(unbilled.getTotalAmount())
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal gstAmount = estimate != null && estimate.getTotalGstAmount() != null
                ? safe2(estimate.getTotalGstAmount())
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return totalAmount
                .subtract(gstAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }



    private BigDecimal getTotalActiveTdsAmount(
            UnbilledInvoice unbilled
    ) {
        if (unbilled == null) {
            return BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );
        }

        if (isInternationalTransaction(
                unbilled.getEstimate(),
                unbilled
        )) {
            return BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );
        }

        return tdsRegistrationRepository
                .findAllByUnbilledInvoiceAndIsDeletedFalse(unbilled)
                .stream()
                .filter(tds ->
                        tds.getStatus() == TdsStatus.PENDING
                                || tds.getStatus() == TdsStatus.APPROVED
                )
                .map(TdsRegistration::getTdsAmount)
                .filter(Objects::nonNull)
                .map(this::safe2)
                .reduce(
                        BigDecimal.ZERO.setScale(
                                2,
                                RoundingMode.HALF_UP
                        ),
                        BigDecimal::add
                )
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }



    private void approveTdsForPaymentAfterInvoiceCreated(
            PaymentReceipt payment,
            User approver,
            Invoice invoice
    ) {
        // =====================================================
        // 1. BASIC VALIDATION
        // =====================================================
        if (payment == null) {
            log.warn("TDS approval skipped because payment receipt is null");
            return;
        }

        UnbilledInvoice unbilled = payment.getUnbilledInvoice();

        Estimate estimate = unbilled != null
                ? unbilled.getEstimate()
                : null;

        boolean internationalTransaction =
                isInternationalTransaction(
                        estimate,
                        unbilled
                );

        // =====================================================
        // 2. FIND ACTIVE TDS REGISTRATION
        // =====================================================
        Optional<TdsRegistration> tdsOptional =
                tdsRegistrationRepository
                        .findByPaymentReceiptAndIsDeletedFalse(payment);

        // =====================================================
        // 3. INTERNATIONAL TRANSACTION
        // =====================================================
        if (internationalTransaction) {

            /*
             * INTERNATIONAL transactions must never have:
             * - Active TDS registration
             * - Approved TDS registration
             * - TDS date
             * - TDS active flag
             */
            if (unbilled != null) {
                unbilled.setTdsActive(false);
                unbilled.setUpdatedAt(LocalDateTime.now());
            }

            /*
             * Soft-delete any stale TDS registration that may have
             * been created before the company/unit was changed to
             * INTERNATIONAL.
             */
            tdsOptional.ifPresent(tds -> {

                tds.setDeleted(true);
                tds.setTdsDate(null);
                tds.setUpdatedBy(approver);

                tdsRegistrationRepository.save(tds);

                log.warn(
                        "Stale TDS registration disabled for INTERNATIONAL payment "
                                + "| paymentReceiptId={} | tdsId={} | previousStatus={}",
                        payment.getId(),
                        tds.getId(),
                        tds.getStatus()
                );
            });

            /*
             * Explicit save is safe here. The caller also saves the
             * unbilled invoice later, but this keeps this method
             * independently consistent.
             */
            if (unbilled != null && unbilled.getId() != null) {
                unbilledInvoiceRepository.save(unbilled);
            }

            log.info(
                    "TDS approval skipped for INTERNATIONAL payment "
                            + "| paymentReceiptId={} | estimateId={} | unbilledId={}",
                    payment.getId(),
                    estimate != null ? estimate.getId() : null,
                    unbilled != null ? unbilled.getId() : null
            );

            return;
        }

        // =====================================================
        // 4. NO TDS REGISTRATION FOUND
        // =====================================================
        if (tdsOptional.isEmpty()) {
            log.debug(
                    "No active TDS registration found for payment | paymentReceiptId={}",
                    payment.getId()
            );
            return;
        }

        TdsRegistration tds = tdsOptional.get();

        // =====================================================
        // 5. VALIDATE TDS STATUS
        // =====================================================
        /*
         * Only PENDING TDS should move to APPROVED.
         *
         * APPROVED is permitted for idempotency, so repeating the
         * approval API does not corrupt the record.
         *
         * Any other status must not be modified.
         */
        if (tds.getStatus() != TdsStatus.PENDING
                && tds.getStatus() != TdsStatus.APPROVED) {

            log.warn(
                    "TDS approval skipped because current status is not approvable "
                            + "| paymentReceiptId={} | tdsId={} | status={}",
                    payment.getId(),
                    tds.getId(),
                    tds.getStatus()
            );

            return;
        }

        // =====================================================
        // 6. RESOLVE TDS DATE
        // =====================================================
        LocalDate invoiceDate =
                invoice != null && invoice.getInvoiceDate() != null
                        ? invoice.getInvoiceDate()
                        : LocalDate.now();

        // =====================================================
        // 7. APPROVE TDS
        // =====================================================
        if (tds.getStatus() == TdsStatus.PENDING) {
            tds.setStatus(TdsStatus.APPROVED);
        }

        tds.setTdsDate(invoiceDate);
        tds.setUpdatedBy(approver);

        TdsRegistration savedTds =
                tdsRegistrationRepository.save(tds);

        // =====================================================
        // 8. UPDATE UNBILLED FLAG
        // =====================================================
        if (unbilled != null) {
            unbilled.setTdsActive(true);
            unbilled.setUpdatedAt(LocalDateTime.now());
        }

        log.info(
                "TDS approved successfully "
                        + "| paymentReceiptId={} | tdsId={} | tdsDate={} "
                        + "| invoiceId={} | tdsAmount={} | status={}",
                payment.getId(),
                savedTds.getId(),
                invoiceDate,
                invoice != null ? invoice.getId() : null,
                savedTds.getTdsAmount(),
                savedTds.getStatus()
        );
    }



    private boolean isPurchaseOrderPayment(PaymentReceipt payment) {
        if (payment == null || payment.getPaymentType() == null) {
            return false;
        }

        String code = payment.getPaymentType().getCode() != null
                ? payment.getPaymentType().getCode().trim().toUpperCase()
                : "";

        return "PURCHASE_ORDER".equals(code);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }


    private void createOperationProjectForPurchaseOrderIfNotExists(
            UnbilledInvoice unbilled,
            Estimate estimate,
            UnbilledInvoiceApprovalResponseDto response
    ) {
        try {
            ResponseEntity<OperationProjectResponseDto> existingProjectResponse =
                    operationFeignClient.getProjectByUnbilledNumber(unbilled.getUnbilledNumber());

            if (existingProjectResponse.getStatusCode().is2xxSuccessful()
                    && existingProjectResponse.getBody() != null) {
                log.info(
                        "Operation project already exists for PO unbilled | unbilled={} | projectId={}",
                        unbilled.getUnbilledNumber(),
                        existingProjectResponse.getBody().getId()
                );
                return;
            }

        } catch (FeignException.NotFound ex) {
            log.info(
                    "Operation project not found for PO unbilled. Creating project | unbilled={}",
                    unbilled.getUnbilledNumber()
            );
        } catch (FeignException ex) {
            log.error(
                    "Operation service error while checking PO project | unbilled={} | status={} | message={}",
                    unbilled.getUnbilledNumber(),
                    ex.status(),
                    ex.getMessage()
            );
            throw ex;
        }

        OperationProjectRequestDto projectDto = new OperationProjectRequestDto();

        projectDto.setName(response.getName());
        projectDto.setProjectNo(response.getProjectNo());

        projectDto.setSalesPersonId(response.getSalesPersonId());
        projectDto.setSalesPersonName(response.getSalesPersonName());

        projectDto.setProductId(response.getProductId());
        projectDto.setCompanyId(response.getCompanyId());
        projectDto.setUnitId(response.getCompanyUnitId());

        projectDto.setUnbilledNumber(response.getUnbilledNumber());
        projectDto.setEstimateNumber(response.getEstimateNumber());

        projectDto.setContactId(response.getContactId());
        projectDto.setLeadId(response.getLeadId());

        projectDto.setDate(response.getDate());

        projectDto.setTotalAmount(response.getTotalAmount());

        // Important for PO: no payment received yet
        projectDto.setPaidAmount(0.0);

        projectDto.setPaymentTypeId(response.getPaymentTypeId());

        projectDto.setApprovedById(response.getApprovedById());
        projectDto.setCreatedBy(response.getCreatedBy());
        projectDto.setUpdatedBy(response.getUpdatedBy());

        operationFeignClient.createProject(projectDto);

        log.info(
                "Operation project created successfully for PURCHASE_ORDER | unbilled={} | projectNo={}",
                unbilled.getUnbilledNumber(),
                projectDto.getProjectNo()
        );
    }


    private void validatePoBillingEligibility(UnbilledInvoice unbilled) {
        try {
            ResponseEntity<OperationProjectResponseDto> res =
                    operationFeignClient.getProjectByUnbilledNumber(unbilled.getUnbilledNumber());

            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                throw new ValidationException(
                        "Project details not found from Operation Service",
                        "ERR_OPERATION_PROJECT_NOT_FOUND",
                        "unbilledNumber"
                );
            }

            OperationProjectResponseDto project = res.getBody();

            if (!Boolean.TRUE.equals(project.getPoBillingEligible())) {
                throw new ValidationException(
                        "Tax invoice cannot be raised yet. All non-Certification milestones must be completed first.",
                        "ERR_PO_PROJECT_NOT_READY_FOR_TAX_INVOICE",
                        "unbilledNumber"
                );
            }

        } catch (FeignException.NotFound ex) {
            throw new ValidationException(
                    "Project not found in Operation Service for this PO unbilled",
                    "ERR_OPERATION_PROJECT_NOT_FOUND",
                    "unbilledNumber"
            );
        }
    }

    private boolean isCompanyApprovedForPayment(Company company) {
        return company != null
                && !company.isDeleted()
                && (
                company.isAccountsApproved()
                        || company.getOnboardingStatus() == OnboardingStatus.APPROVED
        );
    }

    private boolean isUnitApprovedForPayment(CompanyUnit unit) {
        return unit != null
                && !unit.isDeleted()
                && (
                unit.isAccountsApproved()
                        || unit.getOnboardingStatus() == OnboardingStatus.APPROVED
        );
    }



    private boolean isInternationalTransaction(
            Estimate estimate,
            UnbilledInvoice unbilled
    ) {
        /*
         * Check the Unbilled snapshot.
         */
        if (unbilled != null
                && unbilled.getGstRegistrationType()
                == GstRegistrationType.INTERNATIONAL) {
            return true;
        }

        /*
         * Check the Estimate snapshot.
         */
        if (estimate != null
                && estimate.getGstRegistrationType()
                == GstRegistrationType.INTERNATIONAL) {
            return true;
        }

        /*
         * Check the Unit linked with Unbilled.
         */
        if (unbilled != null
                && unbilled.getUnit() != null
                && unbilled.getUnit().getGstRegistrationType()
                == GstRegistrationType.INTERNATIONAL) {
            return true;
        }

        /*
         * Check the Unit linked with Estimate.
         */
        return estimate != null
                && estimate.getUnit() != null
                && estimate.getUnit().getGstRegistrationType()
                == GstRegistrationType.INTERNATIONAL;
    }


    private GstRegistrationType resolveGstRegistrationType(
            Estimate estimate,
            UnbilledInvoice unbilled
    ) {
        /*
         * INTERNATIONAL must take priority if any available
         * snapshot or linked unit identifies the transaction
         * as INTERNATIONAL.
         */
        if (isInternationalTransaction(estimate, unbilled)) {
            return GstRegistrationType.INTERNATIONAL;
        }

        if (unbilled != null
                && unbilled.getGstRegistrationType() != null) {
            return unbilled.getGstRegistrationType();
        }

        if (estimate != null
                && estimate.getGstRegistrationType() != null) {
            return estimate.getGstRegistrationType();
        }

        if (unbilled != null
                && unbilled.getUnit() != null
                && unbilled.getUnit().getGstRegistrationType() != null) {
            return unbilled.getUnit().getGstRegistrationType();
        }

        if (estimate != null
                && estimate.getUnit() != null
                && estimate.getUnit().getGstRegistrationType() != null) {
            return estimate.getUnit().getGstRegistrationType();
        }

        return GstRegistrationType.REGISTERED;
    }



    private void validateInternationalTdsRestriction(
            PaymentRegistrationRequestDto request,
            Estimate estimate,
            UnbilledInvoice unbilled
    ) {
        if (!isInternationalTransaction(estimate, unbilled)) {
            return;
        }

        /*
         * Reject the request when the frontend sends either:
         *
         * tdsActive = true
         * OR
         * tds object is present
         */
        if (Boolean.TRUE.equals(request.getTdsActive())
                || request.getTds() != null) {

            throw new ValidationException(
                    "TDS is not applicable for INTERNATIONAL transactions",
                    "ERR_TDS_NOT_ALLOWED_FOR_INTERNATIONAL",
                    "tdsActive"
            );
        }

        /*
         * Defensive normalization.
         */
        request.setTdsActive(false);
        request.setTds(null);
    }


    private void validatePurchaseOrderRequest(
            PaymentRegistrationRequestDto request,
            boolean isPurchaseOrder
    ) {

        if (!isPurchaseOrder) {
            return;
        }

        if (request == null
                || request.getPoAttachmentUrl() == null
                || request.getPoAttachmentUrl().trim().isEmpty()) {

            throw new ValidationException(
                    "PO attachment is required for Purchase Order payment",
                    "ERR_PO_ATTACHMENT_REQUIRED",
                    "poAttachmentUrl"
            );
        }
    }




//    private void validatePurchaseOrderRequest(
//            PaymentRegistrationRequestDto request,
//            boolean isPurchaseOrder
//    ) {
//
//        if (!isPurchaseOrder) {
//            return;
//        }
//
//        if (request.getPoNumber() == null
//                || request.getPoNumber().trim().isEmpty()) {
//
//            throw new ValidationException(
//                    "PO number is required for Purchase Order payment",
//                    "ERR_PO_NUMBER_REQUIRED",
//                    "poNumber"
//            );
//        }
//
//        if (request.getPoAttachmentUrl() == null
//                || request.getPoAttachmentUrl().trim().isEmpty()) {
//
//            throw new ValidationException(
//                    "PO attachment is required for Purchase Order payment",
//                    "ERR_PO_ATTACHMENT_REQUIRED",
//                    "poAttachmentUrl"
//            );
//        }
//
//        if (request.getPaymentTermsDays() == null
//                || request.getPaymentTermsDays() < 0) {
//
//            throw new ValidationException(
//                    "Payment terms days is required for Purchase Order payment",
//                    "ERR_PAYMENT_TERMS_DAYS_REQUIRED",
//                    "paymentTermsDays"
//            );
//        }
//
//        if (request.getAmount() == null) {
//            throw new ValidationException(
//                    "Payment amount is required",
//                    "ERR_AMOUNT_REQUIRED",
//                    "amount"
//            );
//        }
//
//        if (request.getAmount().compareTo(BigDecimal.ZERO) < 0) {
//            throw new ValidationException(
//                    "Purchase Order amount cannot be negative",
//                    "ERR_AMOUNT_NEGATIVE",
//                    "amount"
//            );
//        }
//    }





}

