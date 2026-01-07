package com.account.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.List;

@Entity
@Data
@NoArgsConstructor(force = true)
@Table(name = "user")
public class User {

    @Id
    private Long id;

    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String designation;

    @Column(nullable = false)
    private String department;

    // Soft delete flag (standard across your entities)
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    // Optional: active status (if you want both active/inactive without full delete)
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
    
    @ManyToMany(cascade = CascadeType.ALL)
	@JoinTable(name="user_role",joinColumns = {@JoinColumn(name="user_id",referencedColumnName="id",nullable=true)},
			inverseJoinColumns = {@JoinColumn(name="user_role_id"
					+ "",referencedColumnName = "id",nullable=true,unique=false)})
    private List<Role>userRole;

	@NonNull
	private List<String> role;


}

