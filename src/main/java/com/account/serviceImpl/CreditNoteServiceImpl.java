package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.creditNote.CreditNote;
import com.account.domain.creditNote.CreditNoteInvoiceDetail;
import com.account.domain.creditNote.CreditNoteStatus;
import com.account.domain.estimate.Estimate;
import com.account.dto.creditNote.*;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.CreditNoteRepository;
import com.account.repository.InvoiceRepository;
import com.account.repository.UnbilledInvoiceRepository;
import com.account.repository.UserRepository;
import com.account.service.CreditNoteService;
import com.account.service.PaymentService;
import com.account.service.UnbilledService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditNoteServiceImpl implements CreditNoteService {

    private final CreditNoteRepository creditNoteRepository;
    private final UnbilledInvoiceRepository unbilledInvoiceRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final UnbilledService unbilledService;

    @Override
    @Transactional
    public CreditNoteResponseDto createRefundCreditNote(CreateCreditNoteRefundRequestDto request) {

        validateCreateRequest(request);

        User createdBy = userRepository.findById(request.getCreatedByUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + request.getCreatedByUserId(),
                        "USER_NOT_FOUND",
                        "User",
                        request.getCreatedByUserId()
                ));

        if (!isSalesPerson(createdBy)) {
            throw new ValidationException(
                    "Only SALES person can create credit note",
                    "ERR_ONLY_SALES_CAN_CREATE_CREDIT_NOTE"
            );
        }

        UnbilledInvoice unbilled = resolveUnbilledForRefund(request);

        if (unbilled.isCancelled() || unbilled.getStatus() == UnbilledStatus.CANCELLED) {
            throw new ValidationException(
                    "Credit note cannot be created because unbilled invoice is already cancelled",
                    "ERR_UNBILLED_ALREADY_CANCELLED"
            );
        }

        if (hasActivePendingCreditNote(unbilled.getId())) {
            throw new ValidationException(
                    "A pending credit note already exists for this unbilled invoice",
                    "ERR_PENDING_CREDIT_NOTE_EXISTS"
            );
        }

        BigDecimal totalAmount = safe(unbilled.getTotalAmount());
        BigDecimal receivedAmount = safe(unbilled.getReceivedAmount());
        BigDecimal currentReceivedAmount = safe(unbilled.getCurrentReceivedAmount());
        BigDecimal outstandingAmount = safe(unbilled.getOutstandingAmount());

        if (receivedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Refund credit note cannot be created because no approved received amount exists",
                    "ERR_NO_APPROVED_PAYMENT_FOR_REFUND"
            );
        }

        BigDecimal refundAmount = request.getRefundAmount() != null
                ? request.getRefundAmount()
                : receivedAmount;

        if (refundAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException(
                    "Refund amount cannot be negative",
                    "ERR_INVALID_REFUND_AMOUNT"
            );
        }

        if (refundAmount.compareTo(receivedAmount) > 0) {
            throw new ValidationException(
                    "Refund amount cannot be greater than approved received amount. Received: "
                            + receivedAmount + ", Refund requested: " + refundAmount,
                    "ERR_REFUND_EXCEEDS_RECEIVED_AMOUNT"
            );
        }

        BigDecimal creditAmount = receivedAmount.subtract(refundAmount);

        Estimate estimate = unbilled.getEstimate();
        Company company = unbilled.getCompany();
        Contact contact = unbilled.getContact();

        CreditNote creditNote = CreditNote.builder()
                .creditNoteNumber(generateCreditNoteNumber())
                .unbilledInvoice(unbilled)
                .estimate(estimate)
                .company(company)
                .contact(contact)
                .unbilledNumber(unbilled.getUnbilledNumber())
                .estimateNumber(estimate != null ? estimate.getEstimateNumber() : null)
                .proposalNumber(null)
                .companyName(company != null ? company.getName() : null)
                .contactName(contact != null ? contact.getName() : null)
                .attachment(request.getAttachment())
                .totalAmount(totalAmount)
                .receivedAmount(receivedAmount)
                .currentReceivedAmount(currentReceivedAmount)
                .outstandingAmount(outstandingAmount)
                .creditAmount(creditAmount)
                .refundAmount(refundAmount)
                .utilizedCreditAmount(BigDecimal.ZERO)
                .remainingCreditAmount(creditAmount)
                .status(CreditNoteStatus.PENDING_ACCOUNT_REVIEW)
                .reason(request.getReason())
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<Invoice> invoices = fetchInvoicesForCreditNote(unbilled, request.getInvoiceIds());

        for (Invoice invoice : invoices) {
            CreditNoteInvoiceDetail detail = CreditNoteInvoiceDetail.builder()
                    .creditNote(creditNote)
                    .invoiceId(invoice.getId())
                    .invoiceNumber(invoice.getInvoiceNumber())
                    .invoiceDate(invoice.getInvoiceDate())
                    .invoiceGrandTotal(invoice.getGrandTotal())
                    .invoiceGstAmount(invoice.getTotalGstAmount())
                    .invoiceCgstAmount(invoice.getCgstAmount())
                    .invoiceSgstAmount(invoice.getSgstAmount())
                    .invoiceIgstAmount(invoice.getIgstAmount())
                    .invoiceStatus(invoice.getStatus() != null ? invoice.getStatus().name() : null)
                    .build();

            creditNote.getInvoiceDetails().add(detail);
        }

        CreditNote saved = creditNoteRepository.save(creditNote);

        log.info(
                "Refund credit note created by Sales | creditNoteId={} | creditNoteNumber={} | unbilled={}",
                saved.getId(),
                saved.getCreditNoteNumber(),
                unbilled.getUnbilledNumber()
        );

        return toResponse(saved);
    }

    @Override
    @Transactional
    public CreditNoteResponseDto approveCreditNoteByAccount(
            Long creditNoteId,
            Long userId,
            ApproveCreditNoteRequestDto request
    ) {

        User accountApprover = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId,
                        "USER_NOT_FOUND",
                        "User",
                        userId
                ));

        if (!isAccountTeam(accountApprover)) {
            throw new ValidationException(
                    "Only ACCOUNT team can approve credit note at account review stage",
                    "ERR_ONLY_ACCOUNT_TEAM_CAN_APPROVE_CREDIT_NOTE"
            );
        }

        CreditNote creditNote = creditNoteRepository.findById(creditNoteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Credit note not found with ID: " + creditNoteId,
                        "CREDIT_NOTE_NOT_FOUND",
                        "CreditNote",
                        creditNoteId
                ));

        if (creditNote.getStatus() != CreditNoteStatus.PENDING_ACCOUNT_REVIEW) {
            throw new ValidationException(
                    "Only PENDING_ACCOUNT_REVIEW credit notes can be approved by account team. Current status: "
                            + creditNote.getStatus(),
                    "ERR_CREDIT_NOTE_NOT_PENDING_ACCOUNT_REVIEW"
            );
        }

        creditNote.setStatus(CreditNoteStatus.PENDING_ADMIN_APPROVAL);
        creditNote.setAccountApprovedBy(accountApprover);
        creditNote.setAccountApprovedAt(LocalDateTime.now());
        creditNote.setAccountApprovalRemarks(
                request != null ? request.getApprovalRemarks() : null
        );
        creditNote.setGstPortalAttachment(
                request != null ? request.getGstPortalAttachment() : null
        );
        creditNote.setUpdatedAt(LocalDateTime.now());

        CreditNote saved = creditNoteRepository.save(creditNote);

        log.info(
                "Credit note reviewed by Account team | creditNoteId={} | creditNoteNumber={} | accountApprovedBy={}",
                saved.getId(),
                saved.getCreditNoteNumber(),
                userId
        );

        return toResponse(saved);
    }

    @Override
    @Transactional
    public CreditNoteResponseDto approveCreditNote(
            Long creditNoteId,
            Long userId,
            ApproveCreditNoteRequestDto request
    ) {

        User adminApprover = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId,
                        "USER_NOT_FOUND",
                        "User",
                        userId
                ));

        if (!isAdmin(adminApprover)) {
            throw new ValidationException(
                    "Only ADMIN user can provide final approval for credit note",
                    "ERR_ONLY_ADMIN_CAN_FINAL_APPROVE_CREDIT_NOTE"
            );
        }

        CreditNote creditNote = creditNoteRepository.findById(creditNoteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Credit note not found with ID: " + creditNoteId,
                        "CREDIT_NOTE_NOT_FOUND",
                        "CreditNote",
                        creditNoteId
                ));

        if (creditNote.getStatus() != CreditNoteStatus.PENDING_ADMIN_APPROVAL) {
            throw new ValidationException(
                    "Only PENDING_ADMIN_APPROVAL credit notes can be finally approved by ADMIN. Current status: "
                            + creditNote.getStatus(),
                    "ERR_CREDIT_NOTE_NOT_PENDING_ADMIN_APPROVAL"
            );
        }

        if (creditNote.getAccountApprovedBy() == null || creditNote.getAccountApprovedAt() == null) {
            throw new ValidationException(
                    "Credit note must be reviewed by account team before final admin approval",
                    "ERR_ACCOUNT_REVIEW_REQUIRED_BEFORE_ADMIN_APPROVAL"
            );
        }

        UnbilledInvoice unbilled = creditNote.getUnbilledInvoice();

        if (unbilled == null) {
            throw new ValidationException(
                    "Credit note is not linked with any unbilled invoice",
                    "ERR_CREDIT_NOTE_UNBILLED_MISSING"
            );
        }

        if (unbilled.isCancelled() || unbilled.getStatus() == UnbilledStatus.CANCELLED) {
            throw new ValidationException(
                    "Credit note cannot be approved because linked unbilled invoice is already cancelled",
                    "ERR_UNBILLED_ALREADY_CANCELLED"
            );
        }

        creditNote.setStatus(CreditNoteStatus.APPROVED);
        creditNote.setApprovedBy(adminApprover);
        creditNote.setApprovedAt(LocalDateTime.now());
        creditNote.setUpdatedAt(LocalDateTime.now());
        creditNote.setApprovalRemarks(
                request != null ? request.getApprovalRemarks() : null
        );

        creditNoteRepository.save(creditNote);

        String cancelReason = "Unbilled cancelled because refund credit note was finally approved by ADMIN. Credit Note: "
                + creditNote.getCreditNoteNumber();

        if (request != null && request.getApprovalRemarks() != null && !request.getApprovalRemarks().isBlank()) {
            cancelReason += " | Remarks: " + request.getApprovalRemarks();
        }

        unbilledService.cancelUnbilled(
                userId,
                unbilled.getId(),
                cancelReason,
                creditNote.getAttachment()
        );

        log.info(
                "Credit note final-approved by ADMIN and unbilled cancellation completed | creditNoteId={} | unbilledId={}",
                creditNoteId,
                unbilled.getId()
        );

        return toResponse(creditNote);
    }

    @Override
    @Transactional
    public CreditNoteResponseDto rejectCreditNote(
            Long creditNoteId,
            Long userId,
            RejectCreditNoteRequestDto request
    ) {

        User rejectedBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId,
                        "USER_NOT_FOUND",
                        "User",
                        userId
                ));

        CreditNote creditNote = creditNoteRepository.findById(creditNoteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Credit note not found with ID: " + creditNoteId,
                        "CREDIT_NOTE_NOT_FOUND",
                        "CreditNote",
                        creditNoteId
                ));

        if (creditNote.getStatus() == CreditNoteStatus.PENDING_ACCOUNT_REVIEW) {

            if (!isAccountTeam(rejectedBy)) {
                throw new ValidationException(
                        "Only ACCOUNT team can reject credit note at account review stage",
                        "ERR_ONLY_ACCOUNT_TEAM_CAN_REJECT_CREDIT_NOTE"
                );
            }

        } else if (creditNote.getStatus() == CreditNoteStatus.PENDING_ADMIN_APPROVAL) {

            if (!isAdmin(rejectedBy)) {
                throw new ValidationException(
                        "Only ADMIN can reject credit note at final approval stage",
                        "ERR_ONLY_ADMIN_CAN_REJECT_CREDIT_NOTE"
                );
            }

        } else {
            throw new ValidationException(
                    "Only pending credit notes can be rejected. Current status: " + creditNote.getStatus(),
                    "ERR_CREDIT_NOTE_NOT_PENDING_FOR_REJECTION"
            );
        }

        String reason = request != null ? request.getRejectionReason() : null;

        if (reason == null || reason.isBlank()) {
            throw new ValidationException(
                    "Rejection reason is required",
                    "ERR_REJECTION_REASON_REQUIRED"
            );
        }

        creditNote.setStatus(CreditNoteStatus.REJECTED);
        creditNote.setRejectedBy(rejectedBy);
        creditNote.setRejectedAt(LocalDateTime.now());
        creditNote.setUpdatedAt(LocalDateTime.now());
        creditNote.setRejectionReason(reason.trim());

        CreditNote saved = creditNoteRepository.save(creditNote);

        log.info(
                "Credit note rejected | creditNoteId={} | creditNoteNumber={} | rejectedBy={}",
                saved.getId(),
                saved.getCreditNoteNumber(),
                userId
        );

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CreditNoteResponseDto> getCreditNotes(
            CreditNoteStatus status,
            Long unbilledId,
            int page,
            int size
    ) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<CreditNote> creditNotes;

        if (status != null && unbilledId != null) {
            creditNotes = creditNoteRepository.findByUnbilledInvoiceIdAndStatus(
                    unbilledId,
                    status,
                    pageable
            );
        } else if (status != null) {
            creditNotes = creditNoteRepository.findByStatus(status, pageable);
        } else if (unbilledId != null) {
            creditNotes = creditNoteRepository.findByUnbilledInvoiceId(unbilledId, pageable);
        } else {
            creditNotes = creditNoteRepository.findAll(pageable);
        }

        return creditNotes.map(this::toResponse);
    }

    private void validateCreateRequest(CreateCreditNoteRefundRequestDto request) {

        if (request == null) {
            throw new ValidationException("Request body is required", "ERR_REQUEST_REQUIRED");
        }

        if (request.getEstimateNumber() == null || request.getEstimateNumber().isBlank()) {
            throw new ValidationException("estimateNumber is required", "ERR_ESTIMATE_NUMBER_REQUIRED");
        }

        if (request.getCreatedByUserId() == null) {
            throw new ValidationException("createdByUserId is required", "ERR_CREATED_BY_REQUIRED");
        }

        if (request.getAttachment() == null || request.getAttachment().isBlank()) {
            throw new ValidationException("Attachment file is required", "ERR_ATTACHMENT_REQUIRED");
        }
    }

    private UnbilledInvoice resolveUnbilledForRefund(CreateCreditNoteRefundRequestDto request) {

        if (request.getEstimateNumber() != null && !request.getEstimateNumber().isBlank()) {
            String estimateNumber = request.getEstimateNumber().trim();

            return unbilledInvoiceRepository.findByEstimateEstimateNumber(estimateNumber)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Unbilled invoice not found for estimate number: " + estimateNumber,
                            "UNBILLED_NOT_FOUND_FOR_ESTIMATE",
                            "UnbilledInvoice",
                            estimateNumber
                    ));
        }

        if (request.getUnbilledId() != null) {
            return unbilledInvoiceRepository.findById(request.getUnbilledId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Unbilled not found with ID: " + request.getUnbilledId(),
                            "UNBILLED_NOT_FOUND",
                            "UnbilledInvoice",
                            request.getUnbilledId()
                    ));
        }

        throw new ValidationException(
                "Either estimateNumber or unbilledId is required",
                "ERR_ESTIMATE_NUMBER_OR_UNBILLED_ID_REQUIRED"
        );
    }

    private List<Invoice> fetchInvoicesForCreditNote(UnbilledInvoice unbilled, List<Long> invoiceIds) {

        List<Invoice> invoices;

        if (invoiceIds != null && !invoiceIds.isEmpty()) {
            invoices = invoiceRepository.findAllById(invoiceIds);

            if (invoices.size() != invoiceIds.size()) {
                throw new ValidationException(
                        "One or more invoice IDs are invalid",
                        "ERR_INVALID_INVOICE_IDS"
                );
            }

            boolean anyInvoiceNotBelongsToUnbilled = invoices.stream()
                    .anyMatch(invoice ->
                            invoice.getUnbilledInvoice() == null
                                    || !invoice.getUnbilledInvoice().getId().equals(unbilled.getId())
                    );

            if (anyInvoiceNotBelongsToUnbilled) {
                throw new ValidationException(
                        "One or more invoices do not belong to selected unbilled invoice",
                        "ERR_INVOICE_UNBILLED_MISMATCH"
                );
            }

            return invoices;
        }

        invoices = unbilled.getTaxInvoices();

        if (invoices == null || invoices.isEmpty()) {
            throw new ValidationException(
                    "No invoices found against this unbilled invoice",
                    "ERR_NO_INVOICES_FOUND"
            );
        }

        return invoices;
    }

    private boolean hasActivePendingCreditNote(Long unbilledId) {

        return creditNoteRepository.existsByUnbilledInvoiceIdAndStatus(
                unbilledId,
                CreditNoteStatus.PENDING_ACCOUNT_REVIEW
        ) || creditNoteRepository.existsByUnbilledInvoiceIdAndStatus(
                unbilledId,
                CreditNoteStatus.PENDING_ADMIN_APPROVAL
        );
    }

    private String generateCreditNoteNumber() {

        String dateTimePart = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        long count = creditNoteRepository.count() + 1;

        String number = String.format("CN-%s-%06d", dateTimePart, count);

        if (creditNoteRepository.existsByCreditNoteNumber(number)) {
            number = String.format("CN-%s-%06d", dateTimePart, count + 1);
        }

        return number;
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private boolean isAdmin(User user) {
        return hasRole(user, "ADMIN")
                || containsIgnoreCase(user.getDesignation(), "ADMIN");
    }

    private boolean isSalesPerson(User user) {
        return hasRole(user, "SALES")
                || hasRole(user, "SALE")
                || containsIgnoreCase(user.getDepartment(), "SALES")
                || containsIgnoreCase(user.getDepartment(), "SALE")
                || containsIgnoreCase(user.getDesignation(), "SALES")
                || containsIgnoreCase(user.getDesignation(), "SALE");
    }

    private boolean isAccountTeam(User user) {
        return hasRole(user, "ACCOUNT")
                || hasRole(user, "ACCOUNTS")
                || containsIgnoreCase(user.getDepartment(), "ACCOUNT")
                || containsIgnoreCase(user.getDepartment(), "ACCOUNTS")
                || containsIgnoreCase(user.getDesignation(), "ACCOUNT")
                || containsIgnoreCase(user.getDesignation(), "ACCOUNTS");
    }

    private boolean hasRole(User user, String roleName) {

        if (user == null || roleName == null) {
            return false;
        }

        boolean hasEntityRole = user.getUserRole() != null
                && user.getUserRole().stream()
                .anyMatch(role ->
                        role != null
                                && !role.isDeleted()
                                && role.getName() != null
                                && roleName.equalsIgnoreCase(role.getName().trim())
                );

        boolean hasStringRole = user.getRole() != null
                && user.getRole().stream()
                .anyMatch(role ->
                        role != null
                                && roleName.equalsIgnoreCase(role.trim())
                );

        return hasEntityRole || hasStringRole;
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null
                && keyword != null
                && value.trim().toUpperCase().contains(keyword.trim().toUpperCase());
    }

    private CreditNoteResponseDto toResponse(CreditNote creditNote) {

        UnbilledInvoice unbilled = creditNote.getUnbilledInvoice();
        Estimate estimate = creditNote.getEstimate();
        Company company = creditNote.getCompany();
        Contact contact = creditNote.getContact();

        List<CreditNoteInvoiceDetailResponseDto> invoiceDtos =
                creditNote.getInvoiceDetails() == null
                        ? List.of()
                        : creditNote.getInvoiceDetails()
                        .stream()
                        .sorted(Comparator.comparing(CreditNoteInvoiceDetail::getId))
                        .map(detail -> CreditNoteInvoiceDetailResponseDto.builder()
                                .id(detail.getId())
                                .invoiceId(detail.getInvoiceId())
                                .invoiceNumber(detail.getInvoiceNumber())
                                .invoiceDate(detail.getInvoiceDate())
                                .invoiceGrandTotal(detail.getInvoiceGrandTotal())
                                .invoiceGstAmount(detail.getInvoiceGstAmount())
                                .invoiceCgstAmount(detail.getInvoiceCgstAmount())
                                .invoiceSgstAmount(detail.getInvoiceSgstAmount())
                                .invoiceIgstAmount(detail.getInvoiceIgstAmount())
                                .invoiceStatus(detail.getInvoiceStatus())
                                .build())
                        .toList();

        return CreditNoteResponseDto.builder()
                .id(creditNote.getId())
                .creditNoteNumber(creditNote.getCreditNoteNumber())

                .proposalId(estimate != null ? estimate.getProposalId() : null)

                .unbilledId(unbilled != null ? unbilled.getId() : null)
                .unbilledNumber(creditNote.getUnbilledNumber())

                .estimateId(estimate != null ? estimate.getId() : null)
                .estimateNumber(creditNote.getEstimateNumber())

                .companyId(company != null ? company.getId() : null)
                .companyName(creditNote.getCompanyName())

                .contactId(contact != null ? contact.getId() : null)
                .contactName(creditNote.getContactName())

                .attachment(creditNote.getAttachment())
                .gstPortalAttachment(creditNote.getGstPortalAttachment())

                .totalAmount(creditNote.getTotalAmount())
                .receivedAmount(creditNote.getReceivedAmount())
                .currentReceivedAmount(creditNote.getCurrentReceivedAmount())
                .outstandingAmount(creditNote.getOutstandingAmount())
                .refundAmount(creditNote.getRefundAmount())
                .creditAmount(creditNote.getCreditAmount())
                .utilizedCreditAmount(creditNote.getUtilizedCreditAmount())
                .remainingCreditAmount(creditNote.getRemainingCreditAmount())


                .status(creditNote.getStatus())
                .reason(creditNote.getReason())

                .createdById(creditNote.getCreatedBy() != null ? creditNote.getCreatedBy().getId() : null)
                .createdAt(creditNote.getCreatedAt())

                .accountApprovedById(
                        creditNote.getAccountApprovedBy() != null
                                ? creditNote.getAccountApprovedBy().getId()
                                : null
                )
                .accountApprovedAt(creditNote.getAccountApprovedAt())
                .accountApprovalRemarks(creditNote.getAccountApprovalRemarks())

                .approvedById(creditNote.getApprovedBy() != null ? creditNote.getApprovedBy().getId() : null)
                .approvedAt(creditNote.getApprovedAt())
                .approvalRemarks(creditNote.getApprovalRemarks())

                .rejectionReason(creditNote.getRejectionReason())
                .rejectedById(creditNote.getRejectedBy() != null ? creditNote.getRejectedBy().getId() : null)
                .rejectedAt(creditNote.getRejectedAt())

                .updatedAt(creditNote.getUpdatedAt())
                .invoices(invoiceDtos)
                .build();
    }
}