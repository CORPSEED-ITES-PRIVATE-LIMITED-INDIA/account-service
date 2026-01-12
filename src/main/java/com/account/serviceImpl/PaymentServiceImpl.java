package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.dto.payment.PaymentRegistrationRequestDto;
import com.account.dto.payment.PaymentRegistrationResponseDto;
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
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;

    @Override
    @Transactional
    public PaymentRegistrationResponseDto registerPayment(PaymentRegistrationRequestDto request, Long salespersonUserId) {

        log.info("Registering payment | estimateId: {}, amount: {}, mode: {}, ref: {}, salespersonId: {}",
                request.getEstimateId(), request.getAmount(), request.getPaymentMode(),
                request.getTransactionReference(), salespersonUserId);

        // 1. Fetch required entities
        Estimate estimate = estimateRepository.findById(request.getEstimateId())
                .orElseThrow(() -> new ResourceNotFoundException("Estimate not found", "ESTIMATE_NOT_FOUND"));

        User salesperson = userRepository.findById(salespersonUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Salesperson user not found", "USER_NOT_FOUND"));

        PaymentType paymentType = paymentTypeRepository.findById(request.getPaymentTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment type not found", "PAYMENT_TYPE_NOT_FOUND"));

        // Optional: Add operational/milestone validation here if needed
        // e.g. if (request.getMilestoneId() != null) { validate milestone completion }

        // 2. Check if UnbilledInvoice already exists for this estimate
        UnbilledInvoice unbilled = unbilledInvoiceRepository.findByEstimate(estimate).orElse(null);

        boolean isFirstPayment = (unbilled == null);

        if (isFirstPayment) {
            // Create new UnbilledInvoice on FIRST payment only
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
            log.info("Created new UnbilledInvoice: {} (PENDING_APPROVAL)", unbilled.getUnbilledNumber());
        }

        // 3. Always create a new PaymentReceipt for every payment
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
        log.info("Created PaymentReceipt ID: {} for amount: {}", receipt.getId(), request.getAmount());

        // 4. Apply payment to UnbilledInvoice (update received & outstanding)
        unbilled.applyPayment(request.getAmount());
        unbilledInvoiceRepository.save(unbilled);
        log.info("Updated UnbilledInvoice: {} | received: {}, outstanding: {}, status: {}",
                unbilled.getUnbilledNumber(), unbilled.getReceivedAmount(),
                unbilled.getOutstandingAmount(), unbilled.getStatus());

        // 5. Generate Tax Invoice ONLY if UnbilledInvoice is APPROVED
        boolean invoiceGenerated = false;
        String invoiceNumber = null;
        BigDecimal invoiceAmount = null;

        if (unbilled.getStatus() == UnbilledStatus.APPROVED) {
            // Only generate when explicitly APPROVED (strict check)
            Invoice generatedInvoice = invoiceService.generateInvoiceForPayment(unbilled, receipt);
            invoiceGenerated = true;
            invoiceNumber = generatedInvoice.getInvoiceNumber();
            invoiceAmount = generatedInvoice.getGrandTotal();
            log.info("Generated tax Invoice: {} | amount: {} (Unbilled APPROVED)", invoiceNumber, invoiceAmount);
        } else if (unbilled.getStatus() == UnbilledStatus.PENDING_APPROVAL) {
            log.info("Unbilled is still PENDING_APPROVAL - no invoice generated yet");
        } else {
            log.info("Unbilled status is {} - invoice already handled in previous payments", unbilled.getStatus());
        }

        // 6. Prepare and return response
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
        // Simple example - in production use proper sequence + financial year
        long count = unbilledInvoiceRepository.count() + 1;
        return String.format("UNB-%d-%08d", LocalDateTime.now().getYear(), count);
    }
}