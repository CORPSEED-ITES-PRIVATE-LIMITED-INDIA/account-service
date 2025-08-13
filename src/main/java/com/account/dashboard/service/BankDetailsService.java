package com.account.dashboard.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.account.dashboard.domain.BankDetails;
import com.account.dashboard.dto.CreateBankDetailsDto;
import com.account.dashboard.dto.UpdateBankDetailsDto;

@Service
public interface BankDetailsService {

	Boolean createBankDetails(CreateBankDetailsDto createBankDetailsDto);

	List<BankDetails> getAllBankDetails();

	BankDetails updateBankDetails(UpdateBankDetailsDto updateBankDetailsDto);

}
