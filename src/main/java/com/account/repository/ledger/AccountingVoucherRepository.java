package com.account.repository.ledger;

import com.account.domain.ledger.AccountingVoucher;
import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AccountingVoucherRepository extends JpaRepository<AccountingVoucher, Long>,
        JpaSpecificationExecutor<AccountingVoucher> {

    Optional<AccountingVoucher> findByIdAndStatusNot(Long id, VoucherStatus status);

    boolean existsByVoucherNumberIgnoreCase(String voucherNumber);

    boolean existsBySourceTypeAndSourceIdAndStatus(
            VoucherSourceType sourceType,
            Long sourceId,
            VoucherStatus status
    );
}