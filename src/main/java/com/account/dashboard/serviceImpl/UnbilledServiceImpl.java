package com.account.dashboard.serviceImpl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.Unbilled;
import com.account.dashboard.dto.UnbilledDTO;
import com.account.dashboard.repository.UnbilledRepository;
import com.account.dashboard.service.UnbilledService;
@Service
public class UnbilledServiceImpl implements UnbilledService{

	@Autowired
    private UnbilledRepository unbilledRepository;

    // Convert Entity to DTO
    private UnbilledDTO toDTO(Unbilled entity) {
        UnbilledDTO dto = new UnbilledDTO();
        dto.setDate(entity.getDate());
        dto.setProject(entity.getProject());
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

    // Convert Invoiced entity to DTO
//    private InvoicedDTO toInvoicedDTO(Invoiced invoiced) {
//        InvoicedDTO dto = new InvoicedDTO();
//        dto.setInvoiceNumber(invoiced.getInvoiceNumber());
//        dto.setAmount(invoiced.getAmount());
//        return dto;
//    }

    // Convert DTO to Entity
    private Unbilled toEntity(UnbilledDTO dto) {
        Unbilled entity = new Unbilled();
        entity.setDate(dto.getDate());
        entity.setProject(dto.getProject());
        entity.setEstimateId(dto.getEstimateId());
//        if (dto.getInvoiced() != null) {
//            List<Invoiced> invoicedList = dto.getInvoiced().stream().map(this::toInvoicedEntity).collect(Collectors.toList());
//            invoicedList.forEach(inv -> inv.setUnbilled(entity)); // set back reference
//            entity.setInvoiced(invoicedList);
//        }
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

    public List<UnbilledDTO> getAll() {
        return unbilledRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public UnbilledDTO getById(Long id) {
        return unbilledRepository.findById(id).map(this::toDTO).orElse(null);
    }

    public UnbilledDTO create(UnbilledDTO dto) {
        Unbilled entity = toEntity(dto);
        Unbilled saved = unbilledRepository.save(entity);
        return toDTO(saved);
    }

    public UnbilledDTO update(Long id, UnbilledDTO dto) {
        return unbilledRepository.findById(id).map(existing -> {
            existing.setDate(dto.getDate());
            existing.setProject(dto.getProject());
            existing.setEstimateId(dto.getEstimateId());

            // Replace invoiced list properly
//            existing.getInvoiced().clear();
//            if (dto.getInvoiced() != null) {
//                List<Invoiced> invoicedList = dto.getInvoiced().stream().map(this::toInvoicedEntity).collect(Collectors.toList());
//                invoicedList.forEach(inv -> inv.setUnbilled(existing));
//                existing.getInvoiced().addAll(invoicedList);
//            }

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

	

}
