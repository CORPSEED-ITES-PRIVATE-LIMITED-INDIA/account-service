package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.dto.payment.PaymentRegistrationRequestDto;
import com.account.dto.payment.PaymentRegistrationResponseDto;
import com.account.dto.unbilled.UnbilledInvoiceApprovalRequestDto;
import com.account.dto.unbilled.UnbilledInvoiceApprovalResponseDto;
import com.account.dto.unbilled.UnbilledInvoiceSummaryDto;
import com.account.exception.ResourceNotFoundException;
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
                .orElseThrow(() -> new ResourceNotFoundException("Estimate not found", "ESTIMATE_NOT_FOUND"));

        User salesperson = userRepository.findById(salespersonUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Salesperson not found", "USER_NOT_FOUND"));

        PaymentType paymentType = paymentTypeRepository.findById(request.getPaymentTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment type not found", "PAYMENT_TYPE_NOT_FOUND"));

        boolean isEprRelated = isEprRelatedEstimate(estimate);
        if (isEprRelated) {
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
                .orElseThrow(() -> new ResourceNotFoundException("Unbilled invoice not found", "UNBILLED_NOT_FOUND"));

        if (unbilled.getStatus() != UnbilledStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Only PENDING_APPROVAL unbilled invoices can be approved. Current status: " + unbilled.getStatus());
        }

        User approver = userRepository.findById(request.getApproverUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Approver not found", "USER_NOT_FOUND"));

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

    @Override
    public List<UnbilledInvoiceSummaryDto> getUnbilledInvoicesList(Long userId, UnbilledStatus status, int page, int size) {

        log.info("Fetching unbilled invoices list (paginated) | userId={}, status={}, page={}, size={}",
                userId != null ? userId : "all",
                status != null ? status : "all",
                page + 1,
                size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<UnbilledInvoice> pageResult;

        if (userId != null && status != null) {
            pageResult = unbilledInvoiceRepository.findByCreatedByIdOrApprovedByIdAndStatus(
                    userId, userId, status, pageable);
        } else if (userId != null) {
            pageResult = unbilledInvoiceRepository.findByCreatedByIdOrApprovedById(
                    userId, userId, pageable);
        } else if (status != null) {
            pageResult = unbilledInvoiceRepository.findByStatus(status, pageable);
        } else {
            pageResult = unbilledInvoiceRepository.findAll(pageable);
        }

        return pageResult.getContent().stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());
    }


    private boolean isEprRelatedEstimate(Estimate estimate) {
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
            throw new IllegalArgumentException("EPR Financial Year is required (YYYY-YYYY)");
        }
        if (request.getEprPortalRegistrationNumber() == null || request.getEprPortalRegistrationNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("EPR Portal Registration Number is required");
        }
        if (!request.getEprFinancialYear().matches("\\d{4}-\\d{4}")) {
            throw new IllegalArgumentException("Invalid EPR Financial Year format. Use YYYY-YYYY");
        }
    }

    private String generateUnbilledNumber() {
        long count = unbilledInvoiceRepository.count() + 1;
        int year = LocalDateTime.now().getYear();
        return String.format("UNB-%d-%08d", year, count);
    }
}
