package com.account.dashboard.serviceImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.account.LedgerType;
import com.account.dashboard.domain.account.Voucher;
import com.account.dashboard.repository.LedgerRepository;
import com.account.dashboard.repository.LedgerTypeRepository;
import com.account.dashboard.repository.VoucherRepository;
import com.account.dashboard.service.ProfitAndLossService;

@Service
public class ProfitAndLossServiceImpl implements ProfitAndLossService {
	
	@Autowired
	LedgerTypeRepository ledgerTypeRepository;
	
	@Autowired
	LedgerRepository ledgerRepository;
	
	@Autowired
	VoucherRepository voucherRepository;
	

	public double getLossCount(String startDate, String endDate) {

		List<String>gList=Arrays.asList("Purchase Account","Direct Expenses");
//		List<LedgerType> group = ledgerTypeRepository.findAll();
		List<LedgerType> group = ledgerTypeRepository.findByNameIn(gList);
		List<Map<String, Object>>result=new ArrayList<>();
		double allPurchase=0;

		for(LedgerType g:group) {
			System.out.println("Group name .."+g.getName());

			Map<String,Object>map=new HashMap<>();
			List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());
			System.out.println("ledgerList .."+ledgerList+"...."+g.getName());

//	         LedgerType ledgerType = ledgerTypeRepository.findById(g.getId()).get();
			List<Voucher>voucherList=voucherRepository.findAllByLedgerIdIn(ledgerList);
//			List<Voucher>voucherList=voucherRepository.findByLedgerIdInAndInBetween(ledgerList,startDate,endDate);

			double totalCredit=0;
			double totalDebit=0;
			double totalAmount=0;
			double indirectExpenses=0;
			System.out.println("..."+voucherList.size());
			for(Voucher v:voucherList) {			
				if(v.isCreditDebit()) {
					double debitAmount =0;
					double creditAmount =0;
					if(v!=null && v.getDebitAmount()!=0) {
						debitAmount =v.getDebitAmount();
						String gName = g.getName();
                         System.out.println("gName .. . . "+gName);
						if("Purchase Account".equals(gName) ||"Direct Expenses".equals(gName)) {
							allPurchase=allPurchase+debitAmount;
						}
						if("Indirect Incomes".equals(gName) ||"Indirect Expenses".equals(gName)) {
							indirectExpenses=indirectExpenses+debitAmount;
						}
					}
					if(v!=null && v.getCreditAmount()!=0) {
						creditAmount =v.getCreditAmount();
						String gName = g.getName();
                        System.out.println("gName .. . . "+gName);

						if("Purchase Account".equals(gName) ||"Direct Expenses".equals(gName)) {
							allPurchase=allPurchase-creditAmount;
						}
						if("Indirect Incomes".equals(gName) ||"Indirect Expenses".equals(gName)) {
							indirectExpenses=indirectExpenses-creditAmount;
						}
					}
					totalCredit=totalCredit+creditAmount;
					totalDebit=totalDebit+debitAmount;
					totalAmount=totalAmount-debitAmount+creditAmount;
					
				}else {
					double debitAmount =v.getDebitAmount();
					totalDebit=totalDebit+debitAmount;
					totalAmount=totalAmount-debitAmount;
					String gName = g.getName();
                    System.out.println("gName .. . . "+gName);

					if("Purchase Account".equals(gName) ||"Direct Expenses".equals(gName)) {
						allPurchase=allPurchase+debitAmount;
					}
					if("Indirect Incomes".equals(gName) ||"Indirect Expenses".equals(gName)) {
						indirectExpenses=indirectExpenses+debitAmount;
					}

				}
			}

		}
		return allPurchase;
	
	}

	@Override
	public Map<String, Object> getAllProfit(String startDate, String endDate) {
		
		List<Long>list=getAllProfit();
		List<LedgerType> group = ledgerTypeRepository.findAllByIdIn(list);
		Map<String, Object>res=new HashMap<>();


//		List<String>gList=Arrays.asList("Sales Account",
//				"Direct Incomes");
//		List<LedgerType> group = ledgerTypeRepository.findAll();
//		List<LedgerType> group = ledgerTypeRepository.findByNameIn(gList);
		List<Map<String, Object>>result=new ArrayList<>();
		double totalSum=0;

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
            totalSum=totalSum+totalAmount;
			result.add(map);
		}
		double loss = getLossCount(startDate,endDate);
		double grossProfit = totalSum-loss;
		res .put("totalSum", totalSum);
		res .put("data", result);
		res .put("grossProfit", grossProfit);
		return res;
	
	}

	@Override
	public Map<String, Object> getAllLoss(String startDate, String endDate) {

		List<String>gList=Arrays.asList("Purchase Account",
				"Indirect Incomes","Direct Expenses","Indirect Expenses");
//		List<LedgerType> group = ledgerTypeRepository.findAll();
		List<LedgerType> group = ledgerTypeRepository.findByNameIn(gList);
		Map<String,Object>res=new HashMap<>();
		List<Map<String, Object>>result=new ArrayList<>();
		double allPurchase=0;
		double indirectExpenses=0;

		for(LedgerType g:group) {
			System.out.println("Group name .."+g.getName());

			Map<String,Object>map=new HashMap<>();
			List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());
			System.out.println("ledgerList .."+ledgerList+"...."+g.getName());

//	         LedgerType ledgerType = ledgerTypeRepository.findById(g.getId()).get();
			List<Voucher>voucherList=voucherRepository.findAllByLedgerIdIn(ledgerList);
//			List<Voucher>voucherList=voucherRepository.findByLedgerIdInAndInBetween(ledgerList,startDate,endDate);

			double totalCredit=0;
			double totalDebit=0;
			double totalAmount=0;
//			double allPurchase=0;
//			double indirectExpenses=0;
			System.out.println("..."+voucherList.size());
			for(Voucher v:voucherList) {			
				if(v.isCreditDebit()) {
					double debitAmount =0;
					double creditAmount =0;
					if(v!=null && v.getDebitAmount()!=0) {
						debitAmount =v.getDebitAmount();
						String gName = g.getName();
                         System.out.println("gName .. . . "+gName);
						if("Purchase Account".equals(gName) ||"Direct Expenses".equals(gName)) {
							allPurchase=allPurchase+debitAmount;
						}
						if("Indirect Incomes".equals(gName) ||"Indirect Expenses".equals(gName)) {
							indirectExpenses=indirectExpenses+debitAmount;
						}
					}
					if(v!=null && v.getCreditAmount()!=0) {
						creditAmount =v.getCreditAmount();
						String gName = g.getName();
                        System.out.println("gName .. . . "+gName);

						if("Purchase Account".equals(gName) ||"Direct Expenses".equals(gName)) {
							allPurchase=allPurchase-creditAmount;
						}
						if("Indirect Incomes".equals(gName) ||"Indirect Expenses".equals(gName)) {
							indirectExpenses=indirectExpenses-creditAmount;
						}
					}
					totalCredit=totalCredit+creditAmount;
					totalDebit=totalDebit+debitAmount;
					totalAmount=totalAmount-debitAmount+creditAmount;
					
				}else {
					double debitAmount =v.getDebitAmount();
					totalDebit=totalDebit+debitAmount;
					totalAmount=totalAmount-debitAmount;
					String gName = g.getName();
                    System.out.println("gName .. . . "+gName);

					if("Purchase Account".equals(gName) ||"Direct Expenses".equals(gName)) {
						allPurchase=allPurchase+debitAmount;
					}
					if("Indirect Incomes".equals(gName) ||"Indirect Expenses".equals(gName)) {
						indirectExpenses=indirectExpenses+debitAmount;
					}

				}
			}
			map.put("totalCredit", totalCredit);
			map.put("groupName", g.getName());
			map.put("totalDebit", totalDebit);
			map .put("totalAmount", totalAmount);
			
			
			
			
//			map .put("allPurchaseLoss", allPurchase);
//			
//			double totalSale = totalSalesCount(startDate,endDate);
//            double grossProfit=totalSale-allPurchase;
//			map .put("totalSale", totalSale);
//			map .put("grossProfit", grossProfit);
//			map .put("indirectExpenses", indirectExpenses);
//			double nettProfit = grossProfit-indirectExpenses;
//			map .put("nettProfit", nettProfit);

			result.add(map);
		}
		
		
		res .put("allPurchaseLoss", allPurchase);
		res .put("data", result);

		double totalSale = totalSalesCount(startDate,endDate);
        double grossProfit=totalSale-allPurchase;
        res .put("totalSale", totalSale);
        res .put("grossProfit", grossProfit);
        res .put("indirectExpenses", indirectExpenses);
		double nettProfit = grossProfit-indirectExpenses;
		res .put("nettProfit", nettProfit);

		return res;
	
	}
	
	public double totalSalesCount(String startDate, String endDate) {

		List<Long>list=getAllLoss();
		List<LedgerType> group = ledgerTypeRepository.findAllByIdIn(list);
//
//		List<String>gList=Arrays.asList("Sales Account",
//				"Direct Incomes");
//		List<LedgerType> group = ledgerTypeRepository.findByNameIn(gList);

		double totalAmount=0;
		List<Map<String, Object>>result=new ArrayList<>();
		for(LedgerType g:group) {
			System.out.println("Group name .."+g.getId());

			Map<String,Object>map=new HashMap<>();
			List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());
			System.out.println("ledgerList .."+ledgerList+"...."+g.getName());

//			List<Voucher>voucherList=voucherRepository.findAllByLedgerIdIn(ledgerList);
			List<Voucher>voucherList=voucherRepository.findByLedgerIdInAndInBetween(ledgerList,startDate,endDate);

			double totalCredit=0;
			double totalDebit=0;
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
		}
		return totalAmount;
	
	
		
	}

	public List<Long>getAllProfit(){
		List<Long>result=new ArrayList<>();
		List<String>gList=Arrays.asList("Sales Account",
				"Direct Incomes");
		List<Long> ledgerType = ledgerTypeRepository.findIdByNameIn(gList);
		result.addAll(ledgerType);
		List<Long> childLedgerType = ledgerTypeRepository.findIdByParentIdIn(ledgerType);
		result.addAll(childLedgerType);
		List<Long> childLedgerType2 = ledgerTypeRepository.findIdByParentIdIn(childLedgerType);
		result.addAll(childLedgerType2);

		List<Long> childLedgerType3 = ledgerTypeRepository.findIdByParentIdIn(childLedgerType2);
		result.addAll(childLedgerType3);

		return result;

	}
	public List<Long>getAllLoss(){
		List<Long>result=new ArrayList<>();
		List<String>gList=Arrays.asList("Purchase Account",
				"Indirect Incomes","Direct Expenses","Indirect Expenses");
		List<Long> ledgerType = ledgerTypeRepository.findIdByNameIn(gList);
		result.addAll(ledgerType);
		List<Long> childLedgerType = ledgerTypeRepository.findIdByParentIdIn(ledgerType);
		result.addAll(childLedgerType);
		List<Long> childLedgerType2 = ledgerTypeRepository.findIdByParentIdIn(childLedgerType);
		result.addAll(childLedgerType2);

		List<Long> childLedgerType3 = ledgerTypeRepository.findIdByParentIdIn(childLedgerType2);
		result.addAll(childLedgerType3);

		return result;

	}

	@Override
    public Map<String, Object> getAllProfitAndLoss(String startDate, String endDate) {
        List<Map<String, Object>> dataList = new ArrayList<>();

        // Revenue from Operations
        List<Map<String, Object>> revenueOps = new ArrayList<>();
        revenueOps.add(createItem("Sale of Products"));
        revenueOps.add(createItem("Sale of Services"));
        revenueOps.add(createItem("Other Operating Revenues"));
        dataList.add(createItemWithChildren("Revenue from Operations", revenueOps));

        // Other Income
        List<Map<String, Object>> otherIncome = new ArrayList<>();
        otherIncome.add(createItem("Interest Income"));
        otherIncome.add(createItem("Dividend Income"));
        otherIncome.add(createItem("Net Gain/Loss on Sale of Investments"));
        otherIncome.add(createItem("Other Non-Operating Income"));
        dataList.add(createItemWithChildren("Other Income", otherIncome));

        // Total Revenue
        dataList.add(createItemWithPrice("Total Revenue", "1000"));

        // Expenses
        List<Map<String, Object>> expenses = new ArrayList<>();
        expenses.add(createItem("Cost of materials consumed"));
        expenses.add(createItem("Purchases of stock-in-trade"));
        expenses.add(createItem("Changes in inventories of finished goods, work-in-progress and stock-in-trade"));

        // Employee benefit expenses
        List<Map<String, Object>> empBenefits = new ArrayList<>();
        empBenefits.add(createItem("Salaries and wages"));
        empBenefits.add(createItem("Contribution to provident & other funds"));
        empBenefits.add(createItem("Staff welfare expenses"));
        expenses.add(createItemWithChildren("Employee benefit expenses", empBenefits));

        // Finance costs
        List<Map<String, Object>> financeCosts = new ArrayList<>();
        financeCosts.add(createItem("Interest expense"));
        financeCosts.add(createItem("Other borrowing costs"));
        expenses.add(createItemWithChildren("Finance costs", financeCosts));

        // Depreciation
        expenses.add(createItem("Depreciation and amortisation expense"));

        // Other expenses
        List<Map<String, Object>> otherExpenses = new ArrayList<>();
        otherExpenses.add(createItem("Power and fuel"));
        otherExpenses.add(createItem("Rent"));
        otherExpenses.add(createItem("Repairs and maintenance"));
        otherExpenses.add(createItem("Advertisement"));
        otherExpenses.add(createItem("Travelling and conveyance"));
        otherExpenses.add(createItem("Legal & professional fees"));
        otherExpenses.add(createItem("Bad debts written off, etc"));
        expenses.add(createItemWithChildren("Other expenses", otherExpenses));

        dataList.add(createItemWithChildren("Expenses", expenses));

        // Other single-line entries
        dataList.add(createItem("Profit before exceptional and extraordinary items and tax"));

        // Exceptional items
        List<Map<String, Object>> exceptional = new ArrayList<>();
        exceptional.add(createItem("Impairment of assets / goodwill"));
        exceptional.add(createItem("Loss on sale/disposal of a business segment or subsidiary"));
        exceptional.add(createItem("Restructuring costs"));
        exceptional.add(createItem("Write-off or write-back of major receivables or inventories"));
        exceptional.add(createItem("Compensation or damages from legal settlements"));
        exceptional.add(createItem("Gain/loss from natural calamities or exceptional events"));
        dataList.add(createItemWithChildren("Exceptional Items", exceptional));

        // Remaining titles
        dataList.add(createItem("Profit before extraordinary items and tax"));
        dataList.add(createItem("Extraordinary Items"));
        dataList.add(createItem("Profit before tax"));

        // Tax expense
        List<Map<String, Object>> taxList = new ArrayList<>();
        taxList.add(createItem("Current tax"));
        taxList.add(createItem("Deferred tax"));
        dataList.add(createItemWithChildren("Tax Expense", taxList));

        dataList.add(createItem("Profit/(Loss) for the period from continuing operations"));
        dataList.add(createItem("Profit/(Loss) from discontinuing operations"));
        dataList.add(createItem("Tax expense of discontinuing operations"));
        dataList.add(createItem("Profit/(Loss) from discontinuing operations"));
        dataList.add(createItem("Profit/(Loss) for the period"));
        dataList.add(createItem("Earnings per equity share"));

        return dataList;
    }

    // Helper methods
    private static Map<String, Object> createItem(String title) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", title);
        return map;
    }

    private static Map<String, Object> createItemWithChildren(String title, List<Map<String, Object>> children) {
        Map<String, Object> map = createItem(title);
        map.put("data", children);
        return map;
    }

    private static Map<String, Object> createItemWithPrice(String title, String price) {
        Map<String, Object> map = createItem(title);
        map.put("price", price);
        return map;
    }

}
