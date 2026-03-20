package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateStatus;
import com.account.dto.operationService.*;
import com.account.dto.payment.PaymentRegistrationRequestDto;
import com.account.dto.payment.PaymentRegistrationResponseDto;
import com.account.dto.unbilled.UnbilledInvoiceApprovalRequestDto;
import com.account.dto.unbilled.UnbilledInvoiceApprovalResponseDto;
import com.account.dto.unbilled.UnbilledInvoiceDetailDto;
import com.account.dto.unbilled.UnbilledInvoiceSummaryDto;
import com.account.exception.AccessDeniedException;
import com.account.exception.ApprovalBlockedException;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.feignClient.OperationFeignClient;
import com.account.repository.*;
import com.account.service.InvoiceService;
import com.account.service.PaymentService;
import com.account.util.DateTimeUtil;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final EstimateRepository estimateRepository;
    private final UnbilledInvoiceRepository unbilledInvoiceRepository;
    private final PaymentReceiptRepository paymentReceiptRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;
    private final DateTimeUtil dateTimeUtil;
    private final ContactRepository contactRepository;
    private final OperationFeignClient operationFeignClient;
    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional
    public PaymentRegistrationResponseDto registerPayment(PaymentRegistrationRequestDto request, Long salespersonUserId) {

        log.info("Registering payment | estimateId: {}, amount: {}, mode: {}, ref: {}, salespersonId: {}",
                request.getEstimateId(), request.getAmount(), request.getPaymentMode(),
                request.getTransactionReference(), salespersonUserId);

        // Basic amount validation
        if (request.getAmount() == null) {
            throw new ValidationException("Payment amount is required", "ERR_AMOUNT_REQUIRED", "amount");
        }

        BigDecimal reqAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (reqAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Payment amount must be positive", "ERR_AMOUNT_NOT_POSITIVE", "amount");
        }

        // Fetch required entities
        Estimate estimate = estimateRepository.findById(request.getEstimateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Estimate not found with ID: " + request.getEstimateId(),
                        "ESTIMATE_NOT_FOUND",
                        "Estimate",
                        request.getEstimateId()
                ));

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

        // Determine if this is product-related (EPR applies)
        boolean isProductRelated = isProductRelatedEstimate(estimate);

        // For product-related estimates → mandatory EPR fields
        if (isProductRelated) {
            validateEprFields(request);
        } else {
            // For services / others → force null (do not save any EPR data)
            request.setEprFinancialYear(null);
            request.setEprPortalRegistrationNumber(null);
            request.setEprCertificateOrInvoiceNumber(null);
        }

        // Find or create Unbilled Invoice
        UnbilledInvoice unbilled = unbilledInvoiceRepository.findByEstimate(estimate).orElse(null);
        boolean isFirstPayment = (unbilled == null);

        if (isFirstPayment) {
            unbilled = new UnbilledInvoice();
            unbilled.setPublicUuid(UUID.randomUUID().toString());
            unbilled.setUnbilledNumber(generateUnbilledNumber());
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

            unbilled = unbilledInvoiceRepository.save(unbilled);

            log.info("Created new UnbilledInvoice {} (PENDING_APPROVAL) for estimate {} with publicUuid {}",
                    unbilled.getUnbilledNumber(), estimate.getEstimateNumber(), unbilled.getPublicUuid());
        }

        // Prevent changing payment type after first payment
        paymentReceiptRepository.findTopByUnbilledInvoiceOrderByIdAsc(unbilled).ifPresent(firstReceipt -> {
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

        // Business rules for amount vs payment type
        validatePaymentRules(paymentType, reqAmount, unbilled, isFirstPayment);

        // Create and save payment receipt
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setUnbilledInvoice(unbilled);
        receipt.setPaymentType(paymentType);
        receipt.setAmount(reqAmount);
        receipt.setPaymentDate(request.getPaymentDate());
        receipt.setPaymentMode(request.getPaymentMode());
        receipt.setTransactionReference(request.getTransactionReference());
        receipt.setRemarks(request.getRemarks());
        receipt.setReceivedBy(salesperson);

        // EPR fields - saved only for product-related estimates (otherwise null)
        receipt.setEprFinancialYear(request.getEprFinancialYear());
        receipt.setEprPortalRegistrationNumber(request.getEprPortalRegistrationNumber());
        receipt.setEprCertificateOrInvoiceNumber(request.getEprCertificateOrInvoiceNumber());

        receipt = paymentReceiptRepository.save(receipt);
        log.info("Created PaymentReceipt {} | amount: {}", receipt.getId(), request.getAmount());

        // Update unbilled totals
        unbilled.applyPayment(reqAmount);
        unbilled.setStatus(UnbilledStatus.PENDING_APPROVAL);
        unbilledInvoiceRepository.save(unbilled);
        log.info("Updated unbilled {} | received: {}, outstanding: {}, status: {}",
                unbilled.getUnbilledNumber(), unbilled.getReceivedAmount(),
                unbilled.getOutstandingAmount(), unbilled.getStatus());

        // Update estimate status
        estimate.setStatus(EstimateStatus.INITIATED);
        estimateRepository.save(estimate);

        // Prepare user-friendly message
        String message = isFirstPayment
                ? "First payment registered. Unbilled created – awaiting Accounts approval"
                : String.format("Additional payment of ₹%s registered. Total received: ₹%s / ₹%s. Awaiting approval.",
                reqAmount, unbilled.getReceivedAmount(), unbilled.getTotalAmount());

        // Build response
        PaymentRegistrationResponseDto response = new PaymentRegistrationResponseDto();
        response.setPaymentReceiptId(receipt.getId());
        response.setUnbilledNumber(unbilled.getUnbilledNumber());
        response.setUnbilledStatus(unbilled.getStatus());
        response.setMessage(message);

        return response;
    }

    private void validatePaymentRules(PaymentType paymentType,
                                      BigDecimal reqAmount,
                                      UnbilledInvoice unbilled,
                                      boolean isFirstPayment) {
        if (paymentType == null || paymentType.getCode() == null) {
            throw new ValidationException("Invalid payment type", "ERR_PAYMENT_TYPE_INVALID", "paymentTypeId");
        }
        BigDecimal outstanding = safe2(unbilled.getOutstandingAmount());
        BigDecimal total = safe2(unbilled.getTotalAmount());
        String code = paymentType.getCode().trim().toUpperCase();
        if (reqAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be positive", "ERR_AMOUNT_NOT_POSITIVE", "amount");
        }
        if (reqAmount.compareTo(outstanding) > 0) {
            throw new ValidationException("Amount is greater than outstanding amount",
                    "ERR_AMOUNT_EXCEEDS_OUTSTANDING", "amount");
        }
        if ("FULL".equals(code)) {
            if (reqAmount.compareTo(outstanding) != 0) {
                throw new ValidationException("FULL payment must equal outstanding amount",
                        "ERR_FULL_AMOUNT_MISMATCH", "amount");
            }
            return;
        }
        if ("PARTIAL".equals(code)) {
            BigDecimal half = total.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal expected = (outstanding.compareTo(half) < 0) ? outstanding : half;
            if (reqAmount.compareTo(expected) != 0) {
                throw new ValidationException(
                        "PARTIAL payment must be " + expected + " (50% of total or remaining outstanding)",
                        "ERR_PARTIAL_AMOUNT_MISMATCH",
                        "amount"
                );
            }
            return;
        }
        if ("INSTALLMENT".equals(code) || "PURCHASE_ORDER".equals(code)) {
            return;
        }
        throw new ValidationException("Unsupported payment type: " + paymentType.getCode(),
                "ERR_UNSUPPORTED_PAYMENT_TYPE", "paymentTypeId");
    }

    private BigDecimal safe2(BigDecimal val) {
        return (val == null ? BigDecimal.ZERO : val).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public void rejectUnbilledInvoice(Long unbilledId, String rejectionReason, Long approverUserId) {

        log.info("Rejecting Unbilled Invoice | unbilledId: {}, approverId: {}, reason: {}",
                unbilledId, approverUserId, rejectionReason);

        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled invoice not found with ID: " + unbilledId,
                        "UNBILLED_NOT_FOUND",
                        "UnbilledInvoice",
                        unbilledId
                ));

        if (unbilled.getStatus() != UnbilledStatus.PENDING_APPROVAL) {
            throw new ValidationException(
                    "Only PENDING_APPROVAL unbilled invoices can be rejected. Current status: " + unbilled.getStatus(),
                    "ERR_INVALID_STATUS_FOR_REJECTION",
                    "status"
            );
        }

        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new ValidationException(
                    "Rejection reason is required",
                    "ERR_REJECTION_REASON_REQUIRED",
                    "rejectionReason"
            );
        }
        String trimmedReason = rejectionReason.trim();

        User approver = userRepository.findById(approverUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approver/Rejector not found with ID: " + approverUserId,
                        "USER_NOT_FOUND",
                        "User",
                        approverUserId
                ));

        unbilled.setStatus(UnbilledStatus.REJECTED);
        unbilled.setRejectionReason(trimmedReason);
        unbilled.setApprovedBy(approver);
        unbilled.setApprovedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        unbilled.setApprovalRemarks(null);

        Estimate estimate = unbilled.getEstimate();
        estimate.setStatus(EstimateStatus.REJECTED);
        estimateRepository.save(estimate);

        List<Invoice> existingInvoices = invoiceRepository.findByUnbilledInvoiceId(unbilled.getId());
        BigDecimal invoicedTotal = existingInvoices.stream()
                .map(Invoice::getGrandTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        unbilled.setReceivedAmount(invoicedTotal);
        unbilled.setOutstandingAmount(
                unbilled.getTotalAmount()
                        .subtract(invoicedTotal)
                        .max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP)
        );

        unbilledInvoiceRepository.save(unbilled);

        log.warn("Unbilled {} REJECTED | reason: '{}' | by user: {}", unbilled.getUnbilledNumber(), trimmedReason, approver.getId());

        // Send notification to salesperson (future enhancement)
        if (unbilled.getCreatedBy() != null && unbilled.getCreatedBy().getEmail() != null) {
            // emailService.sendRejectionNotification(
            //         unbilled.getCreatedBy().getEmail(),
            //         unbilled.getUnbilledNumber(),
            //         trimmedReason,
            //         approver.getFullName() != null ? approver.getFullName() : approver.getEmail()
            // );
            log.info("Notification should be sent to salesperson {} about rejection", unbilled.getCreatedBy().getEmail());
        }
    }

    @Transactional
    public UnbilledInvoiceApprovalResponseDto updateUnbilledInvoiceStatus(
            Long unbilledId,
            UnbilledInvoiceApprovalRequestDto request) {

        log.info("Approving unbilled invoice | unbilledId: {}, approverId: {}",
                unbilledId, request.getApproverUserId());

        // 1. Fetch unbilled invoice
        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled invoice not found with ID: " + unbilledId,
                        "UNBILLED_NOT_FOUND",
                        "UnbilledInvoice",
                        unbilledId
                ));

        // 2. Validate current status
        if (unbilled.getStatus() != UnbilledStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Only PENDING_APPROVAL unbilled invoices can be approved. " +
                            "Current status: " + unbilled.getStatus());
        }

        // 3. Get related entities
        Company company = unbilled.getCompany();
        CompanyUnit unit = unbilled.getUnit();

        // 4. Determine approval eligibility
        boolean companyApproved = company != null && company.getOnboardingStatus() == OnboardingStatus.APPROVED;
        boolean unitApproved = unit == null || unit.getOnboardingStatus() == OnboardingStatus.APPROVED;

        // 5. Block approval if company is not approved
        if (!companyApproved) {
            String companyStatus = (company != null) ? company.getOnboardingStatus().toString() : "N/A";
            throw new ApprovalBlockedException(
                    "Company must be APPROVED before unbilled invoice approval. " +
                            "Current status: " + companyStatus,
                    companyApproved,
                    unitApproved
            );
        }


        // 7. Fetch approver
        User approver = userRepository.findById(request.getApproverUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approver not found with ID: " + request.getApproverUserId(),
                        "USER_NOT_FOUND",
                        "User",
                        request.getApproverUserId()
                ));
        Estimate estimate  = unbilled.getEstimate();
        // 8. Update unbilled invoice to APPROVED (temporary state)
        if(request.getApprovalRemarks().equals("REJECTED")) {

            unbilled.setStatus(UnbilledStatus.REJECTED);

            estimate.setStatus(EstimateStatus.REJECTED);

            // Fetch all invoices for this unbilled
            List<Invoice> invoices = invoiceRepository.findByUnbilledInvoiceId(unbilled.getId());

            BigDecimal approvedTotal = invoices.stream()
                    .map(Invoice::getGrandTotal)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Recalculate received & outstanding amounts
            BigDecimal totalAmount = unbilled.getTotalAmount();

            unbilled.setReceivedAmount(approvedTotal);
            unbilled.setOutstandingAmount(
                    totalAmount.subtract(approvedTotal)
                            .max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP)
            );

            log.info(
                    "Unbilled {} rejected → recalculated amounts | received={} outstanding={}",
                    unbilled.getUnbilledNumber(),
                    unbilled.getReceivedAmount(),
                    unbilled.getOutstandingAmount()
            );

        }else {
            unbilled.setStatus(UnbilledStatus.APPROVED);
            estimate.setStatus(EstimateStatus.APPROVED);
        }
        estimateRepository.save(estimate);
        unbilled.setApprovedBy(approver);
        unbilled.setApprovedAt(dateTimeUtil.nowLocalDateTime());
        unbilled.setApprovalRemarks(request.getApprovalRemarks());


        // 9. Identify the first (triggering) payment receipt
        PaymentReceipt triggeringReceipt = unbilled.getPayments().stream()
                .filter(p -> p.getPaymentDate() != null)
                .min(Comparator.comparing(PaymentReceipt::getPaymentDate))
                .orElseThrow(() -> new IllegalStateException(
                        "No payments found for unbilled invoice: "
                                + unbilled.getUnbilledNumber()));

        // 10. Generate actual GST invoice
        if(request.getApprovalRemarks().equals("APPROVED")) {
            Invoice generatedInvoice = invoiceService.generateInvoiceForPayment(
                    unbilled, triggeringReceipt, approver);

            log.info("Unbilled {} approved → final status: {}, invoice generated: {}",
                    unbilled.getUnbilledNumber(), generatedInvoice.getInvoiceNumber());
        }
        unbilledInvoiceRepository.save(unbilled);

        UnbilledInvoiceApprovalResponseDto response = new UnbilledInvoiceApprovalResponseDto();

        // Project / Solution name fallback logic
        response.setName(
                estimate != null ? estimate.getSolutionName() :
                        (company != null ? company.getName() + " - Project" : "Unnamed Project")
        );

        response.setProjectNo(generateProjectNumber());

        response.setSalesPersonId(unbilled.getCreatedBy() != null ? unbilled.getCreatedBy().getId() : null);
        response.setSalesPersonName(
                unbilled.getCreatedBy() != null
                        ? (unbilled.getCreatedBy().getFullName() != null
                        ? unbilled.getCreatedBy().getFullName()
                        : unbilled.getCreatedBy().getEmail())
                        : null
        );


        response.setProductId(estimate != null ? estimate.getSolutionId() : null);
        response.setCompanyId(company != null ? company.getId() : null);
        response.setCompanyUnitId(unbilled.getUnit().getId());
        response.setUnbilledNumber(unbilled.getUnbilledNumber());
        response.setEstimateNumber(estimate != null ? estimate.getEstimateNumber() : null);
        response.setContactId(unbilled.getContact() != null ? unbilled.getContact().getId() : null);
        response.setLeadId(estimate != null ? estimate.getLeadId() : null);
        response.setDate(LocalDate.now());
        response.setTotalAmount(unbilled.getTotalAmount() != null ? unbilled.getTotalAmount().doubleValue() : 0.0);
        response.setPaidAmount(unbilled.getReceivedAmount() != null ? unbilled.getReceivedAmount().doubleValue() : 0.0);
        response.setPaymentTypeId(
                triggeringReceipt.getPaymentType() != null ? triggeringReceipt.getPaymentType().getId() : null
        );
        response.setApprovedById(approver.getId());
        response.setCreatedBy(unbilled.getCreatedBy() != null ? unbilled.getCreatedBy().getId() : null);
        response.setUpdatedBy(approver.getId());
        response.setCompanyUnitId(unbilled.getUnit() != null ? unbilled.getUnit().getId() : null);


        if ("REJECTED".equals(request.getApprovalRemarks())) {
            log.info("Skipping operation sync because invoice is REJECTED");
            return response;
        }


        try {

            ResponseEntity<OperationProjectResponseDto> res =
                    operationFeignClient.getProjectByUnbilledNumber(unbilled.getUnbilledNumber());

            if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {

                OperationProjectResponseDto project = res.getBody();

                log.info("Project exists → syncing payment | projectId={}", project.getId());

                // ===============================
                // CALCULATE PAYMENT DELTA
                // ===============================

                double accountReceived = unbilled.getReceivedAmount() != null
                        ? unbilled.getReceivedAmount().doubleValue()
                        : 0.0;

                double operationPaid = project.getTotalAmount() - project.getDueAmount();

                double newPayment = accountReceived - operationPaid;

                if (newPayment <= 0) {
                    log.info("No new payment to sync | account={} operation={}",
                            accountReceived, operationPaid);
                    return response;
                }

                // ===============================
                // CREATE PAYMENT DTO
                // ===============================

                OperationProjectPaymentTransactionDto dto = new OperationProjectPaymentTransactionDto();
                dto.setAmount(newPayment);
                dto.setPaymentDate(new Date());
                dto.setCreatedBy(approver.getId());

                // ===============================
                // CALL OPERATION API
                // ===============================

                operationFeignClient.addPaymentTransaction(project.getUnbilledNumber(), dto);

                log.info("Payment synced to operation | amount={}", newPayment);
            }

        } catch (FeignException ex) {

            if (ex.status() == 404) {

                // 🚨 ONLY CREATE PROJECT (NO PAYMENT CALL HERE)
                if (!"APPROVED".equals(request.getApprovalRemarks())) {
                    log.info("Skipping project creation because status is not APPROVED");
                    return response;
                }

                log.info("Project not found → creating project");

                this.operationProjectCreationMethod(unbilled, estimate, response);

                // ❌ DO NOT ADD PAYMENT HERE
                // Payment already handled inside project creation

            } else {

                log.error(
                        "Operation service error while checking project | unbilled={} | status={} | message={}",
                        unbilled.getUnbilledNumber(),
                        ex.status(),
                        ex.getMessage()
                );

                throw ex;
            }
        }



        return response;
    }


    private String getUserDisplayName(User user) {
        if (user == null) return null;
        return user.getFullName() != null ? user.getFullName() : user.getEmail();
    }

    private UnbilledInvoiceSummaryDto mapToSummaryDto(UnbilledInvoice unbilled) {
        UnbilledInvoiceSummaryDto dto = new UnbilledInvoiceSummaryDto();

        dto.setId(unbilled.getId());
        dto.setUnbilledNumber(unbilled.getUnbilledNumber());

        Estimate estimate = unbilled.getEstimate();
        dto.setEstimateNumber(estimate != null ? estimate.getEstimateNumber() : null);
        dto.setEstimateId(estimate != null ? estimate.getId() : null);
        dto.setSolutionId(estimate != null ? estimate.getSolutionId() : null);
        dto.setSolutionName(estimate != null ? estimate.getSolutionName() : null);

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

        dto.setTotalAmount(unbilled.getTotalAmount());
        dto.setReceivedAmount(unbilled.getReceivedAmount());
        dto.setOutstandingAmount(unbilled.getOutstandingAmount());

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

        return dto;
    }

    private UnbilledInvoiceDetailDto mapToDetailDto(UnbilledInvoice unbilled) {
        Estimate estimate = unbilled.getEstimate();

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
                    lineDto.setLineTotalWithGst(item.getLineTotalExGst().add(gstAmount));
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
        dto.setEstimateNumber(estimate != null ? estimate.getEstimateNumber() : null);
        dto.setSolutionName(estimate != null ? estimate.getSolutionName() : null);
        dto.setSolutionType(estimate != null ? estimate.getSolutionType() : null);
        dto.setCompanyName(unbilled.getCompany() != null ? unbilled.getCompany().getName() : null);
        dto.setContactName(unbilled.getContact() != null ? unbilled.getContact().getName() : null);
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
        dto.setOutstandingAmount(unbilled.getOutstandingAmount());
        dto.setCreatedByName(getUserDisplayName(unbilled.getCreatedBy()));
        dto.setCreatedAt(unbilled.getCreatedAt());
        dto.setUpdatedAt(unbilled.getUpdatedAt());
        dto.setApprovedByName(getUserDisplayName(unbilled.getApprovedBy()));
        dto.setApprovedAt(unbilled.getApprovedAt());
        dto.setApprovalRemarks(unbilled.getApprovalRemarks());
        dto.setLineItems(lineItemDtos);

        return dto;
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

    @Override
    public List<UnbilledInvoiceSummaryDto> getUnbilledInvoicesList(Long userId, UnbilledStatus status, int page, int size) {

        log.info("Fetching unbilled invoices list (paginated) | userId={}, status={}, page={}, size={}",
                userId != null ? userId : "all",
                status != null ? status : "all",
                page + 1,
                size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<UnbilledInvoice> pageResult = unbilledInvoiceRepository.findUnbilledInvoices(userId, status, pageable);

        return pageResult.getContent().stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    public long getUnbilledInvoicesCount(Long userId, UnbilledStatus status) {
        log.info("Counting unbilled invoices | userId={}, status={}",
                userId != null ? userId : "all",
                status != null ? status : "all");

        if (userId != null && status != null) {
            return unbilledInvoiceRepository.countByCreatedByIdOrApprovedByIdAndStatus(userId, userId, status);
        } else if (userId != null) {
            return unbilledInvoiceRepository.countByCreatedByIdOrApprovedById(userId, userId);
        } else if (status != null) {
            return unbilledInvoiceRepository.countByStatus(status);
        } else {
            return unbilledInvoiceRepository.count();
        }
    }

    @Override
    public List<UnbilledInvoiceSummaryDto> searchUnbilledInvoices(
            String unbilledNumber,
            String companyName,
            int page,
            int size
    ) {
        log.info("Searching unbilled invoices | unbilledNumber={}, companyName={}, page={}, size={}",
                unbilledNumber, companyName, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<UnbilledInvoice> pageResult = unbilledInvoiceRepository.searchUnbilledInvoices(
                unbilledNumber != null && !unbilledNumber.trim().isEmpty() ? unbilledNumber.trim() : null,
                companyName != null && !companyName.trim().isEmpty() ? companyName.trim() : null,
                pageable
        );

        return pageResult.getContent().stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    public long countSearchUnbilledInvoices(String unbilledNumber, String companyName) {
        log.info("Counting search unbilled invoices | unbilledNumber={}, companyName={}",
                unbilledNumber, companyName);

        return unbilledInvoiceRepository.countSearchUnbilledInvoices(
                unbilledNumber != null && !unbilledNumber.trim().isEmpty() ? unbilledNumber.trim() : null,
                companyName != null && !companyName.trim().isEmpty() ? companyName.trim() : null
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



    private void operationProjectCreationMethod(UnbilledInvoice unbilled,
                                                Estimate estimate,
                                                UnbilledInvoiceApprovalResponseDto response) {

        try {
            log.info("Starting operation project creation | unbilled: {}", unbilled.getUnbilledNumber());


            OperationProjectRequestDto projectDto = new OperationProjectRequestDto();

            projectDto.setName(response.getName());
            projectDto.setProjectNo(response.getProjectNo());

            projectDto.setSalesPersonId(response.getSalesPersonId());
            projectDto.setSalesPersonName(response.getSalesPersonName());

            projectDto.setProductId(response.getProductId());
            projectDto.setCompanyId(response.getCompanyId());

            projectDto.setUnbilledNumber(response.getUnbilledNumber());
            projectDto.setEstimateNumber(response.getEstimateNumber());

            projectDto.setContactId(response.getContactId());
            projectDto.setLeadId(response.getLeadId());

            projectDto.setDate(response.getDate());

            projectDto.setTotalAmount(response.getTotalAmount());
            projectDto.setPaidAmount(response.getPaidAmount());

            projectDto.setPaymentTypeId(response.getPaymentTypeId());

            projectDto.setApprovedById(response.getApprovedById());
            projectDto.setCreatedBy(response.getCreatedBy());
            projectDto.setUpdatedBy(response.getUpdatedBy());

            projectDto.setUnitId(response.getCompanyUnitId());

            operationFeignClient.createProject(projectDto);

            log.info("Project successfully created in operation-service | projectNo={}", projectDto.getProjectNo());

        } catch (Error error) {
            // ❗ DO NOT break main transaction (invoice approval)
            log.error("Failed to create project in operation-service | unbilled={} | error={}",
                    unbilled.getUnbilledNumber(), error.getMessage(), error);

            throw error;

            // Optional: push to retry queue / event / dead-letter
        }
    }

    private String generateProjectNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        long count = unbilledInvoiceRepository.count() + 1;
        String sequence = String.format("%04d", count);

        return "PRJ-" + datePart + "-" + sequence;
    }


}