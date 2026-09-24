package com.account.service.vendor;

import com.account.domain.ledger.LedgerMaster;
import com.account.domain.vendor.ExternalVendor;
import com.account.dto.vendor.VendorPaymentApprovalAccountingResult;
import com.account.dto.vendor.VendorPaymentApprovalRequestDto;

public interface VendorPaymentApprovalAccountingService {

    VendorPaymentApprovalAccountingResult postApprovalVoucher(
            ExternalVendor externalVendor,
            LedgerMaster vendorLedger,
            VendorPaymentApprovalRequestDto request
    );
}
