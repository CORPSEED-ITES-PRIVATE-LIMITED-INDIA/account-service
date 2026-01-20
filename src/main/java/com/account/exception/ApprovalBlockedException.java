package com.account.exception;

public class ApprovalBlockedException extends RuntimeException {

    private final boolean companyApproved;
    private final boolean unitApproved;

    public ApprovalBlockedException(String message, boolean companyApproved, boolean unitApproved) {
        super(message);
        this.companyApproved = companyApproved;
        this.unitApproved = unitApproved;
    }

}