package com.account.serviceImpl;

import com.account.domain.*;
import com.account.dto.estimate.EstimateRequestDto;
import com.account.dto.estimate.EstimateResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.*;
import com.account.service.EstimateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class EstimateServiceImpl implements EstimateService {

    @Autowired
    private EstimateRepository estimateRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyUnitRepository companyUnitRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public EstimateResponseDto createEstimate(EstimateRequestDto dto) {
        // Validate Company
        Company company = companyRepository.findById(dto.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Company not found with id: " + dto.getCompanyId(),
                        "ERR_COMPANY_NOT_FOUND",
                        "Company",
                        dto.getCompanyId()
                ));

        // Validate Unit (if provided)
        CompanyUnit unit = null;
        if (dto.getUnitId() != null) {
            unit = companyUnitRepository.findById(dto.getUnitId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Unit not found with id: " + dto.getUnitId(),
                            "ERR_UNIT_NOT_FOUND",
                            "CompanyUnit",
                            dto.getUnitId()
                    ));
        }

        // Validate Contact (if provided)
        Contact contact = null;
        if (dto.getContactId() != null) {
            contact = contactRepository.findByIdAndDeleteStatusFalse(dto.getContactId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Active contact not found with id: " + dto.getContactId(),
                            "ERR_CONTACT_NOT_FOUND",
                            "Contact",
                            dto.getContactId()
                    ));
        }

        // Check duplicate order number
        if (estimateRepository.existsByOrderNumber(dto.getOrderNumber())) {
            throw new ValidationException(
                    "Order number already exists: " + dto.getOrderNumber(),
                    "ERR_DUPLICATE_ORDER_NUMBER",
                    "orderNumber"
            );
        }

        Estimate estimate = new Estimate();
        mapRequestToEntity(dto, estimate, company, unit, contact);

        // Calculate total price
        estimate.calculateTotal();

        // Set default status
        if (dto.getStatus() == null || dto.getStatus().isBlank()) {
            estimate.setStatus("Draft");
        }

        Estimate saved = estimateRepository.save(estimate);

        return mapToResponseDto(saved);
    }

    @Override
    public EstimateResponseDto getEstimateById(Long id) {
        return estimateRepository.findById(id)
                .map(this::mapToResponseDto)
                .orElse(null);
    }

    @Override
    public List<EstimateResponseDto> getEstimatesByCompanyId(Long companyId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Estimate> estimates = estimateRepository.findByCompanyIdAndIsDeletedFalse(companyId, pageable);
        return estimates.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<EstimateResponseDto> getAllEstimates(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Estimate> estimates = estimateRepository.findByIsDeletedFalse(pageable);
        return estimates.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> searchEstimates(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Estimate> results = estimateRepository.searchByQuery(query, pageable);

        return results.stream()
                .map(this::mapToSimpleMap)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getEstimateForView(Long id) {
        Estimate estimate = estimateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Estimate not found with id: " + id,
                        "ERR_ESTIMATE_NOT_FOUND",
                        "Estimate",
                        id
                ));

        Map<String, Object> view = new HashMap<>();
        view.put("estimate", mapToResponseDto(estimate));

        // Formatted fields for PDF/UI
        view.put("formattedTotal", String.format("₹%,.2f", estimate.getTotalPrice()));
        view.put("formattedDate", estimate.getPurchaseDate() != null ? estimate.getPurchaseDate().toString() : "");
        view.put("generatedAt", LocalDateTime.now());

        return view;
    }

    // ==================== MAPPING HELPERS ====================

    private void mapRequestToEntity(EstimateRequestDto dto, Estimate estimate,
                                    Company company, CompanyUnit unit, Contact contact) {

        estimate.setOrderNumber(dto.getOrderNumber());
        estimate.setPurchaseDate(dto.getPurchaseDate());
        estimate.setProductName(dto.getProductName());
        estimate.setCompany(company);
        estimate.setUnit(unit);
        estimate.setContact(contact);

        // Addresses
        estimate.setBillingAddressLine1(dto.getBillingAddressLine1());
        estimate.setBillingAddressLine2(dto.getBillingAddressLine2());
        estimate.setBillingCity(dto.getBillingCity());
        estimate.setBillingState(dto.getBillingState());
        estimate.setBillingCountry(dto.getBillingCountry());
        estimate.setBillingPinCode(dto.getBillingPinCode());

        estimate.setShippingAddressLine1(dto.getShippingAddressLine1());
        estimate.setShippingAddressLine2(dto.getShippingAddressLine2());
        estimate.setShippingCity(dto.getShippingCity());
        estimate.setShippingState(dto.getShippingState());
        estimate.setShippingCountry(dto.getShippingCountry());
        estimate.setShippingPinCode(dto.getShippingPinCode());

        estimate.setRemark(dto.getRemark());
        estimate.setEmployeeCode(dto.getEmployeeCode());

        // Pricing
        estimate.setBasePrice(dto.getBasePrice());
        estimate.setTax(dto.getTax());
        estimate.setGovernmentFee(dto.getGovernmentFee());
        estimate.setCgst(dto.getCgst());
        estimate.setSgst(dto.getSgst());
        estimate.setServiceCharge(dto.getServiceCharge());
        estimate.setProfessionalFee(dto.getProfessionalFee());
        estimate.setStatus(dto.getStatus());
    }

    private EstimateResponseDto mapToResponseDto(Estimate estimate) {
        EstimateResponseDto dto = new EstimateResponseDto();

        dto.setId(estimate.getId());
        dto.setOrderNumber(estimate.getOrderNumber());
        dto.setPurchaseDate(estimate.getPurchaseDate());
        dto.setProductName(estimate.getProductName());
        dto.setStatus(estimate.getStatus());
        dto.setRemark(estimate.getRemark());
        dto.setEmployeeCode(estimate.getEmployeeCode());

        // Company
        if (estimate.getCompany() != null) {
            dto.setCompanyId(estimate.getCompany().getId());
            dto.setCompanyName(estimate.getCompany().getName());
            dto.setCompanyPanNo(estimate.getCompany().getPanNo());
            dto.setCompanyGstNo(estimate.getCompany().getGstNo());
        }

        // Unit
        if (estimate.getUnit() != null) {
            dto.setUnitId(estimate.getUnit().getId());
            dto.setUnitName(estimate.getUnit().getUnitName());
            dto.setUnitCity(estimate.getUnit().getCity());
            dto.setUnitState(estimate.getUnit().getState());
            dto.setUnitGstNo(estimate.getUnit().getGstNo());
        }

        // Contact
        if (estimate.getContact() != null) {
            dto.setContactId(estimate.getContact().getId());
            dto.setContactName(estimate.getContact().getName());
            dto.setContactEmails(estimate.getContact().getEmails());
            dto.setContactNo(estimate.getContact().getContactNo());
            dto.setWhatsappNo(estimate.getContact().getWhatsappNo());
        }

        // Addresses
        EstimateResponseDto.AddressDto billing = new EstimateResponseDto.AddressDto();
        billing.setLine1(estimate.getBillingAddressLine1());
        billing.setLine2(estimate.getBillingAddressLine2());
        billing.setCity(estimate.getBillingCity());
        billing.setState(estimate.getBillingState());
        billing.setCountry(estimate.getBillingCountry());
        billing.setPinCode(estimate.getBillingPinCode());
        dto.setBillingAddress(billing);

        EstimateResponseDto.AddressDto shipping = new EstimateResponseDto.AddressDto();
        shipping.setLine1(estimate.getShippingAddressLine1());
        shipping.setLine2(estimate.getShippingAddressLine2());
        shipping.setCity(estimate.getShippingCity());
        shipping.setState(estimate.getShippingState());
        shipping.setCountry(estimate.getShippingCountry());
        shipping.setPinCode(estimate.getShippingPinCode());
        dto.setShippingAddress(shipping);

        // Pricing
        dto.setBasePrice(estimate.getBasePrice());
        dto.setProfessionalFee(estimate.getProfessionalFee());
        dto.setServiceCharge(estimate.getServiceCharge());
        dto.setGovernmentFee(estimate.getGovernmentFee());
        dto.setTax(estimate.getTax());
        dto.setCgst(estimate.getCgst());
        dto.setSgst(estimate.getSgst());
        dto.setTotalPrice(estimate.getTotalPrice());

        // Audit
        if (estimate.getCreatedBy() != null) {
            dto.setCreatedByName(estimate.getCreatedBy().getFullName());
        }
        dto.setCreatedAt(estimate.getCreatedAt());

        if (estimate.getUpdatedBy() != null) {
            dto.setUpdatedByName(estimate.getUpdatedBy().getFullName());
        }
        dto.setUpdatedAt(estimate.getUpdatedAt());

        return dto;
    }

    private Map<String, Object> mapToSimpleMap(Estimate estimate) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", estimate.getId());
        map.put("orderNumber", estimate.getOrderNumber());
        map.put("productName", estimate.getProductName());
        map.put("companyName", estimate.getCompany().getName());
        map.put("totalPrice", estimate.getTotalPrice());
        map.put("status", estimate.getStatus());
        map.put("purchaseDate", estimate.getPurchaseDate());
        return map;
    }
}