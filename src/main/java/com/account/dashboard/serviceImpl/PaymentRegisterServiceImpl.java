package com.account.dashboard.serviceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;

import com.account.dashboard.config.GetAllCompanyDto;
import com.account.dashboard.config.LeadFeignClient;
import com.account.dashboard.config.OpenAPIConfig;
import com.account.dashboard.controller.ledger.OrganizationController;
import com.account.dashboard.domain.FileData;
import com.account.dashboard.domain.GstDetails;
import com.account.dashboard.domain.InvoiceData;
import com.account.dashboard.domain.Organization;
import com.account.dashboard.domain.PaymentRegister;
import com.account.dashboard.domain.Unbilled;
import com.account.dashboard.domain.User;
import com.account.dashboard.domain.account.Ledger;
import com.account.dashboard.domain.account.LedgerType;
import com.account.dashboard.domain.account.Voucher;
import com.account.dashboard.domain.account.VoucherType;
import com.account.dashboard.dto.AddGstDto;
import com.account.dashboard.dto.CreateAccountData;
import com.account.dashboard.dto.CreateAmountDto;
import com.account.dashboard.dto.CreatePurchaseOrderDto;
import com.account.dashboard.dto.CreateTdsDto;
import com.account.dashboard.dto.PaymentApproveDto;
import com.account.dashboard.dto.UnbilledDTO;
import com.account.dashboard.dto.UpdatePaymentDto;
import com.account.dashboard.repository.*;
import com.account.dashboard.service.GstDataCrmService;
import com.account.dashboard.service.PaymentRegisterService;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.transaction.Transactional;


@Service
public class PaymentRegisterServiceImpl implements  PaymentRegisterService{

	private final OrganizationController organizationController;

	@Autowired
	private final GstDetailsRepository gstDetailsRepository;

	@Autowired
	private final OpenAPIConfig openAPIConfig;
	
	@Autowired
	GstDataCrmService gstDataCrmService;


	@Autowired
	UnbilledServiceImpl unbilledServiceImpl;

	@Autowired
	private UnbilledRepository unbilledRepository;

	@Autowired
	FileDataRepository fileDataRepository;


	@Autowired
	PaymentRegisterRepository paymentRegisterRepository;


	@Autowired
	LedgerTypeRepository ledgerTypeRepository;

	@Autowired
	VoucherTypeRepo voucherTypeRepo;

	@Autowired
	LedgerRepository ledgerRepository;

	@Autowired
	LeadFeignClient LeadFeignClient;

	@Autowired
	OrganizationRepository organizationRepository;


	@Autowired
	InvoiceDataRepository invoiceDataRepository;

	@Autowired
	TdsServiceImpl tdsServiceImpl;

	@Autowired
	LeadFeignClient leadFeignClient;


	@Autowired
	VoucherRepository voucherRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	TdsDetailRepository tdsDetailRepository;


	PaymentRegisterServiceImpl(OpenAPIConfig openAPIConfig, GstDetailsRepository gstDetailsRepository, OrganizationController organizationController) {
		this.openAPIConfig = openAPIConfig;
		this.gstDetailsRepository = gstDetailsRepository;
		this.organizationController = organizationController;
	}


	@Override
	public PaymentRegister createPaymentRegister(CreateAmountDto createAmountDto) throws Exception {

		PaymentRegister paymentRegister= new PaymentRegister();
		if(createAmountDto.getTotalAmount()>0) {

			paymentRegister.setStatus("initiated");
			paymentRegister.setEstimateId(createAmountDto.getEstimateId());
			paymentRegister.setEstimateNo("EST00"+createAmountDto.getEstimateId());
			paymentRegister.setBillingQuantity(createAmountDto.getBillingQuantity());
			paymentRegister.setPaymentType(createAmountDto.getPaymentType());
			paymentRegister.setCreatedById(createAmountDto.getCreatedById());
			paymentRegister.setTransactionId(createAmountDto.getTransactionId());
			paymentRegister.setServiceName(createAmountDto.getServiceName());
			paymentRegister.setGovermentfees(createAmountDto.getGovermentfees());
			paymentRegister.setGovermentGst(createAmountDto.getGovermentGst());
			paymentRegister.setProductType(createAmountDto.getProductType());
			paymentRegister.setGovermentGstPercent(createAmountDto.getGovermentGstPercent());

			//====
			double quantity;
			double actualAmount;
			paymentRegister.setModeOfPayment(createAmountDto.getModeOfPayment());
			paymentRegister.setReferenceDate(createAmountDto.getReferenceDate());
			paymentRegister.setOtherReference(createAmountDto.getOtherReference());
			paymentRegister.setBuyerOrderNo(createAmountDto.getBuyerOrderNo());
			paymentRegister.setQuantity(createAmountDto.getQuantity());
			paymentRegister.setActualAmount(createAmountDto.getActualAmount());

			if(createAmountDto.getDoc()!=null && createAmountDto.getDoc().size()!=0) {
				List<FileData>list=new ArrayList<>();
				for(String s:createAmountDto.getDoc()) {
					FileData fileData=new FileData(); 
					fileData.setFilePath(s);
					fileDataRepository.save(fileData);
					list.add(fileData);
				}

				paymentRegister.setFileData(list);
			}


			if(createAmountDto.isTdsPresent()) {
				double tdsPercent = createAmountDto.getTdsPercent();
				double totalProfessional = createAmountDto.getProfessionalFees();
				double actualProfPercetage = 100;
				double onePercent = totalProfessional/100;
				double tdsAmount = onePercent*tdsPercent;
				double profFees=onePercent*actualProfPercetage;
				paymentRegister.setProfessionalFees(profFees);
				paymentRegister.setTdsPresent(true);
				paymentRegister.setTdsPercent(createAmountDto.getTdsPercent());
				paymentRegister.setTdsAmount(tdsAmount);


			}else {
				paymentRegister.setProfessionalFees(createAmountDto.getProfessionalFees());
			}
			if("Product".equals(paymentRegister.getProductType())) {

				double totalAmount = createAmountDto.getTotalAmount();
				double sumPercent = createAmountDto.getGstPercent()+100;
				double per = totalAmount/sumPercent;
				if(createAmountDto.isTdsPresent()) {
					double tdsAmount = per*createAmountDto.getTdsPercent();
					paymentRegister.setTdsAmount(tdsAmount);
				}
				double amount=per*100;
				double gstAmount = per*createAmountDto.getGstPercent();
				paymentRegister.setAmount(amount);
				paymentRegister.setGstPercent(createAmountDto.getGstPercent());
				paymentRegister.setGstAmount(gstAmount);
			}
			paymentRegister.setCompanyId(createAmountDto.getCompanyId());
			paymentRegister.setLeadId(createAmountDto.getLeadId());
			paymentRegister.setProfesionalGst(createAmountDto.getProfesionalGst());
			double profesionalGst = createAmountDto.getProfesionalGst();
			paymentRegister.setTermOfDelivery(createAmountDto.getTermOfDelivery());


			double gstAmount = ((createAmountDto.getProfessionalFees()/100)*profesionalGst);

			paymentRegister.setProfessionalGstAmount(gstAmount);

			//		paymentRegister.setProfessionalGstAmount(createAmountDto.getProfesionalGstFee());

			paymentRegister.setServiceCharge(createAmountDto.getServiceCharge());		

			paymentRegister.setOtherFees(createAmountDto.getOtherFees());
			//		paymentRegister.setOtherGst(createAmountDto.g̥etOtherGst());
			paymentRegister.setOtherGstPercent(createAmountDto.getOtherGstPercent());
			//		paymentRegister.setUploadReceipt(createAmountDto.getUploadReceipt());
			paymentRegister.setTotalAmount(createAmountDto.getTotalAmount());
			paymentRegister.setRemark(createAmountDto.getRemark());
			paymentRegister.setPaymentDate(createAmountDto.getPaymentDate());
			//		paymentRegister.setEstimateNo(createAmountDto.getEstimateNo());
			//		paymentRegister.setDoc(createAmountDto.getDoc());
			paymentRegister.setCompanyName(createAmountDto.getCompanyName());
			paymentRegister.setRegisterBy(createAmountDto.getRegisterBy());
			if("milestone".equals(createAmountDto.getPaymentType())) {
				paymentRegister.setDocPersent(createAmountDto.getDocPersent());
				paymentRegister.setFilingPersent(createAmountDto.getFilingPersent());
				paymentRegister.setLiasoningPersent(createAmountDto.getLiasoningPersent());
				paymentRegister.setCertificatePersent(100);
			}else if("partial".equals(createAmountDto.getPaymentType())) {
				paymentRegister.setDocPersent(50);
				paymentRegister.setFilingPersent(50);
				paymentRegister.setLiasoningPersent(50);
				paymentRegister.setCertificatePersent(100);
			}else {
				paymentRegister.setDocPersent(100);
				paymentRegister.setFilingPersent(100);
				paymentRegister.setLiasoningPersent(100);
				paymentRegister.setCertificatePersent(100);
			}
			//		paymentRegister.setDocPersent(createAmountDto.getDocPersent());
			//		paymentRegister.setFilingPersent(createAmountDto.getFilingPersent());
			//		paymentRegister.setLiasoningPersent(createAmountDto.getLiasoningPersent());
			//		paymentRegister.setCertificatePersent(createAmountDto.getCertificatePersent());
			paymentRegisterRepository.save(paymentRegister);
			return paymentRegister;

		}else {
			throw new IllegalArgumentException("Total amount cannot be null. Please provide a valid total amount for processing.");
		}

	}

	public Map<String, Object>remainingAmountAndPaidAmountProduct(Long id) {

		Map<String,Object>result=new HashMap<>();
		Map<String, Object> estimate = leadFeignClient.getEstimateById(id);

		List<PaymentRegister> paymentRegister = paymentRegisterRepository.findAllByEstimateId(id);
		double totalFees=0;
		double totalGst=0;
		double totalAmount=0;
		String paymentType="NA";
		for(PaymentRegister pr:paymentRegister) {
			if(pr.getStatus().equals("approved")) {
				totalFees=totalFees+pr.getTotalAmount();
		    }

	     }
	double estTotalAmount = Double.parseDouble(estimate.get("totalPrice").toString());

	double totAmount = Double.parseDouble(estimate.get("totalPrice").toString());

	String estimateDate = estimate.get("estimateDate").toString();
	result.put("estimateAmount", estTotalAmount);
	result.put("paidAmount", totalFees);

	totAmount=totAmount-totalFees;
	result.put("totAmount", totAmount);
	result.put("totalRemainingAmount", totAmount);
	result.put("remainingProffees", totAmount-totalFees);
	result.put("estimateDate", estimateDate);
	result.put("paymentType", paymentType);
	result.put("paidAmount", totalFees);

	return result;

}


public Map<String, Object>remainingAmountAndPaidAmount(Long id) {

	Map<String,Object>result=new HashMap<>();
	Map<String, Object> estimate = leadFeignClient.getEstimateById(id);

	List<PaymentRegister> paymentRegister = paymentRegisterRepository.findAllByEstimateId(id);
	double proFees=0;
	double profGst=0;
	double govFees=0;
	double otherFees=0;
	double serviceCharge=0;
	double totalAmount=0;
	String paymentType="NA";
	for(PaymentRegister pr:paymentRegister) {
		if(pr.getStatus().equals("approved")) {
			proFees=proFees+pr.getProfessionalFees();
			profGst=pr.getProfessionalGstAmount();   
			govFees=pr.getGovermentfees();
			paymentType=pr.getPaymentType();
			totalAmount=totalAmount+pr.getTotalAmount();
			otherFees=pr.getOtherFees();
			serviceCharge=pr.getServiceCharge();
		}
	}
	double estTotalAmount = Double.parseDouble(estimate.get("totalAmount").toString());
	double paidAmount = totalAmount;

	double totAmount = Double.parseDouble(estimate.get("totalAmount").toString());
	double profees = Double.parseDouble(estimate.get("professionalFees").toString());
	double govfees = Double.parseDouble(estimate.get("govermentFees").toString());
	double serviceFees = Double.parseDouble(estimate.get("serviceCharge").toString());
	double otherCharge = Double.parseDouble(estimate.get("otherFees").toString());
	String estimateDate = estimate.get("estimateDate")!=null?estimate.get("estimateDate").toString():null;
	result.put("estimateAmount", estTotalAmount);
	result.put("paidAmount", paidAmount);

	totAmount=totAmount-totalAmount;
	result.put("totAmount", totAmount);
	result.put("totalRemainingAmount", totAmount);
	result.put("remainingProffees", profees-proFees);
	result.put("remainingGovfees", govfees-govfees);
	//		result.put("estimateAmount", totAmount);
	result.put("estimateDate", estimateDate);
	result.put("paymentType", paymentType);
	result.put("serviceCharge", serviceFees-serviceCharge);

	// client Detail
	result.put("contactName", estimate.get("primaryContactName"));
	result.put("contactEmails", estimate.get("contactEmails"));
	result.put("contactNo", estimate.get("contactNo"));

	return result;

}

@Override
public List<Map<String,Object>> getAllPaymentRegister(String status) {
	List<String>statusList=new ArrayList<>();
	//		if(status!=null && (!status.equals("all"))) {
	//			statusList.add(status);
	//		}else {
	//			statusList=Arrays.asList("initiated","approved");
	//		}
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
		map.put("productType", p.getProductType());

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
		Map<String, Object>remAmount= remainingAmountAndPaidAmount(p.getEstimateId());
		Object dueAmount = remAmount.get("totalRemainingAmount");
		Object txnAmount = remAmount.get("estimateAmount");
		Object estimateCreateDate = remAmount.get("estimateDate");

		map.put("dueAmount", dueAmount);
		map.put("txnAmount", p.getTotalAmount());
		map.put("orderAmount", txnAmount);
		map.put("estimateCreateDate", estimateCreateDate);
		result.add(map);

	}
	return result;
}


@Override
public Boolean updatePaymentRegister(UpdatePaymentDto updatePaymentDto) {
	Boolean flag=false;
	PaymentRegister paymentRegister = paymentRegisterRepository.findById(updatePaymentDto.getId()).get();
	paymentRegister.setEstimateId(updatePaymentDto.getEstimateId());
	paymentRegister.setBillingQuantity(updatePaymentDto.getBillingQuantity());
	paymentRegister.setPaymentType(updatePaymentDto.getPaymentType());
	paymentRegister.setCreatedById(updatePaymentDto.getCreatedById());
	paymentRegister.setTransactionId(updatePaymentDto.getTransactionId());
	paymentRegister.setServiceName(updatePaymentDto.getServiceName());
	if(updatePaymentDto.isTdsPresent()) {
		double tdsPercent = updatePaymentDto.getTdsPercent();
		double totalProfessional = updatePaymentDto.getProfessionalFees();
		double actualProfPercetage = 100-tdsPercent;
		double onePercent = totalProfessional/100;
		double tdsAmount = onePercent*tdsPercent;
		double profFees=onePercent*actualProfPercetage;
		paymentRegister.setProfessionalFees(profFees);
		paymentRegister.setTdsAmount(tdsAmount);
	}else {
		paymentRegister.setProfessionalFees(updatePaymentDto.getProfessionalFees());
	}
	paymentRegister.setGovermentfees(updatePaymentDto.getGovermentfees());
	paymentRegister.setGovermentGst(updatePaymentDto.getGovermentGst());

	paymentRegister.setProfessionalFees(updatePaymentDto.getProfessionalFees());
	paymentRegister.setProfesionalGst(updatePaymentDto.getProfesionalGst());
	paymentRegister.setServiceCharge(updatePaymentDto.getServiceCharge());		

	paymentRegister.setOtherFees(updatePaymentDto.getOtherFees());
	paymentRegister.setOtherGst(updatePaymentDto.getOtherGst());
	//		paymentRegister.setUploadReceipt(updatePaymentDto.getUploadReceipt());
	paymentRegister.setTotalAmount(updatePaymentDto.getTotalAmount());
	paymentRegister.setRemark(updatePaymentDto.getRemark());
	paymentRegister.setPaymentDate(updatePaymentDto.getPaymentDate());
	paymentRegister.setEstimateNo(updatePaymentDto.getEstimateNo());
	//		paymentRegister.setDoc(updatePaymentDto.getDoc());
	paymentRegister.setCompanyName(updatePaymentDto.getCompanyName());

	paymentRegister.setRegisterBy(updatePaymentDto.getRegisterBy());

	paymentRegister.setDocPersent(updatePaymentDto.getDocPersent());
	paymentRegister.setFilingPersent(updatePaymentDto.getFilingPersent());
	paymentRegister.setLiasoningPersent(updatePaymentDto.getLiasoningPersent());
	paymentRegister.setCertificatePersent(updatePaymentDto.getCertificatePersent());
	paymentRegisterRepository.save(paymentRegister);
	flag=true;
	return flag;
}
public Ledger createLedgerData(Map<String, Object> feignLeadClient,UpdatePaymentDto updatePaymentDto) {


	Boolean flag=false;
	Ledger l = new Ledger();
	l.setName(feignLeadClient.get("name").toString());
	l.setEmail(feignLeadClient.get("name").toString());
	l.setAddress(feignLeadClient.get("address").toString());
	l.setState(feignLeadClient.get("State").toString());
	l.setCountry(feignLeadClient.get("country").toString());
	l.setPin(feignLeadClient.get("pin").toString());

	//		ledger gst part
	if(l.isGstRateDetailPresent()) {
		l.setGstRateDetailPresent(l.isGstRateDetailPresent());
		l.setGstRateDetails(l.getGstRateDetails());
		Organization org = organizationRepository.findByName("corpseed");
		if(org!=null) {
			String state = org.getState();
			String eState=feignLeadClient.get("state").toString();
			if(state.equalsIgnoreCase(eState)) {
				double gstRateDetails=updatePaymentDto.getProfesionalGst();//gst percent from company
				double gst =gstRateDetails;
				double cgst=gst/2;
				double sgst=gst-cgst;
				l.setCgst(cgst+"");
				l.setSgst(sgst+"");
				l.setCgstSgstPresent(true);

				l.setGstRateDetails(gstRateDetails+"");
			}else {
				double gstRateDetails=updatePaymentDto.getProfesionalGst();//gst percent from company
				l.setIgstPresent(true);
				l.setGstRateDetails(gstRateDetails+"");
				l.setIgst(gstRateDetails+"");


			}
		}else {
			double gstRateDetails=updatePaymentDto.getProfesionalGst();//gst percent from company
			l.setGstRateDetails(gstRateDetails+"");
			l.setIgst(gstRateDetails+"");
		}
		//					l.setTaxabilityType(ledgerDto.getTaxabilityType());
		l.setGstRates(updatePaymentDto.getProfesionalGst()+"");
	}

	ledgerRepository.save(l);
	return null;


}
public Ledger createLedgerDataV2(Map<String, Object> feignLeadClient,PaymentRegister paymentRegister) {


	Boolean flag=false;
	Ledger l = new Ledger();
	l.setName(feignLeadClient.get("productName")!=null?feignLeadClient.get("productName").toString():null);
	//		l.setEmail(feignLeadClient.get("name").toString());
	l.setAddress(feignLeadClient.get("address").toString());
	l.setState(feignLeadClient.get("State")!=null?feignLeadClient.get("State").toString():null);
	l.setCountry(feignLeadClient.get("country").toString());
	l.setPin(feignLeadClient.get("pin")!=null?feignLeadClient.get("pin").toString():null);

	//
	//				if(ledgerDto.isHsnSacPresent()) {
	//					l.setHsnSacDetails(ledgerDto.getHsnSacDetails());
	//					l.setHsnSacPrsent(ledgerDto.isHsnSacPresent());
	//					l.setHsnSac(ledgerDto.getHsnSac());
	//					l.setHsnDescription(ledgerDto.getHsnDescription());
	//				}

	//		ledger gst part
	if(l.isGstRateDetailPresent()) {
		l.setGstRateDetailPresent(l.isGstRateDetailPresent());
		l.setGstRateDetails(l.getGstRateDetails());
		Organization org = organizationRepository.findByName("corpseed");
		if(org!=null) {
			String state = org.getState();
			String eState=feignLeadClient.get("state").toString();
			if(state.equalsIgnoreCase(eState)) {
				double gstRateDetails=paymentRegister.getProfesionalGst();//gst percent from company
				double gst =gstRateDetails;
				double cgst=gst/2;
				double sgst=gst-cgst;
				l.setCgst(cgst+"");
				l.setSgst(sgst+"");
				l.setCgstSgstPresent(true);

				l.setGstRateDetails(gstRateDetails+"");
			}else {
				double gstRateDetails=paymentRegister.getProfesionalGst();//gst percent from company
				l.setIgstPresent(true);
				l.setGstRateDetails(gstRateDetails+"");
				l.setIgst(gstRateDetails+"");


			}
		}else {
			double gstRateDetails=paymentRegister.getProfesionalGst();//gst percent from company
			l.setGstRateDetails(gstRateDetails+"");
			l.setIgst(gstRateDetails+"");
		}
		//					l.setTaxabilityType(ledgerDto.getTaxabilityType());
		l.setGstRates(paymentRegister.getProfesionalGst()+"");
	}


	//		if(ledgerDto.isBankAccountPresent()){
	//			l.setBankAccountPrsent(ledgerDto.isBankAccountPresent());
	//			l.setAccountHolderName(ledgerDto.getAccountHolderName());
	//			l.setAccountNo(ledgerDto.getAccountNo());
	//			l.setIfscCode(ledgerDto.getIfscCode());
	//			l.setSwiftCode(ledgerDto.getSwiftCode());
	//			l.setBankName(ledgerDto.getBankName());
	//			l.setBranch(ledgerDto.getBranch());
	//		}
	ledgerRepository.save(l);
	return l;


}
@Override
public Boolean paymentApproveAndDisapproved(UpdatePaymentDto updatePaymentDto) {
	//hit project and company creation
	//create create group
	//create ledger
	//create voucher
	Map<String, Object> feignLeadClient = LeadFeignClient.getEstimateById(updatePaymentDto.getEstimateId());

	LedgerType group = ledgerTypeRepository.findByName(updatePaymentDto.getPaymentType());
	if(group!=null) {
		if(updatePaymentDto.getRegisterBy().equals("SALES")) {
			Ledger ledger = ledgerRepository.findByName(updatePaymentDto.getCompanyName());
			if(ledger!=null) {
				Voucher v =new Voucher();
				//					long govermentfees =Long.valueOf(updatePaymentDto.getGovermentfees()!=null?updatePaymentDto.getGovermentfees():"0");
				//					long govermentGst =Long.valueOf(updatePaymentDto.getGovermentGst()!=null?updatePaymentDto.getGovermentGst():"0");
				//					long professionalFees =Long.valueOf(updatePaymentDto.getProfessionalFees()!=null?updatePaymentDto.getProfessionalFees():"0");
				//					long professionalGst =Long.valueOf(updatePaymentDto.getProfesionalGst()!=null?updatePaymentDto.getProfesionalGst():"0");
				//					long serviceCharge =Long.valueOf(updatePaymentDto.getServiceCharge()!=null?updatePaymentDto.getServiceCharge():"0");
				//					long getServiceGst =Long.valueOf(updatePaymentDto.getServiceGst()!=null?updatePaymentDto.getServiceGst():"0");
				//					long otherFees =Long.valueOf(updatePaymentDto.getOtherFees()!=null?updatePaymentDto.getOtherFees():"0");
				//					long otherGst =Long.valueOf(updatePaymentDto.getOtherGst()!=null?updatePaymentDto.getOtherGst():"0");
				double govermentfees =updatePaymentDto.getGovermentfees();
				double govermentGst =updatePaymentDto.getGovermentGst();;
				double professionalFees =updatePaymentDto.getProfessionalFees();
				double professionalGst =updatePaymentDto.getProfesionalGst();
				double serviceCharge =updatePaymentDto.getServiceCharge();
				double getServiceGst =updatePaymentDto.getServiceGst();
				double otherFees =updatePaymentDto.getOtherFees();
				double otherGst =updatePaymentDto.getOtherGst();

				double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
				double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
				v.setDebitAmount(totalDebit);
				v.setIgstPresent(true);//cgst+sgst concept
				v.setIgst(totaDebitGst+"");			
				v.setLedger(ledger);
				v.setEstimateId(updatePaymentDto.getEstimateId());

				voucherRepository.save(v);
			}else {
				Voucher v =new Voucher();
				double govermentfees =updatePaymentDto.getGovermentfees();
				double govermentGst =updatePaymentDto.getGovermentGst();
				double professionalFees =updatePaymentDto.getProfessionalFees();
				double professionalGst =updatePaymentDto.getProfesionalGst();
				double serviceCharge =updatePaymentDto.getServiceCharge();
				double getServiceGst =updatePaymentDto.getServiceGst();
				double otherFees =updatePaymentDto.getOtherFees();
				double otherGst =updatePaymentDto.getOtherGst();
				double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
				double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
				v.setDebitAmount(totalDebit);
				v.setIgstPresent(true);//cgst+sgst concept
				v.setIgst(totaDebitGst+"");

				v.setLedger(ledger);
				v.setEstimateId(updatePaymentDto.getEstimateId());

				voucherRepository.save(v);
			}

		}else {

		}

	}else {
		if(updatePaymentDto.getPaymentType().equals("SALES")) {


			LedgerType g = new LedgerType();
			g.setName(updatePaymentDto.getPaymentType());

			//				Map<String, Object> feignLeadClient = LeadFeignClient.getEstimateById(updatePaymentDto.getEstimateId());
			Ledger l=createLedgerData(feignLeadClient,updatePaymentDto);

			Voucher v =new Voucher();
			double govermentfees =updatePaymentDto.getGovermentfees();
			double govermentGst =updatePaymentDto.getGovermentGst();
			double professionalFees =updatePaymentDto.getProfessionalFees();
			double professionalGst =updatePaymentDto.getProfesionalGst();
			double serviceCharge =updatePaymentDto.getServiceCharge();
			double getServiceGst =updatePaymentDto.getServiceGst();
			double otherFees =updatePaymentDto.getOtherFees();
			double otherGst =updatePaymentDto.getOtherGst();
			double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
			double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
			v.setDebitAmount(totalDebit);
			v.setIgstPresent(true);//cgst+sgst concept
			v.setIgst(totaDebitGst+"");

			v.setLedger(l);
			voucherRepository.save(v);

			//also project api hit 

		}else {

		}

	}


	Ledger ledger=ledgerRepository.findByName(updatePaymentDto.getCompanyName());
	ledger.setName(updatePaymentDto.getCompanyName());

	return null;
}




@Override
public PaymentRegister getPaymentRegisterById(long id) {
	PaymentRegister paymentRegister = paymentRegisterRepository.findById(id).get();
	Map<String, Object> estimate = LeadFeignClient.getEstimateById(paymentRegister.getEstimateId());
	return paymentRegister;
}



public Boolean paymentApproveAndDisapprovedV2(UpdatePaymentDto updatePaymentDto) {
	Boolean flag=false;
	Map<String, Object> feignLeadClient = LeadFeignClient.getEstimateById(updatePaymentDto.getEstimateId());

	List<Voucher> voucherList=voucherRepository.findByEstimateId(updatePaymentDto.getEstimateId());
	if(voucherList!=null) {  // Already created

		Ledger ledger = ledgerRepository.findByName(updatePaymentDto.getCompanyName());

		Voucher v =new Voucher();
		double govermentfees =updatePaymentDto.getGovermentfees();
		double govermentGst =updatePaymentDto.getGovermentGst();
		double professionalFees =updatePaymentDto.getProfessionalFees();
		double professionalGst =updatePaymentDto.getProfesionalGst();
		double serviceCharge =updatePaymentDto.getServiceCharge();
		double getServiceGst =updatePaymentDto.getServiceGst();
		double otherFees =updatePaymentDto.getOtherFees();
		double otherGst =updatePaymentDto.getOtherGst();
		double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
		double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
		v.setDebitAmount(totalDebit);
		v.setIgstPresent(true);//cgst+sgst concept
		v.setIgst(totaDebitGst+"");			
		v.setLedger(ledger);
		v.setEstimateId(updatePaymentDto.getEstimateId());
		voucherRepository.save(v);
		flag=true;
	}else {
		LedgerType group = ledgerTypeRepository.findByName(updatePaymentDto.getPaymentType());

		if(group!=null) {
			Ledger ledger = ledgerRepository.findByName(updatePaymentDto.getCompanyName());
			if(ledger!=null) {

				Voucher v =new Voucher();
				double govermentfees =updatePaymentDto.getGovermentfees();
				double govermentGst =updatePaymentDto.getGovermentGst();
				double professionalFees =updatePaymentDto.getProfessionalFees();
				double professionalGst =updatePaymentDto.getProfesionalGst();
				double serviceCharge =updatePaymentDto.getProfesionalGst();
				double getServiceGst =updatePaymentDto.getServiceGst();
				double otherFees =updatePaymentDto.getOtherFees();
				double otherGst =updatePaymentDto.getOtherGst();
				double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
				double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
				v.setDebitAmount(totalDebit);
				v.setIgstPresent(true);//cgst+sgst concept
				v.setIgst(totaDebitGst+"");			
				v.setLedger(ledger);
				v.setEstimateId(updatePaymentDto.getEstimateId());
				voucherRepository.save(v);
				flag=true;
			}else {
				Ledger l=createLedgerData(feignLeadClient,updatePaymentDto);

				Voucher v =new Voucher();
				double govermentfees =updatePaymentDto.getGovermentfees();
				double govermentGst =updatePaymentDto.getGovermentGst();
				double professionalFees =updatePaymentDto.getProfessionalFees();
				double professionalGst =updatePaymentDto.getProfesionalGst();
				double serviceCharge =updatePaymentDto.getServiceCharge();
				double getServiceGst =updatePaymentDto.getServiceGst();
				double otherFees =updatePaymentDto.getOtherFees();
				double otherGst =updatePaymentDto.getOtherGst();
				double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
				double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
				v.setDebitAmount(totalDebit);
				v.setIgstPresent(true);//cgst+sgst concept
				v.setIgst(totaDebitGst+"");			
				v.setLedger(ledger);
				v.setEstimateId(updatePaymentDto.getEstimateId());
				voucherRepository.save(v);
				flag=true;
			}	
		}			
	}
	return false;

}

public Boolean createInvoice(Long estimateId) {
	Map<String, Object> feignLeadClient = LeadFeignClient.getEstimateById(estimateId);
	List<PaymentRegister> paymentRegister = paymentRegisterRepository.findAllByEstimateId(estimateId);
	InvoiceData invoiceData=new InvoiceData();
	invoiceData.setCreateDate(new Date());

	//		 invoiceData.setProduct(product);
	invoiceData.setAddress(null);
	invoiceData.setCompanyName(feignLeadClient.get("companyName")!=null?feignLeadClient.get("companyName").toString():null);
	invoiceData.setPrimaryContactId(estimateId);

	invoiceData.setProductName(feignLeadClient.get("productName").toString());
	invoiceData.setEstimateId(estimateId);

	String assignee = feignLeadClient.get("assigneeIds").toString();
	Long assigneeId=Long.parseLong(assignee);

	if(assigneeId!=null) {
		User user = userRepository.findById(assigneeId).get();
		invoiceData.setAssignee(user);
	}

	String lead = feignLeadClient.get("leadId")!=null?feignLeadClient.get("leadId").toString():null;
	if(lead!=null) {
		Long leadId=Long.parseLong(lead);
		invoiceData.setLeadId(leadId);

	}

	invoiceData.setGstNo(feignLeadClient.get("gstNo")!=null?feignLeadClient.get("gstNo").toString().toString():null);

	invoiceData.setGstType(feignLeadClient.get("gstType")!=null?feignLeadClient.get("gstType").toString():null);
	invoiceData.setGstDocuments(feignLeadClient.get("gstNo")!=null?feignLeadClient.get("gstNo").toString():null);//misssing 

	//				invoiceData.setCompanyAge(feignLeadClient.get("companyAge").toString());


	invoiceData.setGovermentfees(feignLeadClient.get("govermentFees").toString());
	invoiceData.setGovermentCode(feignLeadClient.get("govermentCode")!=null?feignLeadClient.get("govermentCode").toString():null);
	invoiceData.setGovermentGst(feignLeadClient.get("govermentGst")!=null?feignLeadClient.get("govermentGst").toString():null);

	invoiceData.setProfessionalFees(feignLeadClient.get("professionalFees").toString());
	invoiceData.setProfessionalCode(feignLeadClient.get("profesionalCode")!=null?feignLeadClient.get("profesionalCode").toString():null);
	invoiceData.setProfesionalGst(feignLeadClient.get("profesionalGst")!=null?feignLeadClient.get("profesionalGst").toString():null);

	invoiceData.setServiceCharge(feignLeadClient.get("serviceCharge").toString());

	//		invoiceData.setGovermentfees(feignLeadClient.get("govermentFees").toString());
	//		invoiceData.setGovermentCode(feignLeadClient.get("govermentCode")!=null?feignLeadClient.get("govermentCode").toString():null);
	//		invoiceData.setGovermentGst(feignLeadClient.get("govermentGst")!=null?feignLeadClient.get("govermentGst").toString():null);
	//
	//		invoiceData.setProfessionalFees(feignLeadClient.get("professionalFees").toString());
	//		invoiceData.setProfessionalCode(feignLeadClient.get("profesionalCode")!=null?feignLeadClient.get("profesionalCode").toString():null);
	//		invoiceData.setProfesionalGst(feignLeadClient.get("profesionalGst")!=null?feignLeadClient.get("profesionalGst").toString():null);
	//
	//		invoiceData.setServiceCharge(feignLeadClient.get("serviceCharge").toString());


	invoiceData.setGovermentfees(feignLeadClient.get("govermentFees").toString());
	invoiceData.setGovermentCode(feignLeadClient.get("govermentCode")!=null?feignLeadClient.get("govermentCode").toString():null);
	invoiceData.setGovermentGst(feignLeadClient.get("govermentGst")!=null?feignLeadClient.get("govermentGst").toString():null);

	invoiceData.setProfessionalFees(feignLeadClient.get("professionalFees").toString());
	invoiceData.setProfessionalCode(feignLeadClient.get("profesionalCode")!=null?feignLeadClient.get("profesionalCode").toString():null);
	invoiceData.setProfesionalGst(feignLeadClient.get("profesionalGst")!=null?feignLeadClient.get("profesionalGst").toString():null);

	invoiceData.setServiceCharge(feignLeadClient.get("serviceCharge").toString());
	invoiceData.setTotalAmount(feignLeadClient.get("totalAmount").toString());

	if(feignLeadClient.get("serviceCode")!=null) {
		invoiceData.setServiceCode(feignLeadClient.get("serviceCode").toString());
	}
	if(feignLeadClient.get("serviceGst")!=null) {
		invoiceData.setServiceGst(feignLeadClient.get("serviceGst").toString());

	}

	//
	//		invoiceData.setOtherFees(feignLeadClient.get("otherFees").toString());
	//		invoiceData.setOtherCode(feignLeadClient.get("otherCode")!=null?feignLeadClient.get("otherCode").toString():null);
	//		invoiceData.setOtherGst(feignLeadClient.get("otherGst")!=null?feignLeadClient.get("otherGst").toString():null);

	invoiceData.setAddress(feignLeadClient.get("address").toString());
	invoiceData.setCity(feignLeadClient.get("city").toString());
	invoiceData.setPrimaryPinCode(feignLeadClient.get("primaryPinCode").toString());
	invoiceData.setState(feignLeadClient.get("state").toString());
	invoiceData.setCountry(feignLeadClient.get("country").toString());

	invoiceData.setSecondaryAddress(feignLeadClient.get("secondaryAddress")!=null?feignLeadClient.get("secondaryAddress").toString():null);
	invoiceData.setSecondaryCity(feignLeadClient.get("secondaryCity")!=null?feignLeadClient.get("secondaryCity").toString():null); 
	invoiceData.setSecondaryPinCode(feignLeadClient.get("secondaryPinCode")!=null?feignLeadClient.get("secondaryPinCode").toString():null);
	invoiceData.setSecondaryState(feignLeadClient.get("secondaryState")!=null?feignLeadClient.get("secondaryState").toString():null);
	invoiceData.setSecondaryCountry(feignLeadClient.get("secondaryCountry")!=null?feignLeadClient.get("secondaryCountry").toString():null);

	invoiceData.setInvoiceNote(feignLeadClient.get("invoiceNote").toString());
	invoiceData.setOrderNumber(feignLeadClient.get("orderNumber").toString());
	invoiceData.setPurchaseDate(feignLeadClient.get("purchaseDate").toString());//CURRENT DATE 
	//		invoiceData.setEstimateData((Date)feignLeadClient.get("estimateDate"));
	invoiceData.setRemarksForOption(feignLeadClient.get("address").toString());
	invoiceData.setCc((List<String>)feignLeadClient.get("ccMail"));
	if(feignLeadClient.get("primaryContactId")!=null) {

		invoiceData.setPrimaryContactId(Long.parseLong(feignLeadClient.get("primaryContactId").toString()));
		invoiceData.setPrimaryContactTitle(feignLeadClient.get("primaryContactTitle")!=null?feignLeadClient.get("primaryContactTitle").toString():null);
		invoiceData.setPrimaryContactName(feignLeadClient.get("primaryContactName")!=null?feignLeadClient.get("primaryContactName").toString():null);
		invoiceData.setPrimaryContactemails(feignLeadClient.get("primaryContactEmails")!=null?feignLeadClient.get("primaryContactEmails").toString():null);
		invoiceData.setPrimaryContactNo(feignLeadClient.get("primaryContactContactNo")!=null?feignLeadClient.get("primaryContactContactNo").toString():null);
	}
	if(feignLeadClient.get("secondaryContactId")!=null) {
		invoiceData.setSecondaryContactId(Long.parseLong(feignLeadClient.get("secondaryContactId").toString()));
		invoiceData.setSecondaryContactTitle(feignLeadClient.get("secondaryContactTitle")!=null?feignLeadClient.get("secondaryContactTitle").toString():null);
		invoiceData.setSecondaryContactName(feignLeadClient.get("secondaryContactName")!=null?feignLeadClient.get("secondaryContactName").toString():null);
		invoiceData.setSecondaryContactemails(feignLeadClient.get("secondaryContactEmails")!=null?feignLeadClient.get("secondaryContactEmails").toString():null);
		invoiceData.setSecondaryContactNo(feignLeadClient.get("secondaryContactContactNo")!=null?feignLeadClient.get("secondaryContactContactNo").toString():null);
	}
	invoiceDataRepository.save(invoiceData);
	return true;
}

public Boolean paymentApproveAndDisapprovedV3(Long paymentRegisterId ,Long estimateId) {
	Boolean flag=false;
	//		Organization organization = organizationRepository.findByName("corpseed");
	Map<String, Object> feignLeadClient = LeadFeignClient.getEstimateById(estimateId);
	String ledgerName = (String)feignLeadClient.get("companyName");
	List<Voucher> voucherList=voucherRepository.findByEstimateId(estimateId);
	PaymentRegister paymentRegister = paymentRegisterRepository.findById(paymentRegisterId).get();
	VoucherType vType=voucherTypeRepo.findByName(paymentRegister.getRegisterBy());
	if(vType ==null) {
		vType=new VoucherType();
		vType.setName(paymentRegister.getRegisterBy());
		vType.setDeleted(false);
		voucherTypeRepo.save(vType);
	}
	if(voucherList!=null &&voucherList.size()>0) {  // Already created
		Ledger ledger = ledgerRepository.findByName(ledgerName);
		Organization organization = organizationRepository.findByName("corpseed");
		//			String totalEstimateAmount = feignLeadClient.get("totalAmount")!=null?(String)feignLeadClient.get("totalAmount"):null;
		Voucher v =new Voucher();
		v.setVoucherType(vType);
		double govermentfees =paymentRegister.getGovermentfees();
		double govermentGst =paymentRegister.getGovermentGst();

		double professionalFees =paymentRegister.getProfessionalFees();
		double professionalGst =paymentRegister.getProfesionalGst();

		double serviceCharge =paymentRegister.getServiceCharge();
		double getServiceGst =paymentRegister.getServiceGst();

		double otherFees =paymentRegister.getOtherFees();
		double otherGst =paymentRegister.getOtherGst();

		v.setCreateDate(new Date());
		v.setVoucherType(vType);
		double totalCredit=govermentfees+professionalFees+serviceCharge+otherFees;

		double totalCreditGst = govermentGst+professionalGst+getServiceGst+otherGst;

		String org=(String)feignLeadClient.get("state");

		v.setCreditAmount(totalCredit);
		if(organization!=null && organization.getState()!=null && organization.getState().equals(org)) {
			double cgst=totalCreditGst/2;
			double sgst=totalCreditGst/2;
			v.setCgstSgstPresent(true);
			v.setCgst(cgst+"");
			v.setSgst(sgst+"");
		}else {
			v.setIgstPresent(false);
			v.setIgst(totalCreditGst+"");
		}
		Ledger product = ledgerRepository.findByName(paymentRegister.getServiceName());
		if(product!=null) {
			v.setProduct(product);
		}else {
			product= new Ledger();
			product.setName(paymentRegister.getServiceName());
			ledgerRepository.save(product);
			v.setProduct(product);
		}
		v.setLedger(ledger);
		v.setEstimateId(paymentRegister.getEstimateId());
		voucherRepository.save(v);
		//tds concept
		if(paymentRegister.isTdsPresent()) {
			Voucher tdsRegister =new Voucher();
			tdsRegister.setVoucherType(vType);
			tdsRegister.setCreditDebit(true);
			tdsRegister.setCreateDate(new Date());
			tdsRegister.setCreditAmount(paymentRegister.getTdsAmount());
			Ledger l = ledgerRepository.findByName(paymentRegister.getCompanyName());
			if(l!=null) {
				tdsRegister.setLedger(l);
			}else {
				l=new Ledger();
				l.setName(paymentRegister.getCompanyName());
				ledgerRepository.save(l);
				tdsRegister.setLedger(l);
			}
			tdsRegister.setCreateDate(new Date());
			tdsRegister.setLedger(ledger);
			tdsRegister.setEstimateId(estimateId);
			Ledger productTds = ledgerRepository.findByName("TDS RECEIVABLE");
			if(productTds!=null) {
				tdsRegister.setProduct(productTds);
			}else{
				Ledger tdsLedger = new Ledger();
				tdsLedger.setName("TDS RECEIVABLE");
				ledgerRepository.save(tdsLedger);
				tdsRegister.setProduct(tdsLedger);
			}
			tdsRegister.setProduct(productTds);
			tdsRegister.getCreateDate();
			voucherRepository.save(tdsRegister);
			CreateTdsDto createTdsDto= new CreateTdsDto();
			createTdsDto.setOrganization(paymentRegister.getCompanyName());
			createTdsDto.setPaymentRegisterId(paymentRegister.getTransactionId());
			if(ledger!=null) {
				createTdsDto.setLedgerId(ledger.getId());
			}
			createTdsDto.setTdsAmount(paymentRegister.getTdsAmount());
			createTdsDto.setTdsPrecent(paymentRegister.getTdsPercent());
			createTdsDto.setTdsType("receivable");
			createTdsDto.setTotalPaymentAmount(paymentRegister.getProfessionalFees()+paymentRegister.getTdsAmount());
			tdsServiceImpl.createTds(createTdsDto);
		}


		flag=true;
	}else {

		LedgerType group = ledgerTypeRepository.findByName(paymentRegister.getRegisterBy());
		Organization organization = organizationRepository.findByName("corpseed");
		if(group==null) {
			group =new LedgerType();
			group.setName(paymentRegister.getRegisterBy());
			ledgerTypeRepository.save(group);
		}
		if(group!=null) { 
			Ledger ledger = ledgerRepository.findByName(paymentRegister.getCompanyName());
			double totalEstimateAmount = Double.parseDouble(feignLeadClient.get("totalAmount")!=null?feignLeadClient.get("totalAmount").toString():"0");
			// == total amount register ==
			Voucher totalAmountRegister = new Voucher();
			totalAmountRegister.setVoucherType(vType);
			totalAmountRegister.setCreditDebit(true);
			totalAmountRegister.setCreateDate(new Date());
			totalAmountRegister.setDebitAmount(totalEstimateAmount);
			totalAmountRegister.setLedger(ledger);
			Ledger prod =ledgerRepository.findByName(paymentRegister.getServiceName());
			if(prod!=null) {
				totalAmountRegister.setLedger(prod);
			}else {
				prod=new Ledger();
				prod.setName(paymentRegister.getServiceName());
				ledgerRepository.save(prod);
				totalAmountRegister.setLedger(prod);
			}


			totalAmountRegister.setLedger(ledger);
			Ledger p = ledgerRepository.findByName(paymentRegister.getServiceName());
			if(p!=null) {
				totalAmountRegister.setProduct(p);

			}else{
				p =new Ledger();
				p.setName(paymentRegister.getServiceName());
				ledgerRepository.save(p);
				totalAmountRegister.setProduct(p);

			}

			totalAmountRegister.setProduct(p);
			totalAmountRegister.setCreditDebit(true);
			voucherRepository.save(totalAmountRegister);

			if(ledger!=null) {

				Voucher v =new Voucher();
				v.setVoucherType(vType);
				double govermentfees =paymentRegister.getGovermentfees();
				double govermentGst =paymentRegister.getGovermentGst();
				double professionalFees =paymentRegister.getProfessionalFees();
				double professionalGst =paymentRegister.getProfesionalGst();
				double serviceCharge =paymentRegister.getProfesionalGst();
				double getServiceGst =paymentRegister.getServiceGst();
				double otherFees =paymentRegister.getOtherFees();
				double otherGst =paymentRegister.getOtherGst();
				double totalCredit=govermentfees+professionalFees+serviceCharge+otherFees;
				double totalCreditGst = govermentGst+professionalGst+getServiceGst+otherGst;
				v.setCreditAmount(totalCredit);
				v.setCreditDebit(true);
				v.setCreateDate(new Date());
				//					v.setIgstPresent(true);//cgst+sgst concept
				//					v.setIgst(totaDebitGst+"");		
				if(organization.getState().equals(v)) {
					double cgst=totalCreditGst/2;
					double sgst=totalCreditGst/2;
					v.setCgstSgstPresent(true);
					v.setCgst(cgst+"");
					v.setSgst(sgst+"");
				}else {
					v.setIgstPresent(false);
					v.setIgst(totalCreditGst+"");
				}
				v.setLedger(ledger);
				Ledger product = ledgerRepository.findByName(paymentRegister.getServiceName());
				if(product!=null) {
					v.setProduct(product);
				}else {
					product= new Ledger();
					product.setName(paymentRegister.getServiceName());
					ledgerRepository.save(product);
					v.setProduct(product);

				}

				v.setEstimateId(paymentRegister.getEstimateId());
				voucherRepository.save(v);


				if(paymentRegister.isTdsPresent()) {

					Voucher tdsRegister =new Voucher();
					tdsRegister.setVoucherType(vType);
					tdsRegister.setCreditDebit(true);
					tdsRegister.setCreditAmount(paymentRegister.getTdsAmount());
					tdsRegister.setCreateDate(new Date());
					tdsRegister.setLedger(ledger);
					tdsRegister.setEstimateId(estimateId);
					Ledger productTds = ledgerRepository.findByName("TDS RECEIVABLE");
					if(productTds!=null) {
						tdsRegister.setProduct(productTds);
					}else{
						Ledger tdsLedger = new Ledger();
						tdsLedger.setName("TDS RECEIVABLE");
						ledgerRepository.save(tdsLedger);
						tdsRegister.setProduct(tdsLedger);
					}
					tdsRegister.setCreditDebit(true);
					tdsRegister.setProduct(productTds);
					tdsRegister.getCreateDate();
					voucherRepository.save(tdsRegister);
					CreateTdsDto createTdsDto= new CreateTdsDto();
					createTdsDto.setOrganization(paymentRegister.getCompanyName());
					createTdsDto.setPaymentRegisterId(paymentRegister.getTransactionId());
					createTdsDto.setLedgerId(ledger.getId());
					createTdsDto.setTdsAmount(paymentRegister.getTdsAmount());
					createTdsDto.setTdsPrecent(paymentRegister.getTdsPercent());
					createTdsDto.setTdsType("receivable");
					createTdsDto.setTotalPaymentAmount(paymentRegister.getProfessionalFees()+paymentRegister.getTdsAmount());
					tdsServiceImpl.createTds(createTdsDto);
				}


				flag=true;
			}else {
				Ledger l=createLedgerDataForCompany(feignLeadClient,paymentRegister);

				totalEstimateAmount = Double.parseDouble(feignLeadClient.get("totalAmount")!=null?feignLeadClient.get("totalAmount").toString():"0");
				// == total amount register ==
				totalAmountRegister = new Voucher();
				totalAmountRegister.setVoucherType(vType);
				totalAmountRegister.setCreditDebit(true);
				totalAmountRegister.setCreateDate(new Date());
				totalAmountRegister.setDebitAmount(totalEstimateAmount);
				totalAmountRegister.setLedger(l);
				prod =ledgerRepository.findByName(paymentRegister.getServiceName());
				if(prod!=null) {
					totalAmountRegister.setProduct(prod);

				}else {
					prod=new Ledger();
					prod.setName(paymentRegister.getServiceName());
					ledgerRepository.save(prod);
					totalAmountRegister.setProduct(prod);

				}
				totalAmountRegister.setLedger(l);
				totalAmountRegister.setProduct(ledger);
				totalAmountRegister.setCreditDebit(true);
				voucherRepository.save(totalAmountRegister);

				// ===== recived ammount register with gst ======
				Voucher v =new Voucher();
				v.setVoucherType(vType);
				double govermentfees =paymentRegister.getGovermentfees();
				double govermentGst =paymentRegister.getGovermentGst();
				double professionalFees =paymentRegister.getProfessionalFees();
				double professionalGst =paymentRegister.getProfesionalGst();
				double serviceCharge =paymentRegister.getServiceCharge();
				double getServiceGst =paymentRegister.getServiceGst();
				double otherFees =paymentRegister.getOtherFees();
				double otherGst =paymentRegister.getOtherGst();
				double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
				double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
				v.setCreditDebit(true);
				v.setCreditAmount(totalDebit);
				v.setIgstPresent(true);//cgst+sgst concept
				v.setIgst(totaDebitGst+"");			
				v.setLedger(l);
				v.setCreateDate(new Date());
				Ledger product = ledgerRepository.findByName(paymentRegister.getServiceName());
				if(product!=null) {
					v.setProduct(product);
				}else {
					product= new Ledger();
					product.setName(paymentRegister.getServiceName());
					ledgerRepository.save(product);
					v.setProduct(product);

				}
				v.setEstimateId(paymentRegister.getEstimateId());

				voucherRepository.save(v);   

				// ===== Tds register ======
				if(paymentRegister.isTdsPresent()) {

					Voucher tdsRegister =new Voucher();
					tdsRegister.setVoucherType(vType);
					tdsRegister.setCreditDebit(true);
					tdsRegister.setCreditAmount(paymentRegister.getTdsAmount());
					tdsRegister.setCreateDate(new Date());
					tdsRegister.setLedger(ledger);
					tdsRegister.setEstimateId(estimateId);
					tdsRegister.setCreateDate(new Date());
					Ledger productTds = ledgerRepository.findByName("TDS RECEIVABLE");
					if(productTds!=null) {
						tdsRegister.setProduct(productTds);
					}else{
						Ledger tdsLedger = new Ledger();
						tdsLedger.setName("TDS RECEIVABLE");
						ledgerRepository.save(tdsLedger);
						tdsRegister.setProduct(tdsLedger);
					}
					tdsRegister.setProduct(productTds);
					tdsRegister.getCreateDate();
					voucherRepository.save(tdsRegister);
					CreateTdsDto createTdsDto= new CreateTdsDto();
					createTdsDto.setOrganization(paymentRegister.getCompanyName());
					createTdsDto.setPaymentRegisterId(paymentRegister.getTransactionId());
					if(ledger!=null) {
						createTdsDto.setLedgerId(ledger.getId());
					}
					createTdsDto.setTdsAmount(paymentRegister.getTdsAmount());
					createTdsDto.setTdsPrecent(paymentRegister.getTdsPercent());
					createTdsDto.setTdsType("receivable");
					createTdsDto.setTotalPaymentAmount(paymentRegister.getProfessionalFees()+paymentRegister.getTdsAmount());
					tdsServiceImpl.createTds(createTdsDto);

				}

				flag=true;
			}	
		}			
	}
	return flag;

}

public Ledger createLedgerDataForCompany(Map<String, Object> feignLeadClient,PaymentRegister paymentRegister) {


	Boolean flag=false;
	Ledger l = new Ledger();
	l.setName(paymentRegister.getCompanyName());
	l.setAddress(feignLeadClient.get("address").toString());
	l.setState(feignLeadClient.get("State")!=null?feignLeadClient.get("State").toString():null);
	l.setCountry(feignLeadClient.get("country").toString());
	l.setPin(feignLeadClient.get("pin")!=null?feignLeadClient.get("pin").toString():null);


	//		ledger gst part
	if(l.isGstRateDetailPresent()) {
		l.setGstRateDetailPresent(l.isGstRateDetailPresent());
		l.setGstRateDetails(l.getGstRateDetails());
		Organization org = organizationRepository.findByName("corpseed");
		if(org!=null) {
			String state = org.getState();
			String eState=feignLeadClient.get("state").toString();
			if(state.equalsIgnoreCase(eState)) {
				double gstRateDetails=paymentRegister.getProfesionalGst();//gst percent from company
				double gst =gstRateDetails;
				double cgst=gst/2;
				double sgst=gst-cgst;
				l.setCgst(cgst+"");
				l.setSgst(sgst+"");
				l.setCgstSgstPresent(true);

				l.setGstRateDetails(gstRateDetails+"");
			}else {
				double gstRateDetails=paymentRegister.getProfesionalGst();//gst percent from company
				l.setIgstPresent(true);
				l.setGstRateDetails(gstRateDetails+"");
				l.setIgst(gstRateDetails+"");


			}
		}else {
			double gstRateDetails=paymentRegister.getProfesionalGst();//gst percent from company
			l.setGstRateDetails(gstRateDetails+"");
			l.setIgst(gstRateDetails+"");
		}
		l.setGstRates(paymentRegister.getProfesionalGst()+"");
	}
	ledgerRepository.save(l);
	return l;


}

@Override
public List<PaymentRegister> getPaymentRegisterByEstimateId(long id) {
	List<PaymentRegister>paymentRegisterList= paymentRegisterRepository.findAllByEstimateId(id);
	return paymentRegisterList;
}


@Override
public Map<String,Object> getInvoice(Long id) {
	InvoiceData invoiceData = invoiceDataRepository.findById(id).get();

	Map<String,Object>map=new HashMap<>();
	map.put("id", invoiceData.getId());
	map.put("invoiceNo", invoiceData.getId());
	map.put("service", invoiceData.getProductName());
	map.put("clientid", invoiceData.getPrimaryContactId());
	map.put("clientName", invoiceData.getPrimaryContactName());
	map.put("clientEmail", invoiceData.getPrimaryContactemails());
	map.put("companyName", invoiceData.getCompanyName());
	map.put("txnAmount", invoiceData.getTotalAmount());
	map.put("addedBy", invoiceData.getAssignee());
	map.put("gstNo", invoiceData.getGstNo());
	Double prof = Double.valueOf(invoiceData.getProfessionalFees());
	Double gst =  Double.valueOf(invoiceData.getProfesionalGst());
	if(prof!=null&& prof!=0) {
		double gstAmount = ((gst*prof)/100);

		map.put("gstAmount", gstAmount);
	}
	map.put("profGst", invoiceData.getProfesionalGst());

	map.put("hsnNo", invoiceData.getHsnSacDetails());


	return map;
}


@Override
public Map<String, Integer> leftAmount(Long id) {
	String status="approved";
	List<PaymentRegister>paymentRegister=paymentRegisterRepository.findAllByEstimateId(id,status);
	double totalRegisterAmount=0;
	for(PaymentRegister pr: paymentRegister ) {
		totalRegisterAmount=totalRegisterAmount+pr.getTotalAmount();
	}

	return null;
}


@Override
public List<PaymentRegister> getAllPaymentRegisterByStatus(String status) {
	List<PaymentRegister>paymentRegister=new ArrayList<>();
	if(status!=null) {
		paymentRegister=paymentRegisterRepository.findAllByEstimateId(status);
	}else {
		paymentRegister=paymentRegisterRepository.findAll();
	}
	return paymentRegister;
}


@Override
public Boolean paymentDisapproved(Long id) {
	Boolean flag=false;
	PaymentRegister payment = paymentRegisterRepository.findById(id).get();
	payment.setStatus("disapproved");
	paymentRegisterRepository.save(payment);
	flag=true;
	return flag;
}


//    
public Boolean paymentApproveAndDisapprovedV4(Long paymentRegisterId ,Long estimateId) {

	Boolean flag=false;
	Map<String, Object> feignLeadClient = LeadFeignClient.getEstimateById(estimateId);
	String ledgerName = (String)feignLeadClient.get("companyName");
	List<Voucher> voucherList=voucherRepository.findByEstimateId(estimateId);
	PaymentRegister paymentRegister = paymentRegisterRepository.findById(paymentRegisterId).get();
	//Group
	VoucherType vType=voucherTypeRepo.findByName(paymentRegister.getRegisterBy());
	if(vType ==null) {
		vType=new VoucherType();
		vType.setName(paymentRegister.getRegisterBy());
		vType.setDeleted(false);
		voucherTypeRepo.save(vType);
	}
	// Voucher Exist
	if(voucherList!=null &&voucherList.size()>0) {  // Already created

		Ledger ledger = ledgerRepository.findByName(ledgerName);

		if(ledger==null) {
			ledger=new Ledger();
			ledger.setName(ledgerName);
			ledgerRepository.save(ledger);
		}

		Organization organization = organizationRepository.findByName("corpseed");

		Voucher v =new Voucher();
		v.setVoucherType(vType);
		double govermentfees =paymentRegister.getGovermentfees();
		double govermentGst =paymentRegister.getGovermentGst();

		double professionalFees =paymentRegister.getProfessionalFees();
		double professionalGst =paymentRegister.getProfesionalGst();

		double serviceCharge =paymentRegister.getServiceCharge();
		double getServiceGst =paymentRegister.getServiceGst();

		double otherFees =paymentRegister.getOtherFees();
		double otherGst =paymentRegister.getOtherGst();

		v.setCreateDate(new Date());
		v.setVoucherType(vType);
		System.out.println("test111111");
		double totalCredit=govermentfees+professionalFees+serviceCharge+otherFees;
		double totalProfessionalCredit=govermentfees+professionalFees+serviceCharge+otherFees;

		double totalCreditGst = govermentGst+professionalGst+getServiceGst+otherGst;

		String org=(String)feignLeadClient.get("state");

		v.setCreditAmount(totalCredit);
		if(organization!=null && organization.getState()!=null && organization.getState().equals(org)) {
			System.out.println("test222222");
			double cgst=totalCreditGst/2;
			double sgst=totalCreditGst/2;
			v.setCgstSgstPresent(true);
			v.setCgst(cgst+"");
			v.setSgst(sgst+"");
		}else {
			System.out.println("test333333");
			v.setIgstPresent(false);
			v.setIgst(totalCreditGst+"");
		}
		//			Ledger product = ledgerRepository.findByName(paymentRegister.getServiceName());
		Ledger product = ledgerRepository.findByName("Professional Fees");

		if(product!=null) {
			System.out.println("test4444444..."+product.getName());
			//				v.setProduct(product);
		}else {
			System.out.println("test5555555");
			product= new Ledger();
			product.setName("Professional Fees");
			ledgerRepository.save(product);
			v.setProduct(product);
		}
		v.setLedger(ledger);
		v.setEstimateId(paymentRegister.getEstimateId());
		voucherRepository.save(v);


		//tds concept
		if(paymentRegister.isTdsPresent()) {
			System.out.println("test666666666");
			Voucher tdsRegister =new Voucher();
			tdsRegister.setVoucherType(vType);
			tdsRegister.setCreditDebit(true);
			tdsRegister.setCreateDate(new Date());
			tdsRegister.setCreditAmount(paymentRegister.getTdsAmount());
			Ledger l = ledgerRepository.findByName(paymentRegister.getCompanyName());
			if(l!=null) {
				tdsRegister.setLedger(l);
			}else {
				l=new Ledger();
				l.setName(paymentRegister.getCompanyName());
				ledgerRepository.save(l);
				tdsRegister.setLedger(l);
			}
			tdsRegister.setCreateDate(new Date());
			tdsRegister.setLedger(ledger);
			tdsRegister.setEstimateId(estimateId);
			Ledger productTds = ledgerRepository.findByName("TDS RECEIVABLE");
			if(productTds!=null) {
				System.out.println("test77777777777");
				tdsRegister.setProduct(productTds);
			}else{
				System.out.println("test888888888888");
				Ledger tdsLedger = new Ledger();
				tdsLedger.setName("TDS RECEIVABLE");
				ledgerRepository.save(tdsLedger);
				tdsRegister.setProduct(tdsLedger);
			}
			tdsRegister.setProduct(productTds);
			tdsRegister.getCreateDate();
			voucherRepository.save(tdsRegister);
			CreateTdsDto createTdsDto= new CreateTdsDto();
			createTdsDto.setOrganization(paymentRegister.getCompanyName());
			createTdsDto.setPaymentRegisterId(paymentRegister.getTransactionId());
			if(ledger!=null) {
				createTdsDto.setLedgerId(ledger.getId());
			}
			createTdsDto.setTdsAmount(paymentRegister.getTdsAmount());
			createTdsDto.setTdsPrecent(paymentRegister.getTdsPercent());
			createTdsDto.setTdsType("receivable");
			createTdsDto.setTotalPaymentAmount(paymentRegister.getProfessionalFees()+paymentRegister.getTdsAmount());
			tdsServiceImpl.createTds(createTdsDto);
		}


		flag=true;
	}else {
		//Voucher not exist
		System.out.println("Voucher not Exist");

		LedgerType group = ledgerTypeRepository.findByName("Sales");
		Organization organization = organizationRepository.findByName("corpseed");
		//ledger group not exist
		if(group==null) {
			group =new LedgerType();
			group.setName("Sales");
			ledgerTypeRepository.save(group);
		}
		if(group!=null) { 
			//ledger group exist

			System.out.println("test111111111112222222222");
			Ledger ledger = ledgerRepository.findByName(paymentRegister.getCompanyName());
			double totalEstimateAmount = Double.parseDouble(feignLeadClient.get("totalAmount")!=null?feignLeadClient.get("totalAmount").toString():"0");

			// == total amount register ==
			Voucher totalAmountRegister = new Voucher();
			totalAmountRegister.setVoucherType(vType);
			totalAmountRegister.setCreditDebit(true);
			totalAmountRegister.setCreateDate(new Date());
			totalAmountRegister.setDebitAmount(totalEstimateAmount);
			if(ledger==null) {
				ledger=createLedgerDataForCompany(feignLeadClient,paymentRegister);
			}

			totalAmountRegister.setLedger(ledger);
			Ledger prod =ledgerRepository.findByName(paymentRegister.getCompanyName());
			if(prod!=null) {
				totalAmountRegister.setLedger(prod);
				System.out.println("==== new product ======");

			}else {
				prod=new Ledger();
				System.out.println("==== new service name ======"+paymentRegister.getCompanyName());
				prod.setName(paymentRegister.getCompanyName());
				ledgerRepository.save(prod);
				totalAmountRegister.setLedger(prod);
			}


			totalAmountRegister.setLedger(ledger);
			Ledger p = ledgerRepository.findByName(paymentRegister.getServiceName());
			if(p!=null) {
				totalAmountRegister.setProduct(p);
			}else{
				p =new Ledger();
				p.setName(paymentRegister.getServiceName());
				ledgerRepository.save(p);
				totalAmountRegister.setProduct(p);

			}

			//				totalAmountRegister.setProduct(p);
			totalAmountRegister.setCreditDebit(true);
			voucherRepository.save(totalAmountRegister);
			System.out.println("555555555556666666");
			if(ledger==null) {
				ledger=createLedgerDataForCompany(feignLeadClient,paymentRegister);
			}
			// ledger exist
			if(ledger!=null) {
				System.out.println("test3333333311");

				Voucher v =new Voucher();
				v.setVoucherType(vType);
				double govermentfees =paymentRegister.getGovermentfees();
				double govermentGst =paymentRegister.getGovermentGst();
				double professionalFees =paymentRegister.getProfessionalFees();
				double professionalGst =paymentRegister.getProfesionalGst();
				double serviceCharge =paymentRegister.getProfesionalGst();
				double getServiceGst =paymentRegister.getServiceGst();
				double otherFees =paymentRegister.getOtherFees();
				double otherGst =paymentRegister.getOtherGst();
				double totalCredit=govermentfees+professionalFees+serviceCharge+otherFees;
				double totalCreditGst = govermentGst+professionalGst+getServiceGst+otherGst;
				v.setCreditAmount(totalCredit);
				v.setCreditDebit(true);
				v.setCreateDate(new Date());
				//					v.setIgstPresent(true);//cgst+sgst concept
				//					v.setIgst(totaDebitGst+"");		
				if(organization.getState().equals(v)) {
					double cgst=totalCreditGst/2;
					double sgst=totalCreditGst/2;
					v.setCgstSgstPresent(true);
					v.setCgst(cgst+"");  
					v.setSgst(sgst+"");
				}else {
					v.setIgstPresent(false);
					v.setIgst(totalCreditGst+"");
				}
				ledger=ledgerRepository.findByName(paymentRegister.getCompanyName());
				if(ledger==null) {
					ledger=new Ledger();
					ledger.setName(paymentRegister.getCompanyName());
					ledgerRepository.save(ledger);
				}

				v.setLedger(ledger);
				Ledger product = ledgerRepository.findByName("Professional Fees");
				if(product!=null) {
					v.setProduct(product);
				}else {
					product= new Ledger();
					product.setName("Professional Fees");
					ledgerRepository.save(product);
					v.setProduct(product);

				}

				v.setEstimateId(paymentRegister.getEstimateId());
				voucherRepository.save(v);


				if(paymentRegister.isTdsPresent()) {
					System.out.println("33333333333344444444444");

					Voucher tdsRegister =new Voucher();
					tdsRegister.setVoucherType(vType);
					tdsRegister.setCreditDebit(true);
					tdsRegister.setCreditAmount(paymentRegister.getTdsAmount());
					tdsRegister.setCreateDate(new Date());
					tdsRegister.setLedger(ledger);
					tdsRegister.setEstimateId(estimateId);
					Ledger productTds = ledgerRepository.findByName("TDS RECEIVABLE");
					if(productTds!=null) {
						tdsRegister.setProduct(productTds);
					}else{
						Ledger tdsLedger = new Ledger();
						tdsLedger.setName("TDS RECEIVABLE");
						ledgerRepository.save(tdsLedger);
						tdsRegister.setProduct(tdsLedger);
					}
					tdsRegister.setCreditDebit(true);
					tdsRegister.setProduct(productTds);
					tdsRegister.getCreateDate();
					voucherRepository.save(tdsRegister);
					CreateTdsDto createTdsDto= new CreateTdsDto();
					createTdsDto.setOrganization(paymentRegister.getCompanyName());
					createTdsDto.setPaymentRegisterId(paymentRegister.getTransactionId());
					createTdsDto.setLedgerId(ledger.getId());
					createTdsDto.setTdsAmount(paymentRegister.getTdsAmount());
					createTdsDto.setTdsPrecent(paymentRegister.getTdsPercent());
					createTdsDto.setTdsType("receivable");
					createTdsDto.setTotalPaymentAmount(paymentRegister.getProfessionalFees()+paymentRegister.getTdsAmount());
					tdsServiceImpl.createTds(createTdsDto);
				}


				flag=true;
			}
			//				else {
			//					Ledger l=createLedgerDataForCompany(feignLeadClient,paymentRegister);
			//					
			//					System.out.println("444444444444555555");
			//					totalEstimateAmount = Double.parseDouble(feignLeadClient.get("totalAmount")!=null?feignLeadClient.get("totalAmount").toString():"0");
			//
			//					// == total amount register ==
			//					totalAmountRegister = new Voucher();
			//					totalAmountRegister.setVoucherType(vType);
			//					totalAmountRegister.setCreditDebit(true);
			//					totalAmountRegister.setCreateDate(new Date());
			//					totalAmountRegister.setDebitAmount(totalEstimateAmount+"");
			//					totalAmountRegister.setLedger(l);
			//					prod =ledgerRepository.findByName(paymentRegister.getServiceName());
			//					if(prod!=null) {
			//						totalAmountRegister.setProduct(prod);
			//
			//					}else {
			//						prod=new Ledger();
			//						prod.setName(paymentRegister.getServiceName());
			//						ledgerRepository.save(prod);
			//						totalAmountRegister.setProduct(prod);
			//
			//					}
			//					totalAmountRegister.setLedger(l);
			//					totalAmountRegister.setProduct(ledger);
			//					totalAmountRegister.setCreditDebit(true);
			//					voucherRepository.save(totalAmountRegister);
			//					System.out.println("555555555556666666");
			//
			//					// ===== recived ammount register with gst ======
			//					Voucher v =new Voucher();
			//					v.setVoucherType(vType);
			//					double govermentfees =paymentRegister.getGovermentfees();
			//					double govermentGst =paymentRegister.getGovermentGst();
			//					double professionalFees =paymentRegister.getProfessionalFees();
			//					double professionalGst =paymentRegister.getProfesionalGst();
			//					double serviceCharge =paymentRegister.getServiceCharge();
			//					double getServiceGst =paymentRegister.getServiceGst();
			//					double otherFees =paymentRegister.getOtherFees();
			//					double otherGst =paymentRegister.getOtherGst();
			//					double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
			//					double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
			//					v.setCreditDebit(true);
			//					v.setCreditAmount(totalDebit+"");
			//					v.setIgstPresent(true);//cgst+sgst concept
			//					v.setIgst(totaDebitGst+"");			
			//					ledger=ledgerRepository.findByName(paymentRegister.getCompanyName());
			//					if(ledger==null) {
			//						ledger=new Ledger();
			//						ledger.setName(paymentRegister.getCompanyName());
			//						ledgerRepository.save(ledger);
			//					}
			//					
			//					v.setLedger(ledger);
			//					v.setCreateDate(new Date());
			//					
			//					Ledger product = ledgerRepository.findByName("Professional Fees");
			//					if(product!=null) {
			//						v.setProduct(product);
			//					}else {
			//						product= new Ledger();
			//						product.setName(paymentRegister.getServiceName());
			//						ledgerRepository.save(product);
			//						v.setProduct(product);
			//
			//					}
			//					v.setEstimateId(paymentRegister.getEstimateId());
			//					System.out.println("tttttttttttttttttttttttttttt");
			//
			//					voucherRepository.save(v);   
			//
			//					// ===== Tds register ======
			//					if(paymentRegister.isTdsPresent()) {
			//						System.out.println("55555555555555553333333333333");
			//
			//						Voucher tdsRegister =new Voucher();
			//						tdsRegister.setVoucherType(vType);
			//						tdsRegister.setCreditDebit(true);
			//						tdsRegister.setCreditAmount(paymentRegister.getTdsAmount()+"");
			//						tdsRegister.setCreateDate(new Date());
			//						tdsRegister.setLedger(ledger);
			//						tdsRegister.setEstimateId(estimateId);
			//						tdsRegister.setCreateDate(new Date());
			//						Ledger productTds = ledgerRepository.findByName("TDS RECEIVABLE");
			//						if(productTds!=null) {
			//							tdsRegister.setProduct(productTds);
			//						}else{
			//							Ledger tdsLedger = new Ledger();
			//							tdsLedger.setName("TDS RECEIVABLE");
			//							ledgerRepository.save(tdsLedger);
			//							tdsRegister.setProduct(tdsLedger);
			//						}
			//						tdsRegister.setProduct(productTds);
			//						tdsRegister.getCreateDate();
			//						voucherRepository.save(tdsRegister);
			//						CreateTdsDto createTdsDto= new CreateTdsDto();
			//						createTdsDto.setOrganization(paymentRegister.getCompanyName());
			//						createTdsDto.setPaymentRegisterId(paymentRegister.getTransactionId());
			//						if(ledger!=null) {
			//							createTdsDto.setLedgerId(ledger.getId());
			//						}
			//						createTdsDto.setTdsAmount(paymentRegister.getTdsAmount());
			//						createTdsDto.setTdsPrecent(paymentRegister.getTdsPercent());
			//						createTdsDto.setTdsType("receivable");
			//						createTdsDto.setTotalPaymentAmount(paymentRegister.getProfessionalFees()+paymentRegister.getTdsAmount());
			//						tdsServiceImpl.createTds(createTdsDto);
			//						System.out.println("tnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn");
			//
			//					}
			//
			//					flag=true;
			//				}	
		}			
	}
	return flag;


}


@Override
public List<InvoiceData> getAllInvoiceAccordingToUser(Long userId) {
	List<InvoiceData>invoice=invoiceDataRepository.findAll();
	return invoice;
}


@Override
public List<InvoiceData> getAllInvoiceForSales(Long userId) {
	List<InvoiceData>invoice=invoiceDataRepository.findAll();
	return invoice;
}


@Override
public PaymentRegister createPurchaseOrder(CreatePurchaseOrderDto createPurchaseOrderDto) {

	//		User u=userRepository.findById(createPurchaseOrderDto.getCreatedById()).get();
	PaymentRegister paymentRegister =new PaymentRegister();
	paymentRegister.setRegisterType("Purchase Order");
	paymentRegister.setPurchaseNumber(createPurchaseOrderDto.getPurchaseNumber());
	if(createPurchaseOrderDto.getPurchaseAttach()!=null && createPurchaseOrderDto.getPurchaseAttach().size()>0) {
		paymentRegister.setPurchaseAttach(createPurchaseOrderDto.getPurchaseAttach().get(0));

	}
	paymentRegister.setEstimateNo(createPurchaseOrderDto.getEstimateNo());
	paymentRegister.setStatus("initiated");
	paymentRegister.setPaymentTerm(createPurchaseOrderDto.getPaymentTerm());
	paymentRegister.setComment(createPurchaseOrderDto.getComment());
	paymentRegister.setUpdateDate(new Date().toString());
	paymentRegister.setCreatedById(createPurchaseOrderDto.getCreatedById());
	paymentRegister.setPurchaseDate(new Date());
	if(createPurchaseOrderDto.getCreatedById()!=null) {
		User user = userRepository.findById(createPurchaseOrderDto.getCreatedById()).get();
		paymentRegister.setCreatedByUser(user);
		paymentRegister.setCreatedById(createPurchaseOrderDto.getCreatedById());

	}
	paymentRegister.setPurchaseDate(new Date());
	paymentRegister.setEstimateId(createPurchaseOrderDto.getEstimateId());
	paymentRegister.setLeadId(createPurchaseOrderDto.getLeadId());
	paymentRegister.setServiceName(createPurchaseOrderDto.getServiceName());
	paymentRegister.setCompanyId(createPurchaseOrderDto.getCompanyId());
	paymentRegister.setCompanyName(createPurchaseOrderDto.getCompanyName());
	paymentRegisterRepository.save(paymentRegister);

	return paymentRegister;
}


@Override
public List<Map<String,Object>> getAllPurchaseOrder(Long userId) {
	List<PaymentRegister>paymentRegister=paymentRegisterRepository.findAllByRegisterType("Purchase Order");
	List<Map<String,Object>>list=new ArrayList<>();
	for(PaymentRegister p: paymentRegister) {
		Map<String,Object>map=new HashMap<>();
		map.put("id", p.getId());
		map.put("name", p.getServiceName());
		map.put("registerType", p.getRegisterType());
		map.put("purchaseNumber", p.getPurchaseNumber());
		map.put("purchaseAttach", p.getPurchaseAttach());
		map.put("paymentTerm", p.getPaymentTerm());
		map.put("Comment", p.getComment());
		map.put("updatedDate", p.getUpdateDate());
		map.put("CreatedById", p.getCreatedById());
		map.put("estimateId", p.getEstimateId());
		map.put("leadId", p.getLeadId());

		map.put("serviceName", p.getServiceName());
		map.put("assigneeId", p.getCreatedByUser()!=null?p.getCreatedByUser().getId():null);
		map.put("assigneeName", p.getCreatedByUser()!=null?p.getCreatedByUser().getFullName():null);
		map.put("assigneeEmail", p.getCreatedByUser()!=null?p.getCreatedByUser().getEmail():null);

		map.put("status", p.getStatus());

		list.add(map);

	}
	return list;
}


@Override
public List<Map<String, Object>> getAllPaymentRegisterWithCompany(Long userId) {
	List<String>status=Arrays.asList("initiated");

	List<PaymentRegister> paymentRegisterList = paymentRegisterRepository.findAllByStatus(status);
	List<Long>companyList=paymentRegisterList.stream().map(i->i.getCompanyId()).collect(Collectors.toList());
	System.out.println("map    ......"+companyList.size());

	//	    Map<Object, Object> map = paymentRegisterList.stream().collect(Collectors.toMap(i->i.getCompanyId(), i->i));
	Map<Object, List<PaymentRegister>> m=new HashMap<>();
	for(PaymentRegister p:paymentRegisterList) {

		if(m.get(p.getCompanyId())!=null) {
			List<PaymentRegister> d = m.get(p.getCompanyId());
			d.add(p);
			m.put(p.getCompanyId(), d);
		}else {
			List<PaymentRegister> d = new ArrayList<>();
			d.add(p);
			m.put(p.getCompanyId(), d);
		}

	}
	//	    System.out.println("map    ......"+map);
	GetAllCompanyDto getAllCompanyDto =new GetAllCompanyDto();
	System.out.println("companyList ...."+companyList);

	getAllCompanyDto.setIds(companyList);
	System.out.println("test ...."+getAllCompanyDto.getIds());

	List<Map<String, Object>> test = leadFeignClient.getAllCompanyByIdsForFeign(getAllCompanyDto);
	System.out.println("test  t1...."+test.size());
	List<Map<String, Object>>res=new ArrayList<>();
	for(Map<String, Object> t:test) {
		//	    	Long id = t.get("id")!=null?Long.valueOf(t.get("id")):null;
		Long l=Long.parseLong(t.get("id")!=null?t.get("id").toString():"0");
		List<Object>pay=new ArrayList<>();
		if(l!=null) {
			System.out.println("Final");
			pay.addAll( m.get(l));
			t.put("paymentRegister", pay);
		};
		res.add(t);

	}
	return res;
}


public Boolean paymentApproveAndDisapprovedTest(UpdatePaymentDto updatePaymentDto) {
	//hit project and company creation
	//create create group
	//create ledger
	//create voucher
	Map<String, Object> feignLeadClient = LeadFeignClient.getEstimateById(updatePaymentDto.getEstimateId());

	LedgerType group = ledgerTypeRepository.findByName(updatePaymentDto.getPaymentType());
	if(group!=null) {
		if(updatePaymentDto.getRegisterBy().equals("SALES")) {
			Ledger ledger = ledgerRepository.findByName(updatePaymentDto.getCompanyName());
			if(ledger!=null) {
				Voucher v =new Voucher();
				double govermentfees =updatePaymentDto.getGovermentfees();
				double govermentGst =updatePaymentDto.getGovermentGst();;
				double professionalFees =updatePaymentDto.getProfessionalFees();
				double professionalGst =updatePaymentDto.getProfesionalGst();
				double serviceCharge =updatePaymentDto.getServiceCharge();
				double getServiceGst =updatePaymentDto.getServiceGst();
				double otherFees =updatePaymentDto.getOtherFees();
				double otherGst =updatePaymentDto.getOtherGst();

				double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
				double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
				v.setDebitAmount(totalDebit);
				v.setIgstPresent(true);//cgst+sgst concept
				v.setIgst(totaDebitGst+"");			
				v.setLedger(ledger);
				v.setEstimateId(updatePaymentDto.getEstimateId());

				voucherRepository.save(v);
			}else {
				Voucher v =new Voucher();
				double govermentfees =updatePaymentDto.getGovermentfees();
				double govermentGst =updatePaymentDto.getGovermentGst();
				double professionalFees =updatePaymentDto.getProfessionalFees();
				double professionalGst =updatePaymentDto.getProfesionalGst();
				double serviceCharge =updatePaymentDto.getServiceCharge();
				double getServiceGst =updatePaymentDto.getServiceGst();
				double otherFees =updatePaymentDto.getOtherFees();
				double otherGst =updatePaymentDto.getOtherGst();
				double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
				double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
				v.setDebitAmount(totalDebit);
				v.setIgstPresent(true);//cgst+sgst concept
				v.setIgst(totaDebitGst+"");

				v.setLedger(ledger);
				v.setEstimateId(updatePaymentDto.getEstimateId());

				voucherRepository.save(v);
			}

		}else {

		}

	}else {
		if(updatePaymentDto.getPaymentType().equals("SALES")) {


			LedgerType g = new LedgerType();
			g.setName(updatePaymentDto.getPaymentType());

			//				Map<String, Object> feignLeadClient = LeadFeignClient.getEstimateById(updatePaymentDto.getEstimateId());
			Ledger l=createLedgerData(feignLeadClient,updatePaymentDto);

			Voucher v =new Voucher();
			double govermentfees =updatePaymentDto.getGovermentfees();
			double govermentGst =updatePaymentDto.getGovermentGst();
			double professionalFees =updatePaymentDto.getProfessionalFees();
			double professionalGst =updatePaymentDto.getProfesionalGst();
			double serviceCharge =updatePaymentDto.getServiceCharge();
			double getServiceGst =updatePaymentDto.getServiceGst();
			double otherFees =updatePaymentDto.getOtherFees();
			double otherGst =updatePaymentDto.getOtherGst();
			double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
			double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
			v.setDebitAmount(totalDebit);
			v.setIgstPresent(true);//cgst+sgst concept
			v.setIgst(totaDebitGst+"");

			v.setLedger(l);
			voucherRepository.save(v);

			//also project api hit 

		}else {

		}

	}


	Ledger ledger=ledgerRepository.findByName(updatePaymentDto.getCompanyName());
	ledger.setName(updatePaymentDto.getCompanyName());

	return null;
}
public Boolean paymentApproveAndDisapprovedV3(UpdatePaymentDto updatePaymentDto) {

	Map<String, Object> feignLeadClient = LeadFeignClient.getEstimateById(updatePaymentDto.getEstimateId());

	LedgerType group = ledgerTypeRepository.findByName(updatePaymentDto.getPaymentType());
	if(group!=null) {
		if(updatePaymentDto.getRegisterBy().equals("SALES")) {
			Ledger ledger = ledgerRepository.findByName(updatePaymentDto.getCompanyName());
			if(ledger!=null) {
				Voucher v =new Voucher();
				double govermentfees =updatePaymentDto.getGovermentfees();
				double govermentGst =updatePaymentDto.getGovermentGst();;
				double professionalFees =updatePaymentDto.getProfessionalFees();
				double professionalGst =updatePaymentDto.getProfesionalGst();
				double serviceCharge =updatePaymentDto.getServiceCharge();
				double getServiceGst =updatePaymentDto.getServiceGst();
				double otherFees =updatePaymentDto.getOtherFees();
				double otherGst =updatePaymentDto.getOtherGst();

				double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
				double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
				v.setDebitAmount(totalDebit);
				v.setIgstPresent(true);//cgst+sgst concept
				v.setIgst(totaDebitGst+"");			
				v.setLedger(ledger);
				v.setEstimateId(updatePaymentDto.getEstimateId());

				voucherRepository.save(v);
			}else {
				String primaryPinCode = feignLeadClient.get("primaryPinCode")!=null?feignLeadClient.get("primaryPinCode").toString():null;
				String country = feignLeadClient.get("country")!=null?feignLeadClient.get("country").toString():null;
				String address = feignLeadClient.get("address")!=null?feignLeadClient.get("address").toString():null;
				String state=feignLeadClient.get("state")!=null?feignLeadClient.get("state").toString():null;
				//					String email,String pinCode,String country,String state,String Address
				ledger=createLedger(updatePaymentDto.getCompanyName(),group.getId(),null,primaryPinCode,country,state,address);

				Voucher v =new Voucher();
				double govermentfees =updatePaymentDto.getGovermentfees();
				double govermentGst =updatePaymentDto.getGovermentGst();
				double professionalFees =updatePaymentDto.getProfessionalFees();
				double professionalGst =updatePaymentDto.getProfesionalGst();
				double serviceCharge =updatePaymentDto.getServiceCharge();
				double getServiceGst =updatePaymentDto.getServiceGst();
				double otherFees =updatePaymentDto.getOtherFees();
				double otherGst =updatePaymentDto.getOtherGst();
				double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
				double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
				v.setDebitAmount(totalDebit);
				v.setIgstPresent(true);//cgst+sgst concept
				v.setIgst(totaDebitGst+"");

				v.setLedger(ledger);
				v.setEstimateId(updatePaymentDto.getEstimateId());

				voucherRepository.save(v);
			}

		}else {

		}

	}else {
		if(updatePaymentDto.getPaymentType().equals("SALES")) {


			LedgerType g = new LedgerType();
			g.setName(updatePaymentDto.getPaymentType());
			ledgerTypeRepository.save(g);
			Ledger l=createLedgerData(feignLeadClient,updatePaymentDto);

			Voucher v =new Voucher();
			double govermentfees =updatePaymentDto.getGovermentfees();
			double govermentGst =updatePaymentDto.getGovermentGst();
			double professionalFees =updatePaymentDto.getProfessionalFees();
			double professionalGst =updatePaymentDto.getProfesionalGst();
			double serviceCharge =updatePaymentDto.getServiceCharge();
			double getServiceGst =updatePaymentDto.getServiceGst();
			double otherFees =updatePaymentDto.getOtherFees();
			double otherGst =updatePaymentDto.getOtherGst();
			double totalDebit=govermentfees+professionalFees+serviceCharge+otherFees;
			double totaDebitGst = govermentGst+professionalGst+getServiceGst+otherGst;
			v.setDebitAmount(totalDebit);
			v.setIgstPresent(true);//cgst+sgst concept
			v.setIgst(totaDebitGst+"");

			v.setLedger(l);
			voucherRepository.save(v);

			//also project api hit 

		}
	}


	Ledger ledger=ledgerRepository.findByName(updatePaymentDto.getCompanyName());
	ledger.setName(updatePaymentDto.getCompanyName());

	return null;
}


//create Ledger
public Ledger createLedger(String name,Long ledgerTypeId,String email,String pinCode,String country,String state,String Address) {

	Boolean flag=false;
	Ledger l = new Ledger();
	l.setName(name);
	l.setEmail(email);
	l.setAddress(Address);
	l.setState(state);
	l.setCountry(country);
	l.setPin(pinCode);

	LedgerType ledgerType = ledgerTypeRepository.findById(ledgerTypeId).get();
	l.setLedgerType(ledgerType);


	if(ledgerType.isGstRateDetailPresent()) {
		l.setGstRateDetailPresent(ledgerType.isGstRateDetailPresent());
		l.setGstRateDetails(ledgerType.getGstRateDetails());
		Organization org = organizationRepository.findByName("corpseed");
		if(org!=null) {
			String stated = org.getState();
			if(state.equalsIgnoreCase(stated)) {
				String gstRateDetails=ledgerType.getGstRateDetails();
				double gst = Double.parseDouble(gstRateDetails);
				double cgst=gst/2;
				double sgst=gst-cgst;
				//					ledgerDto.get
				l.setCgst(cgst+"");
				l.setSgst(sgst+"");
				l.setCgstSgstPresent(true);

				l.setGstRateDetails(gstRateDetails);
			}else {
				String gstRateDetails=ledgerType.getGstRateDetails();
				l.setIgstPresent(true);
				l.setGstRateDetails(gstRateDetails);
				l.setIgst(gstRateDetails);


			}
		}else {
			String gstRateDetails=ledgerType.getGstRateDetails();
			l.setGstRateDetails(gstRateDetails);
			l.setIgst(gstRateDetails);
		}
		l.setTaxabilityType(ledgerType.getTaxabilityType());
		l.setGstRates(ledgerType.getGstRateDetails());
	}
	//

	if(ledgerType.isBankAccountPresent()){
		l.setBankAccountPrsent(ledgerType.isBankAccountPresent());
		l.setAccountHolderName(ledgerType.getAccountHolderName());
		l.setAccount(ledgerType.getAccountNo());
		l.setIfscCode(ledgerType.getIfscCode());
		l.setSwiftCode(ledgerType.getSwiftCode());
		l.setBankName(ledgerType.getBankName());
		l.setBranch(ledgerType.getBranch());
	}
	ledgerRepository.save(l);
	flag=true;
	return l;



}

public Boolean paymentApproveAndDisapprovedV5(Long paymentRegisterId ,Long estimateId) {

	Boolean flag=false;
	Map<String, Object> feignLeadClient = LeadFeignClient.getEstimateById(estimateId);
	String ledgerName = (String)feignLeadClient.get("companyName");
	System.out.println("test . . "+ledgerName);
	List<Voucher> voucherList=voucherRepository.findByEstimateId(estimateId);
	PaymentRegister paymentRegister = paymentRegisterRepository.findById(paymentRegisterId).get();
	//Group
	VoucherType vType=voucherTypeRepo.findByName(paymentRegister.getRegisterBy());
	if(vType ==null) {
		vType=new VoucherType();
		vType.setName(paymentRegister.getRegisterBy());
		vType.setDeleted(false);
		voucherTypeRepo.save(vType);
	}

	// Voucher Exist
	if(voucherList!=null &&voucherList.size()>0) {  // Already created
		System.out.println("Voucher Exist");
		Ledger ledger = ledgerRepository.findByName(ledgerName);
		if(ledger==null) {
			ledger=createLedgerDataV5( feignLeadClient,paymentRegister.getProfesionalGst());
		}
		Organization organization = organizationRepository.findByName("corpseed");

		// voucher
		Voucher v =new Voucher();
		v.setVoucherType(vType);
		double govermentfees =paymentRegister.getGovermentfees();
		double govermentGst =paymentRegister.getGovermentGst();

		double professionalFees =paymentRegister.getProfessionalFees();
		double professionalGst =paymentRegister.getProfesionalGst();

		double serviceCharge =paymentRegister.getServiceCharge();
		double getServiceGst =paymentRegister.getServiceGst();

		double otherFees =paymentRegister.getOtherFees();
		double otherGst =paymentRegister.getOtherGst();

		v.setCreateDate(new Date());
		v.setVoucherType(vType);
		System.out.println("test111111");
		double totalCredit=govermentfees+professionalFees+serviceCharge+otherFees;
		double totalProfessionalCredit=govermentfees+professionalFees+serviceCharge+otherFees;

		double totalCreditGst = govermentGst+professionalGst+getServiceGst+otherGst;
		String org=(String)feignLeadClient.get("state");

		v.setCreditAmount(totalCredit);
		if(organization!=null && organization.getState()!=null && organization.getState().equals(org)) {
			System.out.println("test222222");
			double cgst=totalCreditGst/2;
			double sgst=totalCreditGst/2;
			v.setCgstSgstPresent(true);
			v.setCgst(cgst+"");
			v.setSgst(sgst+"");
		}else {
			System.out.println("test333333");
			v.setIgstPresent(false);
			v.setIgst(totalCreditGst+"");
		}
		//			Ledger product = ledgerRepository.findByName(paymentRegister.getServiceName());
		Ledger product = ledgerRepository.findByName("Professional Fees");

		if(product!=null) {
			System.out.println("test4444444..."+product.getName());
			//				v.setProduct(product);
		}else {
			System.out.println("test5555555");
			product= new Ledger();
			product.setName("Professional Fees");
			ledgerRepository.save(product);
			v.setProduct(product);
		}
		v.setLedger(ledger);
		v.setEstimateId(paymentRegister.getEstimateId());
		voucherRepository.save(v);


		//tds concept
		if(paymentRegister.isTdsPresent()) {
			System.out.println("test666666666");
			Voucher tdsRegister =new Voucher();
			tdsRegister.setVoucherType(vType);
			tdsRegister.setCreditDebit(true);
			tdsRegister.setCreateDate(new Date());
			tdsRegister.setCreditAmount(paymentRegister.getTdsAmount());
			Ledger l = ledgerRepository.findByName(paymentRegister.getCompanyName());
			if(l!=null) {
				tdsRegister.setLedger(l);
			}else {
				l=new Ledger();
				l.setName(paymentRegister.getCompanyName());
				ledgerRepository.save(l);
				tdsRegister.setLedger(l);
			}
			tdsRegister.setCreateDate(new Date());
			tdsRegister.setLedger(ledger);
			tdsRegister.setEstimateId(estimateId);
			Ledger productTds = ledgerRepository.findByName("TDS RECEIVABLE");
			if(productTds!=null) {
				System.out.println("test77777777777");
				tdsRegister.setProduct(productTds);
			}else{
				System.out.println("test888888888888");
				Ledger tdsLedger = new Ledger();
				tdsLedger.setName("TDS RECEIVABLE");
				ledgerRepository.save(tdsLedger);
				tdsRegister.setProduct(tdsLedger);
			}
			tdsRegister.setProduct(productTds);
			tdsRegister.getCreateDate();
			voucherRepository.save(tdsRegister);
			CreateTdsDto createTdsDto= new CreateTdsDto();
			createTdsDto.setOrganization(paymentRegister.getCompanyName());
			createTdsDto.setPaymentRegisterId(paymentRegister.getTransactionId());
			if(ledger!=null) {
				createTdsDto.setLedgerId(ledger.getId());
			}
			createTdsDto.setTdsAmount(paymentRegister.getTdsAmount());
			createTdsDto.setTdsPrecent(paymentRegister.getTdsPercent());
			createTdsDto.setTdsType("receivable");
			createTdsDto.setTotalPaymentAmount(paymentRegister.getProfessionalFees()+paymentRegister.getTdsAmount());
			tdsServiceImpl.createTds(createTdsDto);
		}


		flag=true;
	}else {
		//Voucher not exist
		System.out.println("Voucher not Exist");

		LedgerType group = ledgerTypeRepository.findByName("Sales");
		Organization organization = organizationRepository.findByName("corpseed");
		//ledger group not exist
		if(group==null) {
			group =new LedgerType();
			group.setName("Sales");
			ledgerTypeRepository.save(group);
		}
		if(group!=null) { 
			//ledger group exist

			System.out.println("test111111111112222222222");  //professionalFees
			Ledger ledger = ledgerRepository.findByName(paymentRegister.getCompanyName());
			double totalEstimateAmount = Double.parseDouble(feignLeadClient.get("totalAmount")!=null?feignLeadClient.get("totalAmount").toString():"0");

			double proFees = Double.parseDouble(feignLeadClient.get("professionalFees")!=null?feignLeadClient.get("professionalFees").toString():"0");
			double gstAmount = Double.parseDouble(feignLeadClient.get("gstAmount")!=null?feignLeadClient.get("gstAmount").toString():"0");
			double gst = Double.parseDouble(feignLeadClient.get("gst")!=null?feignLeadClient.get("gst").toString():"0");

			// == total amount register ==total product amount register
			Voucher totalAmountRegister = new Voucher();
			totalAmountRegister.setVoucherType(vType);
			totalAmountRegister.setCreditDebit(true);
			totalAmountRegister.setCreateDate(new Date());
			totalAmountRegister.setDebitAmount(proFees);

			totalAmountRegister.setTotalAmount(totalEstimateAmount);
			totalAmountRegister.setIgst(gst+"");
			//				totalAmountRegister.setIgstDebitAmount(gstAmount);
			//company
			if(ledger==null) {
				ledger=createLedgerDataForCompany(feignLeadClient,paymentRegister);
			}
			totalAmountRegister.setLedger(ledger);
			Ledger prod =ledgerRepository.findByName(paymentRegister.getServiceName());
			if(prod!=null) {
				totalAmountRegister.setLedger(prod);
				System.out.println("==== new product ======");
			}else {
				prod=new Ledger();
				System.out.println("==== new service name ======"+paymentRegister.getCompanyName());
				prod.setName(paymentRegister.getCompanyName());
				ledgerRepository.save(prod);
				totalAmountRegister.setLedger(prod);
			}
			totalAmountRegister.setLedger(ledger);
			Ledger p = ledgerRepository.findByName(paymentRegister.getServiceName());
			if(p!=null) {
				totalAmountRegister.setProduct(p);
			}else{
				p =new Ledger();
				p.setName(paymentRegister.getServiceName());
				ledgerRepository.save(p);
				totalAmountRegister.setProduct(p);
			}
			//				totalAmountRegister.setProduct(p);
			totalAmountRegister.setCreditDebit(true);
			voucherRepository.save(totalAmountRegister);


			System.out.println("555555555556666666");
			if(ledger==null) {
				ledger=createLedgerDataForCompany(feignLeadClient,paymentRegister);
			}
			// ledger exist then Professional fee
			if(ledger!=null) {
				System.out.println("test3333333311");

				Voucher v =new Voucher();
				v.setVoucherType(vType);
				double govermentfees =paymentRegister.getGovermentfees();

				double professionalFees =paymentRegister.getProfessionalFees();
				double professionalGst =paymentRegister.getProfesionalGst();

				double serviceCharge =paymentRegister.getServiceCharge();
				double otherFees =paymentRegister.getOtherFees();
				double totalCredit=govermentfees+professionalFees+serviceCharge+otherFees;//  create same gov fees
				v.setCreditAmount(totalCredit);

				totalCredit=totalCredit+ paymentRegister.getProfessionalGstAmount();
				v.setTotalAmount(totalCredit);

				double totalCreditGst = professionalGst;

				v.setCreditAmount(totalCredit);
				v.setCreditDebit(true);
				v.setCreateDate(new Date());
				//					v.setIgstPresent(true);//cgst+sgst concept
				//					v.setIgst(totaDebitGst+"");	
				String org=(String)feignLeadClient.get("state");
				System.out.println("totalCreditGst   . ...."+totalCreditGst);
				if(organization.getState().equals(org)) {
					double cgst=totalCreditGst/2;
					double sgst=totalCreditGst/2;
					v.setCgstSgstPresent(true);
					v.setCgst(cgst+"");  
					v.setSgst(sgst+"");
				}else {
					v.setIgstPresent(true);
					v.setIgst(totalCreditGst+"");
				}
				ledger=ledgerRepository.findByName(paymentRegister.getCompanyName());
				if(ledger==null) {
					ledger=new Ledger();
					ledger.setName(paymentRegister.getCompanyName());
					ledgerRepository.save(ledger);
				}

				v.setLedger(ledger);
				Ledger product = ledgerRepository.findByName("Professional Fees");
				if(product!=null) {
					v.setProduct(product);
				}else {
					product= new Ledger();
					product.setName("Professional Fees");
					ledgerRepository.save(product);
					v.setProduct(product);

				}

				v.setEstimateId(paymentRegister.getEstimateId());
				voucherRepository.save(v);


				if(paymentRegister.isTdsPresent()) {
					System.out.println("33333333333344444444444");

					Voucher tdsRegister =new Voucher();
					tdsRegister.setVoucherType(vType);
					tdsRegister.setCreditDebit(true);
					tdsRegister.setCreditAmount(paymentRegister.getTdsAmount());
					tdsRegister.setCreateDate(new Date());
					tdsRegister.setLedger(ledger);
					tdsRegister.setEstimateId(estimateId);
					Ledger productTds = ledgerRepository.findByName("TDS RECEIVABLE");
					if(productTds!=null) {
						tdsRegister.setProduct(productTds);
					}else{
						Ledger tdsLedger = new Ledger();
						tdsLedger.setName("TDS RECEIVABLE");
						ledgerRepository.save(tdsLedger);
						tdsRegister.setProduct(tdsLedger);
					}
					tdsRegister.setCreditDebit(true);
					tdsRegister.setProduct(productTds);
					tdsRegister.getCreateDate();
					voucherRepository.save(tdsRegister);
					CreateTdsDto createTdsDto= new CreateTdsDto();
					createTdsDto.setOrganization(paymentRegister.getCompanyName());
					createTdsDto.setPaymentRegisterId(paymentRegister.getTransactionId());
					createTdsDto.setLedgerId(ledger.getId());
					createTdsDto.setTdsAmount(paymentRegister.getTdsAmount());
					createTdsDto.setTdsPrecent(paymentRegister.getTdsPercent());
					createTdsDto.setTdsType("receivable");
					createTdsDto.setTotalPaymentAmount(paymentRegister.getProfessionalFees()+paymentRegister.getTdsAmount());
					tdsServiceImpl.createTds(createTdsDto);
				}


				flag=true;
			}

		}			
	}
	return flag;


}

public Ledger createLedgerDataV5(Map<String, Object> feignLeadClient,double profesionalGst) {

	Boolean flag=false;
	Ledger l = new Ledger();
	l.setName(feignLeadClient.get("name").toString());
	l.setEmail(feignLeadClient.get("name").toString());
	l.setAddress(feignLeadClient.get("address").toString());
	l.setState(feignLeadClient.get("State").toString());
	l.setCountry(feignLeadClient.get("country").toString());
	l.setPin(feignLeadClient.get("pin").toString());

	//		ledger gst part
	if(l.isGstRateDetailPresent()) {
		l.setGstRateDetailPresent(l.isGstRateDetailPresent());
		l.setGstRateDetails(l.getGstRateDetails());
		Organization org = organizationRepository.findByName("corpseed");
		if(org!=null) {
			String state = org.getState();
			String eState=feignLeadClient.get("state").toString();
			if(state.equalsIgnoreCase(eState)) {
				double gstRateDetails=profesionalGst;//gst percent from company
				double gst =gstRateDetails;
				double cgst=gst/2;
				double sgst=gst-cgst;
				l.setCgst(cgst+"");
				l.setSgst(sgst+"");
				l.setCgstSgstPresent(true);

				l.setGstRateDetails(gstRateDetails+"");
			}else {
				double gstRateDetails=profesionalGst;//gst percent from company
				l.setIgstPresent(true);
				l.setGstRateDetails(gstRateDetails+"");
				l.setIgst(gstRateDetails+"");


			}
		}else {
			double gstRateDetails=profesionalGst;//gst percent from company
			l.setGstRateDetails(gstRateDetails+"");
			l.setIgst(gstRateDetails+"");
		}
		//					l.setTaxabilityType(ledgerDto.getTaxabilityType());
		l.setGstRates(profesionalGst+"");
	}

	ledgerRepository.save(l);
	return null;


}


@Override
public List<Map<String,Object>> getAllPaymentRegisterWithPage(int page, int size, String status) {
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
	List<PaymentRegister> paymentRegisterList = paymentRegisterRepository.findAllByStatus(pageableDesc,statusList);
	List<Map<String,Object>>result=new ArrayList<>();
	for(PaymentRegister p :paymentRegisterList) {
		Map<String, Object> map = new HashMap<>();

		map.put("id", p.getId());
		map.put("leadId", p.getLeadId());
		map.put("estimateId", p.getEstimateId());
		map.put("comment", p.getComment());

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
		map.put("remark", p.getRemark());
		map.put("paymentDate", p.getPaymentDate());
		map.put("estimateNo", p.getEstimateNo()!=null?p.getEstimateNo():"EST00"+p.getEstimateId());
		map.put("status", p.getStatus());

		map.put("docPersent", p.getDocPersent());
		map.put("filingPersent", p.getFilingPersent());
		map.put("liasoningPersent", p.getLiasoningPersent());
		map.put("certificatePersent", p.getCertificatePersent());

		map.put("companyName", p.getCompanyName());
		map.put("companyId", p.getCompanyId());
		map.put("updateDate", p.getUpdateDate());
		map.put("productType", p.getProductType());
		map.put("doc", p.getFileData());

		map.put("approvedById", p.getApprovedById());
		map.put("approveDate", p.getApproveDate());

		//			Map<String, Object>remAmount= remainingAmountAndPaidAmount(p.getEstimateId());
		if("Product".equals(p.getProductType())) {
			Map<String, Object>remAmount= remainingAmountAndPaidAmountProduct(p.getEstimateId());

			Object dueAmount = remAmount.get("totalRemainingAmount");
			Object orderAmount = remAmount.get("estimateAmount");
			Object estimateCreateDate = remAmount.get("estimateDate");
			Object totAmount = remAmount.get("totAmount");
			Object paidAmount = remAmount.get("paidAmount");

			//paidAmount
			map.put("dueAmount", dueAmount);
			map.put("txnAmount", p.getTotalAmount());
			map.put("orderAmount", orderAmount);
			map.put("estimateCreateDate", estimateCreateDate);
			map.put("dueAmount", dueAmount);
			map.put("paidAmount", paidAmount);

			map.put("contactName", remAmount.get("contactName"));
			map.put("contactEmails",  remAmount.get("contactName"));
			map.put("contactNo",  remAmount.get("contactNo"));

		}else {
			Map<String, Object>remAmount= remainingAmountAndPaidAmount(p.getEstimateId());

			Object dueAmount = remAmount.get("totalRemainingAmount");
			Object orderAmount = remAmount.get("estimateAmount");
			Object estimateCreateDate = remAmount.get("estimateDate");
			Object totAmount = remAmount.get("totAmount");
			Object paidAmount = remAmount.get("paidAmount");

			//paidAmount
			map.put("dueAmount", dueAmount);
			map.put("txnAmount", p.getTotalAmount());
			map.put("orderAmount", orderAmount);
			map.put("estimateCreateDate", estimateCreateDate);
			map.put("dueAmount", dueAmount);
			map.put("paidAmount", paidAmount);

			map.put("contactName", remAmount.get("contactName"));
			map.put("contactEmails",  remAmount.get("contactName"));
			map.put("contactNo",  remAmount.get("contactNo"));
		}

		map.put("assigneeId", p.getCreatedByUser()!=null?p.getCreatedByUser().getId():null);
		map.put("assigneeName", p.getCreatedByUser()!=null?p.getCreatedByUser().getFullName():null);
		map.put("assigneeEmail", p.getCreatedByUser()!=null?p.getCreatedByUser().getEmail():null);

		map.put("status", p.getStatus());
		result.add(map);

	}
	return result;
	//		return paymentRegisterList;
}


@Override
public Long getAllPaymentRegisterCount(String status) {

	List<String>statusList=new ArrayList<>();
	if(status.equals("all")) {
		statusList=Arrays.asList("initiated","approved","hold","disapproved");
	}else {
		statusList.add(status);
	}
	long paymentRegisterList = paymentRegisterRepository.findCountByStatus(statusList);
	return paymentRegisterList;
}


@Override
public List<PaymentRegister> getAllPaymentRegisterByUser(int page, int size,Long userId, String status) {

	Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
	// For descending order, use:
	Pageable pageableDesc = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
	Optional<User> user = userRepository.findById(userId);
	List<String>statusList=new ArrayList<>();
	if(status.equals("all")) {
		statusList=Arrays.asList("initiated","approved","hold","disapproved");
	}else {
		statusList.add(status);
	}
	List<PaymentRegister> paymentRegisterList =new ArrayList<>();
	if(user.get()!=null &&user.get().getRole().contains("ADMIN")) {
		paymentRegisterList = paymentRegisterRepository.findAllByStatus(pageable,statusList);

	}else {
		paymentRegisterList = paymentRegisterRepository.findAllByStatus(pageable,statusList);
	}
	return paymentRegisterList;
}


@Override
public List<Map<String,Object>> getAllInvoice(Long userId, int page, int size) {
	Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
	// For descending order, use:
	Pageable pageableDesc = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
	Optional<User> user = userRepository.findById(userId);

	List<InvoiceData>invoice=invoiceDataRepository.findAll(pageableDesc).getContent();
	List<Map<String,Object>>result=new ArrayList<>();
	for(InvoiceData invoiceData:invoice){
		Map<String,Object>map=new HashMap<>();
		map.put("id", invoiceData.getId());
		map.put("invoiceNo", invoiceData.getInvoiceNo());
		map.put("service", invoiceData.getProductName());
		map.put("clientid", invoiceData.getPrimaryContactId());
		map.put("clientName", invoiceData.getPrimaryContactName());
		map.put("clientEmail", invoiceData.getPrimaryContactemails());
		map.put("companyName", invoiceData.getCompanyName());
		map.put("date", invoiceData.getCreateDate());
		map.put("txnAmount", invoiceData.getTotalAmount());
		map.put("addedBy", invoiceData.getAssignee());
		map.put("termOfDelivery", invoiceData.getTermOfDelivery());

		map.put("profGst", invoiceData.getProfesionalGst());

		map.put("gstNo", invoiceData.getGstNo());
		Double prof = Double.valueOf(invoiceData.getProfessionalFees());
		Double gst =  Double.valueOf(invoiceData.getProfesionalGst());
		if(prof!=null&& prof!=0) {
			double gstAmount = ((gst*prof)/100);

			map.put("gstAmount", gstAmount);
		}

		map.put("hsnNo", invoiceData.getHsnSacDetails());

		result.add(map);
	}

	return result;
}
//================================================================

@Override
public long getAllInvoiceCount(Long userId) {
	long invoice=invoiceDataRepository.findAllCount();
	return invoice;
}
@Transactional
public Boolean allPaymentApprovedV3(Long id) {
	Boolean flag=true;
	PaymentRegister paymentRegister = paymentRegisterRepository.findById(id).get();
	Boolean invoice = createInvoice(paymentRegister.getEstimateId());
	Boolean payment = paymentApproveAndDisapprovedV5(paymentRegister.getId() ,paymentRegister.getEstimateId());
	createUnbilled(paymentRegister);
	leadFeignClient.createProject(paymentRegister.getEstimateId());
	if(invoice && payment) {
		flag=true;
	}
	return flag;
}

public Unbilled createUnbilled(PaymentRegister paymentRegister){
	Boolean flag=false;
	Map<String, Object>estimate=leadFeignClient.getEstimateById(paymentRegister.getEstimateId());
	String projectName = estimate.get("productName")!=null?estimate.get("productName").toString():"NA";
	Unbilled unbilled=unbilledRepository.findByEstimateId(paymentRegister.getEstimateId());
	if(unbilled!=null) {
		double totalAmount = paymentRegister.getTotalAmount();
		Date approveDate = paymentRegister.getApproveDate();

		double paidAmount = unbilled.getPaidAmount();
		paidAmount=paidAmount+totalAmount;
		double dueAmount = unbilled.getDueAmount();
		dueAmount=dueAmount-totalAmount;
		unbilled.setPaidAmount(paidAmount);
		unbilled.setDueAmount(dueAmount);
		unbilled.setDate(new Date());
		unbilled.setProject(projectName);
		unbilledRepository.save(unbilled);
		flag=true;
	}else {
		unbilled=new Unbilled();
		unbilled.setCompany(paymentRegister.getCompanyName());
		unbilled.setDate(paymentRegister.getApproveDate());
		unbilled.setEstimateId(paymentRegister.getEstimateId());

		double totalPaidAmount = paymentRegister.getTotalAmount();			   
		unbilled.setPaidAmount(totalPaidAmount);
		unbilled.setProject(projectName);
		unbilled.setDate(new Date());

		double totalAmount = objectToDouble(estimate.get("totalAmount"));
		unbilled.setOrderAmount(totalAmount);

		double paidAmount = totalAmount-totalPaidAmount;
		unbilled.setDueAmount(paidAmount);
		unbilledRepository.save(unbilled);
		flag=true;

	}
	return unbilled;
}


public double objectToDouble(Object totalAmountObj) {
	double totalAmount = 0.0;

	if (totalAmountObj != null) {
		if (totalAmountObj instanceof Number) {
			totalAmount = ((Number) totalAmountObj).doubleValue();
		} else if (totalAmountObj instanceof String) {
			try {
				totalAmount = Double.parseDouble((String) totalAmountObj);
			} catch (NumberFormatException e) {
				// Handle invalid string format here if needed
				totalAmount = 0.0; // or some default
			}
		} else {
			// Handle other unexpected types if needed
		}
	}
	return totalAmount;
}


@Override
public Map<String, Object> getRemainingAmount(Long id) {
	Map<String,Object>result=new HashMap<>();
	Map<String, Object> estimate = leadFeignClient.getEstimateById(id);

	List<PaymentRegister> paymentRegister = paymentRegisterRepository.findAllByEstimateId(id);
	double proFees=0;
	double profGst=0;
	double govFees=0;
	double otherFees=0;
	double serviceCharge=0;
	double totalAmount=0;
	String paymentType="NA";
	String productType=estimate.get("productType")!=null?estimate.get("productType").toString():"NA";

	for(PaymentRegister pr:paymentRegister) {
		proFees=proFees+pr.getProfessionalFees();
		profGst=pr.getProfessionalGstAmount();   
		govFees=pr.getGovermentfees();
		paymentType=pr.getPaymentType();
		totalAmount=totalAmount+pr.getTotalAmount();
		otherFees=pr.getOtherFees();
		serviceCharge=pr.getServiceCharge();
	}
	//		String productType=estimate.get("productType")!=null?estimate.get("productType").toString():"NA";
	double totAmount =0;
	if("Product".equalsIgnoreCase(productType)) {
		totAmount = Double.parseDouble(estimate.get("totalPrice").toString());
		if(totalAmount>0) {
			result.put("primary", false);
		}else{
			result.put("primary", true);
		}
	}else {
		if(proFees>0) {
			result.put("primary", false);
		}else{
			result.put("primary", true);
		}
		totAmount = Double.parseDouble(estimate.get("totalAmount").toString());
	}
	double profees = Double.parseDouble(estimate.get("professionalFees").toString());
	double govfees = Double.parseDouble(estimate.get("govermentFees").toString());
	double serviceFees = Double.parseDouble(estimate.get("serviceCharge").toString());
	double otherCharge = Double.parseDouble(estimate.get("otherFees").toString());

	totAmount=totAmount-totalAmount;
	result.put("totalAmount", totAmount);
	String quantity = estimate.get("quantity")!=null?estimate.get("quantity").toString():"NA";
	Object productGst = estimate.get("gstAmount");
	Object actualPrice = estimate.get("actualPrice");

	String gst = estimate.get("gst")!=null?estimate.get("gst").toString():"NA";
	result.put("gst", gst);
	result.put("actualPrice", actualPrice);

	result.put("quantity", quantity);
	result.put("gstAmount", productGst);
	result.put("proffees", profees-proFees);
	result.put("govfees", govfees-govfees);
	//		if(proFees>0) {
	//			result.put("primary", false);
	//		}else{
	//			result.put("primary", true);
	//		}
	result.put("productType", paymentType);

	result.put("paymentType", paymentType);
	result.put("otherFees", otherCharge-otherFees);
	result.put("serviceCharge", serviceFees-serviceCharge);

	return result;

}


@Override
public List<Map<String,Object>> searchPaymentRegister(String searchParam, String name,String fromDate,String toDate) {
	List<PaymentRegister>result=new ArrayList<>();
	if(searchParam.equals("clientName")) {
		result=paymentRegisterRepository.findByName(name);
	}else if(searchParam.equals("companyName")){
		result=paymentRegisterRepository.findBycompanyName(name);

	}else if(searchParam.equals("invoiceNo")){
		//			result=paymentRegisterRepository.findByInvoiceNo(name);
	}else {
		result=paymentRegisterRepository.findByInBetweenDate(fromDate,toDate);
	}
	List<Map<String,Object>>res=new ArrayList<>();
	for(PaymentRegister p :result) {
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
		Map<String, Object>remAmount= remainingAmountAndPaidAmount(p.getEstimateId());
		Object dueAmount = remAmount.get("totalRemainingAmount");
		Object txnAmount = remAmount.get("estimateAmount");
		Object estimateCreateDate = remAmount.get("estimateDate");

		map.put("dueAmount", dueAmount);
		map.put("txnAmount", txnAmount);
		map.put("orderAmount", p.getTotalAmount());
		map.put("estimateCreateDate", estimateCreateDate);
		res.add(map);

	}
	return res;
}


@Override
public Map<String,Object> paymentApproveAndDisapprovedManual(PaymentApproveDto paymentApproveDto) {
	Map<String,Object>res=new HashMap<>();
	Boolean flag=true;
	PaymentRegister paymentRegister = paymentRegisterRepository.findById(paymentApproveDto.getPaymentRegisterId()).get();
	Unbilled unbilled =new Unbilled();
	if(paymentApproveDto.getStatus().equals("approved")) {
		// Boolean invoice = createInvoice(paymentRegister.getEstimateId());
		Boolean invoice = createInvoiceV2(paymentRegister.getEstimateId(),paymentApproveDto.getPaymentRegisterId());
		Boolean payment = paymentApproveAndDisapprovedV5(paymentRegister.getId() ,paymentRegister.getEstimateId());
		//			createUnbilled(paymentRegister);
		Long project = leadFeignClient.createProject(paymentRegister.getEstimateId());
		unbilled = createUnbilled(paymentRegister);

		if(invoice && payment) {
			flag=true;
			paymentRegister.setStatus("approved");
			paymentRegister.setComment(paymentApproveDto.getComment());
			paymentRegisterRepository.save(paymentRegister);

		}
	}else {
		paymentRegister.setStatus(paymentApproveDto.getStatus());
		paymentRegister.setComment(paymentApproveDto.getComment());

		paymentRegisterRepository.save(paymentRegister);
		flag=true;

	}
	Map<String,Object>map=new HashMap<>();
	map.put("unbilledId", unbilled!=null?unbilled.getId():null);
	map.put("estimateId", paymentRegister!=null?paymentRegister.getEstimateId():null);

	return map;

}

public Boolean createInvoiceV2(Long estimateId,Long paymentRegisterId) {
	Organization organization = organizationRepository.findById(1l).get();
	Map<String, Object> feignLeadClient = LeadFeignClient.getEstimateById(estimateId);
	PaymentRegister pRegister = paymentRegisterRepository.findById(paymentRegisterId).get();
	InvoiceData invoiceData=new InvoiceData();
	invoiceData.setCreateDate(new Date());

	//		 invoiceData.setProduct(product);
	invoiceData.setAddress(null);
	
	invoiceData.setCompanyName(feignLeadClient.get("companyName")!=null?feignLeadClient.get("companyName").toString():null);
	invoiceData.setPrimaryContactId(estimateId); //--
	invoiceData.setTermOfDelivery(pRegister.getTermOfDelivery());
	invoiceData.setProductName(pRegister.getServiceName());   //service name
	invoiceData.setEstimateId(estimateId);

	String assignee = feignLeadClient.get("assigneeIds").toString();
	Long assigneeId=Long.parseLong(assignee);
	String state=feignLeadClient.get("state")!=null?feignLeadClient.get("state").toString():"NA";
	invoiceData.setModeOfPayment(pRegister.getModeOfPayment());
	invoiceData.setReferenceDate(pRegister.getReferenceDate());
	invoiceData.setOtherReference(pRegister.getOtherReference());
	invoiceData.setBuyerOrderNo(pRegister.getBuyerOrderNo());
	invoiceData.setActualAmount(pRegister.getActualAmount());
	invoiceData.setQuantity(pRegister.getQuantity());
    if(organization.getState().equals(state)) {
    	if("Product".equals(pRegister.getProductType())) {
    		invoiceData.setCgstSgstPresent(true);
    		invoiceData.setCgst(pRegister.getGstPercent()/2);
        	invoiceData.setSgst(pRegister.getGstPercent()/2);
    	}else {
    		invoiceData.setCgstSgstPresent(true);
    	   	invoiceData.setCgst(pRegister.getProfesionalGst()/2);
        	invoiceData.setSgst(pRegister.getProfesionalGst()/2);
    	}

    }else {
    	invoiceData.setIgst(pRegister.getIgst());
    	if("Product".equals(pRegister.getProductType())) {
    		invoiceData.setIgstPresent(true);

        	invoiceData.setIgst(pRegister.getIgst());
    	}else {
    		invoiceData.setIgstPresent(true);

        	invoiceData.setIgst(pRegister.getProfesionalGst());
    	}

    }

	if(assigneeId!=null) {
		//						User user = userRepository.findById(assigneeId).get();
		//						invoiceData.setAssignee(user);
	}
	

    // Format: YYYYMMDD
	LocalDate today = LocalDate.now();
    String formattedDate = today.format(DateTimeFormatter.BASIC_ISO_DATE);    
    long count = invoiceDataRepository.count()+1;
    String invoiceNo="INV00"+formattedDate+count;
    invoiceData.setInvoiceNo(invoiceNo);
	String lead = feignLeadClient.get("leadId")!=null?feignLeadClient.get("leadId").toString():null;
	if(lead!=null) {
		Long leadId=Long.parseLong(lead);
		invoiceData.setLeadId(leadId);

	}

	invoiceData.setGstNo(feignLeadClient.get("gstNo")!=null?feignLeadClient.get("gstNo").toString().toString():null);

	invoiceData.setGstType(feignLeadClient.get("gstType")!=null?feignLeadClient.get("gstType").toString():null);
	invoiceData.setGstDocuments(feignLeadClient.get("gstNo")!=null?feignLeadClient.get("gstNo").toString():null);//misssing 

	//				invoiceData.setCompanyAge(feignLeadClient.get("companyAge").toString());


	invoiceData.setGovermentfees(pRegister.getGovermentfees()+"");
	invoiceData.setGovermentCode(feignLeadClient.get("govermentCode")!=null?feignLeadClient.get("govermentCode").toString():null);
	invoiceData.setGovermentGst(pRegister.getGovermentGst()+"");

	invoiceData.setProfessionalFees(pRegister.getProfessionalFees()+"");
	invoiceData.setProfessionalCode(feignLeadClient.get("profesionalCode")!=null?feignLeadClient.get("profesionalCode").toString():null);
	invoiceData.setProfesionalGst(pRegister.getProfesionalGst()+"");  //check gst percent

	invoiceData.setServiceCharge(feignLeadClient.get("serviceCharge").toString());

	invoiceData.setProductType(pRegister.getProductType());

	invoiceData.setGovermentfees(pRegister.getGovermentfees()+"");
	invoiceData.setGovermentCode(feignLeadClient.get("govermentCode")!=null?feignLeadClient.get("govermentCode").toString():null);
	invoiceData.setGovermentGst(feignLeadClient.get("govermentGst")!=null?feignLeadClient.get("govermentGst").toString():null);

	invoiceData.setProfessionalFees(pRegister.getProfessionalFees()+"");
	invoiceData.setProfessionalCode(feignLeadClient.get("profesionalCode")!=null?feignLeadClient.get("profesionalCode").toString():null);
	invoiceData.setProfesionalGst(pRegister.getProfesionalGst()+"");

	invoiceData.setServiceCharge(feignLeadClient.get("serviceCharge").toString());
	invoiceData.setTotalAmount(pRegister.getTotalAmount()+"");
	invoiceData.setProductType(pRegister.getProductType());

	invoiceData.setAmount(pRegister.getAmount());
	invoiceData.setGstPercent(pRegister.getGstPercent());
	invoiceData.setGstAmount(pRegister.getGstAmount());

	if(feignLeadClient.get("serviceCode")!=null) {
		invoiceData.setServiceCode(feignLeadClient.get("serviceCode").toString());
	}
	if(feignLeadClient.get("serviceGst")!=null) {
		invoiceData.setServiceGst(feignLeadClient.get("serviceGst").toString());

	}
	String leadId = feignLeadClient.get("leadId")!=null?feignLeadClient.get("leadId").toString():null;
	//Gst Data Store
	AddGstDto addGstDto=new AddGstDto();
	addGstDto.setCompany(feignLeadClient.get("companyName")!=null?feignLeadClient.get("companyName").toString():null);
	addGstDto.setGst(pRegister.getGstPercent());
	if("Product".equals(pRegister.getProductType())) {
		addGstDto.setGst(pRegister.getGstPercent());
		addGstDto.setGstAmount(pRegister.getGstAmount());
	}else {
		addGstDto.setGst(pRegister.getProfesionalGst());
		addGstDto.setGstAmount(pRegister.getProfessionalGstAmount());

	}
	addGstDto.setPaymentRegisterId(paymentRegisterId);
	addGstDto.setStatus("initiated");
	addGstDto.setType("payable");
	gstDataCrmService.addGstDataCrm(addGstDto);
	System.out.println("Payment Data has been apprioved");
	if(leadId!=null) {
		long lId = Long.parseLong(leadId);
		invoiceData.setLeadId(lId);

	}

	//
	//		invoiceData.setOtherFees(feignLeadClient.get("otherFees").toString());
	//		invoiceData.setOtherCode(feignLeadClient.get("otherCode")!=null?feignLeadClient.get("otherCode").toString():null);
	//		invoiceData.setOtherGst(feignLeadClient.get("otherGst")!=null?feignLeadClient.get("otherGst").toString():null);

	invoiceData.setAddress(feignLeadClient.get("address").toString());
	invoiceData.setCity(feignLeadClient.get("city").toString());
	invoiceData.setPrimaryPinCode(feignLeadClient.get("primaryPinCode").toString());
	invoiceData.setState(feignLeadClient.get("state").toString());
	invoiceData.setCountry(feignLeadClient.get("country").toString());

	invoiceData.setSecondaryAddress(feignLeadClient.get("secondaryAddress")!=null?feignLeadClient.get("secondaryAddress").toString():null);
	invoiceData.setSecondaryCity(feignLeadClient.get("secondaryCity")!=null?feignLeadClient.get("secondaryCity").toString():null); 
	invoiceData.setSecondaryPinCode(feignLeadClient.get("secondaryPinCode")!=null?feignLeadClient.get("secondaryPinCode").toString():null);
	invoiceData.setSecondaryState(feignLeadClient.get("secondaryState")!=null?feignLeadClient.get("secondaryState").toString():null);
	invoiceData.setSecondaryCountry(feignLeadClient.get("secondaryCountry")!=null?feignLeadClient.get("secondaryCountry").toString():null);
	invoiceData.setProductType(pRegister.getProductType());

	invoiceData.setInvoiceNote(feignLeadClient.get("invoiceNote").toString());
	invoiceData.setOrderNumber(feignLeadClient.get("orderNumber").toString());
	invoiceData.setPurchaseDate(feignLeadClient.get("purchaseDate").toString());//CURRENT DATE 
	//		invoiceData.setEstimateData((Date)feignLeadClient.get("estimateDate"));
	invoiceData.setRemarksForOption(feignLeadClient.get("address").toString());
	invoiceData.setCc((List<String>)feignLeadClient.get("ccMail"));
	if(feignLeadClient.get("primaryContactId")!=null) {

		invoiceData.setPrimaryContactId(Long.parseLong(feignLeadClient.get("primaryContactId").toString()));
		invoiceData.setPrimaryContactTitle(feignLeadClient.get("primaryContactTitle")!=null?feignLeadClient.get("primaryContactTitle").toString():null);
		invoiceData.setPrimaryContactName(feignLeadClient.get("primaryContactName")!=null?feignLeadClient.get("primaryContactName").toString():null);
		invoiceData.setPrimaryContactemails(feignLeadClient.get("primaryContactEmails")!=null?feignLeadClient.get("primaryContactEmails").toString():null);
		invoiceData.setPrimaryContactNo(feignLeadClient.get("primaryContactContactNo")!=null?feignLeadClient.get("primaryContactContactNo").toString():null);
	}
	if(feignLeadClient.get("secondaryContactId")!=null) {
		invoiceData.setSecondaryContactId(Long.parseLong(feignLeadClient.get("secondaryContactId").toString()));
		invoiceData.setSecondaryContactTitle(feignLeadClient.get("secondaryContactTitle")!=null?feignLeadClient.get("secondaryContactTitle").toString():null);
		invoiceData.setSecondaryContactName(feignLeadClient.get("secondaryContactName")!=null?feignLeadClient.get("secondaryContactName").toString():null);
		invoiceData.setSecondaryContactemails(feignLeadClient.get("secondaryContactEmails")!=null?feignLeadClient.get("secondaryContactEmails").toString():null);
		invoiceData.setSecondaryContactNo(feignLeadClient.get("secondaryContactContactNo")!=null?feignLeadClient.get("secondaryContactContactNo").toString():null);
	}
	invoiceDataRepository.save(invoiceData);
	return true;
}


@Override
public Map<String, Object> getPaymentRegisterWithEstimateById(Long id) {
	PaymentRegister paymentRegister = paymentRegisterRepository.findById(id).get();
	Map<String, Object> estimate = LeadFeignClient.getEstimateById(paymentRegister.getEstimateId());
	Map<String,Object>map = new HashMap<>();
	map.put("id", estimate.get("id"));
	map.put("productName", estimate.get("productName"));
	map.put("address", estimate.get("address"));
	map.put("city", estimate.get("city"));
	map.put("companyAge",estimate.get("companyAge"));
	map.put("company", estimate.get("company"));
	map.put("companyName", estimate.get("companyName"));
	map.put("consultingSales", estimate.get("consultingSales"));
	map.put("country", estimate.get("id"));
	map.put("documents", estimate.get("documents"));
	map.put("purchaseDate", estimate.get("purchaseDate"));
	map.put("performaInvoice", estimate.get("performaInvoice"));
	map.put("productType", estimate.get("productType"));

	map.put("govermentCode", estimate.get("govermentCode"));
	map.put("govermentFees", estimate.get("govermentFees"));
	map.put("govermentGst", estimate.get("govermentGst"));
	map.put("gstDocuments",estimate.get("gstDocuments"));

	map.put("gstNo", estimate.get("gstNo"));
	map.put("gstType", estimate.get("gstType"));
	map.put("invoiceNote", estimate.get("invoiceNote"));
	map.put("isPrimaryAddress",estimate.get("isPrimaryAddress"));
	map.put("isSecondaryAddress", estimate.get("isSecondaryAddress"));
	map.put("leadId", estimate.get("leadId"));
	map.put("orderNumber", estimate.get("orderNumber"));

	map.put("otherCode", estimate.get("otherCode"));
	map.put("otherGst", estimate.get("otherGst"));
	map.put("otherFees", estimate.get("otherFees"));

	map.put("panNo", estimate.get("panNo"));
	map.put("primaryPinCode",estimate.get("primaryPinCode"));
	map.put("primaryTitle", estimate.get("primaryTitle"));

	map.put("serviceCode", estimate.get("serviceCode"));
	map.put("serviceGst", estimate.get("serviceGst"));
	map.put("serviceCharge", estimate.get("serviceCharge"));

	map.put("state", estimate.get("state"));
	map.put("status", estimate.get("status"));
	map.put("unitId", estimate.get("unitName"));
	map.put("unitName", estimate.get("id"));
	map.put("assigneeId",estimate.get("assigneeId"));
	map.put("assigneeIds",estimate.get("assigneeIds"));

	map.put("ccMail", estimate.get("ccMail"));
	map.put("createDate", estimate.get("createDate"));
	map.put("estimateDate", estimate.get("estimateDate"));
	map.put("profesionalGst", estimate.get("profesionalGst"));
	map.put("profesionalCode", estimate.get("profesionalCode"));
	map.put("professionalFees", estimate.get("professionalFees"));

	map.put("primaryContact", estimate.get("primaryContact"));
	map.put("primaryContactId",estimate.get("primaryContactId"));
	map.put("primaryContactTitle", estimate.get("primaryContactTitle"));
	map.put("primaryContactName", estimate.get("primaryContactName"));
	map.put("primaryContactEmails", estimate.get("primaryContactDesignation"));
	map.put("primaryContactDesignation", estimate.get("id"));
	map.put("primaryContactContactNo", estimate.get("primaryContactContactNo"));

	map.put("secondaryContactId", estimate.get("secondaryContactId"));
	map.put("secondaryContactTitle", estimate.get("secondaryContactTitle"));
	map.put("secondaryContactName", estimate.get("secondaryContactName"));
	map.put("secondaryContactEmails", estimate.get("secondaryContactDesignation"));
	map.put("secondaryContactDesignation", estimate.get("id"));
	map.put("secondaryContactContactNo", estimate.get("secondaryContactContactNo"));

	map.put("primaryPinCode", estimate.get("primaryPinCode"));
	map.put("primaryContact", estimate.get("primaryContact"));

	map.put("secondaryAddress", estimate.get("secondaryAddress"));
	map.put("secondaryCity", estimate.get("secondaryCity"));
	map.put("secondaryPinCode", estimate.get("secondaryPinCode"));
	map.put("secondaryState", estimate.get("secondaryState"));
	map.put("secondaryCountry", estimate.get("secondaryCountry"));
	map.put("remarkForOperation", estimate.get("remarkForOperation"));

	map.put("isUnit", estimate.get("isUnit"));
	map.put("secondaryContact", estimate.get("secondaryContact"));
	map.put("isSecondary", estimate.get("isSecondary"));
	int gst =paymentRegister.getProfessionalGstPercent();
//	int gst = s.getProfesionalGst() != null ? Integer.valueOf(s.getProfesionalGst()) : 0;
	double totalAmount = paymentRegister.getGovermentfees() + paymentRegister.getProfessionalFees() + paymentRegister.getOtherFees();

//	double totalAmount = s.getGovermentfees() + s.getProfessionalFees() + s.getOtherFees();
	Double proGst = findGstAmount(paymentRegister.getProfesionalGst(), paymentRegister.getProfessionalFees());
	map.put("totalAmount", totalAmount + proGst);
	map.put("gstAmount", proGst);
	map.put("gst", estimate.get("gst"));
	map.put("estimateProduct",estimate.get("estimateProduct"));

	map.put("productType", estimate.get("productType"));
	map.put("actualPrice", estimate.get("actualPrice"));
	map.put("quantity", estimate.get("quantity"));
	String productType = estimate.get("productType")!=null?estimate.get("productType").toString():"NA";
	if ("Product".equalsIgnoreCase(productType)) {
	    map.put("totalPrice", paymentRegister.getTotalAmount());
	    map.put("gst", estimate.get("gst"));
	    map.put("gstCode", estimate.get("gstCode"));

	    map.put("gstAmount", estimate.get("gstCode"));
	}

	map.put("gst", estimate.get("gst"));
	map.put("gstCode", estimate.get("gstCode"));
	map.put("consultantByCompany",estimate.get("consultantByCompany"));
	
	return map;
}

public 	Double findGstAmount(double gstPercent, double amount) {

	Double gst=gstPercent;
	if(gst!=0 && amount!=0) {
		double a=amount;
		Double totalAmount = (a*gst)/100;
		System.out.println(totalAmount);
		return totalAmount;
	}
	else {
		return 0.0;
	}

}


}
