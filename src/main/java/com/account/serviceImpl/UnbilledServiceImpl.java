package com.account.serviceImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.account.dto.UnbilledDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.account.config.LeadFeignClient;
import com.account.domain.Unbilled;
import com.account.repository.UnbilledRepository;
import com.account.service.UnbilledService;
@Service
public class UnbilledServiceImpl implements UnbilledService{

	@Autowired
    private UnbilledRepository unbilledRepository;
	
	@Autowired
	LeadFeignClient leadFeignClient;

    // Convert Entity to DTO
    private UnbilledDTO toDTO(Unbilled entity) {
        UnbilledDTO dto = new UnbilledDTO();
        dto.setDate(entity.getDate());
//        dto.setProject(entity.getProject());
        dto.setEstimateId(entity.getEstimateId());
        
        dto.setClient(entity.getClient());
        dto.setCompany(entity.getCompany());
        dto.setTxnAmount(entity.getTxnAmount());
        dto.setOrderAmount(entity.getOrderAmount());
        dto.setDueAmount(entity.getDueAmount());
        dto.setPaidAmount(entity.getPaidAmount());
        dto.setStatus(entity.getStatus());
        return dto;
    }

    // Convert DTO to Entity
    private Unbilled toEntity(UnbilledDTO dto) {
        Unbilled entity = new Unbilled();
        entity.setDate(dto.getDate());
//        entity.setProject(dto.getProject());
        entity.setEstimateId(dto.getEstimateId());
        entity.setClient(dto.getClient());
        entity.setCompany(dto.getCompany());
        entity.setTxnAmount(dto.getTxnAmount());
        entity.setOrderAmount(dto.getOrderAmount());
        entity.setDueAmount(dto.getDueAmount());
        entity.setPaidAmount(dto.getPaidAmount());
        entity.setStatus(dto.getStatus());
        return entity;
    }

//    private Invoiced toInvoicedEntity(InvoicedDTO dto) {
//        Invoiced invoiced = new Invoiced();
//        invoiced.setInvoiceNumber(dto.getInvoiceNumber());
//        invoiced.setAmount(dto.getAmount());
//        return invoiced;
//    }

    // CRUD Methods

    public List<UnbilledDTO> getAllUnbilled() {
        return unbilledRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public UnbilledDTO getUnbilledById(Long id) {
        return unbilledRepository.findById(id).map(this::toDTO).orElse(null);
    }

    public UnbilledDTO createUnbilled(UnbilledDTO dto) {
        Unbilled entity = toEntity(dto);
        Unbilled saved = unbilledRepository.save(entity);
        return toDTO(saved);
    }

    public UnbilledDTO update(Long id, UnbilledDTO dto) {
        return unbilledRepository.findById(id).map(existing -> {
            existing.setDate(dto.getDate());
//            existing.setProject(dto.getProject());
            existing.setEstimateId(dto.getEstimateId());
            existing.setClient(dto.getClient());
            existing.setCompany(dto.getCompany());
            existing.setTxnAmount(dto.getTxnAmount());
            existing.setOrderAmount(dto.getOrderAmount());
            existing.setDueAmount(dto.getDueAmount());
            existing.setPaidAmount(dto.getPaidAmount());
            existing.setStatus(dto.getStatus());

            Unbilled updated = unbilledRepository.save(existing);
            return toDTO(updated);
        }).orElse(null);
    }

    public void delete(Long id) {
        unbilledRepository.deleteById(id);
    }

	@Override
	public Map<String, Object> getAllUnbilledAmount() {
		List<Unbilled> unbilledList = unbilledRepository.findAll();
		double totalAmount=0l;
		double paidAmount=0l;
		double dueAmount=0l;
		Map<String,Object>res=new HashMap<>();
		for(Unbilled u:unbilledList) {
			totalAmount=totalAmount+u.getOrderAmount();
			paidAmount=paidAmount+u.getPaidAmount();
			dueAmount=dueAmount+u.getDueAmount();
		}
		res.put("totalAmount", totalAmount);
		res.put("paidAmount", paidAmount);
		res.put("dueAmount", dueAmount);

		return res;
	}
//
//	private Date date;
//	private Long project;
//	private Long estimateId;
//	private List<Long> invoiced;
//	private String client;
//	private String company;
//	private double txnAmount;
//	private double orderAmount;
//	private double dueAmount;
//	private double paidAmount;
//	private String status;
	
	@Override
	public List<Map<String,Object>>getAllUnbilled(int page, int size) {
		
		Pageable pageableDesc = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
		 List<Unbilled> unbilledList = unbilledRepository.findAll(pageableDesc).getContent();

		 List<Map<String,Object>>result=new ArrayList<>();
		 for(Unbilled ub:unbilledList) {
			Map<String, Object> estimate = leadFeignClient.getEstimateById(ub.getEstimateId());

			 Map<String,Object>map=new HashMap<>();
			 map.put("id", ub.getId());
			 map.put("date", ub.getDate());
			 map.put("company", ub.getCompany());
			 map.put("txnAmount", ub.getPaidAmount());
			 map.put("productName", estimate.get("productName"));
			 map.put("invoicedNumber", null);
			 map.put("assigneeName", estimate.get("assigneeName"));
			 map.put("clientId", estimate.get("primaryContactId"));
			 map.put("clientName", estimate.get("primaryContactName"));
			 map.put("clientEmail", estimate.get("primaryContactEmails"));

			 result.add(map);
		 }
        return result;

	}

	@Override
	public int getAllUnbilledCount() {
		return unbilledRepository.findAll().size();
	}

	@Override
	public List<Map<String, Object>> searchUnbilled(String name,String searchBy) {
		List<Unbilled>unbilledList=new ArrayList<>();
		if(searchBy.equals("companyName")) {
			unbilledList= unbilledRepository.findByCompanyName(name);
		}else if(searchBy.equals("estimateId")){
			unbilledList= unbilledRepository.findByEstimateId(name);
		}
		List<Map<String,Object>>result=new ArrayList<>();
		 for(Unbilled ub:unbilledList) {
			Map<String, Object> estimate = leadFeignClient.getEstimateById(ub.getEstimateId());

			 Map<String,Object>map=new HashMap<>();
			 map.put("id", ub.getId());
			 map.put("date", ub.getDate());
			 map.put("company", ub.getCompany());
			 map.put("txnAmount", ub.getTxnAmount());
			 map.put("productName", estimate.get("productName"));
			 map.put("invoicedNumber", null);
			 map.put("assigneeName", estimate.get("assigneeName"));
			 result.add(map);
		 }
       return result;
	}

	@Override
	public List<Map<String, Object>> getAllUnbilledForExport(String startDate,String endDate) {
		List<Unbilled>  unbilledList =new ArrayList<>();
		if(startDate!=null && endDate!=null) {
			 unbilledList = unbilledRepository.findAllByInBetweenDate(startDate,endDate);

		}else {
			 unbilledList = unbilledRepository.findAll();

		}
//		 List<Unbilled> unbilledList = unbilledRepository.findAll();

		 List<Map<String,Object>>result=new ArrayList<>();
		 for(Unbilled ub:unbilledList) {
			Map<String, Object> estimate = leadFeignClient.getEstimateById(ub.getEstimateId());

			 Map<String,Object>map=new HashMap<>();
			 map.put("id", ub.getId());
			 map.put("date", ub.getDate());
			 map.put("company", ub.getCompany());
			 map.put("txnAmount", ub.getTxnAmount());
			 map.put("productName", estimate.get("productName"));
			 map.put("invoicedNumber", null);
			 map.put("assigneeName", estimate.get("assigneeName"));
			 map.put("clientId", estimate.get("primaryContactId"));
			 map.put("clientName", estimate.get("primaryContactName"));
			 map.put("clientEmail", estimate.get("primaryContactEmails"));

			 result.add(map);
		 }
        return result;

	
	}

	@Override
	public Map<String, Object> getUnbilledByIdForView(Long id) {
		Map<String, Object>map=new HashMap<>();
		double totalAmount=0l;
		double paidAmount=0l;
		double dueAmount=0l;
		Optional<Unbilled> unbilled = unbilledRepository.findById(id);
		if(unbilled!=null&& unbilled.get()!=null) {
			Unbilled u=unbilled.get();
			Map<String, Object> estimate = leadFeignClient.getEstimateByLeadIdForView(unbilled.get().getEstimateId());
			totalAmount=totalAmount+u.getOrderAmount();
			paidAmount=paidAmount+u.getPaidAmount();
			dueAmount=dueAmount+u.getDueAmount();
	         map.put("estimate", estimate);
		}
         map.put("dueAmount", dueAmount);

         return map;
	}

	

}
