package com.account.repository.ledger;

import com.account.domain.ledger.LedgerMaster;
import com.account.domain.ledger.LedgerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerMasterRepository extends JpaRepository<LedgerMaster, Long>, JpaSpecificationExecutor<LedgerMaster> {

    Optional<LedgerMaster> findByIdAndDeletedFalse(Long id);

    boolean existsByLedgerNameIgnoreCase(String ledgerName);

    boolean existsByLedgerNameIgnoreCaseAndIdNot(String ledgerName, Long id);

    boolean existsByLedgerCodeIgnoreCase(String ledgerCode);

    List<LedgerMaster> findByDeletedFalseAndActiveTrueOrderByLedgerNameAsc();

    @Query("""
            SELECT lm
            FROM LedgerMaster lm
            WHERE lm.company.id = :companyId
              AND lm.unit.id = :unitId
              AND lm.ledgerType = :ledgerType
              AND lm.deleted = false
            """)
    Optional<LedgerMaster> findByCompanyIdAndUnitIdAndLedgerTypeAndDeletedFalse(
            @Param("companyId") Long companyId,
            @Param("unitId") Long unitId,
            @Param("ledgerType") LedgerType ledgerType
    );


    Optional<LedgerMaster> findByLedgerTypeAndDeletedFalse(LedgerType ledgerType);

    Optional<LedgerMaster> findByCompanyIdAndLedgerTypeAndDeletedFalse(
            Long companyId,
            LedgerType ledgerType
    );


    @Query("""
        SELECT lm
        FROM LedgerMaster lm
        WHERE lm.company.id = :companyId
          AND lm.ledgerType IN :ledgerTypes
          AND lm.deleted = false
        ORDER BY lm.id ASC
        """)
    List<LedgerMaster> findByCompanyIdAndLedgerTypeInAndDeletedFalse(
            @Param("companyId") Long companyId,
            @Param("ledgerTypes") List<LedgerType> ledgerTypes
    );





}