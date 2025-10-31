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
import com.account.dashboard.domain.TdsDetail;
import com.account.dashboard.domain.User;
import com.account.dashboard.domain.VendorPaymentHistory;
import com.account.dashboard.domain.VendorPaymentRegister;
import com.account.dashboard.dto.CreateVendorAmountDto;
import com.account.dashboard.dto.CreateVendorSubDto;
import com.account.dashboard.dto.VendorPaymentAddDto;
import com.account.dashboard.repository.FileDataRepository;
import com.account.dashboard.repository.PaymentRegisterRepository;
import com.account.dashboard.repository.ProductEstimateRepository;
import com.account.dashboard.repository.TdsDetailRepository;
import com.account.dashboard.repository.UserRepository;
import com.account.dashboard.repository.VendorPaymentHistoryRepository;
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
	@Autowired
	VendorPaymentHistoryRepository vendorPaymentHistoryRepository;
	
	@Autowired
	TdsDetailRepository tdsDetailRepository;
	
	

	@Override
	public VendorPaymentRegister createVendorPaymentRegister(CreateVendorAmountDto createVendorAmountDto) {
		VendorPaymentRegister vendorPaymentRegister = new VendorPaymentRegister();

		vendorPaymentRegister.setStatus("initiated");
		vendorPaymentRegister.setEstimateId(createVendorAmountDto.getEstimateId());
		vendorPaymentRegister.setProductCategoryId(createVendorAmountDto.getProductCategoryId());
		vendorPaymentRegister.setBusinessArrangmentId(createVendorAmountDto.getBusinessArrangmentId());
		vendorPaymentRegister.setPaymentDate(new Date());
		if(createVendorAmountDto.getCreatedById()!=null) {
//			User user = userRepository.findById(createVendorAmountDto.getCreatedById()).get();
//			vendorPaymentRegister.setCreatedBy(user);
			vendorPaymentRegister.setCreateDate(new Date());
		}
		vendorPaymentRegister.setServiceName(createVendorAmountDto.getServiceName());
		List<ProductEstimate>estimateList=new ArrayList<>();
		double  totalAmount=0;
		if(createVendorAmountDto.getCreateVendorSubDto()!=null &&createVendorAmountDto.getCreateVendorSubDto().size()>0) {
			for(CreateVendorSubDto v:createVendorAmountDto.getCreateVendorSubDto()) {
				ProductEstimate productEstimate=new ProductEstimate();
				productEstimate.setName(v.getName());

				
				productEstimate.setServiceFees(v.getServiceFees());
				double gstAmount = ((v.getServiceFees()/100)*v.getServiceGstPercent());
				productEstimate.setProductSubCategoryId(v.getProductSubCategoryId());
				productEstimate.setServiceGstAmount(gstAmount);
				productEstimate.setServiceGstPercent(v.getServiceGstPercent());
				
				totalAmount=totalAmount+v.getTotalPrice();
				
				productEstimate.setQuantity(v.getQuantity());
				productEstimate.setTotalPrice(v.getTotalPrice());
				productEstimate.setGstAmount(v.getGstAmount());
				productEstimate.setGstPercent(v.getGstPercent());
				productEstimate.setActualPrice(v.getActualPrice());
				productEstimateRepository.save(productEstimate);
				estimateList.add(productEstimate);
			}
			

		}
		
		vendorPaymentRegister.setTotalDueAmount(totalAmount);
		vendorPaymentRegister.setTotalPaidAmount(0);
		vendorPaymentRegister.setTotalAmount(totalAmount);
		
		vendorPaymentRegister.setProductEstimate(estimateList);     
		vendorPaymentRegister.setLeadId(createVendorAmountDto.getLeadId());
		vendorPaymentRegister.setRemarkByVendor(createVendorAmountDto.getRemarkByVendor());
		vendorPaymentRegister.setCreateDate(new Date());
		
		vendorPaymentRegister.setName(createVendorAmountDto.getName());
		vendorPaymentRegister.setVendorCompanyName(createVendorAmountDto.getVendorCompanyName());
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
			List<FileData>fileList=new ArrayList<>();
			for(String file:createVendorAmountDto.getFileData()){
				FileData fileData = new FileData();
				fileData.setFilePath(file);
				fileDataRepository.save(fileData);
				fileList.add(fileData);
			}
			vendorPaymentRegister.setFileData(fileList);
		}
		vendorPaymentRegister.setStatus("initiated");

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
		    List<ProductEstimate> productEstimate = v.getProductEstimate();
		    List<Map<String,Object>>arr=new ArrayList<>();
		    for(ProductEstimate pe:productEstimate) {
		    	Map<String,Object>m=new HashMap<>();
		        m.put("productSubCategoryId", pe.getProductSubCategoryId());
		        
			    map.put("companyName", v.getVendorCompanyName());
			    map.put("address", v.getAddress());
			    map.put("state", v.getState());
			    map.put("city", v.getCity());
			    map.put("country", v.getCountry());
			    map.put("pinCode", v.getPinCode());

			    m.put("service", pe.getName());
			    m.put("type", pe.getType());
			    m.put("serviceFees", pe.getServiceFees());
			    m.put("serviceGstAmount", pe.getServiceGstAmount());
			    m.put("serviceGstPercent", pe.getServiceGstPercent());
			    m.put("quantity", pe.getQuantity());

			    m.put("gstPercent", pe.getGstPercent());  
			    m.put("actualPrice", pe.getActualPrice());
			    m.put("gstAmount", pe.getGstAmount());

			    m.put("totalPrice", pe.getTotalPrice());
			    arr.add(m);
		    }
		    map.put("productEstimate", arr);
		    
		    map.put("totalDueAmount", v.getTotalDueAmount());
		    map.put("totalAmount", v.getTotalAmount());
		    map.put("totalPaidAmount", v.getTotalPaidAmount());


		    map.put("remark", v.getRemark());
		    map.put("paymentDate", v.getPaymentDate());
		    map.put("estimateNo", v.getEstimateNo()!=null?v.getEstimateNo():"EST000"+v.getEstimateId());
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
			    
			    m.put("gstPercent", pe.getGstPercent());  
			    m.put("actualPrice", pe.getActualPrice());
			    m.put("gstAmount", pe.getGstAmount());
			    arr.add(m);
		    }
		    map.put("productEstimate", arr);
		    map.put("totalDueAmount", v.getTotalDueAmount());
		    map.put("totalAmount", v.getTotalAmount());
		    map.put("totalPaidAmount", v.getTotalPaidAmount());
		    
		    map.put("companyName", v.getVendorCompanyName());
		    map.put("address", v.getAddress());
		    map.put("state", v.getState());
		    map.put("city", v.getCity());
		    map.put("country", v.getCountry());
		    map.put("pinCode", v.getPinCode());

		    map.put("remark", v.getRemark());
		    map.put("paymentDate", v.getPaymentDate());
		    map.put("estimateNo", v.getEstimateNo()!=null?v.getEstimateNo():"EST000"+v.getEstimateId());
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
		vendorPayment.setApproveDate(new Date());
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

	@Override
	public Boolean addAmountByAccountTeam(VendorPaymentAddDto vendorPaymentAddDto) {
		Boolean flag=false;
		VendorPaymentRegister vendor = vendorPaymentRegisterRepo.findById(vendorPaymentAddDto.getVendorPaymentId()).get();
		double totalAmount = vendorPaymentAddDto.getTotalAmount();
		double dueAmount = vendor.getTotalDueAmount();
		dueAmount=dueAmount-totalAmount;
		vendor.setTotalDueAmount(dueAmount);
		
		double paidAmount = vendor.getTotalPaidAmount();
		paidAmount=paidAmount+totalAmount;
		vendor.setTotalPaidAmount(paidAmount);
		vendorPaymentRegisterHistory( vendorPaymentAddDto);
		vendorPaymentRegisterRepo.save(vendor);
		if(vendorPaymentAddDto.getTdsPercent()!=0 && vendorPaymentAddDto.getTdsAmount()!=0) {
			TdsDetail tdsDetail=new TdsDetail();
			tdsDetail.setTdsAmount(vendorPaymentAddDto.getTdsAmount());
			tdsDetail.setOrganization(vendor.getVendorCompanyName());
			tdsDetail.setTdsPrecent(vendorPaymentAddDto.getTdsPercent());			
			tdsDetail.setTdsType("Payable");
			tdsDetail.setTotalPaymentAmount(vendorPaymentAddDto.getTotalAmount());
			tdsDetailRepository.save(tdsDetail);
			flag=true;
		}
		return flag;
	}
	public Boolean vendorPaymentRegisterHistory(VendorPaymentAddDto vendorPaymentAddDto) {
		Boolean flag=false;
		VendorPaymentHistory vendorPaymentHistory=new VendorPaymentHistory();
		
		vendorPaymentHistory.setLeadId(vendorPaymentAddDto.getLeadId());
		vendorPaymentHistory.setServiceName(vendorPaymentAddDto.getServiceName());
		vendorPaymentHistory.setTotalAmount(vendorPaymentAddDto.getTotalAmount());
		vendorPaymentHistory.setVendorPaymentRegisterId(vendorPaymentAddDto.getVendorPaymentId());
		vendorPaymentHistory.setActualAmount(vendorPaymentAddDto.getActualAmount());
		Optional<User> createdBy = userRepository.findById(vendorPaymentAddDto.getCreateBy());
		vendorPaymentHistory.setCreateBy(createdBy!=null?createdBy.get():null);
		vendorPaymentHistory.setCreateDate(new Date());
		vendorPaymentHistory.setDocument(vendorPaymentAddDto.getDocument());
		vendorPaymentHistory.setEstimateId(vendorPaymentAddDto.getEstimateId());
		vendorPaymentHistory.setGst(vendorPaymentAddDto.getGst());
		vendorPaymentHistory.setGstAmount(vendorPaymentAddDto.getGstAmount());
		vendorPaymentHistory.setTdsAmount(vendorPaymentAddDto.getTdsAmount());
		vendorPaymentHistory.setTdsPercent(vendorPaymentAddDto.getTdsPercent());

		vendorPaymentHistoryRepository.save(vendorPaymentHistory);
		flag=true;
		return flag;
	}

	@Override
	public List<Map<String, Object>> getAllVendorPaymentRegisterHistoryById(Long id) {
		List<VendorPaymentHistory> vendorPaymentHistory = vendorPaymentHistoryRepository.findAllByVendorPaymentRegisterId(id);
		List<Map<String, Object>>result=new ArrayList<>();
		for (VendorPaymentHistory history : vendorPaymentHistory) {
		    Map<String, Object> map = new HashMap<>();

		    map.put("id", history.getId());
		    map.put("serviceName", history.getServiceName());
		    map.put("vendorPaymentRegisterId", history.getVendorPaymentRegisterId());
		    map.put("leadId", history.getLeadId());
		    map.put("estimateId", history.getEstimateId());
		    map.put("actualAmount", history.getActualAmount());
		    map.put("gst", history.getGst());
		    map.put("gstAmount", history.getGstAmount());
		    map.put("tdsAmount", history.getTdsAmount());
		    map.put("tdsPercent", history.getTdsPercent());
		    map.put("totalAmount", history.getTotalAmount());
		    map.put("createDate", history.getCreateDate());
		    map.put("createById", history.getCreateBy()!=null?history.getCreateBy().getId():null); // possibly extract ID or username if needed
		    map.put("createByName", history.getCreateBy()!=null?history.getCreateBy().getFullName():null); // possibly extract ID or username if needed
		    map.put("createByEmail", history.getCreateBy()!=null?history.getCreateBy().getEmail():null); // possibly extract ID or username if needed

		    map.put("document", history.getDocument());

		    // Add map to a list if needed
		    result.add(map); // Assuming resultList is defined somewhere
		}
		return result;
	}
	
	public List<Map<String, Object>> getAllVendorPaymentRegisterForAdmin(int page,int size,String status) {
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
			    
			    m.put("gstPercent", pe.getGstPercent());  
			    m.put("actualPrice", pe.getActualPrice());
			    m.put("gstAmount", pe.getGstAmount());
			    arr.add(m);
		    }
		    
		    map.put("companyName", v.getVendorCompanyName());
		    map.put("address", v.getAddress());
		    map.put("state", v.getState());
		    map.put("city", v.getCity());
		    map.put("country", v.getCountry());
		    map.put("pinCode", v.getPinCode());
		    map.put("productEstimate", arr);
		    map.put("totalDueAmount", v.getTotalDueAmount());
		    map.put("totalAmount", v.getTotalAmount());
		    map.put("totalPaidAmount", v.getTotalPaidAmount());
		    
		    map.put("remark", v.getRemark());
		    map.put("paymentDate", v.getPaymentDate());
		    map.put("estimateNo", v.getEstimateNo()!=null?v.getEstimateNo():"EST000"+v.getEstimateId());
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
	public int getAllVendorPaymentRegisterCountForAdmin(String status) {
		List<VendorPaymentRegister> vendorPaymentRegister = new ArrayList<>();

		if("all".equals(status)) {
			 vendorPaymentRegister = vendorPaymentRegisterRepo.findAll();

		}else {
			vendorPaymentRegister = vendorPaymentRegisterRepo.findAllByStatus(status);
		}
		return vendorPaymentRegister.size();
	}

	@Override
	public VendorPaymentRegister createVendorPaymentRegisterManual(CreateVendorAmountDto createVendorAmountDto) {
		// TODO Auto-generated method stub
		return null;
	}
}
