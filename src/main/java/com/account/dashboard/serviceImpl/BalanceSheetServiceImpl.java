package com.account.dashboard.serviceImpl;

import com.account.dashboard.domain.account.LedgerType;
import com.account.dashboard.domain.account.Voucher;
import com.account.dashboard.repository.LedgerRepository;
import com.account.dashboard.repository.LedgerTypeRepository;
import com.account.dashboard.repository.VoucherRepository;
import com.account.dashboard.service.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BalanceSheetServiceImpl implements BalanceSheetService{
	
	@Autowired
	LedgerTypeRepository ledgerTypeRepository;
	
	@Autowired
	LedgerRepository ledgerRepository;
	
	@Autowired
	VoucherRepository voucherRepository;

	@Override
	public List<Map<String, Object>> getAllBalanceSheetLiabilities(String startDate, String endDate) {

		List<String>gList=Arrays.asList("Loans",
				"Branch / Divisions","Suspense Account","Salary Payable");
		List<LedgerType> group = ledgerTypeRepository.findByNameIn(gList);
		List<Map<String, Object>>result=new ArrayList<>();
		for(LedgerType g:group) {
			System.out.println("Group name .."+g.getId());

			Map<String,Object>map=new HashMap<>();
			List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());
			System.out.println("ledgerList .."+ledgerList+"...."+g.getName());

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
	public List<Map<String, Object>> getAllBalanceSheetAssets(String startDate, String endDate) {

		List<String>gList=Arrays.asList("Capital Account",
				"Current Liabilities","	Fixed Assets","Current Assets");
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
	@Override
	public List<Map<String, Object>> getAllBalanceSheetLiabilitiesForExport(String startDate, String endDate) {

		List<String>gList=Arrays.asList("Loans",
				"Branch / Divisions","Suspense Account","Salary Payable");
		List<LedgerType> group = ledgerTypeRepository.findByNameIn(gList);
		List<Map<String, Object>>result=new ArrayList<>();
		for(LedgerType g:group) {
			System.out.println("Group name .."+g.getId());

			Map<String,Object>map=new HashMap<>();
			List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());
			System.out.println("ledgerList .."+ledgerList+"...."+g.getName());

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
	public List<Map<String, Object>> getAllBalanceSheetAssetsForExport(String startDate, String endDate) {

		List<String>gList=Arrays.asList("Capital Account",
				"Current Liabilities","	Fixed Assets","Current Assets");
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

	@Override
	public List<Map<String, Object>> getAllGroupByParentGroupId(String startDate, String endDate) {
		List<Long>list=getAllChildHierarchy();
		List<LedgerType> group = ledgerTypeRepository.findAllByIdIn(list);
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
	public List<Long>getAllAssetsChildHierarchy(){
		List<Long>result=new ArrayList<>();
		List<String>gList=Arrays.asList("Capital Account",
				"Current Liabilities","Fixed Assets","Current Assets");
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
	
	public List<Long>getAllLiabilitiesChildHierarchy(){
		List<Long>result=new ArrayList<>();
		List<String>gList=Arrays.asList("Capital Account",
				"Current Liabilities","Fixed Assets","Current Assets");
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

}
