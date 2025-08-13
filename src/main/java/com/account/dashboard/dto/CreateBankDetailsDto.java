package com.account.dashboard.dto;

import lombok.Data;

@Data
public class CreateBankDetailsDto {

	String bankName;
	String ifscCode;
	String swiftCode;
	String branch;
	
	
}
