package com.account.service;

import com.account.dto.unbilled.UnbilledInvoiceFilterRequest;
import com.account.dto.unbilled.UnbilledInvoiceSummaryDto;

import java.util.List;

public interface UnbilledService {

    List<UnbilledInvoiceSummaryDto> searchUnbilledInvoices(
            Long userId,
            UnbilledInvoiceFilterRequest filter,
            int page,
            int size
    );
}
