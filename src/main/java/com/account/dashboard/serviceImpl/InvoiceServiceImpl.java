package com.account.dashboard.serviceImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import com.account.dashboard.domain.InvoiceData;
import com.account.dashboard.domain.User;
import com.account.dashboard.repository.InvoiceDataRepository;
import com.account.dashboard.service.InvoiceService;

@Service
public class InvoiceServiceImpl implements InvoiceService{

	@Autowired
	InvoiceDataRepository invoiceDataRepository;
	@Override
	public Map<String, Object> getInvoiceById(Long id) {
		InvoiceData invoice = invoiceDataRepository.findById(id).get();
		  Map<String, Object> res = new HashMap<>();
	        
	        // Add fields to the map
	        res.put("id", invoice.getId());  // @Id and @GeneratedValue are typically used for entity persistence
	        
	        // Product information
	        res.put("productName", invoice.getProductName());
	        res.put("estimateId", invoice.getEstimateId());

	        // Primary contact information

	        res.put("primaryContactemails", invoice.getPrimaryContactemails());

	        // Secondary contact information
	        res.put("secondaryContactemails", invoice.getSecondaryContactemails());
			res.put("gstNo", invoice.getGstNo());
			Double prof = Double.valueOf(invoice.getProfessionalFees());
			Double gst =  Double.valueOf(invoice.getProfesionalGst());
			if(prof!=null&& prof!=0) {
				double gstAmount = ((gst*prof)/100);			
				res.put("gstAmount", gstAmount);
			}else {
				res.put("gstAmount", 0);
			}
			res.put("profGst", invoice.getProfesionalGst());

	        // Dates and Estimate Number
	        res.put("estimateDate", invoice.getEstimateDate());
	        res.put("createDate", invoice.getCreateDate());
	        res.put("estimateNo", invoice.getEstimateNo());

	        // Company details
	        res.put("isPresent", invoice.getIsPresent());
	        res.put("companyName", invoice.getCompanyName());
	        res.put("companyId", invoice.getCompanyId());
	        res.put("unitName", invoice.getUnitName());
	        res.put("unitId", invoice.getUnitId());
	        res.put("panNo", invoice.getPanNo());
	        res.put("gstNo", invoice.getGstNo());
	        res.put("gstType", invoice.getGstType());
	        res.put("gstDocuments", invoice.getGstDocuments());
	        res.put("companyAge", invoice.getCompanyAge());

	        // Primary Address
	        res.put("isPrimaryAddress", invoice.getIsPrimaryAddress());
	        res.put("primaryTitle", invoice.getPrimaryTitle());
	        res.put("Address", invoice.getAddress());
	        res.put("City", invoice.getCity());
	        res.put("State", invoice.getState());
	        res.put("primaryPinCode", invoice.getPrimaryPinCode());
	        res.put("Country", invoice.getCountry());

	        // Secondary Address
	        res.put("isSecondaryAddress", invoice.getIsSecondaryAddress());
	        res.put("secondaryTitle", invoice.getSecondaryTitle());
	        res.put("secondaryAddress", invoice.getSecondaryAddress());
	        res.put("secondaryCity", invoice.getSecondaryCity());
	        res.put("secondaryState", invoice.getSecondaryState());
	        res.put("secondaryPinCode", invoice.getSecondaryPinCode());
	        res.put("secondaryCountry", invoice.getSecondaryCountry());

	        // HSN/SAC details
	        res.put("hsnSacPrsent", invoice.isHsnSacPrsent());
	        res.put("hsnSacDetails", invoice.getHsnSacDetails());
	        res.put("HsnSac", invoice.getHsnSac());
	        res.put("hsnDescription", invoice.getHsnDescription());

	        // Assignee and Lead information
	        res.put("assignee", invoice.getAssignee());  // Assuming assignee is a user object
	        res.put("leadId", invoice.getLeadId());
	        res.put("status", invoice.getStatus());

	        // Product details
	        res.put("consultingSale", invoice.getConsultingSale());
	        res.put("productType", invoice.getProductType());
	        res.put("orderNumber", invoice.getOrderNumber());
	        res.put("purchaseDate", invoice.getPurchaseDate());
	        res.put("cc", invoice.getCc());  // Assuming cc is a List<String>
	        res.put("invoiceNote", invoice.getInvoiceNote());
	        res.put("remarksForOption", invoice.getRemarksForOption());
	        res.put("documents", invoice.getDocuments());
	        res.put("termOfDelivery", invoice.getTermOfDelivery());

	        // Fees and Amounts
//	        res.put("govermentfees", invoice.getGovermentfees());
//	        res.put("govermentCode", invoice.getGovermentCode());
//	        res.put("govermentGst", invoice.getGovermentGst());
	        res.put("professionalFees", invoice.getProfessionalFees());
	        res.put("professionalCode", invoice.getProfessionalCode());
	        res.put("profesionalGst", invoice.getProfesionalGst());
//	        res.put("serviceCharge", invoice.getServiceCharge());
//	        res.put("serviceCode", invoice.getServiceCode());
//	        res.put("serviceGst", invoice.getServiceGst());
	        res.put("otherFees", invoice.getOtherFees());
	        res.put("otherCode", invoice.getOtherCode());
	        res.put("otherGst", invoice.getOtherGst());
	        res.put("totalAmount", invoice.getTotalAmount());
	        res.put("paidAmount", invoice.getPaidAmount());
	        res.put("invoiceNo", "INV00000"+invoice.getId());
	        String gstNo = invoice.getGstNo();
	        String firstTwo = gstNo.substring(0, 2);
	        res.put("gstCode", firstTwo);

	        // Unbilled information
//	        res.put("unbilled", invoice.getUnbilled());  // Assuming U
		return res;
	}
	@Override
	public List<Map<String, Object>> getAllInvoiceForExport(String startDate,String endDate) {

		// For descending order, use:


		List<InvoiceData>invoice=invoiceDataRepository.findAll();
	    List<Map<String,Object>>result=new ArrayList<>();
		for(InvoiceData invoiceData:invoice){
			Map<String,Object>map=new HashMap<>();
			map.put("id", invoiceData.getId());
			map.put("invoiceNo", invoiceData.getId());
			map.put("service", invoiceData.getProductName());
			map.put("clientid", invoiceData.getPrimaryContactId());
			map.put("clientName", invoiceData.getPrimaryContactName());
			map.put("clientEmail", invoiceData.getPrimaryContactemails());
			map.put("companyName", invoiceData.getCompanyName());
			map.put("date", invoiceData.getCreateDate());
			map.put("txnAmount", invoiceData.getTotalAmount());
			map.put("termOfDelivery", invoiceData.getTermOfDelivery());

			map.put("addedBy", invoiceData.getAssignee());
			result.add(map);
		}
̥
		return result;
	
	}

}
