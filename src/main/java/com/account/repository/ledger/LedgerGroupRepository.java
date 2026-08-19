package com.account.repository.ledger;

import com.account.domain.ledger.LedgerGroup;
import com.account.domain.ledger.LedgerGroupType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface LedgerGroupRepository
        extends JpaRepository<LedgerGroup, Long>,
        JpaSpecificationExecutor<LedgerGroup> {

    Optional<LedgerGroup> findByIdAndDeletedFalse(Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<LedgerGroup> findByDeletedFalseAndActiveTrueOrderByNameAsc();

    Optional<LedgerGroup> findByGroupTypeAndDeletedFalse(
            LedgerGroupType groupType
    );

    Optional<LedgerGroup> findByGroupType(
            LedgerGroupType groupType
    );

    boolean existsByGroupType(
            LedgerGroupType groupType
    );

    boolean existsByGroupTypeAndIdNot(
            LedgerGroupType groupType,
            Long id
    );
}