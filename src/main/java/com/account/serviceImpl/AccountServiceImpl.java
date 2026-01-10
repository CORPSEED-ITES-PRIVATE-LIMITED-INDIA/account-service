package com.account.serviceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.account.domain.ManageSales;
import com.account.dto.CreateAccountData;
import com.account.dto.UpdateAccountData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.domain.Amount;
import com.account.domain.BankAccount;
import com.account.domain.Organization;
import com.account.domain.User;
import com.account.repository.AccountRepository;
import com.account.repository.AmountRepository;
import com.account.repository.OrganizationRepository;
import com.account.repository.UserRepository;
import com.account.service.AccountService;

@Service
public class AccountServiceImpl implements AccountService{

	@Autowired
	AccountRepository accountRepository;
	
	@Autowired
	OrganizationRepository  organizationRepository;
	
	@Autowired
	AmountRepository amountRepository;
	
	@Autowired
	UserRepository userRepository;
	
	@Override
	public ManageSales getAccountData(Long accountId) {
		Optional<ManageSales> account = accountRepository.findById(accountId);
		return account.get();
	}

	@Override
	public List<ManageSales> getAllAccountData() {
		List<ManageSales> accountList=accountRepository.findAll();
		return accountList;
	}

	@Override
	public ManageSales DeleteAccountData(Long manageSalesId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ManageSales createAccountData(CreateAccountData createAccountData) {
		ManageSales manageSales=accountRepository.findByProjectId(createAccountData.getProjectId());
		Amount amount = new Amount();
		amount.setTransactionId(createAccountData.getTransactionId());
		amount.setProfessionalFees(createAccountData.getProfessionalFees());
		amount.setPaymentDate(createAccountData.getPaymentDate());
		amount.setTotalAmount(createAccountData.getTotalAmount());
		amount.setServiceCharge(createAccountData.getServiceCharge());
		amount.setGovermentfees(createAccountData.getGovermentfees());
		amount.setOtherFees(createAccountData.getOtherFees());
		amount.setProfessionalFees(createAccountData.getProfessionalFees());
		amount.setApproveDate(createAccountData.getApproveDate());
		amount.setPaymentDate(createAccountData.getPaymentDate());
		amount.setDoc(createAccountData.getDoc());
		User aprovedById = userRepository.findById(createAccountData.getApprovedById()).get();
		amount.setApprovedBy(aprovedById);
		User createdBy = userRepository.findById(createAccountData.getCreatedById()).get();
		amount.setCreatedBy(createdBy);
		amount.setRemark(createAccountData.getRemark());
		amount.setTransactionType(createAccountData.getTransactionType());
		amountRepository.save(amount);
		if(manageSales==null) {
			manageSales = new ManageSales();
			manageSales.setClientId(manageSales.getClientId());
//			User createdBy = userRepository.findById(createAccountData.getCreatedById()).get();
			manageSales.setCreatedBy(createdBy);
			manageSales.setEstimateNo(createAccountData.getEstimateNo());
			manageSales.setGovermentfees(createAccountData.getGovermentfees());
			manageSales.setOtherFees(createAccountData.getOtherFees());

			manageSales.setProfessionalFees(createAccountData.getProfessionalFees());
			manageSales.setProjectId(createAccountData.getProjectId());
			manageSales.setServiceCharge(createAccountData.getServiceCharge());
			List<Amount>manageSalesAmount=new ArrayList<>();
			manageSalesAmount.add(amount);
			manageSales.setManageSalesAmount(manageSalesAmount);
			manageSales.setTotalAmount(createAccountData.getTotalAmount());
			User updatedBy = userRepository.findById(createAccountData.getCreatedById()).get();
			manageSales.setUpdatedBy(updatedBy);
		}else {
			List<Amount>manageSalesAmount= manageSales.getManageSalesAmount();
			manageSalesAmount.add(amount);
			manageSales.setManageSalesAmount(manageSalesAmount);
		}
         accountRepository.save(manageSales);
		return manageSales;
	}

	@Override
	public ManageSales updateAccountData(UpdateAccountData updateAccountData) {
		
		return null;
	}


}
