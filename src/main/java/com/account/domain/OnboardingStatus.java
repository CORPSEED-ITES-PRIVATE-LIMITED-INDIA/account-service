package com.account.domain;

/**
 * Simplified onboarding status with only four states:
 *
 * 1. MINIMAL     → Just basic company created (quick estimate flow)
 * 2. INITIATED   → Full details entered + payment registered → waiting for accounts review
 * 3. APPROVED    → All units verified and approved by accounts
 * 4. DISAPPROVED → Accounts rejected (issues found) → needs correction and re-submission
 */
public enum OnboardingStatus {

    MINIMAL,

    INITIATED,

    APPROVED,

    DISAPPROVED
}