
package com.account.repository.ledger;

import com.account.domain.ledger.LedgerGroup;
import com.account.domain.ledger.LedgerGroupType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerGroupRepository
        extends JpaRepository<LedgerGroup, Long>,
        JpaSpecificationExecutor<LedgerGroup> {

    @Query(value = """
            SELECT lg.*
            FROM ledger_group lg
            WHERE lg.id = :id
              AND lg.deleted = 0
            LIMIT 1
            """, nativeQuery = true)
    Optional<LedgerGroup> findByIdAndDeletedFalse(
            @Param("id") Long id
    );

    @Query("""
            SELECT CASE WHEN COUNT(lg) > 0 THEN true ELSE false END
            FROM LedgerGroup lg
            WHERE LOWER(lg.name) = LOWER(:name)
            """)
    boolean existsByNameIgnoreCase(
            @Param("name") String name
    );

    @Query("""
            SELECT CASE WHEN COUNT(lg) > 0 THEN true ELSE false END
            FROM LedgerGroup lg
            WHERE LOWER(lg.name) = LOWER(:name)
              AND lg.id <> :id
            """)
    boolean existsByNameIgnoreCaseAndIdNot(
            @Param("name") String name,
            @Param("id") Long id
    );

    @Query(value = """
            SELECT lg.*
            FROM ledger_group lg
            WHERE lg.deleted = 0
              AND lg.active = 1
            ORDER BY lg.name ASC
            """, nativeQuery = true)
    List<LedgerGroup> findByDeletedFalseAndActiveTrueOrderByNameAsc();

    @Query(value = """
            SELECT lg.*
            FROM ledger_group lg
            WHERE lg.group_type = :#{#groupType.name()}
              AND lg.deleted = 0
            LIMIT 1
            """, nativeQuery = true)
    Optional<LedgerGroup> findByGroupTypeAndDeletedFalse(
            @Param("groupType") LedgerGroupType groupType
    );

    @Query(value = """
            SELECT lg.*
            FROM ledger_group lg
            WHERE lg.group_type = :#{#groupType.name()}
            LIMIT 1
            """, nativeQuery = true)
    Optional<LedgerGroup> findByGroupType(
            @Param("groupType") LedgerGroupType groupType
    );

    @Query("""
            SELECT CASE WHEN COUNT(lg) > 0 THEN true ELSE false END
            FROM LedgerGroup lg
            WHERE lg.groupType = :groupType
            """)
    boolean existsByGroupType(
            @Param("groupType") LedgerGroupType groupType
    );

    @Query("""
            SELECT CASE WHEN COUNT(lg) > 0 THEN true ELSE false END
            FROM LedgerGroup lg
            WHERE lg.groupType = :groupType
              AND lg.id <> :id
            """)
    boolean existsByGroupTypeAndIdNot(
            @Param("groupType") LedgerGroupType groupType,
            @Param("id") Long id
    );
}
