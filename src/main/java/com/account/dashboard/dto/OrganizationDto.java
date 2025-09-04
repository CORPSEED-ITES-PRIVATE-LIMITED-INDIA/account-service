package com.account.dashboard.dto;

import java.util.Date;

public class OrganizationDto {

     String name;
	
	Date joiningDate;
	boolean isDeleted ;
	
	String address;	
	String state;	
	String country;	
	String pin;	
	Date createDate;
	public String getName() {
		return name;
	}
	public Date getJoiningDate() {
		return joiningDate;
	}
	public boolean isDeleted() {
		return isDeleted;
	}
	public String getAddress() {
		return address;
	}
	public String getState() {
		return state;
	}
	public String getCountry() {
		return country;
	}
	public String getPin() {
		return pin;
	}
	public Date getCreateDate() {
		return createDate;
	}
	public void setName(String name) {
		this.name = name;
	}
	public void setJoiningDate(Date joiningDate) {
		this.joiningDate = joiningDate;
	}
	public void setDeleted(boolean isDeleted) {
		this.isDeleted = isDeleted;
	}
	public void setAddress(String address) {
		this.address = address;
	}
	public void setState(String state) {
		this.state = state;
	}
	public void setCountry(String country) {
		this.country = country;
	}
	public void setPin(String pin) {
		this.pin = pin;
	}
	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}  
	
	
	
	
}
