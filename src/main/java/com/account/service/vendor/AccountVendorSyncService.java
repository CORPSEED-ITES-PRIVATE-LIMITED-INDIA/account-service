package com.account.service.vendor;

import com.account.dto.vendor.AccountVendorSyncRequestDto;
import com.account.dto.vendor.AccountVendorSyncResponseDto;

/**
 * Account Service vendor payment synchronization interface.
 *
 * Receives immutable calculation snapshots from Operation Service and creates:
 * 1. PURCHASE_INVOICE voucher
 * 2. PAYMENT voucher (with TDS if applicable)
 *
 * Account Service NEVER recalculates GST/TDS — it only validates and posts.
 */
public interface AccountVendorSyncService {

    /**
     * Synchronizes vendor payment data from Operation Service.
     *
     * @param request Immutable vendor payment approval request from Operation Service
     * @return Response containing created voucher IDs and ledger information
     * @throws com.account.exception.ValidationException if snapshot validation fails
     * @throws com.account.exception.ResourceNotFoundException if required ledgers not found
     */
    AccountVendorSyncResponseDto syncVendor(AccountVendorSyncRequestDto request);
}
