package com.account.dashboard.serviceImpl;

import com.account.dashboard.domain.account.Ledger;
import com.account.dashboard.domain.account.LedgerType;
import com.account.dashboard.domain.account.Voucher;
import com.account.dashboard.repository.LedgerRepository;
import com.account.dashboard.repository.LedgerTypeRepository;
import com.account.dashboard.repository.VoucherRepository;
import com.account.dashboard.service.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

//	@Override
	public Map<String,Object> getAllBalanceSheetLiabilitiesV2(String startDate, String endDate) {

		List<Long>list=getAllLiabilitiesChildHierarchy();
		System.out.println("Group list name .."+list);

		List<LedgerType> group = ledgerTypeRepository.findAllByIdIn(list);
		Map<String, Object>res = new HashMap<>();
		List<Map<String, Object>>result=new ArrayList<>();
		double tAmount=0;
		for(LedgerType g:group) {
			System.out.println("Group name .."+g.getName());

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
			tAmount=tAmount+totalAmount;
			map.put("totalCredit", totalCredit);
			map.put("groupName", g.getName());
			map.put("totalDebit", totalDebit);
			map .put("totalAmount", totalAmount);
			result.add(map);
		}
		res.put("data", result);
		res.put("totalPrice", tAmount);
		return res;
	
	
	}

//	@Override
	public Map<String, Object> getAllBalanceSheetAssetsV2(String startDate, String endDate) {

//		List<String>gList=Arrays.asList("Capital Account",
//				"Current Liabilities","	Fixed Assets","Current Assets");
////		List<LedgerType> group = ledgerTypeRepository.findAll();
//		List<LedgerType> group = ledgerTypeRepository.findByNameIn(gList);
		List<Long>list=getAllAssetsChildHierarchy();
		List<LedgerType> group = ledgerTypeRepository.findAllByIdIn(list);

		Map<String, Object>res = new HashMap<>();
		double tAmount=0;
		List<Map<String, Object>>result=new ArrayList<>();
		for(LedgerType g:group) {
			System.out.println("Group name .."+g.getId());

			Map<String,Object>map=new HashMap<>();
			List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());
			System.out.println("ledgerList .."+ledgerList+"...."+g.getName());

//	         LedgerType ledgerType = ledgerTypeRepository.findById(g.getId()).get();
			List<Voucher>voucherList=voucherRepository.findAllByLedgerIdIn(ledgerList);
//			List<Voucher>voucherList=voucherRepository.findByLedgerIdInAndInBetween(ledgerList,startDate,endDate);

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
			tAmount=tAmount+totalAmount;

			map.put("totalCredit", totalCredit);
			map.put("groupName", g.getName());
			map.put("totalDebit", totalDebit);
			map .put("totalAmount", totalAmount);
			result.add(map);
		}
		res.put("data", result);
		res.put("totalPrice", tAmount);

		return res;
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

		List<Long>list=getAllAssetsChildHierarchy();
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

	@Override
	public List<Map<String, Object>> getAllGroupByParentGroupId(String startDate, String endDate) {
		List<Long>list=getAllAssetsChildHierarchy();
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
		List<String>gList=Arrays.asList("Capital Account","Asset","Assets","Capital",
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
		List<String>gList=Arrays.asList("Loans","Liability","Liabilities",
				"Branch / Divisions","Suspense Account","Salary Payable");
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
	
	//===============================  New Version =====================================================
	
	public Map<Long,List<Long>>getAllLiabilitiesChildHierarchyV2(){
//		List<Long>result=new ArrayList<>();
		List<String>gList=Arrays.asList("Liabilities",
				"Branch / Divisions","Suspense Account","Salary Payable","Capital Account","Capital");
		List<Long> ledgerType = ledgerTypeRepository.findIdByNameIn(gList);

		Map<Long,List<Long>>result =new HashMap<>();
		for(Long s:ledgerType) {
			List<Long> childLedgerType = ledgerTypeRepository.findIdByParentId(s);
			childLedgerType.add(s);
			result.put(s, childLedgerType);
		}
		return result;
	}
	
	public Map<Long,List<Long>>getAllAssetsChildHierarchyV2(){
//		List<Long>result=new ArrayList<>();
		List<String>gList=Arrays.asList("Asset","Assets",
//				"Current Liabilities",
				"Fixed Assets","Current Assets");
		List<Long> ledgerType = ledgerTypeRepository.findIdByNameIn(gList);

		Map<Long,List<Long>>result =new HashMap<>();
		for(Long s:ledgerType) {
			List<Long> childLedgerType = ledgerTypeRepository.findIdByParentId(s);
			childLedgerType.add(s);
			result.put(s, childLedgerType);
		}
		return result;
	}
	
	
	
	public Map<String,Object> getAllBalanceSheetLiabilitiesV3(String startDate, String endDate) {


		Map<Long,List<Long>>mapData=getAllLiabilitiesChildHierarchyV2();
		Map<String, Object>res = new HashMap<>();
		double tAmount=0;
		List<Map<String, Object>>result=new ArrayList<>();

		for(Map.Entry<Long,List<Long>>m:mapData.entrySet()) {
//			List<Map<String, Object>>result=new ArrayList<>();

			LedgerType ledgerType = ledgerTypeRepository.findById(m.getKey()).get();
			List<LedgerType> group = ledgerTypeRepository.findAllByIdIn(m.getValue());
			double totalCredit=0;
			double totalDebit=0;
			double totalAmount=0;
			Map<String,Object>map=new HashMap<>();

			for(LedgerType g:group) {
				List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());	                
				List<Voucher>voucherList=voucherRepository.findByLedgerIdInAndInBetween(ledgerList,startDate,endDate);

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
				tAmount=tAmount+totalAmount;
			}
			tAmount=tAmount+totalAmount;
			
			map.put("totalCredit", totalCredit);
			map.put("groupName", ledgerType.getName());
			map.put("totalDebit", totalDebit);
			map .put("totalAmount", totalAmount);
			result.add(map);
			

		}
		res.put("data",result);
		res.put("totalPrice", tAmount);

         return res;
	}
	public Map<String,Object> getAllBalanceSheetLiabilities(String startDate, String endDate) {
		Map<Long,List<Long>>mapData=getAllLiabilitiesChildHierarchyV2();
		Map<String, Object>res = new HashMap<>();
		double tAmount=0;
		List<Map<String, Object>>result=new ArrayList<>();
		for(Map.Entry<Long,List<Long>>m:mapData.entrySet()) {
			//			List<Map<String, Object>>result=new ArrayList<>();
			LedgerType ledgerType = ledgerTypeRepository.findById(m.getKey()).get();
			List<LedgerType> group = ledgerTypeRepository.findAllByIdIn(m.getValue());
			double totalCredit=0;
			double totalDebit=0;
			double totalAmount=0;
			Map<String,Object>map=new HashMap<>();
			Set<Voucher>voucherResult=new HashSet<>();
			for(LedgerType g:group) {
				List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());	                
				List<Voucher>voucherList=voucherRepository.findByLedgerIdInAndInBetween(ledgerList,startDate,endDate);
				voucherResult.addAll(voucherList);
			}
			for(Voucher v:voucherResult) {			
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
			map.put("groupName", ledgerType.getName());
			map.put("totalDebit", totalDebit);
			map .put("totalAmount", totalAmount);
			result.add(map);
			tAmount=tAmount+totalAmount;
		}
		res.put("data",result);
		res.put("totalPrice", tAmount);

		return res;
	}
	
	
	public Map<String, Object> getAllBalanceSheetAssets(String startDate, String endDate) {

		
		Map<Long,List<Long>>mapData=getAllAssetsChildHierarchyV2();
		Map<String, Object>res = new HashMap<>();
		double tAmount=0;
		List<Map<String, Object>>result=new ArrayList<>();
		for(Map.Entry<Long,List<Long>>m:mapData.entrySet()) {
			LedgerType ledgerType = ledgerTypeRepository.findById(m.getKey()).get();
			List<LedgerType> group = ledgerTypeRepository.findAllByIdIn(m.getValue());
			double totalCredit=0;
			double totalDebit=0;
			double totalAmount=0;
			Map<String,Object>map=new HashMap<>();
			Set<Voucher>voucherResult=new HashSet<>();
			for(LedgerType g:group) {
				List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());
				List<Voucher>voucherList=voucherRepository.findByLedgerIdInAndInBetween(ledgerList,startDate,endDate);
				voucherResult.addAll(voucherList);
			}
			for(Voucher v:voucherResult) {			
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
			tAmount=tAmount+totalAmount;
			
			map.put("totalCredit", totalCredit);
			map.put("groupName", ledgerType.getName());
			map.put("totalDebit", totalDebit);
			map .put("totalAmount", totalAmount);
			result.add(map);
		}
		res.put("data",result);
		res.put("totalPrice", tAmount);
         return res;
	}
	public Map<Long,List<Long>>getAllChildHierarchy(String name){

		Long ledgerTypeId = ledgerTypeRepository.findIdByName(name);
		List<Long> childLedgerTypeIds = ledgerTypeRepository.findIdByParentId(ledgerTypeId);
		childLedgerTypeIds.add(ledgerTypeId);
		Map<Long,List<Long>>result =new HashMap<>();
		for(Long s:childLedgerTypeIds) {
			List<Long> childLedgerType = ledgerTypeRepository.findIdByParentId(s);
			childLedgerType.add(s);
			result.put(s, childLedgerType);
		}
		System.out.println("Group Result name .    ."+result);

		return result;
	}
	
	public Map<String,Object> getLiabilitiesSubGroupByGroup(String startDate, String endDate,String name) {
		Map<Long,List<Long>>mapData=getAllChildHierarchy(name);
		Map<String, Object>res = new HashMap<>();
		double tAmount=0;
		List<Map<String, Object>>result=new ArrayList<>();
		for(Map.Entry<Long,List<Long>>m:mapData.entrySet()) {
			//			List<Map<String, Object>>result=new ArrayList<>();
			LedgerType ledgerType = ledgerTypeRepository.findById(m.getKey()).get();
			List<LedgerType> group = ledgerTypeRepository.findAllByIdIn(m.getValue());
			double totalCredit=0;
			double totalDebit=0;
			double totalAmount=0;
			Map<String,Object>map=new HashMap<>();
			Set<Voucher>voucherResult=new HashSet<>();
			for(LedgerType g:group) {
				List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());	                
				List<Voucher>voucherList=voucherRepository.findByLedgerIdInAndInBetween(ledgerList,startDate,endDate);
				voucherResult.addAll(voucherList);
			}
			for(Voucher v:voucherResult) {			
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
			map.put("groupName", ledgerType.getName());
			map.put("totalDebit", totalDebit);
			map .put("totalAmount", totalAmount);
			result.add(map);
			tAmount=tAmount+totalAmount;
		}
		res.put("data",result);
		res.put("totalPrice", tAmount);

		return res;
	}

	public Map<String, Object> getAllAssetsBySubAssets(String startDate, String endDate,String name) {

		
		Map<Long,List<Long>>mapData=getAllChildHierarchy(name);
		Map<String, Object>res = new HashMap<>();
		double tAmount=0;
		List<Map<String, Object>>result=new ArrayList<>();
		for(Map.Entry<Long,List<Long>>m:mapData.entrySet()) {
			LedgerType ledgerType = ledgerTypeRepository.findById(m.getKey()).get();
			List<LedgerType> group = ledgerTypeRepository.findAllByIdIn(m.getValue());
			double totalCredit=0;
			double totalDebit=0;
			double totalAmount=0;
			Map<String,Object>map=new HashMap<>();
			Set<Voucher>voucherResult=new HashSet<>();
			for(LedgerType g:group) {
				List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());
				List<Voucher>voucherList=voucherRepository.findByLedgerIdInAndInBetween(ledgerList,startDate,endDate);
				voucherResult.addAll(voucherList);
			}
			for(Voucher v:voucherResult) {			
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
			tAmount=tAmount+totalAmount;
			
			map.put("totalCredit", totalCredit);
			map.put("groupName", ledgerType.getName());
			map.put("totalDebit", totalDebit);
			map .put("totalAmount", totalAmount);
			result.add(map);
		}
		res.put("data",result);
		res.put("totalPrice", tAmount);
         return res;
	}

	@Override
	public Map<String, Object> getAssetsSubGroupByGroup(String startDate, String endDate, String name) {


		
		Map<Long,List<Long>>mapData=getAllChildHierarchy(name);
		Map<String, Object>res = new HashMap<>();
		double tAmount=0;
		List<Map<String, Object>>result=new ArrayList<>();
		for(Map.Entry<Long,List<Long>>m:mapData.entrySet()) {
			LedgerType ledgerType = ledgerTypeRepository.findById(m.getKey()).get();
			List<LedgerType> group = ledgerTypeRepository.findAllByIdIn(m.getValue());
			double totalCredit=0;
			double totalDebit=0;
			double totalAmount=0;
			Map<String,Object>map=new HashMap<>();
			Set<Voucher>voucherResult=new HashSet<>();
			for(LedgerType g:group) {
				List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());
				List<Voucher>voucherList=voucherRepository.findByLedgerIdInAndInBetween(ledgerList,startDate,endDate);
				voucherResult.addAll(voucherList);
			}
			for(Voucher v:voucherResult) {			
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
			tAmount=tAmount+totalAmount;
			
			map.put("totalCredit", totalCredit);
			map.put("groupName", ledgerType.getName());
			map.put("totalDebit", totalDebit);
			map .put("totalAmount", totalAmount);
			result.add(map);
		}
		res.put("data",result);
		res.put("totalPrice", tAmount);
         return res;
	
	}
    public List<String> getAllLiabilities(){
    	  List<String> balanceSheet = new ArrayList<>(Arrays.asList(
    	            // I. EQUITY AND LIABILITIES
    	            "EQUITY AND LIABILITIES",

    	            // Shareholders’ Funds
    	            "Shareholders’ Funds",
    	            "Share Capital",
    	            "Equity Share Capital",
    	            "Preference Share Capital",
    	            "Reserves and Surplus",
    	            "Capital Reserve",
    	            "Share Premium Account",
    	            "Revaluation Reserve",
    	            "General Reserve",
    	            "Retained Earnings",
    	            "Other Specific Reserves",
    	            "Money received against share warrants",

    	            // Share Application Money Pending Allotment
    	            "Share Application Money Pending Allotment",

    	            // Non-current Liabilities
    	            "Non-current Liabilities",
    	            "Long-term borrowings",
    	            "Secured Loans",
    	            "Unsecured Loans",
    	            "Deferred Payment Liabilities",
    	            "Long-term Deposits",
    	            "Bonds / Debentures",
    	            "Deferred tax liabilities (net)",
    	            "Other long-term liabilities",
    	            "Long-term payables",
    	            "Long-term lease obligations",
    	            "Long-term provisions",
    	            "Provision for employee benefits",
    	            "Provision for warranty",
    	            "Provision for decommissioning/restoration costs",
    	            "Other long-term provisions",

    	            // Current Liabilities
    	            "Current Liabilities",
    	            "Short-term borrowings",
    	            "Loans from Banks",
    	            "Loans from Financial Institutions / NBFCs",
    	            "Loans from Others / Unsecured Loans",
    	            "Other Short-term Borrowings",
    	            "Trade payables",
    	            "Micro, Small and Medium Enterprises (MSMEs)",
    	            "Others",
    	            "Other current liabilities",
    	            "Current Maturities of Long-term Borrowings",
    	            "Statutory Dues Payable",
    	            "Advance from Customers",
    	            "Interest Accrued but Not Due",
    	            "Dividends Payable",
    	            "Other Payables / Liabilities",
    	            "Short-term provisions",
    	            "Provision for Employee Benefits",
    	            "Provision for Tax",
    	            "Provision for Warranty",
    	            "Other Short-term Provisions"));
    	  
    	  return balanceSheet;
    }
    
    public List<String> getAllAssetsGroup(){
    	List<String> assetsList = new ArrayList<>(Arrays.asList(
                "ASSETS",
                "Non-current Assets",
                "Property, Plant and Equipment",
                "Land",
                "Buildings",
                "Plant and machinery",
                "Furniture and fixtures",
                "Vehicles",
                "Office equipment",
                "Other",
                "Intangible Assets",
                "Goodwill",
                "Other Intangible Assets",
                "Intangible Assets under Development / Work-in-Progress",
                "Capital Work-in-Progress",
                "Non-current investments",
                "Deferred tax assets (net)",
                "Long-term loans and advances",
                "Other non-current assets",
                "Current Assets",
                "Current investments",
                "Inventories",
                "Trade receivables",
                "Cash and cash equivalents",
                "Short-term loans and advances",
                "Other current assets",
                "Total Assets"
            ));
    	return assetsList;
    }
	 
    @Override
    public List<Map<String, Object>> getAllAssetsAndLiabilities() {
    	
    	Year currentYear = Year.now();

    	// Start of current year: January 1st, 00:00:00
    	String startOfYear = LocalDateTime.of(
    			currentYear.getValue(), 1, 1, 0, 0, 0).toString();
         
    	// End of current year: December 31st, 23:59:59
    	String endOfYear = LocalDateTime.of(
    			currentYear.getValue(), 12, 31, 23, 59, 59).toString();

    	List<String> assetsGroup = getAllAssetsGroup();
    	Map<String,List<Voucher>>mapAssets=new HashMap<>();
    	Map<String,Double>mapCountAssets=new HashMap<>();
    	
        double currAssetsTotal=0;
    	for(String s:assetsGroup) {
    		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
    		if(ledgerType!=null &&ledgerType.getId()!=null) {
    			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
    			List<Voucher> voucher = voucherRepository.findByLedgerIdInAndInBetween(ledgerIds,startOfYear,endOfYear);
    			
    			mapAssets.put(s, voucher);
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
    			mapCountAssets.put(s, totalAmount);
    			currAssetsTotal=currAssetsTotal+totalAmount;

    		}
    		

    	}
//== = = = = = = == = = = = = = = = == previous Year = = = = === = = = = == = = =   
        int cYear = Year.now().getValue();

    	int previousYear = cYear - 1;

        // Start of previous year: Jan 1, 00:00:00
        String startOfPreviousYear = LocalDateTime.of(previousYear, 1, 1, 0, 0, 0).toString();

        // End of previous year: Dec 31, 23:59:59
        String endOfPreviousYear = LocalDateTime.of(previousYear, 12, 31, 23, 59, 59).toString();


    	List<String> prevAssetsGroup = getAllAssetsGroup();
    	Map<String,List<Voucher>>mapPrevAssets=new HashMap<>();
    	Map<String,Double>mapCountPrevAssets=new HashMap<>();
        double prevAssetsTotal=0;

    	for(String s:prevAssetsGroup) {
    		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
    		if(ledgerType!=null &&ledgerType.getId()!=null) {
    			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
    			List<Voucher> voucher = voucherRepository.findByLedgerIdInAndInBetween(ledgerIds,startOfPreviousYear,endOfPreviousYear);
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
    			prevAssetsTotal=prevAssetsTotal+totalAmount;
    		}

    	}

    	
    	
    	
    	
    	
    	
    	
    	
//    	// Get the current year
//    	Year currentYear = Year.now();
//
//    	// Start of current year: January 1st, 00:00:00
//    	LocalDateTime startOfYear = LocalDateTime.of(
//    			currentYear.getValue(), 1, 1, 0, 0, 0);
//
//    	// End of current year: December 31st, 23:59:59
//    	LocalDateTime endOfYear = LocalDateTime.of(
//    			currentYear.getValue(), 12, 31, 23, 59, 59);

    	List<String> liabilitiesGroup = getAllLiabilities();
    	Map<String,List<Voucher>>map=new HashMap<>();
    	Map<String,Double>mapCount=new HashMap<>();
        double currLiabilitiesTotal=0;
    	for(String s:liabilitiesGroup) {
    		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
    		if(ledgerType!=null &&ledgerType.getId()!=null) {
    			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
//    			List<Voucher> voucher = voucherRepository.findAllByLedgerIdIn(ledgerIds);
    			List<Voucher> voucher = voucherRepository.findByLedgerIdInAndInBetween(ledgerIds,startOfYear,endOfYear);

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
    			currLiabilitiesTotal=currLiabilitiesTotal+totalAmount;
    		}

    	}
//== = = = = = = == = = = = = = = = == previous Year = = = = === = = = = == = = =   
//        int cYear = Year.now().getValue();
//
//    	int previousYear = cYear - 1;
//
//        // Start of previous year: Jan 1, 00:00:00
//        LocalDateTime startOfPreviousYear = LocalDateTime.of(previousYear, 1, 1, 0, 0, 0);
//
//        // End of previous year: Dec 31, 23:59:59
//        LocalDateTime endOfPreviousYear = LocalDateTime.of(previousYear, 12, 31, 23, 59, 59);


    	List<String> prevLiabilitiesGroup = getAllLiabilities();
    	Map<String,List<Voucher>>mapPrev=new HashMap<>();
    	Map<String,Double>mapCountPrev=new HashMap<>();
    	double prevLiabilitiesTotal=0;
    	for(String s:prevLiabilitiesGroup) {
    		LedgerType ledgerType = ledgerTypeRepository.findByName(s);
    		if(ledgerType!=null &&ledgerType.getId()!=null) {
    			List<Long> ledgerIds = ledgerRepository.findByLedgerTypeId(ledgerType.getId());
    			List<Voucher> voucher = voucherRepository.findByLedgerIdInAndInBetween(ledgerIds,startOfPreviousYear,endOfPreviousYear);
//    			List<Voucher> voucher = voucherRepository.findAllByLedgerIdIn(ledgerIds);
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
    			prevLiabilitiesTotal=prevLiabilitiesTotal+totalAmount;
    		}

    	}




    	List<Map<String, Object>> response = new ArrayList<>();

    	// ======================= EQUITY AND LIABILITIES =======================
    	Map<String, Object> equityAndLiabilities = new HashMap<>();
    	equityAndLiabilities.put("title", "EQUITY AND LIABILITIES");
    	equityAndLiabilities.put("totalCurrLiabilities",currLiabilitiesTotal);
    	equityAndLiabilities.put("totalPrevLiabilities",prevLiabilitiesTotal);

    	List<Map<String, Object>> equityList = new ArrayList<>();

    	// ------------------ Shareholders’ Funds ------------------
    	Map<String, Object> shareholdersFunds = new HashMap<>();
    	shareholdersFunds.put("title", "Shareholders’ Funds");

    	List<Map<String, Object>> shareholdersData = new ArrayList<>();

    	// Share Capital
    	Map<String, Object> shareCapital = new HashMap<>();
    	shareCapital.put("title", "Share Capital");
    	List<Map<String, Object>> shareCapitalList = new ArrayList<>();
    	Map<String,Object>m1=new HashMap<>();
    	m1.put("title", "Equity Share Capital");
    	m1.put("price", "1000");
    	m1.put("totalCurrentAmount",mapCount.get("Equity Share Capital"));
    	m1.put("totalPreviousAmount",mapCountPrev.get("Equity Share Capital"));

    	//	        shareCapitalList.add(Map.of("title", "Equity Share Capital"));
    	shareCapitalList.add(m1);

    	Map<String,Object>m2=new HashMap<>();
    	m2.put("title", "Preference Share Capital");
    	m2.put("totalCurrentAmount",mapCount.get("Preference Share Capital"));
    	m2.put("totalPreviousAmount",mapCountPrev.get("Preference Share Capital"));

    	shareCapitalList.add(m2);

    	//	        shareCapitalList.add(Map.of("title", "Preference Share Capital"));
    	shareCapital.put("data", shareCapitalList);
    	shareholdersData.add(shareCapital);

    	// Reserves and Surplus
    	Map<String, Object> reserves = new HashMap<>();
    	reserves.put("title", "Reserves and Surplus");
    	List<Map<String, Object>> reservesList = new ArrayList<>();

    	Map<String, Object>m3=new HashMap<>();
    	m3.put("title", "Capital Reserve");
    	m3.put("price", "1000");
    	m3.put("totalCurrentAmount",mapCount.get("Capital Reserve"));
    	m3.put("totalPreviousAmount",mapCountPrev.get("Capital Reserve"));

    	reservesList.add(m3);

    	//	        reservesList.add(Map.of("title", "Capital Reserve"));
    	Map<String, Object>m4=new HashMap<>();
    	m4.put("title", "Share Premium Account");
    	m4.put("price", "1000");
    	m4.put("totalCurrentAmount",mapCount.get("Share Premium Account"));
    	m4.put("totalPreviousAmount",mapCountPrev.get("Share Premium Account"));

    	reservesList.add(m4);

    	//	        reservesList.add(Map.of("title", "Share Premium Account"));
    	Map<String, Object>m5=new HashMap<>();
    	m5.put("title", "Revaluation Reserve");
    	m5.put("price", "1000");
    	m5.put("totalCurrentAmount",mapCount.get("Revaluation Reserve"));
    	m5.put("totalPreviousAmount",mapCountPrev.get("Revaluation Reserve"));

    	reservesList.add(m5);

    	//	        reservesList.add(Map.of("title", "Revaluation Reserve"));
    	Map<String, Object>m6=new HashMap<>();
    	m6.put("title", "General Reserve");
    	m6.put("price", "1000");
    	m6.put("totalCurrentAmount",mapCount.get("General Reserve"));
    	m6.put("totalPreviousAmount",mapCountPrev.get("General Reserve"));

    	reservesList.add(m6);
    	//	        reservesList.add(Map.of("title", "General Reserve"));
    	Map<String, Object>m7=new HashMap<>();
    	m7.put("title", "Retained Earnings");
    	m7.put("price", "1000");
    	m7.put("totalCurrentAmount",mapCount.get("Retained Earnings"));
    	m7.put("totalPreviousAmount",mapCountPrev.get("Retained Earnings"));

    	reservesList.add(m7);

    	//	        reservesList.add(Map.of("title", "Retained Earnings"));
    	Map<String, Object>m8=new HashMap<>();
    	m8.put("title", "Other Specific Reserves");
    	m8.put("price", "1000");
    	m8.put("totalCurrentAmount",mapCount.get("Other Specific Reserves"));
    	m8.put("totalPreviousAmount",mapCountPrev.get("Other Specific Reserves"));

    	reservesList.add(m8);
    	//	        reservesList.add(Map.of("title", "Other Specific Reserves"));
    	reserves.put("data", reservesList);
    	shareholdersData.add(reserves);

    	Map<String, Object>m9=new HashMap<>();
    	m9.put("title", "Money received against share warrants");
    	m9.put("price", "1000");
    	m9.put("totalCurrentAmount",mapCount.get("Money received against share warrants"));
    	m9.put("totalPreviousAmount",mapCountPrev.get("Money received against share warrants"));

    	shareholdersData.add(m9);
    	//	        shareholdersData.add(Map.of("title", "Money received against share warrants"));

    	shareholdersFunds.put("data", shareholdersData);
    	equityList.add(shareholdersFunds);

    	// Share Application Money Pending Allotment
    	Map<String, Object>m10=new HashMap<>();
    	m10.put("title", "Money received against share warrants");
    	m10.put("price", "price");
    	m10.put("totalCurrentAmount",mapCount.get("Money received against share warrants"));
    	m10.put("totalPreviousAmount",mapCountPrev.get("Money received against share warrants"));

    	equityList.add(m10);
    	//	        equityList.add(Map.of("title", "Share Application Money Pending Allotment", "data", new ArrayList<>()));

    	// ------------------ Non-current Liabilities ------------------
    	Map<String, Object> nonCurrentLiabilities = new HashMap<>();
    	nonCurrentLiabilities.put("title", "Non-current Liabilities");

    	List<Map<String, Object>> nonCurrentList = new ArrayList<>();

    	Map<String, Object> longTermBorrowings = new HashMap<>();
    	longTermBorrowings.put("title", "Long-term borrowings");
    	List<Map<String, Object>> longTermBorrowingsList = new ArrayList<>();

    	Map<String, Object>m11=new HashMap<>();
    	m11.put("title", "Secured Loans");
    	m11.put("price", "1000");
    	m11.put("totalCurrentAmount",mapCount.get("Secured Loans"));
    	m11.put("totalPreviousAmount",mapCountPrev.get("Secured Loans"));

    	longTermBorrowingsList.add(m11);

    	//	        longTermBorrowingsList.add(Map.of("title", "Secured Loans"));
    	Map<String, Object>m12=new HashMap<>();
    	m12.put("title", "Unsecured Loans");
    	m12.put("price", "1000");
    	m12.put("totalCurrentAmount",mapCount.get("Unsecured Loans"));
    	m12.put("totalPreviousAmount",mapCountPrev.get("Unsecured Loans"));

    	longTermBorrowingsList.add(m12);
    	//	        longTermBorrowingsList.add(Map.of("title", "Unsecured Loans"));

    	Map<String, Object>m13=new HashMap<>();
    	m13.put("title", "Deferred Payment Liabilities");
    	m13.put("price", "1000");
    	m13.put("totalCurrentAmount",mapCount.get("Deferred Payment Liabilities"));
    	m13.put("totalPreviousAmount",mapCountPrev.get("Deferred Payment Liabilities"));

    	longTermBorrowingsList.add(m13);
    	//	        longTermBorrowingsList.add(Map.of("title", "Deferred Payment Liabilities"));
    	Map<String, Object>m14=new HashMap<>();
    	m14.put("title", "Long-term Deposits");
    	m14.put("price", "1000");
    	m14.put("totalCurrentAmount",mapCount.get("Long-term Deposits"));
    	m14.put("totalPreviousAmount",mapCountPrev.get("Long-term Deposits"));

    	longTermBorrowingsList.add(m14);
    	//	        longTermBorrowingsList.add(Map.of("title", "Long-term Deposits"));
    	Map<String, Object>m15=new HashMap<>();
    	m15.put("title", "Bonds / Debentures");
    	m15.put("price", "1000");
    	m15.put("totalCurrentAmount",mapCount.get("Bonds / Debentures"));
    	m15.put("totalPreviousAmount",mapCountPrev.get("Bonds / Debentures"));

    	longTermBorrowingsList.add(m15);
    	
    	//	        longTermBorrowingsList.add(Map.of("title", "Bonds / Debentures"));
    	longTermBorrowings.put("data", longTermBorrowingsList);
    	nonCurrentList.add(longTermBorrowings);

    	nonCurrentList.add(Map.of("title", "Deferred tax liabilities (net)", "data", new ArrayList<>()));

    	Map<String, Object> otherLongTermLiabilities = new HashMap<>();
    	otherLongTermLiabilities.put("title", "Other long-term liabilities");
    	Double longTermPayables = mapCount.get("Long-term payables")!=null?mapCount.get("Long-term payables"):0;
    	otherLongTermLiabilities.put("data", List.of(
    			Map.of("title", "Long-term payables","price","1000","totalCurrentAmount",longTermPayables),
    			Map.of("title", "Long-term lease obligations","price","1000","totalCurrentAmount",mapCount.get("Long-term lease obligations")!=null?mapCount.get("Long-term lease obligations"):0,"totalPreviousAmount",mapCountPrev.get("Long-term lease obligations")!=null?mapCountPrev.get("Long-term lease obligations"):0)
    			));

    	nonCurrentList.add(otherLongTermLiabilities);

    	Map<String, Object> longTermProvisions = new HashMap<>();
    	longTermProvisions.put("title", "Long-term provisions");
    	longTermProvisions.put("data", List.of(
    			Map.of("title", "Provision for employee benefits","price","1000","totalCurrentAmount",mapCount.get("Bonds / Debentures")!=null?mapCount.get("Bonds / Debentures"):0,"totalPreviousAmount",mapCountPrev.get("Bonds / Debentures")!=null?mapCountPrev.get("Bonds / Debentures"):0),
    			Map.of("title", "Provision for warranty","price","1000","totalCurrentAmount",mapCount.get("Provision for warranty")!=null?mapCount.get("Provision for warranty"):0),
    			Map.of("title", "Provision for decommissioning/restoration costs","price","1000","totalCurrentAmount",mapCount.get("Provision for decommissioning/restoration costs")!=null?mapCount.get("Provision for decommissioning/restoration costs"):0,"totalPreviousAmount"
    					,mapCountPrev.get("Provision for decommissioning/restoration costs")!=null?mapCountPrev.get("Provision for decommissioning/restoration costs"):0),
    			Map.of("title", "Other long-term provisions","price","1000","totalCurrentAmount",mapCount.get("Other long-term provisions")!=null?mapCount.get("Other long-term provisions"):0,"totalPreviousAmount",mapCountPrev.get("Other long-term provisions")!=null?mapCountPrev.get("Other long-term provisions"):0)
    			));
    	nonCurrentList.add(longTermProvisions);

    	nonCurrentLiabilities.put("data", nonCurrentList);
    	equityList.add(nonCurrentLiabilities);

    	// ------------------ Current Liabilities ------------------
    	Map<String, Object> currentLiabilities = new HashMap<>();
    	currentLiabilities.put("title", "Current Liabilities");

    	List<Map<String, Object>> currentList = new ArrayList<>();

    	Map<String, Object> shortTermBorrowings = new HashMap<>();
    	shortTermBorrowings.put("title", "Short-term borrowings");
    	shortTermBorrowings.put("data", List.of(
    			Map.of("title", "Loans from Banks","price","1000","totalCurrentAmount",mapCount.get("Loans from Banks")!=null?mapCount.get("Loans from Banks"):0,"totalPreviousAmount",mapCountPrev.get("Loans from Banks")!=null?mapCountPrev.get("Loans from Banks"):0),
    			Map.of("title", "Loans from Financial Institutions / NBFCs","price","1000","totalCurrentAmount",mapCount.get("Loans from Banks")!=null?mapCount.get("Loans from Banks"):0,"totalPreviousAmount",mapCountPrev.get("Loans from Banks")!=null?mapCountPrev.get("Loans from Banks"):0),
    			Map.of("title", "Loans from Others / Unsecured Loans","price","1000","totalCurrentAmount",mapCount.get("Loans from Others / Unsecured Loans")!=null?mapCount.get("Loans from Others / Unsecured Loans"):0,"totalPreviousAmount",mapCountPrev.get("Loans from Others / Unsecured Loans")!=null?mapCountPrev.get("Loans from Others / Unsecured Loans"):0),
    			Map.of("title", "Other Short-term Borrowings","totalCurrentAmount",mapCount.get("Other Short-term Borrowings")!=null?mapCount.get("Other Short-term Borrowings"):0,"totalPreviousAmount",mapCountPrev.get("Other Short-term Borrowings")!=null?mapCountPrev.get("Other Short-term Borrowings"):0)
    			));
    	currentList.add(shortTermBorrowings);

    	currentList.add(Map.of(
    			"title", "Trade payables",
    			"data", List.of(
    					Map.of("title", "Micro, Small and Medium Enterprises (MSMEs)","totalCurrentAmount",mapCount.get("Micro, Small and Medium Enterprises (MSMEs)")!=null?mapCount.get("Micro, Small and Medium Enterprises (MSMEs)"):0,"totalPreviousAmount",mapCountPrev.get("Micro, Small and Medium Enterprises (MSMEs)")!=null?mapCountPrev.get("Micro, Small and Medium Enterprises (MSMEs)"):0),
    					Map.of("title", "Others","price","1000","totalCurrentAmount",mapCount.get("Others")!=null?mapCount.get("Others"):0)
    					)
    			));

    	currentList.add(Map.of(
    			"title", "Other current liabilities",
    			"data", List.of(
    					Map.of("title", "Current Maturities of Long-term Borrowings","price","1000","totalCurrentAmount",mapCount.get("Current Maturities of Long-term Borrowings")!=null?mapCount.get("Current Maturities of Long-term Borrowings"):0,"totalPreviousAmount",mapCountPrev.get("Current Maturities of Long-term Borrowings")!=null?mapCountPrev.get("Current Maturities of Long-term Borrowings"):0),
    					Map.of("title", "Statutory Dues Payable","price","1000","totalCurrentAmount",mapCount.get("Statutory Dues Payable")!=null?mapCount.get("Statutory Dues Payable"):0,"totalPreviousAmount",mapCountPrev.get("Statutory Dues Payable")!=null?mapCountPrev.get("Statutory Dues Payable"):0),
    					Map.of("title", "Advance from Customers","price","1000","totalCurrentAmount",mapCount.get("Advance from Customers")!=null?mapCount.get("Advance from Customers"):0,"totalPreviousAmount",mapCountPrev.get("Advance from Customers")!=null?mapCountPrev.get("Advance from Customers"):0),
    					Map.of("title", "Interest Accrued but Not Due","price","1000","totalCurrentAmount",mapCount.get("Interest Accrued but Not Due")!=null?mapCount.get("Interest Accrued but Not Due"):0,"totalPreviousAmount",mapCountPrev.get("Interest Accrued but Not Due")!=null?mapCountPrev.get("Interest Accrued but Not Due"):0),
    					Map.of("title", "Dividends Payable","price","1000","totalCurrentAmount",mapCount.get("Dividends Payable")!=null?mapCount.get("Dividends Payable"):0,"totalPreviousAmount",mapCountPrev.get("Dividends Payable")!=null?mapCountPrev.get("Dividends Payable"):0),
    					Map.of("title", "Other Payables / Liabilities","price","1000","totalCurrentAmount",mapCount.get("Other Payables / Liabilities")!=null?mapCount.get("Other Payables / Liabilities"):0,"totalPreviousAmount",mapCountPrev.get("Other Payables / Liabilities")!=null?mapCountPrev.get("Other Payables / Liabilities"):0)
    					)
    			));

    	currentList.add(Map.of(
    			"title", "Short-term provisions",
    			"data", List.of(
    					Map.of("title", "Provision for Employee Benefits","price","1000","totalCurrentAmount",mapCount.get("Provision for Employee Benefits")!=null?mapCount.get("Provision for Employee Benefits"):0,"totalPreviousAmount",mapCountPrev.get("Provision for Employee Benefits")!=null?mapCountPrev.get("Provision for Employee Benefits"):0),
    					Map.of("title", "Provision for Tax","totalCurrentAmount",mapCount.get("Provision for Tax")!=null?mapCount.get("Provision for Tax"):0,"totalPreviousAmount",mapCountPrev.get("Provision for Tax")!=null?mapCountPrev.get("Provision for Tax"):0),
    					Map.of("title", "Provision for Warranty","totalCurrentAmount",mapCount.get("Provision for Warranty")!=null?mapCount.get("Provision for Warranty"):0,"totalPreviousAmount",mapCountPrev.get("Provision for Warranty")!=null?mapCountPrev.get("Provision for Warranty"):0),
    					Map.of("title", "Other Short-term Provisions","totalCurrentAmount",mapCount.get("Other Short-term Provisions")!=null?mapCount.get("Other Short-term Provisions"):0,"totalPreviousAmount",mapCountPrev.get("Other Short-term Provisions")!=null?mapCountPrev.get("Other Short-term Provisions"):0)
    					)
    			));

    	currentLiabilities.put("data", currentList);
    	equityList.add(currentLiabilities);

    	equityAndLiabilities.put("data", equityList);
    	equityAndLiabilities.put(endOfPreviousYear, shortTermBorrowings);
    	response.add(equityAndLiabilities);

    	// ======================= ASSETS =======================
    	
    	Map<String, Object> assets = new HashMap<>();
    	assets.put("title", "ASSETS");
    	assets.put("total", 0);

    	List<Map<String, Object>> assetsList = new ArrayList<>();

    	// Non-current Assets
    	Map<String, Object> nonCurrentAssets = new HashMap<>();
    	nonCurrentAssets.put("title", "Non-current Assets");
    	nonCurrentAssets.put("totalCurrAssets",currAssetsTotal);
    	nonCurrentAssets.put("totalPrevAssets", prevAssetsTotal);

//    	Map<String,Double>mapCountPrevAssets=new HashMap<>();
//    	Map<String,Double>mapCountAssets=new HashMap<>();

    	List<Map<String, Object>> nonCurrentAssetsList = new ArrayList<>();
    	nonCurrentAssetsList.add(Map.of(
    			"title", "Property, Plant and Equipment",
    			"data", List.of(
    					Map.of("title", "Land","price","1000","totalCurrentAmount",mapCountAssets.get("Land")!=null?mapCountAssets.get("Land"):0,"totalPreviousAmount",mapCountPrevAssets.get("Land")!=null?mapCountPrevAssets.get("Land"):0),
    					Map.of("title", "Buildings","price","1000","totalCurrentAmount",mapCountAssets.get("Buildings")!=null?mapCountAssets.get("Buildings"):0,"totalPreviousAmount",mapCountPrevAssets.get("Buildings")!=null?mapCountPrevAssets.get("Buildings"):0),
    					Map.of("title", "Plant and machinery","price","1000","totalCurrentAmount",mapCountAssets.get("Plant and machinery")!=null?mapCountAssets.get("Plant and machinery"):0,"totalPreviousAmount",mapCountPrevAssets.get("Plant and machinery")!=null?mapCountPrevAssets.get("Plant and machinery"):0),
    					Map.of("title", "Furniture and fixtures","totalCurrentAmount",mapCountAssets.get("Furniture and fixtures")!=null?mapCountAssets.get("Furniture and fixtures"):0,"totalPreviousAmount",mapCountPrevAssets.get("Furniture and fixtures")!=null?mapCountPrevAssets.get("Furniture and fixtures"):0),
    					Map.of("title", "Vehicles","price","1000","totalCurrentAmount",mapCountAssets.get("Vehicles")!=null?mapCountAssets.get("Vehicles"):0,"totalPreviousAmount",mapCountPrevAssets.get("Vehicles")!=null?mapCountPrevAssets.get("Vehicles"):0),
    					Map.of("title", "Office equipment","price","1000","totalCurrentAmount",mapCountAssets.get("Office equipment")!=null?mapCountAssets.get("Office equipment"):0,"totalPreviousAmount",mapCountPrevAssets.get("Office equipment")!=null?mapCountPrevAssets.get("Office equipment"):0),
    					Map.of("title", "Other","price","1000","totalCurrentAmount",mapCountAssets.get("Other")!=null?mapCountAssets.get("Other"):0,"totalPreviousAmount",mapCountPrevAssets.get("Other")!=null?mapCountPrevAssets.get("Other"):0)
    					)
    			));
    	nonCurrentAssetsList.add(Map.of(
    			"title", "Intangible Assets",
    			"data", List.of(
    					Map.of("title", "Goodwill","price","1000","totalCurrentAmount",mapCountAssets.get("Other")!=null?mapCountAssets.get("Other"):0,"totalPreviousAmount",mapCountPrevAssets.get("Other")!=null?mapCountPrevAssets.get("Other"):0),
    					Map.of("title", "Other Intangible Assets","price","1000","totalCurrentAmount",mapCountAssets.get("Other")!=null?mapCountAssets.get("Other"):0,"totalPreviousAmount",mapCountPrevAssets.get("Other")!=null?mapCountPrevAssets.get("Other"):0),
    					Map.of("title", "Intangible Assets under Development / Work-in-Progress","price","1000","totalCurrentAmount",mapCountAssets.get("Intangible Assets under Development / Work-in-Progress")!=null?mapCountAssets.get("Intangible Assets under Development / Work-in-Progress"):0,"totalPreviousAmount",mapCountPrevAssets.get("Intangible Assets under Development / Work-in-Progress")!=null?mapCountPrevAssets.get("Intangible Assets under Development / Work-in-Progress"):0)
    					)
    			));
    	nonCurrentAssetsList.add(Map.of("title", "Capital Work-in-Progress","price","1000","totalCurrentAmount",mapCountAssets.get("Capital Work-in-Progress")!=null?mapCountAssets.get("Capital Work-in-Progress"):0,"totalPreviousAmount",mapCountPrevAssets.get("Capital Work-in-Progress")!=null?mapCountPrevAssets.get("Capital Work-in-Progress"):0));
    	nonCurrentAssetsList.add(Map.of("title", "Non-current investments","price","1000","totalCurrentAmount",mapCountAssets.get("Non-current investments")!=null?mapCountAssets.get("Non-current investments"):0,"totalPreviousAmount",mapCountPrevAssets.get("Non-current investments")!=null?mapCountPrevAssets.get("Non-current investments"):0));
    	nonCurrentAssetsList.add(Map.of("title", "Deferred tax assets (net)","price","1000","totalCurrentAmount",mapCountAssets.get("Deferred tax assets (net)")!=null?mapCountAssets.get("Deferred tax assets (net)"):0,"totalPreviousAmount",mapCountPrevAssets.get("Deferred tax assets (net)")!=null?mapCountPrevAssets.get("Deferred tax assets (net)"):0));
    	nonCurrentAssetsList.add(Map.of("title", "Long-term loans and advances","price","1000","totalCurrentAmount",mapCountAssets.get("Long-term loans and advances")!=null?mapCountAssets.get("Long-term loans and advances"):0,"totalPreviousAmount",mapCountPrevAssets.get("Long-term loans and advances")!=null?mapCountPrevAssets.get("Long-term loans and advances"):0));
    	nonCurrentAssetsList.add(Map.of("title", "Other non-current assets","price","1000","totalCurrentAmount",mapCountAssets.get("Other non-current assets")!=null?mapCountAssets.get("Other non-current assets"):0,"totalPreviousAmount",mapCountPrevAssets.get("Other non-current assets")!=null?mapCountPrevAssets.get("Other non-current assets"):0));
    	nonCurrentAssets.put("data", nonCurrentAssetsList);
    	assetsList.add(nonCurrentAssets);

    	// Current Assets
    	assetsList.add(Map.of(
    			"title", "Current Assets",
    			"data", List.of(
    					Map.of("title", "Current investments","price","1000","totalCurrentAmount",mapCountAssets.get("Current investments")!=null?mapCountAssets.get("Current investments"):0,"totalPreviousAmount",mapCountPrevAssets.get("Current investments")!=null?mapCountPrevAssets.get("Current investments"):0),
    					Map.of("title", "Inventories","price","1000","totalCurrentAmount",mapCountAssets.get("Inventories")!=null?mapCountAssets.get("Inventories"):0,"totalPreviousAmount",mapCountPrevAssets.get("Inventories")!=null?mapCountPrevAssets.get("Inventories"):0),
    					Map.of("title", "Trade receivables","price","1000","totalCurrentAmount",mapCountAssets.get("Trade receivables")!=null?mapCountAssets.get("Trade receivables"):0,"totalPreviousAmount",mapCountPrevAssets.get("Trade receivables")!=null?mapCountPrevAssets.get("Trade receivables"):0),
    					Map.of("title", "Cash and cash equivalents","price","1000","totalCurrentAmount",mapCountAssets.get("Cash and cash equivalents")!=null?mapCountAssets.get("Cash and cash equivalents"):0,"totalPreviousAmount",mapCountPrevAssets.get("Cash and cash equivalents")!=null?mapCountPrevAssets.get("Cash and cash equivalents"):0),
    					Map.of("title", "Short-term loans and advances","price","1000","totalCurrentAmount",mapCountAssets.get("Short-term loans and advances")!=null?mapCountAssets.get("Short-term loans and advances"):0,"totalPreviousAmount",mapCountPrevAssets.get("Short-term loans and advances")!=null?mapCountPrevAssets.get("Short-term loans and advances"):0),
    					Map.of("title", "Other current assets","price","1000","totalCurrentAmount",mapCountAssets.get("Other current assets")!=null?mapCountAssets.get("Other current assets"):0,"totalPreviousAmount",mapCountPrevAssets.get("Other current assets")!=null?mapCountPrevAssets.get("Other current assets"):0)
    					)
    			));

    	assets.put("data", assetsList);
    	response.add(assets);

    	return response;

    	//		return null;
    }
}
