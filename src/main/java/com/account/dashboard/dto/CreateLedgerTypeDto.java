package com.account.dashboard.dto;

import org.springframework.web.bind.annotation.RequestParam;

public class CreateLedgerTypeDto {

	
	 String name;
	 boolean subLeadger;
	 boolean isDebitCredit;
	 boolean usedForCalculation;
	 Long id;
	 
	 
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public boolean isSubLeadger() {
		return subLeadger;
	}
	public void setSubLeadger(boolean subLeadger) {
		this.subLeadger = subLeadger;
	}
	public boolean isDebitCredit() {
		return isDebitCredit;
	}
	public void setDebitCredit(boolean isDebitCredit) {
		this.isDebitCredit = isDebitCredit;
	}
	public boolean isUsedForCalculation() {
		return usedForCalculation;
	}
	public void setUsedForCalculation(boolean usedForCalculation) {
		this.usedForCalculation = usedForCalculation;
	}
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	 
	 
}
