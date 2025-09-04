package com.account.dashboard.domain;

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

	public Long getId() {
		return id;
	}
	public String getEmail() {
		return email;
	}
	public String getDesignation() {
		return designation;
	}
	public String getDepartment() {
		return department;
	}
	
	//All Setter
	
	public void setId(Long id) {
		this.id = id;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public void setDesignation(String designation) {
		this.designation = designation;
	}
	public void setDepartment(String department) {
		this.department = department;
	}
	public List<String> getRole() {
		return role;
	}
	public void setRole(List<String> role) {
		this.role = role;
	}
	public String getFullName() {
		return fullName;
	}
	public void setFullName(String fullName) {
		this.fullName = fullName;
	}
	public List<Role> getUserRole() {
		return userRole;
	}
	public void setUserRole(List<Role> userRole) {
		this.userRole = userRole;
	}

}

