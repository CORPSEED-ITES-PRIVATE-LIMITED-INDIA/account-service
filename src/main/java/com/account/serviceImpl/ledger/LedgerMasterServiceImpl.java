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
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerMasterServiceImpl implements LedgerMasterService {

    private static final String LEDGER_STATEMENT_VERSION =
            "2026-08-18-VENDOR-PAYMENT-TDS-SPLIT-V5";

    private static final int MONEY_SCALE = 3;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

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
    public LedgerMasterResponseDto createLedger(
            LedgerMasterRequestDto request
    ) {

        log.info(
                "Creating ledger. ledgerType={}, companyId={}, unitId={}",
                request != null ? request.getLedgerType() : null,
                request != null ? request.getCompanyId() : null,
                request != null ? request.getUnitId() : null
        );

        validateRequest(request);

        Company company = getCompany(request.getCompanyId());
        CompanyUnit unit = getUnit(request.getUnitId());
        Contact contact = getContact(request.getContactId());

        validateLedgerBusinessRules(
                request,
                company,
                unit
        );

        String ledgerName = resolveLedgerName(
                request,
                company,
                unit,
                null
        );

        validateLedgerUniquenessForCreate(
                request,
                company,
                unit,
                ledgerName
        );

        LedgerGroup ledgerGroup =
                resolveLedgerGroupForLedgerType(
                        request.getLedgerType(),
                        request.getLedgerGroupId()
                );

        LedgerMaster ledger = buildLedgerMaster(
                request,
                ledgerName,
                ledgerGroup,
                company,
                unit,
                contact
        );

        LedgerMaster saved =
                ledgerMasterRepository.save(ledger);

        log.info(
                "Ledger created successfully. ledgerId={}, ledgerCode={}, ledgerName={}, companyId={}, unitId={}",
                saved.getId(),
                saved.getLedgerCode(),
                saved.getLedgerName(),
                company != null ? company.getId() : null,
                unit != null ? unit.getId() : null
        );

        return mapToResponse(saved);
    }
    private void validateAdminUserForLedgerEdit(Long userId) {

        log.debug("Validating admin permission for ledger edit. userId={}", userId);

        if (userId == null) {
            log.warn("Ledger edit permission validation failed. User ID is null");
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
            log.warn("Ledger edit permission validation failed. User is inactive. userId={}", userId);
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
            log.warn("Ledger edit permission validation failed. ADMIN role missing. userId={}", userId);
            throw new ValidationException(
                    "Only ADMIN role can edit ledger",
                    "ERR_LEDGER_EDIT_ADMIN_ONLY",
                    "userId"
            );
        }

        log.debug("Ledger edit permission validation passed. userId={}", userId);
    }


    @Override
    @Transactional
    public LedgerMasterResponseDto updateLedger(
            Long id,
            LedgerMasterRequestDto request,
            Long userId
    ) {

        log.info(
                "Updating ledger. ledgerId={}, userId={}",
                id,
                userId
        );

        validateAdminUserForLedgerEdit(userId);
        validateRequest(request);

        LedgerMaster ledger =
                ledgerMasterRepository
                        .findByIdAndDeletedFalse(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Ledger not found with ID: " + id,
                                        "LEDGER_NOT_FOUND"
                                )
                        );

        Company company = getCompany(request.getCompanyId());
        CompanyUnit unit = getUnit(request.getUnitId());
        Contact contact = getContact(request.getContactId());

        validateLedgerBusinessRules(
                request,
                company,
                unit
        );

        String ledgerName = resolveLedgerName(
                request,
                company,
                unit,
                id
        );

        validateLedgerUniquenessForUpdate(
                id,
                request,
                company,
                unit,
                ledgerName
        );

        LedgerGroup ledgerGroup =
                resolveLedgerGroupForLedgerType(
                        request.getLedgerType(),
                        request.getLedgerGroupId()
                );

        ledger.setLedgerName(ledgerName);
        ledger.setLedgerType(request.getLedgerType());
        ledger.setLedgerGroup(ledgerGroup);

        ledger.setCompany(company);
        ledger.setUnit(unit);
        ledger.setContact(contact);

        applyLedgerTaxDetails(
                ledger,
                request,
                company,
                unit
        );

        ledger.setBankName(clean(request.getBankName()));
        ledger.setAccountHolderName(
                clean(request.getAccountHolderName())
        );
        ledger.setAccountNumber(
                clean(request.getAccountNumber())
        );
        ledger.setIfscCode(clean(request.getIfscCode()));
        ledger.setBranchName(clean(request.getBranchName()));

        ledger.setOpeningBalance(
                moneyForStatement(request.getOpeningBalance())
        );

        ledger.setOpeningBalanceType(
                resolveOpeningBalanceType(request)
        );

        if (request.getActive() != null) {
            ledger.setActive(request.getActive());
        }

        LedgerMaster saved =
                ledgerMasterRepository.save(ledger);

        log.info(
                "Ledger updated successfully. ledgerId={}, ledgerCode={}, ledgerName={}, companyId={}, unitId={}",
                saved.getId(),
                saved.getLedgerCode(),
                saved.getLedgerName(),
                company != null ? company.getId() : null,
                unit != null ? unit.getId() : null
        );

        return mapToResponse(saved);
    }


    private boolean isCustomerLedgerType(
            LedgerType ledgerType
    ) {
        return ledgerType == LedgerType.CUSTOMER
                || ledgerType == LedgerType.CUSTOMER_ADVANCE;
    }

    private List<LedgerType> customerLedgerTypes() {
        return List.of(
                LedgerType.CUSTOMER,
                LedgerType.CUSTOMER_ADVANCE
        );
    }

    private DebitCredit resolveOpeningBalanceType(
            LedgerMasterRequestDto request
    ) {
        BigDecimal openingBalance =
                safeMoney(request.getOpeningBalance());

        if (openingBalance.compareTo(BigDecimal.ZERO) == 0
                && request.getOpeningBalanceType() == null) {
            return DebitCredit.DEBIT;
        }

        return request.getOpeningBalanceType();
    }

    private String resolveLedgerName(
            LedgerMasterRequestDto request,
            Company company,
            CompanyUnit unit,
            Long existingLedgerId
    ) {
        /*
         * Non-customer or manually created customer ledger:
         * use the entered ledger name.
         */
        if (!isCustomerLedgerType(request.getLedgerType())
                || company == null
                || unit == null) {

            return normalizeName(request.getLedgerName());
        }

        /*
         * Linked customer ledger:
         * generate name from company and unit.
         */
        String companyName =
                company.getName() != null
                        && !company.getName().trim().isEmpty()
                        ? company.getName().trim()
                        : "Company-" + company.getId();

        String unitName =
                unit.getUnitName() != null
                        && !unit.getUnitName().trim().isEmpty()
                        ? unit.getUnitName().trim()
                        : "Unit-" + unit.getId();

        String baseName = normalizeName(
                companyName + " - " + unitName
        );

        boolean duplicateName;

        if (existingLedgerId == null) {
            duplicateName =
                    ledgerMasterRepository
                            .existsByLedgerNameIgnoreCase(baseName);
        } else {
            duplicateName =
                    ledgerMasterRepository
                            .existsByLedgerNameIgnoreCaseAndIdNot(
                                    baseName,
                                    existingLedgerId
                            );
        }

        if (duplicateName) {
            return normalizeName(
                    baseName + " - Unit-" + unit.getId()
            );
        }

        return baseName;
    }

    private void validateLedgerUniquenessForCreate(
            LedgerMasterRequestDto request,
            Company company,
            CompanyUnit unit,
            String ledgerName
    ) {
        /*
         * Company-unit duplicate validation applies only
         * to linked customer ledgers.
         */
        if (isCustomerLedgerType(request.getLedgerType())
                && company != null
                && unit != null) {

            boolean exists =
                    ledgerMasterRepository
                            .existsByCompanyIdAndUnitIdAndLedgerTypeInAndDeletedFalse(
                                    company.getId(),
                                    unit.getId(),
                                    customerLedgerTypes()
                            );

            if (exists) {
                throw new ValidationException(
                        "Customer ledger already exists for company "
                                + company.getName()
                                + " and unit "
                                + unit.getUnitName(),
                        "ERR_CUSTOMER_UNIT_LEDGER_DUPLICATE",
                        "unitId"
                );
            }

            return;
        }

        /*
         * Manual customer and other ledgers are checked by name.
         */
        if (ledgerMasterRepository
                .existsByLedgerNameIgnoreCase(ledgerName)) {

            throw new ValidationException(
                    "Ledger already exists with name: " + ledgerName,
                    "ERR_LEDGER_DUPLICATE",
                    "ledgerName"
            );
        }
    }

    private void validateLedgerUniquenessForUpdate(
            Long ledgerId,
            LedgerMasterRequestDto request,
            Company company,
            CompanyUnit unit,
            String ledgerName
    ) {
        if (isCustomerLedgerType(request.getLedgerType())
                && company != null
                && unit != null) {

            boolean exists =
                    ledgerMasterRepository
                            .existsByCompanyIdAndUnitIdAndLedgerTypeInAndDeletedFalseAndIdNot(
                                    company.getId(),
                                    unit.getId(),
                                    customerLedgerTypes(),
                                    ledgerId
                            );

            if (exists) {
                throw new ValidationException(
                        "Customer ledger already exists for company "
                                + company.getName()
                                + " and unit "
                                + unit.getUnitName(),
                        "ERR_CUSTOMER_UNIT_LEDGER_DUPLICATE",
                        "unitId"
                );
            }

            return;
        }

        if (ledgerMasterRepository
                .existsByLedgerNameIgnoreCaseAndIdNot(
                        ledgerName,
                        ledgerId
                )) {

            throw new ValidationException(
                    "Ledger already exists with name: " + ledgerName,
                    "ERR_LEDGER_DUPLICATE",
                    "ledgerName"
            );
        }
    }


    @Override
    @Transactional(readOnly = true)
    public LedgerMasterResponseDto getLedgerById(Long id) {

        log.debug("Fetching ledger by ID. ledgerId={}", id);

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

        log.info("Fetching ledgers. search={}, ledgerType={}, ledgerGroupId={}, ledgerGroupType={}, companyId={}, unitId={}, active={}, page={}, size={}",
                search, ledgerType, ledgerGroupId, ledgerGroupType, companyId, unitId, active, page, size);

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
                ? BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING)
                : value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal toSignedBalanceForStatement(BigDecimal amount, DebitCredit type) {
        BigDecimal value = moneyForStatement(amount);

        if (type == DebitCredit.CREDIT) {
            return value.negate().setScale(MONEY_SCALE, MONEY_ROUNDING);
        }

        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal absAmountForStatement(BigDecimal signedAmount) {
        return moneyForStatement(signedAmount).abs().setScale(MONEY_SCALE, MONEY_ROUNDING);
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

        log.info("Deleting ledger. ledgerId={}", id);

        LedgerMaster ledger = ledgerMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger not found with ID: " + id,
                        "LEDGER_NOT_FOUND"
                ));

        if (ledger.isSystemCreated()) {
            log.warn("Ledger delete failed. System-created ledger cannot be deleted. ledgerId={}", id);
            throw new ValidationException(
                    "System-created ledger cannot be deleted",
                    "ERR_SYSTEM_LEDGER_DELETE_NOT_ALLOWED",
                    "id"
            );
        }

        ledger.setDeleted(true);
        ledger.setActive(false);

        ledgerMasterRepository.save(ledger);

        log.info("Ledger deleted successfully. ledgerId={}", id);
    }

    private void validateRequest(LedgerMasterRequestDto request) {

        log.debug("Validating ledger request");

        if (request == null) {
            throw new ValidationException(
                    "Request body is required",
                    "ERR_REQUEST_REQUIRED"
            );
        }

        if (request.getLedgerType() == null) {
            throw new ValidationException(
                    "Ledger type is required",
                    "ERR_LEDGER_TYPE_REQUIRED",
                    "ledgerType"
            );
        }

        /*
         * Customer ledger name is generated automatically as:
         *
         * Company Name - Unit Name
         *
         * Therefore ledgerName is mandatory only for non-customer ledgers.
         */
        if (!isCustomerLedgerType(request.getLedgerType())
                && (request.getLedgerName() == null
                || request.getLedgerName().trim().isEmpty())) {

            throw new ValidationException(
                    "Ledger name is required",
                    "ERR_LEDGER_NAME_REQUIRED",
                    "ledgerName"
            );
        }

        /*
         * ledgerGroupId is optional.
         * When it is not supplied, the group is resolved from LedgerType.
         */
        if (request.getLedgerGroupId() != null
                && request.getLedgerGroupId() <= 0) {

            throw new ValidationException(
                    "Ledger group ID must be greater than zero",
                    "ERR_LEDGER_GROUP_INVALID",
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

        if (ledgerType == LedgerType.BANK
                && (request.getBankName() == null
                || request.getBankName().trim().isEmpty())) {

            throw new ValidationException(
                    "Bank name is required for bank ledger",
                    "ERR_BANK_NAME_REQUIRED",
                    "bankName"
            );
        }

        /*
         * Customer ledger supports two modes:
         *
         * 1. Linked customer:
         *    companyId and unitId must both be provided.
         *
         * 2. Manual customer:
         *    companyId and unitId may both be null.
         */
        if (isCustomerLedgerType(ledgerType)) {
            boolean companyProvided = company != null;
            boolean unitProvided = unit != null;

            if (companyProvided != unitProvided) {
                throw new ValidationException(
                        "Company and company unit must either both be selected or both be empty",
                        "ERR_CUSTOMER_COMPANY_UNIT_REQUIRED_TOGETHER",
                        companyProvided ? "unitId" : "companyId"
                );
            }
        }

        if (unit != null && company == null) {
            throw new ValidationException(
                    "Company is required when a company unit is selected",
                    "ERR_COMPANY_REQUIRED_FOR_UNIT",
                    "companyId"
            );
        }

        if (unit != null) {
            if (unit.getCompany() == null
                    || unit.getCompany().getId() == null) {

                throw new ValidationException(
                        "Selected unit is not linked with a company",
                        "ERR_UNIT_COMPANY_LINK_MISSING",
                        "unitId"
                );
            }

            if (!unit.getCompany().getId().equals(company.getId())) {
                throw new ValidationException(
                        "Selected unit does not belong to selected company",
                        "ERR_UNIT_COMPANY_MISMATCH",
                        "unitId"
                );
            }
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

        log.debug("Generated ledger code. ledgerType={}, ledgerCode={}", ledgerType, code);

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
        return moneyForStatement(value);
    }

    private LedgerMasterResponseDto mapToResponse(LedgerMaster ledger) {

        if (ledger == null) {
            return null;
        }

        Company company = ledger.getCompany();
        CompanyUnit unit = ledger.getUnit();
        Contact contact = ledger.getContact();
        LedgerGroup ledgerGroup = ledger.getLedgerGroup();

        /*
         * Accounting balance:
         * DEBIT  = positive
         * CREDIT = negative
         *
         * BANK, CASH and PAYMENT_GATEWAY ledgers are displayed in
         * passbook-style (money IN = Credit, money OUT = Debit).
         * All other ledger types use standard accounting display.
         */
        BigDecimal displayOpeningSignedBalance = displaySignedBalanceForLedger(
                ledger,
                toSignedBalanceForStatement(
                        ledger.getOpeningBalance(),
                        ledger.getOpeningBalanceType()
                )
        );

        BigDecimal displayCurrentSignedBalance = displaySignedBalanceForLedger(
                ledger,
                toSignedBalanceForStatement(
                        ledger.getCurrentBalance(),
                        ledger.getCurrentBalanceType()
                )
        );

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
                .gstRegistrationType(
                        unit != null ? unit.getGstRegistrationType() : null
                )

                .gstNo(ledger.getGstNo())
                .panNo(ledger.getPanNo())

                .bankName(ledger.getBankName())
                .accountHolderName(ledger.getAccountHolderName())
                .accountNumber(ledger.getAccountNumber())
                .ifscCode(ledger.getIfscCode())
                .branchName(ledger.getBranchName())

                /*
                 * DISPLAY opening balance
                 */
                .openingBalance(absAmountForStatement(displayOpeningSignedBalance))
                .openingBalanceType(balanceTypeForStatement(displayOpeningSignedBalance))

                /*
                 * DISPLAY current balance
                 */
                .currentBalance(absAmountForStatement(displayCurrentSignedBalance))
                .currentBalanceType(balanceTypeForStatement(displayCurrentSignedBalance))

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

        BigDecimal openingBalance =
                moneyForStatement(request.getOpeningBalance());

        DebitCredit openingBalanceType =
                resolveOpeningBalanceType(request);

        LedgerMaster ledger = new LedgerMaster();

        ledger.setLedgerName(ledgerName);
        ledger.setLedgerCode(
                generateLedgerCode(request.getLedgerType())
        );

        ledger.setLedgerType(request.getLedgerType());
        ledger.setLedgerGroup(ledgerGroup);

        ledger.setCompany(company);
        ledger.setUnit(unit);
        ledger.setContact(contact);

        applyLedgerTaxDetails(
                ledger,
                request,
                company,
                unit
        );

        ledger.setBankName(clean(request.getBankName()));
        ledger.setAccountHolderName(
                clean(request.getAccountHolderName())
        );
        ledger.setAccountNumber(
                clean(request.getAccountNumber())
        );
        ledger.setIfscCode(clean(request.getIfscCode()));
        ledger.setBranchName(clean(request.getBranchName()));

        ledger.setOpeningBalance(openingBalance);
        ledger.setOpeningBalanceType(openingBalanceType);

        /*
         * On creation, current balance starts from opening balance.
         */
        ledger.setCurrentBalance(openingBalance);
        ledger.setCurrentBalanceType(openingBalanceType);

        ledger.setSystemCreated(false);
        ledger.setActive(
                request.getActive() == null
                        || request.getActive()
        );
        ledger.setDeleted(false);

        return ledger;
    }

    private void applyLedgerTaxDetails(
            LedgerMaster ledger,
            LedgerMasterRequestDto request,
            Company company,
            CompanyUnit unit
    ) {
        /*
         * Linked customer ledger:
         * obtain tax details from company and unit.
         */
        if (isCustomerLedgerType(request.getLedgerType())
                && company != null
                && unit != null) {

            ledger.setGstNo(clean(unit.getGstNo()));
            ledger.setPanNo(clean(company.getPanNo()));
            return;
        }

        /*
         * Manual customer or other ledger:
         * use manually entered tax details.
         */
        ledger.setGstNo(clean(request.getGstNo()));
        ledger.setPanNo(clean(request.getPanNo()));
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
                        log.info("Activating inactive ledger group. groupId={}, groupType={}",
                                existingGroup.getId(),
                                existingGroup.getGroupType());
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

                    LedgerGroup savedGroup = ledgerGroupRepository.save(ledgerGroup);

                    log.info("Created default ledger group. groupId={}, groupType={}",
                            savedGroup.getId(),
                            savedGroup.getGroupType());

                    return savedGroup;
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
        log.info(
                "[LEDGER-STATEMENT-START] version={} | ledgerId={} | fromDate={} | toDate={} | "
                        + "search={} | voucherType={} | sourceType={} | entryType={} | page={} | size={}",
                LEDGER_STATEMENT_VERSION,
                ledgerId,
                fromDate,
                toDate,
                search,
                voucherType,
                sourceType,
                entryType,
                page,
                size
        );

        if (ledgerId == null || ledgerId <= 0) {
            log.warn("Ledger transaction fetch failed. Invalid ledgerId={}", ledgerId);
            throw new ValidationException(
                    "Ledger ID is required",
                    "ERR_LEDGER_ID_REQUIRED",
                    "ledgerId"
            );
        }

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            log.warn("Ledger transaction fetch failed. Invalid date range. fromDate={}, toDate={}", fromDate, toDate);
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
                    .setScale(MONEY_SCALE, MONEY_ROUNDING);
        }

        List<AccountingVoucherEntry> entries =
                accountingVoucherEntryRepository.findLedgerEntriesForStatement(
                        ledgerId,
                        fromDate,
                        toDate,
                        VoucherStatus.POSTED
                );

        log.info(
                "[LEDGER-STATEMENT-OPENING] version={} | ledgerId={} | ledgerName={} | "
                        + "ledgerType={} | accountingOpeningSigned={} | entryCount={}",
                LEDGER_STATEMENT_VERSION,
                ledger.getId(),
                ledger.getLedgerName(),
                ledger.getLedgerType(),
                openingSignedBalance,
                entries != null ? entries.size() : 0
        );

        BigDecimal runningSignedBalance = openingSignedBalance;

        List<LedgerTransactionResponseDto> allRows = new ArrayList<>();

        Map<Long, Invoice> invoiceCache = new HashMap<>();
        Map<Long, List<AccountingVoucherEntry>> otherEntriesCache = new HashMap<>();

        for (AccountingVoucherEntry entry : entries) {

            AccountingVoucher voucher = entry.getVoucher();

            BigDecimal accountingDebit = moneyForStatement(entry.getDebitAmount());
            BigDecimal accountingCredit = moneyForStatement(entry.getCreditAmount());

            /*
             * Previous balance is required for display-only adjustment.
             *
             * Example:
             * Actual accounting credit = 5,086.21
             * Bank display credit      = 5,000.00
             * TDS display credit       = 86.21
             *
             * First row balance should show 5,000 CR, not 5,086.21 CR.
             */
            BigDecimal previousRunningSignedBalance = runningSignedBalance;

            /*
             * Actual running balance must always use real accounting values.
             * Do not change actual balance calculation.
             */
            runningSignedBalance = runningSignedBalance
                    .add(accountingDebit)
                    .subtract(accountingCredit)
                    .setScale(MONEY_SCALE, MONEY_ROUNDING);

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

            /*
             * Display the actual accounting side for every ledger type.
             *
             * Example for a bank-to-bank CONTRA:
             *   Dr destination bank -> Debit column
             *   Cr source bank      -> Credit column
             */
            BigDecimal displayDebit = displayDebitForLedger(
                    ledger,
                    accountingDebit,
                    accountingCredit
            );

            BigDecimal displayCredit = displayCreditForLedger(
                    ledger,
                    accountingDebit,
                    accountingCredit
            );

            boolean customerReceiptCreditRowWithTdsSplit = false;
            boolean vendorPaymentDebitRowWithTdsSplit = false;

            /*
             * The accounting voucher contains one customer credit equal to
             * Bank + TDS. For the customer-ledger UI, split that credit into:
             *
             * 1. Bank/Cash/Payment Gateway row
             * 2. TDS Receivable row
             *
             * This is display-only. The posted voucher remains unchanged.
             */
            if (isCustomerReceiptCreditRow(
                    ledger,
                    voucher,
                    accountingDebit,
                    accountingCredit
            )) {
                BigDecimal bankAmount = getReceiptBankAmountForCustomerLedger(
                        ledger,
                        voucher,
                        otherEntriesCache
                );

                if (bankAmount.compareTo(BigDecimal.ZERO) > 0
                        && bankAmount.compareTo(accountingCredit) < 0) {
                    displayDebit = zeroMoneyForStatement();
                    displayCredit = bankAmount;
                    particulars = receiptBankName;
                    customerReceiptCreditRowWithTdsSplit = true;
                }
            }

            /*
             * A procurement vendor PAYMENT voucher contains one vendor debit
             * equal to Bank + TDS:
             *
             *   Dr Vendor                  gross settlement
             *       Cr Bank/Cash           actual bank payment
             *       Cr TDS Payable         tax withheld
             *
             * The vendor statement is easier to understand when that gross
             * debit is displayed as two rows: Bank and TDS Payable. This is
             * display-only; the posted accounting voucher is not changed.
             */
            if (isVendorPaymentDebitRow(
                    ledger,
                    voucher,
                    accountingDebit,
                    accountingCredit
            )) {
                BigDecimal bankAmount = getVendorPaymentBankAmount(
                        ledger,
                        voucher,
                        otherEntriesCache
                );

                if (bankAmount.compareTo(BigDecimal.ZERO) > 0
                        && bankAmount.compareTo(accountingDebit) < 0) {
                    displayDebit = bankAmount;
                    displayCredit = zeroMoneyForStatement();
                    receiptBankName = resolvePaymentBankName(
                            ledger,
                            voucher,
                            otherEntriesCache
                    );
                    particulars = receiptBankName;
                    vendorPaymentDebitRowWithTdsSplit = true;
                }
            }

            BigDecimal rowDisplaySignedBalance = runningSignedBalance;

            if (customerReceiptCreditRowWithTdsSplit
                    || vendorPaymentDebitRowWithTdsSplit) {
                rowDisplaySignedBalance = previousRunningSignedBalance
                        .add(displayDebit)
                        .subtract(displayCredit)
                        .setScale(MONEY_SCALE, MONEY_ROUNDING);
            }

            BigDecimal displayRunningSignedBalance =
                    displaySignedBalanceForLedger(ledger, rowDisplaySignedBalance);

            log.info(
                    "[LEDGER-ENTRY-CALCULATION] version={} | ledgerId={} | voucherId={} | "
                            + "voucherNumber={} | voucherType={} | accountingDebit={} | "
                            + "accountingCredit={} | signedBalanceBefore={} | signedBalanceAfter={} | "
                            + "displayDebit={} | displayCredit={} | displayBalance={} | "
                            + "displayBalanceType={} | particulars={}",
                    LEDGER_STATEMENT_VERSION,
                    ledger.getId(),
                    voucher != null ? voucher.getId() : null,
                    voucher != null ? voucher.getVoucherNumber() : null,
                    voucher != null ? voucher.getVoucherType() : null,
                    accountingDebit,
                    accountingCredit,
                    previousRunningSignedBalance,
                    runningSignedBalance,
                    displayDebit,
                    displayCredit,
                    absAmountForStatement(displayRunningSignedBalance),
                    balanceTypeForStatement(displayRunningSignedBalance),
                    particulars
            );

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

                    .debitAmount(displayDebit)
                    .creditAmount(displayCredit)

                    .runningBalanceAmount(absAmountForStatement(displayRunningSignedBalance))
                    .runningBalanceType(balanceTypeForStatement(displayRunningSignedBalance))

                    .narration(narration)
                    .particulars(particulars)
                    .serviceName(serviceName)
                    .bankName(receiptBankName)
                    .gstDetails(gstDetails)

                    .build();

            allRows.add(mainRow);

            if (customerReceiptCreditRowWithTdsSplit) {
                allRows.addAll(
                        buildAdditionalReceiptRows(
                                ledger,
                                voucher,
                                otherEntriesCache,
                                rowDisplaySignedBalance
                        )
                );
            }

            if (vendorPaymentDebitRowWithTdsSplit) {
                allRows.addAll(
                        buildAdditionalVendorPaymentRows(
                                ledger,
                                voucher,
                                otherEntriesCache,
                                rowDisplaySignedBalance
                        )
                );
            }
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

        /*
         * Customer receipts and procurement vendor payments may produce
         * display-only Bank and TDS rows. Their sum always equals the actual
         * posted party-ledger entry.
         */
        BigDecimal totalDebit = filteredRows.stream()
                .map(LedgerTransactionResponseDto::getDebitAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, MONEY_ROUNDING);

        BigDecimal totalCredit = filteredRows.stream()
                .map(LedgerTransactionResponseDto::getCreditAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, MONEY_ROUNDING);

        int totalElements = filteredRows.size();

        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / safeSize);

        int start = Math.min(safePage * safeSize, totalElements);
        int end = Math.min(start + safeSize, totalElements);

        List<LedgerTransactionResponseDto> pagedRows = filteredRows.subList(start, end);

        BigDecimal displayOpeningSignedBalance =
                displaySignedBalanceForLedger(ledger, openingSignedBalance);

        BigDecimal displayClosingSignedBalance =
                displaySignedBalanceForLedger(ledger, runningSignedBalance);

        log.info(
                "[LEDGER-STATEMENT-SUMMARY] version={} | ledgerId={} | ledgerName={} | "
                        + "openingBalance={} {} | totalDebit={} | totalCredit={} | "
                        + "closingBalance={} {} | totalElements={} | totalPages={} | returnedRows={}",
                LEDGER_STATEMENT_VERSION,
                ledgerId,
                ledger.getLedgerName(),
                absAmountForStatement(displayOpeningSignedBalance),
                balanceTypeForStatement(displayOpeningSignedBalance),
                totalDebit,
                totalCredit,
                absAmountForStatement(displayClosingSignedBalance),
                balanceTypeForStatement(displayClosingSignedBalance),
                totalElements,
                totalPages,
                pagedRows.size()
        );

        return LedgerStatementResponseDto.builder()
                .ledgerId(ledger.getId())
                .ledgerName(ledger.getLedgerName())
                .ledgerCode(ledger.getLedgerCode())
                .ledgerType(ledger.getLedgerType())

                .fromDate(fromDate)
                .toDate(toDate)

                .openingBalanceAmount(absAmountForStatement(displayOpeningSignedBalance))
                .openingBalanceType(balanceTypeForStatement(displayOpeningSignedBalance))

                .closingBalanceAmount(absAmountForStatement(displayClosingSignedBalance))
                .closingBalanceType(balanceTypeForStatement(displayClosingSignedBalance))

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
            log.debug("Sales invoice loaded from cache. invoiceId={}", invoiceId);
            return Optional.ofNullable(invoiceCache.get(invoiceId));
        }

        log.debug("Fetching sales invoice for ledger statement. invoiceId={}", invoiceId);
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
                "PAYMENT",
                /*
                 * Government-fee Step 3A: Dr receiving bank, Cr customer.
                 * Without this, HDFC Bank ledger shows wrong particulars and
                 * Mr Beast customer ledger misses the credit entry display.
                 */
                "PROJECT_EXPENSE_CLIENT_RECEIPT"
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
             * If user opened Customer / Customer Advance ledger, return all
             * counter-ledger names in one row. Example:
             *
             * HDFC Bank, TDS Receivable
             */
            if (currentLedger != null
                    && (currentLedger.getLedgerType() == LedgerType.CUSTOMER
                    || currentLedger.getLedgerType() == LedgerType.CUSTOMER_ADVANCE)) {

                String counterLedgers = resolveOppositeLedgerName(
                        currentLedger,
                        voucher,
                        otherEntriesCache
                );

                if (counterLedgers != null && !counterLedgers.trim().isEmpty()) {
                    return counterLedgers;
                }
            }

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
         * GOVERNMENT FEE RECEIVABLE / PAYABLE LEDGER LOGIC
         *
         * When the user opens:
         *   - GOVERNMENT_FEE_RECEIVABLE ledger → show client company name
         *     (i.e., "which client's project is this advance for?")
         *   - GOVERNMENT_FEE_PAYABLE ledger    → show client company name
         *     (i.e., "which client's government fee is this liability for?")
         *
         * The client name is stored as a snapshot on the voucher itself
         * (voucher.clientCompanyName / voucher.clientUn    itName), so it
         * remains correct even if master data changes later.
         *
         * Falls back to project number if name is missing, and finally
         * to the opposite ledger name if neither is available.
         */
        if (currentLedger != null
                && isGovernmentFeeLedger(currentLedger)
                && voucher != null) {

            String clientName = firstNonBlank(
                    voucher.getClientCompanyName(),
                    voucher.getClientUnitName(),
                    voucher.getProjectName(),
                    voucher.getProjectNo()
            );

            if (clientName != null && !clientName.trim().isEmpty()) {
                return clientName.trim();
            }
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

        /*
         * Customer ledgers are unit-wise, so prefer the ledger name:
         * Microsoft - Delhi
         * Microsoft - Mumbai
         */
        if (ledger.getLedgerName() != null
                && !ledger.getLedgerName().trim().isEmpty()) {
            return ledger.getLedgerName().trim();
        }

        if (ledger.getCompany() != null
                && ledger.getCompany().getName() != null
                && !ledger.getCompany().getName().trim().isEmpty()) {
            return ledger.getCompany().getName().trim();
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

        log.debug("Fetching other voucher entries. voucherId={}, currentLedgerId={}", voucherId, currentLedgerId);

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

    /**
     * Returns true for ledger types that represent government-fee accounting.
     * When the user opens these ledgers, particulars should show the client
     * company/project name (from the voucher snapshot), not the opposite
     * ledger name.
     */
    private boolean isGovernmentFeeLedger(LedgerMaster ledger) {
        if (ledger == null || ledger.getLedgerType() == null) {
            return false;
        }
        return ledger.getLedgerType() == LedgerType.GOVERNMENT_FEE_RECEIVABLE
                || ledger.getLedgerType() == LedgerType.GOVERNMENT_FEE_PAYABLE
                || ledger.getLedgerType() == LedgerType.GOVERNMENT_FEE_CLIENT_ADVANCE
                || ledger.getLedgerType() == LedgerType.GOVERNMENT_FEE_EXPENSE;
    }

    /**
     * Returns the first non-null, non-blank string from the given candidates.
     * Used to build particulars/narration from voucher snapshot fields.
     */
    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return null;
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

        log.debug("Fetching receipt ledgers");

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


    private boolean isCustomerLedgerForStatement(LedgerMaster ledger) {
        return ledger != null
                && ledger.getLedgerType() != null
                && (ledger.getLedgerType() == LedgerType.CUSTOMER
                || ledger.getLedgerType() == LedgerType.CUSTOMER_ADVANCE);
    }

    private boolean isVendorLedgerForStatement(LedgerMaster ledger) {
        return ledger != null
                && ledger.getLedgerType() != null
                && (ledger.getLedgerType() == LedgerType.VENDOR
                || ledger.getLedgerType() == LedgerType.SUPPLIER
                || ledger.getLedgerType() == LedgerType.VENDOR_PAYABLE);
    }

    private boolean isVendorPaymentDebitRow(
            LedgerMaster ledger,
            AccountingVoucher voucher,
            BigDecimal debit,
            BigDecimal credit
    ) {
        return isVendorLedgerForStatement(ledger)
                && voucher != null
                && voucher.getVoucherType() == VoucherType.PAYMENT
                && voucher.getSourceType()
                == VoucherSourceType.PROCUREMENT_VENDOR_PAYMENT
                && moneyForStatement(debit).compareTo(BigDecimal.ZERO) > 0
                && moneyForStatement(credit).compareTo(BigDecimal.ZERO) == 0;
    }

    private BigDecimal getVendorPaymentBankAmount(
            LedgerMaster currentLedger,
            AccountingVoucher voucher,
            Map<Long, List<AccountingVoucherEntry>> otherEntriesCache
    ) {
        BigDecimal bankAmount = getOtherVoucherEntries(
                voucher,
                currentLedger.getId(),
                otherEntriesCache
        ).stream()
                .filter(entry -> entry != null && entry.getLedger() != null)
                .filter(entry -> isBankOrCashLedger(entry.getLedger()))
                .map(AccountingVoucherEntry::getCreditAmount)
                .filter(Objects::nonNull)
                .map(this::moneyForStatement)
                .reduce(zeroMoneyForStatement(), BigDecimal::add)
                .setScale(MONEY_SCALE, MONEY_ROUNDING);

        log.info(
                "[VENDOR-PAYMENT-BANK-SPLIT] voucherId={} | vendorLedgerId={} | bankAmount={}",
                voucher != null ? voucher.getId() : null,
                currentLedger != null ? currentLedger.getId() : null,
                bankAmount
        );

        return bankAmount;
    }

    private String resolvePaymentBankName(
            LedgerMaster currentLedger,
            AccountingVoucher voucher,
            Map<Long, List<AccountingVoucherEntry>> otherEntriesCache
    ) {
        return getOtherVoucherEntries(
                voucher,
                currentLedger.getId(),
                otherEntriesCache
        ).stream()
                .filter(entry -> entry != null && entry.getLedger() != null)
                .map(AccountingVoucherEntry::getLedger)
                .filter(this::isBankOrCashLedger)
                .map(this::displayBankLedgerName)
                .filter(Objects::nonNull)
                .filter(name -> !name.trim().isEmpty())
                .findFirst()
                .orElse("Bank/Cash");
    }

    private boolean isCustomerReceiptCreditRow(
            LedgerMaster ledger,
            AccountingVoucher voucher,
            BigDecimal debit,
            BigDecimal credit
    ) {
        return isCustomerLedgerForStatement(ledger)
                && isReceiptVoucher(voucher)
                && moneyForStatement(debit).compareTo(BigDecimal.ZERO) == 0
                && moneyForStatement(credit).compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal getReceiptBankAmountForCustomerLedger(
            LedgerMaster currentLedger,
            AccountingVoucher voucher,
            Map<Long, List<AccountingVoucherEntry>> otherEntriesCache
    ) {
        List<AccountingVoucherEntry> otherEntries = getOtherVoucherEntries(
                voucher,
                currentLedger.getId(),
                otherEntriesCache
        );

        BigDecimal bankAmount = otherEntries.stream()
                .filter(entry -> entry != null && entry.getLedger() != null)
                .filter(entry -> isBankOrCashLedger(entry.getLedger()))
                .map(AccountingVoucherEntry::getDebitAmount)
                .filter(Objects::nonNull)
                .map(this::moneyForStatement)
                .reduce(zeroMoneyForStatement(), BigDecimal::add)
                .setScale(MONEY_SCALE, MONEY_ROUNDING);

        log.info(
                "[CUSTOMER-RECEIPT-BANK-SPLIT] voucherId={} | customerLedgerId={} | bankAmount={}",
                voucher != null ? voucher.getId() : null,
                currentLedger != null ? currentLedger.getId() : null,
                bankAmount
        );

        return bankAmount;
    }

    private List<LedgerTransactionResponseDto> buildAdditionalReceiptRows(
            LedgerMaster currentLedger,
            AccountingVoucher voucher,
            Map<Long, List<AccountingVoucherEntry>> otherEntriesCache,
            BigDecimal baseRunningSignedBalanceAfterMainRow
    ) {
        if (!isCustomerLedgerForStatement(currentLedger) || !isReceiptVoucher(voucher)) {
            return new ArrayList<>();
        }

        List<AccountingVoucherEntry> otherEntries = getOtherVoucherEntries(
                voucher,
                currentLedger.getId(),
                otherEntriesCache
        );

        BigDecimal runningAfterMainRow = baseRunningSignedBalanceAfterMainRow == null
                ? zeroMoneyForStatement()
                : moneyForStatement(baseRunningSignedBalanceAfterMainRow);

        List<LedgerTransactionResponseDto> rows = new ArrayList<>();

        for (AccountingVoucherEntry counterEntry : otherEntries) {
            if (counterEntry == null || counterEntry.getLedger() == null) {
                continue;
            }

            LedgerMaster tdsLedger = counterEntry.getLedger();

            if (tdsLedger.getLedgerType() != LedgerType.TDS_RECEIVABLE) {
                continue;
            }

            BigDecimal tdsAmount = moneyForStatement(counterEntry.getDebitAmount());

            if (tdsAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            runningAfterMainRow = runningAfterMainRow
                    .subtract(tdsAmount)
                    .setScale(MONEY_SCALE, MONEY_ROUNDING);

            BigDecimal displayRunningSignedBalance =
                    displaySignedBalanceForLedger(currentLedger, runningAfterMainRow);

            log.info(
                    "[CUSTOMER-RECEIPT-TDS-SPLIT] voucherId={} | voucherNumber={} | "
                            + "customerLedgerId={} | tdsLedgerId={} | tdsAmount={} | runningBalance={} {}",
                    voucher.getId(),
                    voucher.getVoucherNumber(),
                    currentLedger.getId(),
                    tdsLedger.getId(),
                    tdsAmount,
                    absAmountForStatement(displayRunningSignedBalance),
                    balanceTypeForStatement(displayRunningSignedBalance)
            );

            rows.add(
                    LedgerTransactionResponseDto.builder()
                            .entryId(counterEntry.getId())
                            .voucherId(voucher.getId())
                            .voucherNumber(voucher.getVoucherNumber())
                            .voucherType(voucher.getVoucherType())
                            .voucherDate(voucher.getVoucherDate())
                            .sourceType(voucher.getSourceType())
                            .sourceId(voucher.getSourceId())
                            .status(voucher.getStatus())
                            .ledgerId(currentLedger.getId())
                            .ledgerName(currentLedger.getLedgerName())
                            .ledgerCode(currentLedger.getLedgerCode())
                            .debitAmount(zeroMoneyForStatement())
                            .creditAmount(tdsAmount)
                            .runningBalanceAmount(absAmountForStatement(displayRunningSignedBalance))
                            .runningBalanceType(balanceTypeForStatement(displayRunningSignedBalance))
                            .particulars(tdsLedger.getLedgerName())
                            .serviceName(null)
                            .bankName(null)
                            .narration(
                                    counterEntry.getNarration() != null
                                            && !counterEntry.getNarration().trim().isEmpty()
                                            ? counterEntry.getNarration()
                                            : "TDS deducted by customer"
                            )
                            .gstDetails(null)
                            .build()
            );
        }

        return rows;
    }

    private List<LedgerTransactionResponseDto> buildAdditionalVendorPaymentRows(
            LedgerMaster currentLedger,
            AccountingVoucher voucher,
            Map<Long, List<AccountingVoucherEntry>> otherEntriesCache,
            BigDecimal baseRunningSignedBalanceAfterMainRow
    ) {
        if (!isVendorLedgerForStatement(currentLedger)
                || voucher == null
                || voucher.getVoucherType() != VoucherType.PAYMENT
                || voucher.getSourceType()
                != VoucherSourceType.PROCUREMENT_VENDOR_PAYMENT) {
            return new ArrayList<>();
        }

        List<AccountingVoucherEntry> otherEntries = getOtherVoucherEntries(
                voucher,
                currentLedger.getId(),
                otherEntriesCache
        );

        BigDecimal runningAfterMainRow =
                baseRunningSignedBalanceAfterMainRow == null
                        ? zeroMoneyForStatement()
                        : moneyForStatement(baseRunningSignedBalanceAfterMainRow);

        List<LedgerTransactionResponseDto> rows = new ArrayList<>();

        for (AccountingVoucherEntry counterEntry : otherEntries) {
            if (counterEntry == null || counterEntry.getLedger() == null) {
                continue;
            }

            LedgerMaster tdsLedger = counterEntry.getLedger();

            if (tdsLedger.getLedgerType() != LedgerType.TDS_PAYABLE) {
                continue;
            }

            BigDecimal tdsAmount =
                    moneyForStatement(counterEntry.getCreditAmount());

            if (tdsAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            /*
             * The original vendor row is a debit for Bank + TDS. The main
             * display row already applied the Bank debit, so apply the TDS
             * debit here to arrive at the original accounting balance.
             */
            runningAfterMainRow = runningAfterMainRow
                    .add(tdsAmount)
                    .setScale(MONEY_SCALE, MONEY_ROUNDING);

            BigDecimal displayRunningSignedBalance =
                    displaySignedBalanceForLedger(
                            currentLedger,
                            runningAfterMainRow
                    );

            log.info(
                    "[VENDOR-PAYMENT-TDS-SPLIT] voucherId={} | voucherNumber={} | "
                            + "vendorLedgerId={} | tdsLedgerId={} | tdsAmount={} | "
                            + "runningBalance={} {}",
                    voucher.getId(),
                    voucher.getVoucherNumber(),
                    currentLedger.getId(),
                    tdsLedger.getId(),
                    tdsAmount,
                    absAmountForStatement(displayRunningSignedBalance),
                    balanceTypeForStatement(displayRunningSignedBalance)
            );

            rows.add(
                    LedgerTransactionResponseDto.builder()
                            .entryId(counterEntry.getId())
                            .voucherId(voucher.getId())
                            .voucherNumber(voucher.getVoucherNumber())
                            .voucherType(voucher.getVoucherType())
                            .voucherDate(voucher.getVoucherDate())
                            .sourceType(voucher.getSourceType())
                            .sourceId(voucher.getSourceId())
                            .status(voucher.getStatus())

                            /*
                             * This is a display row inside the vendor
                             * statement, so retain the vendor ledger identity.
                             */
                            .ledgerId(currentLedger.getId())
                            .ledgerName(currentLedger.getLedgerName())
                            .ledgerCode(currentLedger.getLedgerCode())

                            .debitAmount(tdsAmount)
                            .creditAmount(zeroMoneyForStatement())
                            .runningBalanceAmount(
                                    absAmountForStatement(
                                            displayRunningSignedBalance
                                    )
                            )
                            .runningBalanceType(
                                    balanceTypeForStatement(
                                            displayRunningSignedBalance
                                    )
                            )
                            .particulars(tdsLedger.getLedgerName())
                            .serviceName(null)
                            .bankName(null)
                            .narration(
                                    counterEntry.getNarration() != null
                                            && !counterEntry.getNarration()
                                            .trim().isEmpty()
                                            ? counterEntry.getNarration()
                                            : "TDS deducted from vendor payment"
                            )
                            .gstDetails(null)
                            .build()
            );
        }

        return rows;
    }

    private BigDecimal zeroMoneyForStatement() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }


    /**
     * ================================================================
     * PASSBOOK-STYLE DISPLAY FOR BANK / CASH / PAYMENT_GATEWAY LEDGERS
     * ================================================================
     * In company double-entry books, BANK is an ASSET ledger:
     *   Money IN  = Debit  (asset increases)
     *   Money OUT = Credit (asset decreases)
     *
     * In a bank passbook / statement (what users expect to see):
     *   Money IN  = Credit (deposit)
     *   Money OUT = Debit  (withdrawal)
     *
     * These three helpers swap Dr/Cr display for BANK, CASH and
     * PAYMENT_GATEWAY ledgers ONLY. Voucher entries, currentBalance,
     * and trial balance are completely unchanged.
     *
     * All other ledger types (CUSTOMER, VENDOR, GOVERNMENT_FEE_PAYABLE,
     * SERVICE_INCOME, etc.) remain on standard accounting display.
     * ================================================================
     */
    private boolean isPassbookStyleLedger(LedgerMaster ledger) {
        if (ledger == null || ledger.getLedgerType() == null) {
            return false;
        }
        return ledger.getLedgerType() == LedgerType.BANK
                || ledger.getLedgerType() == LedgerType.CASH
                || ledger.getLedgerType() == LedgerType.PAYMENT_GATEWAY;
    }

    private BigDecimal displayDebitForLedger(
            LedgerMaster ledger,
            BigDecimal accountingDebit,
            BigDecimal accountingCredit
    ) {
        if (isPassbookStyleLedger(ledger)) {
            // Passbook: accounting Credit (money out) shown as Debit (withdrawal)
            return moneyForStatement(accountingCredit);
        }
        return moneyForStatement(accountingDebit);
    }

    private BigDecimal displayCreditForLedger(
            LedgerMaster ledger,
            BigDecimal accountingDebit,
            BigDecimal accountingCredit
    ) {
        if (isPassbookStyleLedger(ledger)) {
            // Passbook: accounting Debit (money in) shown as Credit (deposit)
            return moneyForStatement(accountingDebit);
        }
        return moneyForStatement(accountingCredit);
    }

    private BigDecimal displaySignedBalanceForLedger(
            LedgerMaster ledger,
            BigDecimal accountingSignedBalance
    ) {
        if (isPassbookStyleLedger(ledger)) {
            // Negate so DR balance shows as CR (you have money in the bank)
            // and CR balance shows as DR (overdraft)
            return moneyForStatement(accountingSignedBalance).negate();
        }
        return moneyForStatement(accountingSignedBalance);
    }



}