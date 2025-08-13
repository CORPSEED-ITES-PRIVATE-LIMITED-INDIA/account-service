package com.account.dashboard.controller.ledger;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.domain.GstDetails;
import com.account.dashboard.dto.CreateGstDto;
import com.account.dashboard.dto.LedgerDto;
import com.account.dashboard.util.UrlsMapping;
import com.account.dashboard.service.GstDetailsService;

@RestController
public class GstDetailsController {
      
    @Autowired
	GstDetailsService gstDetailsService;
    
	@PostMapping(UrlsMapping.CREATE_GST)
	public Boolean createGst(@RequestBody CreateGstDto createGstDto){
		Boolean res=gstDetailsService.createGst(createGstDto);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_GST_BY_ID)
	public GstDetails getGstById(@RequestParam Long id){
		GstDetails res=gstDetailsService.getGstById(id);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_GST)
	public List<GstDetails> getAllGst(){
		List<GstDetails> res=gstDetailsService.getAllGst();	
		return res;
	}
}
