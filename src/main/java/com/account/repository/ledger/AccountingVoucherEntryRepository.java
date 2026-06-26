package com.account.repository.ledger;

import com.account.domain.ledger.AccountingVoucherEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountingVoucherEntryRepository extends JpaRepository<AccountingVoucherEntry, Long> {

    List<AccountingVoucherEntry> findByVoucher_IdOrderByDisplayOrderAsc(Long voucherId);
}