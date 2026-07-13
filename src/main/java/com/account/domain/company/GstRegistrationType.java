package com.account.domain.company;

public enum GstRegistrationType {

    REGISTERED("Registered GST Customer"),
    UNREGISTERED("Unregistered Customer"),
    SEZ("SEZ Unit - Zero Rated Supply"),
    INTERNATIONAL("International / Export Customer - Zero Rated");

    private final String description;

    GstRegistrationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * GST can be charged for registered and unregistered domestic customers.
     */
    public boolean isGstApplicable() {
        return this == REGISTERED || this == UNREGISTERED;
    }

    /**
     * SEZ and international/export supplies are zero-rated.
     */
    public boolean isZeroRated() {
        return this == SEZ || this == INTERNATIONAL;
    }
}