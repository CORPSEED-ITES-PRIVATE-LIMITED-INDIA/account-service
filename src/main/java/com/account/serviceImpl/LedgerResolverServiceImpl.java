package com.account.serviceImpl;

import com.account.domain.User;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.LedgerGroup;
import com.account.domain.ledger.LedgerGroupType;
import com.account.domain.ledger.LedgerMaster;
import com.account.domain.ledger.LedgerType;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.ledger.LedgerGroupRepository;
import com.account.repository.ledger.LedgerMasterRepository;
import com.account.service.LedgerResolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LedgerResolverServiceImpl implements LedgerResolverService {

    private final LedgerMasterRepository ledgerMasterRepository;
    private final LedgerGroupRepository ledgerGroupRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerMaster systemLedger(
            LedgerType type,
            LedgerGroupType groupType,
            String name,
            DebitCredit balanceType,
            User by
    ) {
        Optional<LedgerMaster> existing =
                ledgerMasterRepository.findByLedgerTypeAndDeletedFalse(type);

        if (existing.isPresent()) {
            return existing.get();
        }

        LedgerGroup group = ledgerGroupRepository
                .findByGroupTypeAndDeletedFalse(groupType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        groupType + " ledger group not found",
                        groupType + "_GROUP_NOT_FOUND"
                ));

        LedgerMaster ledger = new LedgerMaster();
        ledger.setLedgerName(name);
        ledger.setLedgerCode(nextCode(type.name()));
        ledger.setLedgerType(type);
        ledger.setLedgerGroup(group);

        ledger.setOpeningBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        ledger.setOpeningBalanceType(balanceType);
        ledger.setCurrentBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        ledger.setCurrentBalanceType(balanceType);

        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);

        if (by != null) {
            ledger.setCreatedBy(by);
            ledger.setUpdatedBy(by);
        }

        return ledgerMasterRepository.save(ledger);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerMaster customerLedger(UnbilledInvoice unbilled, User by) {

        if (unbilled == null) {
            throw new ValidationException(
                    "Unbilled invoice is required to resolve customer ledger",
                    "ERR_UNBILLED_REQUIRED_FOR_LEDGER",
                    "unbilled"
            );
        }

        Company company = unbilled.getCompany();
        CompanyUnit unit = unbilled.getUnit();

        if (company == null || company.getId() == null) {
            throw new ValidationException(
                    "Company is required to resolve customer ledger",
                    "ERR_COMPANY_REQUIRED_FOR_LEDGER",
                    "companyId"
            );
        }

        String companyName =
                company.getName() != null && !company.getName().trim().isEmpty()
                        ? company.getName().trim()
                        : "Company-" + company.getId();

        List<LedgerMaster> existing =
                ledgerMasterRepository.findByCompanyIdAndLedgerTypeInAndDeletedFalse(
                        company.getId(),
                        List.of(LedgerType.CUSTOMER, LedgerType.CUSTOMER_ADVANCE)
                );

        LedgerMaster ledger;

        if (existing != null && !existing.isEmpty()) {
            ledger = existing.stream()
                    .filter(l -> l.getLedgerName() != null
                            && l.getLedgerName().trim().equalsIgnoreCase(companyName))
                    .findFirst()
                    .orElseGet(() -> existing.stream()
                            .min(Comparator.comparing(LedgerMaster::getId))
                            .get());
        } else {
            LedgerGroup debtors = ledgerGroupRepository
                    .findByGroupTypeAndDeletedFalse(LedgerGroupType.SUNDRY_DEBTORS)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Sundry Debtors ledger group not found",
                            "SUNDRY_DEBTORS_GROUP_NOT_FOUND"
                    ));

            ledger = new LedgerMaster();
            ledger.setLedgerCode(nextCode("CUST"));
            ledger.setLedgerName(companyName);
            ledger.setLedgerType(LedgerType.CUSTOMER);
            ledger.setLedgerGroup(debtors);

            ledger.setOpeningBalance(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
            ledger.setOpeningBalanceType(DebitCredit.DEBIT);
            ledger.setCurrentBalance(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
            ledger.setCurrentBalanceType(DebitCredit.DEBIT);

            if (by != null) {
                ledger.setCreatedBy(by);
            }
        }

        ledger.setCompany(company);
        ledger.setUnit(unit);
        ledger.setContact(unbilled.getContact());
        ledger.setGstNo(unit != null ? unit.getGstNo() : null);
        ledger.setPanNo(company.getPanNo());
        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);

        if (by != null) {
            ledger.setUpdatedBy(by);
        }

        return ledgerMasterRepository.save(ledger);
    }

    private String nextCode(String prefix) {
        String safePrefix = prefix == null || prefix.trim().isEmpty()
                ? "SYS"
                : prefix.trim().replaceAll("[^A-Za-z0-9]", "-").toUpperCase();

        long sequence = ledgerMasterRepository.count() + 1;
        String code;

        do {
            code = String.format("LED-%s-%06d", safePrefix, sequence++);
        } while (ledgerMasterRepository.existsByLedgerCodeIgnoreCase(code));

        return code;
    }
}