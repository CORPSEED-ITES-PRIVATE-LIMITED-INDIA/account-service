package com.account.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "contact")
@Getter
@Setter
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "emails")
    private String emails; // Comma-separated or JSON array as string if needed

    @Column(name = "contact_no")
    private String contactNo;

    @Column(name = "whatsapp_no")
    private String whatsappNo;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "is_deleted")
    private boolean deleteStatus = false;
}