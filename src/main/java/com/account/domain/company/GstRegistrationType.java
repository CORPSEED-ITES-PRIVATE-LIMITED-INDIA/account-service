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
        return this == REGISTERED || this == UNREGISTERED;
    }

    public boolean isZeroRated() {
        return this == SEZ || this == INTERNATIONAL;
    }


}