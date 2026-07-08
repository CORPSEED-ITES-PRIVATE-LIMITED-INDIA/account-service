package com.account.repository.ledger;

import com.account.domain.ledger.LedgerGroup;
import com.account.domain.ledger.LedgerGroupType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Repository for LedgerGroup entity.
 *
 * LedgerGroup means the accounting category/group under which ledgers are created.
 *
 * Examples:
 * 1. Sundry Debtors
 * 2. Bank Accounts
 * 3. Duties & Taxes
 * 4. Sales Accounts
 * 5. Current Assets
 * 6. Current Liabilities
 *
 * This repository supports:
 * - Basic CRUD using JpaRepository
 * - Dynamic filtering/search using JpaSpecificationExecutor
 * - Soft delete based queries using deleted = false
 */
public interface LedgerGroupRepository extends JpaRepository<LedgerGroup, Long>, JpaSpecificationExecutor<LedgerGroup> {

    /**
     * Fetch a ledger group by id only if it is not soft deleted.
     *
     * Used in update, view and delete flows.
     */
    Optional<LedgerGroup> findByIdAndDeletedFalse(Long id);

    /**
     * Checks whether a ledger group already exists with same name.
     *
     * IgnoreCase means:
     * "Bank Accounts" and "bank accounts" will be treated as same.
     *
     * Used while creating a new ledger group.
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Checks duplicate name while updating.
     *
     * Same name is allowed for the same record,
     * but not allowed for another ledger group.
     *
     * Example:
     * Updating ID 2 from "Bank Accounts" to "Bank Accounts" is allowed.
     * Updating ID 2 to a name already used by ID 5 is not allowed.
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * Fetch all active and non-deleted ledger groups.
     *
     * Used for dropdowns while creating LedgerMaster.
     */
    List<LedgerGroup> findByDeletedFalseAndActiveTrueOrderByNameAsc();

    /*
     * Used while auto-creating system ledgers.
     *
     * Example:
     * LedgerGroupType.CURRENT_LIABILITIES
     * LedgerGroupType.BANK_ACCOUNTS
     * LedgerGroupType.SUNDRY_DEBTORS
     */
    Optional<LedgerGroup> findByGroupTypeAndDeletedFalse(LedgerGroupType groupType);


    Optional<LedgerGroup> findByGroupType(LedgerGroupType groupType);


}