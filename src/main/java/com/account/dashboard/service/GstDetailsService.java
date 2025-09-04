package com.account.dashboard.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.account.dashboard.domain.GstDetails;
import com.account.dashboard.dto.CreateGstDto;

@Service
public interface GstDetailsService {

	Boolean createGst(CreateGstDto createGstDto);

	GstDetails getGstById(Long id);

	List<GstDetails> getAllGst();

}
