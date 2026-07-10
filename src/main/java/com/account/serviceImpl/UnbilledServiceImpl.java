package com.account.serviceImpl;

import com.account.config.LeadFeignClient;
import com.account.domain.*;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateStatus;
import com.account.domain.invoice.Invoice;
import com.account.domain.status.InvoiceStatus;
import com.account.domain.status.UnbilledStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.dto.operationService.*;
import com.account.dto.payment.TdsResponseDto;
import com.account.dto.unbilled.*;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
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

    private final LeadFeignClient leadFeignClient;


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
    @Transactional
    public void requestCancelUnbilled(Long userId, Long unbilledId, String reason, String cancelAttachment) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId,
                        "USER_NOT_FOUND",
                        "User",
                        userId
                ));

        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled not found with ID: " + unbilledId,
                        "UNBILLED_NOT_FOUND",
                        "UnbilledInvoice",
                        unbilledId
                ));

        if (unbilled.isCancelled()) {
            throw new IllegalStateException("Unbilled already cancelled");
        }

        if (unbilled.getStatus() == UnbilledStatus.CANCEL_REQUESTED) {
            throw new IllegalStateException("Cancel request already pending for admin approval");
        }

        String finalReason = reason != null && !reason.trim().isEmpty()
                ? reason.trim()
                : "Unbilled cancellation requested";

        String finalCancelAttachment = cancelAttachment != null && !cancelAttachment.trim().isEmpty()
                ? cancelAttachment.trim()
                : null;

        unbilled.setStatus(UnbilledStatus.CANCEL_REQUESTED);
        unbilled.setRejectionReason(finalReason);
        unbilled.setCancelAttachment(finalCancelAttachment);
        unbilled.setUpdatedBy(user);
        unbilled.setUpdatedAt(LocalDateTime.now());

        unbilledInvoiceRepository.save(unbilled);
    }

    @Override
    @Transactional
    public void rejectCancelUnbilled(Long adminUserId, Long unbilledId, String reason) {

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admin user not found with ID: " + adminUserId,
                        "USER_NOT_FOUND",
                        "User",
                        adminUserId
                ));

        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled not found with ID: " + unbilledId,
                        "UNBILLED_NOT_FOUND",
                        "UnbilledInvoice",
                        unbilledId
                ));

        if (unbilled.getStatus() != UnbilledStatus.CANCEL_REQUESTED) {
            throw new IllegalStateException(
                    "Only CANCEL_REQUESTED unbilled can be rejected. Current status: "
                            + unbilled.getStatus()
            );
        }

        String finalReason = reason != null && !reason.trim().isEmpty()
                ? reason.trim()
                : "Cancellation rejected by admin";

        unbilled.setStatus(UnbilledStatus.CANCEL_REJECTED);
        unbilled.setRejectionReason(finalReason);
        unbilled.setUpdatedBy(admin);
        unbilled.setUpdatedAt(LocalDateTime.now());

        unbilledInvoiceRepository.save(unbilled);
    }

    @Override
    @Transactional
    public void approveCancelUnbilled(Long adminUserId, Long unbilledId) {

        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled not found with ID: " + unbilledId,
                        "UNBILLED_NOT_FOUND",
                        "UnbilledInvoice",
                        unbilledId
                ));

        if (unbilled.getStatus() != UnbilledStatus.CANCEL_REQUESTED) {
            throw new IllegalStateException(
                    "Only CANCEL_REQUESTED unbilled can be approved for cancellation. Current status: "
                            + unbilled.getStatus()
            );
        }

        String reason = unbilled.getRejectionReason();
        String attachment = unbilled.getCancelAttachment();

        // This calls your existing full cancellation logic
        cancelUnbilled(adminUserId, unbilledId, reason, attachment);
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

        UnbilledStatus effectiveStatus =
                status == null || status == UnbilledStatus.ALL ? null : status;

        boolean cancelledFilter = status == UnbilledStatus.CANCELLED;

        Page<UnbilledInvoice> unbilledInvoicePage =
                unbilledInvoiceRepository.findUnbilledInvoices(
                        applicableUserId,
                        effectiveStatus,
                        cancelledFilter,
                        UnbilledStatus.CANCELLED,
                        pageable
                );

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

        if (company != null) {
            dto.setCompanyId(company.getId());
            dto.setCompanyName(company.getName());

            dto.setCompanyStatus(
                    company.getOnboardingStatus() != null
                            ? company.getOnboardingStatus().name()
                            : null
            );
        } else {
            dto.setCompanyId(null);
            dto.setCompanyName(null);
            dto.setCompanyStatus(null);
        }

        Contact contact = unbilled.getContact();
        dto.setContactName(contact != null ? contact.getName() : null);
        dto.setEmails(contact != null ? contact.getEmails() : null);
        dto.setContactNo(contact != null ? contact.getContactNo() : null);

        CompanyUnit unit = unbilled.getUnit();
        if (unit != null) {

            dto.setUnitId(unit.getId());
            dto.setUnitName(unit.getUnitName());
            dto.setUnitStatus(unit.getOnboardingStatus().name());


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
        dto.setCancelAttachment(unbilled.getCancelAttachment());
        dto.setRejectionReason(unbilled.getRejectionReason());

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
                .tdsDate(tds.getTdsDate())
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

//    private void operationProjectCreationMethod(UnbilledInvoice unbilled,
//                                                Estimate estimate,
//                                                UnbilledInvoiceApprovalResponseDto response) {
//
//        try {
//            log.info("Starting operation project creation | unbilled: {}", unbilled.getUnbilledNumber());
//
//
//            OperationProjectRequestDto projectDto = new OperationProjectRequestDto();
//
//            projectDto.setName(response.getName());
//            projectDto.setProjectNo(response.getProjectNo());
//
//            projectDto.setSalesPersonId(response.getSalesPersonId());
//            projectDto.setSalesPersonName(response.getSalesPersonName());
//
//            projectDto.setProductId(response.getProductId());
//            projectDto.setCompanyId(response.getCompanyId());
//
//            projectDto.setUnbilledNumber(response.getUnbilledNumber());
//            projectDto.setEstimateNumber(response.getEstimateNumber());
//
//            projectDto.setContactId(response.getContactId());
//            projectDto.setLeadId(response.getLeadId());
//
//            projectDto.setDate(response.getDate());
//
//            projectDto.setTotalAmount(response.getTotalAmount());
//            projectDto.setPaidAmount(response.getPaidAmount());
//
//            projectDto.setPaymentTypeId(response.getPaymentTypeId());
//
//            projectDto.setApprovedById(response.getApprovedById());
//            projectDto.setCreatedBy(response.getCreatedBy());
//            projectDto.setUpdatedBy(response.getUpdatedBy());
//
//            projectDto.setUnitId(response.getCompanyUnitId());
//
//            operationFeignClient.createProject(projectDto);
//
//            log.info("Project successfully created in operation-service | projectNo={}", projectDto.getProjectNo());
//
//        } catch (Error error) {
//            //  DO NOT break main transaction (invoice approval)
//            log.error("Failed to create project in operation-service | unbilled={} | error={}",
//                    unbilled.getUnbilledNumber(), error.getMessage(), error);
//
//            throw error;
//
//        }
//    }



    private String getUserDisplayName(User user) {
        if (user == null) return null;
        return user.getFullName() != null ? user.getFullName() : user.getEmail();
    }


    @Override
    @Transactional
    public void cancelUnbilled(Long userId, Long unbilledId, String reason, String cancelAttachment) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Approver not found with ID: " + userId,
                        "USER_NOT_FOUND",
                        "User",
                        userId
                ));

        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled not found with ID: " + unbilledId,
                        "UNBILLED_NOT_FOUND",
                        "UnbilledInvoice",
                        unbilledId
                ));

        // ===============================
        // VALIDATION
        // ===============================

        if (unbilled.isCancelled()) {
            throw new IllegalStateException("Unbilled already cancelled");
        }

        String finalReason = reason != null && !reason.trim().isEmpty()
                ? reason.trim()
                : "Unbilled cancelled";

        String finalCancelAttachment = cancelAttachment != null && !cancelAttachment.trim().isEmpty()
                ? cancelAttachment.trim()
                : null;

        // ===============================
        // CANCEL UNBILLED
        // ===============================

        unbilled.setCancelled(true);
        unbilled.setStatus(UnbilledStatus.CANCELLED);
        unbilled.setRejectionReason(finalReason);
        unbilled.setCancelAttachment(finalCancelAttachment);
        unbilled.setUpdatedBy(user);
        unbilled.setUpdatedAt(LocalDateTime.now());

        // ===============================
        // CANCEL ESTIMATE
        // ===============================

        Estimate estimate = unbilled.getEstimate();

        if (estimate != null) {
            estimate.setCancelled(true);
            estimate.setStatus(EstimateStatus.CANCELLED);
            estimate.setRejectionReason(finalReason);
            estimate.setRejectedAt(LocalDateTime.now());
            estimate.setRejectedBy(user);
            estimate.setUpdatedBy(user);
            estimate.setUpdatedAt(LocalDateTime.now());

            estimateRepository.save(estimate);
        }

        // ===============================
        // CANCEL PROPOSAL THROUGH LEAD SERVICE
        // ===============================

        if (estimate != null && estimate.getProposalId() != null) {
            try {
                ResponseEntity<String> proposalCancelResponse =
                        leadFeignClient.forceCancelProposal(
                                userId,
                                estimate.getProposalId(),
                                finalReason
                        );

                log.info(
                        "Proposal force cancelled successfully from Lead Service | proposalId={} | response={}",
                        estimate.getProposalId(),
                        proposalCancelResponse.getBody()
                );

            } catch (FeignException.NotFound ex) {
                log.warn(
                        "Proposal not found in Lead Service while cancelling unbilled | proposalId={}",
                        estimate.getProposalId()
                );

            } catch (FeignException ex) {
                log.error(
                        "Lead Service error while force cancelling proposal | proposalId={} | status={} | message={}",
                        estimate.getProposalId(),
                        ex.status(),
                        ex.getMessage()
                );
                throw ex;
            }
        } else {
            log.info(
                    "Proposal cancellation skipped because proposalId is not mapped with estimate | unbilledId={}",
                    unbilledId
            );
        }

        // ===============================
        // CANCEL INVOICES
        // ===============================

        List<Invoice> invoices = unbilled.getTaxInvoices();

        if (invoices != null && !invoices.isEmpty()) {
            for (Invoice invoice : invoices) {
                invoice.setCancelled(true);
                invoice.setStatus(InvoiceStatus.CANCELLED);

                if (invoice.getLineItems() != null) {
                    invoice.getLineItems().forEach(item -> item.setCancelled(true));
                }
            }

            invoiceRepository.saveAll(invoices);
        }

        // ===============================
        // CANCEL PAYMENTS
        // ===============================

        List<PaymentReceipt> payments = unbilled.getPayments();

        if (payments != null && !payments.isEmpty()) {
            for (PaymentReceipt payment : payments) {
                payment.setCancelled(true);
            }

            paymentReceiptRepository.saveAll(payments);
        }

        // ===============================
        // SAVE UNBILLED
        // ===============================

        unbilledInvoiceRepository.save(unbilled);

        // ===============================
        // CALL OPERATION SERVICE
        // ===============================

        try {
            ResponseEntity<OperationProjectResponseDto> res =
                    operationFeignClient.getProjectByUnbilledNumber(unbilled.getUnbilledNumber());

            if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
                OperationProjectResponseDto project = res.getBody();

                log.info("Project exists → cancelling project | projectId={}", project.getId());

                try {
                    operationFeignClient.cancelProjectByUnbilledNumber(
                            userId,
                            unbilled.getUnbilledNumber()
                    );

                    log.info("Project cancelled in operation service");

                } catch (FeignException cancelEx) {

                    if (cancelEx.status() == 400 || cancelEx.status() == 409) {
                        log.info(
                                "Project already cancelled → skipping | unbilled={}",
                                unbilled.getUnbilledNumber()
                        );
                    } else {
                        throw cancelEx;
                    }
                }
            }

        } catch (FeignException ex) {

            if (ex.status() == 404) {
                log.info("Project not found → nothing to cancel");
            } else {
                log.error(
                        "Operation service error while checking project to cancel | unbilled={} | status={} | message={}",
                        unbilled.getUnbilledNumber(),
                        ex.status(),
                        ex.getMessage()
                );
                throw ex;
            }
        }
    }





}