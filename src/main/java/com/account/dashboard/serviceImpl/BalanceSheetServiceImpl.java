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
}
