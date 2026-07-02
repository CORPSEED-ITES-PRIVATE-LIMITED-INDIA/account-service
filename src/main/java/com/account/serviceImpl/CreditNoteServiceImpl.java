    package com.account.serviceImpl;
    
    import com.account.domain.*;
    import com.account.domain.company.Company;
    import com.account.domain.company.CompanyUnit;
    import com.account.domain.creditNote.CreditNote;
    import com.account.domain.creditNote.CreditNoteInvoiceDetail;
    import com.account.domain.creditNote.CreditNoteStatus;
    import com.account.domain.estimate.Estimate;
    import com.account.domain.invoice.Invoice;
    import com.account.domain.ledger.*;
    import com.account.domain.status.UnbilledStatus;
    import com.account.domain.unbilled.UnbilledInvoice;
    import com.account.dto.creditNote.*;
    import com.account.dto.ledger.AccountingVoucherEntryRequestDto;
    import com.account.dto.ledger.AccountingVoucherRequestDto;
    import com.account.exception.ResourceNotFoundException;
    import com.account.exception.ValidationException;
    import com.account.repository.CreditNoteRepository;
    import com.account.repository.InvoiceRepository;
    import com.account.repository.UnbilledInvoiceRepository;
    import com.account.repository.UserRepository;
    import com.account.repository.ledger.LedgerGroupRepository;
    import com.account.repository.ledger.LedgerMasterRepository;
    import com.account.service.CreditNoteService;
    import com.account.service.UnbilledService;
    import com.account.service.ledger.AccountingVoucherService;
    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.data.domain.Page;
    import org.springframework.data.domain.PageRequest;
    import org.springframework.data.domain.Pageable;
    import org.springframework.data.domain.Sort;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;
    
    import java.math.BigDecimal;
    import java.math.RoundingMode;
    import java.time.LocalDate;
    import java.time.LocalDateTime;
    import java.time.format.DateTimeFormatter;
    import java.util.ArrayList;
    import java.util.Comparator;
    import java.util.List;
    import java.util.Optional;
    
    @Service
    @RequiredArgsConstructor
    @Slf4j
    public class CreditNoteServiceImpl implements CreditNoteService {
    
        private final CreditNoteRepository creditNoteRepository;
        private final UnbilledInvoiceRepository unbilledInvoiceRepository;
        private final InvoiceRepository invoiceRepository;
        private final UserRepository userRepository;
        private final UnbilledService unbilledService;
    
        private final AccountingVoucherService accountingVoucherService;
        private final LedgerMasterRepository ledgerMasterRepository;
        private final LedgerGroupRepository ledgerGroupRepository;
    
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
    
            CreditNote savedCreditNote = creditNoteRepository.save(creditNote);
    
            String cancelReason = "Unbilled cancelled because refund credit note was finally approved by ADMIN. Credit Note: "
                    + savedCreditNote.getCreditNoteNumber();
    
            if (request != null && request.getApprovalRemarks() != null && !request.getApprovalRemarks().isBlank()) {
                cancelReason += " | Remarks: " + request.getApprovalRemarks();
            }
    
            unbilledService.cancelUnbilled(
                    userId,
                    unbilled.getId(),
                    cancelReason,
                    savedCreditNote.getAttachment()
            );
    
            /*
             * Post credit note accounting voucher only after final ADMIN approval.
             *
             * Dr Sales Return / Credit Note Ledger
             * Dr Output CGST / SGST / IGST Ledger
             * Cr Customer Ledger
             */
            postCreditNoteAccountingVoucher(savedCreditNote, adminApprover);
    
            log.info(
                    "Credit note final-approved by ADMIN, unbilled cancellation completed and voucher posted | creditNoteId={} | unbilledId={}",
                    creditNoteId,
                    unbilled.getId()
            );
    
            return toResponse(savedCreditNote);
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
    
        private void postCreditNoteAccountingVoucher(
                CreditNote creditNote,
                User adminApprover
        ) {
            if (creditNote == null || creditNote.getId() == null) {
                throw new ValidationException(
                        "Saved credit note is required to post accounting voucher",
                        "ERR_CREDIT_NOTE_REQUIRED_FOR_VOUCHER"
                );
            }
    
            BigDecimal refundAmount = safeMoney(creditNote.getRefundAmount());
    
            if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.info("Skipping credit note voucher because refund amount is zero | creditNote={}",
                        creditNote.getCreditNoteNumber());
                return;
            }
    
            if (creditNote.getInvoiceDetails() == null || creditNote.getInvoiceDetails().isEmpty()) {
                throw new ValidationException(
                        "Credit note invoice details are required for GST voucher calculation",
                        "ERR_CREDIT_NOTE_INVOICE_DETAILS_REQUIRED"
                );
            }
    
            CreditNoteTaxBreakup taxBreakup = calculateCreditNoteTaxBreakup(creditNote);
    
            LedgerMaster salesReturnLedger = getOrCreateSystemLedger(
                    LedgerType.SALES_RETURN,
                    LedgerGroupType.SALES_ACCOUNTS,
                    "Sales Return / Credit Note",
                    DebitCredit.DEBIT,
                    adminApprover
            );
    
            LedgerMaster outputCgstLedger = getOrCreateSystemLedger(
                    LedgerType.OUTPUT_CGST,
                    LedgerGroupType.DUTIES_AND_TAXES,
                    "Output CGST",
                    DebitCredit.CREDIT,
                    adminApprover
            );
    
            LedgerMaster outputSgstLedger = getOrCreateSystemLedger(
                    LedgerType.OUTPUT_SGST,
                    LedgerGroupType.DUTIES_AND_TAXES,
                    "Output SGST",
                    DebitCredit.CREDIT,
                    adminApprover
            );
    
            LedgerMaster outputIgstLedger = getOrCreateSystemLedger(
                    LedgerType.OUTPUT_IGST,
                    LedgerGroupType.DUTIES_AND_TAXES,
                    "Output IGST",
                    DebitCredit.CREDIT,
                    adminApprover
            );
    
            LedgerMaster customerLedger = getOrCreateCustomerLedger(
                    creditNote,
                    adminApprover
            );
    
            List<AccountingVoucherEntryRequestDto> entries = new ArrayList<>();
    
            // Dr Sales Return / Credit Note Ledger
            if (taxBreakup.taxableAmount().compareTo(BigDecimal.ZERO) > 0) {
                entries.add(
                        buildVoucherEntry(
                                salesReturnLedger.getId(),
                                taxBreakup.taxableAmount(),
                                BigDecimal.ZERO,
                                "Sales return booked for credit note " + creditNote.getCreditNoteNumber()
                        )
                );
            }
    
            // Dr Output CGST
            if (taxBreakup.cgstAmount().compareTo(BigDecimal.ZERO) > 0) {
                entries.add(
                        buildVoucherEntry(
                                outputCgstLedger.getId(),
                                taxBreakup.cgstAmount(),
                                BigDecimal.ZERO,
                                "Output CGST reversed for credit note " + creditNote.getCreditNoteNumber()
                        )
                );
            }
    
            // Dr Output SGST
            if (taxBreakup.sgstAmount().compareTo(BigDecimal.ZERO) > 0) {
                entries.add(
                        buildVoucherEntry(
                                outputSgstLedger.getId(),
                                taxBreakup.sgstAmount(),
                                BigDecimal.ZERO,
                                "Output SGST reversed for credit note " + creditNote.getCreditNoteNumber()
                        )
                );
            }
    
            // Dr Output IGST
            if (taxBreakup.igstAmount().compareTo(BigDecimal.ZERO) > 0) {
                entries.add(
                        buildVoucherEntry(
                                outputIgstLedger.getId(),
                                taxBreakup.igstAmount(),
                                BigDecimal.ZERO,
                                "Output IGST reversed for credit note " + creditNote.getCreditNoteNumber()
                        )
                );
            }
    
            // Cr Customer Ledger / Sundry Debtors
            entries.add(
                    buildVoucherEntry(
                            customerLedger.getId(),
                            BigDecimal.ZERO,
                            refundAmount,
                            "Customer liability/adjustment created for credit note " + creditNote.getCreditNoteNumber()
                    )
            );
    
            AccountingVoucherRequestDto voucherRequest =
                    AccountingVoucherRequestDto.builder()
                            .voucherType(VoucherType.CREDIT_NOTE)
                            .voucherDate(LocalDate.now())
                            .sourceType(VoucherSourceType.CREDIT_NOTE)
                            .sourceId(creditNote.getId())
                            .narration(
                                    "Credit note approved and posted: "
                                            + creditNote.getCreditNoteNumber()
                                            + ", unbilled: "
                                            + creditNote.getUnbilledNumber()
                            )
                            .entries(entries)
                            .build();
    
            accountingVoucherService.createVoucher(voucherRequest);
    
            log.info(
                    "Credit note voucher posted | creditNote={} | taxable={} | cgst={} | sgst={} | igst={} | customerCredit={}",
                    creditNote.getCreditNoteNumber(),
                    taxBreakup.taxableAmount(),
                    taxBreakup.cgstAmount(),
                    taxBreakup.sgstAmount(),
                    taxBreakup.igstAmount(),
                    refundAmount
            );
        }
    
        private AccountingVoucherEntryRequestDto buildVoucherEntry(
                Long ledgerId,
                BigDecimal debitAmount,
                BigDecimal creditAmount,
                String narration
        ) {
            return AccountingVoucherEntryRequestDto.builder()
                    .ledgerId(ledgerId)
                    .debitAmount(safeMoney(debitAmount))
                    .creditAmount(safeMoney(creditAmount))
                    .narration(narration)
                    .build();
        }
    
        private LedgerMaster getOrCreateCustomerLedger(
                CreditNote creditNote,
                User createdBy
        ) {
            Company company = creditNote.getCompany();
            Contact contact = creditNote.getContact();
    
            UnbilledInvoice unbilled = creditNote.getUnbilledInvoice();
            CompanyUnit unit = unbilled != null ? unbilled.getUnit() : null;
    
            if (company == null || company.getId() == null) {
                throw new ValidationException(
                        "Company is required to create customer ledger",
                        "ERR_COMPANY_REQUIRED_FOR_CUSTOMER_LEDGER"
                );
            }
    
            Long companyId = company.getId();
            Long unitId = unit != null ? unit.getId() : null;
    
            Optional<LedgerMaster> existingLedger;
    
            if (unitId != null) {
                existingLedger = ledgerMasterRepository
                        .findByCompanyIdAndUnitIdAndLedgerTypeAndDeletedFalse(
                                companyId,
                                unitId,
                                LedgerType.CUSTOMER
                        );
            } else {
                existingLedger = ledgerMasterRepository
                        .findByCompanyIdAndLedgerTypeAndDeletedFalse(
                                companyId,
                                LedgerType.CUSTOMER
                        );
            }
    
            if (existingLedger.isPresent()) {
                return existingLedger.get();
            }
    
            LedgerGroup sundryDebtorsGroup = ledgerGroupRepository
                    .findByGroupTypeAndDeletedFalse(LedgerGroupType.SUNDRY_DEBTORS)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Sundry Debtors ledger group not found",
                            "SUNDRY_DEBTORS_GROUP_NOT_FOUND"
                    ));
    
            String companyName = company.getName() != null
                    ? company.getName().trim()
                    : "Company-" + companyId;
    
            String unitName = unit != null && unit.getUnitName() != null
                    ? unit.getUnitName().trim()
                    : null;
    
            String ledgerName = unitName != null && !unitName.isBlank()
                    ? "Customer - " + companyName + " - " + unitName
                    : "Customer - " + companyName;
    
            LedgerMaster ledger = new LedgerMaster();
    
            ledger.setLedgerName(ledgerName);
            ledger.setLedgerCode(generateLedgerCode("CUST"));
            ledger.setLedgerType(LedgerType.CUSTOMER);
            ledger.setLedgerGroup(sundryDebtorsGroup);
    
            ledger.setCompany(company);
            ledger.setUnit(unit);
            ledger.setContact(contact);
    
            ledger.setGstNo(unit != null ? unit.getGstNo() : null);
            ledger.setPanNo(company.getPanNo());
    
            ledger.setOpeningBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            ledger.setOpeningBalanceType(DebitCredit.DEBIT);
    
            ledger.setCurrentBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            ledger.setCurrentBalanceType(DebitCredit.DEBIT);
    
            ledger.setSystemCreated(true);
            ledger.setActive(true);
            ledger.setDeleted(false);
    
            if (createdBy != null) {
                ledger.setCreatedBy(createdBy);
                ledger.setUpdatedBy(createdBy);
            }
    
            return ledgerMasterRepository.save(ledger);
        }
    
        private CreditNoteTaxBreakup calculateCreditNoteTaxBreakup(CreditNote creditNote) {
    
            BigDecimal refundAmount = safeMoney(creditNote.getRefundAmount());
    
            BigDecimal invoiceGrandTotal = creditNote.getInvoiceDetails()
                    .stream()
                    .map(CreditNoteInvoiceDetail::getInvoiceGrandTotal)
                    .map(this::safeMoney)
                    .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    
            if (invoiceGrandTotal.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException(
                        "Invoice grand total is required to calculate credit note GST breakup",
                        "ERR_INVOICE_TOTAL_REQUIRED_FOR_CREDIT_NOTE_GST"
                );
            }
    
            BigDecimal invoiceCgst = creditNote.getInvoiceDetails()
                    .stream()
                    .map(CreditNoteInvoiceDetail::getInvoiceCgstAmount)
                    .map(this::safeMoney)
                    .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    
            BigDecimal invoiceSgst = creditNote.getInvoiceDetails()
                    .stream()
                    .map(CreditNoteInvoiceDetail::getInvoiceSgstAmount)
                    .map(this::safeMoney)
                    .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    
            BigDecimal invoiceIgst = creditNote.getInvoiceDetails()
                    .stream()
                    .map(CreditNoteInvoiceDetail::getInvoiceIgstAmount)
                    .map(this::safeMoney)
                    .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    
            BigDecimal ratio = refundAmount.divide(
                    invoiceGrandTotal,
                    10,
                    RoundingMode.HALF_UP
            );
    
            BigDecimal cgstAmount = invoiceCgst
                    .multiply(ratio)
                    .setScale(2, RoundingMode.HALF_UP);
    
            BigDecimal sgstAmount = invoiceSgst
                    .multiply(ratio)
                    .setScale(2, RoundingMode.HALF_UP);
    
            BigDecimal igstAmount = invoiceIgst
                    .multiply(ratio)
                    .setScale(2, RoundingMode.HALF_UP);
    
            BigDecimal totalGst = cgstAmount
                    .add(sgstAmount)
                    .add(igstAmount)
                    .setScale(2, RoundingMode.HALF_UP);
    
            BigDecimal taxableAmount = refundAmount
                    .subtract(totalGst)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
    
            return new CreditNoteTaxBreakup(
                    taxableAmount,
                    cgstAmount,
                    sgstAmount,
                    igstAmount
            );
        }
    
        private record CreditNoteTaxBreakup(
                BigDecimal taxableAmount,
                BigDecimal cgstAmount,
                BigDecimal sgstAmount,
                BigDecimal igstAmount
        ) {
        }
    
    
    
        private BigDecimal safeMoney(BigDecimal value) {
            return value == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : value.setScale(2, RoundingMode.HALF_UP);
        }
    
        private String generateLedgerCode(String prefix) {
            String safePrefix = prefix == null || prefix.trim().isEmpty()
                    ? "SYS"
                    : prefix.trim().replaceAll("[^A-Za-z0-9]", "-").toUpperCase();
    
            long sequence = ledgerMasterRepository.count() + 1;
            String ledgerCode;
    
            do {
                ledgerCode = String.format("LED-%s-%06d", safePrefix, sequence++);
            } while (ledgerMasterRepository.existsByLedgerCodeIgnoreCase(ledgerCode));
    
            return ledgerCode;
        }
        private LedgerMaster getOrCreateSystemLedger(
                LedgerType ledgerType,
                LedgerGroupType ledgerGroupType,
                String ledgerName,
                DebitCredit balanceType,
                User createdBy
        ) {
            Optional<LedgerMaster> existingLedger =
                    ledgerMasterRepository.findByLedgerTypeAndDeletedFalse(ledgerType);
    
            if (existingLedger.isPresent()) {
                return existingLedger.get();
            }
    
            LedgerGroup ledgerGroup = ledgerGroupRepository
                    .findByGroupTypeAndDeletedFalse(ledgerGroupType)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            ledgerGroupType + " ledger group not found",
                            ledgerGroupType + "_GROUP_NOT_FOUND"
                    ));
    
            LedgerMaster ledger = new LedgerMaster();
    
            ledger.setLedgerName(ledgerName);
            ledger.setLedgerCode(generateLedgerCode(ledgerType.name()));
            ledger.setLedgerType(ledgerType);
            ledger.setLedgerGroup(ledgerGroup);
    
            ledger.setOpeningBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            ledger.setOpeningBalanceType(balanceType);
    
            ledger.setCurrentBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            ledger.setCurrentBalanceType(balanceType);
    
            ledger.setSystemCreated(true);
            ledger.setActive(true);
            ledger.setDeleted(false);
    
            if (createdBy != null && createdBy.getId() != null) {
                ledger.setCreatedBy(createdBy);
                ledger.setUpdatedBy(createdBy);
            }
    
            return ledgerMasterRepository.save(ledger);
        }
    
    }