package com.account.util;

import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = "http://localhost:3000")
public interface UrlsMapping {
	public final static String PREFIX = "accountService/api/v1";

	public static final String TEST=PREFIX+ "/account/test";
	public static final String GET_ALL_USER=PREFIX+ "/users/getAllUser";
	public static final String IS_USER_EXIST_OR_NOT=PREFIX+ "/users/isUserExistOrNot";
	public static final String CREATE_USER=PREFIX+ "/users/createUser";
	public static final String CREATE_USER_BY_LEAD=PREFIX+ "/users/createUserByLead";

	public static final String GET_USER=PREFIX+ "/users/getUser";
	public static final String UPDATE_USER=PREFIX+ "/users/updateUser";
	public static final String DELETE_USER=PREFIX+ "/users/deleteUser";
	public static final String CREATE_USER_BY_EMAIL=PREFIX+ "/users/createUserByEmail";
	public static final String CREATE_USER_BY_LEAD_SERVICES=PREFIX+ "/users/createUserByLeadServices";

	//======================== ROLE =================================
	public static final String CREATE_ROLE=PREFIX+ "/roles/createRole";
	public static final String GET_ALL_ROLE=PREFIX+ "/roles/getAllRole";
	public static final String CREATE_ALL_ROLE_BY_LEAD=PREFIX+ "/roles/createAllRoleByLead";



}
