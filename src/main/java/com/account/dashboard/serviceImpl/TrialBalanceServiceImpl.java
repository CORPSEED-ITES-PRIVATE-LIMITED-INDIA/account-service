package com.account.dashboard.serviceImpl;

import java.util.ArrayList;
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
	public List<Map<String, Object>> getAllTrialBalance() {
		List<LedgerType> group = ledgerTypeRepository.findAll();
		List<Map<String, Object>>result=new ArrayList<>();
		for(LedgerType g:group) {
			Map<String,Object>map=new HashMap<>();
			List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(g.getId());
	         LedgerType ledgerType = ledgerTypeRepository.findById(g.getId()).get();
			List<Voucher>voucherList=voucherRepository.findAllByLedgerIdIn(ledgerList);

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
			map.put("groupName", ledgerType.getName());
			map.put("totalDebit", totalDebit);
			map .put("totalAmount", totalAmount);
			result.add(map);
		}
		return result;
	}

}
