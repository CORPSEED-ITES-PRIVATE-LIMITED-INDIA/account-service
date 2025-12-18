package com.account.dashboard.serviceImpl.report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.PaymentRegister;
import com.account.dashboard.domain.VendorPaymentRegister;
import com.account.dashboard.repository.PaymentRegisterRepository;
import com.account.dashboard.repository.VendorPaymentRegisterRepo;
import com.account.dashboard.service.report.SalesReportService;

@Service
public class SalesReportServiceImpl implements SalesReportService{
	
	@Autowired
	PaymentRegisterRepository paymentRegisterRepository;
	
	@Autowired
	VendorPaymentRegisterRepo vendorPaymentRegisterRepo;
	
	
	
	@Override
	public List<Map<String, Object>> getAllSalesReport(int page, int size, String status) {

		List<Map<String,Object>>res=new ArrayList<>();
		Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
		List<String>statusList=new ArrayList<>();

		// For descending order, use:
		Pageable pageableDesc = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
		if(status.equals("all")) {
			statusList=Arrays.asList("initiated","approved","hold","disapproved");
		}else {
			statusList.add(status);
		}
		List<PaymentRegister> paymentRegisterList = paymentRegisterRepository.findAllByStatus(pageableDesc,statusList).getContent();
		List<Map<String,Object>>result=new ArrayList<>();
		for(PaymentRegister p :paymentRegisterList) {
			Map<String, Object> map = new HashMap<>();

			map.put("id", p.getId());
			map.put("leadId", p.getLeadId());
			map.put("estimateId", p.getEstimateId());

			map.put("billingQuantity", p.getBillingQuantity());
			map.put("paymentType", p.getPaymentType());

			map.put("name", p.getName());
			map.put("emails", p.getEmails());
			map.put("contactNo", p.getContactNo());
			map.put("whatsappNo", p.getWhatsappNo());

			map.put("registerBy", p.getRegisterBy());
			map.put("createdById", p.getCreatedById());
			map.put("transactionId", p.getTransactionId());
			map.put("serviceName", p.getServiceName());

			map.put("govermentfees", p.getGovermentfees());
			map.put("govermentGst", p.getGovermentGst());
			map.put("govermentGstPercent", p.getGovermentGstPercent());

			map.put("professionalFees", p.getProfessionalFees());
			map.put("profesionalGst", p.getProfesionalGst());
			map.put("professionalGstPercent", p.getProfessionalGstPercent());
			map.put("professionalGstAmount", p.getProfessionalGstAmount());

			map.put("tdsPresent", p.isTdsPresent());
			map.put("tdsAmount", p.getTdsAmount());
			map.put("tdsPercent", p.getTdsPercent());

			map.put("serviceCharge", p.getServiceCharge());
			map.put("serviceGst", p.getServiceGst());
			map.put("serviceGstPercent", p.getServiceGstPercent());

			map.put("otherFees", p.getOtherFees());
			map.put("otherGst", p.getOtherGst());
			map.put("otherGstPercent", p.getOtherGstPercent());

			map.put("totalAmount", p.getTotalAmount());
			if(p.getProductType().equalsIgnoreCase("Product")) {
				double vendorAmount = vendorPaymentData(p.getEstimateId());
				map.put("totalSaleAmount", p.getTotalAmount()-vendorAmount);
			}else {
				map.put("totalSaleAmount", p.getTotalAmount());
			}
			map.put("remark", p.getRemark());
			map.put("paymentDate", p.getPaymentDate());
			map.put("estimateNo", p.getEstimateNo());
			map.put("status", p.getStatus());

			map.put("docPersent", p.getDocPersent());
			map.put("filingPersent", p.getFilingPersent());
			map.put("liasoningPersent", p.getLiasoningPersent());
			map.put("certificatePersent", p.getCertificatePersent());

			map.put("companyName", p.getCompanyName());
			map.put("companyId", p.getCompanyId());
			map.put("updateDate", p.getUpdateDate());

			map.put("approvedById", p.getApprovedById());
			map.put("approveDate", p.getApproveDate());

			map.put("orderAmount", p.getTotalAmount());
			
			map.put("assigneeId", p.getCreatedByUser()!=null?p.getCreatedByUser().getId():null);
			map.put("assigneeName", p.getCreatedByUser()!=null?p.getCreatedByUser().getFullName():null);
			map.put("assigneeEmail", p.getCreatedByUser()!=null?p.getCreatedByUser().getEmail():null);
            
			map.put("status", p.getStatus());
			result.add(map);

		}
		return result;
//		return paymentRegisterList;
	
	}
	
	public double vendorPaymentData(Long estimateId){
		VendorPaymentRegister vendorPaymentRegister = vendorPaymentRegisterRepo.findByEstimateId(estimateId);
		return vendorPaymentRegister.getTotalAmount();
	}

	@Override
	public Long getAllSalesReportCount(String status) {
		List<Map<String,Object>>res=new ArrayList<>();
		List<String>statusList=new ArrayList<>();
		if(status.equals("all")) {
			statusList=Arrays.asList("initiated","approved","hold","disapproved");
		}else {
			statusList.add(status);
		}
		Long prCount = paymentRegisterRepository.findCountByStatus(statusList);
		return prCount;
	}
	
	
	
	
	
	public List<Map<String, Object>> getAllSalesReportForExport(String status) {

		List<Map<String,Object>>res=new ArrayList<>();
		List<String>statusList=new ArrayList<>();

		// For descending order, use:
		if(status.equals("all")) {
			statusList=Arrays.asList("initiated","approved","hold","disapproved");
		}else {
			statusList.add(status);
		}
		List<PaymentRegister> paymentRegisterList = paymentRegisterRepository.findAllByStatus(statusList);
		List<Map<String,Object>>result=new ArrayList<>();
		for(PaymentRegister p :paymentRegisterList) {
			Map<String, Object> map = new HashMap<>();

			map.put("id", p.getId());
			map.put("leadId", p.getLeadId());
			map.put("estimateId", p.getEstimateId());

			map.put("billingQuantity", p.getBillingQuantity());
			map.put("paymentType", p.getPaymentType());

			map.put("name", p.getName());
			map.put("emails", p.getEmails());
			map.put("contactNo", p.getContactNo());
			map.put("whatsappNo", p.getWhatsappNo());

			map.put("registerBy", p.getRegisterBy());
			map.put("createdById", p.getCreatedById());
			map.put("transactionId", p.getTransactionId());
			map.put("serviceName", p.getServiceName());

			map.put("govermentfees", p.getGovermentfees());
			map.put("govermentGst", p.getGovermentGst());
			map.put("govermentGstPercent", p.getGovermentGstPercent());

			map.put("professionalFees", p.getProfessionalFees());
			map.put("profesionalGst", p.getProfesionalGst());
			map.put("professionalGstPercent", p.getProfessionalGstPercent());
			map.put("professionalGstAmount", p.getProfessionalGstAmount());

			map.put("tdsPresent", p.isTdsPresent());
			map.put("tdsAmount", p.getTdsAmount());
			map.put("tdsPercent", p.getTdsPercent());

			map.put("serviceCharge", p.getServiceCharge());
			map.put("serviceGst", p.getServiceGst());
			map.put("serviceGstPercent", p.getServiceGstPercent());

			map.put("otherFees", p.getOtherFees());
			map.put("otherGst", p.getOtherGst());
			map.put("otherGstPercent", p.getOtherGstPercent());

			map.put("totalAmount", p.getTotalAmount());
			if(p.getProductType().equalsIgnoreCase("Product")) {
				double vendorAmount = vendorPaymentData(p.getEstimateId());
				map.put("totalSaleAmount", p.getTotalAmount()-vendorAmount);
			}else {
				map.put("totalSaleAmount", p.getTotalAmount());
			}
			map.put("remark", p.getRemark());
			map.put("paymentDate", p.getPaymentDate());
			map.put("estimateNo", p.getEstimateNo());
			map.put("status", p.getStatus());

			map.put("docPersent", p.getDocPersent());
			map.put("filingPersent", p.getFilingPersent());
			map.put("liasoningPersent", p.getLiasoningPersent());
			map.put("certificatePersent", p.getCertificatePersent());

			map.put("companyName", p.getCompanyName());
			map.put("companyId", p.getCompanyId());
			map.put("updateDate", p.getUpdateDate());

			map.put("approvedById", p.getApprovedById());
			map.put("approveDate", p.getApproveDate());

			map.put("orderAmount", p.getTotalAmount());
			
			map.put("assigneeId", p.getCreatedByUser()!=null?p.getCreatedByUser().getId():null);
			map.put("assigneeName", p.getCreatedByUser()!=null?p.getCreatedByUser().getFullName():null);
			map.put("assigneeEmail", p.getCreatedByUser()!=null?p.getCreatedByUser().getEmail():null);
            
			map.put("status", p.getStatus());
			result.add(map);

		}
		return result;
//		return paymentRegisterList;
	
	}

}
