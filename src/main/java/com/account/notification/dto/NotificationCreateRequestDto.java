package com.account.notification.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationCreateRequestDto {

    private Long receiverId;

    private Long actorId;

    private String actorName;

    private NotificationModule module;

    private NotificationEventType eventType;

    private Long referenceId;

    private String referenceNumber;

    private String title;

    private String message;

    private String redirectUrl;

    private NotificationPriority priority;

    private NotificationDisplayType displayType;

    private String metadataJson;

    public enum NotificationModule {
        LEAD,
        PROPOSAL,
        ACCOUNT,
        COMPANY,
        TASK,
        USER,
        PAYMENT,
        INVOICE,
        CREDIT_NOTE,
        DOCUMENT,
        SYSTEM
    }

    public enum NotificationEventType {

        LEAD_CREATED,
        LEAD_ASSIGNED,
        LEAD_UPDATED,
        LEAD_STATUS_CHANGED,
        LEAD_REOPENED,
        LEAD_DELETED,

        PROPOSAL_CREATED,
        PROPOSAL_SENT,
        PROPOSAL_APPROVED,
        PROPOSAL_REJECTED,
        PROPOSAL_CANCELLED,
        PROPOSAL_VERSION_CREATED,


        COMPANY_UPDATED,
        COMPANY_APPROVAL_REQUIRED,
        COMPANY_APPROVED,
        COMPANY_REJECTED,

        UNIT_UPDATED,
        UNIT_APPROVAL_REQUIRED,
        UNIT_APPROVED,
        UNIT_REJECTED,

        INVOICE_CREATED,
        INVOICE_UPDATED,
        INVOICE_SENT,

        PAYMENT_REGISTERED,
        PAYMENT_APPROVED,
        PAYMENT_REJECTED,
        PAYMENT_RECEIVED,
        PAYMENT_FAILED,

        CREDIT_NOTE_CREATED,
        CREDIT_NOTE_APPROVED,
        CREDIT_NOTE_REJECTED,

        TASK_CREATED,
        TASK_ASSIGNED,
        TASK_COMPLETED,
        TASK_MISSED,
        TASK_DUE_SOON,

        EMAIL_SENT,
        WHATSAPP_SENT,
        CLIENT_REPLIED,
        CALL_DONE,

        DOCUMENT_UPLOADED,
        DOCUMENT_APPROVED,
        DOCUMENT_REJECTED,

        REMARK_ADDED,
        SYSTEM_NOTIFICATION
    }

    public enum NotificationDisplayType {
        INFO,
        SUCCESS,
        WARNING,
        DANGER
    }
}