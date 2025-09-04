package com.account.dashboard.serviceImpl;

import java.util.List;
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
		LedgerType ledgerType = ledgerTypeRepository.findById(updateLedgerTypeDto.getId()).get();
		ledgerType.setName(updateLedgerTypeDto.getName());
		ledgerType.setDebitCredit(updateLedgerTypeDto.isDebitCredit());
		ledgerType.setSubLeadger(updateLedgerTypeDto.isSubLeadger());
        if(updateLedgerTypeDto.isSubLeadger()) {
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
	public LedgerType getAllLedgerTypeById(Long id) {
		LedgerType ledgerType = ledgerTypeRepository.findById(id).get();
		return ledgerType;
	}



	@Override
	public List<LedgerType> groupSearchApi(String name) {
		List<LedgerType>ledgerType=ledgerTypeRepository.findByNameGlobal(name);
		return ledgerType;
	}

	
	

}
