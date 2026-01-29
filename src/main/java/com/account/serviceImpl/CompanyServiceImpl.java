package com.account.serviceImpl;

import com.account.domain.Company;
import com.account.domain.CompanyUnit;
import com.account.domain.User;
import com.account.dto.BasicCompanyRequestDto;
import com.account.dto.company.request.BasicUnitCreateRequest;
import com.account.dto.company.request.CompanyRequestDto;
import com.account.dto.company.response.CompanyResponseDto;
import com.account.dto.company.request.CompanyUnitRequestDto;
import com.account.dto.company.response.CompanyUnitResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.CompanyRepository;
import com.account.repository.CompanyUnitRepository;
import com.account.repository.UserRepository;
import com.account.service.company.CompanyService;
import com.account.util.DateTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class CompanyServiceImpl implements CompanyService {

    private static final Logger logger = LoggerFactory.getLogger(CompanyServiceImpl.class);

    private final CompanyRepository companyRepository;
    private final CompanyUnitRepository companyUnitRepository;
    private final UserRepository userRepository;
    private final DateTimeUtil dateTimeUtil;

    public CompanyServiceImpl(
            CompanyRepository companyRepository,
            CompanyUnitRepository companyUnitRepository,
            UserRepository userRepository,
            DateTimeUtil dateTimeUtil) {
        this.companyRepository = companyRepository;
        this.companyUnitRepository = companyUnitRepository;
        this.userRepository = userRepository;
        this.dateTimeUtil = dateTimeUtil;
    }

    @Override
    public CompanyResponseDto createCompanyFromLead(CompanyRequestDto requestDto) {

        Long leadCompanyId = requestDto.getLeadCompanyId();
        if (leadCompanyId == null) {
            throw new ValidationException("leadCompanyId is required", "ERR_LEAD_ID_REQUIRED");
        }

        // Find by business key (leadId)
        Company company = companyRepository.findByLeadId(leadCompanyId)
                .orElseGet(Company::new);

        boolean isNew = (company.getId() == null);

        // ── Core mapping ────────────────────────────────────────────────────────
        company.setLeadId(leadCompanyId);

        company.setName(StringUtils.trimWhitespace(requestDto.getName()));
        company.setPanNo(StringUtils.hasText(requestDto.getPanNo())
                ? requestDto.getPanNo().trim().toUpperCase()
                : company.getPanNo());

        company.setEstablishDate(dateTimeUtil.toLocalDate(requestDto.getEstablishDate()));
        company.setIndustry(StringUtils.trimWhitespace(requestDto.getIndustry()));
        company.setStatus(StringUtils.trimWhitespace(requestDto.getStatus()));

        // Agreements
        company.setPaymentTerm(StringUtils.trimWhitespace(requestDto.getPaymentTerm()));
        company.setAggrementPresent(Boolean.TRUE.equals(requestDto.getAggrementPresent()));
        company.setAggrement(requestDto.getAggrement());
        company.setNdaPresent(Boolean.TRUE.equals(requestDto.getNdaPresent()));
        company.setNda(requestDto.getNda());
        company.setRevenue(StringUtils.trimWhitespace(requestDto.getRevenue()));

        company.setIsConsultant(Boolean.TRUE.equals(requestDto.getIsConsultant()));

        // ── Auditing ────────────────────────────────────────────────────────────
        if (isNew && requestDto.getCreatedById() != null) {
            User creator = userRepository.findByIdAndNotDeleted(requestDto.getCreatedById())
                    .orElseThrow(() -> new ValidationException(
                            "Creator user not found or deleted with ID: " + requestDto.getCreatedById(),
                            "USER_NOT_FOUND", "createdById"));
            company.setCreatedBy(creator);
        }

        if (requestDto.getUpdatedById() != null) {
            User updater = userRepository.findByIdAndNotDeleted(requestDto.getUpdatedById())
                    .orElseThrow(() -> new ValidationException(
                            "Updater user not found or deleted with ID: " + requestDto.getUpdatedById(),
                            "USER_NOT_FOUND", "updatedById"));
            company.setUpdatedBy(updater);
        }

        // Defaults for new company
        if (isNew) {
            company.setUuid(dateTimeUtil.generateUuid());
            company.setOnboardingStatus("PendingApproval");
            company.setAccountsApproved(false);
            company.setDeleted(false);
        }

        company = companyRepository.save(company);

        // ── Units sync ──────────────────────────────────────────────────────────
        if (requestDto.getUnits() != null && !requestDto.getUnits().isEmpty()) {
            for (CompanyUnitRequestDto unitDto : requestDto.getUnits()) {

                CompanyUnit unit;
                if (unitDto.getLeadUnitId() != null) {
                    unit = companyUnitRepository.findByLeadId(unitDto.getLeadUnitId())
                            .orElseGet(CompanyUnit::new);
                    unit.setLeadId(unitDto.getLeadUnitId());
                } else {
                    unit = new CompanyUnit();
                }

                unit.setUnitName(StringUtils.trimWhitespace(unitDto.getUnitName()));
                if (!StringUtils.hasText(unit.getUnitName())) {
                    unit.setUnitName("Default Branch");
                }

                unit.setAddressLine1(unitDto.getAddressLine1());
                unit.setAddressLine2(unitDto.getAddressLine2());
                unit.setCity(unitDto.getCity());
                unit.setState(unitDto.getState());
                unit.setPinCode(unitDto.getPinCode());
                unit.setGstNo(StringUtils.trimWhitespace(unitDto.getGstNo()));
                unit.setGstType(unitDto.getGstTypeEntity());
                unit.setGstBusinessType(unitDto.getGstBusinessType());
                unit.setGstTypePrice(unitDto.getGstTypePrice());

                // Contacts – assuming lazy loading or ID reference is fine
                // If you need to load Contact entity → add ContactRepository and fetch

                unit.setUnitOpeningDate(dateTimeUtil.toLocalDate(unitDto.getUnitOpeningDate()));
                unit.setConsultantPresent(Boolean.TRUE.equals(unitDto.getConsultantPresent()));

                unit.setCompany(company);

                if (!StringUtils.hasText(unit.getStatus())) {
                    unit.setStatus("Active");
                }

                // Unit auditing
                if (unit.getId() == null && requestDto.getCreatedById() != null) {
                    User creator = userRepository.findByIdAndNotDeleted(requestDto.getCreatedById())
                            .orElseThrow(() -> new ValidationException(
                                    "Creator not found or deleted", "USER_NOT_FOUND", "createdById"));
                    unit.setCreatedBy(creator);
                }

                if (requestDto.getUpdatedById() != null) {
                    User updater = userRepository.findByIdAndNotDeleted(requestDto.getUpdatedById())
                            .orElseThrow(() -> new ValidationException(
                                    "Updater not found or deleted", "USER_NOT_FOUND", "updatedById"));
                    unit.setUpdatedBy(updater);
                }

                companyUnitRepository.save(unit);
            }
        }

        return mapToResponseDto(company);
    }

    @Override
    public CompanyResponseDto basicCreateCompany(BasicCompanyRequestDto dto) {
        logger.info("Basic company creation started for name: {}, leadCompanyId: {}",
                dto.getName(), dto.getLeadCompanyId());

        Long leadCompanyId = dto.getLeadCompanyId();
        if (leadCompanyId == null) {
            throw new ValidationException("leadCompanyId is required", "ERR_LEAD_COMPANY_ID_REQUIRED");
        }

        if (companyRepository.existsByLeadId(leadCompanyId)) {
            throw new ValidationException(
                    "Company with leadCompanyId " + leadCompanyId + " already exists",
                    "ERR_DUPLICATE_COMPANY_ID");
        }

        String name = StringUtils.trimWhitespace(dto.getName());
        if (companyRepository.existsByNameIgnoreCaseAndIsDeletedFalse(name)) {
            throw new ValidationException(
                    "Company name '" + name + "' already exists",
                    "ERR_DUPLICATE_COMPANY_NAME");
        }

        String panNo = null;
        if (StringUtils.hasText(dto.getPanNo())) {
            panNo = dto.getPanNo().trim().toUpperCase();
            if (companyRepository.existsByPanNoAndIsDeletedFalse(panNo)) {
                throw new ValidationException("PAN " + panNo + " already exists", "ERR_DUPLICATE_PAN");
            }
        }

        String gstNo = null;
        if (StringUtils.hasText(dto.getGstNo())) {
            gstNo = dto.getGstNo().trim().toUpperCase();
            if (panNo != null && gstNo.length() >= 12) {
                String panFromGst = gstNo.substring(2, 12);
                if (!panFromGst.equals(panNo)) {
                    throw new ValidationException(
                            "PAN from GST (" + panFromGst + ") does not match provided PAN",
                            "ERR_PAN_GST_MISMATCH");
                }
            }
        }

        Company company = new Company();
        company.setLeadId(leadCompanyId);
        company.setName(name);
        company.setPanNo(panNo);
        company.setUuid(dateTimeUtil.generateUuid());
        company.setIsConsultant(false);
        company.setOnboardingStatus("Minimal");
        company.setDeleted(false);

        // Optional auditing (if provided in DTO)
        if (dto.getCreatedById() != null) {
            User creator = userRepository.findByIdAndNotDeleted(dto.getCreatedById())
                    .orElseThrow(() -> new ValidationException(
                            "Creator user not found or deleted with ID: " + dto.getCreatedById(),
                            "USER_NOT_FOUND", "createdById"));
            company.setCreatedBy(creator);
        }

        company = companyRepository.save(company);
        logger.info("Basic company created → ID: {}, leadId: {}", company.getId(), company.getLeadId());

        // Auto-create default unit if needed
        boolean shouldCreateUnit = StringUtils.hasText(dto.getAddress())
                || StringUtils.hasText(dto.getUnitName())
                || StringUtils.hasText(gstNo);

        if (shouldCreateUnit) {
            CompanyUnit unit = new CompanyUnit();

            String unitName = StringUtils.hasText(dto.getUnitName())
                    ? dto.getUnitName().trim()
                    : name + " - Main Branch";

            unit.setUnitName(unitName);
            unit.setAddressLine1(dto.getAddress());
            unit.setCity(dto.getCity());
            unit.setState(dto.getState());
            unit.setPinCode(dto.getPinCode());
            unit.setGstNo(gstNo);
            unit.setStatus("Active");
            unit.setCompany(company);

            companyUnitRepository.save(unit);
            logger.info("Auto-created unit '{}' for company {}", unitName, company.getId());
        }

        return mapToResponseDto(company);
    }

    // ────────────────────────────────────────────────
    // Mapping methods (unchanged)
    // ────────────────────────────────────────────────
    private CompanyResponseDto mapToResponseDto(Company company) {
        CompanyResponseDto dto = new CompanyResponseDto();
        dto.setId(company.getId());
        dto.setName(company.getName());
        dto.setPanNo(company.getPanNo());
        dto.setOnboardingStatus(company.getOnboardingStatus());
        dto.setAccountsApproved(company.isAccountsApproved());
        dto.setAccountsRemark(company.getAccountsRemark());

        if (company.getUnits() != null) {
            dto.setUnits(company.getUnits().stream()
                    .filter(u -> !u.isDeleted())
                    .map(this::mapUnitToResponseDto)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    @Override
    @Transactional
    public CompanyResponseDto addBasicUnitToCompany(Long companyId, BasicUnitCreateRequest request, Long updatedById) {
        logger.info("Adding/Updating basic unit for company ID: {} by user {}", companyId, updatedById);

        Company company = companyRepository.findByIdAndIsDeletedFalse(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found", "ERR_COMPANY_NOT_FOUND"));

        User updatedBy = userRepository.findByUserIdAndIsDeletedFalse(updatedById);
        if (updatedBy == null) {
            throw new ResourceNotFoundException("User not found", "ERR_USER_NOT_FOUND");
        }

        CompanyUnit unit;

        Long requestedId = request.getCompanyUnitId();

        if (requestedId != null) {
            // Try to find existing active unit
            Optional<CompanyUnit> existing = companyUnitRepository.findByIdAndCompanyIdAndIsDeletedFalse(
                    requestedId, companyId);

            if (existing.isPresent()) {
                // Update existing
                unit = existing.get();
                logger.info("Updating existing unit ID: {}", requestedId);
            } else {
                // Create NEW unit → force the requested ID
                unit = new CompanyUnit();
                unit.setId(requestedId);               // ← force ID (risky!)
                unit.setCompany(company);
                unit.setStatus("Active");
                logger.info("Creating new unit with forced ID: {}", requestedId);
            }
        } else {
            // No ID provided → normal create (DB generates ID)
            unit = new CompanyUnit();
            unit.setCompany(company);
            unit.setStatus("Active");
            logger.info("Creating new unit (auto ID)");
        }

        // Unit name logic (fallback only on create)
        String unitName = StringUtils.hasText(request.getUnitName())
                ? request.getUnitName().trim()
                : (unit.getId() == null || unit.getUnitName() == null  // if forced ID but new entity
                ? company.getName() + " - Branch " + new Date().toString().substring(4, 10)
                : unit.getUnitName());

        // Duplicate name check (exclude self)
        boolean duplicateUnitName = company.getUnits().stream()
                .filter(u -> !u.isDeleted() && !u.getId().equals(unit.getId()))
                .anyMatch(u -> StringUtils.hasText(u.getUnitName()) &&
                        u.getUnitName().trim().equalsIgnoreCase(unitName));

        if (duplicateUnitName) {
            throw new ValidationException("Unit name '" + unitName + "' already exists", "ERR_DUPLICATE_UNIT_NAME");
        }

        // Apply fields
        unit.setUnitName(unitName);
        unit.setAddressLine1(request.getAddress());
        unit.setCity(request.getCity());
        unit.setState(request.getState());
        unit.setCountry(StringUtils.hasText(request.getCountry()) ? request.getCountry().trim() : "India");
        unit.setPinCode(request.getPinCode());
        unit.setGstNo(StringUtils.hasText(request.getGstNo()) ? request.getGstNo().trim().toUpperCase() : null);

        unit.setUpdatedBy(updatedBy);

        companyUnitRepository.save(unit);

        // Add to collection only if it was truly new
        if (!company.getUnits().contains(unit)) {
            company.getUnits().add(unit);
        }

        company.setUpdatedBy(updatedBy);
        companyRepository.save(company);

        logger.info("Basic unit '{}' (ID: {}) processed for company ID {} by user {}",
                unitName, unit.getId(), companyId, updatedById);

        return mapToResponseDto(company);
    }

    private CompanyUnitResponseDto mapUnitToResponseDto(CompanyUnit unit) {
        CompanyUnitResponseDto dto = new CompanyUnitResponseDto();
        dto.setId(unit.getId());
        dto.setUnitName(unit.getUnitName());
        dto.setAddressLine1(unit.getAddressLine1());
        dto.setAddressLine2(unit.getAddressLine2());
        dto.setCity(unit.getCity());
        dto.setState(unit.getState());
        dto.setCountry(unit.getCountry());
        dto.setPinCode(unit.getPinCode());
        dto.setGstNo(unit.getGstNo());
        dto.setStatus(unit.getStatus());
        dto.setUnitOpeningDate(unit.getUnitOpeningDate());
        dto.setCreatedAt(unit.getCreatedAt());
        dto.setUpdatedAt(unit.getUpdatedAt());
        return dto;
    }
}