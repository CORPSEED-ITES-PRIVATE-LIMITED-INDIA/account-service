package com.account.service;
import com.account.dto.invoice.InvoiceFeedItemDto;
import com.account.enm.InvoiceFeedFilter;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceFeedService {

    List<InvoiceFeedItemDto> getFeed(
            Long userId,
            com.account.enm.InvoiceFeedFilter filter,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    );

    long getFeedCount(
            Long userId,
            InvoiceFeedFilter filter,
            LocalDate fromDate,
            LocalDate toDate
    );
}