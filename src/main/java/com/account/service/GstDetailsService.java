package com.account.service;

import java.util.List;

import com.account.dto.CreateGstDto;
import org.springframework.stereotype.Service;

import com.account.domain.GstDetails;

@Service
public interface GstDetailsService {

	Boolean createGst(CreateGstDto createGstDto);

	GstDetails getGstById(Long id);

	List<GstDetails> getAllGst();

}
