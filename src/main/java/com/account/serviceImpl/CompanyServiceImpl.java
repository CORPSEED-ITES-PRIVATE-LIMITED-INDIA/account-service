package com.account.serviceImpl;

import com.account.domain.Company;
import com.account.domain.CompanyUnit;
import com.account.exception.ValidationException;
import com.account.repository.CompanyRepository;
import com.account.repository.CompanyUnitRepository;
import com.account.service.company.CompanyService;
import com.account.dto.company.CompanyRequestDto;
import com.account.dto.company.CompanyResponseDto;
import com.account.dto.company.CompanyUnitRequestDto;
import com.account.dto.company.CompanyUnitResponseDto;
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

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyUnitRepository companyUnitRepository;

    @Override
    public CompanyResponseDto createCompanyFromLead(CompanyRequestDto requestDto) {

        Long leadCompanyId = requestDto.getLeadCompanyId();
        if (leadCompanyId == null) {
            throw new ValidationException("leadCompanyId is required to sync with same ID", "ERR_LEAD_ID_REQUIRED");
        }

        Optional<Company> existingCompany = companyRepository.findById(leadCompanyId);

        Company company;
        boolean isNew = false;

        if (existingCompany.isPresent()) {
            company = existingCompany.get();
        } else {
            company = new Company();
            company.setId(leadCompanyId); // Set same ID from lead-service
            isNew = true;
        }

        // Map fields
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

        // === Sync Units ===
        if (requestDto.getUnits() != null && !requestDto.getUnits().isEmpty()) {

            for (CompanyUnitRequestDto unitDto : requestDto.getUnits()) {

                CompanyUnit unit;

                if (unitDto.getLeadUnitId() != null) {
                    Optional<CompanyUnit> existingUnit = companyUnitRepository.findById(unitDto.getLeadUnitId());
                    unit = existingUnit.orElse(new CompanyUnit());
                    if (existingUnit.isEmpty()) {
                        unit.setId(unitDto.getLeadUnitId()); // Preserve same ID
                    }
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

        // Map units
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