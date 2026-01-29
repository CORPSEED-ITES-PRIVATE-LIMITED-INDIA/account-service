package com.account.serviceImpl;

import com.account.domain.Company;
import com.account.domain.CompanyUnit;
import com.account.domain.User;
import com.account.dto.BasicCompanyRequestDto;
import com.account.dto.company.request.BasicUnitCreateRequest;
import com.account.dto.company.request.CompanyRequestDto;
import com.account.dto.company.request.CompanyUnitFullRequestDto;
import com.account.dto.company.response.CompanyResponseDto;
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
    public CompanyResponseDto basicCreateCompany(BasicCompanyRequestDto dto) {
        logger.info("Basic company creation started for name: {}, leadCompanyId: {}",
                dto.getName(), dto.getLeadCompanyId());

        Long companyId = dto.getLeadCompanyId();   // this will become PK

        if (companyId == null) {
            throw new ValidationException("leadCompanyId is required", "ERR_LEAD_COMPANY_ID_REQUIRED");
        }

        // 1. Already exists? → reject
        if (companyRepository.existsById(companyId)) {
            throw new ValidationException(
                    "Company with ID " + companyId + " already exists",
                    "ERR_DUPLICATE_COMPANY_ID");
        }

        // 2. Name uniqueness (soft-deleted excluded)
        String name = StringUtils.trimWhitespace(dto.getName());
        if (companyRepository.existsByNameIgnoreCaseAndIsDeletedFalse(name)) {
            throw new ValidationException(
                    "Company name '" + name + "' already exists",
                    "ERR_DUPLICATE_COMPANY_NAME");
        }

        // PAN checks ...
        String panNo = null;
        if (StringUtils.hasText(dto.getPanNo())) {
            panNo = dto.getPanNo().trim().toUpperCase();
            if (companyRepository.existsByPanNoAndIsDeletedFalse(panNo)) {
                throw new ValidationException("PAN " + panNo + " already exists", "ERR_DUPLICATE_PAN");
            }
        }

        // GST ↔ PAN consistency check (if both present) ...
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

        // ── Create entity ──
        Company company = new Company();
        company.setId(companyId);                    // ← important: set PK manually
        company.setLeadId(companyId);                // if you still want to keep this field
        company.setName(name);
        company.setPanNo(panNo);
        company.setUuid(dateTimeUtil.generateUuid());
        company.setIsConsultant(false);
        company.setOnboardingStatus("Minimal");
        company.setDeleted(false);

        // Optional: set creator
        if (dto.getCreatedById() != null) {
            User creator = userRepository.findByIdAndNotDeleted(dto.getCreatedById())
                    .orElseThrow(() -> new ValidationException(
                            "Creator user not found or deleted", "USER_NOT_FOUND"));
            company.setCreatedBy(creator);
        }

        company = companyRepository.save(company);
        logger.info("Basic company created → ID: {}", company.getId());

        // Auto-create default unit (your existing logic, unchanged)
        boolean shouldCreateUnit = StringUtils.hasText(dto.getAddress())
                || StringUtils.hasText(dto.getUnitName())
                || StringUtils.hasText(gstNo);

        if (shouldCreateUnit) {
            CompanyUnit unit = new CompanyUnit();
            String unitName = StringUtils.hasText(dto.getUnitName())
                    ? dto.getUnitName().trim()
                    : name + " - Main Branch";

            unit.setId(dto.getCompanyUnitId());
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
    public CompanyResponseDto updateFullCompanyDetails(Long companyId, CompanyRequestDto dto, Long updatedById) {
        logger.info("Full company update started for companyId: {}, updatedBy: {}", companyId, updatedById);

        if (companyId == null) {
            throw new ValidationException("companyId is required", "ERR_COMPANY_ID_REQUIRED");
        }

        Company company = companyRepository.findByIdAndIsDeletedFalse(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found", "ERR_COMPANY_NOT_FOUND"));

        User updatedBy = userRepository.findByIdAndNotDeleted(updatedById)
                .orElseThrow(() -> new ResourceNotFoundException("User not found", "ERR_USER_NOT_FOUND"));

        // ── Update company fields (only if provided) ──
        if (StringUtils.hasText(dto.getName())) {
            String trimmedName = dto.getName().trim();
            if (!company.getName().equalsIgnoreCase(trimmedName) &&
                    companyRepository.existsByNameIgnoreCaseAndIsDeletedFalse(trimmedName)) {
                throw new ValidationException("Company name already exists", "ERR_DUPLICATE_COMPANY_NAME");
            }
            company.setName(trimmedName);
        }

        if (StringUtils.hasText(dto.getPanNo())) {
            String pan = dto.getPanNo().trim().toUpperCase();
            if (!pan.equals(company.getPanNo()) &&
                    companyRepository.existsByPanNoAndIsDeletedFalse(pan)) {
                throw new ValidationException("PAN already exists", "ERR_DUPLICATE_PAN");
            }
            company.setPanNo(pan);
        }

        if (dto.getEstablishDate() != null) {
            company.setEstablishDate(dto.getEstablishDate());
        }

        if (StringUtils.hasText(dto.getIndustry())) company.setIndustry(dto.getIndustry().trim());
        if (StringUtils.hasText(dto.getSubIndustry())) company.setSubIndustry(dto.getSubIndustry().trim());

        if (StringUtils.hasText(dto.getPaymentTerm())) company.setPaymentTerm(dto.getPaymentTerm().trim());
        if (dto.getAggrementPresent() != null) company.setAggrementPresent(dto.getAggrementPresent());
        if (StringUtils.hasText(dto.getAggrement())) company.setAggrement(dto.getAggrement());
        if (dto.getNdaPresent() != null) company.setNdaPresent(dto.getNdaPresent());
        if (StringUtils.hasText(dto.getNda())) company.setNda(dto.getNda());
        if (StringUtils.hasText(dto.getRevenue())) company.setRevenue(dto.getRevenue().trim());

        company.setUpdatedBy(updatedBy);

        // ── Handle units ── full replace / upsert style
        if (dto.getUnits() != null && !dto.getUnits().isEmpty()) {

            // Optional: clear old units if you want full replacement
            // company.getUnits().clear();   // ← only if you want to delete old ones not sent now

            for (CompanyUnitFullRequestDto unitDto : dto.getUnits()) {
                Long unitId = unitDto.getId();

                if (unitId == null) {
                    throw new ValidationException("Unit id is required for full update flow", "ERR_UNIT_ID_REQUIRED");
                }

                CompanyUnit unit;

                Optional<CompanyUnit> existingOpt = companyUnitRepository.findByIdAndCompanyIdAndIsDeletedFalse(
                        unitId, companyId);

                if (existingOpt.isPresent()) {
                    unit = existingOpt.get();
                    logger.info("Updating existing unit id: {}", unitId);
                } else {
                    unit = new CompanyUnit();
                    unit.setId(unitId);           // manual ID assignment
                    unit.setCompany(company);
                    unit.setCreatedBy(updatedBy); // new unit
                    logger.info("Creating new unit with id: {}", unitId);
                }

                // ── Apply / update fields ──
                String unitName = unitDto.getUnitName().trim();
                // Duplicate name check (exclude self)
                boolean duplicate = company.getUnits().stream()
                        .filter(u -> !u.isDeleted() && !u.getId().equals(unitId))
                        .anyMatch(u -> u.getUnitName() != null &&
                                u.getUnitName().trim().equalsIgnoreCase(unitName));

                if (duplicate) {
                    throw new ValidationException("Unit name '" + unitName + "' already exists in this company", "ERR_DUPLICATE_UNIT_NAME");
                }

                unit.setUnitName(unitName);
                unit.setAddressLine1(unitDto.getAddressLine1().trim());
                unit.setAddressLine2(StringUtils.trimWhitespace(unitDto.getAddressLine2()));
                unit.setCity(unitDto.getCity().trim());
                unit.setState(unitDto.getState().trim());
                unit.setCountry(StringUtils.hasText(unitDto.getCountry()) ? unitDto.getCountry().trim() : "India");
                unit.setPinCode(unitDto.getPinCode().trim());

                if (StringUtils.hasText(unitDto.getGstNo())) {
                    String gst = unitDto.getGstNo().trim().toUpperCase();
                    // Optional: validate GST-PAN match if company has PAN
                    if (company.getPanNo() != null && gst.length() >= 12) {
                        String panFromGst = gst.substring(2, 12);
                        if (!panFromGst.equals(company.getPanNo())) {
                            throw new ValidationException(
                                    "GST " + gst + " PAN part does not match company PAN", "ERR_PAN_GST_MISMATCH");
                        }
                    }
                    unit.setGstNo(gst);
                } else {
                    unit.setGstNo(null);
                }

                unit.setUnitOpeningDate(unitDto.getUnitOpeningDate());
                unit.setGstBusinessType(StringUtils.trimWhitespace(unitDto.getGstBusinessType()));

                unit.setUpdatedBy(updatedBy);
                unit.setStatus("Active"); // or from DTO if you add it

                companyUnitRepository.save(unit);

                if (!company.getUnits().contains(unit)) {
                    company.getUnits().add(unit);
                }
            }
        }

        companyRepository.save(company);

        logger.info("Full company details updated → companyId: {}, onboardingStatus: {}",
                company.getId(), company.getOnboardingStatus());

        return mapToResponseDto(company);
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