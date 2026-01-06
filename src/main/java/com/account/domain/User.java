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

    @NonNull
    private String email;

    @NonNull
    private String designation;

    @NonNull
    private String department;
    
    @ManyToMany(cascade = CascadeType.ALL)
	@JoinTable(name="user_role",joinColumns = {@JoinColumn(name="user_id",referencedColumnName="id",nullable=true)},
			inverseJoinColumns = {@JoinColumn(name="user_role_id"
					+ "",referencedColumnName = "id",nullable=true,unique=false)})
    private List<Role>userRole;

	@NonNull
	private List<String> role;


}

