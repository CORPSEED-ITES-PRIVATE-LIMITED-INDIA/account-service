package com.account.repository.ledger;

import com.account.domain.ledger.LedgerMaster;
import com.account.domain.ledger.LedgerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface LedgerMasterRepository extends JpaRepository<LedgerMaster, Long>, JpaSpecificationExecutor<LedgerMaster> {

    Optional<LedgerMaster> findByIdAndDeletedFalse(Long id);

    boolean existsByLedgerNameIgnoreCase(String ledgerName);

    boolean existsByLedgerNameIgnoreCaseAndIdNot(String ledgerName, Long id);

    boolean existsByLedgerCodeIgnoreCase(String ledgerCode);

    List<LedgerMaster> findByDeletedFalseAndActiveTrueOrderByLedgerNameAsc();

    Optional<LedgerMaster> findByCompany_IdAndUnit_IdAndLedgerTypeAndDeletedFalse(
            Long companyId,
            Long unitId,
            LedgerType ledgerType
    );

    Optional<LedgerMaster> findByCompany_IdAndLedgerTypeAndUnitIsNullAndDeletedFalse(
            Long companyId,
            LedgerType ledgerType
    );
}