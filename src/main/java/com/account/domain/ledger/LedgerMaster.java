package com.account.domain.ledger;

import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.Contact;
import com.account.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "ledger_master",
        indexes = {
                @Index(name = "idx_ledger_name_unique", columnList = "ledger_name", unique = true),
                @Index(name = "idx_ledger_code_unique", columnList = "ledger_code", unique = true),
                @Index(name = "idx_ledger_type", columnList = "ledger_type"),
                @Index(name = "idx_ledger_company_id", columnList = "company_id"),
                @Index(name = "idx_ledger_unit_id", columnList = "unit_id"),
                @Index(name = "idx_ledger_group_id", columnList = "ledger_group_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Example: NESTLE INDIA LTD - NOIDA UNIT
    @Column(name = "ledger_name", nullable = false, unique = true, length = 255)
    private String ledgerName;

    // Example: LED-CUST-000001
    @Column(name = "ledger_code", nullable = false, unique = true, length = 50)
    private String ledgerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledger_type", nullable = false, length = 50)
    private LedgerType ledgerType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_group_id", nullable = false)
    private LedgerGroup ledgerGroup;

    // For customer/vendor ledgers
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private CompanyUnit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    // GST/PAN details for party ledger
    @Column(name = "gst_no", length = 15)
    private String gstNo;

    @Column(name = "pan_no", length = 20)
    private String panNo;

    // Bank ledger fields
    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "account_holder_name", length = 150)
    private String accountHolderName;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "ifsc_code", length = 20)
    private String ifscCode;

    @Column(name = "branch_name", length = 100)
    private String branchName;

    // Opening balance
    @Column(name = "opening_balance", precision = 15, scale = 2, nullable = false)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "opening_balance_type", length = 10)
    private DebitCredit openingBalanceType;

    // Current balance for fast dashboard/listing
    @Column(name = "current_balance", precision = 15, scale = 2, nullable = false)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_balance_type", length = 10)
    private DebitCredit currentBalanceType;

    // true for auto-created Nestle customer ledger / customer advance ledger
    @Column(nullable = false)
    private boolean systemCreated = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean deleted = false;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}