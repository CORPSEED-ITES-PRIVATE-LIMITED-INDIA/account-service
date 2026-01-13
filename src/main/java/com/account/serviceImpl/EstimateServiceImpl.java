package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateLineItem;
import com.account.domain.estimate.EstimateStatus;
import com.account.dto.EstimateCreationRequestDto;
import com.account.dto.estimate.CompanySummaryDto;
import com.account.dto.estimate.CompanyUnitSummaryDto;
import com.account.dto.estimate.EstimateResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.*;
import com.account.service.EstimateService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class EstimateServiceImpl implements EstimateService {

    private static final Logger log = LogManager.getLogger(EstimateServiceImpl.class);

    private final EstimateRepository estimateRepository;
    private final CompanyRepository companyRepository;
    private final CompanyUnitRepository companyUnitRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;

    @Override
    public EstimateResponseDto createEstimate(EstimateCreationRequestDto requestDto) {
        log.info("Starting estimate creation | companyId: {} | userId: {} | solution: {} | lineItems: {}",
                requestDto.getCompanyId(),
                requestDto.getCreatedByUserId(),
                requestDto.getSolutionName(),
                requestDto.getLineItems() != null ? requestDto.getLineItems().size() : 0);

        // 1. Basic validation
        if (requestDto.getLineItems() == null || requestDto.getLineItems().isEmpty()) {
            log.warn("Validation failed: No line items provided for estimate creation");
            throw new ValidationException("At least one line item is required", "ERR_NO_LINE_ITEMS");
        }

        // 2. Validate creator exists
        log.debug("Fetching creator user with id: {}", requestDto.getCreatedByUserId());
        User creator = userRepository.findById(requestDto.getCreatedByUserId())
                .orElseThrow(() -> {
                    log.error("User not found for id: {}", requestDto.getCreatedByUserId());
                    return new ResourceNotFoundException(
                            "User not found with ID: " + requestDto.getCreatedByUserId(), "USER_NOT_FOUND");
                });

        // 3. Fetch referenced entities
        log.debug("Fetching company with id: {}", requestDto.getCompanyId());
        Company company = companyRepository.findById(requestDto.getCompanyId())
                .orElseThrow(() -> {
                    log.error("Company not found for id: {}", requestDto.getCompanyId());
                    return new ResourceNotFoundException("Company not found", "COMPANY_NOT_FOUND");
                });

        CompanyUnit unit = null;
        if (requestDto.getUnitId() != null && requestDto.getUnitId() > 0) {
            log.debug("Fetching unit with id: {}", requestDto.getUnitId());
            unit = companyUnitRepository.findById(requestDto.getUnitId())
                    .orElseThrow(() -> {
                        log.error("Unit not found for id: {}", requestDto.getUnitId());
                        return new ResourceNotFoundException("Unit not found", "UNIT_NOT_FOUND");
                    });
        }

        Contact contact = null;
        if (requestDto.getContactId() != null && requestDto.getContactId() > 0) {
            log.debug("Fetching contact with id: {}", requestDto.getContactId());
            contact = contactRepository.findById(requestDto.getContactId())
                    .orElseThrow(() -> {
                        log.error("Contact not found for id: {}", requestDto.getContactId());
                        return new ResourceNotFoundException("Contact not found", "CONTACT_NOT_FOUND");
                    });
        }

        // 4. Create Estimate entity
        log.debug("Creating new Estimate entity");
        Estimate estimate = new Estimate();

        // Generate secure public UUID for sharing
        String publicUuid = UUID.randomUUID().toString();
        estimate.setPublicUuid(publicUuid);
        log.debug("Generated public UUID for estimate: {}", publicUuid);

        String estimateNumber = generateEstimateNumber();
        estimate.setEstimateNumber(estimateNumber);

        estimate.setEstimateDate(
                requestDto.getEstimateDate() != null ? requestDto.getEstimateDate() : LocalDate.now());
        estimate.setValidUntil(
                requestDto.getValidUntil() != null ? requestDto.getValidUntil() : LocalDate.now().plusDays(30));

        estimate.setCompany(company);
        estimate.setUnit(unit);
        estimate.setContact(contact);
        estimate.setSolutionName(requestDto.getSolutionName());
        estimate.setSolutionId(requestDto.getSolutionId());
        estimate.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));

        // Validate and set SolutionType
        try {
            SolutionType solutionType = SolutionType.valueOf(requestDto.getSolutionType());
            estimate.setSolutionType(solutionType);
        } catch (IllegalArgumentException e) {
            String allowedValues = Arrays.stream(SolutionType.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));

            log.error("Invalid solution type provided: '{}' | Allowed values: {}",
                    requestDto.getSolutionType(), allowedValues);

            throw new ValidationException(
                    "Invalid solution type: " + requestDto.getSolutionType() +
                            ". Allowed values: " + allowedValues,
                    "ERR_INVALID_SOLUTION_TYPE");
        }

        estimate.setCustomerNotes(requestDto.getCustomerNotes());
        estimate.setInternalRemarks(requestDto.getInternalRemarks());
        estimate.setCurrency("INR");
        estimate.setStatus(EstimateStatus.DRAFT);
        estimate.setVersion(1);
        estimate.setRevisionReason("Initial creation");
        estimate.setCreatedBy(creator);

        // 5. Process line items
        log.debug("Processing {} line items", requestDto.getLineItems().size());
        List<EstimateLineItem> lineItems = new ArrayList<>();

        for (int i = 0; i < requestDto.getLineItems().size(); i++) {
            EstimateCreationRequestDto.EstimateLineItemDto itemDto = requestDto.getLineItems().get(i);

            EstimateLineItem lineItem = new EstimateLineItem();
            lineItem.setEstimate(estimate);
            lineItem.setSourceItemId(itemDto.getSourceItemId());
            lineItem.setItemName(itemDto.getItemName());
            lineItem.setDescription(itemDto.getDescription());
            lineItem.setHsnSacCode(itemDto.getHsnSacCode());
            lineItem.setQuantity(itemDto.getQuantity());
            lineItem.setUnit(itemDto.getUnit());
            lineItem.setUnitPriceExGst(itemDto.getUnitPriceExGst());
            lineItem.setGstRate(itemDto.getGstRate());
            lineItem.setCategoryCode(itemDto.getCategoryCode());
            lineItem.setFeeType(itemDto.getFeeType());
            lineItem.setDisplayOrder(i + 1);

            lineItem.calculateLineTotals();
            lineItems.add(lineItem);

            log.trace("Added line item #{}: {} | qty: {} | unitPrice: {} | totalExGst: {}",
                    i + 1, itemDto.getItemName(), itemDto.getQuantity(),
                    itemDto.getUnitPriceExGst(), lineItem.getLineTotalExGst());
        }

        estimate.setLineItems(lineItems);

        // 6. Calculate totals
        log.debug("Calculating estimate totals");
        estimate.calculateTotals();

        // 7. Persist
        log.info("Saving estimate: number={} | publicUuid={} | company={} | total={}",
                estimateNumber, publicUuid, company.getId(), estimate.getGrandTotal());
        estimate = estimateRepository.save(estimate);

        log.info("Estimate created successfully | id={} | number={} | publicUuid={} | createdBy={} | total={}",
                estimate.getId(), estimate.getEstimateNumber(), estimate.getPublicUuid(),
                creator.getId(), estimate.getGrandTotal());

        // 8. Return response
        return mapToResponseDto(estimate);
    }

    private String generateEstimateNumber() {
        long count = estimateRepository.count() + 1;
        String number = String.format("EST-%d-%06d", LocalDate.now().getYear(), count);
        log.debug("Generated estimate number: {}", number);
        return number;
    }

    @Override
    public EstimateResponseDto getEstimateById(Long estimateId, Long requestingUserId) {
        log.info("Fetching estimate | estimateId={} | requestedByUser={}", estimateId, requestingUserId);

        // Basic security check
        if (!userRepository.existsById(requestingUserId)) {
            log.warn("User not found: userId={}", requestingUserId);
            throw new ResourceNotFoundException("User not found", "USER_NOT_FOUND");
        }

        // Fetch the estimate
        Estimate estimate = estimateRepository.findById(estimateId)
                .orElseThrow(() -> {
                    log.warn("Estimate not found: id={}", estimateId);
                    return new ResourceNotFoundException("Estimate not found", "ESTIMATE_NOT_FOUND");
                });

        log.info("Estimate fetched successfully | number={} | total={}",
                estimate.getEstimateNumber(), estimate.getGrandTotal());

        return mapToResponseDto(estimate);
    }

    @Override
    public List<EstimateResponseDto> getEstimatesByLeadId(Long leadId) {
        log.info("Fetching all estimates for leadId: {}", leadId);

        if (leadId == null || leadId <= 0) {
            throw new ValidationException("Invalid lead ID", "ERR_INVALID_LEAD_ID");
        }

        List<Estimate> estimates = estimateRepository.findByLeadIdAndIsDeletedFalseOrderByCreatedAtDesc(leadId);

        log.info("Found {} estimates for leadId: {}", estimates.size(), leadId);

        return estimates.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<EstimateResponseDto> getEstimatesByCompanyId(Long companyId) {
        log.info("Fetching estimates for companyId: {}", companyId);

        if (companyId == null || companyId <= 0) {
            throw new ValidationException("Invalid company ID", "ERR_INVALID_COMPANY_ID");
        }

        if (!companyRepository.existsById(companyId)) {
            throw new ResourceNotFoundException("Company not found with ID: " + companyId, "COMPANY_NOT_FOUND");
        }

        List<Estimate> estimates = estimateRepository
                .findByCompanyIdAndIsDeletedFalseOrderByCreatedAtDesc(companyId);

        log.info("Found {} estimates for companyId: {}", estimates.size(), companyId);

        return estimates.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Maps Estimate entity to EstimateResponseDto manually (no builder pattern)
     */
    private EstimateResponseDto mapToResponseDto(Estimate estimate) {
        log.trace("Mapping Estimate entity to response DTO | id={}", estimate.getId());

        EstimateResponseDto dto = new EstimateResponseDto();

        // Basic fields
        dto.setId(estimate.getId());
        dto.setPublicUuid(estimate.getPublicUuid());
        dto.setEstimateNumber(estimate.getEstimateNumber());
        dto.setEstimateDate(estimate.getEstimateDate());
        dto.setValidUntil(estimate.getValidUntil());
        dto.setSolutionName(estimate.getSolutionName());
        dto.setSolutionType(estimate.getSolutionType() != null ? estimate.getSolutionType().name() : null);
        dto.setStatus(estimate.getStatus() != null ? estimate.getStatus().name() : null);
        dto.setCurrency(estimate.getCurrency());

        // Financials
        dto.setSubTotalExGst(estimate.getSubTotalExGst());
        dto.setTotalGstAmount(estimate.getTotalGstAmount());
        dto.setCgstAmount(estimate.getCgstAmount());
        dto.setSgstAmount(estimate.getSgstAmount());
        dto.setIgstAmount(estimate.getIgstAmount());
        dto.setGrandTotal(estimate.getGrandTotal());

        // Notes & versioning
        dto.setCustomerNotes(estimate.getCustomerNotes());
        dto.setInternalRemarks(estimate.getInternalRemarks());
        dto.setVersion(estimate.getVersion());
        dto.setRevisionReason(estimate.getRevisionReason());

        // Audit
        dto.setCreatedAt(estimate.getCreatedAt());
        dto.setCreatedById(estimate.getCreatedBy() != null ? estimate.getCreatedBy().getId() : null);

        // Company summary
        if (estimate.getCompany() != null) {
            Company company = estimate.getCompany();
            CompanySummaryDto companyDto = new CompanySummaryDto();
            companyDto.setId(company.getId());
            companyDto.setName(company.getName());
            companyDto.setPanNo(company.getPanNo());
            companyDto.setGstNo(company.getGstNo());
            companyDto.setGstType(company.getGstType());
            companyDto.setState(company.getState());
            companyDto.setCity(company.getCity());
            companyDto.setPrimaryPinCode(company.getPrimaryPinCode());
            companyDto.setOnboardingStatus(company.getOnboardingStatus());
            companyDto.setAccountsApproved(company.isAccountsApproved());
            dto.setCompany(companyDto);
        }

        // Unit summary
        if (estimate.getUnit() != null) {
            CompanyUnit unit = estimate.getUnit();
            CompanyUnitSummaryDto unitDto = new CompanyUnitSummaryDto();
            unitDto.setId(unit.getId());
            unitDto.setUnitName(unit.getUnitName());
            unitDto.setAddressLine1(unit.getAddressLine1());
            unitDto.setAddressLine2(unit.getAddressLine2());
            unitDto.setCity(unit.getCity());
            unitDto.setState(unit.getState());
            unitDto.setPinCode(unit.getPinCode());
            unitDto.setGstNo(unit.getGstNo());
            unitDto.setGstType(unit.getGstType());
            unitDto.setStatus(unit.getStatus());
            dto.setUnit(unitDto);
        }

        // Line items
        List<EstimateResponseDto.EstimateLineItemResponseDto> itemDtos = new ArrayList<>();
        if (estimate.getLineItems() != null) {
            for (EstimateLineItem item : estimate.getLineItems()) {
                EstimateResponseDto.EstimateLineItemResponseDto itemDto =
                        new EstimateResponseDto.EstimateLineItemResponseDto();

                itemDto.setId(item.getId());
                itemDto.setSourceItemId(item.getSourceItemId());
                itemDto.setItemName(item.getItemName());
                itemDto.setDescription(item.getDescription());
                itemDto.setHsnSacCode(item.getHsnSacCode());
                itemDto.setQuantity(item.getQuantity());
                itemDto.setUnit(item.getUnit());
                itemDto.setUnitPriceExGst(item.getUnitPriceExGst());
                itemDto.setGstRate(item.getGstRate());
                itemDto.setLineTotalExGst(item.getLineTotalExGst());
                itemDto.setGstAmount(item.getGstAmount());
                itemDto.setDisplayOrder(item.getDisplayOrder());
                itemDto.setCategoryCode(item.getCategoryCode());
                itemDto.setFeeType(item.getFeeType());

                itemDtos.add(itemDto);
            }
        }
        dto.setLineItems(itemDtos);

        return dto;
    }
}