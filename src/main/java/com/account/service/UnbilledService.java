package com.account.service;

import java.util.List;
import java.util.Map;

import com.account.dto.UnbilledDTO;
import org.springframework.stereotype.Service;

@Service
public interface UnbilledService {

	List<UnbilledDTO> getAllUnbilled();

	UnbilledDTO getUnbilledById(Long id);

	UnbilledDTO createUnbilled(UnbilledDTO dto);

	UnbilledDTO update(Long id, UnbilledDTO dto);

	void delete(Long id);

	Map<String, Object> getAllUnbilledAmount();

	List<Map<String,Object>> getAllUnbilled(int page, int size);

	int getAllUnbilledCount();

	List<Map<String, Object>> searchUnbilled(String name,String searchBy);

	List<Map<String, Object>> getAllUnbilledForExport(String startDate,String endDate);

	Map<String, Object> getUnbilledByIdForView(Long id);

}
