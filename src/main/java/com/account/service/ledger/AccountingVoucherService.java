package com.account.service.ledger;

import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherStatus;
import com.account.domain.ledger.VoucherType;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.dto.ledger.AccountingVoucherResponseDto;
import org.springframework.data.domain.Page;
import java.time.LocalDate;


public interface AccountingVoucherService {

    AccountingVoucherResponseDto createVoucher(AccountingVoucherRequestDto request);

    AccountingVoucherResponseDto getVoucherById(Long id);

    Page<AccountingVoucherResponseDto> getVouchers(
            VoucherType voucherType,
            VoucherSourceType sourceType,
            VoucherStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    );

    void cancelVoucher(Long id, String reason);
    boolean existsPostedVoucher(
            VoucherType voucherType,
            VoucherSourceType sourceType,
            Long sourceId
    );
}