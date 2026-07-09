package com.account.serviceImpl.ledger;

import com.account.domain.User;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.Contact;
import com.account.domain.invoice.Invoice;
import com.account.domain.ledger.*;
import com.account.dto.ledger.*;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.*;
import com.account.repository.ledger.AccountingVoucherEntryRepository;
import com.account.repository.ledger.LedgerGroupRepository;
import com.account.repository.ledger.LedgerMasterRepository;
import com.account.service.ledger.LedgerMasterService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LedgerMasterServiceImpl implements LedgerMasterService {

    private final LedgerMasterRepository ledgerMasterRepository;
    private final LedgerGroupRepository ledgerGroupRepository;
    private final CompanyRepository companyRepository;
    private final CompanyUnitRepository companyUnitRepository;
    private final ContactRepository contactRepository;
    private final AccountingVoucherEntryRepository accountingVoucherEntryRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public LedgerMasterResponseDto createLedger(LedgerMasterRequestDto request) {

        validateRequest(request);

        String ledgerName = normalizeName(request.getLedgerName());

        if (ledgerMasterRepository.existsByLedgerNameIgnoreCase(ledgerName)) {
            throw new ValidationException(
                    "Ledger already exists with name: " + ledgerName,
                    "ERR_LEDGER_DUPLICATE",
                    "ledgerName"
            );
        }

        LedgerGroup ledgerGroup = resolveLedgerGroupForLedgerType(
                request.getLedgerType(),
                request.getLedgerGroupId()
        );


        Company company = getCompany(request.getCompanyId());
        CompanyUnit unit = getUnit(request.getUnitId());
        Contact contact = getContact(request.getContactId());

        validateLedgerBusinessRules(request, company, unit);

        LedgerMaster ledger = buildLedgerMaster(
                request,
                ledgerName,
                ledgerGroup,
                company,
                unit,
                contact
        );

        LedgerMaster saved = ledgerMasterRepository.save(ledger);

        return mapToResponse(saved);
    }
    private void validateAdminUserForLedgerEdit(Long userId) {

        if (userId == null) {
            throw new ValidationException(
                    "User ID is required to edit ledger",
                    "ERR_USER_ID_REQUIRED",
                    "userId"
            );
        }

        User user = userRepository.findByIdAndNotDeleted(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId,
                        "USER_NOT_FOUND"
                ));

        if (!user.isActive()) {
            throw new ValidationException(
                    "Inactive user cannot edit ledger",
                    "ERR_INACTIVE_USER_LEDGER_EDIT_NOT_ALLOWED",
                    "userId"
            );
        }

        boolean isAdminFromRoleList =
                user.getRole() != null
                        && user.getRole().stream()
                        .filter(Objects::nonNull)
                        .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.trim()));

        boolean isAdminFromUserRole =
                user.getUserRole() != null
                        && user.getUserRole().stream()
                        .filter(Objects::nonNull)
                        .anyMatch(role ->
                                role.getName() != null
                                        && "ADMIN".equalsIgnoreCase(role.getName().trim())
                        );

        if (!isAdminFromRoleList && !isAdminFromUserRole) {
            throw new ValidationException(
                    "Only ADMIN role can edit ledger",
                    "ERR_LEDGER_EDIT_ADMIN_ONLY",
                    "userId"
            );
        }
    }


    @Override
    @Transactional
    public LedgerMasterResponseDto updateLedger(Long id, LedgerMasterRequestDto request, Long userId) {

        validateAdminUserForLedgerEdit(userId);

        validateRequest(request);

        LedgerMaster ledger = ledgerMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger not found with ID: " + id,
                        "LEDGER_NOT_FOUND"
                ));

        String ledgerName = normalizeName(request.getLedgerName());

        if (ledgerMasterRepository.existsByLedgerNameIgnoreCaseAndIdNot(ledgerName, id)) {
            throw new ValidationException(
                    "Ledger already exists with name: " + ledgerName,
                    "ERR_LEDGER_DUPLICATE",
                    "ledgerName"
            );
        }

        LedgerGroup ledgerGroup = resolveLedgerGroupForLedgerType(
                request.getLedgerType(),
                request.getLedgerGroupId()
        );

        Company company = getCompany(request.getCompanyId());
        CompanyUnit unit = getUnit(request.getUnitId());
        Contact contact = getContact(request.getContactId());

        validateLedgerBusinessRules(request, company, unit);

        ledger.setLedgerName(ledgerName);
        ledger.setLedgerType(request.getLedgerType());
        ledger.setLedgerGroup(ledgerGroup);

        ledger.setCompany(company);
        ledger.setUnit(unit);
        ledger.setContact(contact);

        ledger.setGstNo(clean(request.getGstNo()));
        ledger.setPanNo(clean(request.getPanNo()));

        ledger.setBankName(clean(request.getBankName()));
        ledger.setAccountHolderName(clean(request.getAccountHolderName()));
        ledger.setAccountNumber(clean(request.getAccountNumber()));
        ledger.setIfscCode(clean(request.getIfscCode()));
        ledger.setBranchName(clean(request.getBranchName()));

        ledger.setOpeningBalance(safeMoney(request.getOpeningBalance()));
        ledger.setOpeningBalanceType(request.getOpeningBalanceType());

        if (request.getActive() != null) {
            ledger.setActive(request.getActive());
        }

        LedgerMaster saved = ledgerMasterRepository.save(ledger);

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerMasterResponseDto getLedgerById(Long id) {

        LedgerMaster ledger = ledgerMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger not found with ID: " + id,
                        "LEDGER_NOT_FOUND"
                ));

        return mapToResponse(ledger);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<LedgerMasterResponseDto> getLedgers(
            String search,
            LedgerType ledgerType,
            Long ledgerGroupId,
            LedgerGroupType ledgerGroupType,
            Long companyId,
            Long unitId,
            Boolean active,
            int page,
            int size
    ) {

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 || size > 200 ? 20 : size;

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.ASC, "ledgerName")
        );

        Specification<LedgerMaster> specification = buildSpecification(
                search,
                ledgerType,
                ledgerGroupId,
                ledgerGroupType,
                companyId,
                unitId,
                active
        );

        return ledgerMasterRepository.findAll(specification, pageable)
                .map(ledger -> {

                    LedgerMasterResponseDto response = mapToResponse(ledger);

                    LedgerStatementResponseDto statement = getLedgerTransactions(
                            ledger.getId(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            0,
                            20
                    );

                    response.setTransactions(
                            statement != null && statement.getTransactions() != null
                                    ? statement.getTransactions()
                                    : new ArrayList<>()
                    );

                    return response;
                });
    }


    private BigDecimal moneyForStatement(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toSignedBalanceForStatement(BigDecimal amount, DebitCredit type) {
        BigDecimal value = moneyForStatement(amount);

        if (type == DebitCredit.CREDIT) {
            return value.negate().setScale(2, RoundingMode.HALF_UP);
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal absAmountForStatement(BigDecimal signedAmount) {
        return moneyForStatement(signedAmount).abs().setScale(2, RoundingMode.HALF_UP);
    }

    private DebitCredit balanceTypeForStatement(BigDecimal signedAmount) {
        if (signedAmount == null || signedAmount.compareTo(BigDecimal.ZERO) >= 0) {
            return DebitCredit.DEBIT;
        }

        return DebitCredit.CREDIT;
    }

    @Override
    @Transactional
    public void deleteLedger(Long id) {

        LedgerMaster ledger = ledgerMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger not found with ID: " + id,
                        "LEDGER_NOT_FOUND"
                ));

        if (ledger.isSystemCreated()) {
            throw new ValidationException(
                    "System-created ledger cannot be deleted",
                    "ERR_SYSTEM_LEDGER_DELETE_NOT_ALLOWED",
                    "id"
            );
        }

        ledger.setDeleted(true);
        ledger.setActive(false);

        ledgerMasterRepository.save(ledger);
    }

    private Specification<LedgerMaster> buildSpecification(
            String search,
            LedgerType ledgerType,
            Long ledgerGroupId,
            Boolean active
    ) {
        return (root, query, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.isFalse(root.get("deleted")));

            if (search != null && !search.trim().isEmpty()) {
                String likeSearch = "%" + search.trim().toLowerCase() + "%";

                predicates.add(
                        criteriaBuilder.or(
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("ledgerName")), likeSearch),
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("ledgerCode")), likeSearch)
                        )
                );
            }

            if (ledgerType != null) {
                predicates.add(criteriaBuilder.equal(root.get("ledgerType"), ledgerType));
            }

            if (ledgerGroupId != null) {
                Join<LedgerMaster, LedgerGroup> groupJoin = root.join("ledgerGroup", JoinType.LEFT);
                predicates.add(criteriaBuilder.equal(groupJoin.get("id"), ledgerGroupId));
            }

            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void validateRequest(LedgerMasterRequestDto request) {

        if (request == null) {
            throw new ValidationException(
                    "Request body is required",
                    "ERR_REQUEST_REQUIRED"
            );
        }

        if (request.getLedgerName() == null || request.getLedgerName().trim().isEmpty()) {
            throw new ValidationException(
                    "Ledger name is required",
                    "ERR_LEDGER_NAME_REQUIRED",
                    "ledgerName"
            );
        }

        if (request.getLedgerType() == null) {
            throw new ValidationException(
                    "Ledger type is required",
                    "ERR_LEDGER_TYPE_REQUIRED",
                    "ledgerType"
            );
        }

        if (request.getLedgerGroupId() == null || request.getLedgerGroupId() <= 0) {
            throw new ValidationException(
                    "Ledger group ID is required",
                    "ERR_LEDGER_GROUP_REQUIRED",
                    "ledgerGroupId"
            );
        }

        if (request.getOpeningBalance() != null
                && request.getOpeningBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException(
                    "Opening balance cannot be negative",
                    "ERR_OPENING_BALANCE_NEGATIVE",
                    "openingBalance"
            );
        }

        if (request.getOpeningBalance() != null
                && request.getOpeningBalance().compareTo(BigDecimal.ZERO) > 0
                && request.getOpeningBalanceType() == null) {
            throw new ValidationException(
                    "Opening balance type is required when opening balance is greater than zero",
                    "ERR_OPENING_BALANCE_TYPE_REQUIRED",
                    "openingBalanceType"
            );
        }
    }

    private void validateLedgerBusinessRules(
            LedgerMasterRequestDto request,
            Company company,
            CompanyUnit unit
    ) {

        LedgerType ledgerType = request.getLedgerType();

        if (ledgerType == LedgerType.BANK) {
            if (request.getBankName() == null || request.getBankName().trim().isEmpty()) {
                throw new ValidationException(
                        "Bank name is required for bank ledger",
                        "ERR_BANK_NAME_REQUIRED",
                        "bankName"
                );
            }
        }

        if (ledgerType == LedgerType.CUSTOMER || ledgerType == LedgerType.CUSTOMER_ADVANCE) {
            if (company == null) {
                throw new ValidationException(
                        "Company is required for customer ledger",
                        "ERR_COMPANY_REQUIRED_FOR_CUSTOMER_LEDGER",
                        "companyId"
                );
            }
        }

        if (unit != null && company != null && unit.getCompany() != null
                && !unit.getCompany().getId().equals(company.getId())) {
            throw new ValidationException(
                    "Selected unit does not belong to selected company",
                    "ERR_UNIT_COMPANY_MISMATCH",
                    "unitId"
            );
        }
    }

    private LedgerGroup getLedgerGroup(Long ledgerGroupId) {
        return ledgerGroupRepository.findByIdAndDeletedFalse(ledgerGroupId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger group not found with ID: " + ledgerGroupId,
                        "LEDGER_GROUP_NOT_FOUND"
                ));
    }

    private Company getCompany(Long companyId) {
        if (companyId == null || companyId <= 0) {
            return null;
        }

        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Company not found with ID: " + companyId,
                        "COMPANY_NOT_FOUND"
                ));
    }

    private CompanyUnit getUnit(Long unitId) {
        if (unitId == null || unitId <= 0) {
            return null;
        }

        return companyUnitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Company unit not found with ID: " + unitId,
                        "COMPANY_UNIT_NOT_FOUND"
                ));
    }

    private Contact getContact(Long contactId) {
        if (contactId == null || contactId <= 0) {
            return null;
        }

        return contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contact not found with ID: " + contactId,
                        "CONTACT_NOT_FOUND"
                ));
    }

    private String generateLedgerCode(LedgerType ledgerType) {

        String prefix = switch (ledgerType) {
            case CUSTOMER -> "LED-CUST-";
            case CUSTOMER_ADVANCE -> "LED-ADV-";
            case BANK -> "LED-BANK-";
            case CASH -> "LED-CASH-";
            case SALES, SERVICE_INCOME -> "LED-SALE-";
            case OUTPUT_IGST, OUTPUT_CGST, OUTPUT_SGST -> "LED-GST-";
            case TDS_RECEIVABLE -> "LED-TDS-";
            case REFUND_PAYABLE -> "LED-REF-";
            default -> "LED-";
        };

        String code;

        do {
            code = prefix + System.currentTimeMillis();
        } while (ledgerMasterRepository.existsByLedgerCodeIgnoreCase(code));

        return code;
    }

    private LedgerGroupType resolveDefaultGroupTypeFromLedgerType(LedgerType ledgerType) {

        if (ledgerType == null) {
            throw new ValidationException(
                    "Ledger type is required",
                    "ERR_LEDGER_TYPE_REQUIRED",
                    "ledgerType"
            );
        }

        return switch (ledgerType) {

            case CASH -> LedgerGroupType.CASH_IN_HAND;

            case BANK,
                    PAYMENT_GATEWAY -> LedgerGroupType.BANK_ACCOUNTS;

            case CUSTOMER -> LedgerGroupType.SUNDRY_DEBTORS;

            case SUPPLIER,
                    VENDOR,
                    VENDOR_PAYABLE -> LedgerGroupType.SUNDRY_CREDITORS;

            case CUSTOMER_ADVANCE,
                    LIABILITY,
                    REFUND_PAYABLE -> LedgerGroupType.CURRENT_LIABILITIES;

            case SALES,
                    SERVICE_INCOME,
                    SALES_RETURN -> LedgerGroupType.SALES_ACCOUNTS;

            case PURCHASE -> LedgerGroupType.PURCHASE_ACCOUNTS;

            case TAX,
                    OUTPUT_IGST,
                    OUTPUT_CGST,
                    OUTPUT_SGST,
                    INPUT_IGST,
                    INPUT_CGST,
                    INPUT_SGST,
                    TDS_RECEIVABLE,
                    TDS_PAYABLE -> LedgerGroupType.DUTIES_AND_TAXES;

            case EXPENSE,
                    ROUND_OFF -> LedgerGroupType.INDIRECT_EXPENSES;

            case INCOME -> LedgerGroupType.INDIRECT_INCOMES;

            case ASSET -> LedgerGroupType.CURRENT_ASSETS;

            case LOAN -> LedgerGroupType.LOANS_LIABILITY;

            case CAPITAL -> LedgerGroupType.CAPITAL_ACCOUNT;

            case INVESTMENT -> LedgerGroupType.INVESTMENTS;

            case STOCK -> LedgerGroupType.STOCK_IN_HAND;

            case SUSPENSE -> LedgerGroupType.SUSPENSE_ACCOUNT;

            case BRANCH -> LedgerGroupType.BRANCH_DIVISIONS;

            default -> throw new ValidationException(
                    "No default ledger group mapping found for ledger type: " + ledgerType,
                    "ERR_LEDGER_GROUP_MAPPING_NOT_FOUND",
                    "ledgerType"
            );
        };
    }

    private String normalizeName(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private String clean(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private LedgerMasterResponseDto mapToResponse(LedgerMaster ledger) {

        if (ledger == null) {
            return null;
        }

        Company company = ledger.getCompany();
        CompanyUnit unit = ledger.getUnit();
        Contact contact = ledger.getContact();
        LedgerGroup ledgerGroup = ledger.getLedgerGroup();

        return LedgerMasterResponseDto.builder()
                .id(ledger.getId())
                .ledgerName(ledger.getLedgerName())
                .ledgerCode(ledger.getLedgerCode())
                .ledgerType(ledger.getLedgerType())

                .ledgerGroupId(ledgerGroup != null ? ledgerGroup.getId() : null)
                .ledgerGroupName(ledgerGroup != null ? ledgerGroup.getName() : null)
                .ledgerGroupType(ledgerGroup != null ? ledgerGroup.getGroupType() : null)

                .companyId(company != null ? company.getId() : null)
                .companyName(company != null ? company.getName() : null)

                .unitId(unit != null ? unit.getId() : null)
                .unitName(unit != null ? unit.getUnitName() : null)

                .addressLine1(unit != null ? unit.getAddressLine1() : null)
                .addressLine2(unit != null ? unit.getAddressLine2() : null)
                .city(unit != null ? unit.getCity() : null)
                .state(unit != null ? unit.getState() : null)
                .country(unit != null ? unit.getCountry() : null)
                .pinCode(unit != null ? unit.getPinCode() : null)
                .fullAddress(buildFullAddress(unit))

                .contactId(contact != null ? contact.getId() : null)
                .contactName(contact != null ? contact.getName() : null)

                .gstNo(ledger.getGstNo())
                .panNo(ledger.getPanNo())

                .bankName(ledger.getBankName())
                .accountHolderName(ledger.getAccountHolderName())
                .accountNumber(ledger.getAccountNumber())
                .ifscCode(ledger.getIfscCode())
                .branchName(ledger.getBranchName())

                .openingBalance(ledger.getOpeningBalance())
                .openingBalanceType(ledger.getOpeningBalanceType())

                .currentBalance(ledger.getCurrentBalance())
                .currentBalanceType(ledger.getCurrentBalanceType())

                .systemCreated(ledger.isSystemCreated())
                .active(ledger.isActive())
                .deleted(ledger.isDeleted())

                .createdAt(ledger.getCreatedAt())
                .updatedAt(ledger.getUpdatedAt())
                .build();
    }

    private String buildFullAddress(CompanyUnit unit) {

        if (unit == null) {
            return null;
        }

        List<String> addressParts = new ArrayList<>();

        if (unit.getAddressLine1() != null && !unit.getAddressLine1().trim().isEmpty()) {
            addressParts.add(unit.getAddressLine1().trim());
        }

        if (unit.getAddressLine2() != null && !unit.getAddressLine2().trim().isEmpty()) {
            addressParts.add(unit.getAddressLine2().trim());
        }

        if (unit.getCity() != null && !unit.getCity().trim().isEmpty()) {
            addressParts.add(unit.getCity().trim());
        }

        if (unit.getState() != null && !unit.getState().trim().isEmpty()) {
            addressParts.add(unit.getState().trim());
        }

        if (unit.getCountry() != null && !unit.getCountry().trim().isEmpty()) {
            addressParts.add(unit.getCountry().trim());
        }

        if (unit.getPinCode() != null && !unit.getPinCode().trim().isEmpty()) {
            addressParts.add(unit.getPinCode().trim());
        }

        return addressParts.isEmpty()
                ? null
                : String.join(", ", addressParts);
    }
    private LedgerMaster buildLedgerMaster(
            LedgerMasterRequestDto request,
            String ledgerName,
            LedgerGroup ledgerGroup,
            Company company,
            CompanyUnit unit,
            Contact contact
    ) {
        BigDecimal openingBalance = safeMoney(request.getOpeningBalance());

        LedgerMaster ledger = new LedgerMaster();

        ledger.setLedgerName(ledgerName);
        ledger.setLedgerCode(generateLedgerCode(request.getLedgerType()));

        ledger.setLedgerType(request.getLedgerType());
        ledger.setLedgerGroup(ledgerGroup);

        ledger.setCompany(company);
        ledger.setUnit(unit);
        ledger.setContact(contact);

        ledger.setGstNo(clean(request.getGstNo()));
        ledger.setPanNo(clean(request.getPanNo()));

        ledger.setBankName(clean(request.getBankName()));
        ledger.setAccountHolderName(clean(request.getAccountHolderName()));
        ledger.setAccountNumber(clean(request.getAccountNumber()));
        ledger.setIfscCode(clean(request.getIfscCode()));
        ledger.setBranchName(clean(request.getBranchName()));

        ledger.setOpeningBalance(openingBalance);
        ledger.setOpeningBalanceType(request.getOpeningBalanceType());

        /*
         * At the time of ledger creation, current balance should be same as opening balance.
         * Later, voucher posting will update currentBalance.
         */
        ledger.setCurrentBalance(openingBalance);
        ledger.setCurrentBalanceType(request.getOpeningBalanceType());

        ledger.setSystemCreated(false);
        ledger.setActive(request.getActive() == null || request.getActive());
        ledger.setDeleted(false);

        return ledger;
    }

    private LedgerGroup resolveLedgerGroupForLedgerType(
            LedgerType ledgerType,
            Long ledgerGroupId
    ) {
        /*
         * If frontend sends ledgerGroupId, respect it.
         * If frontend does not send ledgerGroupId, auto-resolve from ledgerType.
         */
        if (ledgerGroupId != null && ledgerGroupId > 0) {
            return getLedgerGroup(ledgerGroupId);
        }

        LedgerGroupType defaultGroupType = resolveDefaultGroupTypeFromLedgerType(ledgerType);

        return getOrCreateLedgerGroupByType(defaultGroupType);
    }

    private LedgerGroup getOrCreateLedgerGroupByType(LedgerGroupType groupType) {

        if (groupType == null) {
            throw new ValidationException(
                    "Ledger group type is required",
                    "ERR_LEDGER_GROUP_TYPE_REQUIRED",
                    "groupType"
            );
        }

        return ledgerGroupRepository.findByGroupTypeAndDeletedFalse(groupType)
                .map(existingGroup -> {
                    if (!existingGroup.isActive()) {
                        existingGroup.setActive(true);
                        return ledgerGroupRepository.save(existingGroup);
                    }
                    return existingGroup;
                })
                .orElseGet(() -> {
                    LedgerGroup ledgerGroup = LedgerGroup.builder()
                            .name(formatGroupTypeLabel(groupType))
                            .groupType(groupType)
                            .description("System-created default ledger group")
                            .systemDefault(true)
                            .active(true)
                            .deleted(false)
                            .build();

                    return ledgerGroupRepository.save(ledgerGroup);
                });
    }

    private String formatGroupTypeLabel(LedgerGroupType groupType) {

        if (groupType == null) {
            return null;
        }

        return java.util.Arrays.stream(groupType.name().toLowerCase().split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .reduce((first, second) -> first + " " + second)
                .orElse(groupType.name());
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerStatementResponseDto getLedgerTransactions(
            Long ledgerId,
            LocalDate fromDate,
            LocalDate toDate,
            String search,
            String voucherType,
            String sourceType,
            String entryType,
            int page,
            int size
    ) {
        if (ledgerId == null || ledgerId <= 0) {
            throw new ValidationException(
                    "Ledger ID is required",
                    "ERR_LEDGER_ID_REQUIRED",
                    "ledgerId"
            );
        }

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ValidationException(
                    "From date cannot be after to date",
                    "ERR_INVALID_DATE_RANGE",
                    "fromDate"
            );
        }

        LedgerMaster ledger = ledgerMasterRepository.findByIdAndDeletedFalse(ledgerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger not found with ID: " + ledgerId,
                        "LEDGER_NOT_FOUND"
                ));

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 || size > 200 ? 20 : size;

        BigDecimal openingSignedBalance = toSignedBalanceForStatement(
                ledger.getOpeningBalance(),
                ledger.getOpeningBalanceType()
        );

        if (fromDate != null) {
            BigDecimal debitBefore = moneyForStatement(
                    accountingVoucherEntryRepository.sumDebitBeforeDate(
                            ledgerId,
                            fromDate,
                            VoucherStatus.POSTED
                    )
            );

            BigDecimal creditBefore = moneyForStatement(
                    accountingVoucherEntryRepository.sumCreditBeforeDate(
                            ledgerId,
                            fromDate,
                            VoucherStatus.POSTED
                    )
            );

            openingSignedBalance = openingSignedBalance
                    .add(debitBefore)
                    .subtract(creditBefore)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        List<AccountingVoucherEntry> entries =
                accountingVoucherEntryRepository.findLedgerEntriesForStatement(
                        ledgerId,
                        fromDate,
                        toDate,
                        VoucherStatus.POSTED
                );

        BigDecimal runningSignedBalance = openingSignedBalance;

        List<LedgerTransactionResponseDto> allRows = new ArrayList<>();

        Map<Long, Invoice> invoiceCache = new HashMap<>();
        Map<Long, List<AccountingVoucherEntry>> otherEntriesCache = new HashMap<>();

        for (AccountingVoucherEntry entry : entries) {

            AccountingVoucher voucher = entry.getVoucher();

            BigDecimal debit = moneyForStatement(entry.getDebitAmount());
            BigDecimal credit = moneyForStatement(entry.getCreditAmount());

            runningSignedBalance = runningSignedBalance
                    .add(debit)
                    .subtract(credit)
                    .setScale(2, RoundingMode.HALF_UP);

            String narration = entry.getNarration();

            if ((narration == null || narration.trim().isEmpty()) && voucher != null) {
                narration = voucher.getNarration();
            }

            Optional<Invoice> salesInvoiceOptional = getSalesInvoice(voucher, invoiceCache);

            Invoice salesInvoice = salesInvoiceOptional.orElse(null);

            String serviceName = salesInvoice != null
                    ? clean(salesInvoice.getSolutionName())
                    : null;

            String receiptBankName = isReceiptVoucher(voucher)
                    ? resolveReceiptBankName(ledger, voucher, otherEntriesCache)
                    : null;

            String particulars = buildParticulars(
                    ledger,
                    voucher,
                    narration,
                    serviceName,
                    receiptBankName,
                    otherEntriesCache
            );

            LedgerTransactionGstDetailsDto gstDetails = salesInvoice != null
                    ? buildGstDetails(salesInvoice)
                    : null;

            LedgerTransactionResponseDto mainRow = LedgerTransactionResponseDto.builder()
                    .entryId(entry.getId())

                    .voucherId(voucher != null ? voucher.getId() : null)
                    .voucherNumber(voucher != null ? voucher.getVoucherNumber() : null)
                    .voucherType(voucher != null ? voucher.getVoucherType() : null)
                    .voucherDate(voucher != null ? voucher.getVoucherDate() : null)

                    .sourceType(voucher != null ? voucher.getSourceType() : null)
                    .sourceId(voucher != null ? voucher.getSourceId() : null)
                    .status(voucher != null ? voucher.getStatus() : null)

                    .ledgerId(ledger.getId())
                    .ledgerName(ledger.getLedgerName())
                    .ledgerCode(ledger.getLedgerCode())

                    .debitAmount(debit)
                    .creditAmount(credit)

                    .runningBalanceAmount(absAmountForStatement(runningSignedBalance))
                    .runningBalanceType(balanceTypeForStatement(runningSignedBalance))

                    .narration(narration)
                    .particulars(particulars)
                    .serviceName(serviceName)
                    .bankName(receiptBankName)
                    .gstDetails(gstDetails)

                    .build();

            allRows.add(mainRow);

            allRows.addAll(
                    buildAdditionalReceiptRows(
                            ledger,
                            voucher,
                            otherEntriesCache
                    )
            );
        }

        List<LedgerTransactionResponseDto> filteredRows = allRows.stream()
                .filter(row -> matchesTransactionFilters(
                        row,
                        search,
                        voucherType,
                        sourceType,
                        entryType
                ))
                .collect(Collectors.toList());

        BigDecimal totalDebit = filteredRows.stream()
                .filter(row -> row.getRunningBalanceType() != null)
                .map(LedgerTransactionResponseDto::getDebitAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalCredit = filteredRows.stream()
                .filter(row -> row.getRunningBalanceType() != null)
                .map(LedgerTransactionResponseDto::getCreditAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);



        int totalElements = filteredRows.size();

        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / safeSize);

        int start = Math.min(safePage * safeSize, totalElements);
        int end = Math.min(start + safeSize, totalElements);

        List<LedgerTransactionResponseDto> pagedRows = filteredRows.subList(start, end);

        return LedgerStatementResponseDto.builder()
                .ledgerId(ledger.getId())
                .ledgerName(ledger.getLedgerName())
                .ledgerCode(ledger.getLedgerCode())
                .ledgerType(ledger.getLedgerType())

                .fromDate(fromDate)
                .toDate(toDate)

                .openingBalanceAmount(absAmountForStatement(openingSignedBalance))
                .openingBalanceType(balanceTypeForStatement(openingSignedBalance))

                .closingBalanceAmount(absAmountForStatement(runningSignedBalance))
                .closingBalanceType(balanceTypeForStatement(runningSignedBalance))

                .totalDebit(totalDebit)
                .totalCredit(totalCredit)

                .page(safePage + 1)
                .size(safeSize)
                .totalElements(totalElements)
                .totalPages(totalPages)

                .transactions(pagedRows)
                .build();
    }

    private Specification<LedgerMaster> buildSpecification(
            String search,
            LedgerType ledgerType,
            Long ledgerGroupId,
            LedgerGroupType ledgerGroupType,
            Long companyId,
            Long unitId,
            Boolean active
    ) {
        return (root, query, criteriaBuilder) -> {

            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.isFalse(root.get("deleted")));

            Join<LedgerMaster, LedgerGroup> groupJoin = root.join("ledgerGroup", JoinType.LEFT);
            Join<LedgerMaster, Company> companyJoin = root.join("company", JoinType.LEFT);
            Join<LedgerMaster, CompanyUnit> unitJoin = root.join("unit", JoinType.LEFT);
            Join<LedgerMaster, Contact> contactJoin = root.join("contact", JoinType.LEFT);

            if (search != null && !search.trim().isEmpty()) {
                String likeSearch = "%" + search.trim().toLowerCase() + "%";

                predicates.add(
                        criteriaBuilder.or(
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("ledgerName")), likeSearch),
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("ledgerCode")), likeSearch),
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("gstNo")), likeSearch),
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("panNo")), likeSearch),
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("bankName")), likeSearch),
                                criteriaBuilder.like(criteriaBuilder.lower(root.get("accountNumber")), likeSearch),

                                criteriaBuilder.like(criteriaBuilder.lower(groupJoin.get("name")), likeSearch),
                                criteriaBuilder.like(criteriaBuilder.lower(companyJoin.get("name")), likeSearch),
                                criteriaBuilder.like(criteriaBuilder.lower(unitJoin.get("unitName")), likeSearch),
                                criteriaBuilder.like(criteriaBuilder.lower(contactJoin.get("name")), likeSearch)
                        )
                );
            }

            if (ledgerType != null) {
                predicates.add(criteriaBuilder.equal(root.get("ledgerType"), ledgerType));
            }

            if (ledgerGroupId != null) {
                predicates.add(criteriaBuilder.equal(groupJoin.get("id"), ledgerGroupId));
            }

            if (ledgerGroupType != null) {
                predicates.add(criteriaBuilder.equal(groupJoin.get("groupType"), ledgerGroupType));
            }

            if (companyId != null) {
                predicates.add(criteriaBuilder.equal(companyJoin.get("id"), companyId));
            }

            if (unitId != null) {
                predicates.add(criteriaBuilder.equal(unitJoin.get("id"), unitId));
            }

            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Optional<Invoice> getSalesInvoice(
            AccountingVoucher voucher,
            Map<Long, Invoice> invoiceCache
    ) {
        if (!isSalesInvoiceVoucher(voucher)) {
            return Optional.empty();
        }

        if (voucher.getSourceId() == null || voucher.getSourceId() <= 0) {
            return Optional.empty();
        }

        Long invoiceId = voucher.getSourceId();

        if (invoiceCache.containsKey(invoiceId)) {
            return Optional.ofNullable(invoiceCache.get(invoiceId));
        }

        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        invoiceCache.put(invoiceId, invoice);

        return Optional.ofNullable(invoice);
    }

    private boolean isSalesInvoiceVoucher(AccountingVoucher voucher) {
        return isSourceType(
                voucher,
                "SALES_INVOICE",
                "TAX_INVOICE",
                "INVOICE"
        );
    }

    private boolean isReceiptVoucher(AccountingVoucher voucher) {
        return isSourceType(
                voucher,
                "RECEIPT",
                "PAYMENT_RECEIPT",
                "PAYMENT"
        );
    }

    private boolean isSourceType(AccountingVoucher voucher, String... acceptedNames) {
        if (voucher == null || voucher.getSourceType() == null) {
            return false;
        }

        String actual = voucher.getSourceType().name();

        for (String acceptedName : acceptedNames) {
            if (acceptedName.equalsIgnoreCase(actual)) {
                return true;
            }
        }

        return false;
    }

    private LedgerTransactionGstDetailsDto buildGstDetails(Invoice invoice) {
        if (invoice == null) {
            return null;
        }

        return LedgerTransactionGstDetailsDto.builder()
                .gstNo(clean(invoice.getBuyerGstin()))
                .subTotalExGst(moneyForStatement(invoice.getSubTotalExGst()))
                .totalGstAmount(moneyForStatement(invoice.getTotalGstAmount()))
                .cgstAmount(moneyForStatement(invoice.getCgstAmount()))
                .sgstAmount(moneyForStatement(invoice.getSgstAmount()))
                .igstAmount(moneyForStatement(invoice.getIgstAmount()))
                .grandTotal(moneyForStatement(invoice.getGrandTotal()))
                .build();
    }

    private String buildParticulars(
            LedgerMaster currentLedger,
            AccountingVoucher voucher,
            String narration,
            String serviceName,
            String receiptBankName,
            Map<Long, List<AccountingVoucherEntry>> otherEntriesCache
    ) {
        /*
         * RECEIPT VOUCHER LOGIC
         *
         * Example:
         * HDFC Bank Dr              54,000
         * TDS Receivable Dr          5,000
         *      To Microsoft Customer        59,000
         *
         * If current ledger = Microsoft Customer -> particulars = HDFC Bank
         * If current ledger = HDFC Bank          -> particulars = Microsoft
         * If current ledger = TDS Receivable     -> particulars = Microsoft
         */
        if (isReceiptVoucher(voucher)) {

            /*
             * If user opened Bank / Cash / Payment Gateway ledger,
             * particulars should show customer/company name.
             */
            if (currentLedger != null && isBankOrCashLedger(currentLedger)) {
                String partyName = resolvePartyLedgerName(
                        currentLedger,
                        voucher,
                        otherEntriesCache
                );



                if (partyName != null && !partyName.trim().isEmpty()) {
                    return partyName;
                }

                String oppositeLedger = resolveOppositeLedgerName(
                        currentLedger,
                        voucher,
                        otherEntriesCache
                );

                if (oppositeLedger != null && !oppositeLedger.trim().isEmpty()) {
                    return oppositeLedger;
                }

                return narration;
            }

            /*
             * If user opened TDS / GST ledger,
             * particulars should show customer/company name.
             */
            if (isTaxOrTdsLedger(currentLedger)) {
                String partyName = resolvePartyLedgerName(
                        currentLedger,
                        voucher,
                        otherEntriesCache
                );

                if (partyName != null && !partyName.trim().isEmpty()) {
                    return partyName;
                }

                String oppositeLedger = resolveOppositeLedgerName(
                        currentLedger,
                        voucher,
                        otherEntriesCache
                );

                if (oppositeLedger != null && !oppositeLedger.trim().isEmpty()) {
                    return oppositeLedger;
                }

                return narration;
            }

            /*
             * If user opened Customer ledger,
             * particulars should show bank name.
             */
            if (receiptBankName != null && !receiptBankName.trim().isEmpty()) {
                return receiptBankName;
            }
        }

        /*
         * SALES INVOICE / TAX LEDGER LOGIC
         *
         * Output GST / Input GST / TDS ledgers should show company name.
         */
        if (isTaxOrTdsLedger(currentLedger)) {
            String partyName = resolvePartyLedgerName(
                    currentLedger,
                    voucher,
                    otherEntriesCache
            );

            if (partyName != null && !partyName.trim().isEmpty()) {
                return partyName;
            }
        }

        /*
         * Sales invoice row should show service name.
         *
         * Example:
         * Customer ledger invoice row -> 12a Registration
         */
        if (serviceName != null && !serviceName.trim().isEmpty()) {
            return serviceName;
        }

        /*
         * Normal fallback:
         * show opposite ledger name.
         */
        String oppositeLedger = resolveOppositeLedgerName(
                currentLedger,
                voucher,
                otherEntriesCache
        );

        if (oppositeLedger != null && !oppositeLedger.trim().isEmpty()) {
            return oppositeLedger;
        }

        return narration;
    }


    private boolean isTaxOrTdsLedger(LedgerMaster ledger) {
        if (ledger == null || ledger.getLedgerType() == null) {
            return false;
        }

        return ledger.getLedgerType() == LedgerType.TDS_RECEIVABLE
                || ledger.getLedgerType() == LedgerType.TDS_PAYABLE

                || ledger.getLedgerType() == LedgerType.OUTPUT_CGST
                || ledger.getLedgerType() == LedgerType.OUTPUT_SGST
                || ledger.getLedgerType() == LedgerType.OUTPUT_IGST

                || ledger.getLedgerType() == LedgerType.INPUT_CGST
                || ledger.getLedgerType() == LedgerType.INPUT_SGST
                || ledger.getLedgerType() == LedgerType.INPUT_IGST;
    }

    private String resolvePartyLedgerName(
            LedgerMaster currentLedger,
            AccountingVoucher voucher,
            Map<Long, List<AccountingVoucherEntry>> otherEntriesCache
    ) {
        List<AccountingVoucherEntry> otherEntries = getOtherVoucherEntries(
                voucher,
                currentLedger != null ? currentLedger.getId() : null,
                otherEntriesCache
        );

        if (otherEntries == null || otherEntries.isEmpty()) {
            return null;
        }

        Optional<LedgerMaster> customerLedger = otherEntries.stream()
                .map(AccountingVoucherEntry::getLedger)
                .filter(Objects::nonNull)
                .filter(ledger ->
                        ledger.getLedgerType() == LedgerType.CUSTOMER
                                || ledger.getLedgerType() == LedgerType.CUSTOMER_ADVANCE
                )
                .findFirst();

        if (customerLedger.isPresent()) {
            return displayPartyLedgerName(customerLedger.get());
        }

        Optional<LedgerMaster> vendorLedger = otherEntries.stream()
                .map(AccountingVoucherEntry::getLedger)
                .filter(Objects::nonNull)
                .filter(ledger ->
                        ledger.getLedgerType() == LedgerType.SUPPLIER
                                || ledger.getLedgerType() == LedgerType.VENDOR
                                || ledger.getLedgerType() == LedgerType.VENDOR_PAYABLE
                )
                .findFirst();

        if (vendorLedger.isPresent()) {
            return displayPartyLedgerName(vendorLedger.get());
        }

        Optional<LedgerMaster> companyMappedLedger = otherEntries.stream()
                .map(AccountingVoucherEntry::getLedger)
                .filter(Objects::nonNull)
                .filter(ledger -> ledger.getCompany() != null)
                .findFirst();

        return companyMappedLedger
                .map(this::displayPartyLedgerName)
                .orElse(null);
    }

    private String displayPartyLedgerName(LedgerMaster ledger) {
        if (ledger == null) {
            return null;
        }

        if (ledger.getCompany() != null
                && ledger.getCompany().getName() != null
                && !ledger.getCompany().getName().trim().isEmpty()) {
            return ledger.getCompany().getName().trim();
        }

        if (ledger.getLedgerName() != null && !ledger.getLedgerName().trim().isEmpty()) {
            return ledger.getLedgerName().trim();
        }

        return null;
    }

    private String resolveReceiptBankName(
            LedgerMaster currentLedger,
            AccountingVoucher voucher,
            Map<Long, List<AccountingVoucherEntry>> otherEntriesCache
    ) {
        if (currentLedger != null && isBankOrCashLedger(currentLedger)) {
            return displayBankLedgerName(currentLedger);
        }

        List<AccountingVoucherEntry> otherEntries = getOtherVoucherEntries(
                voucher,
                currentLedger != null ? currentLedger.getId() : null,
                otherEntriesCache
        );

        for (AccountingVoucherEntry otherEntry : otherEntries) {
            LedgerMaster otherLedger = otherEntry.getLedger();

            if (otherLedger != null && isBankOrCashLedger(otherLedger)) {
                return displayBankLedgerName(otherLedger);
            }
        }

        return null;
    }

    private String resolveOppositeLedgerName(
            LedgerMaster currentLedger,
            AccountingVoucher voucher,
            Map<Long, List<AccountingVoucherEntry>> otherEntriesCache
    ) {
        List<AccountingVoucherEntry> otherEntries = getOtherVoucherEntries(
                voucher,
                currentLedger != null ? currentLedger.getId() : null,
                otherEntriesCache
        );

        if (otherEntries.isEmpty()) {
            return null;
        }

        return otherEntries.stream()
                .map(AccountingVoucherEntry::getLedger)
                .filter(Objects::nonNull)
                .map(LedgerMaster::getLedgerName)
                .filter(name -> name != null && !name.trim().isEmpty())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private List<AccountingVoucherEntry> getOtherVoucherEntries(
            AccountingVoucher voucher,
            Long currentLedgerId,
            Map<Long, List<AccountingVoucherEntry>> otherEntriesCache
    ) {
        if (voucher == null || voucher.getId() == null || currentLedgerId == null) {
            return new ArrayList<>();
        }

        Long voucherId = voucher.getId();

        if (otherEntriesCache.containsKey(voucherId)) {
            return otherEntriesCache.get(voucherId);
        }

        List<AccountingVoucherEntry> otherEntries =
                accountingVoucherEntryRepository.findOtherEntriesByVoucherId(
                        voucherId,
                        currentLedgerId
                );

        otherEntriesCache.put(voucherId, otherEntries);

        return otherEntries;
    }

    private boolean isBankOrCashLedger(LedgerMaster ledger) {
        if (ledger == null || ledger.getLedgerType() == null) {
            return false;
        }

        return ledger.getLedgerType() == LedgerType.BANK
                || ledger.getLedgerType() == LedgerType.PAYMENT_GATEWAY
                || ledger.getLedgerType() == LedgerType.CASH;
    }


    private String displayBankLedgerName(LedgerMaster ledger) {
        if (ledger == null) {
            return null;
        }

        if (ledger.getBankName() != null && !ledger.getBankName().trim().isEmpty()) {
            return ledger.getBankName().trim();
        }

        return ledger.getLedgerName();
    }

    private boolean matchesTransactionFilters(
            LedgerTransactionResponseDto row,
            String search,
            String voucherType,
            String sourceType,
            String entryType
    ) {
        if (row == null) {
            return false;
        }

        if (voucherType != null && !voucherType.trim().isEmpty()) {
            if (row.getVoucherType() == null
                    || !row.getVoucherType().name().equalsIgnoreCase(voucherType.trim())) {
                return false;
            }
        }

        if (sourceType != null && !sourceType.trim().isEmpty()) {
            if (row.getSourceType() == null
                    || !row.getSourceType().name().equalsIgnoreCase(sourceType.trim())) {
                return false;
            }
        }

        if (entryType != null && !entryType.trim().isEmpty()) {
            String normalizedEntryType = entryType.trim().toUpperCase();

            if ("DEBIT".equals(normalizedEntryType)
                    && row.getDebitAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }

            if ("CREDIT".equals(normalizedEntryType)
                    && row.getCreditAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }
        }

        if (search != null && !search.trim().isEmpty()) {
            String value = search.trim().toLowerCase();

            return containsIgnoreCase(row.getVoucherNumber(), value)
                    || containsIgnoreCase(row.getLedgerName(), value)
                    || containsIgnoreCase(row.getLedgerCode(), value)
                    || containsIgnoreCase(row.getNarration(), value)
//                    || containsIgnoreCase(row.getpa(), value)
//                    || containsIgnoreCase(row.getServiceName(), value)
//                    || containsIgnoreCase(row.getBankName(), value)
                    || enumContainsIgnoreCase(row.getVoucherType(), value)
                    || enumContainsIgnoreCase(row.getSourceType(), value)
                    || enumContainsIgnoreCase(row.getStatus(), value);
        }

        return true;
    }

    private boolean containsIgnoreCase(String actual, String search) {
        return actual != null && actual.toLowerCase().contains(search);
    }

    private boolean enumContainsIgnoreCase(Enum<?> actual, String search) {
        return actual != null && actual.name().toLowerCase().contains(search);
    }


    @Override
    @Transactional(readOnly = true)
    public List<LedgerMasterResponseDto> getReceiptLedgers() {

        List<LedgerType> allowedTypes = List.of(
                LedgerType.BANK,
                LedgerType.CASH,
                LedgerType.PAYMENT_GATEWAY
        );

        return ledgerMasterRepository
                .findByDeletedFalseAndActiveTrueAndLedgerTypeInOrderByLedgerNameAsc(allowedTypes)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    private List<LedgerTransactionResponseDto> buildAdditionalReceiptRows(
            LedgerMaster currentLedger,
            AccountingVoucher voucher,
            Map<Long, List<AccountingVoucherEntry>> otherEntriesCache
    ) {
        if (currentLedger == null || currentLedger.getLedgerType() == null) {
            return new ArrayList<>();
        }

        if (!isReceiptVoucher(voucher)) {
            return new ArrayList<>();
        }

        /*
         * Extra TDS row should be shown only when user opens
         * Customer / Customer Advance ledger.
         *
         * Example:
         * Actual voucher saved in DB:
         *
         * HDFC Bank Dr             54,000
         * TDS Receivable Dr         5,000
         *      To Microsoft              59,000
         *
         * In Microsoft customer ledger response, show extra display row:
         *
         * Microsoft Cr              5,000
         * Particulars = TDS Receivable
         */
        if (currentLedger.getLedgerType() != LedgerType.CUSTOMER
                && currentLedger.getLedgerType() != LedgerType.CUSTOMER_ADVANCE) {
            return new ArrayList<>();
        }

        List<AccountingVoucherEntry> otherEntries = getOtherVoucherEntries(
                voucher,
                currentLedger.getId(),
                otherEntriesCache
        );

        if (otherEntries == null || otherEntries.isEmpty()) {
            return new ArrayList<>();
        }

        return otherEntries.stream()
                .filter(entry -> entry != null && entry.getLedger() != null)
                .filter(entry -> entry.getLedger().getLedgerType() == LedgerType.TDS_RECEIVABLE)
                .filter(entry -> moneyForStatement(entry.getDebitAmount()).compareTo(BigDecimal.ZERO) > 0)
                .map(entry -> {
                    LedgerMaster tdsLedger = entry.getLedger();
                    BigDecimal tdsAmount = moneyForStatement(entry.getDebitAmount());

                    return LedgerTransactionResponseDto.builder()
                            .entryId(entry.getId())

                            .voucherId(voucher.getId())
                            .voucherNumber(voucher.getVoucherNumber())
                            .voucherType(voucher.getVoucherType())
                            .voucherDate(voucher.getVoucherDate())

                            .sourceType(voucher.getSourceType())
                            .sourceId(voucher.getSourceId())
                            .status(voucher.getStatus())

                            /*
                             * IMPORTANT:
                             * Since user opened customer ledger,
                             * keep ledger as current customer ledger.
                             */
                            .ledgerId(currentLedger.getId())
                            .ledgerName(currentLedger.getLedgerName())
                            .ledgerCode(currentLedger.getLedgerCode())

                            /*
                             * In customer ledger, TDS should be shown as credit.
                             */
                            .debitAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                            .creditAmount(tdsAmount)

                            /*
                             * Do not calculate running balance for this display row,
                             * otherwise customer balance will double-count TDS.
                             */
                            .runningBalanceAmount(null)
                            .runningBalanceType(null)

                            /*
                             * Customer ledger particulars should show TDS Receivable.
                             */
                            .particulars(tdsLedger.getLedgerName())
                            .serviceName(null)
                            .bankName(null)

                            .narration(
                                    entry.getNarration() != null && !entry.getNarration().trim().isEmpty()
                                            ? entry.getNarration()
                                            : "TDS deducted by customer"
                            )

                            .gstDetails(null)
                            .build();
                })
                .collect(Collectors.toList());
    }



}