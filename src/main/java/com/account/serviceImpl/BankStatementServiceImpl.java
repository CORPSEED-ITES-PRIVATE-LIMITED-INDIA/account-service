package com.account.serviceImpl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.account.dto.CreateBankStatementDto;
import com.account.repository.PaymentRegisterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.domain.BankAccount;
import com.account.domain.BankStatement;
import com.account.domain.PaymentRegister;
import com.account.repository.BankAccountRepository;
import com.account.repository.BankStatementRepository;
import com.account.service.BankStatementService;

@Service
public class BankStatementServiceImpl implements BankStatementService{

	@Autowired
	BankStatementRepository bankStatementRepository;
	
	@Autowired
	BankAccountRepository bankAccountRepository;

	@Autowired
    PaymentRegisterRepository paymentRegisterRepository;
	@Override
	public BankStatement createBankStatements(CreateBankStatementDto createBankStatementDto) {
		BankStatement bankStatement = new BankStatement();
		bankStatement.setName(createBankStatementDto.getName());
		bankStatement.setPaymentDate(createBankStatementDto.getPaymentDate());
		bankStatement.setTotalAmount(createBankStatementDto.getTotalAmount());
		bankStatement.setLeftAmount(createBankStatementDto.getTotalAmount());
		BankAccount bankAccount = bankAccountRepository.findById(createBankStatementDto.getBankAccountId()).get();
		bankStatement.setCreateDate(new Date());
		bankStatement.setBankAccount(bankAccount);
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
			map.put("bankAccount", b.getBankAccount());

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
			map.put("bankAccount", b.getBankAccount());

			map.put("paymentDate", b.getPaymentDate());
			map.put("updateDate", b.getUpdateDate());
			map.put("paymentRegister", b.getPaymentRegister());

			result.add(map);
		}
		return result;
	}
	@Override
	public List<BankAccount> getAllBankAccounts() {
		List<BankAccount> bankAccount = bankAccountRepository.findAll();
		return bankAccount;
	}

}
