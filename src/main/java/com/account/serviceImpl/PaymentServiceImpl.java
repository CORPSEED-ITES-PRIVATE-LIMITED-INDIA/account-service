
package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateStatus;
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
    @Transactional
    public PaymentRegistrationResponseDto registerPayment(PaymentRegistrationRequestDto request, Long salespersonUserId) {

        log.info("Registering payment | estimateId: {}, amount: {}, mode: {}, ref: {}, salespersonId: {}",
                request.getEstimateId(), request.getAmount(), request.getPaymentMode(),
                request.getTransactionReference(), salespersonUserId);

        // =====================================================
        // 1. BASIC VALIDATIONS
        // =====================================================
        if (request.getAmount() == null) {
            throw new ValidationException("Payment amount is required", "ERR_AMOUNT_REQUIRED", "amount");
        }

        BigDecimal reqAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        // =====================================================
        // 2. GOVERNMENT FEE VALIDATION (if applicable)
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

        // Prevent payment registration on REJECTED estimate
        if (estimate.getStatus() == EstimateStatus.REJECTED) {
            throw new ValidationException(
                    "Cannot register payment against a REJECTED estimate. " +
                            "Estimate " + estimate.getEstimateNumber() + " has been rejected.",
                    "ERR_PAYMENT_ON_REJECTED_ESTIMATE",
                    "estimateId"
            );
        }

        // ===================================================================
        // 4. COMPANY APPROVAL CHECK (Critical Business Rule)
        // ===================================================================
        // Payment registration is blocked if the company is not approved by Accounts.
        // This is a compliance and risk control measure.
        Company company = estimate.getCompany();

        boolean companyApproved =
                company != null
                        && !company.isDeleted()
                        && (
                        company.isAccountsApproved()
                                || company.getOnboardingStatus() == OnboardingStatus.APPROVED
                );

        if (!companyApproved) {
            String companyName = company != null && company.getName() != null ? company.getName() : "N/A";
            String companyStatus = company != null && company.getOnboardingStatus() != null
                    ? company.getOnboardingStatus().name() : "N/A";

            throw new ValidationException(
                    "Payment registration is not allowed because company is not approved by Accounts. " +
                            "Company: " + companyName + ", Status: " + companyStatus,
                    "ERR_COMPANY_NOT_APPROVED_FOR_PAYMENT",
                    "companyId"
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

        PaymentType paymentType = paymentTypeRepository.findById(request.getPaymentTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment type not found with ID: " + request.getPaymentTypeId(),
                        "PAYMENT_TYPE_NOT_FOUND",
                        "PaymentType",
                        request.getPaymentTypeId()
                ));

        String paymentTypeCode = paymentType.getCode() != null
                ? paymentType.getCode().trim().toUpperCase()
                : "";

        boolean isPurchaseOrder = "PURCHASE_ORDER".equals(paymentTypeCode);

        // =====================================================
        // 6. VALIDATE BANK LEDGER
        // =====================================================
        LedgerMaster bankLedger = validateAndGetBankLedger(request, reqAmount);

        // Allow zero amount only for PURCHASE_ORDER (no actual money received yet)
        if (reqAmount.compareTo(BigDecimal.ZERO) < 0) {
            if (!isPurchaseOrder) {
                throw new ValidationException(
                        "Payment amount must be positive",
                        "ERR_AMOUNT_NOT_POSITIVE",
                        "amount"
                );
            }
        }

        // =====================================================
        // 7. TDS VALIDATION
        // =====================================================
        // TDS is only allowed for FULL and PURCHASE_ORDER payment types.
        // TDS percentage is restricted to 2% or 10%.
        validateTdsRequest(request, paymentType);

        // =====================================================
        // 8. EPR (Extended Producer Responsibility) HANDLING
        // =====================================================
        // EPR fields are mandatory only for PRODUCT type estimates.
        // For services, EPR fields are forcefully set to null.
        boolean isProductRelated = isProductRelatedEstimate(estimate);

        if (isProductRelated) {
            validateEprFields(request);
        } else {
            request.setEprFinancialYear(null);
            request.setEprPortalRegistrationNumber(null);
            request.setEprCertificateOrInvoiceNumber(null);
        }

        // =====================================================
        // 9. FIND OR CREATE UNBILLED INVOICE
        // =====================================================
        UnbilledInvoice unbilled =
                unbilledInvoiceRepository.findByEstimateAndIsCancelledFalse(estimate).orElse(null);

        boolean isFirstPayment = (unbilled == null);

        if (isFirstPayment) {
            unbilled = new UnbilledInvoice();
            unbilled.setPublicUuid(UUID.randomUUID().toString());
            unbilled.setUnbilledNumber(generateUnbilledNumber());
            unbilled.setAdvanceInvoiceNumber(generateAdvanceInvoiceNumber());
            unbilled.setEstimate(estimate);
            unbilled.setCompany(estimate.getCompany());
            unbilled.setUnit(estimate.getUnit());
            unbilled.setContact(estimate.getContact());
            unbilled.setCreatedAt(LocalDateTime.now());
            unbilled.setUpdatedAt(LocalDateTime.now());

            BigDecimal total = estimate.getGrandTotal() != null
                    ? estimate.getGrandTotal().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            unbilled.setTotalAmount(total);
            unbilled.setReceivedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            unbilled.setOutstandingAmount(total);
            unbilled.setStatus(UnbilledStatus.PENDING_APPROVAL);
            unbilled.setCreatedBy(salesperson);
            unbilled.setApprovedBy(null);
            unbilled.setApprovedAt(null);
            unbilled.setApprovalRemarks(null);
            unbilled.setRejectionReason(null);

            // Set flags from request
            unbilled.setGovernmentFeeActive(Boolean.TRUE.equals(request.getGovernmentFeeActive()));
            unbilled.setTdsActive(Boolean.TRUE.equals(request.getTdsActive()));

            unbilled = unbilledInvoiceRepository.save(unbilled);

            log.info("Created new UnbilledInvoice {} (PENDING_APPROVAL) for estimate {} with publicUuid {}",
                    unbilled.getUnbilledNumber(), estimate.getEstimateNumber(), unbilled.getPublicUuid());
        }

        // =====================================================
        // 10. GOVERNMENT FEE DUPLICATE CHECK
        // =====================================================
        if (Boolean.TRUE.equals(request.getGovernmentFeeActive())) {
            if (!isFirstPayment) {
                Optional<GovernmentFee> existingByEstimate = governmentFeeRepository.findByEstimate(estimate);
                Optional<GovernmentFee> existingByUnbilled = governmentFeeRepository.findByUnbilledInvoice(unbilled);

                GovernmentFee existingGovernmentFee = existingByUnbilled.orElse(existingByEstimate.orElse(null));

                if (existingGovernmentFee != null) {
                    if (existingGovernmentFee.getStatus() == GovernmentFeeStatus.PENDING) {
                        throw new ValidationException(
                                "Government fee is already registered and pending approval for this estimate/unbilled invoice",
                                "ERR_GOV_FEE_ALREADY_PENDING", "governmentFee");
                    }
                    if (existingGovernmentFee.getStatus() == GovernmentFeeStatus.APPROVED) {
                        throw new ValidationException(
                                "Government fee is already approved for this estimate/unbilled invoice and cannot be added again",
                                "ERR_GOV_FEE_ALREADY_APPROVED", "governmentFee");
                    }
                    throw new ValidationException(
                            "Government fee already exists for this estimate/unbilled invoice",
                            "ERR_GOV_FEE_ALREADY_EXISTS", "governmentFee");
                }
                unbilled.setGovernmentFeeActive(true);
            }
        }

        // =====================================================
        // 11. PREVENT PAYMENT TYPE CHANGE AFTER FIRST PAYMENT
        // =====================================================
        paymentReceiptRepository.findTopByUnbilledInvoiceAndIsCancelledFalseOrderByIdAsc(unbilled)
                .ifPresent(firstReceipt -> {
                    String firstCode = firstReceipt.getPaymentType().getCode().trim().toUpperCase();
                    String newCode = paymentType.getCode().trim().toUpperCase();

                    if (!firstCode.equals(newCode)) {
                        throw new ValidationException(
                                "Payment type cannot be changed after first payment. First type: " + firstCode,
                                "ERR_PAYMENT_TYPE_CHANGE_NOT_ALLOWED",
                                "paymentTypeId"
                        );
                    }
                });

        // =====================================================
        // 12. TDS CALCULATION
        // =====================================================
        // Important: TDS is calculated on taxable value (excluding GST).
        // Settlement Amount = Bank Amount (reqAmount) + TDS Amount
        BigDecimal tdsAmountForThisRegistration = calculateTdsAmountIfRequired(
                request, estimate, unbilled
        );

        // =====================================================
        // 13. VALIDATE PAYMENT RULES (including TDS settlement)
        // =====================================================
        validatePaymentRules(
                paymentType,
                reqAmount,
                unbilled,
                request.getPaymentTermsDays(),
                tdsAmountForThisRegistration
        );

        // =====================================================
        // 14. CREATE TDS RECORD (if applicable)
        // =====================================================
        // TDS can only be created before the first payment is approved.
        createTdsIfRequired(
                request,
                estimate,
                unbilled,
                paymentType,
                salesperson,
                tdsAmountForThisRegistration
        );

        // =====================================================
        // 15. PREVENT PAYMENT FROM EXCEEDING TOTAL OUTSTANDING
        // =====================================================
        BigDecimal approvedAmount = safe2(unbilled.getReceivedAmount());
        BigDecimal pendingAmount = safe2(unbilled.getCurrentReceivedAmount());
        BigDecimal totalAmount = safe2(unbilled.getTotalAmount());

        // Settlement = Bank Received + TDS (this is what customer actually paid)
        BigDecimal settlementAmountForThisRegistration = reqAmount
                .add(tdsAmountForThisRegistration)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalAfterThisRegistration = approvedAmount
                .add(pendingAmount)
                .add(settlementAmountForThisRegistration);

        if (totalAfterThisRegistration.compareTo(totalAmount) > 0) {
            BigDecimal remainingAllowed = totalAmount.subtract(approvedAmount.add(pendingAmount))
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal excessAmount = reqAmount.subtract(remainingAllowed)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            throw new ValidationException(
                    String.format(
                            "Payment exceeds allowed amount. Approved amount is ₹%s, pending approval amount is ₹%s, remaining payable amount is ₹%s, and the current payment of ₹%s exceeds it by ₹%s.",
                            approvedAmount, pendingAmount, remainingAllowed, reqAmount, excessAmount
                    ),
                    "ERR_PAYMENT_EXCEEDS_TOTAL_AMOUNT",
                    "amount"
            );
        }

        // =====================================================
        // 16. CREATE PAYMENT RECEIPT
        // =====================================================
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setUnbilledInvoice(unbilled);
        receipt.setPaymentType(paymentType);
        receipt.setAmount(reqAmount);
        receipt.setPaymentDate(request.getPaymentDate());
        receipt.setPaymentMode(request.getPaymentMode());
        receipt.setTransactionReference(request.getTransactionReference());
        receipt.setRemarks(request.getRemarks());
        receipt.setReceivedBy(salesperson);
        receipt.setPaymentProof(request.getPaymentProof());
        receipt.setPaymentTermsDays(request.getPaymentTermsDays());

        if (request.getPaymentTermsDays() != null && request.getPaymentTermsDays() > 0) {
            receipt.setPaymentTerms("Net " + request.getPaymentTermsDays() + " Days");
        } else {
            receipt.setPaymentTerms(request.getPaymentTerms());
        }

        // Bank ledger is saved for future voucher posting (after approval)
        receipt.setBankLedger(bankLedger);

        // EPR fields (only for product-related estimates)
        receipt.setEprFinancialYear(request.getEprFinancialYear());
        receipt.setEprPortalRegistrationNumber(request.getEprPortalRegistrationNumber());
        receipt.setEprCertificateOrInvoiceNumber(request.getEprCertificateOrInvoiceNumber());

        receipt.setStatus(PaymentStatus.PENDING);

        receipt = paymentReceiptRepository.save(receipt);

        log.info("Created PaymentReceipt {} | amount: {}", receipt.getId(), request.getAmount());

        // =====================================================
        // 17. CREATE LEGAL VERIFICATION FOR PURCHASE ORDER
        // =====================================================
        paymentLegalVerificationService.createIfPurchaseOrder(receipt, salesperson);

        // =====================================================
        // 18. CREATE GOVERNMENT FEE (if applicable)
        // =====================================================
        createGovernmentFeeIfRequired(request, estimate, unbilled, salesperson);

        // =====================================================
        // 19. UPDATE UNBILLED INVOICE TOTALS
        // =====================================================
        // settlementAmountForThisRegistration = Bank Amount + TDS Amount
        unbilled.applyPayment(settlementAmountForThisRegistration);
        unbilled.setStatus(UnbilledStatus.PENDING_APPROVAL);
        unbilledInvoiceRepository.save(unbilled);

        log.info("Updated unbilled {} | received: {}, outstanding: {}, status: {}",
                unbilled.getUnbilledNumber(),
                unbilled.getReceivedAmount(),
                unbilled.getOutstandingAmount(),
                unbilled.getStatus());

        // =====================================================
        // 20. UPDATE ESTIMATE STATUS
        // =====================================================
        estimate.setStatus(EstimateStatus.INITIATED);
        estimateRepository.save(estimate);

        // =====================================================
        // 21. PREPARE RESPONSE MESSAGE
        // =====================================================
        String message = isFirstPayment
                ? "First payment registered. Unbilled created – awaiting Accounts approval"
                : String.format(
                "Additional payment of ₹%s registered. Total received: ₹%s / ₹%s. Awaiting approval.",
                reqAmount, unbilled.getReceivedAmount(), unbilled.getTotalAmount());

        if (Boolean.TRUE.equals(request.getGovernmentFeeActive())) {
            message += " Government fee registered in full and awaiting Accounts approval.";
        }

        if (isPurchaseOrder) {
            message += " PO document sent to Legal department for verification.";
        }

        // =====================================================
        // 22. BUILD AND RETURN RESPONSE
        // =====================================================
        PaymentRegistrationResponseDto response = new PaymentRegistrationResponseDto();
        response.setPaymentReceiptId(receipt.getId());
        response.setUnbilledNumber(unbilled.getUnbilledNumber());
        response.setUnbilledStatus(unbilled.getStatus());
        response.setMessage(message);

        // Notify Accounts team for approval
        pushPaymentRegisteredNotificationToAccountUsers(unbilled, receipt, estimate, salesperson);

        return response;
    }


//    @Override
//    @Transactional
//    public PaymentRegistrationResponseDto registerPayment(PaymentRegistrationRequestDto request, Long salespersonUserId) {
//
//
//        log.info("Registering payment | estimateId: {}, amount: {}, mode: {}, ref: {}, salespersonId: {}",
//                request.getEstimateId(), request.getAmount(), request.getPaymentMode(),
//                request.getTransactionReference(), salespersonUserId);
//
//        // Basic amount validation
//        if (request.getAmount() == null) {
//            throw new ValidationException("Payment amount is required", "ERR_AMOUNT_REQUIRED", "amount");
//        }
//
//        BigDecimal reqAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
//
//        // ===============================
//        // GOVERNMENT FEE VALIDATION
//        // ===============================
//        validateGovernmentFeeRequest(request);
//
//        // Fetch required entities
//        Estimate estimate = estimateRepository.findById(request.getEstimateId())
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        "Estimate not found with ID: " + request.getEstimateId(),
//                        "ESTIMATE_NOT_FOUND",
//                        "Estimate",
//                        request.getEstimateId()
//                ));
//
//        // Prevent payment registration on REJECTED estimate
//        if (estimate.getStatus() == EstimateStatus.REJECTED) {
//            throw new ValidationException(
//                    "Cannot register payment against a REJECTED estimate. " +
//                            "Estimate " + estimate.getEstimateNumber() + " has been rejected.",
//                    "ERR_PAYMENT_ON_REJECTED_ESTIMATE",
//                    "estimateId"
//            );
//        }
//
//        // ===================================================================
//        // BLOCK PAYMENT REGISTRATION IF COMPANY IS NOT APPROVED BY ACCOUNTS
//        // ===================================================================
//        Company company = estimate.getCompany();
//
//        boolean companyApproved =
//                company != null
//                        && !company.isDeleted()
//                        && (
//                        company.isAccountsApproved()
//                                || company.getOnboardingStatus() == OnboardingStatus.APPROVED
//                );
//
//        if (!companyApproved) {
//            String companyName = company != null && company.getName() != null
//                    ? company.getName()
//                    : "N/A";
//
//            String companyStatus = company != null && company.getOnboardingStatus() != null
//                    ? company.getOnboardingStatus().name()
//                    : "N/A";
//
//            throw new ValidationException(
//                    "Payment registration is not allowed because company is not approved by Accounts. " +
//                            "Company: " + companyName + ", Status: " + companyStatus,
//                    "ERR_COMPANY_NOT_APPROVED_FOR_PAYMENT",
//                    "companyId"
//            );
//        }
//        // ===================================================================
//
//        User salesperson = userRepository.findById(salespersonUserId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        "Salesperson not found with ID: " + salespersonUserId,
//                        "USER_NOT_FOUND",
//                        "User",
//                        salespersonUserId
//                ));
//
//        PaymentType paymentType = paymentTypeRepository.findById(request.getPaymentTypeId())
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        "Payment type not found with ID: " + request.getPaymentTypeId(),
//                        "PAYMENT_TYPE_NOT_FOUND",
//                        "PaymentType",
//                        request.getPaymentTypeId()
//                ));
//
//        String paymentTypeCode = paymentType.getCode() != null
//                ? paymentType.getCode().trim().toUpperCase()
//                : "";
//
//        boolean isPurchaseOrder = "PURCHASE_ORDER".equals(paymentTypeCode);
//
//        LedgerMaster bankLedger = validateAndGetBankLedger(request, reqAmount);
//
//        // ===================================================================
//        // ALLOW ZERO AMOUNT ONLY FOR PURCHASE_ORDER PAYMENT TYPE
//        // ===================================================================
//        if (reqAmount.compareTo(BigDecimal.ZERO) < 0) {
//            if (!isPurchaseOrder) {
//                throw new ValidationException(
//                        "Payment amount must be positive",
//                        "ERR_AMOUNT_NOT_POSITIVE",
//                        "amount"
//                );
//            }
//        }
//        // ===================================================================
//
//        validateTdsRequest(request, paymentType);
//
//        // Determine if this is product-related (EPR applies)
//        boolean isProductRelated = isProductRelatedEstimate(estimate);
//
//        // For product-related estimates → mandatory EPR fields
//        if (isProductRelated) {
//            validateEprFields(request);
//        } else {
//            // For services / others → force null (do not save any EPR data)
//            request.setEprFinancialYear(null);
//            request.setEprPortalRegistrationNumber(null);
//            request.setEprCertificateOrInvoiceNumber(null);
//        }
//
//        // Find or create Unbilled Invoice
//        UnbilledInvoice unbilled =
//                unbilledInvoiceRepository.findByEstimateAndIsCancelledFalse(estimate).orElse(null);
//
//        boolean isFirstPayment = (unbilled == null);
//
//        if (isFirstPayment) {
//            unbilled = new UnbilledInvoice();
//            unbilled.setPublicUuid(UUID.randomUUID().toString());
//            unbilled.setUnbilledNumber(generateUnbilledNumber());
//            unbilled.setAdvanceInvoiceNumber(generateAdvanceInvoiceNumber());
//            unbilled.setEstimate(estimate);
//            unbilled.setCompany(estimate.getCompany());
//            unbilled.setUnit(estimate.getUnit());
//            unbilled.setContact(estimate.getContact());
//            unbilled.setCreatedAt(LocalDateTime.now());
//            unbilled.setUpdatedAt(LocalDateTime.now());
//
//            BigDecimal total = estimate.getGrandTotal() != null
//                    ? estimate.getGrandTotal().setScale(2, RoundingMode.HALF_UP)
//                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
//
//            unbilled.setTotalAmount(total);
//            unbilled.setReceivedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
//            unbilled.setOutstandingAmount(total);
//
//            unbilled.setStatus(UnbilledStatus.PENDING_APPROVAL);
//            unbilled.setCreatedBy(salesperson);
//
//            unbilled.setApprovedBy(null);
//            unbilled.setApprovedAt(null);
//            unbilled.setApprovalRemarks(null);
//            unbilled.setRejectionReason(null);
//
//            // GOVERNMENT FEE FLAG
//            unbilled.setGovernmentFeeActive(Boolean.TRUE.equals(request.getGovernmentFeeActive()));
//            unbilled.setTdsActive(Boolean.TRUE.equals(request.getTdsActive()));
//
//            unbilled = unbilledInvoiceRepository.save(unbilled);
//
//            log.info("Created new UnbilledInvoice {} (PENDING_APPROVAL) for estimate {} with publicUuid {}",
//                    unbilled.getUnbilledNumber(), estimate.getEstimateNumber(), unbilled.getPublicUuid());
//        }
//
//        // ===============================
//        // GOVERNMENT FEE DUPLICATE CHECK
//        // ===============================
//        if (Boolean.TRUE.equals(request.getGovernmentFeeActive())) {
//            if (!isFirstPayment) {
//                Optional<GovernmentFee> existingByEstimate =
//                        governmentFeeRepository.findByEstimate(estimate);
//
//                Optional<GovernmentFee> existingByUnbilled =
//                        governmentFeeRepository.findByUnbilledInvoice(unbilled);
//
//                GovernmentFee existingGovernmentFee =
//                        existingByUnbilled.orElse(existingByEstimate.orElse(null));
//
//                if (existingGovernmentFee != null) {
//                    if (existingGovernmentFee.getStatus() == GovernmentFeeStatus.PENDING) {
//                        throw new ValidationException(
//                                "Government fee is already registered and pending approval for this estimate/unbilled invoice",
//                                "ERR_GOV_FEE_ALREADY_PENDING",
//                                "governmentFee"
//                        );
//                    }
//
//                    if (existingGovernmentFee.getStatus() == GovernmentFeeStatus.APPROVED) {
//                        throw new ValidationException(
//                                "Government fee is already approved for this estimate/unbilled invoice and cannot be added again",
//                                "ERR_GOV_FEE_ALREADY_APPROVED",
//                                "governmentFee"
//                        );
//                    }
//
//                    throw new ValidationException(
//                            "Government fee already exists for this estimate/unbilled invoice",
//                            "ERR_GOV_FEE_ALREADY_EXISTS",
//                            "governmentFee"
//                    );
//                }
//
//                unbilled.setGovernmentFeeActive(true);
//            }
//        }
//
//        // Prevent changing payment type after first payment
//        paymentReceiptRepository.findTopByUnbilledInvoiceAndIsCancelledFalseOrderByIdAsc(unbilled)
//                .ifPresent(firstReceipt -> {
//                    String firstCode = firstReceipt.getPaymentType().getCode().trim().toUpperCase();
//                    String newCode = paymentType.getCode().trim().toUpperCase();
//
//                    if (!firstCode.equals(newCode)) {
//                        throw new ValidationException(
//                                "Payment type cannot be changed after first payment. First type: " + firstCode,
//                                "ERR_PAYMENT_TYPE_CHANGE_NOT_ALLOWED",
//                                "paymentTypeId"
//                        );
//                    }
//                });
//
//        BigDecimal tdsAmountForThisRegistration = calculateTdsAmountIfRequired(
//                request,
//                estimate,
//                unbilled
//        );
//
//        validatePaymentRules(
//                paymentType,
//                reqAmount,
//                unbilled,
//                request.getPaymentTermsDays(),
//                tdsAmountForThisRegistration
//        );
//
//        createTdsIfRequired(
//                request,
//                estimate,
//                unbilled,
//                paymentType,
//                salesperson,
//                tdsAmountForThisRegistration
//        );
//
////        createTdsIfRequired(request, estimate, unbilled, paymentType, salesperson);
//
//        // Prevent approved + pending + current request from exceeding total amount
//        BigDecimal approvedAmount = safe2(unbilled.getReceivedAmount());
//        BigDecimal pendingAmount = safe2(unbilled.getCurrentReceivedAmount());
//        BigDecimal totalAmount = safe2(unbilled.getTotalAmount());
//
//        BigDecimal settlementAmountForThisRegistration = reqAmount
//                .add(tdsAmountForThisRegistration)
//                .setScale(2, RoundingMode.HALF_UP);
//
//        BigDecimal totalAfterThisRegistration = approvedAmount
//                .add(pendingAmount)
//                .add(settlementAmountForThisRegistration);
//
//
//
//        if (totalAfterThisRegistration.compareTo(totalAmount) > 0) {
//            BigDecimal remainingAllowed = totalAmount.subtract(approvedAmount.add(pendingAmount))
//                    .max(BigDecimal.ZERO)
//                    .setScale(2, RoundingMode.HALF_UP);
//
//            BigDecimal excessAmount = reqAmount.subtract(remainingAllowed)
//                    .max(BigDecimal.ZERO)
//                    .setScale(2, RoundingMode.HALF_UP);
//
//            throw new ValidationException(
//                    String.format(
//                            "Payment exceeds allowed amount. Approved amount is ₹%s, pending approval amount is ₹%s, remaining payable amount is ₹%s, and the current payment of ₹%s exceeds it by ₹%s.",
//                            approvedAmount,
//                            pendingAmount,
//                            remainingAllowed,
//                            reqAmount,
//                            excessAmount
//                    ),
//                    "ERR_PAYMENT_EXCEEDS_TOTAL_AMOUNT",
//                    "amount"
//            );
//        }
//
//        // Create and save payment receipt
//        PaymentReceipt receipt = new PaymentReceipt();
//        receipt.setUnbilledInvoice(unbilled);
//        receipt.setPaymentType(paymentType);
//        receipt.setAmount(reqAmount);
//        receipt.setPaymentDate(request.getPaymentDate());
//        receipt.setPaymentMode(request.getPaymentMode());
//        receipt.setTransactionReference(request.getTransactionReference());
//        receipt.setRemarks(request.getRemarks());
//        receipt.setReceivedBy(salesperson);
//        receipt.setPaymentProof(request.getPaymentProof());
//
//        /*
//         * NEW:
//         * Save PO payment terms on receipt.
//         * Required especially for PURCHASE_ORDER payment type.
//         */
//        receipt.setPaymentTermsDays(request.getPaymentTermsDays());
//
//        if (request.getPaymentTermsDays() != null && request.getPaymentTermsDays() > 0) {
//            receipt.setPaymentTerms("Net " + request.getPaymentTermsDays() + " Days");
//        } else {
//            receipt.setPaymentTerms(request.getPaymentTerms());
//        }
//
//        /*
//         * Save selected bank ledger with pending payment.
//         * Ledger voucher will be posted only after account approval.
//         */
//        receipt.setBankLedger(bankLedger);
//
//        // EPR fields - saved only for product-related estimates (otherwise null)
//        receipt.setEprFinancialYear(request.getEprFinancialYear());
//        receipt.setEprPortalRegistrationNumber(request.getEprPortalRegistrationNumber());
//        receipt.setEprCertificateOrInvoiceNumber(request.getEprCertificateOrInvoiceNumber());
//
//        receipt.setStatus(PaymentStatus.PENDING);
//
//        receipt = paymentReceiptRepository.save(receipt);
//
//        log.info("Created PaymentReceipt {} | amount: {}", receipt.getId(), request.getAmount());
//
//        /*
//         * NEW:
//         * If payment type is PURCHASE_ORDER,
//         * create Legal Verification Request for uploaded PO attachment.
//         *
//         * Current assumption:
//         * request.paymentProof = PO attachment / PO agreement URL.
//         */
//        paymentLegalVerificationService.createIfPurchaseOrder(receipt, salesperson);
//
//        // ===============================
//        // CREATE GOVERNMENT FEE IF REQUIRED
//        // ===============================
//        createGovernmentFeeIfRequired(request, estimate, unbilled, salesperson);
//
//        // Update unbilled totals
//        unbilled.applyPayment(settlementAmountForThisRegistration);
//        unbilled.setStatus(UnbilledStatus.PENDING_APPROVAL);
//        unbilledInvoiceRepository.save(unbilled);
//
//        log.info("Updated unbilled {} | received: {}, outstanding: {}, status: {}",
//                unbilled.getUnbilledNumber(),
//                unbilled.getReceivedAmount(),
//                unbilled.getOutstandingAmount(),
//                unbilled.getStatus());
//
//        // Update estimate status
//        estimate.setStatus(EstimateStatus.INITIATED);
//        estimateRepository.save(estimate);
//
//        // Prepare user-friendly message
//        String message = isFirstPayment
//                ? "First payment registered. Unbilled created – awaiting Accounts approval"
//                : String.format(
//                "Additional payment of ₹%s registered. Total received: ₹%s / ₹%s. Awaiting approval.",
//                reqAmount,
//                unbilled.getReceivedAmount(),
//                unbilled.getTotalAmount()
//        );
//
//        if (Boolean.TRUE.equals(request.getGovernmentFeeActive())) {
//            message += " Government fee registered in full and awaiting Accounts approval.";
//        }
//
//        if (isPurchaseOrder) {
//            message += " PO document sent to Legal department for verification.";
//        }
//
//        // Build response
//        PaymentRegistrationResponseDto response = new PaymentRegistrationResponseDto();
//        response.setPaymentReceiptId(receipt.getId());
//        response.setUnbilledNumber(unbilled.getUnbilledNumber());
//        response.setUnbilledStatus(unbilled.getStatus());
//        response.setMessage(message);
//
//        /*
//         * Notify account department users that payment is registered
//         * and waiting for approval.
//         */
//        pushPaymentRegisteredNotificationToAccountUsers(
//                unbilled,
//                receipt,
//                estimate,
//                salesperson
//        );
//
//        return response;
//    }


    private LedgerMaster validateAndGetBankLedger(
            PaymentRegistrationRequestDto request,
            BigDecimal reqAmount
    ) {
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

//    private void validatePaymentRules(
//            PaymentType paymentType,
//            BigDecimal reqAmount,
//            UnbilledInvoice unbilled,
//            Integer paymentTermsDays,
//            BigDecimal tdsAmount
//    ) {
//
//        if (paymentType == null || paymentType.getCode() == null) {
//            throw new ValidationException(
//                    "Invalid payment type",
//                    "ERR_PAYMENT_TYPE_INVALID",
//                    "paymentTypeId"
//            );
//        }
//
//        BigDecimal outstanding = safe2(unbilled.getOutstandingAmount());
//        BigDecimal total = safe2(unbilled.getTotalAmount());
//        BigDecimal safeReqAmount = safe2(reqAmount);
//        BigDecimal safeTdsAmount = safe2(tdsAmount);
//
//        /*
//         * If TDS is active:
//         * actual customer settlement = bank received + TDS receivable.
//         *
//         * Example:
//         * Bank received = 216
//         * TDS = 20
//         * Settlement = 236
//         */
//        BigDecimal settlementAmount = safeReqAmount
//                .add(safeTdsAmount)
//                .setScale(2, RoundingMode.HALF_UP);
//
//        String code = paymentType.getCode().trim().toUpperCase();
//
//        boolean isPurchaseOrder = "PURCHASE_ORDER".equals(code);
//
//        if (!isPurchaseOrder && safeReqAmount.compareTo(BigDecimal.ZERO) <= 0) {
//            throw new ValidationException(
//                    "Amount must be positive",
//                    "ERR_AMOUNT_NOT_POSITIVE",
//                    "amount"
//            );
//        }
//
//        if (isPurchaseOrder) {
//            if (paymentTermsDays == null || paymentTermsDays < 0) {
//                throw new ValidationException(
//                        "Payment terms days is required for Purchase Order payment type",
//                        "ERR_PAYMENT_TERMS_DAYS_REQUIRED",
//                        "paymentTermsDays"
//                );
//            }
//        }
//
//        if (settlementAmount.compareTo(outstanding) > 0) {
//            throw new ValidationException(
//                    "Settlement amount is greater than outstanding amount. Bank amount: "
//                            + safeReqAmount + ", TDS amount: " + safeTdsAmount
//                            + ", Settlement amount: " + settlementAmount
//                            + ", Outstanding: " + outstanding,
//                    "ERR_AMOUNT_EXCEEDS_OUTSTANDING",
//                    "amount"
//            );
//        }
//
//        if ("FULL".equals(code)) {
//            if (settlementAmount.compareTo(outstanding) != 0) {
//                throw new ValidationException(
//                        "FULL payment settlement must equal outstanding amount. Bank amount: "
//                                + safeReqAmount + ", TDS amount: " + safeTdsAmount
//                                + ", Settlement amount: " + settlementAmount
//                                + ", Outstanding: " + outstanding,
//                        "ERR_FULL_AMOUNT_MISMATCH",
//                        "amount"
//                );
//            }
//            return;
//        }
//
//        if ("PARTIAL".equals(code)) {
//            BigDecimal half = total
//                    .multiply(new BigDecimal("0.50"))
//                    .setScale(2, RoundingMode.HALF_UP);
//
//            BigDecimal expected = (outstanding.compareTo(half) < 0)
//                    ? outstanding
//                    : half;
//
//            if (settlementAmount.compareTo(expected) != 0) {
//                throw new ValidationException(
//                        "PARTIAL payment settlement must be " + expected
//                                + ". Bank amount: " + safeReqAmount
//                                + ", TDS amount: " + safeTdsAmount
//                                + ", Settlement amount: " + settlementAmount,
//                        "ERR_PARTIAL_AMOUNT_MISMATCH",
//                        "amount"
//                );
//            }
//            return;
//        }
//
//        if ("INSTALLMENT".equals(code) || "PURCHASE_ORDER".equals(code)) {
//            return;
//        }
//
//        throw new ValidationException(
//                "Unsupported payment type: " + paymentType.getCode(),
//                "ERR_UNSUPPORTED_PAYMENT_TYPE",
//                "paymentTypeId"
//        );
//    }



    private void validateGovernmentFeeRequest(PaymentRegistrationRequestDto request) {
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
            return;
        }

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

        governmentFeeRepository.save(governmentFee);

        unbilled.setGovernmentFeeActive(true);
    }

    private void validateTdsRequest(PaymentRegistrationRequestDto request, PaymentType paymentType) {

        boolean tdsActive = Boolean.TRUE.equals(request.getTdsActive());

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
    }

    private void createTdsIfRequired(
            PaymentRegistrationRequestDto request,
            Estimate estimate,
            UnbilledInvoice unbilled,
            PaymentType paymentType,
            User salesperson,
            BigDecimal tdsAmount
    ) {
        if (!Boolean.TRUE.equals(request.getTdsActive())) {
            return;
        }

        /*
         * TDS is allowed one time only for one unbilled invoice.
         * It may be entered during first payment, second payment, or later payment,
         * but once created, it cannot be created again.
         */
        Optional<TdsRegistration> existingTdsOpt =
                tdsRegistrationRepository.findByUnbilledInvoiceAndIsDeletedFalse(unbilled);

        if (existingTdsOpt.isPresent()) {
            TdsRegistration existingTds = existingTdsOpt.get();

            if (existingTds.getStatus() == TdsStatus.PENDING) {
                throw new ValidationException(
                        "TDS is already registered and pending approval for this unbilled invoice",
                        "ERR_TDS_ALREADY_PENDING",
                        "tds"
                );
            }

            if (existingTds.getStatus() == TdsStatus.APPROVED) {
                throw new ValidationException(
                        "TDS is already approved for this unbilled invoice and cannot be added again",
                        "ERR_TDS_ALREADY_APPROVED",
                        "tds"
                );
            }

            throw new ValidationException(
                    "TDS already exists for this unbilled invoice",
                    "ERR_TDS_ALREADY_EXISTS",
                    "tds"
            );
        }

        BigDecimal safeTdsAmount = safe2(tdsAmount);

        if (safeTdsAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "TDS amount must be greater than zero when TDS is active",
                    "ERR_TDS_AMOUNT_INVALID",
                    "tds"
            );
        }

        BigDecimal taxableAmount = calculateCurrentPaymentTaxableAmount(
                request,
                estimate,
                unbilled,
                safeTdsAmount
        );

        BigDecimal tdsPercentage = safe2(request.getTds().getTdsPercentage());

        TdsRegistration tds = new TdsRegistration();
        tds.setPublicUuid(UUID.randomUUID().toString());
        tds.setEstimate(estimate);
        tds.setCompany(estimate.getCompany());
        tds.setUnbilledInvoice(unbilled);
        tds.setTdsPercentage(tdsPercentage);
        tds.setTaxableAmount(taxableAmount);
        tds.setTdsAmount(safeTdsAmount);
        tds.setStatus(TdsStatus.PENDING);
        tds.setDeleted(false);
        tds.setCreatedBy(salesperson);

        tdsRegistrationRepository.save(tds);

        unbilled.setTdsActive(true);

        log.info(
                "TDS registered one time | unbilled={} | taxableAmount={} | percentage={} | tdsAmount={}",
                unbilled.getUnbilledNumber(),
                taxableAmount,
                tdsPercentage,
                safeTdsAmount
        );
    }





    private BigDecimal calculateTdsTaxableAmount(Estimate estimate, UnbilledInvoice unbilled) {

        /*
         * TDS should be calculated on amount excluding GST.
         *
         * Example:
         * Service cost = 500
         * GST 18% = 90
         * Total = 590
         *
         * TDS base = 590 - 90 = 500
         */

        if (estimate != null && estimate.getSubTotalExGst() != null) {
            return safe2(estimate.getSubTotalExGst());
        }

        BigDecimal totalAmount = safe2(unbilled.getTotalAmount());

        BigDecimal gstAmount = estimate != null && estimate.getTotalGstAmount() != null
                ? safe2(estimate.getTotalGstAmount())
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return totalAmount.subtract(gstAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
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
            throw new ValidationException("Approval request is required", "ERR_APPROVAL_REQUEST_REQUIRED", "request");
        }

        if (request.getApproverUserId() == null) {
            throw new ValidationException("Approver user ID is required", "ERR_APPROVER_USER_REQUIRED", "approverUserId");
        }

        log.info("Approving unbilled invoice | unbilledId: {}, approverId: {}", unbilledId, request.getApproverUserId());

        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled invoice not found with ID: " + unbilledId,
                        "UNBILLED_NOT_FOUND", "UnbilledInvoice", unbilledId));

        if (unbilled.getStatus() != UnbilledStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Only PENDING_APPROVAL unbilled invoices can be approved/rejected. Current status: " + unbilled.getStatus());
        }

        String approvalDecision = request.getApprovalRemarks() != null
                ? request.getApprovalRemarks().trim().toUpperCase() : "";

        if (!"APPROVED".equals(approvalDecision) && !"REJECTED".equals(approvalDecision)) {
            throw new ValidationException("Invalid approval decision. Allowed values are APPROVED or REJECTED",
                    "ERR_INVALID_APPROVAL_DECISION", "approvalRemarks");
        }

        Company company = unbilled.getCompany();
        CompanyUnit unit = unbilled.getUnit();
        Estimate estimate = unbilled.getEstimate();

        if (estimate == null) {
            throw new ResourceNotFoundException("Estimate not found for unbilled invoice: " + unbilled.getUnbilledNumber(),
                    "ESTIMATE_NOT_FOUND");
        }

        User approver = userRepository.findById(request.getApproverUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approver not found with ID: " + request.getApproverUserId(),
                        "USER_NOT_FOUND", "User", request.getApproverUserId()));

        // Company & Unit must be approved for APPROVED flow only
        if ("APPROVED".equals(approvalDecision)) {
            boolean companyApproved = company != null && !company.isDeleted() &&
                    (company.isAccountsApproved() || company.getOnboardingStatus() == OnboardingStatus.APPROVED);

            boolean unitApproved = unit != null && !unit.isDeleted() &&
                    (unit.isAccountsApproved() || unit.getOnboardingStatus() == OnboardingStatus.APPROVED);

            if (!companyApproved || !unitApproved) {
                throw new ApprovalBlockedException(
                        "Cannot approve unbilled invoice. Company and Company Unit must both be approved before invoice approval.",
                        companyApproved, unitApproved);
            }
        }

        // ==================== REJECTED FLOW ====================
        if ("REJECTED".equals(approvalDecision)) {
            unbilled.setStatus(UnbilledStatus.REJECTED);

            if (unbilled.getPayments() != null) {
                unbilled.getPayments().forEach(p -> {
                    if (p.getStatus() == PaymentStatus.PENDING) p.setStatus(PaymentStatus.REJECTED);
                });
            }

            unbilled.setCurrentReceivedAmount(BigDecimal.ZERO);

            governmentFeeRepository.findByUnbilledInvoice(unbilled).ifPresent(gf -> {
                if (gf.getStatus() == GovernmentFeeStatus.PENDING) {
                    governmentFeeRepository.delete(gf);
                    unbilled.setGovernmentFeeActive(false);
                }
            });

            tdsRegistrationRepository.findByUnbilledInvoiceAndIsDeletedFalse(unbilled).ifPresent(tds -> {
                if (tds.getStatus() == TdsStatus.PENDING) {
                    tdsRegistrationRepository.delete(tds);
                    unbilled.setTdsActive(false);
                }
            });

            unbilled.setApprovedBy(approver);
            unbilled.setApprovedAt(dateTimeUtil.nowLocalDateTime());
            unbilled.setApprovalRemarks(request.getApprovalRemarks());

            if (estimate != null) estimateRepository.save(estimate);
            unbilledInvoiceRepository.save(unbilled);

            pushPaymentApprovalDecisionNotificationToSalesperson(unbilled, estimate, approver, false, request.getApprovalRemarks());

            log.info("Unbilled {} rejected.", unbilled.getUnbilledNumber());

            UnbilledInvoiceApprovalResponseDto response = new UnbilledInvoiceApprovalResponseDto();
            response.setName(estimate != null ? estimate.getSolutionName() : (company != null ? company.getName() + " - Project" : "Unnamed Project"));
            response.setProjectNo(generateProjectNumber());
            response.setSalesPersonId(unbilled.getCreatedBy() != null ? unbilled.getCreatedBy().getId() : null);
            response.setSalesPersonName(unbilled.getCreatedBy() != null ? (unbilled.getCreatedBy().getFullName() != null ? unbilled.getCreatedBy().getFullName() : unbilled.getCreatedBy().getEmail()) : null);
            response.setProductId(estimate != null ? estimate.getSolutionId() : null);
            response.setCompanyId(company != null ? company.getId() : null);
            response.setCompanyUnitId(unbilled.getUnit() != null ? unbilled.getUnit().getId() : null);
            response.setUnbilledNumber(unbilled.getUnbilledNumber());
            response.setAdvanceInvoiceNumber(unbilled.getAdvanceInvoiceNumber());
            response.setAdvanceInvoiceFlag(unbilled.isAdvanceInvoiceFlag());
            response.setEstimateNumber(estimate != null ? estimate.getEstimateNumber() : null);
            response.setContactId(unbilled.getContact() != null ? unbilled.getContact().getId() : null);
            response.setLeadId(estimate != null ? estimate.getLeadId() : null);
            response.setDate(LocalDate.now());
            response.setTotalAmount(unbilled.getTotalAmount() != null ? unbilled.getTotalAmount().doubleValue() : 0.0);
            response.setPaidAmount(unbilled.getReceivedAmount() != null ? unbilled.getReceivedAmount().doubleValue() : 0.0);
            response.setPaymentTypeId(null);
            response.setApprovedById(approver.getId());
            response.setCreatedBy(unbilled.getCreatedBy() != null ? unbilled.getCreatedBy().getId() : null);
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

        List<PaymentReceipt> paymentsToApprove = unbilled.getPayments() == null ? List.of() :
                unbilled.getPayments().stream()
                        .filter(p -> p.getStatus() == PaymentStatus.PENDING && !p.isCancelled())
                        .toList();

        if (paymentsToApprove.isEmpty()) {
            throw new ValidationException("No pending payment found for approval", "ERR_NO_PENDING_PAYMENT_FOUND", "payments");
        }

        paymentsToApprove.forEach(p -> p.setStatus(PaymentStatus.APPROVED));

        BigDecimal tdsAmountForLedger = getPendingTdsAmountForLedger(unbilled);

        BigDecimal newlyApprovedBankAmount = paymentsToApprove.stream()
                .map(PaymentReceipt::getAmount)
                .filter(Objects::nonNull)
                .map(this::safe2)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);

        BigDecimal newlyApprovedAmount = newlyApprovedBankAmount.add(tdsAmountForLedger).setScale(2, RoundingMode.HALF_UP);

        BigDecimal updatedReceived = safe2(unbilled.getReceivedAmount()).add(newlyApprovedAmount).setScale(2, RoundingMode.HALF_UP);
        unbilled.setReceivedAmount(updatedReceived);
        unbilled.setCurrentReceivedAmount(BigDecimal.ZERO);
        unbilled.setOutstandingAmount(safe2(unbilled.getTotalAmount()).subtract(updatedReceived).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));

        boolean tdsPosted = false;
        for (PaymentReceipt payment : paymentsToApprove) {
            if (safe2(payment.getAmount()).compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal tdsForVoucher = !tdsPosted ? tdsAmountForLedger : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                postReceiptVoucherForApprovedPayment(unbilled, payment, approver, tdsForVoucher);
                if (tdsForVoucher.compareTo(BigDecimal.ZERO) > 0) tdsPosted = true;
            }
        }

        governmentFeeRepository.findByUnbilledInvoice(unbilled).ifPresent(gf -> {
            if (gf.getStatus() == GovernmentFeeStatus.PENDING) {
                gf.setStatus(GovernmentFeeStatus.APPROVED);
                governmentFeeRepository.save(gf);
            }
        });

        tdsRegistrationRepository.findByUnbilledInvoiceAndIsDeletedFalse(unbilled).ifPresent(tds -> {
            if (tds.getStatus() == TdsStatus.PENDING) {
                tds.setStatus(TdsStatus.APPROVED);
                tds.setUpdatedBy(approver);
                tdsRegistrationRepository.save(tds);
                unbilled.setTdsActive(true);
            }
        });

        unbilled.setApprovedBy(approver);
        unbilled.setApprovedAt(dateTimeUtil.nowLocalDateTime());
        unbilled.setApprovalRemarks(request.getApprovalRemarks());
        estimateRepository.save(estimate);
        unbilledInvoiceRepository.save(unbilled);

        boolean tdsUsedForInvoice = false;

        for (PaymentReceipt payment : paymentsToApprove) {
            if (!invoiceRepository.existsByTriggeringPayment(payment)) {

                BigDecimal tdsForInvoice = !tdsUsedForInvoice
                        ? tdsAmountForLedger
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

                invoiceService.generateInvoiceForPayment(
                        unbilled,
                        payment,
                        approver,
                        tdsForInvoice
                );

                if (tdsForInvoice.compareTo(BigDecimal.ZERO) > 0) {
                    tdsUsedForInvoice = true;
                }
            }
        }

        PaymentReceipt triggeringReceipt = paymentsToApprove.stream()
                .filter(p -> p.getCreatedAt() != null)
                .max(Comparator.comparing(PaymentReceipt::getCreatedAt))
                .orElse(paymentsToApprove.get(paymentsToApprove.size() - 1));

        pushPaymentApprovalDecisionNotificationToSalesperson(unbilled, estimate, approver, true, request.getApprovalRemarks());

        // ==================== BUILD RESPONSE (APPROVED) ====================
        UnbilledInvoiceApprovalResponseDto response = new UnbilledInvoiceApprovalResponseDto();

        response.setName(estimate != null ? estimate.getSolutionName() : (company != null ? company.getName() + " - Project" : "Unnamed Project"));
        response.setProjectNo(generateProjectNumber());
        response.setSalesPersonId(unbilled.getCreatedBy() != null ? unbilled.getCreatedBy().getId() : null);
        response.setSalesPersonName(unbilled.getCreatedBy() != null ? (unbilled.getCreatedBy().getFullName() != null ? unbilled.getCreatedBy().getFullName() : unbilled.getCreatedBy().getEmail()) : null);
        response.setProductId(estimate != null ? estimate.getSolutionId() : null);
        response.setCompanyId(company != null ? company.getId() : null);
        response.setCompanyUnitId(unbilled.getUnit() != null ? unbilled.getUnit().getId() : null);
        response.setUnbilledNumber(unbilled.getUnbilledNumber());
        response.setAdvanceInvoiceNumber(unbilled.getAdvanceInvoiceNumber());
        response.setAdvanceInvoiceFlag(unbilled.isAdvanceInvoiceFlag());
        response.setEstimateNumber(estimate != null ? estimate.getEstimateNumber() : null);
        response.setContactId(unbilled.getContact() != null ? unbilled.getContact().getId() : null);
        response.setLeadId(estimate != null ? estimate.getLeadId() : null);
        response.setDate(LocalDate.now());
        response.setTotalAmount(unbilled.getTotalAmount() != null ? unbilled.getTotalAmount().doubleValue() : 0.0);
        response.setPaidAmount(unbilled.getReceivedAmount() != null ? unbilled.getReceivedAmount().doubleValue() : 0.0);
        response.setPaymentTypeId(triggeringReceipt != null && triggeringReceipt.getPaymentType() != null ? triggeringReceipt.getPaymentType().getId() : null);
        response.setApprovedById(approver.getId());
        response.setCreatedBy(unbilled.getCreatedBy() != null ? unbilled.getCreatedBy().getId() : null);
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
        dto.setTdsActiveFlag(unbilled.isTdsActive());

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
            tdsRegistrationRepository.findByUnbilledInvoiceAndIsDeletedFalse(unbilled)
                    .ifPresent(tds -> {
                        dto.setTdsResponseDto(mapToTdsResponseDtoForSummary(tds));
                    });
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
        dto.setTdsActiveFlag(unbilled.isTdsActive());
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


        log.info("Converting unbilled into advance invoice", unbilledId, requestingUserId);

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
        return mapToDetailDto(unbilledInvoice);
    }

    @Override
    @Transactional(readOnly = true)
    public GovernmentFeeResponseDto getGovernmentFee(Long unbilledId, Long estimateId) {

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
    @Transactional
    public TdsResponseDto getTds(Long unbilledId, Long estimateId) {

        if (unbilledId == null && estimateId == null) {
            throw new ValidationException(
                    "Either unbilledId or estimateId is required",
                    "ERR_TDS_FILTER_REQUIRED",
                    "unbilledId/estimateId"
            );
        }

        TdsRegistration tds;

        if (unbilledId != null) {
            UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Unbilled invoice not found with ID: " + unbilledId,
                            "UNBILLED_NOT_FOUND",
                            "UnbilledInvoice",
                            unbilledId
                    ));

            tds = tdsRegistrationRepository.findByUnbilledInvoiceAndIsDeletedFalse(unbilled)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "TDS not found for unbilled invoice ID: " + unbilledId,
                            "TDS_NOT_FOUND",
                            "TdsRegistration",
                            unbilledId
                    ));
        } else {
            Estimate estimate = estimateRepository.findById(estimateId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Estimate not found with ID: " + estimateId,
                            "ESTIMATE_NOT_FOUND",
                            "Estimate",
                            estimateId
                    ));

            tds = tdsRegistrationRepository.findByEstimateAndIsDeletedFalse(estimate)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "TDS not found for estimate ID: " + estimateId,
                            "TDS_NOT_FOUND",
                            "TdsRegistration",
                            estimateId
                    ));
        }

        return mapToTdsResponseDto(tds);
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

        if (paymentReceipt.getBankLedger() == null || paymentReceipt.getBankLedger().getId() == null) {
            throw new ValidationException(
                    "Bank ledger is missing in payment receipt",
                    "ERR_PAYMENT_BANK_LEDGER_MISSING",
                    "bankLedgerId"
            );
        }

        LedgerMaster bankLedger = paymentReceipt.getBankLedger();

        LedgerMaster customerLedger = getOrCreateCustomerLedger(
                unbilled,
                approver
        );

        BigDecimal bankAmount = safe2(paymentReceipt.getAmount());
        BigDecimal safeTdsAmount = safe2(tdsAmount);

        /*
         * Customer credit should be Bank amount + TDS amount.
         *
         * Example:
         * Invoice/customer settlement = 98,000 received in bank + 2,000 TDS
         *
         * Dr Bank              98,000
         * Dr TDS Receivable     2,000
         * Cr Customer          100,000
         */
        BigDecimal customerCreditAmount = bankAmount
                .add(safeTdsAmount)
                .setScale(2, RoundingMode.HALF_UP);

        List<AccountingVoucherEntryRequestDto> entries = new ArrayList<>();

        /*
         * Dr Bank / Cash Ledger
         */
        entries.add(
                AccountingVoucherEntryRequestDto.builder()
                        .ledgerId(bankLedger.getId())
                        .debitAmount(bankAmount)
                        .creditAmount(BigDecimal.ZERO)
                        .narration("Payment received in " + bankLedger.getLedgerName())
                        .build()
        );

        /*
         * Dr TDS Receivable Ledger
         */
        if (safeTdsAmount.compareTo(BigDecimal.ZERO) > 0) {

            LedgerMaster tdsReceivableLedger = getOrCreateSystemLedger(
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
                            .creditAmount(BigDecimal.ZERO)
                            .narration("TDS receivable booked for unbilled " + unbilled.getUnbilledNumber())
                            .build()
            );
        }

        /*
         * Cr Customer Ledger / Sundry Debtors
         */
        entries.add(
                AccountingVoucherEntryRequestDto.builder()
                        .ledgerId(customerLedger.getId())
                        .debitAmount(BigDecimal.ZERO)
                        .creditAmount(customerCreditAmount)
                        .narration("Payment received from customer")
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
                        .sourceType(VoucherSourceType.PAYMENT_RECEIPT)
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
                "Receipt voucher posted | paymentReceiptId={} | bankAmount={} | tdsAmount={} | customerCreditAmount={}",
                paymentReceipt.getId(),
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

        Long companyId = company.getId();

        LedgerGroup sundryDebtorsGroup =
                getOrCreateLedgerGroupByType(LedgerGroupType.SUNDRY_DEBTORS);

        String companyName = company.getName() != null && !company.getName().trim().isEmpty()
                ? company.getName().trim()
                : "Company-" + companyId;

        /*
         * IMPORTANT:
         * Only ONE ledger per company.
         *
         * If old CUSTOMER_ADVANCE ledger already exists,
         * reuse and convert it to CUSTOMER / SUNDRY_DEBTORS.
         *
         * Do not create separate:
         * 1. CUSTOMER_ADVANCE
         * 2. CUSTOMER
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

            /*
             * Keep latest unit/contact details in the same company ledger.
             * Do NOT create unit-wise separate ledger.
             */
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
         * No old ledger found, so create only ONE new company ledger.
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
                        return ledgerGroupRepository.save(existingGroup);
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

                    return ledgerGroupRepository.save(ledgerGroup);
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
    private BigDecimal getPendingTdsAmountForLedger(UnbilledInvoice unbilled) {

        if (unbilled == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return tdsRegistrationRepository.findByUnbilledInvoiceAndIsDeletedFalse(unbilled)
                .filter(tds -> tds.getStatus() == TdsStatus.PENDING)
                .map(tds -> safe2(tds.getTdsAmount()))
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
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

        return ledgerMasterRepository.save(ledger);
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

    private BigDecimal calculateTdsAmountIfRequired(
            PaymentRegistrationRequestDto request,
            Estimate estimate,
            UnbilledInvoice unbilled
    ) {
        if (!Boolean.TRUE.equals(request.getTdsActive())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (request.getTds() == null || request.getTds().getTdsPercentage() == null) {
            throw new ValidationException(
                    "TDS percentage is required when TDS is active",
                    "ERR_TDS_PERCENTAGE_REQUIRED",
                    "tds.tdsPercentage"
            );
        }

        BigDecimal bankAmount = safe2(request.getAmount());
        BigDecimal tdsPercentage = safe2(request.getTds().getTdsPercentage());

        if (bankAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Bank amount must be greater than zero when TDS is active",
                    "ERR_TDS_BANK_AMOUNT_REQUIRED",
                    "amount"
            );
        }

        BigDecimal totalInvoiceAmount = safe2(unbilled.getTotalAmount());
        BigDecimal totalTaxableAmount = calculateTdsTaxableAmount(estimate, unbilled);

        if (totalInvoiceAmount.compareTo(BigDecimal.ZERO) <= 0
                || totalTaxableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        /*
         * TDS is on taxable amount, not GST.
         *
         * For current payment:
         * bankAmount is net received after TDS.
         *
         * Formula:
         *
         * taxableRatio = totalTaxableAmount / totalInvoiceAmount
         * effectiveTdsRate = taxableRatio * tdsRate
         * tdsAmount = bankAmount * effectiveTdsRate / (1 - effectiveTdsRate)
         */
        BigDecimal taxableRatio = totalTaxableAmount.divide(
                totalInvoiceAmount,
                10,
                RoundingMode.HALF_UP
        );

        BigDecimal tdsRate = tdsPercentage.divide(
                BigDecimal.valueOf(100),
                10,
                RoundingMode.HALF_UP
        );

        BigDecimal effectiveTdsRate = taxableRatio.multiply(tdsRate);

        BigDecimal denominator = BigDecimal.ONE.subtract(effectiveTdsRate);

        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Invalid TDS calculation",
                    "ERR_INVALID_TDS_CALCULATION",
                    "tds"
            );
        }

        return bankAmount
                .multiply(effectiveTdsRate)
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }



    private BigDecimal calculateCurrentPaymentTaxableAmount(
            PaymentRegistrationRequestDto request,
            Estimate estimate,
            UnbilledInvoice unbilled,
            BigDecimal tdsAmount
    ) {
        BigDecimal bankAmount = safe2(request.getAmount());

        BigDecimal settlementAmount = bankAmount
                .add(safe2(tdsAmount))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalInvoiceAmount = safe2(unbilled.getTotalAmount());
        BigDecimal totalTaxableAmount = calculateTdsTaxableAmount(estimate, unbilled);

        if (totalInvoiceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal taxableRatio = totalTaxableAmount.divide(
                totalInvoiceAmount,
                10,
                RoundingMode.HALF_UP
        );

        return settlementAmount
                .multiply(taxableRatio)
                .setScale(2, RoundingMode.HALF_UP);
    }


}

