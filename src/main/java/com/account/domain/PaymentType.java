package com.account.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_type",
        indexes = {
                @Index(name = "idx_payment_type_code_unique", columnList = "code", unique = true),
                @Index(name = "idx_payment_type_name_unique", columnList = "name", unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PaymentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;           // e.g. "FULL", "PARTIAL", "INSTALLMENT"

    @Column(nullable = false, unique = true, length = 100)
    private String name;           // e.g. "Full Payment", "50% Advance"

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean isDeleted = false;

    // Optional – audit fields
    @Column(updatable = false)
    private Long createdBy;

    private Long updatedBy;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;
}