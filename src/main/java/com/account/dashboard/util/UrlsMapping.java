package com.account.dashboard.util;

import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = "http://localhost:3000")
public interface UrlsMapping {
	public final static String PREFIX = "accountService/api/v1";
	
	
	//======================= USER API=============
	
	public static final String TEST=PREFIX+ "/account/test";	
	public static final String GET_ALL_USER=PREFIX+ "/users/getAllUser";
	public static final String IS_USER_EXIST_OR_NOT=PREFIX+ "/users/isUserExistOrNot";
	public static final String CREATE_USER=PREFIX+ "/users/createUser";
	public static final String CREATE_USER_BY_LEAD=PREFIX+ "/users/createUserByLead";

	public static final String GET_USER=PREFIX+ "/users/getUser";
	public static final String UPDATE_USER=PREFIX+ "/users/updateUser";
	public static final String DELETE_USER=PREFIX+ "/users/deleteUser";
	public static final String CREATE_USER_BY_EMAIL=PREFIX+ "/users/createUserByEmail";
	
	//======================== ROLE =================================
	public static final String CREATE_ROLE=PREFIX+ "/roles/createRole";
	public static final String GET_ALL_ROLE=PREFIX+ "/roles/getAllRole";
	public static final String CREATE_ALL_ROLE_BY_LEAD=PREFIX+ "/roles/createAllRoleByLead";

	//=============================  Account  =================================
	public static final String CREATE_ACCOUNT_DATA=PREFIX+ "/account/createAccountData";
	public static final String GET_ACCOUNT_DATA=PREFIX+ "/account/getAccountData";	
	public static final String GET_ALL_ACCOUNT_DATA=PREFIX+ "/account/getAllAccountData";	
	public static final String UPDATE_ACCOUNT_DATA=PREFIX+ "/account/updateAccountData";	
	public static final String DELETE_ACCOUNT_DATA=PREFIX+ "/account/deleteAccountData";
	
	///===============================  Ledger	Type  =================================
	public static final String CREATE_LEDGER_TYPE=PREFIX+ "/ledgerType/createLedgerType";
	public static final String UPDATE_LEDGER_TYPE=PREFIX+ "/ledgerType/updateLedgerType";
	public static final String GET_ALL_LEDGER_TYPE=PREFIX+ "/ledgerType/getAllLedgerType";
	public static final String DELETE_LEDGER_TYPE=PREFIX+ "/ledgerType/deleteLedgerType";
	public static final String GET_ALL_LEDGER_TYPE_BY_ID=PREFIX+ "/ledgerType/getAllLedgerTypeById";
	public static final String GROUP_SEARCH_API=PREFIX+ "/ledgerType/groupSearchApi";

	
	//================================ Ledger ==========================================
	
	public static final String CREATE_LEDGER=PREFIX+ "/ledger/createLedger";
	public static final String UPDATE_LEDGER=PREFIX+ "/ledger/updateLedger";
	public static final String GET_ALL_LEDGER=PREFIX+ "/ledger/getAllLedger";
	public static final String GET_ALL_LEDGER_COUNT=PREFIX+ "/ledger/getAllLedgerCount";

	public static final String GET_LEDGER_BY_ID=PREFIX+ "/ledger/getLedgerById";
	public static final String GLOBAL_SEARCH_LEDGER=PREFIX+ "/ledger/globalSearchLedger";
	public static final String GET_ALL_LEDGER_BY_GROUP_ID=PREFIX+ "/ledger/getAllLedgerByGroupId";
	public static final String GET_ALL_AMOUNT_BY_GROUP_ID=PREFIX+ "/ledger/getAllAmountByGroupId";
	public static final String GET_ALL_AMOUNT_BY_LEDGER_ID=PREFIX+ "/ledger/getAllAmountByLedgerId";

	//================================ Voucher Type ==========================================

	
	public static final String CREATE_VOUCHER_TYPE=PREFIX+ "/voucherType/createVoucherType";
	public static final String UPDATE_VOUCHER_TYPE=PREFIX+ "/voucherType/updateVoucherType";
	public static final String GET_ALL_VOUCHER_TYPE=PREFIX+ "/voucherType/getAllVoucherType";
	
	//=============================== Voucher  ===========================================
	public static final String CREATE_VOUCHER=PREFIX+ "/voucher/createVoucher";
	public static final String UPDATE_VOUCHER=PREFIX+ "/voucher/updateVoucher";
	public static final String GET_ALL_VOUCHER=PREFIX+ "/voucher/getAllVoucher";
	public static final String GET_ALL_VOUCHER_FOR_EXPORT=PREFIX+ "/voucher/getAllVoucherForExport";

	public static final String GET_ALL_VOUCHER_TEST=PREFIX+ "/voucher/getAllVoucherTest";

	public static final String GET_VOUCHER_AMOUNT=PREFIX+ "/voucher/getVoucherAmount";
	public static final String GET_ALL_VOUCHER_BY_LEDGER_ID=PREFIX+ "/voucher/getAllVoucherByLedgerId";
	public static final String GET_ALL_VOUCHER_IN_BETWEEN_DATE=PREFIX+ "/voucher/getAllVoucherInBetweenDate";
	public static final String GET_ALL_VOUCHER_BY_GROUP=PREFIX+ "/voucher/getAllVoucherByGroup";
	public static final String GET_ALL_PAYABLE_VOUCHER=PREFIX+ "/voucher/getAllVoucher";
	public static final String GET_VOUCHER_BY_ID=PREFIX+ "/voucher/getVoucherById";

	
	//=============================== Bank Details ========================================
	public static final String CREATE_BANK_DETAILS=PREFIX+ "/ledgerType/createBankDetails";
	public static final String UPDATE_BANK_DETAILS=PREFIX+ "/ledgerType/updateBankDetails";
	public static final String GET_ALL_BANK_DETAILS=PREFIX+ "/ledgerType/getAllBankDetails";

	
	//================================= Statutory =========================================
	public static final String ADD_STATUTORY_DETAILS=PREFIX+ "/statutory/addStatutoryDetails";
	
	public static final String UPDATE_STATUTORY_DETAILS=PREFIX+ "/statutory/updateStatutoryDetails";

	public static final String GET_STATUTORY_DETAILS=PREFIX+ "/statutory/getStatutoryDetails";
	public static final String GET_ALL_STATUTORY_DETAILS=PREFIX+ "/statutory/getAllStatutoryDetails";


	
	//==================================== organization ========================================
	public static final String CREATE_ORGANIIZATION=PREFIX+ "/organization/createOrganization";
	public static final String GET_ORGANIIZATION_BY_ID=PREFIX+ "/organization/getOrganizationById";
	public static final String GET_ALL_ORGANIIZATION=PREFIX+ "/organization/getAllOrganization";
	public static final String GET_ALL_ORGANIIZATION_BY_NAME=PREFIX+ "/organization/getAllOrganizationByName";
	public static final String CREATE_STATUTORY_IN_ORGANIIZATION=PREFIX+ "/organization/createStatutoryInOrganization";
	public static final String ADD_BANK_ACCOUNT_IN_ORGANIIZATION=PREFIX+ "/organization/addBankAccountInOrganization";
	public static final String GET_ALL_BANK_ACCOUNT_BY_ORGANIZATION=PREFIX+ "/organization/getAllBankAccountByOrganization";

	//==================================== Payment Register ========================================
		public static final String CREATE_PAYMENT_REGISTER=PREFIX+ "/paymentRegister/createPaymentRegister";
		public static final String GET_ALL_PAYMENT_REGISTER=PREFIX+ "/paymentRegister/getAllPaymentRegister";
		public static final String UPDATE_PAYMENT_REGISTER=PREFIX+ "/paymentRegister/updatePaymentRegister";
		public static final String PAYMENT_APPROVE_V2=PREFIX+ "/paymentRegister/paymentApproveV2";
		public static final String PAYMENT_APPROVE=PREFIX+ "/paymentRegister/paymentApprove";
		public static final String GET_PAYMENT_REGISTER_BY_ID=PREFIX+ "/paymentRegister/getPaymentRegisterById";
		public static final String GET_PAYMENT_REGISTER_BY_ESTIMATE_ID=PREFIX+ "/paymentRegister/getPaymentRegisterByEstimateId";
		public static final String CREATE_INVOICE=PREFIX+ "/paymentRegister/createInvoice";
		public static final String GET_INVOICE=PREFIX+ "/paymentRegister/getInvoice";
		public static final String LEFT_AMOUNT=PREFIX+ "/paymentRegister/leftAmount";
		public static final String GET_ALL_PAYMENT_REGISTER_BY_STATUS=PREFIX+ "/paymentRegister/getAllPaymentRegisterByStatus";
		public static final String PAYMENT_DISSAPPROVE=PREFIX+ "/paymentRegister/paymentDissapprove";
		public static final String PAYMENT_APPROVE_V3=PREFIX+ "/paymentRegister/paymentApproveV3";
		public static final String GET_ALL_INVOICE=PREFIX+ "/paymentRegister/getAllInvoice";
		public static final String GET_ALL_INVOICE_COUNT=PREFIX+ "/paymentRegister/getAllInvoiceCount";

		public static final String GET_ALL_INVOICE_ACCORDING_TO_USER=PREFIX+ "/paymentRegister/getAllInvoiceAccordingToUser";

		public static final String GET_ALL_INVOICE_FOR_MANAGE_SALES=PREFIX+ "/paymentRegister/getAllInvoiceForSales";
		public static final String CREATE_PURCHASE_ORDER=PREFIX+ "/paymentRegister/createPurchaseOrder";
		public static final String GET_ALL_PURCHASE_ORDER=PREFIX+ "/paymentRegister/getAllPurchaseOrder";
		public static final String GET_ALL_PAYMENT_REGISTER_WITH_COMPANY=PREFIX+ "/paymentRegister/getAllPaymentRegisterWithCompany";

		public static final String GET_ALL_PAYMENT_REGISTER_WITH_PAGE=PREFIX+ "/paymentRegister/getAllPaymentRegisterWithPage";
		public static final String GET_ALL_PAYMENT_REGISTER_COUNT=PREFIX+ "/paymentRegister/getAllPaymentRegisterCount";
		public static final String GET_ALL_PAYMENT_REGISTER_BY_USER=PREFIX+ "/paymentRegister/getAllPaymentRegisterByUser";


	
	//====================================  BankStatements ===========================================
		public static final String CREATE_BANK_STATEMENTS=PREFIX+ "/bankStatements/createBankStatement";
		public static final String GET_UNUSED_BANK_STATEMENTS=PREFIX+ "/bankStatements/getUnusedBankStatement";
		public static final String GET_ALL_BANK_STATEMENTS=PREFIX+ "/bankStatements/getAllBankStatements";
		public static final String ADD_REGISTER_AMOUNT_IN_BANK_STATEMENTS =PREFIX+ "/bankStatements/addRegisterAmountInBankStatement";
// =  == ================================== Tds Api creation =======================================
		public static final String CREATE_TDS=PREFIX+ "/tds/createTds";
		public static final String GET_ALL_TDS=PREFIX+ "/tds/getAllTds";
		public static final String GET_ALL_TDS_COUNT=PREFIX+ "/tds/getAllTdsCount";
		
	// = ==========================================  File Upload = = = = =     = == = = = = = == == = = = = 	
		
		public static final String UPLOAD_IMAGE_TO_FILE=PREFIX+"/upload/uploadimageToFileSystem";
		public static final String GET_IMAGE_TO_FILE=PREFIX+"/upload/getImageToFileSystem";
		
	// =  == ================================== Tds Api creation =======================================
		public static final String CREATE_GST=PREFIX+ "/tds/createGst";
		public static final String GET_ALL_GST=PREFIX+ "/tds/getAllGst";
		public static final String GET_GST_BY_ID=PREFIX+ "/tds/getGstById";
		
		
		
		//========================================== Unbilled ==========================================
		public static final String CREATE_UNBILLED=PREFIX+ "/ledgerType/createUnbilled";
		public static final String UPDATE_UNBILLED=PREFIX+ "/ledgerType/updateUnbilled";
		public static final String GET_UNBILLED_BY_ID=PREFIX+ "/ledgerType/getUnbilledById";
		public static final String GET_ALL_UNBILLED=PREFIX+ "/ledgerType/getAllUnbilled";
		public static final String GET_ALL_UNBILLED_AMOUNT=PREFIX+ "/ledgerType/getAllUnbilledAmount";

        // get all trial balance
		
		public static final String GET_ALL_TRIAL_BALANCE=PREFIX+ "/trialBalance/getAllTrialBalance";

		// get All Cash Flow
		
		public static final String GET_ALL_IN_FLOW=PREFIX+ "/cashFlow/getAllInFlow";
		public static final String GET_ALL_OUT_FLOW=PREFIX+ "/cashFlow/getAllOutFlow";

		// get Profit and Loss
		
		public static final String GET_ALL_PROFIT=PREFIX+ "/cashFlow/getAllProfit";
		public static final String GET_ALL_LOSS=PREFIX+ "/cashFlow/getAllLoss";
		
		// get All Balance sheet liabilities
		public static final String GET_ALL_BALANCE_SHEET_LIABILITIES=PREFIX+ "/balanceSheet/getAllBalanceSheetLiabilities";
		public static final String GET_ALL_BALANCE_SHEET_ASSETS=PREFIX+ "/balanceSheet/getAllBalanceSheetAssets";
		public static final String GET_ALL_BALANCE_SHEET_LIABILITIES_FOR_EXPORT=PREFIX+ "/balanceSheet/getAllBalanceSheetLiabilitiesForExport";
		public static final String GET_ALL_BALANCE_SHEET_ASSETS_FOR_EXPORT=PREFIX+ "/balanceSheet/getAllBalanceSheetAssetsForExport";
		public static final String GET_ALL_GROUP_BY_PARENT_GROUP_ID=PREFIX+ "/balanceSheet/getAllGroupByParentGroupId";

}
