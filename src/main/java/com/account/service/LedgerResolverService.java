package com.account.service;

import com.account.domain.User;
import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.LedgerGroupType;
import com.account.domain.ledger.LedgerMaster;
import com.account.domain.ledger.LedgerType;
import com.account.domain.unbilled.UnbilledInvoice;

public interface LedgerResolverService {

    LedgerMaster systemLedger(
            LedgerType type,
            LedgerGroupType groupType,
            String name,
            DebitCredit balanceType,
            User by
    );

    LedgerMaster customerLedger(
            UnbilledInvoice unbilled,
            User by
    );
}