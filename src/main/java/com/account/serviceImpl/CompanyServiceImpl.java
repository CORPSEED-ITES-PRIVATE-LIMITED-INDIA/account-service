package com.account.serviceImpl;

import com.account.domain.Company;
import com.account.domain.CompanyUnit;
import com.account.domain.User;
import com.account.dto.BasicCompanyRequestDto;
import com.account.exception.ValidationException;
import com.account.repository.CompanyRepository;
import com.account.repository.CompanyUnitRepository;
import com.account.service.company.CompanyService;
import com.account.dto.company.CompanyRequestDto;
import com.account.dto.company.CompanyResponseDto;
import com.account.dto.company.CompanyUnitRequestDto;
import com.account.dto.company.CompanyUnitResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyUnitRepository companyUnitRepository;

    // ────────────────────────────────────────────────
    //  Method 1: Sync from Lead (uses external ID)
    // ────────────────────────────────────────────────
    @Override
    public CompanyResponseDto createCompanyFromLead(CompanyRequestDto requestDto) {

        Long leadCompanyId = requestDto.getLeadCompanyId();
        if (leadCompanyId == null) {
            throw new ValidationException("leadCompanyId is required to sync with same ID", "ERR_LEAD_ID_REQUIRED");
        }

        Optional<Company> existing = companyRepository.findById(leadCompanyId);

        Company company;
        boolean isNew = false;

        if (existing.isPresent()) {
            company = existing.get();
        } else {
            company = new Company();
            company.setId(leadCompanyId);           // Preserve lead ID
            isNew = true;
        }

        // Update only if value is provided (partial update friendly)
        company.setName(StringUtils.hasText(requestDto.getName()) ? requestDto.getName().trim() : company.getName());
        company.setPanNo(StringUtils.hasText(requestDto.getPanNo()) ? requestDto.getPanNo().trim().toUpperCase() : company.getPanNo());

        company.setAddress(requestDto.getAddress());
        company.setCity(requestDto.getCity());
        company.setState(requestDto.getState());
        company.setCountry(StringUtils.hasText(requestDto.getCountry()) ? requestDto.getCountry() : "India");
        company.setPrimaryPinCode(requestDto.getPrimaryPinCode());

        company.setSAddress(requestDto.getSAddress());
        company.setSCity(requestDto.getSCity());
        company.setSState(requestDto.getSState());
        company.setSCountry(requestDto.getSCountry());
        company.setSecondaryPinCode(requestDto.getSecondaryPinCode());

        company.setEstablishDate(requestDto.getEstablishDate());
        company.setIndustry(requestDto.getIndustry());

        company.setPaymentTerm(requestDto.getPaymentTerm());
        company.setAggrementPresent(requestDto.getAggrementPresent() != null && requestDto.getAggrementPresent());
        company.setAggrement(requestDto.getAggrement());
        company.setNda(requestDto.getNda());
        company.setNdaPresent(requestDto.getNdaPresent() != null && requestDto.getNdaPresent());
        company.setRevenue(requestDto.getRevenue());

        if (isNew) {
            company.setOnboardingStatus("PendingApproval");
            company.setAccountsApproved(false);
            company.setCreateDate(new Date());
        }
        company.setUpdateDate(new Date());

        company = companyRepository.save(company);

        // ── Sync Units ────────────────────────────────
        if (requestDto.getUnits() != null && !requestDto.getUnits().isEmpty()) {
            for (CompanyUnitRequestDto unitDto : requestDto.getUnits()) {
                CompanyUnit unit;

                if (unitDto.getLeadUnitId() != null) {
                    Optional<CompanyUnit> optUnit = companyUnitRepository.findById(unitDto.getLeadUnitId());
                    unit = optUnit.orElseGet(() -> {
                        CompanyUnit newUnit = new CompanyUnit();
                        newUnit.setId(unitDto.getLeadUnitId()); // preserve lead unit ID
                        return newUnit;
                    });
                } else {
                    unit = new CompanyUnit();
                }

                unit.setUnitName(StringUtils.hasText(unitDto.getUnitName())
                        ? unitDto.getUnitName().trim()
                        : "Default Branch");

                unit.setAddressLine1(unitDto.getAddressLine1());
                unit.setAddressLine2(unitDto.getAddressLine2());
                unit.setCity(unitDto.getCity());
                unit.setState(unitDto.getState());
                unit.setCountry(StringUtils.hasText(unitDto.getCountry()) ? unitDto.getCountry() : "India");
                unit.setPinCode(unitDto.getPinCode());
                unit.setGstNo(StringUtils.hasText(unitDto.getGstNo()) ? unitDto.getGstNo().trim().toUpperCase() : null);

                unit.setUnitOpeningDate(unitDto.getUnitOpeningDate());
                unit.setStatus("Active");
                unit.setConsultantPresent(unitDto.getConsultantPresent() != null && unitDto.getConsultantPresent());
                unit.setCompany(company);

                companyUnitRepository.save(unit);
            }
        }

        return mapToResponseDto(company);
    }

    // ────────────────────────────────────────────────
    //  Method 2: Basic / Minimal company creation
    // ────────────────────────────────────────────────
    @Override
    public CompanyResponseDto basicCreateCompany(BasicCompanyRequestDto dto) {
        logger.info("Basic company creation started for name: {}, leadCompanyId: {}",
                dto.getName(), dto.getLeadCompanyId());

        Long leadCompanyId = dto.getLeadCompanyId();
        if (leadCompanyId == null) {
            throw new ValidationException("leadCompanyId is required", "ERR_LEAD_COMPANY_ID_REQUIRED");
        }

        // Prevent overwriting existing record
        if (companyRepository.existsById(leadCompanyId)) {
            throw new ValidationException(
                    "Company with leadCompanyId " + leadCompanyId + " already exists",
                    "ERR_DUPLICATE_COMPANY_ID"
            );
        }

        String name = dto.getName().trim();

        // Name uniqueness (case-insensitive)
        if (companyRepository.existsByNameIgnoreCaseAndIsDeletedFalse(name)) {
            throw new ValidationException(
                    "A company with the name '" + name + "' already exists",
                    "ERR_DUPLICATE_COMPANY_NAME"
            );
        }

        String panNo = null;
        if (StringUtils.hasText(dto.getPanNo())) {
            panNo = dto.getPanNo().trim().toUpperCase();

            if (companyRepository.existsByPanNoAndIsDeletedFalse(panNo)) {
                throw new ValidationException(
                        "Company with PAN " + panNo + " already exists",
                        "ERR_DUPLICATE_PAN"
                );
            }
        }

        String gstNo = null;
        if (StringUtils.hasText(dto.getGstNo())) {
            gstNo = dto.getGstNo().trim().toUpperCase();

            if (panNo != null && gstNo.length() >= 12) {
                String panFromGst = gstNo.substring(2, 12);
                if (!panFromGst.equals(panNo)) {
                    throw new ValidationException(
                            "PAN extracted from GST (" + panFromGst + ") does not match provided PAN (" + panNo + ")",
                            "ERR_PAN_GST_MISMATCH"
                    );
                }
            }
        }

        // ── Create company with externally provided ID ────────────────────────
        Company company = new Company();
        company.setId(leadCompanyId);                   // ← key line
//        company.setUuid(generateUuid());                // ← replace with real UUID generation
        company.setName(name);
        company.setPanNo(panNo);
        company.setAddress(dto.getAddress());
        company.setCity(dto.getCity());
        company.setState(dto.getState());
        company.setCountry(StringUtils.hasText(dto.getCountry()) ? dto.getCountry() : "India");
        company.setPrimaryPinCode(dto.getPinCode());

        company.setIsConsultant(false);
        company.setOnboardingStatus("Minimal");
        company.setCreateDate(new Date());
        company.setUpdateDate(new Date());
        company.setDeleted(false);

        // Optional: link creator if provided
        // if (dto.getCreatedById() != null) {
        //     company.setCreatedBy(userRepository.getReferenceById(dto.getCreatedById()));
        // }

        company = companyRepository.save(company);
        logger.info("Basic company created → ID: {}", company.getId());

        // ── Auto-create default unit when it makes sense ──────────────────────
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

            company.getUnits().add(unit);
            companyUnitRepository.save(unit);

            logger.info("Auto-created unit '{}' for company {}", unitName, company.getId());
        }

        return mapToResponseDto(company);
    }
    // ────────────────────────────────────────────────
    //          Mapping methods (unchanged)
    // ────────────────────────────────────────────────
    private CompanyResponseDto mapToResponseDto(Company company) {
        CompanyResponseDto dto = new CompanyResponseDto();

        dto.setId(company.getId());
        dto.setName(company.getName());
        dto.setPanNo(company.getPanNo());
        dto.setAddress(company.getAddress());
        dto.setCity(company.getCity());
        dto.setState(company.getState());
        dto.setCountry(company.getCountry());
        dto.setPrimaryPinCode(company.getPrimaryPinCode());

        dto.setSAddress(company.getSAddress());
        dto.setSCity(company.getSCity());
        dto.setSState(company.getSState());
        dto.setSCountry(company.getSCountry());
        dto.setSecondaryPinCode(company.getSecondaryPinCode());

        dto.setOnboardingStatus(company.getOnboardingStatus());
        dto.setAccountsApproved(company.isAccountsApproved());
        dto.setAccountsRemark(company.getAccountsRemark());
        dto.setCreateDate(company.getCreateDate());
        dto.setUpdateDate(company.getUpdateDate());

        if (company.getUnits() != null) {
            dto.setUnits(company.getUnits().stream()
                    .filter(u -> !u.isDeleted())
                    .map(this::mapUnitToResponseDto)
                    .collect(Collectors.toList()));
        }

        return dto;
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