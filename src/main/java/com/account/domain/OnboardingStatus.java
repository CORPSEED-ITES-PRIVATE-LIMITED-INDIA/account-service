package com.account.domain;


public enum OnboardingStatus {

    /**
     * Company created minimally (quick estimate phase)
     */
    MINIMAL,

    /**
     * Sales clicked "Register Payment" after entering complete company + unit details.
     * Now waiting for accounts team to review and approve/reject.
     */
    PENDING_REVIEW,

    /**
     * Accounts verified and approved everything (all units OK).
     * Ready for service delivery.
     */
    APPROVED,

    /**
     * Accounts found issues — sales needs to fix and re-register payment.
     */
    REJECTED
}