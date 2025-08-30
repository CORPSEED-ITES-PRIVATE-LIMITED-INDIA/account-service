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
	public List<Map<String, Object>> getAllProfit(String startDate, String endDate) {

		List<String>gList=Arrays.asList("Sales Account",
				"Direct Incomes");
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
			double loss = getLossCount(startDate,endDate);
			double grossProfit = totalAmount-loss;
			map.put("totalCredit", totalCredit);
			map.put("groupName", g.getName());
			map.put("totalDebit", totalDebit);
			map .put("totalAmount", totalAmount);
			map .put("grossProfit", grossProfit);

			result.add(map);
		}
		return result;
	
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


		List<String>gList=Arrays.asList("Sales Account",
				"Direct Incomes");
		double totalAmount=0;
		List<LedgerType> group = ledgerTypeRepository.findByNameIn(gList);
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



}
