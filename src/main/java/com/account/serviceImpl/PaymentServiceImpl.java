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
            unbilled.setTotalAmount(estimate.getGrandTotal());
            unbilled.setReceivedAmount(BigDecimal.ZERO);
            unbilled.setOutstandingAmount(estimate.getGrandTotal());
            unbilled.setStatus(UnbilledStatus.PENDING_APPROVAL);
            unbilled.setCreatedBy(salesperson);
            unbilled = unbilledInvoiceRepository.save(unbilled);

            log.info("Created UnbilledInvoice {} (PENDING_APPROVAL) for estimate {}",
                    unbilled.getUnbilledNumber(), estimate.getEstimateNumber());
        }

        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setUnbilledInvoice(unbilled);
        receipt.setPaymentType(paymentType);
        receipt.setAmount(request.getAmount());
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

        unbilled.applyPayment(request.getAmount());
        unbilledInvoiceRepository.save(unbilled);
        log.info("Updated unbilled {} | received: {}, outstanding: {}, status: {}",
                unbilled.getUnbilledNumber(), unbilled.getReceivedAmount(),
                unbilled.getOutstandingAmount(), unbilled.getStatus());

        String message = isFirstPayment
                ? "First payment registered. Unbilled created – awaiting Accounts approval"
                : String.format("Additional payment of ₹%s registered. Total received: ₹%s / ₹%s. Awaiting approval.",
                request.getAmount(), unbilled.getReceivedAmount(), unbilled.getTotalAmount());

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
    @Transactional
    public UnbilledInvoiceApprovalResponseDto approveUnbilledInvoice(
            Long unbilledId,
            UnbilledInvoiceApprovalRequestDto request) {

        log.info("Approving unbilled invoice | unbilledId: {}, approverId: {}", unbilledId, request.getApproverUserId());

        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled invoice not found with ID: " + unbilledId,
                        "UNBILLED_NOT_FOUND",
                        "UnbilledInvoice",
                        unbilledId
                ));

        if (unbilled.getStatus() != UnbilledStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Only PENDING_APPROVAL unbilled invoices can be approved. Current status: " + unbilled.getStatus());
        }

        // ────────────────────────────────────────────────
        // Enforce company + unit approval BEFORE allowing unbilled approval
        // ────────────────────────────────────────────────
        Company company = unbilled.getCompany();
        CompanyUnit unit = unbilled.getUnit();

//        boolean companyApproved = company != null && company.isAccountsApproved();
        boolean unitApproved = unit == null || unit.isAccountsApproved(); // unit optional

//        if (!companyApproved || !unitApproved) {
//            StringBuilder msg = new StringBuilder("Cannot approve this unbilled invoice yet. ");
//
//            if (!companyApproved) {
//                msg.append("Company '").append(company != null ? company.getName() : "unknown")
//                        .append("' is not approved. ");
//            }
//            if (!unitApproved) {
//                msg.append("Unit '").append(unit != null ? unit.getUnitName() : "unknown")
//                        .append("' is not approved. ");
//            }
//
//            msg.append("Please go to the company/unit details and approve them first.");
//
//            throw new ApprovalBlockedException(msg.toString(), companyApproved, unitApproved);
//        }

        User approver = userRepository.findById(request.getApproverUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approver not found with ID: " + request.getApproverUserId(),
                        "USER_NOT_FOUND",
                        "User",
                        request.getApproverUserId()
                ));

        unbilled.setStatus(UnbilledStatus.APPROVED);
        unbilled.setApprovedBy(approver);
        unbilled.setApprovedAt(dateTimeUtil.nowLocalDateTime());
        unbilled.setApprovalRemarks(request.getApprovalRemarks());

        PaymentReceipt triggeringReceipt = unbilled.getPayments().stream()
                .min(Comparator.comparing(PaymentReceipt::getCreatedAt))
                .orElseThrow(() -> new IllegalStateException("No payments found for unbilled invoice"));

        Invoice generatedInvoice = invoiceService.generateInvoiceForPayment(unbilled, triggeringReceipt,approver);

        UnbilledStatus finalStatus = (unbilled.getOutstandingAmount().compareTo(BigDecimal.ZERO) <= 0)
                ? UnbilledStatus.FULLY_PAID
                : UnbilledStatus.PARTIALLY_PAID;

        unbilled.setStatus(finalStatus);
        unbilledInvoiceRepository.save(unbilled);

        log.info("Unbilled {} approved → final status: {}, invoice: {}",
                unbilled.getUnbilledNumber(), finalStatus, generatedInvoice.getInvoiceNumber());

        return UnbilledInvoiceApprovalResponseDto.builder()
                .unbilledNumber(unbilled.getUnbilledNumber())
                .status(finalStatus)
                .invoiceNumber(generatedInvoice.getInvoiceNumber())
                .invoiceId(generatedInvoice.getId())
                .approvedByName(approver.getFullName() != null ? approver.getFullName() : approver.getEmail())
                .approvedAt(unbilled.getApprovedAt())
                .approvalRemarks(unbilled.getApprovalRemarks())
                .message("Unbilled invoice approved and tax invoice generated. " +
                        (finalStatus == UnbilledStatus.FULLY_PAID ? "Fully paid." : "Partially paid – awaiting remaining amount."))
                .build();
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
                ? estimate.getSolutionType().name()
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
        if (estimate.getSolutionType() != SolutionType.SERVICE) return false;
        String name = estimate.getSolutionName();
        if (name == null) return false;
        String upper = name.toUpperCase();
        return upper.contains("EPR") || upper.contains("PLASTIC WASTE") ||
                upper.contains("EXTENDED PRODUCER") || upper.contains("PLASTIC EPR") ||
                upper.contains("CPCB EPR");
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