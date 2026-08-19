package com.account.repository.ledger;

import com.account.domain.ledger.LedgerMaster;
import com.account.domain.ledger.LedgerType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerMasterRepository
        extends JpaRepository<LedgerMaster, Long>,
        JpaSpecificationExecutor<LedgerMaster> {

    Optional<LedgerMaster> findByIdAndDeletedFalse(Long id);

    Optional<LedgerMaster> findByLedgerNameIgnoreCase(String ledgerName);

    Optional<LedgerMaster> findByLedgerCodeIgnoreCase(String ledgerCode);

    boolean existsByLedgerNameIgnoreCase(String ledgerName);

    boolean existsByLedgerNameIgnoreCaseAndIdNot(
            String ledgerName,
            Long id
    );

    boolean existsByLedgerCodeIgnoreCase(String ledgerCode);

    List<LedgerMaster>
    findByDeletedFalseAndActiveTrueAndLedgerTypeInOrderByLedgerNameAsc(
            Collection<LedgerType> ledgerTypes
    );

    List<LedgerMaster>
    findAllByCompanyIdAndUnitIdAndLedgerTypeAndDeletedFalseOrderByActiveDescIdAsc(
            Long companyId,
            Long unitId,
            LedgerType ledgerType
    );

    default Optional<LedgerMaster>
    findByCompanyIdAndUnitIdAndLedgerTypeAndDeletedFalse(
            Long companyId,
            Long unitId,
            LedgerType ledgerType
    ) {
        return findAllByCompanyIdAndUnitIdAndLedgerTypeAndDeletedFalseOrderByActiveDescIdAsc(
                companyId,
                unitId,
                ledgerType
        ).stream().findFirst();
    }

    List<LedgerMaster>
    findAllByCompanyIdAndUnitIdAndLedgerTypeInAndDeletedFalseOrderByActiveDescIdAsc(
            Long companyId,
            Long unitId,
            Collection<LedgerType> ledgerTypes
    );

    default Optional<LedgerMaster>
    findFirstByCompanyIdAndUnitIdAndLedgerTypeInAndDeletedFalse(
            Long companyId,
            Long unitId,
            Collection<LedgerType> ledgerTypes
    ) {
        return findAllByCompanyIdAndUnitIdAndLedgerTypeInAndDeletedFalseOrderByActiveDescIdAsc(
                companyId,
                unitId,
                ledgerTypes
        ).stream().findFirst();
    }

    List<LedgerMaster>
    findAllByLedgerTypeAndDeletedFalseOrderByActiveDescIdAsc(
            LedgerType ledgerType
    );

    default Optional<LedgerMaster> findByLedgerTypeAndDeletedFalse(
            LedgerType ledgerType
    ) {
        return findAllByLedgerTypeAndDeletedFalseOrderByActiveDescIdAsc(
                ledgerType
        ).stream().findFirst();
    }

    List<LedgerMaster>
    findAllByCompanyIdAndLedgerTypeAndDeletedFalseOrderByActiveDescIdAsc(
            Long companyId,
            LedgerType ledgerType
    );

    default Optional<LedgerMaster>
    findByCompanyIdAndLedgerTypeAndDeletedFalse(
            Long companyId,
            LedgerType ledgerType
    ) {
        return findAllByCompanyIdAndLedgerTypeAndDeletedFalseOrderByActiveDescIdAsc(
                companyId,
                ledgerType
        ).stream().findFirst();
    }

    @Query("""
            SELECT lm
            FROM LedgerMaster lm
            WHERE lm.company.id = :companyId
              AND lm.ledgerType IN :ledgerTypes
              AND lm.deleted = false
            ORDER BY
                lm.active DESC,
                lm.id ASC
            """)
    List<LedgerMaster> findByCompanyIdAndLedgerTypeInAndDeletedFalse(
            @Param("companyId") Long companyId,
            @Param("ledgerTypes") List<LedgerType> ledgerTypes
    );

    boolean existsByCompanyIdAndUnitIdAndLedgerTypeInAndDeletedFalse(
            Long companyId,
            Long unitId,
            Collection<LedgerType> ledgerTypes
    );

    boolean existsByCompanyIdAndUnitIdAndLedgerTypeInAndDeletedFalseAndIdNot(
            Long companyId,
            Long unitId,
            Collection<LedgerType> ledgerTypes,
            Long id
    );

    @Query("""
            SELECT lm
            FROM LedgerMaster lm
            WHERE lm.company.id = :companyId
              AND lm.unit.id = :unitId
              AND lm.ledgerType = :ledgerType
              AND lm.deleted = false
            ORDER BY lm.id ASC
            """)
    List<LedgerMaster>
    findAllByCompanyIdAndUnitIdAndLedgerTypeAndDeletedFalse(
            @Param("companyId") Long companyId,
            @Param("unitId") Long unitId,
            @Param("ledgerType") LedgerType ledgerType
    );

    Optional<LedgerMaster> findByLedgerCodeIgnoreCaseAndDeletedFalse(
            String ledgerCode
    );

    /**
     * Locks all ledgers participating in a voucher in stable ID order.
     * This prevents lost currentBalance updates when two posted vouchers
     * touch the same bank/customer/system ledger concurrently.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT lm
            FROM LedgerMaster lm
            WHERE lm.id IN :ledgerIds
              AND lm.deleted = false
            ORDER BY lm.id ASC
            """)
    List<LedgerMaster> findAllByIdInAndDeletedFalseForUpdate(
            @Param("ledgerIds") Collection<Long> ledgerIds
    );
}