package com.account.dashboard.serviceImpl;

import java.time.LocalDateTime;
import java.time.Year;
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
	   public List<String> getAllLiabilities(){
	        ArrayList<String> incomeStatement = new ArrayList<>(Arrays.asList(
	                "Revenue from Operations",
	                "Sale of Products",
	                "Sale of Services",
	                "Other Operating Revenues",
	                
	                "Other Income",
	                "Interest Income",
	                "Dividend Income",
	                "Net Gain/Loss on Sale of Investments",
	                "Other Non-Operating Income",
	                
	                "Total Revenue (Revenue from Operations + Other Income)",
	                
	                "Expenses",
	                "Cost of materials consumed",
	                "Purchases of stock-in-trade",
	                "Changes in inventories of finished goods, work-in-progress and stock-in-trade",
	                
	                "Employee benefit expenses",
	                "Salaries and wages",
	                "Contribution to provident & other funds",
	                "Staff welfare expenses",
	                
	                "Finance costs",
	                "Interest expense",
	                "Other borrowing costs",
	                
	                "Depreciation and amortisation expense",
	                
	                "Other expenses",
	                "Power and fuel",
	                "Rent",
	                "Repairs and maintenance",
	                "Advertisement",
	                "Travelling and conveyance",
	                "Legal & professional fees",
	                "Bad debts written off, etc.",
	                
	                "Total Expenses",
	                
	                "Profit before exceptional and extraordinary items and tax",
	                
	                "Exceptional Items",
	                "Loss on sale/disposal of a business segment or subsidiary",
	                "Impairment of assets / goodwill",
	                "Restructuring costs",
	                "Write-off or write-back of major receivables or inventories",
	                "Compensation or damages from legal settlements",
	                "Gain/loss from natural calamities or exceptional events",
	                
	                "Profit before extraordinary items and tax",
	                "Extraordinary Items",
	                "Profit before tax",
	                
	                "Tax Expense",
	                "Current tax",
	                "Deferred tax",
	                
	                "Profit/(Loss) for the period from continuing operations",
	                "Profit/(Loss) from discontinuing operations",
	                "Tax expense of discontinuing operations",
	                "Profit/(Loss) from discontinuing operations (after tax)",
	                "Profit/(Loss) for the period",
	                
	                "Earnings per equity share",
	                "Basic"
	            ));
	    	  
	    	  return incomeStatement;
	    }


	@Override
    public Map<String, Object> getAllProfitAndLoss(String startDate, String endDate) {

    	Year currentYear = Year.now();

    	// Start of current year: January 1st, 00:00:00
    	String startOfYear = LocalDateTime.of(
    			currentYear.getValue(), 1, 1, 0, 0, 0).toString();

    	// End of current year: December 31st, 23:59:59
    	String endOfYear = LocalDateTime.of(
    			currentYear.getValue(), 12, 31, 23, 59, 59).toString();

    	List<String> assetsGroup = getAllLiabilities();
    	Map<String,List<Voucher>>map=new HashMap<>();
    	Map<String,Double>mapCount=new HashMap<>();

    	for(String s:assetsGroup) {
    		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
    		if(ledgerType!=null &&ledgerType.getId()!=null) {
    			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
    			List<Voucher> voucher = voucherRepository.findByLedgerIdInAndInBetween(ledgerIds,startOfYear,endOfYear);
//    			List<Voucher> findByLedgerIdInAndInBetween(List<Long> ledgerList,String d1, String d2);
    			map.put(s, voucher);
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
    			mapCount.put(s, totalAmount);

    		}

    	}
//== = = = = = = == = = = = = = = = == previous Year = = = = === = = = = == = = =   
        int cYear = Year.now().getValue();

    	int previousYear = cYear - 1;

        // Start of previous year: Jan 1, 00:00:00
        String startOfPreviousYear = LocalDateTime.of(previousYear, 1, 1, 0, 0, 0).toString();

        // End of previous year: Dec 31, 23:59:59
        String endOfPreviousYear = LocalDateTime.of(previousYear, 12, 31, 23, 59, 59).toString();


    	List<String> prevAssetsGroup = getAllLiabilities();
    	Map<String,List<Voucher>>mapPrev=new HashMap<>();
    	Map<String,Double>mapCountPrev=new HashMap<>();

    	for(String s:prevAssetsGroup) {
    		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
    		if(ledgerType!=null &&ledgerType.getId()!=null) {
    			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
//    			List<Voucher> voucher = voucherRepository.findAllByLedgerIdIn(ledgerIds);
    			
    			List<Voucher> voucher = voucherRepository.findByLedgerIdInAndInBetween(ledgerIds,startOfPreviousYear,endOfPreviousYear);

    			mapPrev.put(s, voucher);
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
    			mapCountPrev.put(s, totalAmount);

    		}

    	}
        // Revenue from Operations
    	List<Map<String, Object>> dataList = new ArrayList<>();

        // 1. Revenue from Operations
    	double currSalesProduct=mapCount.get("Sale of Products")!=null?mapCount.get("Sale of Products"):0;
    	double preSalesProduct=mapCountPrev.get("Sale of Products")!=null?mapCountPrev.get("Sale of Products"):0;
    	double currSalesService=mapCount.get("Sale of Services")!=null?mapCount.get("Sale of Services"):0;
    	double preSalesService=mapCountPrev.get("Sale of Services")!=null?mapCountPrev.get("Sale of Services"):0;
    	double currOperatingRevenue = mapCount.get("Other Operating Revenues")!=null?mapCount.get("Other Operating Revenues"):0;
    	double preOperatingRevenue = mapCountPrev.get("Other Operating Revenues")!=null?mapCountPrev.get("Other Operating Revenues"):0;
    	
    	double currRevenueOperations=currSalesProduct+currSalesService+currOperatingRevenue;
    	double preRevenueOperations=preSalesProduct+preSalesService+preOperatingRevenue;
    	
    	dataList.add(Map.of(
                "title", "Revenue from Operations",
                "data", Arrays.asList(
                        Map.of("title", "Sale of Products","currentAmount",mapCount.get("Sale of Products")!=null?mapCount.get("Sale of Products"):0,"prevAmount",mapCountPrev.get("Sale of Products")!=null?mapCountPrev.get("Sale of Products"):0),
                        Map.of("title", "Sale of Services","currentAmount",mapCount.get("Sale of Services")!=null?mapCount.get("Sale of Services"):0,"prevAmount",mapCountPrev.get("Sale of Services")!=null?mapCountPrev.get("Sale of Services"):0),
                        Map.of("title", "Other Operating Revenues","currentAmount",mapCount.get("Other Operating Revenues")!=null?mapCount.get("Other Operating Revenues"):0,"prevAmount",mapCountPrev.get("Other Operating Revenues")!=null?mapCountPrev.get("Other Operating Revenues"):0)
                )
        ));

        // 2. Other Income
    	double currInterestIncomet=mapCount.get("Interest Income")!=null?mapCount.get("Interest Income"):0;
    	double preInterestIncome=mapCountPrev.get("Interest Income")!=null?mapCountPrev.get("Interest Income"):0;
    	double currDividendIncome=mapCount.get("Dividend Income")!=null?mapCount.get("Dividend Income"):0;
    	double preDividendIncome=mapCountPrev.get("Dividend Income")!=null?mapCountPrev.get("Dividend Income"):0;
    	double currSaleInvestments = mapCount.get("Net Gain/Loss on Sale of Investments")!=null?mapCount.get("Net Gain/Loss on Sale of Investments"):0;
    	double preSaleInvestments = mapCountPrev.get("Net Gain/Loss on Sale of Investments")!=null?mapCountPrev.get("Net Gain/Loss on Sale of Investments"):0;
    	double currNonOperatingIncome = mapCount.get("Other Non-Operating Income")!=null?mapCount.get("Other Non-Operating Income"):0;
    	double preNonOperatingIncome = mapCountPrev.get("Other Non-Operating Income")!=null?mapCountPrev.get("Other Non-Operating Income"):0;
       
    	double currOtherIncome = currInterestIncomet+currDividendIncome+currSaleInvestments+currNonOperatingIncome;
        double preOtherIncome = preInterestIncome+preDividendIncome+preSaleInvestments+preNonOperatingIncome;		
        dataList.add(Map.of(
                "title", "Other Income",
                "data", Arrays.asList(
                        Map.of("title", "Interest Income", "price", "1000","currentAmount",mapCount.get("Interest Income")!=null?mapCount.get("Interest Income"):0,"prevAmount",mapCountPrev.get("Interest Income")!=null?mapCountPrev.get("Interest Income"):0),
                        Map.of("title", "Dividend Income", "price", "1000","currentAmount",mapCount.get("Dividend Income")!=null?mapCount.get("Dividend Income"):0,"prevAmount",mapCountPrev.get("Dividend Income")!=null?mapCountPrev.get("Dividend Income"):0),
                        Map.of("title", "Net Gain/Loss on Sale of Investments", "price", "1000","currentAmount",mapCount.get("Net Gain/Loss on Sale of Investments")!=null?mapCount.get("Net Gain/Loss on Sale of Investments"):0,"prevAmount",mapCountPrev.get("Net Gain/Loss on Sale of Investments")!=null?mapCountPrev.get("Net Gain/Loss on Sale of Investments"):0),
                        Map.of("title", "Other Non-Operating Income", "price", "1000","currentAmount",mapCount.get("Other Non-Operating Income")!=null?mapCount.get("Other Non-Operating Income"):0,"prevAmount",mapCountPrev.get("Other Non-Operating Income")!=null?mapCountPrev.get("Other Non-Operating Income"):0)
                )
        ));

        // 3. Total Revenue
          double currTotalRevenue = currRevenueOperations+currOtherIncome;
          double preTotalRevenue = preRevenueOperations+preOtherIncome;
        dataList.add(Map.of("title", "Total Revenue", "price", "1000","currentAmount",currTotalRevenue,"prevAmount",preTotalRevenue));

        // 4. Expenses
    	double currCostMaterials=mapCount.get("Cost of materia consumed")!=null?mapCount.get("Cost of materials consumed"):0;
    	double preCostMaterials=mapCountPrev.get("Cost of materials consumed")!=null?mapCountPrev.get("Cost of materials consumed"):0;
    	double currPurchasesStock=mapCount.get("Purchases of stock-in-trade")!=null?mapCount.get("Purchases of stock-in-trade"):0;
    	double prePurchasesStock=mapCountPrev.get("Purchases of stock-in-trade")!=null?mapCountPrev.get("Purchases of stock-in-trade"):0;
    	double currInventoriesGoods=mapCount.get("Changes in inventories of finished goods, work-in-progress and stock-in-trade")!=null?mapCount.get("Changes in inventories of finished goods, work-in-progress and stock-in-trade"):0;
    	double preInventoriesGoods=mapCountPrev.get("Changes in inventories of finished goods, work-in-progress and stock-in-trade")!=null?mapCountPrev.get("Changes in inventories of finished goods, work-in-progress and stock-in-trade"):0;
    	

    	List<Map<String, Object>> expenses = new ArrayList<>();
        expenses.add(Map.of("title", "Cost of materials consumed", "price", "1000","currentAmount",mapCount.get("Cost of materials consumed")!=null?mapCount.get("Cost of materials consumed"):0,"prevAmount",mapCountPrev.get("Cost of materials consumed")!=null?mapCountPrev.get("Cost of materials consumed"):0));
        expenses.add(Map.of("title", "Purchases of stock-in-trade", "price", "1000","currentAmount",mapCount.get("Purchases of stock-in-trade")!=null?mapCount.get("Purchases of stock-in-trade"):0,"prevAmount",mapCountPrev.get("Purchases of stock-in-trade")!=null?mapCountPrev.get("Purchases of stock-in-trade"):0));
        expenses.add(Map.of("title", "Changes in inventories of finished goods, work-in-progress and stock-in-trade", "price", "1000","currentAmount",mapCount.get("Changes in inventories of finished goods, work-in-progress and stock-in-trade")!=null?mapCount.get("Changes in inventories of finished goods, work-in-progress and stock-in-trade"):0,"prevAmount",mapCountPrev.get("Changes in inventories of finished goods, work-in-progress and stock-in-trade")!=null?mapCountPrev.get("Changes in inventories of finished goods, work-in-progress and stock-in-trade"):0));

        // Employee benefit expenses (nested)
        
    	double currSalaries=mapCount.get("Salaries and wages")!=null?mapCount.get("Salaries and wages"):0;
    	double preSalaries=mapCountPrev.get("Salaries and wages")!=null?mapCountPrev.get("Salaries and wages"):0;
    	double currContributionToProvident=mapCount.get("Contribution to provident & other funds")!=null?mapCount.get("Contribution to provident & other funds"):0;
    	double preContributionToProvident=mapCountPrev.get("Contribution to provident & other funds")!=null?mapCountPrev.get("Contribution to provident & other funds"):0;
    	double currStaffWelfare = mapCount.get("Staff welfare expenses")!=null?mapCount.get("Staff welfare expenses"):0;
    	double preStaffWelfare = mapCountPrev.get("Staff welfare expenses")!=null?mapCountPrev.get("Staff welfare expenses"):0;
    	double currEmployeeBenefitExpenses = currSalaries+currContributionToProvident+currStaffWelfare;
    	double preEmployeeBenefitExpenses = preSalaries+preContributionToProvident+preStaffWelfare;
    	expenses.add(Map.of(
                "title", "Employee benefit expenses",
                "data", Arrays.asList(
                        Map.of("title", "Salaries and wages", "price", "1000","currentAmount",mapCount.get("Salaries and wages")!=null?mapCount.get("Salaries and wages"):0,"prevAmount",mapCountPrev.get("Salaries and wages")!=null?mapCountPrev.get("Salaries and wages"):0),
                        Map.of("title", "Contribution to provident & other funds", "price", "1000","currentAmount",mapCount.get("Contribution to provident & other funds")!=null?mapCount.get("Contribution to provident & other funds"):0,"prevAmount",mapCountPrev.get("Contribution to provident & other funds")!=null?mapCountPrev.get("Contribution to provident & other funds"):0),
                        Map.of("title", "Staff welfare expenses", "price", "1000","currentAmount",mapCount.get("Staff welfare expenses")!=null?mapCount.get("Staff welfare expenses"):0,"prevAmount",mapCountPrev.get("Staff welfare expenses")!=null?mapCountPrev.get("Staff welfare expenses"):0)
                )
        ));

        // Finance costs (nested)
    	double currInterestExpense=mapCount.get("Interest expense")!=null?mapCount.get("Interest expense"):0;
    	double preInterestExpense=mapCountPrev.get("Interest expense")!=null?mapCountPrev.get("Interest expense"):0;
    	double currOtherBorrowing=mapCount.get("Other borrowing costs")!=null?mapCount.get("Other borrowing costs"):0;
    	double preOtherBorrowing=mapCountPrev.get("Other borrowing costs")!=null?mapCountPrev.get("Other borrowing costs"):0;
    	double currFinanceCosts = currInterestExpense+currOtherBorrowing;
    	double preFinanceCosts = preInterestExpense+preOtherBorrowing;
        expenses.add(Map.of(
                "title", "Finance costs",
                "data", Arrays.asList(
                        Map.of("title", "Interest expense", "price", "1000","currentAmount",mapCount.get("Interest expense")!=null?mapCount.get("Interest expense"):0,"prevAmount",mapCountPrev.get("Interest expense")!=null?mapCountPrev.get("Interest expense"):0),
                        Map.of("title", "Other borrowing costs", "price", "1000","currentAmount",mapCount.get("Other borrowing costs")!=null?mapCount.get("Other borrowing costs"):0,"prevAmount",mapCountPrev.get("Other borrowing costs")!=null?mapCountPrev.get("Other borrowing costs"):0)
                )
        ));

        // Depreciation
        expenses.add(Map.of("title", "Depreciation and amortisation expense","currentAmount",mapCount.get("Depreciation and amortisation expense")!=null?mapCount.get("Depreciation and amortisation expense"):0,"prevAmount",mapCountPrev.get("Depreciation and amortisation expense")!=null?mapCountPrev.get("Depreciation and amortisation expense"):0));

        // Other expenses (nested)
    	double currPowerAndFuel=mapCount.get("Power and fuel")!=null?mapCount.get("Power and fuel"):0;
    	double prePowerAndFuel=mapCountPrev.get("Power and fuel")!=null?mapCountPrev.get("Power and fuel"):0;
    	double currRent=mapCount.get("Rent")!=null?mapCount.get("Rent"):0;
    	double preRent=mapCountPrev.get("Rent")!=null?mapCountPrev.get("Rent"):0;
    	double currRepairs = mapCount.get("Repairs and maintenance")!=null?mapCount.get("Repairs and maintenance"):0;
    	double preRepairs = mapCountPrev.get("Repairs and maintenance")!=null?mapCountPrev.get("Repairs and maintenance"):0;
    	double currAdvertisement = mapCount.get("Advertisement")!=null?mapCount.get("Advertisement"):0;
    	double preAdvertisement = mapCountPrev.get("Advertisement")!=null?mapCountPrev.get("Advertisement"):0;
       	double currTravelling = mapCount.get("Travelling and conveyance")!=null?mapCount.get("Travelling and conveyance"):0;
    	double preTravelling = mapCountPrev.get("Travelling and conveyance")!=null?mapCountPrev.get("Travelling and conveyance"):0;
    	double currLegal = mapCount.get("Legal & professional fees")!=null?mapCount.get("Legal & professional fees"):0;
    	double preLegal = mapCountPrev.get("Legal & professional fees")!=null?mapCountPrev.get("Legal & professional fees"):0;
       	double currBad = mapCount.get("Bad debts written off, etc")!=null?mapCount.get("Bad debts written off, etc"):0;
    	double preBad = mapCountPrev.get("Bad debts written off, etc")!=null?mapCountPrev.get("Bad debts written off, etc"):0;
    	
    	double currOtherExpenses = currPowerAndFuel+currRent+currRepairs+currAdvertisement+currTravelling+currLegal+currBad;
    	double preOtherExpenses = prePowerAndFuel+preRent+preRepairs+preAdvertisement+preTravelling+preLegal+preBad;
    	expenses.add(Map.of(
                "title", "Other expenses",
                "data", Arrays.asList(
                        Map.of("title", "Power and fuel","currentAmount",mapCount.get("Power and fuel")!=null?mapCount.get("Power and fuel"):0,"prevAmount",mapCountPrev.get("Power and fuel")!=null?mapCountPrev.get("Power and fuel"):0),
                        Map.of("title", "Rent","currentAmount",mapCount.get("Rent")!=null?mapCount.get("Rent"):0,"prevAmount",mapCountPrev.get("Rent")!=null?mapCountPrev.get("Rent"):0),
                        Map.of("title", "Repairs and maintenance","currentAmount",mapCount.get("Repairs and maintenance")!=null?mapCount.get("Repairs and maintenance"):0,"prevAmount",mapCountPrev.get("Repairs and maintenance")!=null?mapCountPrev.get("Repairs and maintenance"):0),
                        Map.of("title", "Advertisement","currentAmount",mapCount.get("Advertisement")!=null?mapCount.get("Advertisement"):0,"prevAmount",mapCountPrev.get("Advertisement")!=null?mapCountPrev.get("Advertisement"):0),
                        Map.of("title", "Travelling and conveyance","currentAmount",mapCount.get("Travelling and conveyance")!=null?mapCount.get("Travelling and conveyance"):0,"prevAmount",mapCountPrev.get("Travelling and conveyance")!=null?mapCountPrev.get("Travelling and conveyance"):0),
                        Map.of("title", "Legal & professional fees","currentAmount",mapCount.get("Legal & professional fees")!=null?mapCount.get("Legal & professional fees"):0,"prevAmount",mapCountPrev.get("Legal & professional fees")!=null?mapCountPrev.get("Legal & professional fees"):0),
                        Map.of("title", "Bad debts written off, etc","currentAmount",mapCount.get("Bad debts written off, etc")!=null?mapCount.get("Bad debts written off, etc"):0,"prevAmount",mapCountPrev.get("Travelling and conveyance")!=null?mapCountPrev.get("Travelling and conveyance"):0)
                )
        ));
    	double currExpenses = currCostMaterials+currPurchasesStock+currInventoriesGoods+currEmployeeBenefitExpenses+currFinanceCosts+currOtherExpenses;
    	double preExpenses = preCostMaterials+prePurchasesStock+preInventoriesGoods+preEmployeeBenefitExpenses+preFinanceCosts+preOtherExpenses;
    	//Total Expenses
        dataList.add(Map.of("title", "Total Expenses","currentAmount",currExpenses,"prevAmount",preExpenses));

        // 5. Profit before exceptional and extraordinary items and tax
        double currProfitExceptional = currTotalRevenue-currExpenses;
        double preProfitExceptional = preTotalRevenue-preExpenses;

        dataList.add(Map.of("title", "Profit before exceptional and extraordinary items and tax","currentAmount",currProfitExceptional,"prevAmount",preProfitExceptional));

        // 6. Exceptional Items
    	double currImpairment=mapCount.get("Impairment of assets / goodwill")!=null?mapCount.get("Impairment of assets / goodwill"):0;
    	double preImpairment=mapCountPrev.get("Impairment of assets / goodwill")!=null?mapCountPrev.get("Impairment of assets / goodwill"):0;
    	double currLoss=mapCount.get("Loss on sale/disposal of a business segment or subsidiary")!=null?mapCount.get("Loss on sale/disposal of a business segment or subsidiary"):0;
    	double preLoss=mapCountPrev.get("Loss on sale/disposal of a business segment or subsidiary")!=null?mapCountPrev.get("Loss on sale/disposal of a business segment or subsidiary"):0;
    	double currRestructuring = mapCount.get("Restructuring costs")!=null?mapCount.get("Restructuring costs"):0;
    	double preRestructuring = mapCountPrev.get("Restructuring costs")!=null?mapCountPrev.get("Restructuring costs"):0;
    	double currInventories = mapCount.get("Write-off or write-back of major receivables or inventories")!=null?mapCount.get("Write-off or write-back of major receivables or inventories"):0;
    	double preInventories = mapCountPrev.get("Write-off or write-back of major receivables or inventories")!=null?mapCountPrev.get("Write-off or write-back of major receivables or inventories"):0;
       	double currCompensation = mapCount.get("Compensation or damages from legal settlements")!=null?mapCount.get("Compensation or damages from legal settlements"):0;
    	double preCompensation = mapCountPrev.get("Compensation or damages from legal settlements")!=null?mapCountPrev.get("Compensation or damages from legal settlements"):0;
    	double currGainAndLoss = mapCount.get("Gain/loss from natural calamities or exceptional events")!=null?mapCount.get("Gain/loss from natural calamities or exceptional events"):0;
    	double preGainAndLoss = mapCountPrev.get("Gain/loss from natural calamities or exceptional events")!=null?mapCountPrev.get("Gain/loss from natural calamities or exceptional events"):0;
    	double currExceptionalItems = currImpairment+currLoss+currRestructuring+currInventories+currCompensation+currGainAndLoss;
    	double preExceptionalItems  = preImpairment+preLoss+preRestructuring+preInventories+preCompensation+preGainAndLoss;
        dataList.add(Map.of(
                "title", "Exceptional Items",
                "data", Arrays.asList(
                        Map.of("title", "Impairment of assets / goodwill","currentAmount",mapCount.get("Impairment of assets / goodwill")!=null?mapCount.get("Impairment of assets / goodwill"):0,"prevAmount",mapCountPrev.get("Impairment of assets / goodwill")!=null?mapCountPrev.get("Impairment of assets / goodwill"):0),
                        Map.of("title", "Loss on sale/disposal of a business segment or subsidiary","currentAmount",mapCount.get("Loss on sale/disposal of a business segment or subsidiary")!=null?mapCount.get("Loss on sale/disposal of a business segment or subsidiary"):0,"prevAmount",mapCountPrev.get("Loss on sale/disposal of a business segment or subsidiary")!=null?mapCountPrev.get("Loss on sale/disposal of a business segment or subsidiary"):0),
                        Map.of("title", "Restructuring costs","currentAmount",mapCount.get("Restructuring costs")!=null?mapCount.get("Restructuring costs"):0,"prevAmount",mapCountPrev.get("Restructuring costs")!=null?mapCountPrev.get("Restructuring costs"):0),
                        Map.of("title", "Write-off or write-back of major receivables or inventories","currentAmount",mapCount.get("Write-off or write-back of major receivables or inventories")!=null?mapCount.get("Write-off or write-back of major receivables or inventories"):0,"prevAmount",mapCountPrev.get("Write-off or write-back of major receivables or inventories")!=null?mapCountPrev.get("Write-off or write-back of major receivables or inventories"):0),
                        Map.of("title", "Compensation or damages from legal settlements","currentAmount",mapCount.get("Compensation or damages from legal settlements")!=null?mapCount.get("Compensation or damages from legal settlements"):0,"prevAmount",mapCountPrev.get("Compensation or damages from legal settlements")!=null?mapCountPrev.get("Compensation or damages from legal settlements"):0),
                        Map.of("title", "Gain/loss from natural calamities or exceptional events","currentAmount",mapCount.get("Gain/loss from natural calamities or exceptional events")!=null?mapCount.get("Gain/loss from natural calamities or exceptional events"):0,"prevAmount",mapCountPrev.get("Gain/loss from natural calamities or exceptional events")!=null?mapCountPrev.get("Gain/loss from natural calamities or exceptional events"):0)
                )
        ));

        // 7–15. Other sections
        double currProfitBeforeExtraordinary=currProfitExceptional-currExceptionalItems;
        double preProfitBeforeExtraordinary=preProfitExceptional-preExceptionalItems;
        
        dataList.add(Map.of("title", "Profit before extraordinary items and tax","currentAmount",currProfitBeforeExtraordinary,"prevAmount",preProfitBeforeExtraordinary));
        double currExtraordinaryItems = mapCount.get("Extraordinary Items")!=null?mapCount.get("Extraordinary Items"):0;
        double preExtraordinaryItems = mapCountPrev.get("Extraordinary Items")!=null?mapCountPrev.get("Extraordinary Items"):0;
        dataList.add(Map.of("title", "Extraordinary Items","currentAmount",currExtraordinaryItems,"prevAmount",preExtraordinaryItems));
       
        double currProfitBeforeTax = currProfitBeforeExtraordinary-currExtraordinaryItems;
        double preProfitBeforeTax = preProfitBeforeExtraordinary-preExtraordinaryItems;
        dataList.add(Map.of("title", "Profit before tax","currentAmount",currProfitBeforeTax,"prevAmount",preProfitBeforeTax));
       
        
    	double currCurrentTax=mapCount.get("Current tax")!=null?mapCount.get("Current tax"):0;
    	double preCurrentTax=mapCountPrev.get("Current tax")!=null?mapCountPrev.get("Current tax"):0;
    	double currDeferredTax=mapCount.get("Deferred tax")!=null?mapCount.get("Deferred tax"):0;
    	double preDeferredTax=mapCountPrev.get("Deferred tax")!=null?mapCountPrev.get("Deferred tax"):0;
    	
    	double currTaxExpense = currCurrentTax+currDeferredTax;
    	double preTaxExpense = preCurrentTax+preDeferredTax;
    	
        dataList.add(Map.of(
                "title", "Tax Expense",
                "data", Arrays.asList(
                        Map.of("title", "Current tax","currentAmount",mapCount.get("Current tax")!=null?mapCount.get("Current tax"):0,"prevAmount",mapCountPrev.get("Current tax")!=null?mapCountPrev.get("Current tax"):0),
                        Map.of("title", "Deferred tax","currentAmount",mapCount.get("Deferred tax")!=null?mapCount.get("Deferred tax"):0,"prevAmount",mapCountPrev.get("Deferred tax")!=null?mapCountPrev.get("Deferred tax"):0)
                )
        ));
        
        double currContinuingOperation = currProfitBeforeTax-currTaxExpense;
        double preContinuingOperation = preProfitBeforeTax-preTaxExpense;
        dataList.add(Map.of("title", "Profit/(Loss) for the period from continuing operations","currentAmount",currContinuingOperation,"prevAmount",preContinuingOperation));
       
        
        dataList.add(Map.of("title", "Profit/(Loss) from discontinuing operations", "price", "1000","currentAmount",mapCount.get("Profit/(Loss) from discontinuing operations")!=null?mapCount.get("Profit/(Loss) from discontinuing operations"):0,"prevAmount",mapCountPrev.get("Profit/(Loss) from discontinuing operations")!=null?mapCountPrev.get("Profit/(Loss) from discontinuing operations"):0));
        dataList.add(Map.of("title", "Tax expense of discontinuing operations", "price", "1000","currentAmount",mapCount.get("Tax expense of discontinuing operations")!=null?mapCount.get("Tax expense of discontinuing operations"):0,"prevAmount",mapCountPrev.get("Tax expense of discontinuing operations")!=null?mapCountPrev.get("Tax expense of discontinuing operations"):0));
         double currDiscontinuingOperations = mapCount.get("Profit/(Loss) from discontinuing operations")!=null?mapCount.get("Profit/(Loss) from discontinuing operations"):0;
         double preDiscontinuingOperations =mapCountPrev.get("Profit/(Loss) from discontinuing operations")!=null?mapCountPrev.get("Profit/(Loss) from discontinuing operations"):0;
       
         
         dataList.add(Map.of("title", "Profit/(Loss) from discontinuing operations","currentAmount",mapCount.get("Profit/(Loss) from discontinuing operations")!=null?mapCount.get("Profit/(Loss) from discontinuing operations"):0,"prevAmount",mapCountPrev.get("Profit/(Loss) from discontinuing operations")!=null?mapCountPrev.get("Profit/(Loss) from discontinuing operations"):0));
        double currPeriod = currContinuingOperation+currDiscontinuingOperations;
        double prePeriod = preContinuingOperation+preDiscontinuingOperations;
        dataList.add(Map.of("title", "Profit/(Loss) for the period", "price", "1000","currentAmount",currPeriod,"prevAmount",prePeriod));
        dataList.add(Map.of("title", "Earnings per equity share", "price", "1000","currentAmount",mapCount.get("Earnings per equity share")!=null?mapCount.get("Earnings per equity share"):0,"prevAmount",mapCountPrev.get("Earnings per equity share")!=null?mapCountPrev.get("Earnings per equity share"):0));

        // Final response map
        Map<String, Object> response = new HashMap<>();
        response.put("data", dataList);

        return response;
	}
}
