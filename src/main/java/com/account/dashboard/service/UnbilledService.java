package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.account.dashboard.dto.UnbilledDTO;

@Service
public interface UnbilledService {

	List<UnbilledDTO> getAllUnbilled();

	UnbilledDTO getUnbilledById(Long id);

	UnbilledDTO createUnbilled(UnbilledDTO dto);

	UnbilledDTO update(Long id, UnbilledDTO dto);

	void delete(Long id);

	Map<String, Object> getAllUnbilledAmount();

}
