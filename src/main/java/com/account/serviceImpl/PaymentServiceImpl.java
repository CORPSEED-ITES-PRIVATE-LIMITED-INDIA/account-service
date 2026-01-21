package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateLineItem;
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
import java.time.LocalDateTime;
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
            unbilled.setPublicUuid(UUID.randomUUID().toString());
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

        return PaymentRegistrationResponseDto.builder()
                .paymentReceiptId(receipt.getId())
                .unbilledNumber(unbilled.getUnbilledNumber())
                .unbilledStatus(unbilled.getStatus())
                .invoiceGenerated(false)
                .invoiceNumber(null)
                .invoiceAmount(null)
                .message(message)
                .build();
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
        // NEW: Enforce company + unit approval BEFORE allowing unbilled approval
        // ────────────────────────────────────────────────
        Company company = unbilled.getCompany();
        CompanyUnit unit = unbilled.getUnit();

        boolean companyApproved = company != null && company.isAccountsApproved();
        boolean unitApproved = unit == null || unit.isAccountsApproved(); // if unit is optional/null → ignore

        if (!companyApproved || !unitApproved) {
            StringBuilder msg = new StringBuilder("Cannot approve this unbilled invoice yet. ");

            if (!companyApproved) {
                msg.append("Company '").append(company != null ? company.getName() : "unknown")
                        .append("' is not approved. ");
            }
            if (!unitApproved) {
                msg.append("Unit '").append(unit != null ? unit.getUnitName() : "unknown")
                        .append("' is not approved. ");
            }

            msg.append("Please go to the company/unit details and approve them first.");

            // Optional: throw custom exception with extra metadata for frontend
            throw new ApprovalBlockedException(msg.toString(), companyApproved, unitApproved);
        }

        // If we reach here → approvals are OK → proceed normally
        User approver = userRepository.findById(request.getApproverUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approver not found with ID: " + request.getApproverUserId(),
                        "USER_NOT_FOUND",
                        "User",
                        request.getApproverUserId()
                ));

        unbilled.setStatus(UnbilledStatus.APPROVED);
        unbilled.setApprovedBy(approver);
        unbilled.setApprovedAt(LocalDateTime.now());
        unbilled.setApprovalRemarks(request.getApprovalRemarks());

        PaymentReceipt triggeringReceipt = unbilled.getPayments().stream()
                .min(Comparator.comparing(PaymentReceipt::getCreatedAt))
                .orElseThrow(() -> new IllegalStateException("No payments found for unbilled invoice"));

        Invoice generatedInvoice = invoiceService.generateInvoiceForPayment(unbilled, triggeringReceipt);

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
        return UnbilledInvoiceSummaryDto.builder()
                .id(unbilled.getId())
                .unbilledNumber(unbilled.getUnbilledNumber())
                .estimateNumber(unbilled.getEstimate() != null ? unbilled.getEstimate().getEstimateNumber() : null)
                .estimateId(unbilled.getEstimate() != null ? unbilled.getEstimate().getId() : null)
                .companyName(unbilled.getCompany() != null ? unbilled.getCompany().getName() : null)
                .contactName(unbilled.getContact() != null ? unbilled.getContact().getName() : null)
                .totalAmount(unbilled.getTotalAmount())
                .receivedAmount(unbilled.getReceivedAmount())
                .outstandingAmount(unbilled.getOutstandingAmount())
                .status(unbilled.getStatus())
                .createdAt(unbilled.getCreatedAt())
                .createdByName(unbilled.getCreatedBy() != null
                        ? (unbilled.getCreatedBy().getFullName() != null
                        ? unbilled.getCreatedBy().getFullName()
                        : unbilled.getCreatedBy().getEmail())
                        : null)
                .approvedAt(unbilled.getApprovedAt())
                .approvedByName(unbilled.getApprovedBy() != null
                        ? (unbilled.getApprovedBy().getFullName() != null
                        ? unbilled.getApprovedBy().getFullName()
                        : unbilled.getApprovedBy().getEmail())
                        : null)
                .build();
    }

    private UnbilledInvoiceDetailDto mapToDetailDto(UnbilledInvoice unbilled) {
        Estimate estimate = unbilled.getEstimate();
        String placeOfSupply = estimate.getPlaceOfSupplyStateCode();
        boolean isIntraState = "06".equals(placeOfSupply); // Assuming seller state is "06" (Haryana)

        List<UnbilledInvoiceDetailDto.LineItemDto> lineItemDtos = estimate.getLineItems().stream()
                .map(item -> {
                    BigDecimal gstAmount = item.getGstAmount();
                    BigDecimal halfGst = gstAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    BigDecimal cgstAmount = isIntraState ? halfGst : BigDecimal.ZERO;
                    BigDecimal sgstAmount = isIntraState ? halfGst : BigDecimal.ZERO;
                    BigDecimal igstAmount = isIntraState ? BigDecimal.ZERO : gstAmount;

                    return UnbilledInvoiceDetailDto.LineItemDto.builder()
                            .id(item.getId())
                            .sourceEstimateLineItemId(item.getId())
                            .itemName(item.getItemName())
                            .description(item.getDescription())
                            .hsnSacCode(item.getHsnSacCode())
                            .quantity(item.getQuantity())
                            .unit(item.getUnit())
                            .unitPriceExGst(item.getUnitPriceExGst())
                            .lineTotalExGst(item.getLineTotalExGst())
                            .gstRate(item.getGstRate())
                            .gstAmount(gstAmount)
                            .lineTotalWithGst(item.getLineTotalExGst().add(gstAmount))
                            .cgstAmount(cgstAmount)
                            .sgstAmount(sgstAmount)
                            .igstAmount(igstAmount)
                            .displayOrder(item.getDisplayOrder())
                            .categoryCode(item.getCategoryCode())
                            .feeType(item.getFeeType())
                            .build();
                }).collect(Collectors.toList());

        return UnbilledInvoiceDetailDto.builder()
                .id(unbilled.getId())
                .publicUuid(unbilled.getPublicUuid())
                .unbilledNumber(unbilled.getUnbilledNumber())
                .estimateNumber(estimate.getEstimateNumber())
                .companyName(unbilled.getCompany() != null ? unbilled.getCompany().getName() : null)
                .contactName(unbilled.getContact() != null ? unbilled.getContact().getName() : null)
                .invoiceDate(unbilled.getCreatedAt() != null ? unbilled.getCreatedAt().toLocalDate() : null)
                .currency(estimate.getCurrency())
                .status(unbilled.getStatus())
                .subTotalExGst(estimate.getSubTotalExGst())
                .totalGstAmount(estimate.getTotalGstAmount())
                .cgstAmount(estimate.getCgstAmount())
                .sgstAmount(estimate.getSgstAmount())
                .igstAmount(estimate.getIgstAmount())
                .grandTotal(unbilled.getTotalAmount())
                .receivedAmount(unbilled.getReceivedAmount())
                .outstandingAmount(unbilled.getOutstandingAmount())
                .createdByName(unbilled.getCreatedBy() != null
                        ? (unbilled.getCreatedBy().getFullName() != null
                        ? unbilled.getCreatedBy().getFullName()
                        : unbilled.getCreatedBy().getEmail())
                        : null)
                .createdAt(unbilled.getCreatedAt())
                .updatedAt(unbilled.getUpdatedAt())
                .approvedByName(unbilled.getApprovedBy() != null
                        ? (unbilled.getApprovedBy().getFullName() != null
                        ? unbilled.getApprovedBy().getFullName()
                        : unbilled.getApprovedBy().getEmail())
                        : null)
                .approvedAt(unbilled.getApprovedAt())
                .approvalRemarks(unbilled.getApprovalRemarks())
                .lineItems(lineItemDtos)
                .build();
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
        int year = LocalDateTime.now().getYear();
        return String.format("UNB-%d-%08d", year, count);
    }

}