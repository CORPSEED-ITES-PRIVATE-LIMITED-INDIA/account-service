package com.account.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "payment_type",
        indexes = {@Index(name = "idx_payment_type_code_unique", columnList = "code", unique = true)})
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
    private String code;  // e.g. "FULL", "PARTIAL", "INSTALLMENT", "PURCHASE_ORDER"

    @Column(nullable = false, length = 100)
    private String name;  // e.g. "Full Payment", "Partial Payment (50%)"

    @Column(columnDefinition = "TEXT")
    private String description;  // Optional detailed description

    private boolean active = true;

    // Optional: For Milestone type - can link to predefined milestones later
    // private boolean isMilestone = false;

    // No approval fields here - these are static master data
}