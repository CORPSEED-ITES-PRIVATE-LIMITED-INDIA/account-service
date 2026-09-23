package com.account.serviceImpl;

import com.account.config.EmailServiceImpl;
import com.account.domain.*;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.company.GstRegistrationType;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateLineItem;
import com.account.domain.estimate.EstimateStatus;
import com.account.domain.invoice.Invoice;
import com.account.domain.status.UnbilledStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.dto.EstimateCreationRequestDto;
import com.account.dto.company.request.CompanyUnitProjectOverviewRequestDto;
import com.account.dto.company.response.CompanyUnitOverviewDto;
import com.account.dto.company.response.CompanyUnitProjectOverviewResponseDto;
import com.account.dto.company.response.UnitBusinessRecordDto;
import com.account.dto.dashboard.CompanyRevenueDto;
import com.account.dto.dashboard.EstimateDashboardFilterRequest;
import com.account.dto.dashboard.EstimateDashboardResponse;
import com.account.dto.dashboard.MonthlyTrendDto;
import com.account.dto.estimate.*;
import com.account.dto.estimate.response.EstimateStatusResponseDto;
import com.account.dto.operationService.OperationProjectResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.feignClient.OperationFeignClient;
import com.account.repository.*;
import com.account.service.EstimateService;
import feign.FeignException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class EstimateServiceImpl implements EstimateService {

    private static final Logger log = LogManager.getLogger(EstimateServiceImpl.class);

    private static final int MONEY_SCALE = 3;
    private static final int DOCUMENT_SCALE = 0;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final EstimateRepository estimateRepository;
    private final CompanyRepository companyRepository;
    private final CompanyUnitRepository companyUnitRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final UnbilledInvoiceRepository unbilledInvoiceRepository;
    private final OrganizationRepository organizationRepository;
    private final OperationFeignClient operationFeignClient;
    private final PaymentReceiptRepository paymentReceiptRepository;
    private final TdsRegistrationRepository tdsRegistrationRepository;

    @Autowired
    private UnbilledInvoiceRepository unbilledRepository;
    @Autowired
    private EmailServiceImpl emailServiceImpl;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentReceiptRepository paymentRepository;

    @Autowired
    private EmailServiceImpl emailService;



    @Override
    public EstimateResponseDto createEstimate(
            EstimateCreationRequestDto requestDto
    ) {

        // =====================================================
        // 1. REQUEST VALIDATION
        // =====================================================

        if (requestDto == null) {
            throw new ValidationException(
                    "Request body is required",
                    "ERR_REQUEST_REQUIRED"
            );
        }

        log.info(
                "Starting estimate creation | companyId={} | unitId={} | userId={} | solution={} | lineItems={}",
                requestDto.getCompanyId(),
                requestDto.getUnitId(),
                requestDto.getCreatedByUserId(),
                requestDto.getSolutionName(),
                requestDto.getLineItems() != null
                        ? requestDto.getLineItems().size()
                        : 0
        );

        if (requestDto.getCompanyId() == null
                || requestDto.getCompanyId() <= 0) {

            throw new ValidationException(
                    "Invalid companyId",
                    "ERR_INVALID_COMPANY_ID",
                    "companyId"
            );
        }

        if (requestDto.getCreatedByUserId() == null
                || requestDto.getCreatedByUserId() <= 0) {

            throw new ValidationException(
                    "Invalid createdByUserId",
                    "ERR_INVALID_CREATED_BY",
                    "createdByUserId"
            );
        }

        if (requestDto.getLeadId() == null
                || requestDto.getLeadId() <= 0) {

            throw new ValidationException(
                    "Invalid leadId",
                    "ERR_INVALID_LEAD_ID",
                    "leadId"
            );
        }

        if (requestDto.getSolutionId() == null
                || requestDto.getSolutionId() <= 0) {

            throw new ValidationException(
                    "Invalid solutionId",
                    "ERR_INVALID_SOLUTION_ID",
                    "solutionId"
            );
        }

        if (requestDto.getSolutionName() == null
                || requestDto.getSolutionName().trim().isEmpty()) {

            throw new ValidationException(
                    "solutionName is required",
                    "ERR_SOLUTION_NAME_REQUIRED",
                    "solutionName"
            );
        }

        if (requestDto.getSolutionType() == null
                || requestDto.getSolutionType().trim().isEmpty()) {

            throw new ValidationException(
                    "solutionType is required",
                    "ERR_SOLUTION_TYPE_REQUIRED",
                    "solutionType"
            );
        }

        if (requestDto.getLineItems() == null
                || requestDto.getLineItems().isEmpty()) {

            throw new ValidationException(
                    "At least one line item is required",
                    "ERR_NO_LINE_ITEMS",
                    "lineItems"
            );
        }

        // =====================================================
        // 2. PREVENT DUPLICATE ESTIMATE FOR LEAD
        // =====================================================

        boolean existsNonRejectedEstimate =
                estimateRepository
                        .existsByLeadIdAndIsDeletedFalseAndIsCancelledFalseAndStatusNot(
                                requestDto.getLeadId(),
                                EstimateStatus.REJECTED
                        );

        if (existsNonRejectedEstimate) {
            throw new ValidationException(
                    "Estimate already exists for this lead. First reject the already created estimate(s).",
                    "ERR_ESTIMATE_ALREADY_EXISTS_FOR_LEAD",
                    "leadId"
            );
        }

        // =====================================================
        // 3. FETCH CREATOR
        // =====================================================

        User creator = userRepository
                .findByIdAndNotDeleted(
                        requestDto.getCreatedByUserId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: "
                                + requestDto.getCreatedByUserId(),
                        "USER_NOT_FOUND"
                ));

        // =====================================================
        // 4. FETCH COMPANY
        // =====================================================

        Company company = companyRepository
                .findById(requestDto.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Company not found with ID: "
                                + requestDto.getCompanyId(),
                        "COMPANY_NOT_FOUND"
                ));

        // =====================================================
        // 5. FETCH COMPANY UNIT
        // =====================================================

        CompanyUnit unit = null;

        if (requestDto.getUnitId() != null
                && requestDto.getUnitId() > 0) {

            unit = companyUnitRepository
                    .findById(requestDto.getUnitId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Unit not found with ID: "
                                    + requestDto.getUnitId(),
                            "UNIT_NOT_FOUND"
                    ));
        }

        // =====================================================
        // 6. RESOLVE GST REGISTRATION TYPE
        // =====================================================

        GstRegistrationType gstRegistrationType =
                unit != null
                        && unit.getGstRegistrationType() != null
                        ? unit.getGstRegistrationType()
                        : GstRegistrationType.REGISTERED;

        /*
         * Exact GST-type checks.
         */
        boolean isSez =
                gstRegistrationType
                        == GstRegistrationType.SEZ;

        boolean isInternational =
                gstRegistrationType
                        == GstRegistrationType.INTERNATIONAL;

        boolean zeroRatedSupply =
                isSez || isInternational;

        boolean gstApplicable =
                gstRegistrationType.isGstApplicable();

        log.info(
                "Estimate GST type resolved | unitId={} | type={} | isSez={} | isInternational={} | zeroRated={} | gstApplicable={}",
                unit != null ? unit.getId() : null,
                gstRegistrationType,
                isSez,
                isInternational,
                zeroRatedSupply,
                gstApplicable
        );

        // =====================================================
        // 7. FETCH CONTACT
        // =====================================================

        Contact contact = null;

        if (requestDto.getContactId() != null
                && requestDto.getContactId() > 0) {

            contact = contactRepository
                    .findById(requestDto.getContactId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Contact not found with ID: "
                                    + requestDto.getContactId(),
                            "CONTACT_NOT_FOUND"
                    ));
        }

        // =====================================================
        // 8. CREATE ESTIMATE
        // =====================================================

        LocalDateTime now =
                LocalDateTime.now(
                        ZoneId.of("Asia/Kolkata")
                );

        Estimate estimate = new Estimate();

        String publicUuid =
                UUID.randomUUID().toString();

        String estimateNumber =
                generateEstimateNumber();

        estimate.setPublicUuid(publicUuid);
        estimate.setEstimateNumber(estimateNumber);
        estimate.setPerformanceInvoiceNumber(
                generatePINumber()
        );
        estimate.setClientPoNumber(
                requestDto.getClientPoNumber() != null
                        && !requestDto.getClientPoNumber().trim().isEmpty()
                        ? requestDto.getClientPoNumber().trim()
                        : null
        );
        estimate.setPerformanceInvoiceFlag(false);

        estimate.setLeadId(
                requestDto.getLeadId()
        );

        estimate.setProposalId(
                requestDto.getProposalId()
        );

        estimate.setEstimateDate(
                requestDto.getEstimateDate() != null
                        ? requestDto.getEstimateDate()
                        : LocalDate.now()
        );

        estimate.setValidUntil(
                requestDto.getValidUntil() != null
                        ? requestDto.getValidUntil()
                        : estimate.getEstimateDate().plusDays(30)
        );

        if (estimate.getValidUntil()
                .isBefore(estimate.getEstimateDate())) {

            throw new ValidationException(
                    "validUntil cannot be before estimateDate",
                    "ERR_INVALID_DATES",
                    "validUntil"
            );
        }

        estimate.setCompany(company);
        estimate.setUnit(unit);
        estimate.setContact(contact);

        /*
         * GST type snapshot used when this estimate is created.
         */
        estimate.setGstRegistrationType(
                gstRegistrationType
        );

        estimate.setSolutionId(
                requestDto.getSolutionId()
        );

        estimate.setSolutionName(
                requestDto.getSolutionName().trim()
        );

        estimate.setSolutionType(
                requestDto.getSolutionType().trim()
        );

        estimate.setCustomerNotes(
                requestDto.getCustomerNotes()
        );

        estimate.setInternalRemarks(
                requestDto.getInternalRemarks()
        );

        estimate.setCurrency("INR");
        estimate.setStatus(EstimateStatus.DRAFT);
        estimate.setVersion(1);
        estimate.setRevisionReason("Initial creation");
        estimate.setCreatedBy(creator);
        estimate.setUpdatedBy(creator);
        estimate.setCreatedAt(now);
        estimate.setUpdatedAt(now);

        // =====================================================
        // 9. DETERMINE IGST OR CGST/SGST
        // =====================================================

        /*
         * Default to IGST.
         *
         * For SEZ and INTERNATIONAL, igstFlag remains true,
         * but GST rate is forced to zero.
         */
        boolean igstFlag = true;

        if (gstApplicable && unit != null) {

            Optional<Organization> organizationOptional =
                    organizationRepository.findTopOrganization();

            if (organizationOptional.isPresent()) {

                String organizationState =
                        organizationOptional.get().getState();

                String unitState =
                        unit.getState();

                boolean sameState =
                        organizationState != null
                                && !organizationState.trim().isEmpty()
                                && unitState != null
                                && !unitState.trim().isEmpty()
                                && organizationState
                                .trim()
                                .equalsIgnoreCase(
                                        unitState.trim()
                                );

                /*
                 * Same state  -> CGST + SGST
                 * Other state -> IGST
                 */
                igstFlag = !sameState;
            }
        }

        // =====================================================
        // 10. CREATE ESTIMATE LINE ITEMS
        // =====================================================

        List<EstimateLineItem> lineItems =
                new ArrayList<>();

        for (int i = 0;
             i < requestDto.getLineItems().size();
             i++) {

            EstimateCreationRequestDto.EstimateLineItemDto itemDto =
                    requestDto.getLineItems().get(i);

            String fieldPrefix =
                    "lineItems[" + i + "]";

            if (itemDto == null) {
                throw new ValidationException(
                        "Line item cannot be null",
                        "ERR_INVALID_LINE_ITEM",
                        fieldPrefix
                );
            }

            if (itemDto.getItemName() == null
                    || itemDto.getItemName().trim().isEmpty()) {

                throw new ValidationException(
                        "Item name is required",
                        "ERR_ITEM_NAME_REQUIRED",
                        fieldPrefix + ".itemName"
                );
            }

            if (itemDto.getQuantity() == null
                    || itemDto.getQuantity() <= 0) {

                throw new ValidationException(
                        "Quantity must be greater than 0",
                        "ERR_INVALID_QUANTITY",
                        fieldPrefix + ".quantity"
                );
            }

            if (itemDto.getUnitPriceExGst() == null) {
                throw new ValidationException(
                        "unitPriceExGst is required",
                        "ERR_UNIT_PRICE_REQUIRED",
                        fieldPrefix + ".unitPriceExGst"
                );
            }

            if (itemDto.getUnitPriceExGst()
                    .compareTo(BigDecimal.ZERO) < 0) {

                throw new ValidationException(
                        "unitPriceExGst cannot be negative",
                        "ERR_INVALID_UNIT_PRICE",
                        fieldPrefix + ".unitPriceExGst"
                );
            }

            /*
             * GST rate is required only for domestic GST-applicable
             * customers.
             */
            if (gstApplicable
                    && itemDto.getGstRate() == null) {

                throw new ValidationException(
                        "gstRate is required for registered and unregistered customers",
                        "ERR_GST_RATE_REQUIRED",
                        fieldPrefix + ".gstRate"
                );
            }

            if (itemDto.getGstRate() != null
                    && itemDto.getGstRate()
                    .compareTo(BigDecimal.ZERO) < 0) {

                throw new ValidationException(
                        "gstRate cannot be negative",
                        "ERR_INVALID_GST_RATE",
                        fieldPrefix + ".gstRate"
                );
            }

            EstimateLineItem lineItem =
                    new EstimateLineItem();

            lineItem.setEstimate(estimate);

            lineItem.setSourceItemId(
                    itemDto.getSourceItemId()
            );

            lineItem.setItemName(
                    itemDto.getItemName().trim()
            );

            lineItem.setDescription(
                    itemDto.getDescription()
            );

            lineItem.setHsnSacCode(
                    itemDto.getHsnSacCode()
            );

            lineItem.setQuantity(
                    itemDto.getQuantity()
            );

            lineItem.setUnit(
                    itemDto.getUnit()
            );

            lineItem.setUnitPriceExGst(
                    itemDto.getUnitPriceExGst()
                            .setScale(
                                    MONEY_SCALE,
                                    ROUNDING_MODE
                            )
            );

            /*
             * CRITICAL:
             *
             * SEZ and INTERNATIONAL always use zero GST.
             * Even if the frontend sends gstRate = 18,
             * effective GST rate will be zero.
             */
            BigDecimal effectiveGstRate;

            if (isSez || isInternational) {
                effectiveGstRate =
                        BigDecimal.ZERO;
            } else {
                effectiveGstRate =
                        itemDto.getGstRate() != null
                                ? itemDto.getGstRate()
                                : BigDecimal.ZERO;
            }

            lineItem.setGstRate(
                    effectiveGstRate.setScale(
                            MONEY_SCALE,
                            ROUNDING_MODE
                    )
            );

            lineItem.setIgstFlag(
                    igstFlag
            );

            lineItem.setCategoryCode(
                    itemDto.getCategoryCode()
            );

            lineItem.setFeeType(
                    itemDto.getFeeType()
            );

            lineItem.setDisplayOrder(i + 1);

            /*
             * For SEZ or INTERNATIONAL:
             *
             * gstRate   = 0
             * gstAmount = 0
             * igstRate  = 0
             * cgstRate  = 0
             * sgstRate  = 0
             */
            lineItem.calculateLineTotals();

            lineItems.add(lineItem);

            log.info(
                    "Estimate line created | index={} | item={} | gstRegistrationType={} | requestGstRate={} | effectiveGstRate={} | gstAmount={} | igstFlag={}",
                    i,
                    lineItem.getItemName(),
                    gstRegistrationType,
                    itemDto.getGstRate(),
                    lineItem.getGstRate(),
                    lineItem.getGstAmount(),
                    lineItem.getIgstFlag()
            );
        }

        estimate.setLineItems(lineItems);

        // =====================================================
        // 11. CALCULATE TOTALS
        // =====================================================

        estimate.calculateTotals();

        /*
         * Defensive zero-rated enforcement at Estimate header.
         */
        if (zeroRatedSupply) {
            estimate.setTotalGstAmount(zeroMoney());
            estimate.setCgstAmount(zeroMoney());
            estimate.setSgstAmount(zeroMoney());
            estimate.setIgstAmount(zeroMoney());
        }

        // =====================================================
        // 12. FINAL DOCUMENT ROUNDING
        // =====================================================

        BigDecimal rawEstimateTotal = safeMoney(estimate.getSubTotalExGst())
                .add(safeMoney(estimate.getTotalGstAmount()))
                .setScale(MONEY_SCALE, ROUNDING_MODE);

        BigDecimal finalEstimateTotal = rawEstimateTotal
                .setScale(DOCUMENT_SCALE, ROUNDING_MODE);

        BigDecimal roundOffAmount = finalEstimateTotal
                .subtract(rawEstimateTotal)
                .setScale(MONEY_SCALE, ROUNDING_MODE);

        estimate.setGrandTotal(finalEstimateTotal);
        estimate.setRoundOffAmount(roundOffAmount);

        // =====================================================
        // 13. SAVE ESTIMATE
        // =====================================================

        log.info(
                "Saving estimate | number={} | companyId={} | unitId={} | gstRegistrationType={} | subtotal={} | gst={} | grandTotal={}",
                estimateNumber,
                company.getId(),
                unit != null ? unit.getId() : null,
                gstRegistrationType,
                estimate.getSubTotalExGst(),
                estimate.getTotalGstAmount(),
                estimate.getGrandTotal()
        );

        Estimate savedEstimate =
                estimateRepository.save(estimate);

        log.info(
                "Estimate created successfully | id={} | number={} | gstRegistrationType={} | totalGst={} | grandTotal={}",
                savedEstimate.getId(),
                savedEstimate.getEstimateNumber(),
                savedEstimate.getGstRegistrationType(),
                savedEstimate.getTotalGstAmount(),
                savedEstimate.getGrandTotal()
        );

        // =====================================================
        // 14. RESPONSE
        // =====================================================

        return mapToResponseDto(savedEstimate);
    }






    private static BigDecimal safeMoney(BigDecimal value) {
        return value == null
                ? zeroMoney()
                : value.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    private String generateEstimateNumber() {
        long count = estimateRepository.count() + 1;
        String number = String.format("EST-%d-%06d", LocalDate.now().getYear(), count);
        log.debug("Generated estimate number: {}", number);
        return number;
    }

    private String generatePINumber() {
        long count = estimateRepository.count() + 1;
        String number = String.format("PI-%d-%06d", LocalDate.now().getYear(), count);
        log.debug("Generated PI number: {}", number);
        return number;
    }

    @Override
    public EstimateResponseDto getEstimateById(Long estimateId, Long requestingUserId) {
        log.info("Fetching estimate | estimateId={} | requestedByUser={}", estimateId, requestingUserId);

        if (requestingUserId == null || requestingUserId <= 0) {
            throw new ValidationException("Invalid requestingUserId", "ERR_INVALID_REQUESTING_USER", "requestingUserId");
        }

        // Basic security check
        if (!userRepository.existsById(requestingUserId)) {
            log.warn("User not found: userId={}", requestingUserId);
            throw new ResourceNotFoundException("User not found", "USER_NOT_FOUND");
        }

        if (estimateId == null || estimateId <= 0) {
            throw new ValidationException("Invalid estimateId", "ERR_INVALID_ESTIMATE_ID", "estimateId");
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
            throw new ValidationException("Invalid lead ID", "ERR_INVALID_LEAD_ID", "leadId");
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
            throw new ValidationException("Invalid company ID", "ERR_INVALID_COMPANY_ID", "companyId");
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
        dto.setLeadId(estimate.getLeadId());
        dto.setPublicUuid(estimate.getPublicUuid());
        dto.setProposalId(estimate.getProposalId());
        dto.setClientPoNumber(
                estimate.getClientPoNumber()
        );

        dto.setEstimateNumber(estimate.getEstimateNumber());
        dto.setPerformanceInvoiceNumber(estimate.getPerformanceInvoiceNumber());
        dto.setPerformanceInvoiceFlag(estimate.isPerformanceInvoiceFlag());
        dto.setEstimateDate(estimate.getEstimateDate());
        dto.setValidUntil(estimate.getValidUntil());
        dto.setSolutionName(estimate.getSolutionName());
        dto.setSolutionType(estimate.getSolutionType() != null ? estimate.getSolutionType() : null);
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
            companyDto.setOnboardingStatus(
                    company.getOnboardingStatus() != null ?
                            company.getOnboardingStatus().name() : null
            );
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
            unitDto.setGstRegistrationType(
                    unit.getGstRegistrationType() != null
                            ? unit.getGstRegistrationType().name()
                            : null
            );

            unitDto.setStatus(unit.getStatus());
            unitDto.setOnboardingStatus(
                    unit.getOnboardingStatus() != null ?
                            unit.getOnboardingStatus().name() : null
            );
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
                itemDto.setIgstFlag(item.getIgstFlag());
                itemDto.setIgstRate(item.getIgstRate());
                itemDto.setSgstRate(item.getSgstRate());
                itemDto.setCgstRate(item.getCgstRate());
                itemDto.setLineTotalExGst(item.getLineTotalExGst());
                itemDto.setGstAmount(item.getGstAmount());
                itemDto.setDisplayOrder(item.getDisplayOrder());
                itemDto.setCategoryCode(item.getCategoryCode());
                itemDto.setFeeType(item.getFeeType());

                itemDtos.add(itemDto);
            }
        }
        dto.setLineItems(itemDtos);


        Optional<UnbilledInvoice> unbilledOpt = unbilledInvoiceRepository.
                findTopByEstimateAndIsCancelledFalseOrderByCreatedAtDesc(estimate);

        if (unbilledOpt.isPresent()) {

            UnbilledInvoice unbilled = unbilledOpt.get();

            /*
             * Set paymentTypeId only when unbilled has approved received amount.
             * receivedAmount = approved payment amount
             * currentReceivedAmount = pending payment amount
             */
            if (unbilled.getReceivedAmount() != null
                    && unbilled.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0) {

                if (unbilled.getPayments() != null && !unbilled.getPayments().isEmpty()) {
                    PaymentReceipt receipt = unbilled.getPayments().get(0);

                    if (receipt.getPaymentType() != null) {
                        dto.setPaymentTypeId(receipt.getPaymentType().getId());
                        dto.setPaymentTypeCode(receipt.getPaymentType().getCode());
                    } else {
                        dto.setPaymentTypeId(null);
                        dto.setPaymentTypeCode(null);
                    }
                } else {
                    dto.setPaymentTypeId(null);
                    dto.setPaymentTypeCode(null);
                }

            } else {
                dto.setPaymentTypeId(null);
                dto.setPaymentTypeCode(null);
            }
        }

        return dto;
    }

    @Override
    public long getEstimatesCount(
            Long requestingUserId,
            String search,
            String status,
            LocalDate fromDate,
            LocalDate toDate
    ) {

        log.info("Counting estimates with filters | requestedBy={}", requestingUserId);

        if (requestingUserId == null || requestingUserId <= 0) {
            throw new ValidationException(
                    "Invalid requestingUserId",
                    "ERR_INVALID_REQUESTING_USER",
                    "requestingUserId"
            );
        }

        User user = userRepository.findByIdAndNotDeleted(requestingUserId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found", "USER_NOT_FOUND")
                );

        boolean isAdmin = user.getUserRole().stream()
                .anyMatch(role ->
                        "ADMIN".equalsIgnoreCase(role.getName()) ||
                                "SUPER_ADMIN".equalsIgnoreCase(role.getName())
                );

        search = normalizeString(search);
        status = normalizeString(status);

        Specification<Estimate> spec = Specification
                .where(searchingQueryBuilder(search))
                .and(statusFilter(status))
                .and(dateRangeFilter(fromDate, toDate))
                .and((root, query, cb) ->
                        cb.isFalse(root.get("isDeleted"))
                );

        if (!isAdmin) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("createdBy").get("id"), requestingUserId)
            );
        }

        return estimateRepository.count(spec);
    }

    @Override
    @Transactional
    public EstimateResponseDto sendEstimateToClient(Long estimateId, Long requestingUserId) {

        Estimate estimate = estimateRepository.findById(estimateId)
                .orElseThrow(() -> new ResourceNotFoundException("Estimate not found", "EST_NOT_FOUND"));

        if (!estimate.getCreatedBy().getId().equals(requestingUserId)) {
            throw new ValidationException("Only the creator can send this estimate right now", "FORBIDDEN");
        }

        if (estimate.getStatus() != EstimateStatus.DRAFT) {
            throw new ValidationException(
                    "Can only send estimates in DRAFT status. Current: " + estimate.getStatus(),
                    "ERR_INVALID_STATUS"
            );
        }

        if (estimate.getUnit() == null || estimate.getUnit().getId() == null) {
            throw new ValidationException("No company unit linked to this estimate", "ERR_NO_COMPANY_UNIT");
        }

        List<String> sentEmails = emailServiceImpl.sendEstimateEmailToUnitContacts(estimate);

        if (sentEmails == null || sentEmails.isEmpty()) {
            throw new ValidationException("No valid emails found to send estimate", "ERR_NO_EMAIL");
        }

        String primaryEmail = sentEmails.get(0);
        String allEmails = String.join(",", sentEmails);

        estimate.setStatus(EstimateStatus.SENT_TO_CLIENT);
        estimate.setSentToClientAt(LocalDateTime.now());
        estimate.setSentToEmail(primaryEmail);
        estimate.setLastSentEmails(allEmails);
        estimate.setSentByUserName(
                estimate.getCreatedBy() != null ? estimate.getCreatedBy().getFullName() : null
        );

        estimate = estimateRepository.save(estimate);

        log.info("Estimate sent successfully | estimateId={} | recipients={} | triggeredBy={}",
                estimateId, allEmails, requestingUserId);

        return mapToResponseDto(estimate);
    }
    @Override
    public List<EstimateResponseDto>  getAllEstimates(
            Long requestingUserId,
            String search,
            String status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {

        log.info("Fetching all estimates | requestedBy={} | page={} | size={}", requestingUserId, page, size);

        if (requestingUserId == null || requestingUserId <= 0) {
            throw new ValidationException("Invalid requestingUserId", "ERR_INVALID_REQUESTING_USER", "requestingUserId");
        }
        if (page < 0) {
            throw new ValidationException("page must be >= 0", "ERR_INVALID_PAGE", "page");
        }
        if (size <= 0 || size > 200) {
            throw new ValidationException("size must be between 1 and 200", "ERR_INVALID_SIZE", "size");
        }

        // 1. Validate user exists
        User user = userRepository.findByIdAndNotDeleted(requestingUserId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found", "USER_NOT_FOUND"));

        // 2. Check if user has admin privileges
        boolean isAdmin = user.getUserRole().stream()
                .anyMatch(role ->
                        "ADMIN".equalsIgnoreCase(role.getName())
                );

        // 3. Prepare pagination + default sorting (newest first)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));


        search = normalizeString(search);
        status = normalizeString(status);
        Specification<Estimate> spec = Specification
                .where(searchingQueryBuilder(search))
                .and(statusFilter(status))
                .and(dateRangeFilter(fromDate, toDate))
                .and((root, query, cb) -> cb.isFalse(root.get("isDeleted")));


        if (!isAdmin) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("createdBy").get("id"), requestingUserId)
            );
        }

        return estimateRepository
                .findAll(spec, pageable)
                .getContent()
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }
    private Specification<Estimate> searchingQueryBuilder(String search) {

        return (root, query, cb) -> {

            if (search == null || search.trim().isEmpty()) {
                return cb.conjunction();
            }

            query.distinct(true); // Important when joining

            String likePattern = "%" + search.toLowerCase() + "%";

            List<Predicate> predicates = new ArrayList<>();

            //  Search by estimate number
            predicates.add(
                    cb.like(cb.lower(root.get("estimateNumber")), likePattern)
            );

            // 2️⃣ Search by company name
            Join<Estimate, Company> companyJoin =
                    root.join("company", JoinType.LEFT);

            predicates.add(
                    cb.like(cb.lower(companyJoin.get("name")), likePattern)
            );

            // 3️⃣  NEW — Search by unit name
            Join<Estimate, CompanyUnit> unitJoin =
                    root.join("unit", JoinType.LEFT);

            predicates.add(
                    cb.like(cb.lower(unitJoin.get("unitName")), likePattern)
            );

            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }
    private Specification<Estimate> statusFilter(String status) {

        return (root, query, cb) -> {

            if (status == null || status.isBlank()) {
                return cb.conjunction();
            }

            EstimateStatus enumStatus;
            try {
                enumStatus = EstimateStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ValidationException(
                        "Invalid estimate status: " + status,
                        "ERR_INVALID_STATUS",
                        "status"
                );
            }

            return cb.equal(root.get("status"), enumStatus);
        };
    }
    private Specification<Estimate> dateRangeFilter(
            LocalDate fromDate,
            LocalDate toDate) {

        return (root, query, cb) -> {

            if (fromDate == null && toDate == null) {
                return cb.conjunction();
            }

            if (fromDate != null && toDate != null) {
                return cb.between(
                        root.get("estimateDate"),
                        fromDate,
                        toDate
                );
            }

            if (fromDate != null) {
                return cb.greaterThanOrEqualTo(
                        root.get("estimateDate"),
                        fromDate
                );
            }

            return cb.lessThanOrEqualTo(
                    root.get("estimateDate"),
                    toDate
            );
        };
    }
    private String normalizeString(String value) {
        if (value == null) return null;

        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        if ("string".equalsIgnoreCase(trimmed)) return null;

        return trimmed;
    }



    @Override
    public EstimateDashboardResponse getEstimateDashboard(
            EstimateDashboardFilterRequest request
    ) {

        if (request.getUserId() == null || request.getUserId() <= 0) {
            throw new ValidationException("Invalid userId",
                    "ERR_INVALID_USER", "userId");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found",
                                "USER_NOT_FOUND"));

        boolean isAdmin = user.getUserRole().stream()
                .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));

//        System.out.println("user.getUserRole():  "+user.getUserRole());

        Specification<Estimate> spec = buildSpecification(request, isAdmin);

        List<Estimate> estimates = estimateRepository.findAll(spec);

        return buildDashboardResponse(estimates);
    }
    private Specification<Estimate> buildSpecification(
            EstimateDashboardFilterRequest request,
            boolean isAdmin
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (!isAdmin) {
                predicates.add(
                        cb.equal(root.get("createdBy").get("id"),
                                request.getUserId())
                );
            }

            if (request.getFromDate() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("estimateDate"),
                                request.getFromDate()
                        )
                );
            }

            if (request.getToDate() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("estimateDate"),
                                request.getToDate()
                        )
                );
            }

            if (request.getStatus() != null &&
                    !request.getStatus().isBlank()) {

                EstimateStatus status =
                        EstimateStatus.valueOf(
                                request.getStatus().toUpperCase());

                predicates.add(
                        cb.equal(root.get("status"), status)
                );
            }

            if (request.getMinAmount() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("grandTotal"),
                                request.getMinAmount()
                        )
                );
            }

            if (request.getMaxAmount() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("grandTotal"),
                                request.getMaxAmount()
                        )
                );
            }

            if (request.getCompanyId() != null) {
                predicates.add(
                        cb.equal(root.get("company").get("id"),
                                request.getCompanyId())
                );
            }

            if (request.getCompanyName() != null &&
                    !request.getCompanyName().isBlank()) {

                Join<Object, Object> company =
                        root.join("company", JoinType.INNER);

                predicates.add(
                        cb.like(
                                cb.lower(company.get("name")),
                                "%" + request.getCompanyName()
                                        .toLowerCase() + "%"
                        )
                );
            }

            if (request.getUnitId() != null) {
                predicates.add(
                        cb.equal(root.get("unit").get("id"),
                                request.getUnitId())
                );
            }

            if (request.getSolutionType() != null &&
                    !request.getSolutionType().isBlank()) {

                predicates.add(
                        cb.equal(root.get("solutionType"),
                                request.getSolutionType())
                );
            }

            if (Boolean.TRUE.equals(request.getSentOnly())) {
                predicates.add(
                        cb.isNotNull(root.get("sentToClientAt"))
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
    private EstimateDashboardResponse buildDashboardResponse(
            List<Estimate> estimates
    ) {

        long totalCount = estimates.size();
        List<Long> estimateIds = estimates.stream()
                .map(Estimate::getId)
                .toList();
        List<UnbilledInvoice> unbilledList =
                unbilledRepository.findByEstimateIdInAndIsCancelledFalse(estimateIds);

        long totalUnbilledCount = unbilledList.size();

        BigDecimal totalUnbilledAmount = unbilledList.stream()
                .map(UnbilledInvoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReceivedAmount = unbilledList.stream()
                .map(UnbilledInvoice::getReceivedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstandingAmount = unbilledList.stream()
                .map(UnbilledInvoice::getOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);


        long totalInvoiceCount = unbilledList.stream()
                .mapToLong(u -> u.getTaxInvoices().size())
                .sum();

        BigDecimal totalInvoicedAmount = unbilledList.stream()
                .flatMap(u -> u.getTaxInvoices().stream())
                .map(Invoice::getGrandTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);



        double conversionRate = totalCount > 0
                ? (totalUnbilledCount * 100.0) / totalCount
                : 0.0;

        double collectionEfficiency = totalUnbilledAmount.compareTo(BigDecimal.ZERO) > 0
                ? totalReceivedAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(totalUnbilledAmount, 2, RoundingMode.HALF_UP)
                .doubleValue()
                : 0.0;





        BigDecimal totalRevenue = estimates.stream()
                .map(Estimate::getGrandTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSubTotal = estimates.stream()
                .map(Estimate::getSubTotalExGst)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGst = estimates.stream()
                .map(Estimate::getTotalGstAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avg = totalCount > 0
                ? totalRevenue.divide(
                BigDecimal.valueOf(totalCount),
                2,
                RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Status breakdown
        Map<String, Long> statusCount =
                estimates.stream()
                        .collect(Collectors.groupingBy(
                                e -> e.getStatus().name(),
                                Collectors.counting()
                        ));

        Map<String, BigDecimal> statusRevenue =
                estimates.stream()
                        .collect(Collectors.groupingBy(
                                e -> e.getStatus().name(),
                                Collectors.mapping(
                                        Estimate::getGrandTotal,
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                BigDecimal::add)
                                )
                        ));

        // Monthly Trend
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM");

        Map<String, List<Estimate>> groupedByMonth =
                estimates.stream()
                        .collect(Collectors.groupingBy(
                                e -> e.getEstimateDate()
                                        .format(formatter)
                        ));

        List<MonthlyTrendDto> monthlyTrend =
                groupedByMonth.entrySet().stream()
                        .map(entry -> new MonthlyTrendDto(
                                entry.getKey(),
                                (long) entry.getValue().size(),
                                entry.getValue().stream()
                                        .map(Estimate::getGrandTotal)
                                        .reduce(BigDecimal.ZERO,
                                                BigDecimal::add)
                        ))
                        .sorted(Comparator.comparing(
                                MonthlyTrendDto::getMonth))
                        .toList();

        // Top Companies
        Map<String, List<Estimate>> groupedByCompany =
                estimates.stream()
                        .collect(Collectors.groupingBy(
                                e -> e.getCompany().getName()
                        ));

        List<CompanyRevenueDto> topCompanies =
                groupedByCompany.entrySet().stream()
                        .map(entry -> new CompanyRevenueDto(
                                entry.getKey(),
                                (long) entry.getValue().size(),
                                entry.getValue().stream()
                                        .map(Estimate::getGrandTotal)
                                        .reduce(BigDecimal.ZERO,
                                                BigDecimal::add)
                        ))
                        .sorted((a, b) ->
                                b.getRevenue()
                                        .compareTo(a.getRevenue()))
                        .limit(5)
                        .toList();

        long sentCount = estimates.stream()
                .filter(e -> e.getSentToClientAt() != null)
                .count();

        long draftCount = estimates.stream()
                .filter(e -> e.getStatus()
                        == EstimateStatus.DRAFT)
                .count();

        long approvedCount = estimates.stream()
                .filter(e -> e.getStatus()
                        == EstimateStatus.APPROVED)
                .count();

        return new EstimateDashboardResponse(
                totalCount,
                totalRevenue,
                totalSubTotal,
                totalGst,
                avg,
                statusCount,
                statusRevenue,
                monthlyTrend,
                topCompanies,
                sentCount,
                draftCount,
                approvedCount,
                totalUnbilledCount,
                totalUnbilledAmount,
                totalInvoiceCount,
                totalInvoicedAmount,
                totalReceivedAmount,
                totalOutstandingAmount,
                conversionRate,
                collectionEfficiency
        );
    }



    @Override
    public Page<EstimateResponseDto> estimateReport(
            EstimateSearchRequest request
    ) {

        if (request.getUserId() == null || request.getUserId() <= 0) {
            throw new ValidationException("Invalid userId",
                    "ERR_INVALID_USER", "userId");
        }

        User user = userRepository.findByIdAndNotDeleted(request.getUserId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found", "USER_NOT_FOUND"));



        boolean isAdmin = user.getUserRole().stream()
                .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));

        Specification<Estimate> spec = buildSpecificationReport(request, isAdmin);

        Sort sort = Sort.by(
                Sort.Direction.fromString(request.getSortDirection()),
                request.getSortBy()
        );

        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                sort
        );

        Page<Estimate> pageResult =
                estimateRepository.findAll(spec, pageable);

        return pageResult.map(this::mapToEstimateResponseDto);
    }
    private Specification<Estimate> buildSpecificationReport(
            EstimateSearchRequest request,
            boolean isAdmin
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (!isAdmin) {
                predicates.add(
                        cb.equal(root.get("createdBy").get("id"),
                                request.getUserId())
                );
            }

            if (request.getFromDate() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("estimateDate"),
                                request.getFromDate()
                        )
                );
            }

            if (request.getToDate() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("estimateDate"),
                                request.getToDate()
                        )
                );
            }

            if (request.getStatus() != null &&
                    !request.getStatus().isBlank()) {

                EstimateStatus status =
                        EstimateStatus.valueOf(
                                request.getStatus().toUpperCase());

                predicates.add(
                        cb.equal(root.get("status"), status)
                );
            }

            if (request.getMinAmount() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("grandTotal"),
                                request.getMinAmount()
                        )
                );
            }

            if (request.getMaxAmount() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("grandTotal"),
                                request.getMaxAmount()
                        )
                );
            }

            if (request.getCompanyId() != null) {
                predicates.add(
                        cb.equal(root.get("company").get("id"),
                                request.getCompanyId())
                );
            }

            if (request.getCompanyName() != null &&
                    !request.getCompanyName().isBlank()) {

                Join<Object, Object> company =
                        root.join("company", JoinType.INNER);

                predicates.add(
                        cb.like(
                                cb.lower(company.get("name")),
                                "%" + request.getCompanyName()
                                        .toLowerCase() + "%"
                        )
                );
            }

            if (request.getUnitId() != null) {
                predicates.add(
                        cb.equal(root.get("unit").get("id"),
                                request.getUnitId())
                );
            }

            if (request.getSolutionType() != null &&
                    !request.getSolutionType().isBlank()) {

                predicates.add(
                        cb.equal(root.get("solutionType"),
                                request.getSolutionType())
                );
            }

            if (Boolean.TRUE.equals(request.getSentOnly())) {
                predicates.add(
                        cb.isNotNull(root.get("sentToClientAt"))
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
    private EstimateResponseDto mapToEstimateResponseDto(Estimate estimate) {

        EstimateResponseDto dto = new EstimateResponseDto();

        dto.setId(estimate.getId());
        dto.setPublicUuid(estimate.getPublicUuid());
        dto.setProposalId(estimate.getProposalId());
        dto.setLeadId(estimate.getLeadId());
        dto.setClientPoNumber(estimate.getClientPoNumber());
        dto.setEstimateNumber(estimate.getEstimateNumber());
        dto.setPerformanceInvoiceNumber(estimate.getPerformanceInvoiceNumber());
        dto.setPerformanceInvoiceFlag(estimate.isPerformanceInvoiceFlag());
        dto.setEstimateDate(estimate.getEstimateDate());
        dto.setValidUntil(estimate.getValidUntil());

        dto.setSolutionName(estimate.getSolutionName());
        dto.setSolutionType(estimate.getSolutionType());
        dto.setStatus(estimate.getStatus() != null
                ? estimate.getStatus().name()
                : null);

        dto.setCurrency(estimate.getCurrency());


        dto.setSubTotalExGst(estimate.getSubTotalExGst());
        dto.setTotalGstAmount(estimate.getTotalGstAmount());
        dto.setCgstAmount(estimate.getCgstAmount());
        dto.setSgstAmount(estimate.getSgstAmount());
        dto.setIgstAmount(estimate.getIgstAmount());
        dto.setGrandTotal(estimate.getGrandTotal());

        dto.setCustomerNotes(estimate.getCustomerNotes());
        dto.setInternalRemarks(estimate.getInternalRemarks());

        dto.setVersion(estimate.getVersion());
        dto.setRevisionReason(estimate.getRevisionReason());

        dto.setCreatedAt(estimate.getCreatedAt());

        if (estimate.getCreatedBy() != null) {
            dto.setCreatedById(estimate.getCreatedBy().getId());
            dto.setCreatedByName(estimate.getCreatedBy().getFullName());
        }

        if (estimate.getCompany() != null) {
            dto.setCompany(new CompanySummaryDto(
                    estimate.getCompany().getId(),
                    estimate.getCompany().getName(),
                    estimate.getCompany().getPanNo(),
                    null,
                    null,
                    null,
                    null,
                    estimate.getCompany().getOnboardingStatus() != null
                            ? estimate.getCompany().getOnboardingStatus().name()
                            : null
            ));
        }

        if (estimate.getUnit() != null) {
            dto.setUnit(new CompanyUnitSummaryDto(
                    estimate.getUnit().getId(),
                    estimate.getUnit().getUnitName(),
                    estimate.getUnit().getAddressLine1(),
                    estimate.getUnit().getAddressLine2(),
                    estimate.getUnit().getCity(),
                    estimate.getUnit().getState(),
                    estimate.getUnit().getPinCode(),
                    estimate.getUnit().getGstNo(),

                    estimate.getUnit().getGstRegistrationType() != null
                            ? estimate.getUnit().getGstRegistrationType().name()
                            : null,

                    estimate.getUnit().getStatus(),

                    estimate.getUnit().getOnboardingStatus() != null
                            ? estimate.getUnit().getOnboardingStatus().name()
                            : null
            ));
        }

        dto.setLineItems(null);

        return dto;
    }



    @Override
    public Page<EstimateResponseDto> searchEstimates(EstimateSearchRequestDto request, Long userId) {

        if ( userId== null || userId <= 0) {
            throw new ValidationException(
                    "Invalid userId",
                    "ERR_INVALID_USER",
                    "userId"
            );
        }

        User user = userRepository.findByIdAndNotDeleted(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found",
                                "USER_NOT_FOUND"
                        )
                );


        boolean isAdmin = user.getUserRole().stream()
                .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));

        Specification<Estimate> spec =
                buildSearchSpecification(request, isAdmin, userId);

        Sort sort = Sort.by(
                Sort.Direction.fromString(request.getSortDirection()),
                request.getSortBy()
        );

        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                sort
        );


        return estimateRepository
                .findAll(spec, pageable)
                .map(this::mapToResponseDto);
    }
    private Specification<Estimate> buildSearchSpecification(
            EstimateSearchRequestDto request,
            boolean isAdmin,
            Long userId
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isFalse(root.get("isDeleted")));

            // 🔐 Role-based access
            if (!isAdmin) {
                predicates.add(
                        cb.equal(
                                root.get("createdBy").get("id"),
                                userId
                        )
                );
            }

            // 🔍 Free text search
            if (request.getQuery() != null && !request.getQuery().isBlank()) {

                String likePattern = "%" + request.getQuery().toLowerCase() + "%";

                Join<Object, Object> companyJoin =
                        root.join("company", JoinType.LEFT);

                Join<Object, Object> unitJoin =
                        root.join("unit", JoinType.LEFT);

                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("estimateNumber")), likePattern),
                                cb.like(cb.lower(root.get("solutionName")), likePattern),
                                cb.like(cb.lower(companyJoin.get("name")), likePattern),
                                cb.like(cb.lower(unitJoin.get("gstNo")), likePattern),
                                cb.like(cb.lower(unitJoin.get("unitName")), likePattern)
                        )
                );
            }

            // 🎯 Exact filters
            if (request.getCompanyId() != null) {
                predicates.add(cb.equal(root.get("company").get("id"), request.getCompanyId()));
            }

            if (request.getUnitId() != null) {
                predicates.add(cb.equal(root.get("unit").get("id"), request.getUnitId()));
            }

            if (request.getContactId() != null) {
                predicates.add(cb.equal(root.get("contact").get("id"), request.getContactId()));
            }

            if (request.getLeadId() != null) {
                predicates.add(cb.equal(root.get("leadId"), request.getLeadId()));
            }

            if (request.getEstimateNumber() != null &&
                    !request.getEstimateNumber().isBlank()) {

                predicates.add(
                        cb.like(
                                cb.lower(root.get("estimateNumber")),
                                "%" + request.getEstimateNumber().toLowerCase() + "%"
                        )
                );
            }

            if (request.getStatus() != null &&
                    !request.getStatus().isBlank()) {

                EstimateStatus status =
                        EstimateStatus.valueOf(request.getStatus().toUpperCase());

                predicates.add(cb.equal(root.get("status"), status));
            }

            if (request.getSolutionType() != null &&
                    !request.getSolutionType().isBlank()) {

                predicates.add(
                        cb.equal(root.get("solutionType"), request.getSolutionType())
                );
            }

            // 📅 Created date range
            if (request.getCreatedFrom() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("createdAt"),
                                request.getCreatedFrom().atStartOfDay()
                        )
                );
            }

            if (request.getCreatedTo() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("createdAt"),
                                request.getCreatedTo().atTime(23, 59, 59)
                        )
                );
            }

            // 📅 Valid until range
            if (request.getValidUntilFrom() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("validUntil"),
                                request.getValidUntilFrom()
                        )
                );
            }

            if (request.getValidUntilTo() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("validUntil"),
                                request.getValidUntilTo()
                        )
                );
            }

            // 💰 Amount range
            if (request.getMinGrandTotal() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("grandTotal"),
                                request.getMinGrandTotal()
                        )
                );
            }

            if (request.getMaxGrandTotal() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("grandTotal"),
                                request.getMaxGrandTotal()
                        )
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }



    @Override
    public void sendEstimate(Long estimateId, Long requestingUserId){

        log.info("Sending email with  estimate | estimateId={} | requestedByUser={}", estimateId, requestingUserId);

        if (requestingUserId == null || requestingUserId <= 0) {
            throw new ValidationException("Invalid requestingUserId", "ERR_INVALID_REQUESTING_USER", "requestingUserId");
        }

        // Basic security check
        if (!userRepository.existsById(requestingUserId)) {
            log.warn("User not found: userId={}", requestingUserId);
            throw new ResourceNotFoundException("User not found", "USER_NOT_FOUND");
        }

        if (estimateId == null || estimateId <= 0) {
            throw new ValidationException("Invalid estimateId", "ERR_INVALID_ESTIMATE_ID", "estimateId");
        }

        // Fetch the estimate
        Estimate estimate = estimateRepository.findById(estimateId)
                .orElseThrow(() -> {
                    log.warn("Estimate not found: id={}", estimateId);
                    return new ResourceNotFoundException("Estimate not found", "ESTIMATE_NOT_FOUND");
                });

        if(estimate.getCompany() == null){

        }

//        emailService.sendEmail();




    }


    @Override
    public EstimateResponseDto convertIntoPI(Long estimateId, Long requestingUserId){
        log.info("Converting estimate into performace invoice", estimateId, requestingUserId);

        if (requestingUserId == null || requestingUserId <= 0) {
            throw new ValidationException("Invalid requestingUserId", "ERR_INVALID_REQUESTING_USER", "requestingUserId");
        }

        // Basic security check
        if (!userRepository.existsById(requestingUserId)) {
            log.warn("User not found: with userId={}", requestingUserId);
            throw new ResourceNotFoundException("User not found", "USER_NOT_FOUND");
        }

        if (estimateId == null || estimateId <= 0) {
            throw new ValidationException("Invalid estimateId", "ERR_INVALID_ESTIMATE_ID", "estimateId");
        }

        // Fetch the estimate
        Estimate estimate = estimateRepository.findById(estimateId)
                .orElseThrow(() -> {
                    log.warn("Estimate not found: id={}", estimateId);
                    return new ResourceNotFoundException("Estimate not found", "ESTIMATE_NOT_FOUND");
                });


        estimate.setPerformanceInvoiceFlag(true);
        estimateRepository.save(estimate);
        return mapToEstimateResponseDto(estimate);

    }


    @Override
    @Transactional
    public EstimateStatusResponseDto cancelEstimateByProposalId(
            Long proposalId,
            EstimateCancelRequestDto requestDto) {

        if (proposalId == null || proposalId <= 0) {
            throw new ValidationException(
                    "Invalid proposal id",
                    "ERR_INVALID_PROPOSAL_ID",
                    "proposalId"
            );
        }

        if (requestDto == null) {
            throw new ValidationException("Request body is required", "ERR_REQUEST_REQUIRED");
        }

        if (requestDto.getCancelledByUserId() == null || requestDto.getCancelledByUserId() <= 0) {
            throw new ValidationException("Invalid cancelledByUserId", "ERR_INVALID_CANCELLED_BY", "cancelledByUserId");
        }

        if (requestDto.getCancellationReason() == null || requestDto.getCancellationReason().trim().isEmpty()) {
            throw new ValidationException("Cancellation reason is required", "ERR_CANCELLATION_REASON_REQUIRED", "cancellationReason");
        }

        // Fetch estimate linked to proposal
        Estimate estimate = estimateRepository
                .findByProposalIdAndIsDeletedFalseAndIsCancelledFalse(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Estimate not found with proposal id: " + proposalId,
                        "ESTIMATE_NOT_FOUND"
                ));

        // === NEW: STRICT CHECK FOR GENERATED INVOICE ===
        boolean hasGeneratedInvoice = hasAnyGeneratedTaxInvoice(estimate);

        if (hasGeneratedInvoice) {
            throw new ValidationException(
                    "Cannot cancel this proposal/estimate because tax invoice(s) have already been generated. " +
                            "Please contact Accounts/Admin team to cancel from the Account module.",
                    "ERR_CANNOT_CANCEL_ESTIMATE_WITH_INVOICE"
            );
        }

        return cancelEstimate(estimate.getId(), requestDto);
    }

    /**
     * Checks if any tax invoice has been generated against this estimate
     */
    private boolean hasAnyGeneratedTaxInvoice(Estimate estimate) {
        if (estimate == null) {
            return false;
        }

        Optional<UnbilledInvoice> unbilledOpt =
                unbilledInvoiceRepository.findByEstimateAndIsCancelledFalse(estimate);

        if (unbilledOpt.isPresent()) {
            UnbilledInvoice unbilled = unbilledOpt.get();
            return unbilled.getTaxInvoices() != null && !unbilled.getTaxInvoices().isEmpty();
        }

        return false;
    }


    @Override
    @Transactional
    public EstimateStatusResponseDto cancelEstimate(Long estimateId, EstimateCancelRequestDto requestDto) {

        if (estimateId == null || estimateId <= 0) {
            throw new ValidationException("Invalid estimate id", "ERR_INVALID_ESTIMATE_ID", "estimateId");
        }

        if (requestDto == null) {
            throw new ValidationException("Request body is required", "ERR_REQUEST_REQUIRED");
        }

        if (requestDto.getCancelledByUserId() == null || requestDto.getCancelledByUserId() <= 0) {
            throw new ValidationException("Invalid cancelledByUserId", "ERR_INVALID_CANCELLED_BY", "cancelledByUserId");
        }

        if (requestDto.getCancellationReason() == null || requestDto.getCancellationReason().trim().isEmpty()) {
            throw new ValidationException("Cancellation reason is required", "ERR_CANCELLATION_REASON_REQUIRED", "cancellationReason");
        }

        // Fetch Estimate
        Estimate estimate = estimateRepository.findById(estimateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Estimate not found with id: " + estimateId,
                        "ESTIMATE_NOT_FOUND"
                ));

        if (estimate.isDeleted()) {
            throw new ValidationException("Estimate is deleted", "ERR_ESTIMATE_DELETED");
        }

        if (estimate.isCancelled()) {
            throw new ValidationException("Estimate is already cancelled", "ERR_ESTIMATE_ALREADY_CANCELLED");
        }

        if (estimate.getStatus() == EstimateStatus.APPROVED) {
            throw new ValidationException("Approved estimate cannot be cancelled", "ERR_ESTIMATE_ALREADY_APPROVED");
        }

        // Fetch user who is cancelling
        User cancelledBy = userRepository.findById(requestDto.getCancelledByUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + requestDto.getCancelledByUserId(),
                        "USER_NOT_FOUND"
                ));

        // Handle Unbilled Invoice (if exists)
        Optional<UnbilledInvoice> unbilledOpt = unbilledInvoiceRepository.findByEstimateAndIsCancelledFalse(estimate);

        if (unbilledOpt.isPresent()) {
            UnbilledInvoice unbilled = unbilledOpt.get();

            boolean hasReceivedPayment = unbilled.getReceivedAmount() != null
                    && unbilled.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0;

            boolean hasGeneratedInvoices = !unbilled.getTaxInvoices().isEmpty();

            // Block cancellation only if payment received or invoice generated
            if (hasReceivedPayment || hasGeneratedInvoices || unbilled.getStatus() == UnbilledStatus.APPROVED) {
                throw new ValidationException(
                        "Cannot cancel estimate because payment has been received or invoice has been generated",
                        "ERR_CANNOT_CANCEL_PAID_ESTIMATE"
                );
            }

            // Cancel the unbilled invoice as well
            unbilled.setCancelled(true);
            unbilled.setStatus(UnbilledStatus.CANCELLED);
            unbilled.setRejectionReason("Cancelled along with estimate: " + requestDto.getCancellationReason());
            unbilled.setUpdatedBy(cancelledBy);
            unbilled.setUpdatedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        }

        // Cancel the Estimate
        estimate.setCancelled(true);
        estimate.setStatus(EstimateStatus.CANCELLED);
        estimate.setRejectionReason(requestDto.getCancellationReason().trim());
        estimate.setRejectedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        estimate.setRejectedBy(cancelledBy);
        estimate.setUpdatedBy(cancelledBy);
        estimate.setRevisionReason("Estimate cancelled");

        estimateRepository.save(estimate);

        log.info("Estimate cancelled successfully | estimateId={} | cancelledBy={} | reason={}",
                estimate.getId(), cancelledBy.getId(), requestDto.getCancellationReason());

        return new EstimateStatusResponseDto(
                estimate.getId(),
                estimate.getEstimateNumber(),
                estimate.getStatus().name(),
                "Estimate cancelled successfully"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public EstimatePaymentResponseDto getAllPaymentsEstimate(
            Long estimateId,
            Long requestingUserId
    ) {
        if (estimateId == null) {
            throw new IllegalArgumentException("Estimate ID is required");
        }

        if (requestingUserId == null || requestingUserId <= 0) {
            throw new ValidationException("Invalid requestingUserId", "ERR_INVALID_REQUESTING_USER", "requestingUserId");
        }

        // Basic security check
        if (!userRepository.existsById(requestingUserId)) {
            log.warn("User not found: with userId={}", requestingUserId);
            throw new ResourceNotFoundException("User not found", "USER_NOT_FOUND");
        }

        Estimate estimate = estimateRepository.findById(estimateId).orElseThrow(()-> new ResourceNotFoundException("Estimate Not Found","EST_NOT_FOUND"));


        UnbilledInvoice unbilledInvoice = unbilledInvoiceRepository
                .findTopByEstimateAndIsCancelledFalseOrderByCreatedAtDesc(
                        estimate
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active unbilled invoice found for estimate ID: "
                                + estimateId,"UNBILLED_NOT_FOUND"
                ));

        Optional<TdsRegistration> tdsRegistration = tdsRegistrationRepository.findByEstimateAndIsDeletedFalse(estimate);
        BigDecimal tdsPercentage = tdsRegistration
                .map(TdsRegistration::getTdsPercentage)
                .orElse(null);

        List<PaymentReceipt> paymentReceipts =
                paymentReceiptRepository
                        .findByUnbilledInvoiceIdAndIsCancelledFalseOrderByPaymentDateDescIdDesc(
                                unbilledInvoice.getId()
                        );

        List<EstimatePaymentHistoryDto> paymentHistory =
                paymentReceipts.stream()
                        .map(this::mapPaymentReceipt)
                        .toList();

        return EstimatePaymentResponseDto.builder()
                .estimateId(estimateId)
                .unbilledInvoiceId(unbilledInvoice.getId())
                .unbilledNumber(unbilledInvoice.getUnbilledNumber())
                .totalAmount(zeroIfNull(
                        unbilledInvoice.getTotalAmount()
                ))
                .receivedAmount(zeroIfNull(
                        unbilledInvoice.getReceivedAmount()
                ))
                .outstandingAmount(zeroIfNull(
                        unbilledInvoice.getOutstandingAmount()
                ))
                .currentReceivedAmount(zeroIfNull(
                        unbilledInvoice.getCurrentReceivedAmount()
                ))
                .totalPaymentReceipts(paymentHistory.size())
                .tdsPercentage(tdsPercentage)
                .paymentHistory(paymentHistory)
                .build();
    }

    private EstimatePaymentHistoryDto mapPaymentReceipt(
            PaymentReceipt receipt
    ) {
        return EstimatePaymentHistoryDto.builder()
                .paymentReceiptId(receipt.getId())
                .amount(zeroIfNull(receipt.getAmount()))
                .allocatedAmount(zeroIfNull(
                        receipt.getAllocatedAmount()
                ))
                .unallocatedAmount(zeroIfNull(
                        receipt.getUnallocatedAmount()
                ))
                .paymentDate(receipt.getPaymentDate())
                .paymentMode(
                        receipt.getPaymentMode() == null
                                ? null
                                : receipt.getPaymentMode().toString()
                )
                .transactionReference(
                        receipt.getTransactionReference()
                )
                .status(receipt.getStatus())
                .remarks(receipt.getRemarks())

                .paymentProof(receipt.getPaymentProof())
                .paymentTerms(receipt.getPaymentTerms())
                .paymentTermsDays(
                        receipt.getPaymentTermsDays()
                )
                .paymentTypeId(
                        receipt.getPaymentType() == null
                                ? null
                                : receipt.getPaymentType().getId()
                )
                .receivedByUserId(
                        receipt.getReceivedBy() == null
                                ? null
                                : receipt.getReceivedBy().getId()
                )
                .bankLedgerId(
                        receipt.getBankLedger() == null
                                ? null
                                : receipt.getBankLedger().getId()
                )
                .poNumber(receipt.getPoNumber())
                .poAttachmentUrl(
                        receipt.getPoAttachmentUrl()
                )
                .eprCertificateOrInvoiceNumber(
                        receipt.getEprCertificateOrInvoiceNumber()
                )
                .eprFinancialYear(
                        receipt.getEprFinancialYear()
                )
                .eprPortalRegistrationNumber(
                        receipt.getEprPortalRegistrationNumber()
                )
                .createdAt(receipt.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyUnitProjectOverviewResponseDto getCompanyUnitProjectOverview(
            CompanyUnitProjectOverviewRequestDto request
    ) {

        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Company not found with ID: " + request.getCompanyId(),
                        "COMPANY_NOT_FOUND",
                        "Company",
                        request.getCompanyId()
                ));

        CompanyUnit unit = companyUnitRepository.findByIdAndCompanyIdAndIsDeletedFalse(
                        request.getCompanyUnitId(),
                        request.getCompanyId()
                )
                .orElseThrow(() -> new ValidationException(
                        "Company unit does not exist for the given company",
                        "ERR_INVALID_COMPANY_UNIT_FOR_COMPANY",
                        "companyUnitId"
                ));

        List<Estimate> estimates = estimateRepository
                .findByCompanyIdAndUnitIdAndIsDeletedFalseAndIsCancelledFalseOrderByCreatedAtDesc(
                        company.getId(),
                        unit.getId()
                );

        List<UnitBusinessRecordDto> records = estimates.stream()
                .map(estimate -> {
                    UnbilledInvoice unbilled = unbilledInvoiceRepository
                            .findByEstimateAndIsCancelledFalse(estimate)
                            .orElse(null);

                    OperationProjectResponseDto project = null;

                    if (unbilled != null && unbilled.getUnbilledNumber() != null) {
                        try {
                            ResponseEntity<OperationProjectResponseDto> projectResponse =
                                    operationFeignClient.getProjectByUnbilledNumber(unbilled.getUnbilledNumber());

                            if (projectResponse.getStatusCode().is2xxSuccessful()) {
                                project = projectResponse.getBody();
                            }
                        } catch (FeignException.NotFound ex) {
                            log.info("No project found in operation service for unbilledNumber={}",
                                    unbilled.getUnbilledNumber());
                        } catch (FeignException ex) {
                            log.error("Operation service error while fetching project for unbilledNumber={} status={} message={}",
                                    unbilled.getUnbilledNumber(), ex.status(), ex.getMessage());
                        }
                    }

                    return UnitBusinessRecordDto.builder()
                            .estimateId(estimate.getId())
                            .estimateNumber(estimate.getEstimateNumber())
                            .estimatePublicUuid(estimate.getPublicUuid())
                            .solutionName(estimate.getSolutionName())
                            .solutionId(estimate.getSolutionId())
                            .solutionType(estimate.getSolutionType())
                            .estimateStatus(estimate.getStatus() != null ? estimate.getStatus().name() : null)
                            .estimateDate(estimate.getEstimateDate())
                            .estimateGrandTotal(estimate.getGrandTotal())

                            .unbilledId(unbilled != null ? unbilled.getId() : null)
                            .unbilledNumber(unbilled != null ? unbilled.getUnbilledNumber() : null)
                            .unbilledPublicUuid(unbilled != null ? unbilled.getPublicUuid() : null)
                            .unbilledStatus(unbilled != null && unbilled.getStatus() != null ? unbilled.getStatus().name() : null)
                            .unbilledTotalAmount(unbilled != null ? unbilled.getTotalAmount() : null)
                            .receivedAmount(unbilled != null ? unbilled.getReceivedAmount() : null)
                            .outstandingAmount(unbilled != null ? unbilled.getOutstandingAmount() : null)
                            .governmentFeeActive(unbilled != null ? unbilled.isGovernmentFeeActive() : null)

                            .operationProjectId(project != null ? project.getId() : null)
                            .operationProjectName(project != null ? project.getName() : null)
                            .operationProjectStatus(project != null ? project.getStatusName() : null)
                            .projectUnbilledNumber(project != null ? project.getUnbilledNumber() : null)
                            .projectTotalAmount(project != null ? project.getTotalAmount() : null)
                            .projectDueAmount(project != null ? project.getDueAmount() : null)
                            .build();
                })
                .toList();

        CompanyUnitOverviewDto unitOverview = CompanyUnitOverviewDto.builder()
                .unitId(unit.getId())
                .unitName(unit.getUnitName())
                .city(unit.getCity())
                .state(unit.getState())
                .gstNo(unit.getGstNo())
                .onboardingStatus(unit.getOnboardingStatus() != null ? unit.getOnboardingStatus().name() : null)
                .records(records)
                .build();

        return CompanyUnitProjectOverviewResponseDto.builder()
                .companyId(company.getId())
                .companyName(company.getName())
                .panNo(company.getPanNo())
                .onboardingStatus(company.getOnboardingStatus() != null ? company.getOnboardingStatus().name() : null)
                .companyUnits(List.of(unitOverview))
                .build();
    }

    @Override
    public EstimateResponseDto getEstimateByEstimateNumber(String estimateNumber, Long requestingUserId) {
        log.info("Fetching estimate by estimateNumber: {} | requestedByUser={}", estimateNumber, requestingUserId);

        if (requestingUserId == null || requestingUserId <= 0) {
            throw new ValidationException("Invalid requestingUserId", "ERR_INVALID_REQUESTING_USER", "requestingUserId");
        }

        if (estimateNumber == null || estimateNumber.trim().isEmpty()) {
            throw new ValidationException("Estimate number is required", "ERR_INVALID_ESTIMATE_NUMBER", "estimateNumber");
        }

        // Basic user existence check
        if (!userRepository.existsById(requestingUserId)) {
            throw new ResourceNotFoundException("User not found", "USER_NOT_FOUND");
        }

        Estimate estimate = estimateRepository
                .findByEstimateNumberAndIsDeletedFalse(estimateNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Estimate not found with number: " + estimateNumber,
                        "ESTIMATE_NOT_FOUND"
                ));

        log.info("Estimate found | id={} | number={}", estimate.getId(), estimate.getEstimateNumber());

        return mapToResponseDto(estimate);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateRequest(
            Long estimateId,
            Long requestingUserId
    ) {
        if (estimateId == null) {
            throw new IllegalArgumentException(
                    "Estimate ID is required"
            );
        }

        if (requestingUserId == null) {
            throw new IllegalArgumentException(
                    "Requesting user ID is required"
            );
        }
    }

}
