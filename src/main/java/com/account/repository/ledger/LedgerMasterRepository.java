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

public interface LedgerMasterRepository
        extends JpaRepository<LedgerMaster, Long>,
        JpaSpecificationExecutor<LedgerMaster> {

    // =====================================================
    // BASIC LOOKUPS
    // =====================================================

    Optional<LedgerMaster> findByIdAndDeletedFalse(Long id);

    Optional<LedgerMaster> findByLedgerNameIgnoreCase(String ledgerName);

    Optional<LedgerMaster> findByLedgerCodeIgnoreCase(String ledgerCode);

    // =====================================================
    // DUPLICATE VALIDATION
    // =====================================================

    boolean existsByLedgerNameIgnoreCase(String ledgerName);

    boolean existsByLedgerNameIgnoreCaseAndIdNot(
            String ledgerName,
            Long id
    );

    boolean existsByLedgerCodeIgnoreCase(String ledgerCode);

    // =====================================================
    // ACTIVE LEDGER LIST
    // =====================================================

    List<LedgerMaster>
    findByDeletedFalseAndActiveTrueAndLedgerTypeInOrderByLedgerNameAsc(
            Collection<LedgerType> ledgerTypes
    );

    // =====================================================
    // COMPANY + UNIT + LEDGER TYPE
    // =====================================================

    /*
     * This method returns all matching ledgers.
     *
     * It is required because old database records may contain
     * multiple ledgers for the same company, unit and ledger type.
     */
    List<LedgerMaster>
    findAllByCompanyIdAndUnitIdAndLedgerTypeAndDeletedFalseOrderByActiveDescIdAsc(
            Long companyId,
            Long unitId,
            LedgerType ledgerType
    );

    /*
     * Keep the existing method name so PaymentServiceImpl does not
     * require any code changes.
     *
     * Active ledger is preferred. If duplicates exist, the oldest
     * matching ledger is returned.
     */
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

    // =====================================================
    // LEDGER TYPE LOOKUP
    // =====================================================

    /*
     * Multiple ledgers can have the same ledger type, so first
     * retrieve a list instead of directly returning Optional.
     */
    List<LedgerMaster>
    findAllByLedgerTypeAndDeletedFalseOrderByActiveDescIdAsc(
            LedgerType ledgerType
    );

    /*
     * Existing method retained for backward compatibility.
     */
    default Optional<LedgerMaster> findByLedgerTypeAndDeletedFalse(
            LedgerType ledgerType
    ) {

        return findAllByLedgerTypeAndDeletedFalseOrderByActiveDescIdAsc(
                ledgerType
        ).stream().findFirst();
    }

    // =====================================================
    // COMPANY + LEDGER TYPE LOOKUP
    // =====================================================

    List<LedgerMaster>
    findAllByCompanyIdAndLedgerTypeAndDeletedFalseOrderByActiveDescIdAsc(
            Long companyId,
            LedgerType ledgerType
    );

    /*
     * Existing method retained for backward compatibility.
     *
     * Prevents IncorrectResultSizeDataAccessException when old
     * duplicate ledgers exist.
     */
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

    // =====================================================
    // COMPANY LEDGERS BY MULTIPLE TYPES
    // =====================================================

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

    // =====================================================
    // DUPLICATE CHECK BY COMPANY + UNIT + TYPE
    // =====================================================

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

    /*
     * Return a list because old/incorrect data can contain duplicate
     * ledgers for the same company, unit and ledger type.
     */
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



}