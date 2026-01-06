package com.account.service;

import java.util.List;

import com.account.dto.CreateBankDetailsDto;
import com.account.dto.UpdateBankDetailsDto;
import org.springframework.stereotype.Service;

import com.account.domain.BankDetails;

@Service
public interface BankDetailsService {

	Boolean createBankDetails(CreateBankDetailsDto createBankDetailsDto);

	List<BankDetails> getAllBankDetails();

	BankDetails updateBankDetails(UpdateBankDetailsDto updateBankDetailsDto);

}
