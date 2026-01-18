package com.account.exception;

public class ApprovalBlockedException extends RuntimeException {

    private final boolean companyApproved;
    private final boolean unitApproved;

    public ApprovalBlockedException(String message, boolean companyApproved, boolean unitApproved) {
        super(message);
        this.companyApproved = companyApproved;
        this.unitApproved = unitApproved;
    }

    // getters if you want to send more structured info to frontend
    public boolean isCompanyApproved() { return companyApproved; }
    public boolean isUnitApproved() { return unitApproved; }
}