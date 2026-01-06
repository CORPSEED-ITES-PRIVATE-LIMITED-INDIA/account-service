package com.account.service;

import java.util.List;
import java.util.Map;

import com.account.dto.CreateTdsDto;
import org.springframework.stereotype.Service;

import com.account.domain.TdsDetail;

@Service
public interface TdsService {

	TdsDetail createTds(CreateTdsDto createTdsDto);

	List<TdsDetail> getAllTds();

	Map<String, Object> getAllTdsCount();

	Boolean updateTdsClaimAmount(Long id, double amount, String document,Long currentUserId);

}
