package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateStatus;
import com.account.dto.operationService.OperationCompanyRequestDto;
import com.account.dto.operationService.OperationCompanyResponseDto;
import com.account.dto.operationService.OperationCompanyUnitRequestDto;
import com.account.dto.operationService.OperationContactRequestDto;
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
import java.util.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final ContactRepository contactRepository;
    private final OperationFeignClient operationFeignClient;
    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional
    public PaymentRegistrationResponseDto registerPayment(PaymentRegistrationRequestDto request, Long salespersonUserId) {

        log.info("Registering payment | estimateId: {}, amount: {}, mode: {}, ref: {}, salespersonId: {}",
                request.getEstimateId(), request.getAmount(), request.getPaymentMode(),
                request.getTransactionReference(), salespersonUserId);

        // 1. Basic validation
        if (request.getAmount() == null) {
            throw new ValidationException("Payment amount is required", "ERR_AMOUNT_REQUIRED", "amount");
        }

        BigDecimal reqAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (reqAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Payment amount must be positive", "ERR_AMOUNT_NOT_POSITIVE", "amount");
        }

        // 2. Fetch required entities
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

        // 3. EPR validation for product-related estimates
        boolean isProductRelated = isProductRelatedEstimate(estimate);

        if (isProductRelated) {
            validateEprFields(request);
        } else {
            // Force null for non-product
            request.setEprFinancialYear(null);
            request.setEprPortalRegistrationNumber(null);
            request.setEprCertificateOrInvoiceNumber(null);
        }

        // ────────────────────────────────────────────────
        // Find or create Unbilled Invoice
        // ────────────────────────────────────────────────
//        UnbilledInvoice unbilled = unbilledInvoiceRepository
//                .findTopByEstimateOrderByCreatedAtDesc(estimate)
//                .orElse(null);
        // 4. Find or create Unbilled Invoice
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

        // 5. NEW: Block registration if unbilled is already rejected
        if (unbilled.getStatus() == UnbilledStatus.REJECTED) {
            throw new ValidationException(
                    "Cannot register new payment on rejected Unbilled Invoice. " +
                            "Please contact Accounts team to resolve or re-open.",
                    "ERR_UNBILLED_REJECTED",
                    "unbilledStatus"
            );
        }

        // 6. Prevent changing payment type after first payment
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

        // ────────────────────────────────────────────────
        // Status check
        // ────────────────────────────────────────────────
//        if (unbilled.getStatus() == UnbilledStatus.REJECTED) {
//            throw new ValidationException(
//                    "Cannot register payment for rejected unbilled invoice",
//                    "ERR_UNBILLED_REJECTED", "unbilledStatus");
//        }


        validatePaymentRules(paymentType, reqAmount, unbilled, isFirstPayment);

        // 8. Create and save the payment receipt
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setUnbilledInvoice(unbilled);
        receipt.setPaymentType(paymentType);
        System.out.println("paymentType: "+paymentType.getId());
        receipt.setAmount(reqAmount);
        receipt.setPaymentDate(request.getPaymentDate());
        receipt.setPaymentMode(request.getPaymentMode());
        receipt.setTransactionReference(request.getTransactionReference());
        receipt.setRemarks(request.getRemarks());
        receipt.setReceivedBy(salesperson);

        // EPR fields only if product-related
        receipt.setEprFinancialYear(request.getEprFinancialYear());
        receipt.setEprPortalRegistrationNumber(request.getEprPortalRegistrationNumber());
        receipt.setEprCertificateOrInvoiceNumber(request.getEprCertificateOrInvoiceNumber());

        receipt = paymentReceiptRepository.save(receipt);
        log.info("Created PaymentReceipt ID {} | amount: ₹{}", receipt.getId(), reqAmount);

        // 9. Update unbilled totals and reset status
        unbilled.applyPayment(reqAmount);
        unbilled.setStatus(UnbilledStatus.PENDING_APPROVAL);
        unbilledInvoiceRepository.save(unbilled);

        log.info("Updated Unbilled {} | received: ₹{}, outstanding: ₹{}, status: {}",
                unbilled.getUnbilledNumber(), unbilled.getReceivedAmount(),
                unbilled.getOutstandingAmount(), unbilled.getStatus());

        // 10. Update estimate status
        estimate.setStatus(EstimateStatus.INITIATED);
        estimateRepository.save(estimate);

        // 11. Prepare friendly message
        String message = isFirstPayment
                ? "First payment registered successfully. Unbilled created – awaiting Accounts approval."
                : String.format("Additional payment of ₹%s registered. Total received: ₹%s / ₹%s. Awaiting approval.",
                reqAmount, unbilled.getReceivedAmount(), unbilled.getTotalAmount());

        // 12. Build response
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


    @Override
    @Transactional
    public UnbilledInvoiceApprovalResponseDto updateUnbilledInvoiceStatus(
            Long unbilledId,
            UnbilledInvoiceApprovalRequestDto request) {

        log.info("Approving Unbilled Invoice | unbilledId: {}, approverId: {}",
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

        // 3. Fetch approver
        User approver = userRepository.findById(request.getApproverUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approver not found with ID: " + request.getApproverUserId(),
                        "USER_NOT_FOUND",
                        "User",
                        request.getApproverUserId()
                ));

        // 4. Determine approval eligibility (company/unit must be approved)
        Company company = unbilled.getCompany();
        CompanyUnit unit = unbilled.getUnit();

        boolean companyApproved = company != null && company.getOnboardingStatus() == OnboardingStatus.APPROVED;
        boolean unitApproved = unit == null || unit.getOnboardingStatus() == OnboardingStatus.APPROVED;

        if (!companyApproved) {
            String companyStatus = (company != null) ? company.getOnboardingStatus().toString() : "N/A";
            throw new ApprovalBlockedException(
                    "Company must be APPROVED before unbilled invoice approval. " +
                            "Current status: " + companyStatus,
                    companyApproved,
                    unitApproved
            );
        }

        // 5. Get only payments that have NOT been invoiced yet
        List<PaymentReceipt> paymentsToInvoice = paymentReceiptRepository
                .findUninvoicedPaymentsByUnbilledId(unbilledId);


        // 7. Fetch approver
//        User approver = userRepository.findById(request.getApproverUserId())
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        "Approver not found with ID: " + request.getApproverUserId(),
//                        "USER_NOT_FOUND",
//                        "User",
//                        request.getApproverUserId()
//                ));
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
        if (paymentsToInvoice.isEmpty()) {
            throw new ValidationException(
                    "No new or uninvoiced payments available to approve/invoice.",
                    "NO_NEW_PAYMENTS"
            );
        }

        log.info("Found {} uninvoiced payment(s) to process for Unbilled {}",
                paymentsToInvoice.size(), unbilled.getUnbilledNumber());

        // 6. Generate one invoice per uninvoiced payment
        List<Invoice> generatedInvoices = new ArrayList<>();

        for (PaymentReceipt receipt : paymentsToInvoice) {
            Invoice invoice = invoiceService.generateInvoiceForPayment(
                    unbilled,
                    receipt,
                    approver
            );
            generatedInvoices.add(invoice);

            log.info("Generated Invoice {} for PaymentReceipt {} | amount: ₹{}",
                    invoice.getInvoiceNumber(), receipt.getId(), receipt.getAmount());
        }

        // 7. Update unbilled to APPROVED (after all invoices are created)
        unbilled.setStatus(UnbilledStatus.APPROVED);
        unbilled.setApprovedBy(approver);
        unbilled.setApprovedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
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




        // 12. Build response
        unbilledInvoiceRepository.save(unbilled);

        log.info("Unbilled {} fully approved | {} invoice(s) generated | approver: {}",
                unbilled.getUnbilledNumber(), generatedInvoices.size(), approver.getId());

        // 8. Build response
        UnbilledInvoiceApprovalResponseDto response = new UnbilledInvoiceApprovalResponseDto();

        // Project/Solution name fallback
//        Estimate estimate = unbilled.getEstimate();
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
        response.setCompanyUnitId(unit != null ? unit.getId() : null);
        response.setUnbilledNumber(unbilled.getUnbilledNumber());
        response.setEstimateNumber(estimate != null ? estimate.getEstimateNumber() : null);
        response.setContactId(unbilled.getContact() != null ? unbilled.getContact().getId() : null);
        response.setLeadId(estimate != null ? estimate.getLeadId() : null);
        response.setDate(LocalDate.now());
        response.setTotalAmount(unbilled.getTotalAmount() != null ? unbilled.getTotalAmount().doubleValue() : 0.0);
        response.setPaidAmount(unbilled.getReceivedAmount() != null ? unbilled.getReceivedAmount().doubleValue() : 0.0);

        // Payment type from first payment (or latest if you prefer)
        PaymentReceipt firstOrLatest = paymentsToInvoice.get(0);
        response.setPaymentTypeId(
                firstOrLatest.getPaymentType() != null ? firstOrLatest.getPaymentType().getId() : null
        );

        response.setApprovedById(approver.getId());
        response.setCreatedBy(unbilled.getCreatedBy() != null ? unbilled.getCreatedBy().getId() : null);
        response.setUpdatedBy(approver.getId());


        // Optional: if your DTO has list field, add invoice numbers
        // response.setGeneratedInvoiceNumbers(generatedInvoices.stream().map(Invoice::getInvoiceNumber).toList());

        // External service calls (company creation in operation service) - keep your existing try-catch
        try {
            ResponseEntity<OperationCompanyResponseDto> res =
                    operationFeignClient.getCompanyById(company.getId());

            if (res.getStatusCode().is2xxSuccessful()) {
                log.info("Company already exists in operation service | companyId={}", company.getId());
            }
        } catch (FeignException ex) {
            if (ex.status() == 404) {
                log.info("Company not found in operation service, creating | companyId={}", company.getId());
//                this.operationCompanyCreationMethod(company);
            } else {
                log.error("Operation service error | companyId={} | status={} | message={}",
                        company.getId(), ex.status(), ex.getMessage());
                throw ex; // rollback transaction if fails
            }
        }

        return response;
    }

    private UnbilledInvoiceSummaryDto mapToSummaryDto(UnbilledInvoice unbilled) {
        UnbilledInvoiceSummaryDto dto = new UnbilledInvoiceSummaryDto();

        // Basic unbilled fields
        dto.setId(unbilled.getId());
        dto.setUnbilledNumber(unbilled.getUnbilledNumber());

        // Estimate related
        Estimate estimate = unbilled.getEstimate();
        dto.setEstimateNumber(estimate != null ? estimate.getEstimateNumber() : null);
        dto.setEstimateId(estimate != null ? estimate.getId() : null);
        dto.setSolutionId(estimate != null ? estimate.getSolutionId() : null);
        dto.setSolutionName(estimate != null ? estimate.getSolutionName() : null);

        // Company info
        Company company = unbilled.getCompany();
        dto.setCompanyName(company != null ? company.getName() : null);

        // Contact info (from unbilled → comes from estimate.contact)
        Contact contact = unbilled.getContact();
        dto.setContactName(contact != null ? contact.getName() : null);
        dto.setEmails(contact != null ? contact.getEmails() : null);
        dto.setContactNo(contact != null ? contact.getContactNo() : null);

        // Address & GST fields → come from CompanyUnit (not Company)
        CompanyUnit unit = unbilled.getUnit();
        if (unit != null) {
            dto.setAddressLine1(unit.getAddressLine1());
            dto.setAddressLine2(unit.getAddressLine2());
            dto.setCity(unit.getCity());
            dto.setState(unit.getState());
            dto.setCountry(unit.getCountry() != null ? unit.getCountry() : "India");
            dto.setPinCode(unit.getPinCode());
            dto.setGstNo(unit.getGstNo());
        } else if (company != null) {
            // Fallback: if no unit → try to use company-level address (if you ever add them)
            // Currently Company doesn't have these fields → so most cases will be null
            // You can leave this block empty or add comment
        }

        // Financials
        dto.setTotalAmount(unbilled.getTotalAmount());
        dto.setReceivedAmount(unbilled.getReceivedAmount());
        dto.setOutstandingAmount(unbilled.getOutstandingAmount());

        // Status & audit timestamps
        dto.setStatus(unbilled.getStatus());
        dto.setCreatedAt(unbilled.getCreatedAt());
        dto.setApprovedAt(unbilled.getApprovedAt());

        // Created by (salesperson)
        User createdBy = unbilled.getCreatedBy();
        dto.setCreatedByName(
                createdBy != null
                        ? (createdBy.getFullName() != null ? createdBy.getFullName() : createdBy.getEmail())
                        : null
        );

        // Approved by (accounts person)
        User approvedBy = unbilled.getApprovedBy();
        dto.setApprovedByName(
                approvedBy != null
                        ? (approvedBy.getFullName() != null ? approvedBy.getFullName() : approvedBy.getEmail())
                        : null
        );

        // Fallback/project name
        dto.setName(
                estimate != null && estimate.getSolutionName() != null
                        ? estimate.getSolutionName()
                        : (company != null ? company.getName() + " - Project" : "Unnamed Project")
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


//    private void operationCompanyCreationMethod(Company company){
//
//        OperationCompanyRequestDto operationCompanyRequestDto = this.mapOperationCompanyRequestDto(company);
//
//        operationFeignClient.createCompany(operationCompanyRequestDto, company.getId());
//
//    }
    private OperationCompanyRequestDto mapOperationCompanyRequestDto(Company company) {

        OperationCompanyRequestDto dto = new OperationCompanyRequestDto();

        /* ---------------- Company Basic Info ---------------- */

        dto.setName(company.getName());
        dto.setPanNo(company.getPanNo());
        dto.setEstablishDate(company.getEstablishDate());
        dto.setIndustry(company.getIndustry());
        dto.setIndustries(company.getIndustries());
        dto.setSubIndustry(company.getSubIndustry());
        dto.setSubSubIndustry(company.getSubsubIndustry());
        System.out.println("company.getCreatedBy(): "+company.getCreatedBy());
        if (company.getCreatedBy() != null) {
            dto.setCreatedBy(company.getCreatedBy().getId());
        }

        /* ---------------- Company Units ---------------- */

        if (company.getUnits() != null && !company.getUnits().isEmpty()) {

            for (CompanyUnit unit : company.getUnits()) {

                OperationCompanyUnitRequestDto unitDto = new OperationCompanyUnitRequestDto();

                unitDto.setUnitId(unit.getId());
                unitDto.setUnitName(unit.getUnitName());
                unitDto.setAddress(unit.getAddressLine1());
                unitDto.setCity(unit.getCity());
                unitDto.setState(unit.getState());
                unitDto.setCountry(unit.getCountry());
                unitDto.setPinCode(unit.getPinCode());
                unitDto.setGstNo(unit.getGstNo());
                unitDto.setStatus(unit.getStatus());

                dto.getUnits().add(unitDto);


                /* ---------------- Contacts From Unit ---------------- */

                List<Contact> contacts = contactRepository.findByCompanyUnitIdAndDeleteStatusFalse(unit.getId());

                for (Contact contact : contacts) {

                    OperationContactRequestDto contactDto = new OperationContactRequestDto();

                    contactDto.setContactId(contact.getId());
                    contactDto.setName(contact.getName());
                    contactDto.setTitle(contact.getTitle());
                    contactDto.setDesignation(contact.getDesignation());
                    contactDto.setEmail(contact.getEmails());
                    contactDto.setContactNo(contact.getContactNo());
                    contactDto.setWhatsappNo(contact.getWhatsappNo());

                    contactDto.setCompanyId(company.getId());
                    contactDto.setUnitId(unit.getId());
                    contactDto.setCreatedBy(
                            unit.getCreatedBy() != null ? unit.getCreatedBy().getId() : null
                    );

                    contactDto.setUpdatedBy(
                            unit.getUpdatedBy() != null ? unit.getUpdatedBy().getId() : null
                    );

                    dto.getContacts().add(contactDto);
                }
            }
        }

        return dto;
    }


    /**
     * Rejects an unbilled invoice.
     * Only allowed when status is PENDING_APPROVAL.
     * Saves rejection reason, sets status to REJECTED,
     * records who rejected it (using approvedBy field),
     * and prevents further payments.
     */
    @Override
    @Transactional
    public void rejectUnbilledInvoice(Long unbilledId, String rejectionReason, Long approverUserId) {

        log.info("Rejecting Unbilled Invoice | unbilledId: {}, approverId: {}, reason: {}",
                unbilledId, approverUserId, rejectionReason);

        // 1. Fetch unbilled invoice
        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled invoice not found with ID: " + unbilledId,
                        "UNBILLED_NOT_FOUND",
                        "UnbilledInvoice",
                        unbilledId
                ));

        // 2. Validate current status - only PENDING_APPROVAL can be rejected
        if (unbilled.getStatus() != UnbilledStatus.PENDING_APPROVAL) {
            throw new ValidationException(
                    "Only PENDING_APPROVAL unbilled invoices can be rejected. " +
                            "Current status: " + unbilled.getStatus(),
                    "ERR_INVALID_STATUS_FOR_REJECTION",
                    "status"
            );
        }

        // 3. Validate and trim rejection reason
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new ValidationException(
                    "Rejection reason is required",
                    "ERR_REJECTION_REASON_REQUIRED",
                    "rejectionReason"
            );
        }
        String trimmedReason = rejectionReason.trim();

        // 4. Fetch the user who is rejecting (approver)
        User approver = userRepository.findById(approverUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approver/Rejector not found with ID: " + approverUserId,
                        "USER_NOT_FOUND",
                        "User",
                        approverUserId
                ));

        // 5. Update unbilled to REJECTED
        unbilled.setStatus(UnbilledStatus.REJECTED);
        unbilled.setRejectionReason(trimmedReason);

        // Reuse approvedBy and approvedAt for rejection audit (common pattern)
        unbilled.setApprovedBy(approver);
        unbilled.setApprovedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));

        // Save changes
        unbilledInvoiceRepository.save(unbilled);

        // 6. Log for audit trail
        log.warn("Unbilled {} REJECTED | reason: '{}' | rejected by user: {} ({}) | time: {}",
                unbilled.getUnbilledNumber(),
                trimmedReason,
                approver.getId(),
                approver.getEmail() != null ? approver.getEmail() : "N/A",
                unbilled.getApprovedAt());

        // Optional: Future enhancement - send email to creator/salesperson
        // if (unbilled.getCreatedBy() != null && unbilled.getCreatedBy().getEmail() != null) {
        //     emailService.sendRejectionNotification(
        //         unbilled.getCreatedBy().getEmail(),
        //         unbilled.getUnbilledNumber(),
        //         trimmedReason,
        //         approver.getFullName()
        //     );
        // }
    }


}