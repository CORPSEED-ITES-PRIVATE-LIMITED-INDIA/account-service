package com.account.serviceImpl;

import com.account.domain.Company;
import com.account.domain.CompanyUnit;
import com.account.domain.OnboardingStatus;
import com.account.domain.User;
import com.account.dto.BasicCompanyRequestDto;
import com.account.dto.CompanyMigrationRequestDto;
import com.account.dto.CompanyUnitMigrationDto;
import com.account.dto.company.request.*;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
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

    // =========================================================
    // CREATE COMPANY (manual ID + timestamps)
    // =========================================================
    @Override
    public CompanyResponseDto basicCreateCompany(BasicCompanyRequestDto dto) {

        Long companyId = dto.getLeadCompanyId();
        if (companyId == null)
            throw new ValidationException("leadCompanyId is required", "ERR_LEAD_COMPANY_ID_REQUIRED");

        if (companyRepository.existsById(companyId))
            throw new ValidationException("Company already exists", "ERR_DUPLICATE_COMPANY_ID");

        String name = StringUtils.trimWhitespace(dto.getName());
        if (!StringUtils.hasText(name))
            throw new ValidationException("Company name required", "ERR_COMPANY_NAME_REQUIRED");

        if (companyRepository.existsByNameIgnoreCaseAndIsDeletedFalse(name))
            throw new ValidationException("Company name exists", "ERR_DUPLICATE_COMPANY_NAME");

        String panNo = null;
        if (StringUtils.hasText(dto.getPanNo())) {
            panNo = dto.getPanNo().trim().toUpperCase();
            if (companyRepository.existsByPanNoAndIsDeletedFalse(panNo))
                throw new ValidationException("PAN exists", "ERR_DUPLICATE_PAN");
        }

        String gstNo = null;
        if (StringUtils.hasText(dto.getGstNo())) {
            gstNo = dto.getGstNo().trim().toUpperCase();
            if (panNo != null) {
                String panFromGst = gstNo.substring(2, 12);
                if (!panFromGst.equals(panNo))
                    throw new ValidationException("PAN GST mismatch", "ERR_PAN_GST_MISMATCH");
            }
        }

        LocalDateTime now = dateTimeUtil.nowLocalDateTime();

        Company company = new Company();
        company.setId(companyId);
        company.setLeadId(companyId);
        company.setName(name);
        company.setPanNo(panNo);
        company.setUuid(dateTimeUtil.generateUuid());
        company.setIsConsultant(false);
        company.setOnboardingStatus(OnboardingStatus.MINIMAL);
        company.setDeleted(false);
        company.setCreatedAt(now);
        company.setUpdatedAt(now);

        if (dto.getCreatedById() != null) {
            User creator = userRepository.findByIdAndNotDeleted(dto.getCreatedById())
                    .orElseThrow(() -> new ValidationException("User not found", "ERR_USER_NOT_FOUND"));
            company.setCreatedBy(creator);
        }

        companyRepository.save(company);

        boolean shouldCreateUnit =
                StringUtils.hasText(dto.getUnitName())
                        || StringUtils.hasText(dto.getAddress())
                        || StringUtils.hasText(gstNo);

        if (shouldCreateUnit) {

            Long unitId = dto.getCompanyUnitId();
            if (unitId == null)
                throw new ValidationException("companyUnitId required", "ERR_UNIT_ID_REQUIRED");

            if (companyUnitRepository.findById(unitId).isPresent()) {
                logger.info("CompanyUnit {} already exists. Skipping.", unitId);
                return mapToResponseDto(company);
            }

            CompanyUnit unit = new CompanyUnit();
            unit.setId(unitId);
            unit.setCompany(company);
            unit.setUnitName(
                    StringUtils.hasText(dto.getUnitName())
                            ? dto.getUnitName().trim()
                            : name + " - Main Branch"
            );
            unit.setAddressLine1(dto.getAddress());
            unit.setCity(dto.getCity());
            unit.setState(dto.getState());
            unit.setCountry("India");
            unit.setPinCode(dto.getPinCode());
            unit.setGstNo(gstNo);
            unit.setStatus("Active");
            unit.setCreatedAt(now);
            unit.setUpdatedAt(now);

            company.getUnits().add(unit);
            companyRepository.save(company);
        }

        return mapToResponseDto(company);
    }

    // =========================================================
    // UPDATE COMPANY + UNITS (FULL)
    // =========================================================
    @Override
    public CompanyResponseDto updateFullCompanyDetails(Long companyId, CompanyRequestDto dto, Long updatedById) {

        Company company = companyRepository.findByIdAndIsDeletedFalse(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found", "ERR_COMPANY_NOT_FOUND"));

        User updatedBy = userRepository.findByIdAndNotDeleted(updatedById)
                .orElseThrow(() -> new ResourceNotFoundException("User not found", "ERR_USER_NOT_FOUND"));

        if (StringUtils.hasText(dto.getName()))
            company.setName(dto.getName().trim());

        if (StringUtils.hasText(dto.getPanNo()))
            company.setPanNo(dto.getPanNo().trim().toUpperCase());

        company.setEstablishDate(dto.getEstablishDate());
        company.setIndustry(dto.getIndustry());
        company.setSubIndustry(dto.getSubIndustry());
        company.setPaymentTerm(dto.getPaymentTerm());
        company.setAggrementPresent(Boolean.TRUE.equals(dto.getAggrementPresent()));
        company.setAggrement(dto.getAggrement());
        company.setNdaPresent(Boolean.TRUE.equals(dto.getNdaPresent()));
        company.setNda(dto.getNda());
        company.setRevenue(dto.getRevenue());
        company.setUpdatedBy(updatedBy);
        company.setUpdatedAt(dateTimeUtil.nowLocalDateTime());
        company.setOnboardingStatus(OnboardingStatus.INITIATED);

        if (dto.getUnits() != null) {
            for (CompanyUnitFullRequestDto u : dto.getUnits()) {

                CompanyUnit unit = companyUnitRepository
                        .findByIdAndCompanyIdAndIsDeletedFalse(u.getId(), companyId)
                        .orElseThrow(() -> new ValidationException("Unit not found", "ERR_UNIT_NOT_FOUND"));

                unit.setUnitName(u.getUnitName());
                unit.setAddressLine1(u.getAddressLine1());
                unit.setAddressLine2(u.getAddressLine2());
                unit.setCity(u.getCity());
                unit.setState(u.getState());
                unit.setCountry(u.getCountry());
                unit.setPinCode(u.getPinCode());
                unit.setGstNo(u.getGstNo());
                unit.setGstBusinessType(u.getGstBusinessType());
                unit.setUnitOpeningDate(u.getUnitOpeningDate());
                unit.setUpdatedBy(updatedBy);
                unit.setUpdatedAt(dateTimeUtil.nowLocalDateTime());
                unit.setOnboardingStatus(OnboardingStatus.INITIATED);

            }
        }

        companyRepository.save(company);
        return mapToResponseDto(company);
    }

    // =========================================================
    // ADD BASIC UNIT (manual ID)
    // =========================================================
    @Override
    public CompanyResponseDto addBasicUnitToCompany(Long companyId, BasicUnitCreateRequest request, Long updatedById) {

        Company company = companyRepository.findByIdAndIsDeletedFalse(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found", "ERR_COMPANY_NOT_FOUND"));

        if (companyUnitRepository.findById(request.getCompanyUnitId()).isPresent())
            return mapToResponseDto(company);

        CompanyUnit unit = new CompanyUnit();
        unit.setId(request.getCompanyUnitId());
        unit.setCompany(company);
        unit.setUnitName(request.getUnitName());
        unit.setAddressLine1(request.getAddress());
        unit.setCity(request.getCity());
        unit.setState(request.getState());
        unit.setCountry(request.getCountry());
        unit.setPinCode(request.getPinCode());
        unit.setGstNo(request.getGstNo());
        unit.setStatus("Active");
        unit.setCreatedAt(dateTimeUtil.nowLocalDateTime());
        unit.setUpdatedAt(dateTimeUtil.nowLocalDateTime());

        company.getUnits().add(unit);
        companyRepository.save(company);
        return mapToResponseDto(company);
    }



    @Override
    @Transactional(readOnly = true)
    public List<CompanyResponseDto> fetchCompanies(
            int page,
            int size,
            String onboardingStatus,
            Long userId
    ) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Company> companyPage;

        if (StringUtils.hasText(onboardingStatus) && userId != null) {

            companyPage = companyRepository.findByOnboardingStatusAndCreatedBy(
                    OnboardingStatus.valueOf(onboardingStatus.toUpperCase()),
                    userId,
                    pageable
            );

        }
        else if (StringUtils.hasText(onboardingStatus)) {

            companyPage = companyRepository.findByOnboardingStatus(
                    OnboardingStatus.valueOf(onboardingStatus.toUpperCase()),
                    pageable
            );

        }
        else if (userId != null) {

            companyPage = companyRepository.findByCreatedBy(
                    userId,
                    pageable
            );

        }
        else {

            companyPage = companyRepository.findAllActive(pageable);
        }

        return companyPage.getContent().stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CompanyResponseDto reviewCompany(Long companyId, Long reviewedById, ApproveRejectUnitRequestDto request) {
        logger.info("Reviewing company {} by user {}", companyId, reviewedById);

        Company company = companyRepository.findByIdAndIsDeletedFalse(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found", "ERR_COMPANY_NOT_FOUND"));

        User reviewedBy = userRepository.findByIdAndNotDeleted(reviewedById)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer user not found", "ERR_USER_NOT_FOUND"));

        if (request.getApprove() == null) {
            throw new ValidationException("Approve flag is required (true/false)", "ERR_APPROVE_REQUIRED");
        }

        if (!request.getApprove() && !StringUtils.hasText(request.getRemark())) {
            throw new ValidationException("Remark is required when disapproving", "ERR_REMARK_REQUIRED");
        }

        company.setAccountsApproved(request.getApprove());
        company.setAccountsReviewedBy(reviewedBy);
        company.setAccountsReviewedAt(LocalDateTime.now());
        company.setAccountsRemark(request.getApprove() ? null : request.getRemark().trim());

        // Update onboarding status based on approval
        company.setOnboardingStatus(request.getApprove() ? OnboardingStatus.APPROVED : OnboardingStatus.DISAPPROVED);

        company.setUpdatedBy(reviewedBy);
        companyRepository.save(company);

        logger.info("Company {} {} by user {}. New status: {}",
                companyId, request.getApprove() ? "approved" : "disapproved",
                reviewedById, company.getOnboardingStatus().name());

        return mapToResponseDto(company);
    }


    @Override
    @Transactional
    public CompanyResponseDto reviewUnit(Long companyId, Long unitId, Long reviewedById, ApproveRejectUnitRequestDto request) {
        logger.info("Reviewing unit {} of company {} by user {}", unitId, companyId, reviewedById);

        Company company = companyRepository.findByIdAndIsDeletedFalse(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found", "ERR_COMPANY_NOT_FOUND"));

        CompanyUnit unit = companyUnitRepository.findByIdAndCompanyIdAndIsDeletedFalse(unitId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found", "ERR_UNIT_NOT_FOUND"));

        if (unit.isAccountsApproved()) {
            throw new ValidationException("This unit is already approved - cannot review again", "ERR_UNIT_ALREADY_APPROVED");
        }

        User reviewedBy = userRepository.findByIdAndNotDeleted(reviewedById)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer user not found", "ERR_USER_NOT_FOUND"));

        if (request.getApprove() == null) {
            throw new ValidationException("Approve flag is required (true/false)", "ERR_APPROVE_REQUIRED");
        }

        if (!request.getApprove() && !StringUtils.hasText(request.getRemark())) {
            throw new ValidationException("Remark is required when rejecting/disapproving", "ERR_REMARK_REQUIRED");
        }

        // Apply review
        unit.setAccountsApproved(request.getApprove());
        unit.setAccountsReviewedBy(reviewedBy);
        unit.setAccountsReviewedAt(LocalDateTime.now());
        unit.setAccountsRemark(request.getApprove() ? null : request.getRemark().trim());
        unit.setOnboardingStatus(request.getApprove() ? OnboardingStatus.APPROVED : OnboardingStatus.DISAPPROVED);


        companyUnitRepository.save(unit);

        // Re-calculate company onboarding status
        updateCompanyOnboardingStatus(company);

        company.setUpdatedBy(reviewedBy);
        companyRepository.save(company);

        logger.info("Unit {} of company {} {} by user {}. Company onboarding status now: {}",
                unitId, companyId,
                request.getApprove() ? "APPROVED" : "DISAPPROVED",
                reviewedById, company.getOnboardingStatus().name());

        return mapToResponseDto(company);
    }

    @Override
    public CompanyResponseDto migrateCompany(CompanyMigrationRequestDto dto) {

        if (dto.getCompanyId() == null)
            throw new ValidationException("companyId required", "ERR_COMPANY_ID_REQUIRED");

        if (companyRepository.existsById(dto.getCompanyId()))
            throw new ValidationException("Company already migrated", "ERR_COMPANY_EXISTS");

        Company company = new Company();


        // ===== Identity =====
        company.setId(dto.getCompanyId());
        company.setUuid(
                StringUtils.hasText(dto.getUuid())
                        ? dto.getUuid()
                        : dateTimeUtil.generateUuid()
        );
        company.setLeadId(dto.getCompanyId());


        // ===== Core =====
        company.setName(dto.getName());
        company.setPanNo(normalizeUnique(dto.getPanNo()));
        company.setEstablishDate(dto.getEstablishDate());

        // ===== Address =====
        company.setStatus(dto.getStatus());

        // ===== Industry =====
        company.setIndustry(dto.getIndustry());
        company.setIndustries(dto.getIndustries());
        company.setSubIndustry(dto.getSubIndustry());
        company.setSubsubIndustry(dto.getSubSubIndustry());

        // ===== Consultant =====
        company.setIsConsultant(Boolean.TRUE.equals(dto.getIsConsultant()));





        // ===== Agreements =====
        company.setPaymentTerm(dto.getPaymentTerm());
        company.setAggrementPresent(dto.isAggrementPresent());
        company.setAggrement(dto.getAggrement());
        company.setNdaPresent(dto.isNdaPresent());
        company.setNda(dto.getNda());
        company.setRevenue(dto.getRevenue());

        // ===== Accounts =====
        company.setAccountsApproved(dto.isAccountsApproved());
        company.setAccountsRemark(dto.getAccountsRemark());

        company.setOnboardingStatus(
                StringUtils.hasText(dto.getOnboardingStatus())
                        ? OnboardingStatus.valueOf(dto.getOnboardingStatus())
                        : OnboardingStatus.MINIMAL
        );




        // ===== Audit =====
        if (dto.getCreatedById() != null) {
            User createdBy = userRepository.findByIdAndNotDeleted(dto.getCreatedById())
                    .orElseThrow(() -> new ValidationException("CreatedBy user not found", "ERR_USER_NOT_FOUND"));
            company.setCreatedBy(createdBy);
        }

        if (dto.getUpdatedById() != null) {
            User updatedBy = userRepository.findByIdAndNotDeleted(dto.getUpdatedById())
                    .orElseThrow(() -> new ValidationException("UpdatedBy user not found", "ERR_USER_NOT_FOUND"));
            company.setUpdatedBy(updatedBy);
        }

        company.setCreatedAt(
                dto.getCreatedAt() != null
                        ? dto.getCreatedAt()
                        : dateTimeUtil.nowLocalDateTime()
        );




        company.setUpdatedAt(
                dto.getUpdatedAt() != null
                        ? dto.getUpdatedAt()
                        : dateTimeUtil.nowLocalDateTime()
        );

        company.setDeleted(false);




        boolean hasIncomingUnits =
                dto.getUnits() != null && !dto.getUnits().isEmpty();

        if (hasIncomingUnits) {

            // ===== Existing units from Lead =====
            for (CompanyUnitMigrationDto u : dto.getUnits()) {

                if (u.getUnitId() == null)
                    continue;

                if (companyUnitRepository.existsById(u.getUnitId()))
                    continue; // idempotent

                CompanyUnit unit = new CompanyUnit();

                unit.setId(u.getUnitId());
                unit.setLeadId(u.getLeadUnitId());
                unit.setCompany(company);

                unit.setUnitName(
                        StringUtils.hasText(u.getUnitName())
                                ? u.getUnitName()
                                : company.getName() + " - Unit"
                );

                // 🔴 REQUIRED FIELDS — MUST NOT BE NULL
                unit.setAddressLine1(
                        StringUtils.hasText(u.getAddressLine1())
                                ? u.getAddressLine1()
                                : "N/A"
                );

                unit.setCity(
                        StringUtils.hasText(u.getCity())
                                ? u.getCity()
                                : "UNKNOWN"
                );

                unit.setState(
                        StringUtils.hasText(u.getState())
                                ? u.getState()
                                : "UNKNOWN"
                );

                unit.setPinCode(
                        StringUtils.hasText(u.getPinCode())
                                ? u.getPinCode()
                                : "000000"
                );

                unit.setCountry(
                        StringUtils.hasText(u.getCountry())
                                ? u.getCountry()
                                : "India"
                );

                // GST
                unit.setGstNo(u.getGstNo());
                unit.setGstType(u.getGstType());
                unit.setGstDocuments(u.getGstDocuments());
                unit.setGstTypeEntity(u.getGstTypeEntity());
                unit.setGstBusinessType(u.getGstBusinessType());
                unit.setGstTypePrice(u.getGstTypePrice());

                // Status
                unit.setStatus(
                        StringUtils.hasText(u.getStatus())
                                ? u.getStatus()
                                : "Active"
                );

                unit.setConsultantPresent(u.isConsultantPresent());
                unit.setUnitOpeningDate(u.getUnitOpeningDate());

                // Accounts
                unit.setAccountsApproved(u.isAccountsApproved());
                unit.setAccountsRemark(u.getAccountsRemark());
                unit.setOnboardingStatus(
                        StringUtils.hasText(u.getOnboardingStatus())
                                ? OnboardingStatus.valueOf(u.getOnboardingStatus())
                                : company.getOnboardingStatus()
                );

                unit.setDeleted(false);

                company.getUnits().add(unit);
            }

        } else {

            // ===============================
            // DEFAULT UNIT (Lead has none)
            // ===============================

            // Idempotency: one unit per company
            if (company.getUnits().isEmpty()) {

                CompanyUnit unit = new CompanyUnit();

                unit.setId(company.getId()); // acceptable for migration
                unit.setLeadId(company.getLeadId());
                unit.setCompany(company);

                unit.setUnitName(company.getName() + " - Main Unit");

                // 🔴 REQUIRED — derived from company or defaults
                unit.setAddressLine1(
                        StringUtils.hasText(dto.getAddress())
                                ? dto.getAddress()
                                : "N/A"
                );

                unit.setCity(
                        StringUtils.hasText(dto.getCity())
                                ? dto.getCity()
                                : "UNKNOWN"
                );

                unit.setState(
                        StringUtils.hasText(dto.getState())
                                ? dto.getState()
                                : "UNKNOWN"
                );

                unit.setPinCode(
                        StringUtils.hasText(dto.getPrimaryPinCode())
                                ? dto.getPrimaryPinCode()
                                : "000000"
                );

                unit.setCountry(
                        StringUtils.hasText(dto.getCountry())
                                ? dto.getCountry()
                                : "India"
                );

                unit.setStatus("Active");
                unit.setConsultantPresent(company.getIsConsultant());
                unit.setUnitOpeningDate(company.getEstablishDate());

                unit.setAccountsApproved(company.isAccountsApproved());
                unit.setAccountsRemark(company.getAccountsRemark());
                unit.setOnboardingStatus(company.getOnboardingStatus());

                unit.setDeleted(false);

                company.getUnits().add(unit);
            }
        }



        companyRepository.save(company);

        return mapToResponseDto(company);
    }

    private void updateCompanyOnboardingStatus(Company company) {
        if (company.getUnits().isEmpty()) {
            company.setOnboardingStatus(OnboardingStatus.MINIMAL);
            return;
        }

        boolean allApproved = company.getUnits().stream()
                .filter(u -> !u.isDeleted())
                .allMatch(CompanyUnit::isAccountsApproved);

        boolean hasRejection = company.getUnits().stream()
                .filter(u -> !u.isDeleted())
                .anyMatch(u -> !u.isAccountsApproved() && StringUtils.hasText(u.getAccountsRemark()));

        boolean hasPending = company.getUnits().stream()
                .filter(u -> !u.isDeleted())
                .anyMatch(u -> !u.isAccountsApproved() && !StringUtils.hasText(u.getAccountsRemark()));

        if (allApproved) {
            company.setOnboardingStatus(OnboardingStatus.APPROVED);
        } else if (hasRejection) {
            company.setOnboardingStatus(OnboardingStatus.DISAPPROVED);
        } else if (hasPending) {
            company.setOnboardingStatus(OnboardingStatus.INITIATED);
        } else {
            company.setOnboardingStatus(OnboardingStatus.MINIMAL);
        }
    }

    private CompanyResponseDto mapToResponseDto(Company company) {
        CompanyResponseDto dto = new CompanyResponseDto();

        dto.setId(company.getId());
        dto.setName(company.getName());
        dto.setPanNo(company.getPanNo());
        dto.setOnboardingStatus(company.getOnboardingStatus().name());

        dto.setCreateDate(
                dateTimeUtil.toDate(company.getCreatedAt())
        );
        dto.setUpdateDate(
                dateTimeUtil.toDate(company.getUpdatedAt())
        );

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
        dto.setCity(unit.getCity());
        dto.setState(unit.getState());
        dto.setCountry(unit.getCountry());
        dto.setPinCode(unit.getPinCode());
        dto.setGstNo(unit.getGstNo());
        dto.setStatus(unit.getStatus());

        // These were probably missing too
        dto.setOnboardingStatus(unit.getOnboardingStatus() != null
                ? unit.getOnboardingStatus().name()
                : null);
        dto.setAccountsApproved(unit.isAccountsApproved());
        dto.setAccountsRemark(unit.getAccountsRemark());

        dto.setUnitOpeningDate(unit.getUnitOpeningDate());
        dto.setCreatedAt(unit.getCreatedAt());
        dto.setUpdatedAt(unit.getUpdatedAt());

        return dto;
    }
    private String normalizeUnique(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }



}
