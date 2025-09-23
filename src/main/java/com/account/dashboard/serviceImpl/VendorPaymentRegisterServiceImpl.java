package com.account.dashboard.serviceImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.FileData;
import com.account.dashboard.domain.VendorPaymentRegister;
import com.account.dashboard.dto.CreateVendorAmountDto;
import com.account.dashboard.repository.PaymentRegisterRepository;
import com.account.dashboard.repository.VendorPaymentRegisterRepo;
import com.account.dashboard.service.VendorPaymentRegisterServcie;

@Service
public class VendorPaymentRegisterServiceImpl implements VendorPaymentRegisterServcie {

	
	@Autowired
	VendorPaymentRegisterRepo vendorPaymentRegisterRepo;

	@Override
	public VendorPaymentRegister createVendorPaymentRegister(CreateVendorAmountDto createVendorAmountDto) {
		VendorPaymentRegister vendorPaymentRegister = new VendorPaymentRegister();

		vendorPaymentRegister.setStatus("initiated");
		vendorPaymentRegister.setEstimateId(createVendorAmountDto.getEstimateId());
		vendorPaymentRegister.setBillingQuantity(createVendorAmountDto.getBillingQuantity());
		vendorPaymentRegister.setPaymentType(createVendorAmountDto.getPaymentType());
		vendorPaymentRegister.setCreatedById(createVendorAmountDto.getCreatedById());
		vendorPaymentRegister.setTransactionId(createVendorAmountDto.getTransactionId());
		vendorPaymentRegister.setServiceName(createVendorAmountDto.getServiceName());
		vendorPaymentRegister.setGovermentfees(createVendorAmountDto.getGovermentfees());
		vendorPaymentRegister.setGovermentGst(createVendorAmountDto.getGovermentGst());

		vendorPaymentRegister.setGovermentGstPercent(createVendorAmountDto.getGovermentGstPercent());
		vendorPaymentRegister.setLeadId(createVendorAmountDto.getLeadId());
		vendorPaymentRegister.setProfesionalGst(createVendorAmountDto.getProfesionalGst());

		double profesionalGst = createVendorAmountDto.getProfesionalGst();

		System.out.println("Professional  fees ...."+createVendorAmountDto.getProfessionalFees());
		System.out.println("Professional gst  ...."+profesionalGst);

		double gstAmount = ((createVendorAmountDto.getProfessionalFees()/100)*profesionalGst);
		System.out.println("Professional gst amount ...."+gstAmount);

		vendorPaymentRegister.setProfessionalGstAmount(gstAmount);

		vendorPaymentRegister.setServiceCharge(createVendorAmountDto.getServiceCharge());		

		vendorPaymentRegister.setOtherFees(createVendorAmountDto.getOtherFees());
		vendorPaymentRegister.setOtherGstPercent(createVendorAmountDto.getOtherGstPercent());
		vendorPaymentRegister.setTotalAmount(createVendorAmountDto.getTotalAmount());
		vendorPaymentRegister.setRemark(createVendorAmountDto.getRemark());
		vendorPaymentRegister.setPaymentDate(createVendorAmountDto.getPaymentDate());
		vendorPaymentRegister.setEstimateNo(createVendorAmountDto.getEstimateNo());
		vendorPaymentRegister.setRegisterBy(createVendorAmountDto.getRegisterBy());
		

		vendorPaymentRegisterRepo.save(vendorPaymentRegister);
		
		return vendorPaymentRegister;
	
	}

	@Override
	public List<Map<String, Object>> getAllVendorPaymentRegister(int page, int size) {
		Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
		 List<VendorPaymentRegister> vendorPaymentRegister = vendorPaymentRegisterRepo.findAll(pageable).getContent();
		 List<Map<String, Object>>result=new ArrayList<>();
		 for(VendorPaymentRegister v:vendorPaymentRegister) {
			Map<String,Object> map=new HashMap<>();
			map.put("id", v.getId());
		    map.put("leadId", v.getLeadId());
		    map.put("estimateId", v.getEstimateId());
		    map.put("billingQuantity", v.getBillingQuantity());   // partial, full, milestone
		    map.put("paymentType", v.getPaymentType());           // e.g. Sales

		    // Contact info (if accessible)
		    map.put("name", v.getName());
		    map.put("emails", v.getEmails());
		    map.put("contactNo", v.getContactNo());
		    map.put("whatsappNo", v.getWhatsappNo());
		    map.put("registerBy", v.getRegisterBy());

		    // Payment details
		    map.put("transactionId", v.getTransactionId());
		    map.put("serviceName", v.getServiceName());
		    map.put("govermentFees", v.getGovermentfees());
		    map.put("govermentGst", v.getGovermentGst());
		    map.put("govermentGstPercent", v.getGovermentGstPercent());
		    map.put("professionalFees", v.getProfessionalFees());
		    map.put("professionalGst", v.getProfesionalGst());
		    map.put("professionalGstPercent", v.getProfessionalGstPercent());
		    map.put("professionalGstAmount", v.getProfessionalGstAmount());
		    map.put("tdsPresent", v.isTdsPresent());
		    map.put("tdsAmount", v.getTdsAmount());
		    map.put("tdsPercent", v.getTdsPercent());
		    map.put("serviceCharge", v.getServiceCharge());
		    map.put("serviceGst", v.getServiceGst());
		    map.put("serviceGstPercent", v.getServiceGstPercent());
		    map.put("otherFees", v.getOtherFees());
		    map.put("otherGst", v.getOtherGst());
		    map.put("otherGstPercent", v.getOtherGstPercent());
		    map.put("totalAmount", v.getTotalAmount());
		    map.put("remark", v.getRemark());
		    map.put("paymentDate", v.getPaymentDate());
		    map.put("estimateNo", v.getEstimateNo());
		    map.put("status", v.getStatus());

		    map.put("companyName", v.getCompanyName());
		    map.put("companyId", v.getCompanyId());
		    map.put("updateDate", v.getUpdateDate());
		    map.put("approvedById", v.getApprovedById());
		    map.put("approveDate", v.getApproveDate());

		    // Register Type: payment or purchase order
		    map.put("registerType", v.getRegisterType());
		    map.put("purchaseNumber", v.getPurchaseNumber());
		    map.put("purchaseDate", v.getPurchaseDate());
		    map.put("purchaseAttach", v.getPurchaseAttach());
		    map.put("paymentTerm", v.getPaymentTerm());
		    map.put("comment", v.getComment());

		    // Created by user info
		    if (v.getCreatedByUser() != null) {
		        Map<String, Object> createdByUserMap = new HashMap<>();
		        createdByUserMap.put("id", v.getCreatedByUser().getId());
		        createdByUserMap.put("name", v.getCreatedByUser().getFullName());
		        // add other user fields as needed
		        map.put("createdByUser", createdByUserMap);
		    }

		    // fileData list (if you want to add files as list of maps)
		    if (v.getFileData() != null && !v.getFileData().isEmpty()) {
		        List<Map<String, Object>> filesList = new ArrayList<>();
		        for (FileData file : v.getFileData()) {
		            Map<String, Object> fileMap = new HashMap<>();
		            fileMap.put("id", file.getId());
		            fileMap.put("fileName", file.getName());
		            fileMap.put("fileUrl", file.getFilePath());
		            // add other file fields as needed
		            filesList.add(fileMap);
		        }
		        map.put("fileData", filesList);
		    }
		    
		    result.add(map);


		 }
		return result;
	}

	@Override
	public int getAllVendorPaymentRegisterCount() {
		int count=vendorPaymentRegisterRepo.findAll().size();
		return count;
	}

}
