package com.account.dashboard.serviceImpl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.account.Ledger;
import com.account.dashboard.domain.account.Voucher;
import com.account.dashboard.domain.account.VoucherType;
import com.account.dashboard.dto.CreateVoucherDto;
import com.account.dashboard.repository.LedgerRepository;
import com.account.dashboard.repository.LedgerTypeRepository;
import com.account.dashboard.repository.VoucherRepository;
import com.account.dashboard.repository.VoucherTypeRepo;
import com.account.dashboard.service.VoucherService;
import com.account.dashboard.util.CalendarUtil;

@Service
public class VoucherServiceImpl implements VoucherService{

	@Autowired
	LedgerRepository ledgerRepository;

	@Autowired
	LedgerTypeRepository ledgerTypeRepository;

	@Autowired 
	VoucherTypeRepo voucherTypeRepo;

	@Autowired
	VoucherRepository voucherRepository;

	@Override
	public Boolean createVoucher(CreateVoucherDto createVoucherDto) {

		Boolean flag=false;
		Voucher v = new Voucher();
		v.setCompanyName(createVoucherDto.getCompanyName());

		if(createVoucherDto.getCreditAmount()!=null ) {
			v.setCreditDebit(true);
			v.setCreditAmount(createVoucherDto.getCreditAmount());
			
			v.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
			v.setCgst(createVoucherDto.getCgst());
			v.setCgstCreditAmount(createVoucherDto.getCgstCreditAmount());
			v.setCgstDebitAmount(createVoucherDto.getCgstDebitAmount());
			
			v.setSgst(createVoucherDto.getSgst());
			v.setSgstCreditAmount(createVoucherDto.getSgstCreditAmount());
			v.setSgstDebitAmount(createVoucherDto.getSgstDebitAmount());

			v.setIgstPresent(createVoucherDto.isIgstPresent());
			v.setIgst(createVoucherDto.getIgst());
			v.setIgstCreditAmount(createVoucherDto.getIgstCreditAmount());
			v.setIgstDebitAmount(createVoucherDto.getIgstDebitAmount());

		}

		if(createVoucherDto.getDebitAmount()!=null ) {
			v.setCreditDebit(true);
			v.setDebitAmount(createVoucherDto.getDebitAmount());
		}
		v.setCreateDate(new Date());
		Optional<Ledger> ledger = ledgerRepository.findById(createVoucherDto.getLedgerId());
		if(ledger!=null && ledger.isPresent()&&ledger.get()!=null) {
			v.setLedger(ledger.get());
			v.setLedgerType(ledger.get().getLedgerType());
		}        
		v.setPaymentType(createVoucherDto.getPaymentType());

		Optional<VoucherType> voucherType = voucherTypeRepo.findById(createVoucherDto.getVoucherTypeId());
		if(voucherType!=null &&voucherType.isPresent() && voucherType.get()!=null) {
			v.setVoucherType(voucherType.get());
		}

		Optional<Ledger> product = ledgerRepository.findById(createVoucherDto.getProductId());
		if(product!=null && product.isPresent()&&product.get()!=null) {
			v.setProduct (product.get());
		}    
         v.setTotalAmount(createVoucherDto.getTotalAmount());
		voucherRepository.save(v);
		flag=true;
		return flag;
	}

	@Override
	public List<Voucher>  getAllVoucher() {
		List<Voucher>voucher=voucherRepository.findAll();
		return voucher;
	}

	public List<Map<String,Object>>  getAllVoucherV2() {
		List<Voucher>voucher=voucherRepository.findAll();
		List<Map<String,Object>>res = new ArrayList<>();
		for(Voucher v:voucher) {
			Map<String,Object>map = new HashMap<>();
			map.put("id", v.getId());
			map.put("ledgerId", v.getLedger()!=null?v.getLedger().getId():0);
			map.put("ledgerName", v.getLedger()!=null?v.getLedger().getName():"NA");

			map.put("creditAmount", v.getCreditAmount());
			map.put("debitAmount", v.getDebitAmount());
			//			d gstAmount=0;
			//			if(v.getCgst()!=null &&v.getSgst()!=null) {
			//				Double cgst = Double.valueOf(v.getCgst());
			//				Double sgst = Double.valueOf(v.getSgst());
			//				double gst = cgst+sgst;
			//                double remGstAmount = 100-gst;
			//                if(v.getCreditAmount()) {
			//                	
			//                }
			//
			//			}
			//			Double.valueOf(v.getCgst());
			map.put("cgst", v.getCgst());
			map.put("sgst", v.getSgst());
			map.put("cgstCreditAmount", v.getCgstCreditAmount());
			map.put("cgstDebitAmount", v.getCgstDebitAmount());

			map.put("sgstCreditAmount", v.getSgstCreditAmount());
			map.put("sgstDebitAmount", v.getSgstDebitAmount());

			map.put("igst", v.getIgst());
			map.put("igstCreditAmount", v.getIgstCreditAmount());
			map.put("igstDebitAmount", v.getIgstDebitAmount());


			map.put("paymentType", v.getPaymentType());
			map.put("product", v.getProduct()!=null?v.getProduct().getName():"NA");
			map.put("productId", v.getProduct()!=null?v.getProduct().getId():"0");

			map.put("group", v.getLedger()!=null?v.getLedger().getLedgerType()!=null?v.getLedger().getLedgerType().getName():"NA":"");
			map.put("voucherTypeId", v!=null?v.getLedgerType()!=null?v.getLedgerType().getId():0:0);
			map.put("ledgerTypeId", v.getLedgerType()!=null?v.getLedgerType().getId():0);

			map.put("createDate", v.getCreateDate());
			map.put("ledgerType", v.getLedgerType());
			map.put("voucherType", v.getVoucherType());
			map.put("cerateDate", v.getCreateDate());
			map.put("cerateDate", v.getCreateDate());
			map.put("professionalGstAmount", v.getProfessionalGstAmount());
			map.put("totalAmount", v.getTotalAmount());

			res.add(map);
		}
		return res;
	}

	public List<Map<String,Object>> getAllVoucherV1() {
		List<Voucher>voucher=voucherRepository.findAll();
		List<Map<String,Object>>result = new ArrayList<>();
		for(Voucher v:voucher) {
			Map<String,Object>map = new HashMap<>();
			map.put("id", v.getId());
			map.put("companyName", v.getCompanyName());
			map.put("creaditAmount", v.getCreditAmount());
			map.put("debitAmount", v.getDebitAmount());
			map.put("paymentType", v.getPaymentType());
			map.put("createDate", v.getCreateDate());
			map.put("ledger", v.getLedger());
			map.put("ledgerType", v.getLedgerType());
			map.put("voucherType", v.getVoucherType());
			map.put("cerateDate", v.getCreateDate());
			map.put("professionalGstAmount", v.getProfessionalGstAmount());
			map.put("totalAmount", v.getTotalAmount());
			result.add(map);

		}
		return result;
	}

	@Override
	public Map<String, Object> getVoucherAmount() {
		List<Voucher>voucher=voucherRepository.findAll();
		Map<String,Object>map = new HashMap<>();
		long totalCredit=0;
		long totalDebit=0;
		long totalAmount=0;
		for(Voucher v:voucher) {
			//        	if(v.isCreditDebit()) {
			//        		long debitAmount =Long.valueOf(v.getDebitAmount()!=null?v.getDebitAmount():"0");
			//        		long creditAmount =Long.valueOf(v.getCreditAmount()!=null?v.getCreditAmount():"0");
			//        		totalCredit=totalCredit+creditAmount;
			//        		totalDebit=totalDebit+debitAmount;
			//
			//        		totalAmount=totalAmount+debitAmount-creditAmount;
			//        	}else {
			//        		long debitAmount =Long.valueOf(v.getDebitAmount()!=null?v.getDebitAmount():"0");
			//        		totalDebit=totalDebit+debitAmount;
			//        		totalAmount=totalAmount+debitAmount;
			//        		
			//        	}

			if(v.isCreditDebit()) {
				long debitAmount =Long.valueOf(v.getDebitAmount()!=null?v.getDebitAmount():"0");
				long creditAmount =Long.valueOf(v.getCreditAmount()!=null?v.getCreditAmount():"0");



				if(v.getDebitAmount()!=null) {
					if(v.isIgstPresent()) {
						long amount =Long.valueOf(v.getIgst()!=null?v.getIgst():"0");
						debitAmount=debitAmount+amount;
					}
					if(v.isCgstSgstPresent()) {
						long a1 =Long.valueOf(v.getCgst()!=null?v.getCgst():"0");
						long a2 =Long.valueOf(v.getSgst()!=null?v.getSgst():"0");
						debitAmount=debitAmount+a1+a2;

					}
				}else {
					if(v.isIgstPresent()) {
						long amount =Long.valueOf(v.getIgst()!=null?v.getIgst():"0");
						creditAmount=creditAmount+amount;
					}
					if(v.isCgstSgstPresent()) {
						long a1 =Long.valueOf(v.getCgst()!=null?v.getCgst():"0");
						long a2 =Long.valueOf(v.getSgst()!=null?v.getSgst():"0");
						creditAmount=creditAmount+a1+a2;

					}
				}
				totalCredit=totalCredit+creditAmount;
				totalDebit=totalDebit+debitAmount;

				totalAmount=totalAmount-debitAmount+creditAmount;
			}else {
				long debitAmount =Long.valueOf(v.getDebitAmount()!=null?v.getDebitAmount():"0");
				//        	

				totalDebit=totalDebit+debitAmount;
				//        		totalAmount=totalAmount+debitAmount;
				if(v.isIgstPresent()) {
					long amount =Long.valueOf(v.getIgst()!=null?v.getIgst():"0");
					debitAmount=debitAmount+amount;
				}
				if(v.isCgstSgstPresent()) {
					long a1 =Long.valueOf(v.getCgst()!=null?v.getCgst():"0");
					long a2 =Long.valueOf(v.getSgst()!=null?v.getSgst():"0");
					debitAmount=debitAmount+a1+a2;

				}

				totalAmount=totalAmount-debitAmount;

			}
		}
		map.put("totalCredit", totalCredit);
		map.put("totalDebit", totalDebit);
		map.put("totalAmount", totalAmount);

		return map;
	}

	@Override
	public Map<String, Object> getAllVoucherByLedgerId(Long ledgerId) {

		List<Voucher> voucherList = voucherRepository.findAllByLedgerId(ledgerId);
		Map<String,Object>result = new HashMap<>();
		List<Map<String, Object>>res= new ArrayList<>();
		double totalCredit=0;
		double totalDebit=0;
		double totalAmount=0;
		for(Voucher v:voucherList) {
			Map<String,Object>map = new HashMap<>();
			map.put("id", v.getId());
			map.put("companyName", v.getCompanyName());
			map.put("paymentType", v.getPaymentType());
			map.put("createDate", v.getCreateDate());

			map.put("ledgerId", v.getLedger().getId());
			map.put("ledgerName", v.getLedger().getName());
			map.put("ledgerType", v.getLedger().getLedgerType());
			map.put("isDebitCredit", v.isCreditDebit());

			map.put("ledgerType", v.getLedgerType());
			map.put("voucherType", v.getVoucherType());
			map.put("creditAmount", v.getCreditAmount());
			map.put("debitAmount", v.getDebitAmount());
			if(v.isCgstSgstPresent()) {
				map.put("cgst", v.getCgst());
				map.put("sgst", v.getSgst());
			}
			if(v.isIgstPresent()) {
				map.put("igst", v.getIgst());
			}
			res.add(map);
//			long creditGstAmount =0;
//			long debitGstAmount =0;
           System.out.println("aaaaaaaaaaa111");
			if(v.isCreditDebit()) {
		           System.out.println("aaaaaaaaaaa222");

				long creditGstAmount =0;
				long debitGstAmount =0;
				double debitAmount =Double.parseDouble(v.getDebitAmount()!=null?v.getDebitAmount():"0");
				System.out.println("Voucher id . . ."+v);
				System.out.println("Voucher id . . ."+debitAmount);
				System.out.println("Voucher id . . ."+v.getCreditAmount());

				double creditAmount =Double.parseDouble(v!=null?v.getCreditAmount()!=null?v.getCreditAmount():"0":"0");

		           System.out.println(!v.getDebitAmount().equals("0"));


				if(!v.getDebitAmount().equals("0")) {
			           System.out.println("aaaaaaaaaaa333");

					if(v.isIgstPresent()) {
				           System.out.println("aaaaaaaaaaa444");

						debitGstAmount =(long)(v.getIgstDebitAmount());
					}
					if(!v.isCgstSgstPresent()) {
				           System.out.println("aaaaaaaaaaa555");

						debitGstAmount =(long)(v.getCgstDebitAmount()+v.getSgstDebitAmount());
					}
				}else {
			           System.out.println("aaaaaaaaaaa666");

					
					if(v.isIgstPresent()) {
				           System.out.println("aaaaaaaaaaa777");

						creditGstAmount =(long)(v.getIgstCreditAmount());
						creditAmount=creditAmount;
					}
					if(!v.isCgstSgstPresent()) {
				           System.out.println("aaaaaaaaaaa888");

						creditGstAmount =(long)(v.getCgstCreditAmount()+v.getSgstCreditAmount());

						creditAmount=creditAmount;

					}
				}
				totalCredit=totalCredit+creditAmount+creditGstAmount;
				totalDebit=totalDebit+debitAmount+debitGstAmount;

				totalAmount=totalAmount-debitAmount-debitGstAmount+creditAmount+creditGstAmount;
			}else {
				double debitAmount =Double.parseDouble(v.getDebitAmount()!=null?v.getDebitAmount():"0");
				long debitGstAmount =0;   	
				
				//        		totalAmount=totalAmount+debitAmount;
				if(v.isIgstPresent()) {
					debitGstAmount =(long)(v.getIgstCreditAmount());
					debitAmount=debitAmount;
				}
				if(!v.isCgstSgstPresent()) {
					debitGstAmount =(long)(v.getCgstDebitAmount()+v.getSgstDebitAmount());

					debitAmount=debitAmount;

				}
				totalDebit=totalDebit+debitAmount;
				totalAmount=totalAmount-totalDebit;

			}
		}
		result.put("result", res);
		result.put("totalCredit", totalCredit);
		result.put("totalDebit", totalDebit);
		result .put("totalAmount", totalAmount);
		return result;
	}


	@Override
	public Map<String, Object> getAllVoucherInBetween(String startDate, String endDate) {
		//		List<Voucher>voucherList=voucherRepository.findByIdInBetween(startDate,endDate);
		Map<String,Object>result = new HashMap<>();
		List<Map<String, Object>>res= new ArrayList<>();
		Date date =new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		try {
			date = dateFormat.parse(endDate);
			date= CalendarUtil.addDayInDate(date);

		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}        
		// Convert the Date object to a string
		String dateString = dateFormat.format(date);
		List<Voucher>voucherList=voucherRepository.findByIdInBetween(startDate,dateString);

		double totalCredit=0;
		double totalDebit=0;
		double totalAmount=0;
		for(Voucher v:voucherList) {
			Map<String,Object>map = new HashMap<>();
			map.put("id", v.getId());
			map.put("companyName", v.getCompanyName());
			map.put("paymentType", v.getPaymentType());
			map.put("createDate", v.getCreateDate());
			map.put("professionalGstAmount", v.getProfessionalGstAmount());
			map.put("totalAmount", v.getTotalAmount());
			if( v.getLedger()!=null) {
				map.put("ledgerId", v.getLedger()!=null?v.getLedger().getId():0);
				map.put("ledgerName", v.getLedger().getName());
				map.put("ledgerType", v.getLedger().getLedgerType());
				map.put("isDebitCredit", v.isCreditDebit());
			}

			map.put("ledgerType", v.getLedgerType());
			map.put("voucherType", v.getVoucherType());
			map.put("creditAmount", v.getCreditAmount());
			map.put("debitAmount", v.getDebitAmount());
			res.add(map);

//			if(v.isCreditDebit()) {
//				double debitAmount =0;
//				if(v.getDebitAmount()!=null && (!v.getDebitAmount().equals(""))) {
//					debitAmount =Double.valueOf(v.getDebitAmount()!=null?v.getDebitAmount():"0");
//				}
//				double creditAmount =0;
//				if(v.getCreditAmount()!=null && (!v.getCreditAmount().equals(""))) {
//					creditAmount =Double.valueOf(v.getCreditAmount()!=null?v.getCreditAmount():"0");
//
//				}
//				totalCredit=totalCredit+creditAmount;
//				totalDebit=totalDebit+debitAmount;
//
//				totalAmount=totalAmount-debitAmount+creditAmount;
//			}else {
//				double debitAmount =Double.valueOf(v.getDebitAmount()!=null?v.getDebitAmount():"0");
//				totalDebit=totalDebit+debitAmount;
//				totalAmount=totalAmount-debitAmount;
//
//			}
			
			if(v.isCreditDebit()) {
				double debitAmount =0;
				double igstDebitAmount =0;
				System.out.println("test1");
				double cgstSgstDebitAmount=0;
				if(v.getDebitAmount()!=null && (!v.getDebitAmount().equals(""))) {
					System.out.println("test2");
					debitAmount =Double.valueOf(v.getDebitAmount()!=null?v.getDebitAmount():"0");
					if(v.isCgstSgstPresent()) {
						 cgstSgstDebitAmount = v.getCgstDebitAmount()+v.getSgstDebitAmount();
					}else {
					   igstDebitAmount = v.getIgstDebitAmount();
					}
				}
				double creditAmount =0;
				double cgstSgstCreditAmount =0;
				double igstCreditAmount =0;
				if(v.getCreditAmount()!=null && (!v.getCreditAmount().equals(""))) {
					System.out.println("test3"+v.isCgstSgstPresent());

					creditAmount =Double.valueOf(v.getCreditAmount()!=null?v.getCreditAmount():"0");
					if(!v.isCgstSgstPresent()) {
						 cgstSgstCreditAmount = v.getCgstCreditAmount()+v.getSgstCreditAmount();
					}else {
					   igstCreditAmount = v.getIgstCreditAmount();
					}

				}
				totalCredit=totalCredit+creditAmount+cgstSgstCreditAmount+igstCreditAmount;
				totalDebit=totalDebit+debitAmount+igstDebitAmount+cgstSgstDebitAmount;

				totalAmount=totalAmount-totalDebit+totalCredit;
//				map.put("totalCredit", totalCredit);
//				map.put("totalDebit", totalDebit);
//				map.put("totalAmount", totalAmount);

			}else {
				System.out.println("test4");
				double igstDebitAmount =0;
				double cgstSgstDebitAmount=0;
				double debitAmount =Double.valueOf(v.getDebitAmount()!=null?v.getDebitAmount():"0");
				if(v.isCgstSgstPresent()) {
					 cgstSgstDebitAmount = v.getCgstDebitAmount()+v.getSgstDebitAmount();
				}else {
				   igstDebitAmount = v.getIgstDebitAmount();
				}
				totalDebit=totalDebit+debitAmount+igstDebitAmount+cgstSgstDebitAmount;
				totalAmount=totalAmount-totalDebit;
//				map.put("totalCredit", totalCredit);
//				map.put("totalDebit", totalDebit);
//				map.put("totalAmount", totalAmount);

			}
		}
		result.put("result", res);
		result.put("totalCredit", totalCredit);
		result.put("totalDebit", totalDebit);
		result .put("totalAmount", totalAmount);
		return result;
	}

	@Override
	public Map<String, Object> getAllVoucherByGroup(Long id) {
		List<Long>ledgerList=ledgerRepository.findByLedgerTypeId(id);
		Map<String,Object>result = new HashMap<>();
		List<Map<String, Object>>res= new ArrayList<>();

		List<Voucher>voucherList=voucherRepository.findAllByLedgerIdIn(ledgerList);

		double totalCredit=0;
		double totalDebit=0;
		double totalAmount=0;
		for(Voucher v:voucherList) {
			Map<String,Object>map = new HashMap<>();
			map.put("id", v.getId());
			map.put("companyName", v.getCompanyName());
			map.put("paymentType", v.getPaymentType());
			map.put("createDate", v.getCreateDate());
			if( v.getLedger()!=null) {
				map.put("ledgerId", v.getLedger()!=null?v.getLedger().getId():0);
				map.put("ledgerName", v.getLedger().getName());
				map.put("ledgerType", v.getLedger().getLedgerType());
				map.put("isDebitCredit", v.isCreditDebit());
			}
			map.put("ledgerType", v.getLedgerType());
			map.put("voucherType", v.getVoucherType());
			map.put("creditAmount", v.getCreditAmount());
			map.put("debitAmount", v.getDebitAmount());
			res.add(map);
			if(v.isCreditDebit()) {
				double debitAmount =Double.parseDouble(v.getDebitAmount()!=null?v.getDebitAmount():"0");
				double creditAmount =Double.parseDouble(v.getCreditAmount()!=null?v.getCreditAmount():"0");
				totalCredit=totalCredit+creditAmount;
				totalDebit=totalDebit+debitAmount;
				totalAmount=totalAmount-debitAmount+creditAmount;
			}else {
				double debitAmount =Double.parseDouble(v.getDebitAmount()!=null?v.getDebitAmount():"0");
				totalDebit=totalDebit+debitAmount;
				totalAmount=totalAmount-debitAmount;

			}
		}
		result.put("result", res);
		result.put("totalCredit", totalCredit);
		result.put("totalDebit", totalDebit);
		result .put("totalAmount", totalAmount);
		return result;
	}

	@Override
	public Map<String, Object> getVoucheById(Long id) {

		double totalCredit=0;
		double totalDebit=0;
		double totalAmount=0;
		
		Voucher v= voucherRepository.findById(id).get();


		Map<String,Object>map = new HashMap<>();
		map.put("id", v.getId());
		map.put("companyName", v.getCompanyName());
		map.put("paymentType", v.getPaymentType());
		map.put("createDate", v.getCreateDate());
		map.put("professionalGstAmount", v.getProfessionalGstAmount());
		map.put("totalAmount", v.getTotalAmount());
		if( v.getLedger()!=null) {
			map.put("ledgerId", v.getLedger()!=null?v.getLedger().getId():0);
			map.put("ledgerName", v.getLedger().getName());
			map.put("ledgerType", v.getLedger().getLedgerType());
			map.put("isDebitCredit", v.isCreditDebit());
		}

		map.put("ledgerType", v.getLedgerType());
		map.put("voucherType", v.getVoucherType());
		map.put("creditAmount", v.getCreditAmount());
		map.put("debitAmount", v.getDebitAmount());
        
		if(v.isCreditDebit()) {
			double debitAmount =0;
			double igstDebitAmount =0;
			System.out.println("test1");
			double cgstSgstDebitAmount=0;
			if(v.getDebitAmount()!=null && (!v.getDebitAmount().equals(""))) {
				System.out.println("test2");
				debitAmount =Double.valueOf(v.getDebitAmount()!=null?v.getDebitAmount():"0");
				if(v.isCgstSgstPresent()) {
					 cgstSgstDebitAmount = v.getCgstDebitAmount()+v.getSgstDebitAmount();
				}else {
				   igstDebitAmount = v.getIgstDebitAmount();
				}
			}
			double creditAmount =0;
			double cgstSgstCreditAmount =0;
			double igstCreditAmount =0;
			if(v.getCreditAmount()!=null && (!v.getCreditAmount().equals(""))) {
				System.out.println("test3"+v.isCgstSgstPresent());

				creditAmount =Double.valueOf(v.getCreditAmount()!=null?v.getCreditAmount():"0");
				if(!v.isCgstSgstPresent()) {
					 cgstSgstCreditAmount = v.getCgstCreditAmount()+v.getSgstCreditAmount();
				}else {
				   igstCreditAmount = v.getIgstCreditAmount();
				}

			}
			totalCredit=totalCredit+creditAmount+cgstSgstCreditAmount+igstCreditAmount;
			totalDebit=totalDebit+debitAmount+igstDebitAmount+cgstSgstDebitAmount;

			totalAmount=totalAmount-totalDebit+totalCredit;
			map.put("totalCredit", totalCredit);
			map.put("totalDebit", totalDebit);
			map.put("totalAmount", totalAmount);

		}else {
			System.out.println("test4");
			double igstDebitAmount =0;
			double cgstSgstDebitAmount=0;
			double debitAmount =Double.valueOf(v.getDebitAmount()!=null?v.getDebitAmount():"0");
			if(v.isCgstSgstPresent()) {
				 cgstSgstDebitAmount = v.getCgstDebitAmount()+v.getSgstDebitAmount();
			}else {
			   igstDebitAmount = v.getIgstDebitAmount();
			}
			totalDebit=totalDebit+debitAmount+igstDebitAmount+cgstSgstDebitAmount;
			totalAmount=totalAmount-totalDebit;
			map.put("totalCredit", totalCredit);
			map.put("totalDebit", totalDebit);
			map.put("totalAmount", totalAmount);

		}
		
		
		return map;
	
	
	}
}
