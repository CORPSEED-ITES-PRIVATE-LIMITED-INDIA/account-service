package com.account.dashboard.serviceImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.account.LedgerType;
import com.account.dashboard.dto.CreateLedgerTypeDto;
import com.account.dashboard.dto.UpdateLedgerTypeDto;
import com.account.dashboard.repository.LedgerTypeRepository;
import com.account.dashboard.service.LedgerTypeService;

@Service
public class LedgerTypeServiceImpl implements LedgerTypeService{

	@Autowired
	LedgerTypeRepository ledgerTypeRepository;
	
//	@Override
//	public Boolean createLedgerType(String name) {
//		Boolean flag=false;
//		LedgerType ledgerType = new LedgerType();
//		ledgerType.setName(name);
//		ledgerType.set
//		ledgerTypeRepository.save(ledgerType);
//		flag=true;
//		return flag;
//	}

	@Override
	public Boolean createLedgerType(CreateLedgerTypeDto createLedgerTypeDto) {
		Boolean flag=false;
		LedgerType ledgerType = new LedgerType();
		ledgerType.setName(createLedgerTypeDto.getName());
		ledgerType.setDebitCredit(createLedgerTypeDto.isDebitCredit());
		ledgerType.setSubLeadger(createLedgerTypeDto.isSubLeadger());
		
        if(createLedgerTypeDto.getId()!=null && createLedgerTypeDto.getId()!=0) {
    		LedgerType lType =ledgerTypeRepository.findById(createLedgerTypeDto.getId()).get();
    		ledgerType.setLedgerType(lType);
        }else {
    		ledgerType.setLedgerType(null);
		}
		ledgerType.setUsedForCalculation(createLedgerTypeDto.isUsedForCalculation());
		ledgerTypeRepository.save(ledgerType);
		flag=true;
		return flag;
	}
	
	
	@Override
	public Boolean updateLedgerType(UpdateLedgerTypeDto updateLedgerTypeDto) {
		Boolean flag=false;
		LedgerType ledgerType = ledgerTypeRepository.findById(updateLedgerTypeDto.getSubLedgerId()).get();
		ledgerType.setName(updateLedgerTypeDto.getName());
		ledgerType.setDebitCredit(updateLedgerTypeDto.isDebitCredit());
		ledgerType.setSubLeadger(updateLedgerTypeDto.isSubLeadger());
        if(updateLedgerTypeDto.isSubLeadger() && (!updateLedgerTypeDto.getSubLedgerId().equals(updateLedgerTypeDto.getId()))) {
    		ledgerType.setSubLeadger(updateLedgerTypeDto.isSubLeadger());
    		LedgerType lType = ledgerTypeRepository.findById(updateLedgerTypeDto.getId()).get();
    		ledgerType.setLedgerType(lType);
        }else {
    		ledgerType.setSubLeadger(updateLedgerTypeDto.isSubLeadger());
		}
		ledgerType.setUsedForCalculation(updateLedgerTypeDto.isUsedForCalculation());
		ledgerTypeRepository.save(ledgerType);
		flag=true;
		return flag;
	}

	@Override
	public List<LedgerType> getAllLedgerType() {
		boolean b=false;
		List<LedgerType>leadgerTypeList=ledgerTypeRepository.findAllByIsDeleted(b);
		return leadgerTypeList;
	}


	@Override
	public Boolean deleteLedgerType(Long id) {
		Boolean flag=false;
		Optional<LedgerType> ledgerType = ledgerTypeRepository.findById(id);
		if(ledgerType!=null) {
			LedgerType lType = ledgerType.get();
			lType.setDeleted(false);
			ledgerTypeRepository.save(lType);
			flag=true;
		}
		return flag;
	}


	@Override
	public Map<String,Object> getAllLedgerTypeById(Long id) {
		
		LedgerType ledgerType = ledgerTypeRepository.findById(id).orElse(null);
		// Create a map to store LedgerType and Ledger-related information
		Map<String, Object> map = new HashMap<>();

		if (ledgerType != null) {
		    // Put LedgerType fields into the map
		    map.put("id", ledgerType.getId());
		    map.put("name", ledgerType.getName());
		    map.put("isSubLeadger", ledgerType.isSubLeadger());
		    map.put("isDebitCredit", ledgerType.isDebitCredit());
		    map.put("isUsedForCalculation", ledgerType.isUsedForCalculation());
		    map.put("isParent", ledgerType.isParent());
		    map.put("createDate", ledgerType.getCreateDate());

		    // HSN details
		    map.put("hsnSacPresent", ledgerType.isHsnSacPresent());
		    map.put("hsnSacDetails", ledgerType.getHsnSacDetails());
		    map.put("classification", ledgerType.getClassification());
		    map.put("hsnSacData", ledgerType.getHsnSacData());
		    map.put("hsnDescription", ledgerType.getHsnDescription());
		    map.put("ledgerTypeId", ledgerType.getLedgerType()!=null?ledgerType.getLedgerType():null);

		    // GST rate details
		    map.put("gstRateDetailPresent", ledgerType.isGstRateDetailPresent());
		    map.put("gstRateDetails", ledgerType.getGstRateDetails());
		    map.put("taxabilityType", ledgerType.getTaxabilityType());
		    map.put("gstRatesData", ledgerType.getGstRatesData());

		    // Bank details
		    map.put("bankAccountPresent", ledgerType.isBankAccountPresent());
		    map.put("accountHolderName", ledgerType.getAccountHolderName());
		    map.put("accountNo", ledgerType.getAccountNo());
		    map.put("ifscCode", ledgerType.getIfscCode());
		    map.put("swiftCode", ledgerType.getSwiftCode());
		    map.put("bankName", ledgerType.getBankName());
		    map.put("branch", ledgerType.getBranch());
		    map.put("branch", ledgerType.getBranch());

		    // Common flag
		    map.put("isDeleted", ledgerType.isDeleted());
		}
		return map;
	}



	@Override
	public List<LedgerType> groupSearchApi(String name) {
		List<LedgerType>ledgerType=ledgerTypeRepository.findByNameGlobal(name);
		return ledgerType;
	}

	
	

}
