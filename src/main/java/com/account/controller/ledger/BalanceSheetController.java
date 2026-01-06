package com.account.controller.ledger;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.service.BalanceSheetService;
import com.account.util.UrlsMapping;

@RestController
public class BalanceSheetController {
	
	@Autowired
	BalanceSheetService balanceSheetService;
	
	@GetMapping(UrlsMapping.GET_ALL_BALANCE_SHEET_LIABILITIES)
	public Map<String,Object> getAllBalanceSheetLiabilities(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate){
		Map<String,Object> res=balanceSheetService.getAllBalanceSheetLiabilities(startDate,endDate);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_BALANCE_SHEET_ASSETS)
	public Map<String,Object> getAllBalanceSheetAssets(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate){
		Map<String,Object> res=balanceSheetService.getAllBalanceSheetAssets(startDate,endDate);	
		return res;
	}
	
	
	@GetMapping(UrlsMapping.GET_ALL_BALANCE_SHEET_LIABILITIES_FOR_EXPORT)
	public List<Map<String,Object>> getAllBalanceSheetLiabilitiesForExport(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate){
		List<Map<String,Object>> res=balanceSheetService.getAllBalanceSheetLiabilitiesForExport(startDate,endDate);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_BALANCE_SHEET_ASSETS_FOR_EXPORT)
	public List<Map<String,Object>> getAllBalanceSheetAssetsForExport(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate){
		List<Map<String,Object>> res=balanceSheetService.getAllBalanceSheetAssetsForExport(startDate,endDate);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_GROUP_BY_PARENT_GROUP_ID)
	public List<Map<String,Object>> getAllGroupByParentGroupId(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate){
		List<Map<String,Object>> res=balanceSheetService.getAllGroupByParentGroupId(startDate,endDate);	
		return res;
	}
//	getLiabilitiesSubGroupByGroup
	@GetMapping(UrlsMapping.GET_LIABILITIES_SUBGROUP_BY_GROUP)
	public Map<String,Object> getLiabilitiesSubGroupByGroup(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate,@RequestParam String name){
		Map<String,Object> res=balanceSheetService.getLiabilitiesSubGroupByGroup(startDate,endDate,name);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ASSETS_SUBGROUP_BY_GROUP)
	public Map<String,Object> getAssetsSubGroupByGroup(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate,@RequestParam String name){
		Map<String,Object> res=balanceSheetService.getAssetsSubGroupByGroup(startDate,endDate,name);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_ASSETS_AND_LIABILITIES)
	public List<Map<String, Object>> getAllAssetsAndLiabilities(){
		List<Map<String, Object>> res=balanceSheetService.getAllAssetsAndLiabilities();	
		return res;
	}
}
