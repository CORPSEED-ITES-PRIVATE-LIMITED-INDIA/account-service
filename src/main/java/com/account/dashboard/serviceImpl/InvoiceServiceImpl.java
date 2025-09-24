package com.account.dashboard.serviceImpl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.InvoiceData;
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
	        res.put("primaryContactId", invoice.getPrimaryContactId());
	        res.put("primaryContactTitle", invoice.getPrimaryContactTitle());
	        res.put("primaryContactName", invoice.getPrimaryContactName());
	        res.put("primaryContactemails", invoice.getPrimaryContactemails());
	        res.put("primaryContactNo", invoice.getPrimaryContactNo());
	        res.put("primaryWhatsappNo", invoice.getPrimaryWhatsappNo());
	        res.put("primaryContactDesignation", invoice.getPrimaryContactDesignation());

	        // Secondary contact information
	        res.put("secondaryContactId", invoice.getSecondaryContactId());
	        res.put("secondaryContactTitle", invoice.getSecondaryContactTitle());
	        res.put("secondaryContactName", invoice.getSecondaryContactName());
	        res.put("secondaryContactemails", invoice.getSecondaryContactemails());
	        res.put("secondaryContactNo", invoice.getSecondaryContactNo());
	        res.put("secondaryWhatsappNo", invoice.getSecondaryWhatsappNo());
	        res.put("secondaryContactDesignation", invoice.getSecondaryContactDesignation());

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

	        // Fees and Amounts
	        res.put("govermentfees", invoice.getGovermentfees());
	        res.put("govermentCode", invoice.getGovermentCode());
	        res.put("govermentGst", invoice.getGovermentGst());
	        res.put("professionalFees", invoice.getProfessionalFees());
	        res.put("professionalCode", invoice.getProfessionalCode());
	        res.put("profesionalGst", invoice.getProfesionalGst());
	        res.put("serviceCharge", invoice.getServiceCharge());
	        res.put("serviceCode", invoice.getServiceCode());
	        res.put("serviceGst", invoice.getServiceGst());
	        res.put("otherFees", invoice.getOtherFees());
	        res.put("otherCode", invoice.getOtherCode());
	        res.put("otherGst", invoice.getOtherGst());
	        res.put("totalAmount", invoice.getTotalAmount());
	        res.put("paidAmount", invoice.getPaidAmount());

	        // Unbilled information
//	        res.put("unbilled", invoice.getUnbilled());  // Assuming U
		return res;
	}

}
