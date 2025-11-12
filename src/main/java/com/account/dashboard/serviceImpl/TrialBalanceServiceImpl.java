package com.account.dashboard.serviceImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.account.LedgerType;
import com.account.dashboard.domain.account.Voucher;
import com.account.dashboard.repository.LedgerRepository;
import com.account.dashboard.repository.LedgerTypeRepository;
import com.account.dashboard.repository.VoucherRepository;
import com.account.dashboard.service.TrialBalanceService;

@Service
public class TrialBalanceServiceImpl implements TrialBalanceService{
	
	@Autowired
	LedgerTypeRepository ledgerTypeRepository;
	
	@Autowired
	LedgerRepository ledgerRepository;
	
	@Autowired
	VoucherRepository voucherRepository;


	@Override
	public List<Map<String, Object>> getAllTrialBalance(String startDate, String endDate) {
		List<String>gList=Arrays.asList("Capital Account","Loans","Current Liabilities","Fixed Assets"
				,"Suspense Account","Current Assets","Suspense Account","Direct Incomes",
				"Indirect Incomes","Direct Expenses","Indirect Expenses");
//		List<LedgerType> group = ledgerTypeRepository.findAll();
		List<LedgerType> group = ledgerTypeRepository.findByNameIn(gList);
		List<Map<String, Object>>result=new ArrayList<>();
		for(LedgerType g:group) {
			System.out.println("Group name .."+g.getId());

			Map<String,Object>map=new HashMap<>();
			List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());
			System.out.println("ledgerList .."+ledgerList+"...."+g.getName());

//	         LedgerType ledgerType = ledgerTypeRepository.findById(g.getId()).get();
//			List<Voucher>voucherList=voucherRepository.findAllByLedgerIdIn(ledgerList);
			List<Voucher>voucherList=voucherRepository.findByLedgerIdInAndInBetween(ledgerList,startDate,endDate);

			double totalCredit=0;
			double totalDebit=0;
			double totalAmount=0;
			System.out.println("..."+voucherList.size());
			for(Voucher v:voucherList) {			
				if(v.isCreditDebit()) {
					double debitAmount =0;
					double creditAmount =0;
					if(v!=null && v.getDebitAmount()!=0) {
						debitAmount =v.getDebitAmount();
					}
					if(v!=null && v.getCreditAmount()!=0) {
						creditAmount =v.getCreditAmount();
					}
					totalCredit=totalCredit+creditAmount;
					totalDebit=totalDebit+debitAmount;
					totalAmount=totalAmount-debitAmount+creditAmount;
				}else {
					double debitAmount =v.getDebitAmount();
					totalDebit=totalDebit+debitAmount;
					totalAmount=totalAmount-debitAmount;

				}
			}
			map.put("totalCredit", totalCredit);
			map.put("groupName", g.getName());
			map.put("totalDebit", totalDebit);
			map .put("totalAmount", totalAmount);
			result.add(map);
		}
		return result;
	}
	
	public List<String> getShareholderFunds(){
		// Creating an ArrayList to store the categories of Shareholders' Funds
        ArrayList<String> shareholdersFunds = new ArrayList<>();

        // Adding components of Shareholders' Funds to the list
        shareholdersFunds.add("Share Capital");
        shareholdersFunds.add("Equity Share Capital");
        shareholdersFunds.add("Preference Share Capital");
        shareholdersFunds.add("Reserves and Surplus");
        shareholdersFunds.add("Capital Reserve");
        shareholdersFunds.add("Investment (Current + Non-Current)");
        shareholdersFunds.add("Revaluation Reserve");
        shareholdersFunds.add("General Reserve");
        shareholdersFunds.add("Retained Earnings");
        shareholdersFunds.add("Other Specific Reserves");
        shareholdersFunds.add("Money received against share warrants");
        return shareholdersFunds;
	}
	
	public List<String> getUnsecuredLoans(){
		// Creating an ArrayList to store the categories of Shareholders' Funds
        ArrayList<String> shareholdersFunds = new ArrayList<>();

        // Adding components of Shareholders' Funds to the list
        shareholdersFunds.add("Unsecured Loans");
    
        return shareholdersFunds;
	}
	public List<String> getCurrentLiabilities(){
        ArrayList<String> currentLiabilities = new ArrayList<>();

        // Add all components (no serial numbers or dashes)
        currentLiabilities.add("Current Liabilities");
        currentLiabilities.add("Short-term Borrowings");
        currentLiabilities.add("Loans from Banks");
        currentLiabilities.add("Loans from Financial Institutions / NBFCs");
        currentLiabilities.add("Loans from Others / Unsecured Loans");
        currentLiabilities.add("Other Short-term Borrowings");
        currentLiabilities.add("Trade Payables");
        currentLiabilities.add("Micro, Small and Medium Enterprises (MSMEs)");
        currentLiabilities.add("Others");
        currentLiabilities.add("Other Current Liabilities");
        currentLiabilities.add("Current Maturities of Long-term Borrowings");
        currentLiabilities.add("Statutory Dues Payable");
        currentLiabilities.add("Advance from Customers");
        currentLiabilities.add("Interest Accrued but Not Due");
        currentLiabilities.add("Dividends Payable");
        currentLiabilities.add("Other Payables / Liabilities");
        currentLiabilities.add("Short-term Provisions");
        currentLiabilities.add("Provision for Employee Benefits");
        currentLiabilities.add("Provision for Tax");
        currentLiabilities.add("Provision for Warranty");
        currentLiabilities.add("Other Short-term Provisions");
    
        return currentLiabilities;
	}
	public List<String> getPropertyPlantEquipment(){
        ArrayList<String> propertyPlantEquipment = new ArrayList<>();

        // Add all components (no serial numbers or dashes)
        propertyPlantEquipment.add("Property, Plant and Equipment");
        propertyPlantEquipment.add("Land");
        propertyPlantEquipment.add("Buildings");
        propertyPlantEquipment.add("Plant and Machinery");
        propertyPlantEquipment.add("Furniture and Fixtures");
        propertyPlantEquipment.add("Vehicles");
        propertyPlantEquipment.add("Office Equipment");
        propertyPlantEquipment.add("Other");
    
        return propertyPlantEquipment;
	}
	
	public List<String> getInvestments(){
		 List<String> investments = new ArrayList<>();

	        // Non-current Assets
		 investments.add("Current investments");
		 investments.add(" Non-current investments");	
	        return investments;
	}
	
	public List<String> getCurrentAssets(){
        ArrayList<String> currentAssets = new ArrayList<>();

        // Add all components (no serial numbers)
        currentAssets.add("Current Investments");
        currentAssets.add("Inventories");
        currentAssets.add("Trade Receivables");
        currentAssets.add("Cash and Cash Equivalents");
        currentAssets.add("Short-term Loans and Advances");
        currentAssets.add("Other Current Assets");
        return currentAssets;
	}
	public List<String> getSalesAccount(){
        ArrayList<String> salesAccount = new ArrayList<>();

        // Add all components (no serial numbers)
        salesAccount.add("Sale of Products");
        salesAccount.add("Sale of Services");
        salesAccount.add("Other Operating Revenues");
       
        return salesAccount;
	}
	
	public List<String> getPurchaseAccounts(){
        ArrayList<String> purchaseAccounts = new ArrayList<>();

        // Add all components (no serial numbers)
        purchaseAccounts.add("Purchases of stock-in-trade");
       
        return purchaseAccounts;
	}
	public Map<String, Object> getAllTrialBalanceData(String startDate, String endDate) {
		Map<String ,Object>result=new HashMap<>();
		
		//share holder fund
		List<String> capitalAccount=getShareholderFunds();
    	Map<String,List<Voucher>>mapPrevAssets=new HashMap<>();
    	Map<String,Double>mapCountPrevAssets=new HashMap<>();
        double totalCapitalAmount=0;
    	for(String s:capitalAccount) {
    		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
    		if(ledgerType!=null &&ledgerType.getId()!=null) {
    			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
    			List<Voucher> voucher = voucherRepository.findAllByLedgerIdIn(ledgerIds);
    			mapPrevAssets.put(s, voucher);
    			double totalCredit=0;
    			double totalDebit=0;
    			double totalAmount=0;
    			for(Voucher v:voucher) {			
    						if(v.isCreditDebit()) {
    							double debitAmount =0;
    							double creditAmount =0;
    							if(v!=null && v.getDebitAmount()!=0) {
    								debitAmount =v.getDebitAmount();
    							}
    							if(v!=null && v.getCreditAmount()!=0) {
    								creditAmount =v.getCreditAmount();
    							}
    							totalCredit=totalCredit+creditAmount;
    							totalDebit=totalDebit+debitAmount;
    							totalAmount=totalAmount-debitAmount+creditAmount;
    						}else {
    							double debitAmount =v.getDebitAmount();
    							totalDebit=totalDebit+debitAmount;
    							totalAmount=totalAmount-debitAmount;
    						}
    			}
    			mapCountPrevAssets.put(s, totalAmount);
    			totalCapitalAmount=totalCapitalAmount+totalAmount;
    		}

    	}
    	result.put("Capital Account", totalCapitalAmount);
    	// ====  Loan and Liabilities =====
    	List<String> loansAndLiabilities = getUnsecuredLoans();
    	
        double totalLoansAndLiabilities=0;
    	for(String s:capitalAccount) {
    		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
    		if(ledgerType!=null &&ledgerType.getId()!=null) {
    			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
    			List<Voucher> voucher = voucherRepository.findAllByLedgerIdIn(ledgerIds);
    			double totalCredit=0;
    			double totalDebit=0;
    			double totalAmount=0;
    			for(Voucher v:voucher) {			
    						if(v.isCreditDebit()) {
    							double debitAmount =0;
    							double creditAmount =0;
    							if(v!=null && v.getDebitAmount()!=0) {
    								debitAmount =v.getDebitAmount();
    							}
    							if(v!=null && v.getCreditAmount()!=0) {
    								creditAmount =v.getCreditAmount();
    							}
    							totalCredit=totalCredit+creditAmount;
    							totalDebit=totalDebit+debitAmount;
    							totalAmount=totalAmount-debitAmount+creditAmount;
    						}else {
    							double debitAmount =v.getDebitAmount();
    							totalDebit=totalDebit+debitAmount;
    							totalAmount=totalAmount-debitAmount;
    						}
    			}
    			totalLoansAndLiabilities=totalLoansAndLiabilities+totalAmount;
    		}

    	}
    	result.put("Loans & liability", totalLoansAndLiabilities);
    	
    	//current Liabilities
    	List<String> currentLiabilities = getCurrentLiabilities();
    	
        double totalCurrentLiabilities=0;
    	for(String s:currentLiabilities) {
    		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
    		if(ledgerType!=null &&ledgerType.getId()!=null) {
    			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
    			List<Voucher> voucher = voucherRepository.findAllByLedgerIdIn(ledgerIds);
    			double totalCredit=0;
    			double totalDebit=0;
    			double totalAmount=0;
    			for(Voucher v:voucher) {			
    						if(v.isCreditDebit()) {
    							double debitAmount =0;
    							double creditAmount =0;
    							if(v!=null && v.getDebitAmount()!=0) {
    								debitAmount =v.getDebitAmount();
    							}
    							if(v!=null && v.getCreditAmount()!=0) {
    								creditAmount =v.getCreditAmount();
    							}
    							totalCredit=totalCredit+creditAmount;
    							totalDebit=totalDebit+debitAmount;
    							totalAmount=totalAmount-debitAmount+creditAmount;
    						}else {
    							double debitAmount =v.getDebitAmount();
    							totalDebit=totalDebit+debitAmount;
    							totalAmount=totalAmount-debitAmount;
    						}
    			}
    			totalCurrentLiabilities=totalCurrentLiabilities+totalAmount;
    		}

    	}
    	result.put("Current Liabilities", totalCurrentLiabilities);

    	// ====== Fixed Assets =========
    	List<String> propertyPlantEquipment = getPropertyPlantEquipment();
    	 double totalpropertyPlantEquipment=0;
     	for(String s:currentLiabilities) {
     		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
     		if(ledgerType!=null &&ledgerType.getId()!=null) {
     			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
     			List<Voucher> voucher = voucherRepository.findAllByLedgerIdIn(ledgerIds);
     			double totalCredit=0;
     			double totalDebit=0;
     			double totalAmount=0;
     			for(Voucher v:voucher) {			
     						if(v.isCreditDebit()) {
     							double debitAmount =0;
     							double creditAmount =0;
     							if(v!=null && v.getDebitAmount()!=0) {
     								debitAmount =v.getDebitAmount();
     							}
     							if(v!=null && v.getCreditAmount()!=0) {
     								creditAmount =v.getCreditAmount();
     							}
     							totalCredit=totalCredit+creditAmount;
     							totalDebit=totalDebit+debitAmount;
     							totalAmount=totalAmount-debitAmount+creditAmount;
     						}else {
     							double debitAmount =v.getDebitAmount();
     							totalDebit=totalDebit+debitAmount;
     							totalAmount=totalAmount-debitAmount;
     						}
     			}
     			totalCurrentLiabilities=totalCurrentLiabilities+totalAmount;
     		}

     	}
     	result.put("Fixed Assets", totalCurrentLiabilities);
     	
     	//=========  Current investment + Non current investment ========================
     	List<String> investment = getInvestments();
     	 double totalInvestment=0;
      	for(String s:currentLiabilities) {
      		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
      		if(ledgerType!=null &&ledgerType.getId()!=null) {
      			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
      			List<Voucher> voucher = voucherRepository.findAllByLedgerIdIn(ledgerIds);
      			double totalCredit=0;
      			double totalDebit=0;
      			double totalAmount=0;
      			for(Voucher v:voucher) {			
      						if(v.isCreditDebit()) {
      							double debitAmount =0;
      							double creditAmount =0;
      							if(v!=null && v.getDebitAmount()!=0) {
      								debitAmount =v.getDebitAmount();
      							}
      							if(v!=null && v.getCreditAmount()!=0) {
      								creditAmount =v.getCreditAmount();
      							}
      							totalCredit=totalCredit+creditAmount;
      							totalDebit=totalDebit+debitAmount;
      							totalAmount=totalAmount-debitAmount+creditAmount;
      						}else {
      							double debitAmount =v.getDebitAmount();
      							totalDebit=totalDebit+debitAmount;
      							totalAmount=totalAmount-debitAmount;
      						}
      			}
      			totalInvestment=totalInvestment+totalAmount;
      		}

      	}
      	result.put("Investment", totalInvestment);
     	
// ================ Current assets ==============
    List<String> currentAssets = getCurrentAssets();
   	double totalCurrentAssets=0;
   	for(String s:currentLiabilities) {
   		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
   		if(ledgerType!=null &&ledgerType.getId()!=null) {
   			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
   			List<Voucher> voucher = voucherRepository.findAllByLedgerIdIn(ledgerIds);
   			double totalCredit=0;
   			double totalDebit=0;
   			double totalAmount=0;
   			for(Voucher v:voucher) {			
   						if(v.isCreditDebit()) {
   							double debitAmount =0;
   							double creditAmount =0;
   							if(v!=null && v.getDebitAmount()!=0) {
   								debitAmount =v.getDebitAmount();
   							}
   							if(v!=null && v.getCreditAmount()!=0) {
   								creditAmount =v.getCreditAmount();
   							}
   							totalCredit=totalCredit+creditAmount;
   							totalDebit=totalDebit+debitAmount;
   							totalAmount=totalAmount-debitAmount+creditAmount;
   						}else {
   							double debitAmount =v.getDebitAmount();
   							totalDebit=totalDebit+debitAmount;
   							totalAmount=totalAmount-debitAmount;
   						}
   			}
   			totalInvestment=totalInvestment+totalAmount;
   		}

   	}
   	result.put("Current assets", totalCurrentAssets);
   	
   	//======================= Sales Account =========================
    List<String> salesAccount = getSalesAccount();
 	double totalSalesAccount=0;
   	for(String s:currentLiabilities) {
   		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
   		if(ledgerType!=null &&ledgerType.getId()!=null) {
   			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
   			List<Voucher> voucher = voucherRepository.findAllByLedgerIdIn(ledgerIds);
   			double totalCredit=0;
   			double totalDebit=0;
   			double totalAmount=0;
   			for(Voucher v:voucher) {			
   						if(v.isCreditDebit()) {
   							double debitAmount =0;
   							double creditAmount =0;
   							if(v!=null && v.getDebitAmount()!=0) {
   								debitAmount =v.getDebitAmount();
   							}
   							if(v!=null && v.getCreditAmount()!=0) {
   								creditAmount =v.getCreditAmount();
   							}
   							totalCredit=totalCredit+creditAmount;
   							totalDebit=totalDebit+debitAmount;
   							totalAmount=totalAmount-debitAmount+creditAmount;
   						}else {
   							double debitAmount =v.getDebitAmount();
   							totalDebit=totalDebit+debitAmount;
   							totalAmount=totalAmount-debitAmount;
   						}
   			}
   			totalSalesAccount=totalSalesAccount+totalAmount;
   		}

   	}
   	result.put("Sales Account", totalSalesAccount);
   	
   	//==========================      Purchase Accounts =====================
   	
    List<String> purchaseAccounts = getPurchaseAccounts();
 	double totalPurchaseAccounts=0;
   	for(String s:purchaseAccounts) {
   		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
   		if(ledgerType!=null &&ledgerType.getId()!=null) {
   			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
   			List<Voucher> voucher = voucherRepository.findAllByLedgerIdIn(ledgerIds);
   			double totalCredit=0;
   			double totalDebit=0;
   			double totalAmount=0;
   			for(Voucher v:voucher) {			
   						if(v.isCreditDebit()) {
   							double debitAmount =0;
   							double creditAmount =0;
   							if(v!=null && v.getDebitAmount()!=0) {
   								debitAmount =v.getDebitAmount();
   							}
   							if(v!=null && v.getCreditAmount()!=0) {
   								creditAmount =v.getCreditAmount();
   							}
   							totalCredit=totalCredit+creditAmount;
   							totalDebit=totalDebit+debitAmount;
   							totalAmount=totalAmount-debitAmount+creditAmount;
   						}else {
   							double debitAmount =v.getDebitAmount();
   							totalDebit=totalDebit+debitAmount;
   							totalAmount=totalAmount-debitAmount;
   						}
   			}
   			totalPurchaseAccounts=totalPurchaseAccounts+totalAmount;
   		}

   	}
   	result.put("Purchase Accounts", totalPurchaseAccounts);
   	
   	//=============================   
   	
		return null;
	}

}
