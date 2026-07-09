package com.account.config;

import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.LedgerGroup;
import com.account.domain.ledger.LedgerGroupType;
import com.account.domain.ledger.LedgerMaster;
import com.account.domain.ledger.LedgerType;
import com.account.repository.ledger.LedgerGroupRepository;
import com.account.repository.ledger.LedgerMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DefaultTaxLedgerSeeder implements ApplicationRunner {

    private final LedgerMasterRepository ledgerMasterRepository;
    private final LedgerGroupRepository ledgerGroupRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LedgerGroup dutiesAndTaxesGroup = getOrCreateDutiesAndTaxesGroup();

        List<DefaultLedger> defaultLedgers = List.of(
                new DefaultLedger(
                        "TDS Receivable",
                        "LED-TDS-RECEIVABLE",
                        LedgerType.TDS_RECEIVABLE,
                        DebitCredit.DEBIT
                ),
                new DefaultLedger(
                        "TDS Payable",
                        "LED-TDS-PAYABLE",
                        LedgerType.TDS_PAYABLE,
                        DebitCredit.CREDIT
                ),
                new DefaultLedger(
                        "Output CGST",
                        "LED-OUTPUT-CGST",
                        LedgerType.OUTPUT_CGST,
                        DebitCredit.CREDIT
                ),
                new DefaultLedger(
                        "Output SGST",
                        "LED-OUTPUT-SGST",
                        LedgerType.OUTPUT_SGST,
                        DebitCredit.CREDIT
                ),
                new DefaultLedger(
                        "Output IGST",
                        "LED-OUTPUT-IGST",
                        LedgerType.OUTPUT_IGST,
                        DebitCredit.CREDIT
                ),
                new DefaultLedger(
                        "Input CGST",
                        "LED-INPUT-CGST",
                        LedgerType.INPUT_CGST,
                        DebitCredit.DEBIT
                ),
                new DefaultLedger(
                        "Input SGST",
                        "LED-INPUT-SGST",
                        LedgerType.INPUT_SGST,
                        DebitCredit.DEBIT
                ),
                new DefaultLedger(
                        "Input IGST",
                        "LED-INPUT-IGST",
                        LedgerType.INPUT_IGST,
                        DebitCredit.DEBIT
                )
        );

        defaultLedgers.forEach(defaultLedger ->
                createOrUpdateSystemLedger(defaultLedger, dutiesAndTaxesGroup)
        );
    }

    private LedgerGroup getOrCreateDutiesAndTaxesGroup() {
        Optional<LedgerGroup> existingGroupOptional =
                ledgerGroupRepository.findByGroupType(LedgerGroupType.DUTIES_AND_TAXES);

        if (existingGroupOptional.isPresent()) {
            LedgerGroup existingGroup = existingGroupOptional.get();

            existingGroup.setName("Duties And Taxes");
            existingGroup.setGroupType(LedgerGroupType.DUTIES_AND_TAXES);
            existingGroup.setDescription("System-created default ledger group for GST and TDS ledgers");
            existingGroup.setSystemDefault(true);
            existingGroup.setActive(true);
            existingGroup.setDeleted(false);

            return ledgerGroupRepository.save(existingGroup);
        }

        LedgerGroup ledgerGroup = LedgerGroup.builder()
                .name("Duties And Taxes")
                .groupType(LedgerGroupType.DUTIES_AND_TAXES)
                .description("System-created default ledger group for GST and TDS ledgers")
                .systemDefault(true)
                .active(true)
                .deleted(false)
                .build();

        return ledgerGroupRepository.save(ledgerGroup);
    }

    private void createOrUpdateSystemLedger(DefaultLedger defaultLedger, LedgerGroup ledgerGroup) {
        Optional<LedgerMaster> existingLedgerOptional =
                ledgerMasterRepository.findByLedgerNameIgnoreCase(defaultLedger.ledgerName());

        if (existingLedgerOptional.isEmpty()) {
            existingLedgerOptional =
                    ledgerMasterRepository.findByLedgerCodeIgnoreCase(defaultLedger.ledgerCode());
        }

        LedgerMaster ledger = existingLedgerOptional.orElseGet(LedgerMaster::new);

        ledger.setLedgerName(defaultLedger.ledgerName());
        ledger.setLedgerCode(defaultLedger.ledgerCode());
        ledger.setLedgerType(defaultLedger.ledgerType());
        ledger.setLedgerGroup(ledgerGroup);

        ledger.setOpeningBalance(BigDecimal.ZERO);
        ledger.setOpeningBalanceType(defaultLedger.normalBalanceType());

        ledger.setCurrentBalance(BigDecimal.ZERO);
        ledger.setCurrentBalanceType(defaultLedger.normalBalanceType());

        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);

        ledgerMasterRepository.save(ledger);
    }

    private record DefaultLedger(
            String ledgerName,
            String ledgerCode,
            LedgerType ledgerType,
            DebitCredit normalBalanceType
    ) {
    }
}