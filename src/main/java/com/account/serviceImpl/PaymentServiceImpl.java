package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
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
import com.account.repository.*;
import com.account.service.InvoiceService;
import com.account.service.PaymentService;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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

    @Override
    @Transactional
    public PaymentRegistrationResponseDto registerPayment(PaymentRegistrationRequestDto request, Long salespersonUserId) {

        log.info("Registering payment | estimateId: {}, amount: {}, mode: {}, ref: {}, salespersonId: {}",
                request.getEstimateId(), request.getAmount(), request.getPaymentMode(),
                request.getTransactionReference(), salespersonUserId);

        if (request.getAmount() == null) {
            throw new ValidationException("Payment amount is required", "ERR_AMOUNT_REQUIRED", "amount");
        }

        BigDecimal reqAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

//        if (paymentReceiptRepository.existsByTransactionReference(request.getTransactionReference())) {
//            throw new ValidationException("Duplicate transaction reference",
//                    "ERR_DUPLICATE_TXN_REF", "transactionReference");
//        }

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



        boolean productRelatedEstimate = isProductRelatedEstimate(estimate);
        if (productRelatedEstimate) {
            validateEprFields(request);
        }

        UnbilledInvoice unbilled = unbilledInvoiceRepository.findByEstimate(estimate).orElse(null);
        boolean isFirstPayment = (unbilled == null);

        if (isFirstPayment) {
            unbilled = new UnbilledInvoice();
            unbilled.setUnbilledNumber(generateUnbilledNumber());
            unbilled.setPublicUuid(dateTimeUtil.generateUuid());  // ← Using DateTimeUtil
            unbilled.setEstimate(estimate);
            unbilled.setCompany(estimate.getCompany());
            unbilled.setUnit(estimate.getUnit());
            unbilled.setContact(estimate.getContact());

            // Ensure totals are 2 decimals
            BigDecimal total = estimate.getGrandTotal() == null
                    ? BigDecimal.ZERO
                    : estimate.getGrandTotal().setScale(2, RoundingMode.HALF_UP);

            unbilled.setTotalAmount(total);
            unbilled.setReceivedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            unbilled.setOutstandingAmount(total);
            unbilled.setStatus(UnbilledStatus.PENDING_APPROVAL);
            unbilled.setCreatedBy(salesperson);

            unbilled = unbilledInvoiceRepository.save(unbilled);

            log.info("Created UnbilledInvoice {} (PENDING_APPROVAL) for estimate {}",
                    unbilled.getUnbilledNumber(), estimate.getEstimateNumber());
        }

        // Enforce: payment type cannot change after first payment
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


        // Status gating (adjust as per your enums)
        if (unbilled.getStatus() == UnbilledStatus.REJECTED) {
            throw new ValidationException("Cannot register payment for rejected unbilled invoice",
                    "ERR_UNBILLED_REJECTED", "unbilledStatus");
        }

        validatePaymentRules(paymentType, reqAmount, unbilled, isFirstPayment);

        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setUnbilledInvoice(unbilled);
        receipt.setPaymentType(paymentType);
        receipt.setAmount(reqAmount);
        receipt.setPaymentDate(request.getPaymentDate());
        receipt.setPaymentMode(request.getPaymentMode());
        receipt.setTransactionReference(request.getTransactionReference());
        receipt.setRemarks(request.getRemarks());
        receipt.setReceivedBy(salesperson);

        receipt.setEprFinancialYear(request.getEprFinancialYear());
        receipt.setEprPortalRegistrationNumber(request.getEprPortalRegistrationNumber());
        receipt.setEprCertificateOrInvoiceNumber(request.getEprCertificateOrInvoiceNumber());

        receipt = paymentReceiptRepository.save(receipt);
        log.info("Created PaymentReceipt {} | amount: {}", receipt.getId(), request.getAmount());
        unbilled.applyPayment(reqAmount);
        unbilledInvoiceRepository.save(unbilled);
        log.info("Updated unbilled {} | received: {}, outstanding: {}, status: {}",
                unbilled.getUnbilledNumber(), unbilled.getReceivedAmount(),
                unbilled.getOutstandingAmount(), unbilled.getStatus());

        String message = isFirstPayment
                ? "First payment registered. Unbilled created – awaiting Accounts approval"
                : String.format("Additional payment of ₹%s registered. Total received: ₹%s / ₹%s. Awaiting approval.",
                reqAmount, unbilled.getReceivedAmount(), unbilled.getTotalAmount());

        // Manual creation of DTO (no builder)
        PaymentRegistrationResponseDto response = new PaymentRegistrationResponseDto();

        response.setPaymentReceiptId(receipt.getId());
        response.setUnbilledNumber(unbilled.getUnbilledNumber());
        response.setUnbilledStatus(unbilled.getStatus());
        response.setInvoiceGenerated(false);
        response.setInvoiceNumber(null);
        response.setInvoiceAmount(null);
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

        // Amount must be > 0
        if (reqAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be positive", "ERR_AMOUNT_NOT_POSITIVE", "amount");
        }

        // Never allow overpayment
        if (reqAmount.compareTo(outstanding) > 0) {
            throw new ValidationException("Amount is greater than outstanding amount",
                    "ERR_AMOUNT_EXCEEDS_OUTSTANDING", "amount");
        }

        // FULL: must clear outstanding exactly
        if ("FULL".equals(code)) {
            if (reqAmount.compareTo(outstanding) != 0) {
                throw new ValidationException("FULL payment must equal outstanding amount",
                        "ERR_FULL_AMOUNT_MISMATCH", "amount");
            }
            return;
        }

        // PARTIAL: each payment should be 50% of total (or the final remaining outstanding due to rounding)
        if ("PARTIAL".equals(code)) {
            BigDecimal half = total.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);

            // If this is the final payment and outstanding is not exactly half (rounding case),
            // allow paying the remaining outstanding.
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

        // INSTALLMENT / PURCHASE_ORDER: any amount <= outstanding is valid
        if ("INSTALLMENT".equals(code) || "PURCHASE_ORDER".equals(code)) {
            return;
        }

        throw new ValidationException("Unsupported payment type: " + paymentType.getCode(),
                "ERR_UNSUPPORTED_PAYMENT_TYPE", "paymentTypeId");
    }



    private BigDecimal safe2(BigDecimal val) {
        return (val == null ? BigDecimal.ZERO : val).setScale(2, RoundingMode.HALF_UP);
    }





    @Transactional
    public UnbilledInvoiceApprovalResponseDto approveUnbilledInvoice(
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

        // 8. Update unbilled invoice to APPROVED (temporary state)
        unbilled.setStatus(UnbilledStatus.APPROVED);
        unbilled.setApprovedBy(approver);
        unbilled.setApprovedAt(dateTimeUtil.nowLocalDateTime());
        unbilled.setApprovalRemarks(request.getApprovalRemarks());

        // 9. Identify the first (triggering) payment receipt
        PaymentReceipt triggeringReceipt = unbilled.getPayments().stream()
                .min(Comparator.comparing(PaymentReceipt::getCreatedAt))
                .orElseThrow(() -> new IllegalStateException(
                        "No payments found for unbilled invoice: " + unbilled.getUnbilledNumber()));

        // 10. Generate actual GST invoice
        Invoice generatedInvoice = invoiceService.generateInvoiceForPayment(
                unbilled, triggeringReceipt, approver);

        // 11. Determine final unbilled status based on payment
        UnbilledStatus finalStatus = (unbilled.getOutstandingAmount().compareTo(BigDecimal.ZERO) <= 0)
                ? UnbilledStatus.FULLY_PAID
                : UnbilledStatus.PARTIALLY_PAID;

        unbilled.setStatus(finalStatus);
        unbilledInvoiceRepository.save(unbilled);

        log.info("Unbilled {} approved → final status: {}, invoice generated: {}",
                unbilled.getUnbilledNumber(), finalStatus, generatedInvoice.getInvoiceNumber());

        // 12. Build response
        Estimate estimate = unbilled.getEstimate();

        UnbilledInvoiceApprovalResponseDto response = new UnbilledInvoiceApprovalResponseDto();

        // Project / Solution name fallback logic
        response.setName(
                estimate != null ? estimate.getSolutionName() :
                        (company != null ? company.getName() + " - Project" : "Unnamed Project")
        );

        response.setProjectNo("PRJ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
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

        return response;
    }

    private UnbilledInvoiceSummaryDto mapToSummaryDto(UnbilledInvoice unbilled) {
        UnbilledInvoiceSummaryDto dto = new UnbilledInvoiceSummaryDto();

        dto.setId(unbilled.getId());
        dto.setUnbilledNumber(unbilled.getUnbilledNumber());

        // Estimate related fields with null-safety
        Estimate estimate = unbilled.getEstimate();
        dto.setEstimateNumber(estimate != null ? estimate.getEstimateNumber() : null);
        dto.setEstimateId(estimate != null ? estimate.getId() : null);

        dto.setCompanyName(
                unbilled.getCompany() != null
                        ? unbilled.getCompany().getName()
                        : null
        );

        dto.setContactName(
                unbilled.getContact() != null
                        ? unbilled.getContact().getName()
                        : null
        );

        dto.setTotalAmount(unbilled.getTotalAmount());
        dto.setReceivedAmount(unbilled.getReceivedAmount());
        dto.setOutstandingAmount(unbilled.getOutstandingAmount());
        dto.setStatus(unbilled.getStatus());

        dto.setCreatedAt(unbilled.getCreatedAt());

        // Created by name logic
        dto.setCreatedByName(
                unbilled.getCreatedBy() != null
                        ? (unbilled.getCreatedBy().getFullName() != null
                        ? unbilled.getCreatedBy().getFullName()
                        : unbilled.getCreatedBy().getEmail())
                        : null
        );

        dto.setApprovedAt(unbilled.getApprovedAt());

        // Approved by name logic
        dto.setApprovedByName(
                unbilled.getApprovedBy() != null
                        ? (unbilled.getApprovedBy().getFullName() != null
                        ? unbilled.getApprovedBy().getFullName()
                        : unbilled.getApprovedBy().getEmail())
                        : null
        );

        return dto;
    }
    private UnbilledInvoiceDetailDto mapToDetailDto(UnbilledInvoice unbilled) {
        Estimate estimate = unbilled.getEstimate();

        // Determine GST breakup logic (intra-state vs inter-state)
        String placeOfSupply = estimate != null ? estimate.getPlaceOfSupplyStateCode() : null;
        boolean isIntraState = "09".equals(placeOfSupply); // ← Replace "06" with your actual seller state code

        // Map line items with proper GST split
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

        // Create main DTO with setters
        UnbilledInvoiceDetailDto dto = new UnbilledInvoiceDetailDto();

        dto.setId(unbilled.getId());
        dto.setPublicUuid(unbilled.getPublicUuid());
        dto.setUnbilledNumber(unbilled.getUnbilledNumber());
        dto.setEstimateNumber(estimate != null ? estimate.getEstimateNumber() : null);

        // Added missing fields
        dto.setSolutionName(estimate != null ? estimate.getSolutionName() : null);
        dto.setSolutionType(estimate != null && estimate.getSolutionType() != null
                ? estimate.getSolutionType()
                : null);

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

        dto.setCreatedByName(
                unbilled.getCreatedBy() != null
                        ? (unbilled.getCreatedBy().getFullName() != null
                        ? unbilled.getCreatedBy().getFullName()
                        : unbilled.getCreatedBy().getEmail())
                        : null
        );

        dto.setCreatedAt(unbilled.getCreatedAt());
        dto.setUpdatedAt(unbilled.getUpdatedAt());

        dto.setApprovedByName(
                unbilled.getApprovedBy() != null
                        ? (unbilled.getApprovedBy().getFullName() != null
                        ? unbilled.getApprovedBy().getFullName()
                        : unbilled.getApprovedBy().getEmail())
                        : null
        );

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
                        "UnBilled Invoice",
                        unBilledId
                ));

        // Security check: only creator can view (you can extend with roles later)
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

        Page<UnbilledInvoice> pageResult =
                unbilledInvoiceRepository.findUnbilledInvoices(userId, status, pageable);

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
        if (estimate == null) {
            return false;
        }

        String type = estimate.getSolutionType();
        if (type == null || !type.trim().equalsIgnoreCase("SERVICE")) {
            return false;
        }

        String name = estimate.getSolutionName();
        if (name == null || name.isBlank()) {
            return false;
        }

        String upper = name.toUpperCase();
        return upper.contains("");

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
}