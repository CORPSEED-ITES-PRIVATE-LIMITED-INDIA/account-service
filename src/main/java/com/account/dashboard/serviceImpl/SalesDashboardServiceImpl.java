package com.account.dashboard.serviceImpl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.PaymentRegister;
import com.account.dashboard.domain.User;
import com.account.dashboard.dto.GraphDateFilter;
import com.account.dashboard.repository.PaymentRegisterRepository;
import com.account.dashboard.repository.UserRepository;
import com.account.dashboard.service.dashboard.SalesDashboardService;

@Service 
public class SalesDashboardServiceImpl implements SalesDashboardService {
	
	@Autowired
	UserRepository userRepository;
	
	@Autowired
	PaymentRegisterRepository paymentRegisterRepository;

	@Override
	public List<Map<String, Object>> getAllLeadMonthWiseV2(GraphDateFilter graphDateFilter) {



		List<Object[]> projects=new ArrayList<>();

		String toDate=graphDateFilter.getToDate();
		String fromDate=graphDateFilter.getFromDate();
		String startDate = toDate;
		String endDate = fromDate;
		List<Object[]> leads=new ArrayList<>();
		
		
		User user = userRepository.findById(graphDateFilter.getCurrentUserId()).get();
		List<String> role =user.getUserRole().stream().map(i->i.getName()).collect(Collectors.toList());
		System.out.println("role . ...."+role);
		if(true) {

			if(graphDateFilter.getUserId()!=null) {
				leads=paymentRegisterRepository.findIdAndNameAndCreateDateByInBetweenDateAndAssignee(startDate, endDate,graphDateFilter.getUserId());


			}else{
				leads=paymentRegisterRepository.findIdAndNameAndCreateDateByInBetweenDate(startDate, endDate);
			}

		}else{
			
			List<Long> uList = new ArrayList<>();
//			List<Long> uList = userRepository.findUserIdByMangerId(graphDateFilter.getCurrentUserId());
			if(uList!=null && uList.size()>0) {
				if(graphDateFilter.getUserId()==null) {

					leads=paymentRegisterRepository.findIdAndNameAndCreateDateByInBetweenDateAndAssignee(startDate, endDate,graphDateFilter.getCurrentUserId());

				}else {

					leads=paymentRegisterRepository.findIdAndNameAndCreateDateByInBetweenDateAndAssignee(startDate, endDate,graphDateFilter.getUserId());
				}
			}else {
				leads=paymentRegisterRepository.findIdAndNameAndCreateDateByInBetweenDateAndAssignee(startDate, endDate,graphDateFilter.getCurrentUserId());

			}
		}

		Map<String,Map<String,Object>>map=new LinkedHashMap();
		for(Object[] l:leads) {
			Long id=(Long)l[0];
			Double totalAmount=(Double)l[1];
			Date createDate=(Date)l[2];
			//			createDate=taskManagmentServiceImpl.convertTimeV1(createDate);̥
			int year = 1900+createDate.getYear();
			String temp=createDate.getMonth()+""+year;
			if(map.containsKey(temp)) {

				Map<String, Object> c = map.get(temp);
				Double cValue=(Double)c.get("counts");
				c.put("date", createDate);
				c.put("counts", cValue+totalAmount);
				map.put(temp,c);
			}else {
				Long count = 1l; ;
				Map<String,Object>m=new HashMap<>();
				m.put("counts", totalAmount);
				m.put("date", createDate);
				map.put(temp, m);
			}
		}

		List<Map<String,Object>>result = new ArrayList<>();
		for(Entry<String,Map<String,Object>> entry:map.entrySet()) {
			Map<String,Object>m=new HashMap<>();
			Map<String, Object> data = entry.getValue();
			m.put("name", data.get("date"));
			m.put("value", data.get("counts"));
			m.put("test", entry.getKey());

			result.add(m);
		}		
		result=result.stream().sorted(Comparator.comparing(i->(Date)i.get("name"))) .collect(Collectors.toList());	

		return result;



	
	}

	@Override
	public List<Map<String, Object>> getSalesDashboardRevenueByCompany(GraphDateFilter graphDateFilter) {
		List<Object[]> projects=new ArrayList<>();

		String toDate=graphDateFilter.getToDate();
		String fromDate=graphDateFilter.getFromDate();
		String startDate = toDate;
		String endDate = fromDate;
		List<PaymentRegister> leads=new ArrayList<>();
		
		
		User user = userRepository.findById(graphDateFilter.getCurrentUserId()).get();
		List<String> role =user.getUserRole().stream().map(i->i.getName()).collect(Collectors.toList());
		if(true) {

			if(graphDateFilter.getUserId()!=null) {
				leads=paymentRegisterRepository.findAllByInBetweenDateAndAssignee(startDate, endDate,graphDateFilter.getUserId());


			}else{
				leads=paymentRegisterRepository.findAllByInBetweenDate(startDate, endDate);
			}

		}else{
			
			List<Long> uList = new ArrayList<>();
//			List<Long> uList = userRepository.findUserIdByMangerId(graphDateFilter.getCurrentUserId());
			if(uList!=null && uList.size()>0) {
				if(graphDateFilter.getUserId()==null) {
					leads=paymentRegisterRepository.findAllByInBetweenDateAndAssignee(startDate, endDate,graphDateFilter.getCurrentUserId());

				}else {
					leads=paymentRegisterRepository.findAllByInBetweenDateAndAssignee(startDate, endDate,graphDateFilter.getUserId());

				}
			}else {
				leads=paymentRegisterRepository.findAllByInBetweenDateAndAssignee(startDate, endDate,graphDateFilter.getCurrentUserId());

			}
		}



//		Map<String,Map<String,Object>>map=new LinkedHashMap();
		Map<String,Double>res=new LinkedHashMap();

		for(PaymentRegister l:leads) {
			String companyName = l.getCompanyName();	
			Double totalAmount=l.getTotalAmount();
			
			
			if(res.containsKey(companyName)) {
				
				 Double c = res.get(companyName);
                 c=c+totalAmount;
                 res.put(companyName, c);

			}else {
				res.put(companyName, totalAmount);
			}
		}

		List<Map<String,Object>>result = new ArrayList<>();
		for(Entry<String,Double> entry:res.entrySet()) {
			Map<String,Object>m=new HashMap<>();
			m.put("name", entry.getKey());
			m.put("value", entry.getValue());
			result.add(m);
		}		
		result=result.stream().sorted(Comparator.comparing(i->(Double)i.get("value"))) .collect(Collectors.toList());	

		return result;

	}

	@Override
	public List<Map<String, Object>> getSalesDashboardRevenueByUser(GraphDateFilter graphDateFilter) {
		List<Object[]> projects=new ArrayList<>();

		String toDate=graphDateFilter.getToDate();
		String fromDate=graphDateFilter.getFromDate();
		String startDate = toDate;
		String endDate = fromDate;
		List<PaymentRegister> leads=new ArrayList<>();
		
		
		User user = userRepository.findById(graphDateFilter.getCurrentUserId()).get();
		List<String> role =user.getUserRole().stream().map(i->i.getName()).collect(Collectors.toList());
		if(true) {

			if(graphDateFilter.getUserId()!=null) {
				leads=paymentRegisterRepository.findAllByInBetweenDateAndAssignee(startDate, endDate,graphDateFilter.getUserId());


			}else{
				leads=paymentRegisterRepository.findAllByInBetweenDate(startDate, endDate);
			}

		}else{
			
			List<Long> uList = new ArrayList<>();
//			List<Long> uList = userRepository.findUserIdByMangerId(graphDateFilter.getCurrentUserId());
			if(uList!=null && uList.size()>0) {
				if(graphDateFilter.getUserId()==null) {
					leads=paymentRegisterRepository.findAllByInBetweenDateAndAssignee(startDate, endDate,graphDateFilter.getCurrentUserId());

				}else {
					leads=paymentRegisterRepository.findAllByInBetweenDateAndAssignee(startDate, endDate,graphDateFilter.getUserId());

				}
			}else {
				leads=paymentRegisterRepository.findAllByInBetweenDateAndAssignee(startDate, endDate,graphDateFilter.getCurrentUserId());

			}
		}



//		Map<String,Map<String,Object>>map=new LinkedHashMap();
		Map<String,Double>res=new LinkedHashMap();

		for(PaymentRegister l:leads) {
			String email = l.getCreatedByUser()!=null?l.getCreatedByUser().getEmail():"NA";	
			Double totalAmount=l.getTotalAmount();
			
			
			if(res.containsKey(email)) {
				
				 Double c = res.get(email);
                 c=c+totalAmount;
                 res.put(email, c);

			}else {
				res.put(email, totalAmount);
			}
		}

		List<Map<String,Object>>result = new ArrayList<>();
		for(Entry<String,Double> entry:res.entrySet()) {
			Map<String,Object>m=new HashMap<>();
			m.put("name", entry.getKey());
			m.put("value", entry.getValue());
			result.add(m);
		}		
		result=result.stream().sorted(Comparator.comparing(i->(Double)i.get("value"))) .collect(Collectors.toList());	

		return result;

	}

}
