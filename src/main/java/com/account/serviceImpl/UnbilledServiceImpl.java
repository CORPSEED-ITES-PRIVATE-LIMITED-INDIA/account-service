package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateStatus;
import com.account.dto.operationService.*;
import com.account.dto.unbilled.*;
import com.account.exception.AccessDeniedException;
import com.account.exception.ApprovalBlockedException;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.feignClient.OperationFeignClient;
import com.account.repository.*;
import com.account.service.InvoiceService;
import com.account.service.UnbilledService;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnbilledServiceImpl implements UnbilledService {

    private final PaymentServiceImpl paymentServiceImpl;
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

        if ("REJECTED".equals(request.getApprovalRemarks())) {

            unbilled.setStatus(UnbilledStatus.REJECTED);
            estimate.setStatus(EstimateStatus.REJECTED);

            // ❗ Just discard pending amount
            unbilled.setCurrentReceivedAmount(BigDecimal.ZERO);

            log.info(
                    "Unbilled {} rejected → pending amount discarded, no financial impact",
                    unbilled.getUnbilledNumber()
            );

        } else {

            // ✅ APPROVED FLOW
            unbilled.setStatus(UnbilledStatus.APPROVED);
            estimate.setStatus(EstimateStatus.APPROVED);

            //  Move pending → actual received
            BigDecimal updatedReceived = unbilled.getReceivedAmount()
                    .add(unbilled.getCurrentReceivedAmount());

            unbilled.setReceivedAmount(updatedReceived);

            // Reset pending buffer
            unbilled.setCurrentReceivedAmount(BigDecimal.ZERO);

            // Recalculate outstanding ONLY from approved amount
            unbilled.setOutstandingAmount(
                    unbilled.getTotalAmount()
                            .subtract(updatedReceived)
                            .max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP)
            );

            log.info(
                    "Unbilled {} approved → received={} outstanding={}",
                    unbilled.getUnbilledNumber(),
                    unbilled.getReceivedAmount(),
                    unbilled.getOutstandingAmount()
            );
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
        response.setAdvanceInvoiceNumber(unbilled.getAdvanceInvoiceNumber());
        response.setAdvanceInvoiceFlag(unbilled.isAdvanceInvoiceFlag());
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

//    @Override
//    public List<UnbilledInvoiceSummaryDto> getUnbilledInvoicesList(Long userId, UnbilledStatus status, int page, int size) {
//
//        log.info("Fetching unbilled invoices list (paginated) | userId={}, status={}, page={}, size={}",
//                userId != null ? userId : "all",
//                status != null ? status : "all",
//                page + 1,
//                size);
//
//        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
//
//        Page<UnbilledInvoice> pageResult = unbilledInvoiceRepository.findUnbilledInvoices(userId, status, pageable);
//
//        return pageResult.getContent().stream()
//                .map(this::mapToSummaryDto)
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public long getUnbilledInvoicesCount(Long userId, UnbilledStatus status) {
//        log.info("Counting unbilled invoices | userId={}, status={}",
//                userId != null ? userId : "all",
//                status != null ? status : "all");
//
//        if (userId != null && status != null) {
//            return unbilledInvoiceRepository.countByCreatedByIdOrApprovedByIdAndStatusAndIsCancelledFalse(userId, userId, status);
//        } else if (userId != null) {
//            return unbilledInvoiceRepository.countByCreatedByIdOrApprovedByIdAndIsCancelledFalse(userId, userId);
//        } else if (status != null) {
//            return unbilledInvoiceRepository.countByStatusAndIsCancelledFalse(status);
//        } else {
//            return unbilledInvoiceRepository.count();
//        }
//    }


//    @Override
//    public List<UnbilledInvoiceSummaryDto> searchUnbilledInvoices(
//            String unbilledNumber,
//            String companyName,
//            int page,
//            int size
//    ) {
//        log.info("Searching unbilled invoices | unbilledNumber={}, companyName={}, page={}, size={}",
//                unbilledNumber, companyName, page, size);
//
////        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
//        Pageable pageable = PageRequest.of(page, size);
//
//        Page<UnbilledInvoice> pageResult = unbilledInvoiceRepository.searchUnbilledInvoicesAndIsCancelledFalse(
//                unbilledNumber != null && !unbilledNumber.trim().isEmpty() ? unbilledNumber.trim() : null,
//                companyName != null && !companyName.trim().isEmpty() ? companyName.trim() : null,
//                pageable
//        );
//
//        List<UnbilledInvoiceSummaryDto> dtos = pageResult.getContent().stream()
//                .map(this::mapToSummaryDto)
//                .collect(Collectors.toList());
//
//        long totalCount = unbilledInvoiceRepository.countSearchUnbilledInvoicesAndIsCancelledFalse(
//                unbilledNumber != null && !unbilledNumber.trim().isEmpty() ? unbilledNumber.trim() : null,
//                companyName != null && !companyName.trim().isEmpty() ? companyName.trim() : null
//        );
//
//        for (UnbilledInvoiceSummaryDto dto : dtos) {
//            dto.setSearchCount(totalCount);
//        }
//
//        return dtos;
//    }

//    @Override
//    public long countSearchUnbilledInvoices(String unbilledNumber, String companyName) {
//        log.info("Counting search unbilled invoices | unbilledNumber={}, companyName={}",
//                unbilledNumber, companyName);
//
//        return unbilledInvoiceRepository.countSearchUnbilledInvoicesAndIsCancelledFalse(
//                unbilledNumber != null && !unbilledNumber.trim().isEmpty() ? unbilledNumber.trim() : null,
//                companyName != null && !companyName.trim().isEmpty() ? companyName.trim() : null
//        );
//    }
//
//    @Override
//    @Transactional
//    public void cancelUnbilled(Long userId, Long unbilledId, String reason) {
//
//
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        "Approver not found with ID: " + userId,
//                        "USER_NOT_FOUND",
//                        "User",
//                        userId
//                ));
//
//        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        "Unbilled not found with ID: " + unbilledId,
//                        "UNBILLED_NOT_FOUND",
//                        "UnbilledInvoice",
//                        unbilledId
//                ));
//
//        // ===============================
//        // VALIDATION
//        // ===============================
//
//        if (unbilled.isCancelled()) {
//            throw new IllegalStateException("Unbilled already cancelled");
//        }
//
//        // ===============================
//        // CANCEL UNBILLED
//        // ===============================
//
//        unbilled.setCancelled(true);
//        unbilled.setStatus(UnbilledStatus.CANCELLED);
//        unbilled.setRejectionReason(reason);
//
//        // ===============================
//        // CANCEL ESTIMATE
//        // ===============================
//
//        Estimate estimate = unbilled.getEstimate();
//        if (estimate != null) {
//            estimate.setCancelled(true);
//            estimate.setStatus(EstimateStatus.REJECTED); // or CANCELLED if you add
//            estimateRepository.save(estimate);
//        }
//
//        // ===============================
//        // CANCEL INVOICES
//        // ===============================
//
//        List<Invoice> invoices = unbilled.getTaxInvoices();
//
//        for (Invoice invoice : invoices) {
//            invoice.setCancelled(true);
//            invoice.setStatus(InvoiceStatus.CANCELLED);
//
//            // cancel line items
//            if (invoice.getLineItems() != null) {
//                invoice.getLineItems().forEach(item -> item.setCancelled(true));
//            }
//        }
//
//        invoiceRepository.saveAll(invoices);
//
//        // ===============================
//        // CANCEL PAYMENTS
//        // ===============================
//
//        List<PaymentReceipt> payments = unbilled.getPayments();
//
//        for (PaymentReceipt payment : payments) {
//            payment.setCancelled(true);
//        }
//
//        paymentReceiptRepository.saveAll(payments);
//
//        // ===============================
//        // SAVE UNBILLED
//        // ===============================
//
//        unbilledInvoiceRepository.save(unbilled);
//
//        // ===============================
//        // CALL OPERATION SERVICE
//        // ===============================
//
//        operationFeignClient.cancelProjectByUnbilledNumber(userId, unbilled.getUnbilledNumber());
//        log.info("Project cancelled in operation service");
//
//    }
//
//
//
//    @Override
//    @Transactional
//    public UnbilledInvoiceDetailDto convertIntoADI(Long unbilledId,Long requestingUserId){
//
//
//        log.info("Converting unbilled into advance invoice", unbilledId, requestingUserId);
//
//        if (requestingUserId == null || requestingUserId <= 0) {
//            throw new ValidationException("Invalid requestingUserId", "ERR_INVALID_REQUESTING_USER", "requestingUserId");
//        }
//
//        // Basic security check
//        if (!userRepository.existsById(requestingUserId)) {
//            log.warn("User not found: userId={}", requestingUserId);
//            throw new ResourceNotFoundException("User not found", "USER_NOT_FOUND");
//        }
//
//        if (unbilledId == null || unbilledId <= 0) {
//            throw new ValidationException("Invalid unbilledId", "ERR_INVALID_UNBILLED_ID", "unbilledId");
//        }
//
//        // Fetch the estimate
//        UnbilledInvoice unbilledInvoice = unbilledInvoiceRepository.findById(unbilledId)
//                .orElseThrow(() -> {
//                    log.warn("Unbilled not found: id={}", unbilledId);
//                    return new ResourceNotFoundException("Unbilled not found", "UNBILLED_NOT_FOUND");
//                });
//
//
//        unbilledInvoice.setAdvanceInvoiceFlag(true);
//        unbilledInvoiceRepository.save(unbilledInvoice);
//        return mapToDetailDto(unbilledInvoice);
//    }

//    @Override
//    @Transactional
//    public Page<OperationProjectActivityResponseDto> getExpences(Long userId, Long unbilledId, Pageable pageable) {
//
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        "User not found with ID: " + userId,
//                        "USER_NOT_FOUND",
//                        "User",
//                        userId
//                ));
//
//        // ===============================
//        // FETCH UNBILLED
//        // ===============================
//        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        "Unbilled not found with ID: " + unbilledId,
//                        "UNBILLED_NOT_FOUND",
//                        "UnbilledInvoice",
//                        unbilledId
//                ));
//
//        // ===============================
//        // VALIDATION
//        // ===============================
//        if (unbilled.isCancelled()) {
//            throw new IllegalStateException("Unbilled already cancelled");
//        }
//
//        // ===============================
//        // FETCH PROJECT FROM OPERATION
//        // ===============================
//        OperationProjectResponseDto project;
//
//        try {
//            ResponseEntity<OperationProjectResponseDto> res =
//                    operationFeignClient.getProjectByUnbilledNumber(unbilled.getUnbilledNumber());
//
//            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
//                throw new RuntimeException("Failed to fetch project from operation service");
//            }
//
//            project = res.getBody();
//
//        } catch (FeignException ex) {
//
//
//            log.error("Error fetching project | unbilled={}",
//                    unbilled.getUnbilledNumber()
//            );
//
//            throw new RuntimeException(ex);
//        }
//
//        // ===============================
//        // FETCH EXPENSE ACTIVITIES
//        // ===============================
//        try {
//            ResponseEntity<Page<OperationProjectActivityResponseDto>> activityRes =
//                    operationFeignClient.getActivitiesByType(
//                            project.getId(),
//                            ActivityType.EXPENSE,
//                            pageable
//                    );
//
//            if (!activityRes.getStatusCode().is2xxSuccessful() || activityRes.getBody() == null) {
//                throw new RuntimeException("Failed to fetch expenses from operation service");
//            }
//
//            return activityRes.getBody();
//
//        } catch (FeignException ex) {
//
//
//            log.error("Error fetching expenses..... ");
//
//            throw new RuntimeException(ex);
//        }
//    }
//
//
//    @Override
//    public UnbilledInvoiceDetailDto getUnbilledInvoice(Long unBilledId, Long requestingUserId) {
//        if (unBilledId == null || requestingUserId == null) {
//            throw new IllegalArgumentException("Invoice ID and requesting user ID are required");
//        }
//
//        UnbilledInvoice unbilledInvoice = unbilledInvoiceRepository.findById(unBilledId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        "Unbilled Invoice not found with ID: " + unBilledId,
//                        "INVOICE_NOT_FOUND",
//                        "UnbilledInvoice",
//                        unBilledId
//                ));
//
//        if (unbilledInvoice.getCreatedBy() == null ||
//                !unbilledInvoice.getCreatedBy().getId().equals(requestingUserId)) {
//            throw new AccessDeniedException(
//                    "You are not authorized to view this invoice",
//                    "ACCESS_DENIED_INVOICE"
//            );
//        }
//        return mapToDetailDto(unbilledInvoice);
//    }



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
        dto.setCurrentReceivedAmount(unbilled.getCurrentReceivedAmount());
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

//    @Override
//    @Transactional
//    public void approveExpense(Long userId, Long unbilledId, Long expenseId, String status) {
//
//        // ===============================
//        // VALIDATE USER
//        // ===============================
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        "User not found with ID: " + userId,
//                        "USER_NOT_FOUND",
//                        "User",
//                        userId
//                ));
//
//        // ===============================
//        // FETCH UNBILLED
//        // ===============================
//        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
//                .orElseThrow(() -> new ResourceNotFoundException(
//                        "Unbilled not found with ID: " + unbilledId,
//                        "UNBILLED_NOT_FOUND",
//                        "UnbilledInvoice",
//                        unbilledId
//                ));
//
//        if (unbilled.isCancelled()) {
//            throw new IllegalStateException("Unbilled already cancelled");
//        }
//
//        // ===============================
//        // FETCH PROJECT FROM OPERATION
//        // ===============================
//        OperationProjectResponseDto project;
//
//        try {
//            ResponseEntity<OperationProjectResponseDto> res =
//                    operationFeignClient.getProjectByUnbilledNumber(unbilled.getUnbilledNumber());
//
//            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
//                throw new RuntimeException("Failed to fetch project from operation service");
//            }
//
//            project = res.getBody();
//
//        } catch (FeignException ex) {
//
//
//            log.error("Error fetching project, while approve expenses | unbilled={} ",unbilled.getUnbilledNumber());
//
//            throw new RuntimeException(ex);
//        }
//
//        // ===============================
//        // CALL APPROVE EXPENSE API
//        // ===============================
//        try {
//
//            operationFeignClient.approveExpense(
//                    project.getId(),
//                    user.getId(),
//                    expenseId,
//                    status
//            );
//
//            log.info("Expense approved successfully | projectId={} expenseId={}",
//                    project.getId(),
//                    expenseId
//            );
//
//        } catch (FeignException ex) {
//
//
//            log.error("Error approving expense | projectId={} expenseId={}",
//                    project.getId(),
//                    expenseId
//            );
//
//            throw new RuntimeException(ex);
//        }
//    }
//

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
        dto.setAdvanceInvoiceNumber(unbilled.getAdvanceInvoiceNumber());
        dto.setAdvanceInvoiceFlag(unbilled.isAdvanceInvoiceFlag());
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
        dto.setCurrentReceivedAmount(unbilled.getCurrentReceivedAmount());
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

    private String generateProjectNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        long count = unbilledInvoiceRepository.count() + 1;
        String sequence = String.format("%04d", count);

        return "PRJ-" + datePart + "-" + sequence;
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



    private String getUserDisplayName(User user) {
        if (user == null) return null;
        return user.getFullName() != null ? user.getFullName() : user.getEmail();
    }




}