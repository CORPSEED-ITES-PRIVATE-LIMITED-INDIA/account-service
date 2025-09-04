package com.account.dashboard.serviceImpl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.BankStatement;
import com.account.dashboard.domain.PaymentRegister;
import com.account.dashboard.dto.CreateBankStatementDto;
import com.account.dashboard.repository.BankStatementRepository;
import com.account.dashboard.repository.PaymentRegisterRepository;
import com.account.dashboard.service.BankStatementService;

@Service
public class BankStatementServiceImpl implements BankStatementService{

	@Autowired
	BankStatementRepository bankStatementRepository;

	@Autowired
	PaymentRegisterRepository paymentRegisterRepository;
	@Override
	public BankStatement createBankStatements(CreateBankStatementDto createBankStatementDto) {
		BankStatement bankStatement = new BankStatement();
		bankStatement.setName(createBankStatementDto.getName());
		bankStatement.setPaymentDate(createBankStatementDto.getPaymentDate());
		bankStatement.setTotalAmount(createBankStatementDto.getTotalAmount());
		bankStatement.setLeftAmount(createBankStatementDto.getTotalAmount());
		bankStatement.setCreateDate(new Date());
		bankStatement.setTransactionId(createBankStatementDto.getTransactionId());
		bankStatement.setTotalAmount(createBankStatementDto.getTotalAmount());
		bankStatement.setUpdateDate(new Date());
		bankStatement.setPaymentDate(new Date());
		bankStatementRepository.save(bankStatement);
		return bankStatement;
	}
	@Override
	public List<Map<String, Object>> getUnUsedBankStatements() {
		List<BankStatement> bankStatements = bankStatementRepository.findAllByLeftAmount();
		List<Map<String, Object>>result = new ArrayList<>();
		for(BankStatement b:bankStatements) {
			Map<String, Object>map = new HashMap<>();
			map.put("id", b.getId());
			map.put("name", b.getName());
			map.put("transaction", b.getTransactionId());
			map.put("createDate", b.getCreateDate());
			map.put("leftAmount", b.getLeftAmount());
			map.put("totalAmount", b.getTotalAmount());

			map.put("paymentDate", b.getPaymentDate());
			map.put("updateDate", b.getUpdateDate());
			result.add(map);
		}
		return result;
	}


	@Override
	public Boolean addRegisterAmountInBankStatement(Long bankstatementId, Long registerAmountId) throws Exception {
		Boolean flag=false;
		BankStatement bankStatement = bankStatementRepository.findById(bankstatementId).get();
		PaymentRegister paymentRegister = paymentRegisterRepository.findById(registerAmountId).get();
		Double d = Double.valueOf(paymentRegister.getTotalAmount());
		if(bankStatement.getLeftAmount()>0 && bankStatement.getLeftAmount()>d) {
			if(bankStatement.getTransactionId().equals(paymentRegister.getTransactionId())) {

				double leftAmount=bankStatement.getLeftAmount()-d;
				bankStatement.setLeftAmount(leftAmount);
				List<PaymentRegister> paymentList = bankStatement.getPaymentRegister();
				paymentList.add(paymentRegister);
				bankStatementRepository.save(bankStatement);
				paymentRegister.setStatus("approved");
				paymentRegisterRepository.save(paymentRegister);
				flag=true;
			}else {
				throw new Exception("transaction id not match please check");
			}
		}
		return flag;

	}
	@Override
	public List<Map<String, Object>> getAllBankStatements() {
		List<BankStatement> bankStatements = bankStatementRepository.findAll();
		List<Map<String, Object>>result = new ArrayList<>();
		for(BankStatement b:bankStatements) {
			Map<String, Object>map = new HashMap<>();
			map.put("id", b.getId());
			map.put("name", b.getName());
			map.put("transaction", b.getTransactionId());
			map.put("createDate", b.getCreateDate());
			map.put("leftAmount", b.getLeftAmount());
			map.put("totalAmount", b.getTotalAmount());

			map.put("paymentDate", b.getPaymentDate());
			map.put("updateDate", b.getUpdateDate());
			map.put("paymentRegister", b.getPaymentRegister());

			result.add(map);
		}
		return result;
	}

}
