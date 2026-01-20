package com.account.serviceImpl;

import com.account.domain.Company;
import com.account.domain.CompanyUnit;
import com.account.domain.Contact;
import com.account.domain.User;
import com.account.dto.BasicCompanyRequestDto;
import com.account.dto.company.CompanyRequestDto;
import com.account.dto.company.CompanyResponseDto;
import com.account.dto.company.CompanyUnitRequestDto;
import com.account.dto.company.CompanyUnitResponseDto;
import com.account.exception.ValidationException;
import com.account.repository.CompanyRepository;
import com.account.repository.CompanyUnitRepository;
import com.account.service.company.CompanyService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class CompanyServiceImpl implements CompanyService {

    private static final Logger logger = LoggerFactory.getLogger(CompanyServiceImpl.class);

    private final CompanyRepository companyRepository;
    private final CompanyUnitRepository companyUnitRepository;

    @PersistenceContext
    private EntityManager em;

    public CompanyServiceImpl(CompanyRepository companyRepository,
                              CompanyUnitRepository companyUnitRepository) {
        this.companyRepository = companyRepository;
        this.companyUnitRepository = companyUnitRepository;
    }

    // ────────────────────────────────────────────────
    //  Method 1: Sync from Lead (uses leadCompanyId / leadUnitId)
    // ────────────────────────────────────────────────
    @Override
    public CompanyResponseDto createCompanyFromLead(CompanyRequestDto requestDto) {

        Long leadCompanyId = requestDto.getLeadCompanyId();
        if (leadCompanyId == null) {
            throw new ValidationException("leadCompanyId is required", "ERR_LEAD_ID_REQUIRED");
        }

        // IMPORTANT:
        // Company.id is @GeneratedValue → NEVER use findById(leadCompanyId) or setId(leadCompanyId)
        Company company = companyRepository.findByLeadId(leadCompanyId)
                .orElseGet(Company::new);

        boolean isNew = (company.getId() == null);

        // ── Mapping: ALL CompanyRequestDto fields ─────────────────────────────

        company.setLeadId(leadCompanyId);

        // mandatory
        company.setName(StringUtils.hasText(requestDto.getName()) ? requestDto.getName().trim() : company.getName());
        company.setPanNo(StringUtils.hasText(requestDto.getPanNo()) ? requestDto.getPanNo().trim().toUpperCase() : company.getPanNo());


        company.setEstablishDate(toLocalDate(requestDto.getEstablishDate()));
        company.setIndustry(requestDto.getIndustry());


        // sales & assignment
        company.setStatus(requestDto.getStatus());
        company.setLeadId(requestDto.getLeadId());

        // agreements
        company.setPaymentTerm(requestDto.getPaymentTerm());
        company.setAggrementPresent(Boolean.TRUE.equals(requestDto.getAggrementPresent()));
        company.setAggrement(requestDto.getAggrement());
        company.setNda(requestDto.getNda());
        company.setNdaPresent(Boolean.TRUE.equals(requestDto.getNdaPresent()));
        company.setRevenue(requestDto.getRevenue());

        // consultant flow
        company.setIsConsultant(Boolean.TRUE.equals(requestDto.getIsConsultant()));
//        company.setActualClientCompanyId(requestDto.getActualClientCompanyId());

        // audit (optional)
        if (requestDto.getCreatedById() != null && isNew) {
            company.setCreatedBy(em.getReference(User.class, requestDto.getCreatedById()));
        }
        if (requestDto.getUpdatedById() != null) {
            company.setUpdatedBy(em.getReference(User.class, requestDto.getUpdatedById()));
        }

        // set defaults for new company
        if (isNew) {
            company.setOnboardingStatus("PendingApproval");
            company.setAccountsApproved(false);
            company.setDeleted(false);
        }

        company = companyRepository.save(company);

        // ── Sync Units ────────────────────────────────
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

                // map ALL unit fields
                unit.setUnitName(StringUtils.hasText(unitDto.getUnitName()) ? unitDto.getUnitName().trim() : "Default Branch");

                unit.setAddressLine1(unitDto.getAddressLine1());
                unit.setAddressLine2(unitDto.getAddressLine2());
                unit.setCity(unitDto.getCity());
                unit.setState(unitDto.getState());
                unit.setCountry(StringUtils.hasText(unitDto.getCountry()) ? unitDto.getCountry().trim() : "India");
                unit.setPinCode(unitDto.getPinCode());

                unit.setGstNo(StringUtils.hasText(unitDto.getGstNo()) ? unitDto.getGstNo().trim().toUpperCase() : null);

                // “ID-based” GST fields (from request)
                unit.setGstType(unitDto.getGstTypeEntity());
                unit.setGstBusinessType(unitDto.getGstBusinessType());
                unit.setGstTypePrice(unitDto.getGstTypePrice());


                // contacts (by id)
                if (unitDto.getPrimaryContactId() != null) {
                    unit.setPrimaryContact(em.getReference(Contact.class, unitDto.getPrimaryContactId()));
                } else {
                    unit.setPrimaryContact(null);
                }
                if (unitDto.getSecondaryContactId() != null) {
                    unit.setSecondaryContact(em.getReference(Contact.class, unitDto.getSecondaryContactId()));
                } else {
                    unit.setSecondaryContact(null);
                }

                unit.setUnitOpeningDate(toLocalDate(unitDto.getUnitOpeningDate()));
                unit.setConsultantPresent(Boolean.TRUE.equals(unitDto.getConsultantPresent()));

                // link
                unit.setCompany(company);

                // default status (you can change to “unitDto.getStatus()” if you add it to DTO)
                if (!StringUtils.hasText(unit.getStatus())) {
                    unit.setStatus("Active");
                }

                // audit (optional)
                if (requestDto.getCreatedById() != null && unit.getId() == null) {
                    unit.setCreatedBy(em.getReference(User.class, requestDto.getCreatedById()));
                }
                if (requestDto.getUpdatedById() != null) {
                    unit.setUpdatedBy(em.getReference(User.class, requestDto.getUpdatedById()));
                }

                companyUnitRepository.save(unit);
            }
        } else {
            // If units empty but legacy address exists → optional auto-create a default unit
            boolean shouldCreateDefaultUnit =
                    StringUtils.hasText(requestDto.getAddress()) ||
                            StringUtils.hasText(requestDto.getCity()) ||
                            StringUtils.hasText(requestDto.getState());

            if (shouldCreateDefaultUnit) {
                CompanyUnit unit = new CompanyUnit();
                unit.setUnitName(company.getName() + " - Main Branch");
                unit.setAddressLine1(requestDto.getAddress());
                unit.setCity(requestDto.getCity());
                unit.setState(requestDto.getState());
                unit.setCountry(StringUtils.hasText(requestDto.getCountry()) ? requestDto.getCountry().trim() : "India");
                unit.setPinCode(requestDto.getPrimaryPinCode());
                unit.setStatus("Active");
                unit.setCompany(company);
                companyUnitRepository.save(unit);
            }
        }

        // reload company with units if needed (lazy)
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

        // IMPORTANT: check existence by leadCompanyId, not by primary key id
        if (companyRepository.existsByLeadId(leadCompanyId)) {
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

        Company company = new Company();
        company.setLeadId(leadCompanyId);
        company.setName(name);
        company.setPanNo(panNo);

        company.setIsConsultant(false);
        company.setOnboardingStatus("Minimal");

        company.setDeleted(false);

        company = companyRepository.save(company);
        logger.info("Basic company created → ID: {}, leadCompanyId: {}", company.getId(), company.getLeadId());

        // Auto-create default unit when it makes sense
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
            unit.setCountry(StringUtils.hasText(dto.getCountry()) ? dto.getCountry().trim() : "India");
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
    // Mapping methods
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

    private static LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
