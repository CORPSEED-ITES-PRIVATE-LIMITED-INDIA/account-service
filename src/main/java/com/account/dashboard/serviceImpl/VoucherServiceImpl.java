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
//		Voucher v = new Voucher();
//		v.setCompanyName(createVoucherDto.getCompanyName());
//
//		if(createVoucherDto.getCreditAmount()!=null ) {
//			v.setCreditDebit(true);
//			v.setCreditAmount(createVoucherDto.getCreditAmount());
//			
//			v.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
//			v.setCgst(createVoucherDto.getCgst());
//			v.setCgstCreditAmount(createVoucherDto.getCgstCreditAmount());
//			v.setCgstDebitAmount(createVoucherDto.getCgstDebitAmount());
//			
//			v.setSgst(createVoucherDto.getSgst());
//			v.setSgstCreditAmount(createVoucherDto.getSgstCreditAmount());
//			v.setSgstDebitAmount(createVoucherDto.getSgstDebitAmount());
//
//			v.setIgstPresent(createVoucherDto.isIgstPresent());
//			v.setIgst(createVoucherDto.getIgst());
//			v.setIgstCreditAmount(createVoucherDto.getIgstCreditAmount());
//			v.setIgstDebitAmount(createVoucherDto.getIgstDebitAmount());
//
//		}
//
//		if(createVoucherDto.getDebitAmount()!=null ) {
//			v.setCreditDebit(true);
//			v.setDebitAmount(createVoucherDto.getDebitAmount());
//		}
//		v.setCreateDate(new Date());
//		Optional<Ledger> ledger = ledgerRepository.findById(createVoucherDto.getLedgerId());
//		if(ledger!=null && ledger.isPresent()&&ledger.get()!=null) {
//			v.setLedger(ledger.get());
//			v.setLedgerType(ledger.get().getLedgerType());
//		}        
//		v.setPaymentType(createVoucherDto.getPaymentType());
//
//		Optional<VoucherType> voucherType = voucherTypeRepo.findById(createVoucherDto.getVoucherTypeId());
//		if(voucherType!=null &&voucherType.isPresent() && voucherType.get()!=null) {
//			v.setVoucherType(voucherType.get());
//		}
//
//		Optional<Ledger> product = ledgerRepository.findById(createVoucherDto.getProductId());
//		if(product!=null && product.isPresent()&&product.get()!=null) {
//			v.setProduct (product.get());
//		}    
//         v.setTotalAmount(createVoucherDto.getTotalAmount());
//		voucherRepository.save(v);
		flag=true;
		return flag;
	}
 
//	public Boolean createIgstCredit(CreateVoucherDto createVoucherDto) {
//		Boolean flag=false;
//		Voucher v = new Voucher();
//		v.setCompanyName(createVoucherDto.getCompanyName());
//
//		if(createVoucherDto.getIgstCreditAmount()!=0) {
//			v.setCreditDebit(true);
//			v.setCreditAmount(createVoucherDto.getIgstCreditAmount());
//
//		}
//		v.setCreateDate(new Date());
//		Optional<Ledger> ledger = ledgerRepository.findById(createVoucherDto.getLedgerId());
//		if(ledger!=null && ledger.isPresent()&&ledger.get()!=null) {
//			v.setLedger(ledger.get());
//			v.setLedgerType(ledger.get().getLedgerType());
//		}        
//		v.setPaymentType(createVoucherDto.getPaymentType());
//
//		Optional<VoucherType> voucherType = voucherTypeRepo.findById(createVoucherDto.getVoucherTypeId());
//		if(voucherType!=null &&voucherType.isPresent() && voucherType.get()!=null) {
//			v.setVoucherType(voucherType.get());
//		}
//
//		Ledger product = ledgerRepository.findByName("IGST");
//		if(product!=null) {
//			v.setProduct (product);
//		}    
//         v.setTotalAmount(createVoucherDto.getIgstCreditAmount());
//		voucherRepository.save(v);
//		flag=true;
//		return flag;
//	}
	
	
	public Voucher createIgstCredit(CreateVoucherDto createVoucherDto,double igstAmount,String type) {
		Boolean flag=false;
		Voucher v = new Voucher();
		v.setCompanyName(createVoucherDto.getCompanyName());

		if(createVoucherDto.getIgstCreditAmount()!=0) {
			v.setCreditDebit(true);
			if("credit".equals(type)) {
				v.setCreditAmount(igstAmount);
			}else {
				v.setDebitAmount(igstAmount);
			}
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

		Ledger product = ledgerRepository.findByName("IGST");
		if(product!=null) {
			v.setProduct (product);
		}    
         v.setTotalAmount(igstAmount);
		voucherRepository.save(v);
		flag=true;
		return v;
	}
	
	public Voucher createCgstCredit(CreateVoucherDto createVoucherDto,double cgstAmount,String type) {
		Boolean flag=false;
		Voucher v = new Voucher();
		v.setCompanyName(createVoucherDto.getCompanyName());

		if(createVoucherDto.getIgstCreditAmount()!=0) {
			v.setCreditDebit(true);
			if("credit".equals(type)) {
				v.setCreditAmount(cgstAmount);
			}else{
				v.setDebitAmount(cgstAmount);
			}

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

		Ledger product = ledgerRepository.findByName("CGST");
		if(product!=null) {
			v.setProduct (product);
		}    
         v.setTotalAmount(cgstAmount);
		voucherRepository.save(v);
		flag=true;
		return v;
	}
	public Voucher createSgstCredit(CreateVoucherDto createVoucherDto,double sgstAmount,String type) {
		Boolean flag=false;
		Voucher v = new Voucher();
		v.setCompanyName(createVoucherDto.getCompanyName());

		if(sgstAmount!=0) {
			v.setCreditDebit(true);
			if("credit".equals(type)) {
				v.setCreditAmount(sgstAmount);

			}else {
				v.setDebitAmount(sgstAmount);

			}
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

		Ledger product = ledgerRepository.findByName("SGST");
		if(product!=null) {
			v.setProduct (product);
		}    
         v.setTotalAmount(sgstAmount);
		voucherRepository.save(v);
		flag=true;
		return v;
	}
	public Boolean createVoucherV3(CreateVoucherDto createVoucherDto) {

		Boolean flag=false;
		Voucher v = new Voucher();
		v.setCompanyName(createVoucherDto.getCompanyName());

		if(createVoucherDto.getCreditAmount() !=0 ) {
			v.setCreditDebit(true);
			v.setCreditAmount(createVoucherDto.getCreditAmount());
			
			v.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
            if(createVoucherDto.getCgstCreditAmount()>0) {
    			Voucher cgstCredit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"credit");
    			v.setCgstCreditVoucher(cgstCredit);
            }
            if(createVoucherDto.getCgstDebitAmount()>0) {
    			Voucher cgstDedit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"debit");
                v.setCgstDebitVoucher(cgstDedit);

            }
//			Voucher cgstCredit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"credit");
//			Voucher cgstDedit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"debit");
//			v.setCgstCreditVoucher(cgstCredit);
//            v.setCgstDebitVoucher(cgstDedit);
            if(createVoucherDto.getSgstCreditAmount()>0) {
    			Voucher sgstCredit= createSgstCredit(createVoucherDto,createVoucherDto.getSgstCreditAmount(),"credit");
                v.setSgstCreditVoucher(sgstCredit);
            }
            if(createVoucherDto.getSgstDebitAmount()>0) {
                Voucher sgstDebit = createSgstCredit(createVoucherDto,createVoucherDto.getSgstDebitAmount(),"debit");
                v.setSgstDebitVoucher(sgstDebit);
            }


			v.setIgstPresent(createVoucherDto.isIgstPresent());
			if(createVoucherDto.getIgstCreditAmount()>0) {
				Voucher igstCreditAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstCreditAmount(),"credit");
				v.setIgstCreditVoucher(igstCreditAmount);

			}
				
			if(createVoucherDto.getIgstDebitAmount()>0) {
				Voucher igstDebitAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstDebitAmount(),"debit");
				v.setIgstDebitVoucher(igstDebitAmount);
			}

		}

		if(createVoucherDto.getDebitAmount()!=0 ) {
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
	
	public Boolean createVoucherV2Old(CreateVoucherDto createVoucherDto) {

		Boolean flag=false;
		
		long voucherTypeId = createVoucherDto.getVoucherTypeId();
		Optional<VoucherType> voucherTypeOp = voucherTypeRepo.findById(createVoucherDto.getVoucherTypeId());
		VoucherType voucherType=null;
		if(voucherTypeOp!=null &&voucherTypeOp.isPresent() && voucherTypeOp.get()!=null) {
			voucherType=voucherTypeOp.get();
		}
		String voucherTypeName = voucherType!=null?voucherType.getName():"NA";
		if("Sales Voucher".equals(voucherTypeName)) {
			Voucher v = new Voucher();
			v.setCompanyName(createVoucherDto.getCompanyName());

			if(createVoucherDto.getCreditAmount() !=0 ) {
				v.setCreditDebit(true);
				v.setCreditAmount(createVoucherDto.getCreditAmount());
				
				v.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
	            if(createVoucherDto.getCgstCreditAmount()>0) {
	    			Voucher cgstCredit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"credit");
	    			v.setCgstCreditVoucher(cgstCredit);
	            }
	            if(createVoucherDto.getCgstDebitAmount()>0) {
	    			Voucher cgstDedit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"debit");
	                v.setCgstDebitVoucher(cgstDedit);

	            }
 
	            if(createVoucherDto.getSgstCreditAmount()>0) {
	    			Voucher sgstCredit= createSgstCredit(createVoucherDto,createVoucherDto.getSgstCreditAmount(),"credit");
	                v.setSgstCreditVoucher(sgstCredit);
	            }
	            if(createVoucherDto.getSgstDebitAmount()>0) {
	                Voucher sgstDebit = createSgstCredit(createVoucherDto,createVoucherDto.getSgstDebitAmount(),"debit");
	                v.setSgstDebitVoucher(sgstDebit);
	            }


				v.setIgstPresent(createVoucherDto.isIgstPresent());
				if(createVoucherDto.getIgstCreditAmount()>0) {
					Voucher igstCreditAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstCreditAmount(),"credit");
					v.setIgstCreditVoucher(igstCreditAmount);

				}
					
				if(createVoucherDto.getIgstDebitAmount()>0) {
					Voucher igstDebitAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstDebitAmount(),"debit");
					v.setIgstDebitVoucher(igstDebitAmount);
				}

			}

			if(createVoucherDto.getDebitAmount()!=0 ) {
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

			v.setVoucherType(voucherType);


			Optional<Ledger> product = ledgerRepository.findById(createVoucherDto.getProductId());
			if(product!=null && product.isPresent()&&product.get()!=null) {
				v.setProduct (product.get());
			}    
	         v.setTotalAmount(createVoucherDto.getTotalAmount());
			voucherRepository.save(v);
			flag=true;

		}else if("Recipt Voucher".equals(voucherTypeName)){

			Voucher v = new Voucher();
			v.setCompanyName(createVoucherDto.getCompanyName());

			if(createVoucherDto.getCreditAmount() !=0 ) {
				v.setCreditDebit(true);
				v.setCreditAmount(createVoucherDto.getCreditAmount());
				
				v.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
	            if(createVoucherDto.getCgstCreditAmount()>0) {
	    			Voucher cgstCredit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"credit");
	    			v.setCgstCreditVoucher(cgstCredit);
	            }
	            if(createVoucherDto.getCgstDebitAmount()>0) {
	    			Voucher cgstDedit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"debit");
	                v.setCgstDebitVoucher(cgstDedit);

	            }

	            if(createVoucherDto.getSgstCreditAmount()>0) {
	    			Voucher sgstCredit= createSgstCredit(createVoucherDto,createVoucherDto.getSgstCreditAmount(),"credit");
	                v.setSgstCreditVoucher(sgstCredit);
	            }
	            if(createVoucherDto.getSgstDebitAmount()>0) {
	                Voucher sgstDebit = createSgstCredit(createVoucherDto,createVoucherDto.getSgstDebitAmount(),"debit");
	                v.setSgstDebitVoucher(sgstDebit);
	            }


				v.setIgstPresent(createVoucherDto.isIgstPresent());
				if(createVoucherDto.getIgstCreditAmount()>0) {
					Voucher igstCreditAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstCreditAmount(),"credit");
					v.setIgstCreditVoucher(igstCreditAmount);

				}
					
				if(createVoucherDto.getIgstDebitAmount()>0) {
					Voucher igstDebitAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstDebitAmount(),"debit");
					v.setIgstDebitVoucher(igstDebitAmount);
				}

			}

			if(createVoucherDto.getDebitAmount()!=0 ) {
				v.setCreditDebit(true);
				v.setDebitAmount(createVoucherDto.getDebitAmount());
			}
			v.setCreateDate(new Date());
			Optional<Ledger> ledger = ledgerRepository.findById(createVoucherDto.getProductId());
			if(ledger!=null && ledger.isPresent()&&ledger.get()!=null) {
				v.setLedger(ledger.get());
				v.setLedgerType(ledger.get().getLedgerType());
			}        
			v.setPaymentType(createVoucherDto.getPaymentType());

			v.setVoucherType(voucherType);


			Optional<Ledger> product = ledgerRepository.findById(createVoucherDto.getLedgerId());
			if(product!=null && product.isPresent()&&product.get()!=null) {
				v.setProduct (product.get());
			}    
	         v.setTotalAmount(createVoucherDto.getTotalAmount());
			voucherRepository.save(v);
			flag=true;

		
		}else {

			Voucher v = new Voucher();
			v.setCompanyName(createVoucherDto.getCompanyName());

			if(createVoucherDto.getCreditAmount() !=0 ) {
				v.setCreditDebit(true);
				v.setCreditAmount(createVoucherDto.getCreditAmount());
				
				v.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
	            if(createVoucherDto.getCgstCreditAmount()>0) {
	    			Voucher cgstCredit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"credit");
	    			v.setCgstCreditVoucher(cgstCredit);
	            }
	            if(createVoucherDto.getCgstDebitAmount()>0) {
	    			Voucher cgstDedit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"debit");
	                v.setCgstDebitVoucher(cgstDedit);

	            }
//				Voucher cgstCredit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"credit");
//				Voucher cgstDedit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"debit");
//				v.setCgstCreditVoucher(cgstCredit);
//	            v.setCgstDebitVoucher(cgstDedit);
	            if(createVoucherDto.getSgstCreditAmount()>0) {
	    			Voucher sgstCredit= createSgstCredit(createVoucherDto,createVoucherDto.getSgstCreditAmount(),"credit");
	                v.setSgstCreditVoucher(sgstCredit);
	            }
	            if(createVoucherDto.getSgstDebitAmount()>0) {
	                Voucher sgstDebit = createSgstCredit(createVoucherDto,createVoucherDto.getSgstDebitAmount(),"debit");
	                v.setSgstDebitVoucher(sgstDebit);
	            }


				v.setIgstPresent(createVoucherDto.isIgstPresent());
				if(createVoucherDto.getIgstCreditAmount()>0) {
					Voucher igstCreditAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstCreditAmount(),"credit");
					v.setIgstCreditVoucher(igstCreditAmount);

				}
					
				if(createVoucherDto.getIgstDebitAmount()>0) {
					Voucher igstDebitAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstDebitAmount(),"debit");
					v.setIgstDebitVoucher(igstDebitAmount);
				}

			}

			if(createVoucherDto.getDebitAmount()!=0 ) {
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

			v.setVoucherType(voucherType);


			Optional<Ledger> product = ledgerRepository.findById(createVoucherDto.getProductId());
			if(product!=null && product.isPresent()&&product.get()!=null) {
				v.setProduct (product.get());
			}    
	         v.setTotalAmount(createVoucherDto.getTotalAmount());
			voucherRepository.save(v);
			flag=true;

		
		}
		
		return flag;
	}


	public Boolean createVoucherV2(CreateVoucherDto createVoucherDto) {

		Boolean flag=false;
		
		long voucherTypeId = createVoucherDto.getVoucherTypeId();
		Optional<VoucherType> voucherTypeOp = voucherTypeRepo.findById(createVoucherDto.getVoucherTypeId());
		VoucherType voucherType=null;
		if(voucherTypeOp!=null &&voucherTypeOp.isPresent() && voucherTypeOp.get()!=null) {
			voucherType=voucherTypeOp.get();
		}
		String voucherTypeName = voucherType!=null?voucherType.getName():"NA";
		if("Sales Voucher".equals(voucherTypeName)|| "Debit Note Voucher".equals(voucherTypeName)) {
			
			// sales entry
			Voucher v = new Voucher();
			v.setCompanyName(createVoucherDto.getCompanyName());

			if(createVoucherDto.getCreditAmount() !=0 ) {
				v.setCreditDebit(true);
				v.setCreditAmount(createVoucherDto.getCreditAmount());			
				v.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
	            if(createVoucherDto.getCgstCreditAmount()>0) {
	    			Voucher cgstCredit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"credit");
	    			v.setCgstCreditVoucher(cgstCredit);
	            }
	            if(createVoucherDto.getCgstDebitAmount()>0) {
	    			Voucher cgstDedit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"debit");
	                v.setCgstDebitVoucher(cgstDedit);
	            }
	            if(createVoucherDto.getSgstCreditAmount()>0) {
	    			Voucher sgstCredit= createSgstCredit(createVoucherDto,createVoucherDto.getSgstCreditAmount(),"credit");
	                v.setSgstCreditVoucher(sgstCredit);
	            }
	            if(createVoucherDto.getSgstDebitAmount()>0) {
	                Voucher sgstDebit = createSgstCredit(createVoucherDto,createVoucherDto.getSgstDebitAmount(),"debit");
	                v.setSgstDebitVoucher(sgstDebit);
	            }
				v.setIgstPresent(createVoucherDto.isIgstPresent());
				if(createVoucherDto.getIgstCreditAmount()>0) {
					Voucher igstCreditAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstCreditAmount(),"credit");
					v.setIgstCreditVoucher(igstCreditAmount);
				}					
				if(createVoucherDto.getIgstDebitAmount()>0) {
					Voucher igstDebitAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstDebitAmount(),"debit");
					v.setIgstDebitVoucher(igstDebitAmount);
				}
			}

			if(createVoucherDto.getDebitAmount()!=0 ) {
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

			v.setVoucherType(voucherType);


			Optional<Ledger> product = ledgerRepository.findById(createVoucherDto.getProductId());
			if(product!=null && product.isPresent()&&product.get()!=null) {
				v.setProduct (product.get());
			}    
	         v.setTotalAmount(createVoucherDto.getTotalAmount());
			voucherRepository.save(v);
			flag=true;
			
			
			

			
			// sales entry
			Voucher v2 = new Voucher();
			v2.setCompanyName(createVoucherDto.getCompanyName());

			if(createVoucherDto.getCreditAmount() !=0 ) {
				v2.setCreditDebit(true);
				v2.setDebitAmount(createVoucherDto.getCreditAmount());			
				v2.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
	            if(createVoucherDto.getCgstCreditAmount()>0) {
	    			Voucher cgstCredit2 = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"debit");
	    			v2.setCgstDebitVoucher(cgstCredit2);
	            }
	            if(createVoucherDto.getCgstDebitAmount()>0) {
	    			Voucher cgstDedit2 = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"credit");
	    			v2.setCgstDebitVoucher(cgstDedit2);
	            }
	            if(createVoucherDto.getSgstCreditAmount()>0) {
	    			Voucher sgstCredit2= createSgstCredit(createVoucherDto,createVoucherDto.getSgstCreditAmount(),"debit");
	                v2.setSgstDebitVoucher(sgstCredit2);
	            }
	            if(createVoucherDto.getSgstDebitAmount()>0) {
	                Voucher sgstDebit2 = createSgstCredit(createVoucherDto,createVoucherDto.getSgstDebitAmount(),"credit");
	                v2.setSgstCreditVoucher(sgstDebit2);
	            }
				v2.setIgstPresent(createVoucherDto.isIgstPresent());
				if(createVoucherDto.getIgstCreditAmount()>0) {
					Voucher igstCreditAmount2 = createIgstCredit(createVoucherDto, createVoucherDto.getIgstCreditAmount(),"debit");
					v2.setIgstDebitVoucher(igstCreditAmount2);
				}					
				if(createVoucherDto.getIgstDebitAmount()>0) {
					Voucher igstDebitAmount2 = createIgstCredit(createVoucherDto, createVoucherDto.getIgstDebitAmount(),"credit");
					v2.setIgstCreditVoucher(igstDebitAmount2);
				}
			}

			if(createVoucherDto.getDebitAmount()!=0 ) {
				v2.setCreditDebit(true);
				v2.setCreditAmount(createVoucherDto.getDebitAmount());
			}
			v2.setCreateDate(new Date());
			Optional<Ledger> ledger2 = ledgerRepository.findById(createVoucherDto.getProductId());
			if(ledger2!=null && ledger2.isPresent()&&ledger2.get()!=null) {
				v2.setLedger(ledger2.get());
				v2.setLedgerType(ledger2.get().getLedgerType());
			}        
			v2.setPaymentType(createVoucherDto.getPaymentType());

			v2.setVoucherType(voucherType);


			Optional<Ledger> product2 = ledgerRepository.findById(createVoucherDto.getLedgerId());
			if(product2!=null && product2.isPresent()&&product2.get()!=null) {
				v2.setProduct (product2.get());
			}    
	         v2.setTotalAmount(createVoucherDto.getTotalAmount());
	         v2.setImpact("indirect");
			voucherRepository.save(v2);
			flag=true;

		
			
			
			
			
			
			
			
			
			

		}else if("Recipt Voucher".equals(voucherTypeName)){
			
            //  Recipt Forward
			Voucher v = new Voucher();
			v.setCompanyName(createVoucherDto.getCompanyName());

			if(createVoucherDto.getCreditAmount() !=0 ) {
				v.setCreditDebit(true);
				v.setCreditAmount(createVoucherDto.getCreditAmount());
				
				v.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
	            if(createVoucherDto.getCgstCreditAmount()>0) {
	    			Voucher cgstCredit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"credit");
	    			v.setCgstCreditVoucher(cgstCredit);
	            }
	            if(createVoucherDto.getCgstDebitAmount()>0) {
	    			Voucher cgstDedit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"debit");
	                v.setCgstDebitVoucher(cgstDedit);

	            }

	            if(createVoucherDto.getSgstCreditAmount()>0) {
	    			Voucher sgstCredit= createSgstCredit(createVoucherDto,createVoucherDto.getSgstCreditAmount(),"credit");
	                v.setSgstCreditVoucher(sgstCredit);
	            }
	            if(createVoucherDto.getSgstDebitAmount()>0) {
	                Voucher sgstDebit = createSgstCredit(createVoucherDto,createVoucherDto.getSgstDebitAmount(),"debit");
	                v.setSgstDebitVoucher(sgstDebit);
	            }


				v.setIgstPresent(createVoucherDto.isIgstPresent());
				if(createVoucherDto.getIgstCreditAmount()>0) {
					Voucher igstCreditAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstCreditAmount(),"credit");
					v.setIgstCreditVoucher(igstCreditAmount);

				}
					
				if(createVoucherDto.getIgstDebitAmount()>0) {
					Voucher igstDebitAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstDebitAmount(),"debit");
					v.setIgstDebitVoucher(igstDebitAmount);
				}

			}

			if(createVoucherDto.getDebitAmount()!=0 ) {
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

			v.setVoucherType(voucherType);


			Optional<Ledger> product = ledgerRepository.findById(createVoucherDto.getProductId());
			if(product!=null && product.isPresent()&&product.get()!=null) {
				v.setProduct (product.get());
			}    
	         v.setTotalAmount(createVoucherDto.getTotalAmount());
			voucherRepository.save(v);
			flag=true;
			
			
			
            //  Recipt backword
			Voucher v2 = new Voucher();
			v2.setCompanyName(createVoucherDto.getCompanyName());

			if(createVoucherDto.getCreditAmount() !=0 ) {
				v2.setCreditDebit(true);
				v2.setDebitAmount(createVoucherDto.getCreditAmount());
				
				v2.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
	            if(createVoucherDto.getCgstCreditAmount()>0) {
	    			Voucher cgstCredit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"debit");
	    			v2.setCgstDebitVoucher(cgstCredit);
	            }
	            if(createVoucherDto.getCgstDebitAmount()>0) {
	    			Voucher cgstDedit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"credit");
	                v2.setCgstCreditVoucher(cgstDedit);

	            }

	            if(createVoucherDto.getSgstCreditAmount()>0) {
	    			Voucher sgstCredit= createSgstCredit(createVoucherDto,createVoucherDto.getSgstCreditAmount(),"debit");
	                v2.setSgstDebitVoucher(sgstCredit);
	            }
	            if(createVoucherDto.getSgstDebitAmount()>0) {
	                Voucher sgstDebit = createSgstCredit(createVoucherDto,createVoucherDto.getSgstDebitAmount(),"credit");
	                v2.setSgstCreditVoucher(sgstDebit);
	            }


				v2.setIgstPresent(createVoucherDto.isIgstPresent());
				if(createVoucherDto.getIgstCreditAmount()>0) {
					Voucher igstCreditAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstCreditAmount(),"debit");
					v2.setIgstDebitVoucher(igstCreditAmount);

				}
					
				if(createVoucherDto.getIgstDebitAmount()>0) {
					Voucher igstDebitAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstDebitAmount(),"credit");
					v2.setIgstCreditVoucher(igstDebitAmount);
				}

			}

			if(createVoucherDto.getDebitAmount()!=0 ) {
				v2.setCreditDebit(true);
				v2.setCreditAmount(createVoucherDto.getDebitAmount());
			}
			v2.setCreateDate(new Date());
			Optional<Ledger> ledger2 = ledgerRepository.findById(createVoucherDto.getProductId());
			if(ledger2!=null && ledger2.isPresent()&&ledger2.get()!=null) {
				v2.setLedger(ledger2.get());
				v2.setLedgerType(ledger2.get().getLedgerType());
			}        
			v2.setPaymentType(createVoucherDto.getPaymentType());

			v2.setVoucherType(voucherType);


			Optional<Ledger> product2 = ledgerRepository.findById(createVoucherDto.getLedgerId());
			if(product2!=null && product2.isPresent()&&product2.get()!=null) {
				v2.setProduct (product.get());
			}    
	         v2.setTotalAmount(createVoucherDto.getTotalAmount());
			voucherRepository.save(v2);
			flag=true;
			

		
		}else if("Purchase Voucher".equals(voucherTypeName)&&("Credit Note Voucher".equals(voucherTypeName))){

			
			// sales entry
			Voucher v = new Voucher();
			v.setCompanyName(createVoucherDto.getCompanyName());

			if(createVoucherDto.getCreditAmount() !=0 ) {
				v.setCreditDebit(true);
				v.setCreditAmount(createVoucherDto.getCreditAmount());			
				v.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
	            if(createVoucherDto.getCgstCreditAmount()>0) {
	    			Voucher cgstCredit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"credit");
	    			v.setCgstCreditVoucher(cgstCredit);
	            }
	            if(createVoucherDto.getCgstDebitAmount()>0) {
	    			Voucher cgstDedit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"debit");
	                v.setCgstDebitVoucher(cgstDedit);
	            }
	            if(createVoucherDto.getSgstCreditAmount()>0) {
	    			Voucher sgstCredit= createSgstCredit(createVoucherDto,createVoucherDto.getSgstCreditAmount(),"credit");
	                v.setSgstCreditVoucher(sgstCredit);
	            }
	            if(createVoucherDto.getSgstDebitAmount()>0) {
	                Voucher sgstDebit = createSgstCredit(createVoucherDto,createVoucherDto.getSgstDebitAmount(),"debit");
	                v.setSgstDebitVoucher(sgstDebit);
	            }
				v.setIgstPresent(createVoucherDto.isIgstPresent());
				if(createVoucherDto.getIgstCreditAmount()>0) {
					Voucher igstCreditAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstCreditAmount(),"credit");
					v.setIgstCreditVoucher(igstCreditAmount);
				}					
				if(createVoucherDto.getIgstDebitAmount()>0) {
					Voucher igstDebitAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstDebitAmount(),"debit");
					v.setIgstDebitVoucher(igstDebitAmount);
				}
			}

			if(createVoucherDto.getDebitAmount()!=0 ) {
				v.setCreditDebit(true);
				v.setDebitAmount(createVoucherDto.getDebitAmount());
			}
			v.setCreateDate(new Date());
			Optional<Ledger> ledger = ledgerRepository.findById(createVoucherDto.getProductId());
			if(ledger!=null && ledger.isPresent()&&ledger.get()!=null) {
				v.setLedger(ledger.get());
				v.setLedgerType(ledger.get().getLedgerType());
			}        
			v.setPaymentType(createVoucherDto.getPaymentType());

			v.setVoucherType(voucherType);


			Optional<Ledger> product = ledgerRepository.findById(createVoucherDto.getLedgerId());
			if(product!=null && product.isPresent()&&product.get()!=null) {
				v.setProduct (product.get());
			}    
	         v.setTotalAmount(createVoucherDto.getTotalAmount());
			voucherRepository.save(v);
			flag=true;
			
			
			

			
			// sales entry
			Voucher v2 = new Voucher();
			v2.setCompanyName(createVoucherDto.getCompanyName());

			if(createVoucherDto.getCreditAmount() !=0 ) {
				v2.setCreditDebit(true);
				v2.setDebitAmount(createVoucherDto.getCreditAmount());			
				v2.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
	            if(createVoucherDto.getCgstCreditAmount()>0) {
	    			Voucher cgstCredit2 = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"debit");
	    			v2.setCgstDebitVoucher(cgstCredit2);
	            }
	            if(createVoucherDto.getCgstDebitAmount()>0) {
	    			Voucher cgstDedit2 = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"credit");
	    			v2.setCgstDebitVoucher(cgstDedit2);
	            }
	            if(createVoucherDto.getSgstCreditAmount()>0) {
	    			Voucher sgstCredit2= createSgstCredit(createVoucherDto,createVoucherDto.getSgstCreditAmount(),"debit");
	                v2.setSgstDebitVoucher(sgstCredit2);
	            }
	            if(createVoucherDto.getSgstDebitAmount()>0) {
	                Voucher sgstDebit2 = createSgstCredit(createVoucherDto,createVoucherDto.getSgstDebitAmount(),"credit");
	                v2.setSgstCreditVoucher(sgstDebit2);
	            }
				v2.setIgstPresent(createVoucherDto.isIgstPresent());
				if(createVoucherDto.getIgstCreditAmount()>0) {
					Voucher igstCreditAmount2 = createIgstCredit(createVoucherDto, createVoucherDto.getIgstCreditAmount(),"debit");
					v2.setIgstDebitVoucher(igstCreditAmount2);
				}					
				if(createVoucherDto.getIgstDebitAmount()>0) {
					Voucher igstDebitAmount2 = createIgstCredit(createVoucherDto, createVoucherDto.getIgstDebitAmount(),"credit");
					v2.setIgstCreditVoucher(igstDebitAmount2);
				}
			}

			if(createVoucherDto.getDebitAmount()!=0 ) {
				v2.setCreditDebit(true);
				v2.setCreditAmount(createVoucherDto.getDebitAmount());
			}
			v2.setCreateDate(new Date());
			Optional<Ledger> ledger2 = ledgerRepository.findById(createVoucherDto.getLedgerId());
			if(ledger2!=null && ledger2.isPresent()&&ledger2.get()!=null) {
				v2.setLedger(ledger2.get());
				v2.setLedgerType(ledger2.get().getLedgerType());
			}        
			v2.setPaymentType(createVoucherDto.getPaymentType());

			v2.setVoucherType(voucherType);


			Optional<Ledger> product2 = ledgerRepository.findById(createVoucherDto.getProductId());
			if(product2!=null && product2.isPresent()&&product2.get()!=null) {
				v2.setProduct (product2.get());
			}    
	         v2.setTotalAmount(createVoucherDto.getTotalAmount());
	         v2.setImpact("indirect");
			voucherRepository.save(v2);
			flag=true;

		
		}else {
			
			// sales entry
			Voucher v = new Voucher();
			v.setCompanyName(createVoucherDto.getCompanyName());

			if(createVoucherDto.getCreditAmount() !=0 ) {
				v.setCreditDebit(true);
				v.setCreditAmount(createVoucherDto.getCreditAmount());			
				v.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
	            if(createVoucherDto.getCgstCreditAmount()>0) {
	    			Voucher cgstCredit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"credit");
	    			v.setCgstCreditVoucher(cgstCredit);
	            }
	            if(createVoucherDto.getCgstDebitAmount()>0) {
	    			Voucher cgstDedit = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"debit");
	                v.setCgstDebitVoucher(cgstDedit);
	            }
	            if(createVoucherDto.getSgstCreditAmount()>0) {
	    			Voucher sgstCredit= createSgstCredit(createVoucherDto,createVoucherDto.getSgstCreditAmount(),"credit");
	                v.setSgstCreditVoucher(sgstCredit);
	            }
	            if(createVoucherDto.getSgstDebitAmount()>0) {
	                Voucher sgstDebit = createSgstCredit(createVoucherDto,createVoucherDto.getSgstDebitAmount(),"debit");
	                v.setSgstDebitVoucher(sgstDebit);
	            }
				v.setIgstPresent(createVoucherDto.isIgstPresent());
				if(createVoucherDto.getIgstCreditAmount()>0) {
					Voucher igstCreditAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstCreditAmount(),"credit");
					v.setIgstCreditVoucher(igstCreditAmount);
				}					
				if(createVoucherDto.getIgstDebitAmount()>0) {
					Voucher igstDebitAmount = createIgstCredit(createVoucherDto, createVoucherDto.getIgstDebitAmount(),"debit");
					v.setIgstDebitVoucher(igstDebitAmount);
				}
			}

			if(createVoucherDto.getDebitAmount()!=0 ) {
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

			v.setVoucherType(voucherType);


			Optional<Ledger> product = ledgerRepository.findById(createVoucherDto.getProductId());
			if(product!=null && product.isPresent()&&product.get()!=null) {
				v.setProduct (product.get());
			}    
	         v.setTotalAmount(createVoucherDto.getTotalAmount());
			voucherRepository.save(v);
			flag=true;
			
			
			

			
			// sales entry
			Voucher v2 = new Voucher();
			v2.setCompanyName(createVoucherDto.getCompanyName());

			if(createVoucherDto.getCreditAmount() !=0 ) {
				v2.setCreditDebit(true);
				v2.setDebitAmount(createVoucherDto.getCreditAmount());			
				v2.setCgstSgstPresent(createVoucherDto.isCgstIgstPresent());
	            if(createVoucherDto.getCgstCreditAmount()>0) {
	    			Voucher cgstCredit2 = createCgstCredit(createVoucherDto,createVoucherDto.getCgstCreditAmount(),"debit");
	    			v2.setCgstDebitVoucher(cgstCredit2);
	            }
	            if(createVoucherDto.getCgstDebitAmount()>0) {
	    			Voucher cgstDedit2 = createCgstCredit(createVoucherDto,createVoucherDto.getCgstDebitAmount(),"credit");
	    			v2.setCgstDebitVoucher(cgstDedit2);
	            }
	            if(createVoucherDto.getSgstCreditAmount()>0) {
	    			Voucher sgstCredit2= createSgstCredit(createVoucherDto,createVoucherDto.getSgstCreditAmount(),"debit");
	                v2.setSgstDebitVoucher(sgstCredit2);
	            }
	            if(createVoucherDto.getSgstDebitAmount()>0) {
	                Voucher sgstDebit2 = createSgstCredit(createVoucherDto,createVoucherDto.getSgstDebitAmount(),"credit");
	                v2.setSgstCreditVoucher(sgstDebit2);
	            }
				v2.setIgstPresent(createVoucherDto.isIgstPresent());
				if(createVoucherDto.getIgstCreditAmount()>0) {
					Voucher igstCreditAmount2 = createIgstCredit(createVoucherDto, createVoucherDto.getIgstCreditAmount(),"debit");
					v2.setIgstDebitVoucher(igstCreditAmount2);
				}					
				if(createVoucherDto.getIgstDebitAmount()>0) {
					Voucher igstDebitAmount2 = createIgstCredit(createVoucherDto, createVoucherDto.getIgstDebitAmount(),"credit");
					v2.setIgstCreditVoucher(igstDebitAmount2);
				}
			}

			if(createVoucherDto.getDebitAmount()!=0 ) {
				v2.setCreditDebit(true);
				v2.setCreditAmount(createVoucherDto.getDebitAmount());
			}
			v2.setCreateDate(new Date());
			Optional<Ledger> ledger2 = ledgerRepository.findById(createVoucherDto.getProductId());
			if(ledger2!=null && ledger2.isPresent()&&ledger2.get()!=null) {
				v2.setLedger(ledger2.get());
				v2.setLedgerType(ledger2.get().getLedgerType());
			}        
			v2.setPaymentType(createVoucherDto.getPaymentType());

			v2.setVoucherType(voucherType);


			Optional<Ledger> product2 = ledgerRepository.findById(createVoucherDto.getLedgerId());
			if(product2!=null && product2.isPresent()&&product2.get()!=null) {
				v2.setProduct (product2.get());
			}    
	         v2.setTotalAmount(createVoucherDto.getTotalAmount());
	         v2.setImpact("indirect");
			voucherRepository.save(v2);
			flag=true;

		}
		
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
			map.put("cgstCreditAmount", v.getCgstCreditVoucher()!=null?v.getCgstCreditVoucher().getCreditAmount():0);
			map.put("cgstDebitAmount", v.getCgstDebitVoucher()!=null?v.getCgstDebitVoucher().getDebitAmount():0);

			map.put("sgstCreditAmount", v.getSgstCreditVoucher()!=null?v.getSgstCreditVoucher().getCreditAmount():0);
			map.put("sgstDebitAmount", v.getSgstDebitVoucher()!=null?v.getSgstDebitVoucher().getDebitAmount():0);

			map.put("igst", v.getIgst());
			map.put("igstCreditAmount", v.getIgstCreditVoucher()!=null?v.getIgstCreditVoucher().getCreditAmount():0);
			map.put("igstDebitAmount", v.getIgstDebitVoucher()!=null?v.getIgstDebitVoucher().getDebitAmount():0);


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

	public List<Map<String,Object>>  getAllVoucherForExport() {
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
			map.put("cgstCreditAmount", v.getCgstCreditVoucher()!=null?v.getCgstCreditVoucher().getCreditAmount():0);
			map.put("cgstDebitAmount", v.getCgstDebitVoucher()!=null?v.getCgstDebitVoucher().getDebitAmount():0);

			map.put("sgstCreditAmount", v.getSgstCreditVoucher()!=null?v.getSgstCreditVoucher().getCreditAmount():0);
			map.put("sgstDebitAmount", v.getSgstDebitVoucher()!=null?v.getSgstDebitVoucher().getDebitAmount():0);

			map.put("igst", v.getIgst());
			map.put("igstCreditAmount", v.getIgstCreditVoucher()!=null?v.getIgstCreditVoucher().getCreditAmount():0);
			map.put("igstDebitAmount", v.getIgstDebitVoucher()!=null?v.getIgstDebitVoucher().getDebitAmount():0);


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
				long debitAmount =(long)v.getDebitAmount();
				long creditAmount =(long)v.getCreditAmount();



				if(v.getDebitAmount()!=0) {
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
				long debitAmount =(long)v.getDebitAmount();
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
//		List<Voucher> vS = voucherRepository.findAllByLedgerIdOrProductId(ledgerId,ledgerId);
		
//		List<Voucher> productList = voucherRepository.findAllByProductId(ledgerId);
//		System.out.println(productList);
//		if(voucherList!=null) {
//			voucherList.addAll(productList);
//		}else {
//			voucherList=productList;
//		}

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

		        double creditGstAmount =0;
		        double debitGstAmount =0;
				double debitAmount =(long)v.getDebitAmount();
				System.out.println("Voucher id . . ."+v);
				System.out.println("Voucher id . . ."+debitAmount);
				System.out.println("Voucher id . . ."+v.getCreditAmount());

				double creditAmount =v.getCreditAmount();


				if(v.getDebitAmount()!=0) {
			           System.out.println("aaaaaaaaaaa333");

					if(v.isIgstPresent()) {
				           System.out.println("aaaaaaaaaaa444");

						debitGstAmount =v.getIgstDebitVoucher().getDebitAmount();
					}
					if(!v.isCgstSgstPresent()) {
				           System.out.println("aaaaaaaaaaa555");
				           double cgstDebitVoucher = v.getCgstDebitVoucher()!=null?v.getCgstDebitVoucher().getDebitAmount():0;
				           double sgstDebitVoucher = v.getSgstDebitVoucher()!=null?v.getSgstDebitVoucher().getDebitAmount():0;
						debitGstAmount =cgstDebitVoucher+sgstDebitVoucher;
					}
				}else {
			           System.out.println("aaaaaaaaaaa666");

					
					if(v.isIgstPresent()) {
				           System.out.println("aaaaaaaaaaa777");

						creditGstAmount =(v.getIgstCreditVoucher().getCreditAmount());
						creditAmount=creditAmount;
					}
					if(!v.isCgstSgstPresent()) {
				           System.out.println("aaaaaaaaaaa888");
				           double cgstCreditVoucher = v.getCgstCreditVoucher()!=null?v.getCgstCreditVoucher().getCreditAmount():0;
				           double sgstCreditVoucher = v.getSgstCreditVoucher()!=null?v.getSgstCreditVoucher().getCreditAmount():0;
						creditGstAmount =cgstCreditVoucher+sgstCreditVoucher;

						creditAmount=creditAmount;

					}
				}
				totalCredit=totalCredit+creditAmount+creditGstAmount;
				totalDebit=totalDebit+debitAmount+debitGstAmount;

				totalAmount=totalAmount-debitAmount-debitGstAmount+creditAmount+creditGstAmount;
			}else {
				double debitAmount =v.getDebitAmount();
				double debitGstAmount =0;   	
				
				//        		totalAmount=totalAmount+debitAmount;
				if(v.isIgstPresent()) {
					debitGstAmount =(v.getIgstCreditVoucher().getCreditAmount());
					debitAmount=debitAmount;
				}
				if(!v.isCgstSgstPresent()) {
					double cgstDebitVoucher = v.getCgstDebitVoucher()!=null?v.getCgstDebitVoucher().getDebitAmount():0;
					double sgstDebitVoucher = v.getSgstDebitVoucher()!=null?v.getSgstDebitVoucher().getDebitAmount():0;
					debitGstAmount =cgstDebitVoucher+sgstDebitVoucher;

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
				if(v.getDebitAmount()!=0) {
					System.out.println("test2");
					debitAmount =v.getDebitAmount();
					if(v.isCgstSgstPresent()) {
						 cgstSgstDebitAmount = v.getCgstDebitVoucher().getDebitAmount()+v.getSgstDebitVoucher().getDebitAmount();
					}else {
					   igstDebitAmount = v.getIgstDebitVoucher()!=null?v.getIgstDebitVoucher().getDebitAmount():0;
					}
				}
				double creditAmount =0;
				double cgstSgstCreditAmount =0;
				double igstCreditAmount =0;
				if(v.getCreditAmount()!=0) {
					System.out.println("test3"+v.isCgstSgstPresent());

					creditAmount =v.getCreditAmount();
					if(!v.isCgstSgstPresent()) {
						double cgstCreditVoucher = v.getCgstCreditVoucher()!=null?v.getCgstCreditVoucher().getCreditAmount():0;
						double sgstCreditVoucher = v.getSgstCreditVoucher()!=null?v.getSgstCreditVoucher().getCreditAmount():0;
						 cgstSgstCreditAmount = cgstCreditVoucher+sgstCreditVoucher;
					}else {
					   igstCreditAmount = v.getIgstCreditVoucher().getCreditAmount();
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
				double debitAmount =v.getDebitAmount();
				if(v.isCgstSgstPresent()) {
					 cgstSgstDebitAmount = v.getCgstDebitVoucher().getDebitAmount()+v.getSgstDebitVoucher().getDebitAmount();
				}else {
				   igstDebitAmount = v.getIgstDebitVoucher().getDebitAmount();
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
				double debitAmount =v.getDebitAmount();
				double creditAmount =v.getCreditAmount();
				totalCredit=totalCredit+creditAmount;
				totalDebit=totalDebit+debitAmount;
				totalAmount=totalAmount-debitAmount+creditAmount;
			}else {
				double debitAmount =v.getDebitAmount();
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
			if(v.getDebitAmount()!=0) {
				System.out.println("test2");
				debitAmount =v.getDebitAmount();
				if(v.isCgstSgstPresent()) {
					 cgstSgstDebitAmount = v.getCgstDebitVoucher().getDebitAmount()+v.getSgstDebitVoucher().getDebitAmount();
				}else {
				   igstDebitAmount = v.getIgstDebitVoucher().getDebitAmount();
				}
			}
			double creditAmount =0;
			double cgstSgstCreditAmount =0;
			double igstCreditAmount =0;
			if(v.getCreditAmount()!=0) {
				System.out.println("test3"+v.isCgstSgstPresent());

				creditAmount =v.getCreditAmount();
				if(!v.isCgstSgstPresent()) {
					 cgstSgstCreditAmount = v.getCgstCreditVoucher().getCreditAmount()+v.getSgstCreditVoucher().getCreditAmount();
				}else {
				   igstCreditAmount = v.getIgstCreditVoucher().getCreditAmount();
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
			double debitAmount =v.getDebitAmount();
			if(v.isCgstSgstPresent()) {
				 cgstSgstDebitAmount = v.getCgstDebitVoucher().getDebitAmount()+v.getSgstDebitVoucher().getDebitAmount();
			}else {
			   igstDebitAmount = v.getIgstDebitVoucher().getDebitAmount();
			}
			totalDebit=totalDebit+debitAmount+igstDebitAmount+cgstSgstDebitAmount;
			totalAmount=totalAmount-totalDebit;
			map.put("totalCredit", totalCredit);
			map.put("totalDebit", totalDebit);
			map.put("totalAmount", totalAmount);

		}
		
		
		return map;
	
	
	}

	@Override
	public Map<String, Object> deleteVoucherById(Long id) {
// 
		return null;
	}
}
