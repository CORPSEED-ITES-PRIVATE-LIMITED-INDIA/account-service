package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateStatus;
import com.account.dto.operationService.*;
import com.account.dto.payment.TdsResponseDto;
import com.account.dto.unbilled.*;
import com.account.exception.ApprovalBlockedException;
import com.account.exception.ResourceNotFoundException;
import com.account.feignClient.OperationFeignClient;
import com.account.repository.*;
import com.account.service.InvoiceService;
import com.account.service.UnbilledService;
import com.account.util.DateTimeUtil;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private final TdsRegistrationRepository tdsRegistrationRepository;


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

            //   APPROVED FLOW
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

        System.out.println("response.getContactId(): "+response.getContactId());


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

                //   ONLY CREATE PROJECT (NO PAYMENT CALL HERE)
                if (!"APPROVED".equals(request.getApprovalRemarks())) {
                    log.info("Skipping project creation because status is not APPROVED");
                    return response;
                }

                log.info("Project not found → creating project");

                this.operationProjectCreationMethod(unbilled, estimate, response);

                //   DO NOT ADD PAYMENT HERE
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


    @Override
    @Transactional(readOnly = true)
    public List<UnbilledInvoiceSummaryDto> getUnbilledReport(
            Long userId,
            Long createdByUserId,
            UnbilledStatus status,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate cannot be after toDate");
        }

        Long applicableUserId = hasUnrestrictedUnbilledInvoiceAccess(userId)
                ? null
                : userId;

        LocalDateTime fromDateTime = fromDate != null
                ? fromDate.atStartOfDay()
                : null;

        LocalDateTime toDateTime = toDate != null
                ? toDate.plusDays(1).atStartOfDay()
                : null;

        List<UnbilledInvoice> unbilledInvoices =
                unbilledInvoiceRepository.findUnbilledReport(
                        applicableUserId,
                        createdByUserId,
                        status,
                        fromDateTime,
                        toDateTime
                );

        long totalCount = unbilledInvoiceRepository.countUnbilledReport(
                applicableUserId,
                createdByUserId,
                status,
                fromDateTime,
                toDateTime
        );

        return unbilledInvoices
                .stream()
                .map(unbilled -> {
                    UnbilledInvoiceSummaryDto dto = mapToSummaryDto(unbilled);
                    dto.setSearchCount(totalCount);
                    return dto;
                })
                .collect(Collectors.toList());
    }



    @Override
    @Transactional(readOnly = true)
    public long getUnbilledReportCount(
            Long userId,
            Long createdByUserId,
            UnbilledStatus status,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate cannot be after toDate");
        }

        Long applicableUserId = hasUnrestrictedUnbilledInvoiceAccess(userId)
                ? null
                : userId;

        LocalDateTime fromDateTime = fromDate != null
                ? fromDate.atStartOfDay()
                : null;

        LocalDateTime toDateTime = toDate != null
                ? toDate.plusDays(1).atStartOfDay()
                : null;

        return unbilledInvoiceRepository.countUnbilledReport(
                applicableUserId,
                createdByUserId,
                status,
                fromDateTime,
                toDateTime
        );
    }

    private boolean hasUnrestrictedUnbilledInvoiceAccess(Long userId) {
        if (userId == null) {
            return false;
        }

        User user = userRepository.findById(userId).orElse(null);

        if (user == null || !user.isActive() || user.isDeleted()) {
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
    public List<UnbilledInvoiceSummaryDto> getUnbilledInvoicesList(
            Long userId,
            UnbilledStatus status,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Long applicableUserId = hasUnrestrictedUnbilledInvoiceAccess(userId)
                ? null
                : userId;

        Page<UnbilledInvoice> unbilledInvoicePage =
                unbilledInvoiceRepository.findUnbilledInvoices(applicableUserId, status, pageable);

        return unbilledInvoicePage.getContent()
                .stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());
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

            dto.setUnitId(unit.getId());
            dto.setUnitName(unit.getUnitName());
            dto.setUnitStatus(unit.getStatus());


            dto.setAddressLine1(unit.getAddressLine1());
            dto.setAddressLine2(unit.getAddressLine2());
            dto.setCity(unit.getCity());
            dto.setState(unit.getState());
            dto.setCountry(unit.getCountry() != null ? unit.getCountry() : "India");
            dto.setPinCode(unit.getPinCode());
            dto.setGstNo(unit.getGstNo());
        }

        // ==================== PAYMENT RECEIPT DETAILS ====================
        PaymentReceipt receipt = getLatestActivePaymentReceipt(unbilled);

        if (receipt != null) {
            dto.setPaymentReceiptId(receipt.getId());

            if (receipt.getPaymentType() != null) {
                dto.setPaymentTypeId(receipt.getPaymentType().getId());
                dto.setPaymentTypeCode(receipt.getPaymentType().getCode());
            } else {
                dto.setPaymentTypeId(null);
                dto.setPaymentTypeCode(null);
            }

            dto.setPaymentProof(receipt.getPaymentProof());
            dto.setTransactionReference(receipt.getTransactionReference());
            dto.setPaymentMode(receipt.getPaymentMode());
            dto.setPaymentDate(receipt.getPaymentDate());
            dto.setPaymentAmount(receipt.getAmount());
            dto.setPaymentStatus(receipt.getStatus() != null ? receipt.getStatus().name() : null);

        } else {
            dto.setPaymentReceiptId(null);
            dto.setPaymentTypeId(null);
            dto.setPaymentTypeCode(null);
            dto.setPaymentProof(null);
            dto.setTransactionReference(null);
            dto.setPaymentMode(null);
            dto.setPaymentDate(null);
            dto.setPaymentAmount(null);
            dto.setPaymentStatus(null);
        }
        // ================================================================

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

    private PaymentReceipt getLatestActivePaymentReceipt(UnbilledInvoice unbilled) {
        if (unbilled == null || unbilled.getPayments() == null || unbilled.getPayments().isEmpty()) {
            return null;
        }

        return unbilled.getPayments()
                .stream()
                .filter(payment -> !payment.isCancelled())
                .max(
                        Comparator
                                .comparing(
                                        PaymentReceipt::getCreatedAt,
                                        Comparator.nullsFirst(Comparator.naturalOrder())
                                )
                                .thenComparing(
                                        PaymentReceipt::getId,
                                        Comparator.nullsFirst(Comparator.naturalOrder())
                                )
                )
                .orElse(null);
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


    private String generateProjectNumber() {
        String dateTimePart = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        long count = unbilledInvoiceRepository.count() + 1;
        String sequence = String.format("%04d", count);

        return "PRJ-" + dateTimePart + "-" + sequence;
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
            //  DO NOT break main transaction (invoice approval)
            log.error("Failed to create project in operation-service | unbilled={} | error={}",
                    unbilled.getUnbilledNumber(), error.getMessage(), error);

            throw error;

        }
    }



    private String getUserDisplayName(User user) {
        if (user == null) return null;
        return user.getFullName() != null ? user.getFullName() : user.getEmail();
    }






}