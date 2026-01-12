package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.dto.payment.*;
import com.account.exception.ResourceNotFoundException;
import com.account.repository.*;
import com.account.service.InvoiceService;
import com.account.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final EstimateRepository estimateRepository;
    private final UnbilledInvoiceRepository unbilledInvoiceRepository;
    private final PaymentReceiptRepository paymentReceiptRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService; // Assume you have this for invoice creation

    @Override
    @Transactional
    public PaymentRegistrationResponseDto registerPayment(PaymentRegistrationRequestDto request, Long salespersonUserId) {

        log.info("Registering payment for estimateId: {}, amount: {}, mode: {}, ref: {}",
                request.getEstimateId(), request.getAmount(), request.getPaymentMode(), request.getTransactionReference());

        // 1. Fetch required entities
        Estimate estimate = estimateRepository.findById(request.getEstimateId())
                .orElseThrow(() -> new ResourceNotFoundException("Estimate not found", "ESTIMATE_NOT_FOUND"));

        User salesperson = userRepository.findById(salespersonUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Salesperson user not found", "USER_NOT_FOUND"));

        PaymentType paymentType = paymentTypeRepository.findById(request.getPaymentTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment type not found", "PAYMENT_TYPE_NOT_FOUND"));

        // Optional: Validate milestone if provided
        // if (request.getMilestoneId() != null) { ... }

        // Optional: Add operational gate check here (e.g. milestone completed?)

        // 2. Check if UnbilledInvoice already exists for this estimate
        UnbilledInvoice unbilled = unbilledInvoiceRepository.findByEstimate(estimate).orElse(null);

        boolean isFirstPayment = (unbilled == null);

        if (isFirstPayment) {
            // Create new UnbilledInvoice
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
            log.info("Created new UnbilledInvoice: {}", unbilled.getUnbilledNumber());
        }

        // 3. Create PaymentReceipt
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setUnbilledInvoice(unbilled);
        receipt.setPaymentType(paymentType);
        receipt.setAmount(request.getAmount());
        receipt.setPaymentDate(request.getPaymentDate());
        receipt.setPaymentMode(request.getPaymentMode());
        receipt.setTransactionReference(request.getTransactionReference());
        receipt.setRemarks(request.getRemarks());
        receipt.setReceivedBy(salesperson);
        // receipt.setMilestone(...) if milestoneId provided
        receipt = paymentReceiptRepository.save(receipt);
        log.info("Created PaymentReceipt ID: {}", receipt.getId());

        // 4. Apply payment to UnbilledInvoice
        unbilled.applyPayment(request.getAmount());
        unbilledInvoiceRepository.save(unbilled);

        // 5. Generate Tax Invoice ONLY if Unbilled is APPROVED
        boolean invoiceGenerated = false;
        String invoiceNumber = null;
        BigDecimal invoiceAmount = null;

        if (unbilled.getStatus() == UnbilledStatus.APPROVED ||
                unbilled.getStatus() == UnbilledStatus.PARTIALLY_PAID ||
                unbilled.getStatus() == UnbilledStatus.FULLY_PAID) {

            Invoice generatedInvoice = invoiceService.generateInvoiceForPayment(unbilled, receipt);
            invoiceGenerated = true;
            invoiceNumber = generatedInvoice.getInvoiceNumber();
            invoiceAmount = generatedInvoice.getGrandTotal();
            log.info("Generated Invoice: {} for amount: {}", invoiceNumber, invoiceAmount);
        }

        // 6. Prepare response
        return PaymentRegistrationResponseDto.builder()
                .paymentReceiptId(receipt.getId())
                .unbilledNumber(unbilled.getUnbilledNumber())
                .unbilledStatus(unbilled.getStatus())
                .invoiceGenerated(invoiceGenerated)
                .invoiceNumber(invoiceNumber)
                .invoiceAmount(invoiceAmount)
                .message(isFirstPayment
                        ? "First payment registered. Unbilled created - waiting for Accounts approval"
                        : "Payment registered successfully" + (invoiceGenerated ? " and tax invoice generated" : ""))
                .build();
    }

    private String generateUnbilledNumber() {
        // Simple example - use proper sequence + FY in production
        long count = unbilledInvoiceRepository.count() + 1;
        return String.format("UNB-%d-%08d", LocalDateTime.now().getYear(), count);
    }
}