package com.account.dashboard.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.account.dashboard.dto.UnbilledDTO;

@Service
public interface UnbilledService {

	List<UnbilledDTO> getAll();

	UnbilledDTO getById(Long id);

	UnbilledDTO create(UnbilledDTO dto);

	UnbilledDTO update(Long id, UnbilledDTO dto);

	void delete(Long id);

}
