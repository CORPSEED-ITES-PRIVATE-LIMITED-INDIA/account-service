package com.account.dashboard.serviceImpl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import com.account.dashboard.domain.FileData;
import com.account.dashboard.domain.ProductEstimate;
import com.account.dashboard.domain.User;
import com.account.dashboard.domain.VendorPaymentRegister;
import com.account.dashboard.dto.CreateVendorAmountDto;
import com.account.dashboard.dto.CreateVendorSubDto;
import com.account.dashboard.repository.FileDataRepository;
import com.account.dashboard.repository.PaymentRegisterRepository;
import com.account.dashboard.repository.ProductEstimateRepository;
import com.account.dashboard.repository.UserRepository;
import com.account.dashboard.repository.VendorPaymentRegisterRepo;
import com.account.dashboard.service.VendorPaymentRegisterServcie;

@Service
public class VendorPaymentRegisterServiceImpl implements VendorPaymentRegisterServcie {

	
	@Autowired
	VendorPaymentRegisterRepo vendorPaymentRegisterRepo;
	
	@Autowired
	UserRepository userRepository;
	
	@Autowired
	FileDataRepository fileDataRepository;
	
	@Autowired
	ProductEstimateRepository productEstimateRepository;
	
	

	@Override
	public VendorPaymentRegister createVendorPaymentRegister(CreateVendorAmountDto createVendorAmountDto) {
		VendorPaymentRegister vendorPaymentRegister = new VendorPaymentRegister();

		vendorPaymentRegister.setStatus("initiated");
		vendorPaymentRegister.setEstimateId(createVendorAmountDto.getEstimateId());
		vendorPaymentRegister.setProductCategoryId(createVendorAmountDto.getProductCategoryId());
		vendorPaymentRegister.setBusinessArrangmentId(createVendorAmountDto.getBusinessArrangmentId());

		if(createVendorAmountDto.getCreatedById()!=null) {
			User user = userRepository.findById(createVendorAmountDto.getCreatedById()).get();
			vendorPaymentRegister.setCreatedBy(user);
			vendorPaymentRegister.setCreateDate(new Date());
		}
		vendorPaymentRegister.setServiceName(createVendorAmountDto.getServiceName());
		List<ProductEstimate>estimateList=new ArrayList<>();
		if(createVendorAmountDto.getCreateVendorSubDto()!=null &&createVendorAmountDto.getCreateVendorSubDto().size()>0) {
			for(CreateVendorSubDto v:createVendorAmountDto.getCreateVendorSubDto()) {
				ProductEstimate productEstimate=new ProductEstimate();
				productEstimate.setName(v.getName());
				System.out.println("test......"+v.getName());
				productEstimate.setQuantity(v.getQuantity());
				productEstimate.setServiceFees(v.getServiceFees());
				double gstAmount = ((v.getServiceFees()/100)*v.getServiceGstPercent());
				productEstimate.setProductSubCategoryId(v.getProductSubCategoryId());
				productEstimate.setServiceGstAmount(gstAmount);
				productEstimate.setServiceGstPercent(v.getServiceGstPercent());
				productEstimate.setTotalPrice(v.getTotalPrice());
				productEstimateRepository.save(productEstimate);
				estimateList.add(productEstimate);
			}
			

		}
		vendorPaymentRegister.setProductEstimate(estimateList);     
		vendorPaymentRegister.setLeadId(createVendorAmountDto.getLeadId());

		vendorPaymentRegister.setRemarkByVendor(createVendorAmountDto.getRemarkByVendor());
		vendorPaymentRegister.setCreateDate(new Date());
		
		vendorPaymentRegister.getVendorCompanyName();
		vendorPaymentRegister.setAddress(createVendorAmountDto.getAddress());
		vendorPaymentRegister.setCity(createVendorAmountDto.getCity());
		vendorPaymentRegister.setState(createVendorAmountDto.getState());
		vendorPaymentRegister.setCountry(createVendorAmountDto.getCountry());
		vendorPaymentRegister.setPinCode(createVendorAmountDto.getPinCode());
		vendorPaymentRegister.setStatus(createVendorAmountDto.getStatus());
		vendorPaymentRegister.setGstType(createVendorAmountDto.getGstType());
		vendorPaymentRegister.setGstNo(createVendorAmountDto.getGstNo());
		vendorPaymentRegister.setRemarkByVendor(createVendorAmountDto.getRemarkByVendor());
		if(createVendorAmountDto.getFileData()!=null) {
			List<FileData>fileData=fileDataRepository.findAllByIdIn(createVendorAmountDto.getFileData());
			vendorPaymentRegister.setFileData(fileData);
		}
        
		vendorPaymentRegisterRepo.save(vendorPaymentRegister);
		
		return vendorPaymentRegister;
	
	}

	@Override
	public List<Map<String, Object>> getAllVendorPaymentRegister(int page, int size) {
		Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
		 List<VendorPaymentRegister> vendorPaymentRegister = vendorPaymentRegisterRepo.findAll(pageable).getContent();
		 System.out.println("Vendor Size  . . "+vendorPaymentRegister.size());
		 List<Map<String, Object>>result=new ArrayList<>();
		 for(VendorPaymentRegister v:vendorPaymentRegister) {
			Map<String,Object> map=new HashMap<>();
			map.put("id", v.getId());
		    map.put("leadId", v.getLeadId());
		    map.put("estimateId", v.getEstimateId());
		    map.put("paymentType", v.getPaymentType());  

		    // Contact info (if accessible)
		    map.put("name", v.getName());
		    map.put("emails", v.getEmails());
		    map.put("contactNo", v.getContactNo());
		    map.put("whatsappNo", v.getWhatsappNo());
		    map.put("businessArrangmentId", v.getBusinessArrangmentId());
		    map.put("productCategoryId", v.getProductCategoryId());
		    // Payment details
		    
		    map.put("serviceName", v.getServiceName());
//		    List<ProductEstimate> productEstimate = v.getProductEstimate();
//		    List<Map<String,Object>>arr=new ArrayList<>();
//		    for(ProductEstimate pe:productEstimate) {
//		    	Map<String,Object>m=new HashMap<>();
//		        m.put("productSubCategoryId", pe.getProductSubCategoryId());

//			    m.put("service", pe.getName());
//			    m.put("type", pe.getType());
//			    m.put("serviceFees", pe.getServiceFees());
//			    m.put("serviceGstAmount", pe.getServiceGstAmount());
//			    m.put("serviceGstPercent", pe.getServiceGstPercent());
//			    m.put("quantity", pe.getQuantity());
//			    m.put("totalPrice", pe.getTotalPrice());
//			    arr.add(map);
//		    }
//		    map.put("productEstimate", arr);

		    map.put("remark", v.getRemark());
		    map.put("paymentDate", v.getPaymentDate());
		    map.put("estimateNo", v.getEstimateNo());
		    map.put("status", v.getStatus());
		    map.put("updateDate", v.getUpdateDate());
		    map.put("approvedById", v.getApprovedBy());
		    map.put("approveDate", v.getApproveDate());

		    // Register Type: payment or purchase order
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
//		    if (v.getFileData() != null && !v.getFileData().isEmpty()) {
//		        List<Map<String, Object>> filesList = new ArrayList<>();
//		        for (FileData file : v.getFileData()) {
//		            Map<String, Object> fileMap = new HashMap<>();
//		            fileMap.put("id", file.getId());
//		            fileMap.put("fileName", file.getName());
//		            fileMap.put("fileUrl", file.getFilePath());
//		            // add other file fields as needed
//		            filesList.add(fileMap);
//		        }
//		        map.put("fileData", filesList);
//		    }
		    
		    result.add(map);


		 }
		return result;
	}

	@Override
	public int getAllVendorPaymentRegisterCount() {
		int count=vendorPaymentRegisterRepo.findAll().size();
		return count;
	}

	@Override
	public List<Map<String, Object>> getAllVendorPaymentRegisterForAccount(int page,int size,String status) {
		List<VendorPaymentRegister> vendorPaymentRegister = new ArrayList<>();
		Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
		if("all".equals(status)) {
			 vendorPaymentRegister = vendorPaymentRegisterRepo.findAll(pageable).getContent();

		}else {
			vendorPaymentRegister = vendorPaymentRegisterRepo.findAllByStatus(status,pageable).getContent();
		}
		 List<Map<String, Object>>result=new ArrayList<>();
		 for(VendorPaymentRegister v:vendorPaymentRegister) {
			Map<String,Object> map=new HashMap<>();
			map.put("id", v.getId());
		    map.put("leadId", v.getLeadId());
		    map.put("estimateId", v.getEstimateId());
		    map.put("paymentType", v.getPaymentType());  

		    // Contact info (if accessible)
		    map.put("name", v.getName());
		    map.put("emails", v.getEmails());
		    map.put("contactNo", v.getContactNo());
		    map.put("whatsappNo", v.getWhatsappNo());
		    map.put("businessArrangmentId", v.getBusinessArrangmentId());
		    map.put("productCategoryId", v.getProductCategoryId());

		    // Payment details
		    
		    map.put("serviceName", v.getServiceName());
		    List<ProductEstimate> productEstimate = v.getProductEstimate();
		    List<Map<String,Object>>arr=new ArrayList<>();
		    for(ProductEstimate pe:productEstimate) {
		    	Map<String,Object>m=new HashMap<>();
			    m.put("productSubCategoryId", pe.getProductSubCategoryId());

			    m.put("service", pe.getName());
			    m.put("type", pe.getType());
			    m.put("serviceFees", pe.getServiceFees());
			    m.put("serviceGstAmount", pe.getServiceGstAmount());
			    m.put("serviceGstPercent", pe.getServiceGstPercent());
			    m.put("quantity", pe.getQuantity());
			    m.put("totalPrice", pe.getTotalPrice());
			    arr.add(map);
		    }
		    map.put("productEstimate", arr);

		    map.put("remark", v.getRemark());
		    map.put("paymentDate", v.getPaymentDate());
		    map.put("estimateNo", v.getEstimateNo());
		    map.put("status", v.getStatus());
		    map.put("updateDate", v.getUpdateDate());
		    map.put("approvedById", v.getApprovedBy());
		    map.put("approveDate", v.getApproveDate());

		    // Register Type: payment or purchase order
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
	public Boolean approveVendorPayment(Long currentUserId, String status, Long id) {
		Boolean flag=false;
		VendorPaymentRegister vendorPayment = vendorPaymentRegisterRepo.findById(id).get();
		vendorPayment.setStatus(status);
		User user = userRepository.findById(currentUserId).get();
		vendorPayment.setApprovedBy(user);
		vendorPaymentRegisterRepo.save(vendorPayment);
		flag=true;
		return flag;
	}

	@Override
	public int getAllVendorPaymentRegisterCountForAccount(String status) {
		List<VendorPaymentRegister> vendorPaymentRegister = new ArrayList<>();

		if("all".equals(status)) {
			 vendorPaymentRegister = vendorPaymentRegisterRepo.findAll();

		}else {
			vendorPaymentRegister = vendorPaymentRegisterRepo.findAllByStatus(status);
		}
		return vendorPaymentRegister.size();
	}

}
