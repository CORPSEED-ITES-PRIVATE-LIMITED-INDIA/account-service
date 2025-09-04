package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.account.dashboard.domain.TdsDetail;
import com.account.dashboard.dto.CreateTdsDto;

@Service
public interface TdsService {

	TdsDetail createTds(CreateTdsDto createTdsDto);

	List<TdsDetail> getAllTds();

	Map<String, Object> getAllTdsCount();

}
