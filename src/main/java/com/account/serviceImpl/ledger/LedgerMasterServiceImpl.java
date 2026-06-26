package com.account.serviceImpl.ledger;

import com.account.domain.Company;
import com.account.domain.CompanyUnit;
import com.account.domain.Contact;
import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.LedgerGroup;
import com.account.domain.ledger.LedgerMaster;
import com.account.domain.ledger.LedgerType;
import com.account.dto.ledger.LedgerMasterRequestDto;
import com.account.dto.ledger.LedgerMasterResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.CompanyRepository;
import com.account.repository.CompanyUnitRepository;
import com.account.repository.ContactRepository;
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
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerMasterServiceImpl implements LedgerMasterService {

    private final LedgerMasterRepository ledgerMasterRepository;
    private final LedgerGroupRepository ledgerGroupRepository;
    private final CompanyRepository companyRepository;
    private final CompanyUnitRepository companyUnitRepository;
    private final ContactRepository contactRepository;

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

        LedgerGroup ledgerGroup = getLedgerGroup(request.getLedgerGroupId());

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

    @Override
    @Transactional
    public LedgerMasterResponseDto updateLedger(Long id, LedgerMasterRequestDto request) {

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

        LedgerGroup ledgerGroup = getLedgerGroup(request.getLedgerGroupId());

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
                active
        );

        return ledgerMasterRepository.findAll(specification, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerMasterResponseDto> getActiveLedgers() {

        return ledgerMasterRepository.findByDeletedFalseAndActiveTrueOrderByLedgerNameAsc()
                .stream()
                .map(this::mapToResponse)
                .toList();
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

        return LedgerMasterResponseDto.builder()
                .id(ledger.getId())
                .ledgerName(ledger.getLedgerName())
                .ledgerCode(ledger.getLedgerCode())
                .ledgerType(ledger.getLedgerType())

                .ledgerGroupId(ledger.getLedgerGroup() != null ? ledger.getLedgerGroup().getId() : null)
                .ledgerGroupName(ledger.getLedgerGroup() != null ? ledger.getLedgerGroup().getName() : null)
                .ledgerGroupType(ledger.getLedgerGroup() != null ? ledger.getLedgerGroup().getGroupType() : null)

                .companyId(ledger.getCompany() != null ? ledger.getCompany().getId() : null)
                .companyName(ledger.getCompany() != null ? ledger.getCompany().getName() : null)

                .unitId(ledger.getUnit() != null ? ledger.getUnit().getId() : null)
                .unitName(ledger.getUnit() != null ? ledger.getUnit().getUnitName() : null)

                .contactId(ledger.getContact() != null ? ledger.getContact().getId() : null)
                .contactName(ledger.getContact() != null ? ledger.getContact().getName() : null)

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
}