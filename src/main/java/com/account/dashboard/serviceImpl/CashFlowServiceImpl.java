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
import com.account.dashboard.service.CashFlowService;

@Service
public class CashFlowServiceImpl implements CashFlowService {
	
	@Autowired
	LedgerTypeRepository ledgerTypeRepository;
	
	@Autowired
	LedgerRepository ledgerRepository;
	
	@Autowired
	VoucherRepository voucherRepository;

	@Override
	public List<Map<String, Object>> getAllInFlow(String startDate, String endDate) {

		List<String>gList=Arrays.asList("Current Liabilities","Current Assets","Suspense Account"
				,"Direct Expenses","Indirect Expenses");
//		List<LedgerType> group = ledgerTypeRepository.findAll();
		List<LedgerType> group = ledgerTypeRepository.findByNameIn(gList);
		List<Map<String, Object>>result=new ArrayList<>();
		for(LedgerType g:group) {
			System.out.println("Group name .."+g.getId());

			Map<String,Object>map=new HashMap<>();
			List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());
			System.out.println("ledgerList .."+ledgerList+"...."+g.getName());

//	         LedgerType ledgerType = ledgerTypeRepository.findById(g.getId()).get();
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

	@Override
	public List<Map<String, Object>> getAllOutFlow(String startDate, String endDate) {

		List<String>gList=Arrays.asList("Loans","Current Liabilities"
				,"Current Assets","Suspense Account","Direct Expenses","Indirect Expenses");
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
	public List<String> getDirectExpenses(){
		 List<String> directExpenses = new ArrayList<>();

	        // Non-current Assets
		 directExpenses.add("Cost of Materials");
		 directExpenses.add("Inventory Adjustment");
		 directExpenses.add("Purchases");
	     return directExpenses;
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
	public List<String> getSuspenseAccount(){
        ArrayList<String> currentAssets = new ArrayList<>();

        // Add all components (no serial numbers)
        currentAssets.add("Misc Sus");
        currentAssets.add("Suspense");
        return currentAssets;
	}
	public List<String> getInDirectExpenses(){

		ArrayList<String> indirectExpenses = new ArrayList<>();

		// Add all components (no serial numbers)
		indirectExpenses.add("Salaries & Wages");
		indirectExpenses.add("Provident Fund & Other Funds");
		indirectExpenses.add("Staff Welfare");
		indirectExpenses.add("Finance Costs");
		indirectExpenses.add("Depreciation");
		indirectExpenses.add("Power & Fuel");
		indirectExpenses.add("Rent");
		indirectExpenses.add("Repairs & Maintenance");
		indirectExpenses.add("Advertisement & Marketing");
		indirectExpenses.add("Travelling & Conveyance");
		indirectExpenses.add("Legal & Professional Fees");
		indirectExpenses.add("Bad Debts / Provision");
		return indirectExpenses;
	}
	public Map<String, Object> getAllCashInFlow(String startDate, String endDate) {
		Map<String ,Object>result=new HashMap<>();

    	// ===================== In Flow current Liabilities ==============================
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
    	
    	// ================ Current assets ==============
        List<String> currentAssets = getCurrentAssets();
       	double totalCurrentAssets=0;
       	for(String s:currentAssets) {
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
       			totalCurrentAssets=totalCurrentAssets+totalAmount;
       		}

       	}
       	result.put("Current assets", totalCurrentAssets);
       	
       	//
    	// ================ Suspense account ==============
        List<String> suspenseAccount = getSuspenseAccount();
       	double totalSuspenseAccount=0;
       	for(String s:suspenseAccount) {
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
       			totalSuspenseAccount=totalSuspenseAccount+totalAmount;
       		}

       	}
       	result.put("Suspense A/c", totalSuspenseAccount);
       	
       	
    	// ================ InDirect Expenses account ==============
        List<String> inDirectExpenses = getInDirectExpenses();
       	double totalInDirectExpenses=0;
       	for(String s:inDirectExpenses) {
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
       			totalInDirectExpenses=totalInDirectExpenses+totalAmount;
       		}

       	}
       	result.put("Indirect Expenses", totalInDirectExpenses);
       	// get direct expenses =====================
        List<String> directExpenses = getDirectExpenses();
       	double totalDirectExpenses=0;
       	for(String s:directExpenses) {
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
       			totalDirectExpenses=totalDirectExpenses+totalAmount;
       		}

       	}
       	result.put("Direct Expenses", totalDirectExpenses);
       	result.put("name", "Inflow of Cash");
       	result.put("totalAmount", 100);
		return result;
	}
	
	public Map<String, Object> getAllOutFlowData(String startDate, String endDate) {

		Map<String ,Object>result=new HashMap<>();

    	// ===================== In Flow current Liabilities ==============================
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
    	
    	// ================ Current assets ==============
        List<String> currentAssets = getCurrentAssets();
       	double totalCurrentAssets=0;
       	for(String s:currentAssets) {
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
       			totalCurrentAssets=totalCurrentAssets+totalAmount;
       		}

       	}
       	result.put("Current assets", totalCurrentAssets);
       	
       	//
    	// ================ Suspense account ==============
        List<String> suspenseAccount = getSuspenseAccount();
       	double totalSuspenseAccount=0;
       	for(String s:suspenseAccount) {
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
       			totalSuspenseAccount=totalSuspenseAccount+totalAmount;
       		}

       	}
       	result.put("Suspense A/c", totalSuspenseAccount);
       	
       	
    	// ================ InDirect Expenses account ==============
        List<String> inDirectExpenses = getInDirectExpenses();
       	double totalInDirectExpenses=0;
       	for(String s:inDirectExpenses) {
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
       			totalInDirectExpenses=totalInDirectExpenses+totalAmount;
       		}

       	}
       	result.put("Indirect Expenses", totalInDirectExpenses);
       	// get direct expenses =====================
        List<String> directExpenses = getDirectExpenses();
       	double totalDirectExpenses=0;
       	for(String s:directExpenses) {
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
       			totalDirectExpenses=totalDirectExpenses+totalAmount;
       		}

       	}
       	result.put("Direct Expenses", totalDirectExpenses);      	
       	result.put("name", "Outflow of Cash");
       	result.put("totalAmount", 100);

		return result;
	
	}

	@Override
	public List<Map<String,Object>> getAllCashInAndOutFlow(String startDate, String endDate) {
        List<Map<String ,Object>>result=new ArrayList<>();
        Map<String, Object> outFlow = getAllOutFlowData( startDate,  endDate);
        Map<String, Object> inFlow =getAllCashInFlow(startDate,endDate);
        result.add(outFlow);
        result.add(inFlow);

		return result;
	}
	
	

}
