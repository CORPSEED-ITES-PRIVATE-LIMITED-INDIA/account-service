package com.account.domain.company;

public enum GstRegistrationType {

    REGISTERED("Normal GST registered customer"),
    UNREGISTERED("Unregistered domestic customer"),
    SEZ("SEZ unit - zero-rated supply"),
    INTERNATIONAL("International/export customer - zero-rated supply");

    private final String description;

    GstRegistrationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isGstApplicable() {
        return this == REGISTERED
                || this == UNREGISTERED;
    }

    public boolean isZeroRated() {
        return this == SEZ
                || this == INTERNATIONAL;
    }

    /**
     * Application business rule:
     *
     * REGISTERED and SEZ projects must wait until
     * GST e-invoice confirmation.
     */
    public boolean requiresEInvoiceConfirmation() {
        return this == REGISTERED
                || this == SEZ;
    }

    /**
     * UNREGISTERED and INTERNATIONAL do not use the
     * GST e-invoice confirmation endpoint.
     */
    public boolean allowsEInvoiceConfirmation() {
        return requiresEInvoiceConfirmation();
    }

    /**
     * For types where e-invoice is not required,
     * the Operation project can be created immediately.
     */
    public boolean shouldCreateProjectImmediately() {
        return !requiresEInvoiceConfirmation();
    }
}