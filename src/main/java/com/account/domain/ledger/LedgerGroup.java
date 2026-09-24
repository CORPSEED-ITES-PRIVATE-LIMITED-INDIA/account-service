package com.account.domain.ledger;

import com.account.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_group", indexes = {
        @Index(name = "idx_ledger_group_name_unique", columnList = "name", unique = true),
        @Index(name = "idx_ledger_group_type", columnList = "group_type")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false, length = 50)
    private LedgerGroupType groupType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean systemDefault = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean deleted = false;

    // ==================== AUDIT FIELDS ====================
    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", updatable = false)
    private User createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private User updatedBy;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}