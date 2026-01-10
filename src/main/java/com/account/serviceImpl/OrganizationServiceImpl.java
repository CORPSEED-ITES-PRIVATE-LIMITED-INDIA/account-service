package com.account.serviceImpl;

import com.account.domain.Organization;
import com.account.domain.User;
import com.account.dto.OrganizationRequestDto;
import com.account.dto.OrganizationResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.repository.OrganizationRepository;
import com.account.repository.UserRepository;
import com.account.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

	private final OrganizationRepository organizationRepository;
	private final UserRepository userRepository;

	private static final Long SINGLETON_ID = 1L;

	@Override
	public OrganizationResponseDto saveOrganization(OrganizationRequestDto requestDto, Long currentUserId) {
		log.info("Saving organization configuration by userId: {}", currentUserId);

		User currentUser = userRepository.findById(currentUserId)
				.orElseThrow(() -> new ResourceNotFoundException("Current user not found", "USER_NOT_FOUND"));

		// Check if user has ADMIN role (assuming Role has name "ADMIN")
		boolean isAdmin = currentUser.getUserRole() != null &&
				currentUser.getUserRole().stream()
						.anyMatch(role -> "ADMIN".equals(role.getName()));

		if (!isAdmin) {
			throw new SecurityException("Only ADMIN role can modify organization configuration");
		}

		Organization organization = organizationRepository.findById(SINGLETON_ID)
				.orElseGet(() -> {
					Organization newOrg = new Organization();
					newOrg.setId(SINGLETON_ID);
					log.info("Creating new organization singleton with ID=1");
					return newOrg;
				});

		// Manual mapping
		organization.setName(requestDto.getName());
		organization.setAddressLine1(requestDto.getAddressLine1());
		organization.setAddressLine2(requestDto.getAddressLine2());
		organization.setCity(requestDto.getCity());
		organization.setState(requestDto.getState());
		organization.setCountry(requestDto.getCountry());
		organization.setPinCode(requestDto.getPinCode());
		organization.setGstNo(requestDto.getGstNo());
		organization.setPanNo(requestDto.getPanNo());
		organization.setCinNumber(requestDto.getCinNumber());
		organization.setEstablishedDate(requestDto.getEstablishedDate());
		organization.setOwnerName(requestDto.getOwnerName());
		organization.setBankAccountPresent(requestDto.isBankAccountPresent());
		organization.setAccountHolderName(requestDto.getAccountHolderName());
		organization.setAccountNo(requestDto.getAccountNo());
		organization.setIfscCode(requestDto.getIfscCode());
		organization.setSwiftCode(requestDto.getSwiftCode());
		organization.setBankName(requestDto.getBankName());
		organization.setBranch(requestDto.getBranch());
		organization.setUpiId(requestDto.getUpiId());
		organization.setWebsite(requestDto.getWebsite());
		organization.setPaymentPageLink(requestDto.getPaymentPageLink());
		organization.setEstimateConditions(requestDto.getEstimateConditions());
		organization.setLogoUrl(requestDto.getLogoUrl());
		organization.setEmail(requestDto.getEmail());
		organization.setPhone(requestDto.getPhone());
		organization.setActive(requestDto.isActive());

		// Set last modified by
		organization.setUpdatedBy(currentUser);

		organization = organizationRepository.save(organization);

		return mapToResponseDto(organization);
	}

	@Override
	public OrganizationResponseDto getOrganization() {
		log.info("Fetching current organization configuration");

		Organization organization = organizationRepository.findById(SINGLETON_ID)
				.orElseThrow(() -> new ResourceNotFoundException("Organization configuration not found", "ORG_NOT_FOUND"));

		return mapToResponseDto(organization);
	}

	private OrganizationResponseDto mapToResponseDto(Organization org) {
		OrganizationResponseDto dto = new OrganizationResponseDto();

		dto.setId(org.getId());
		dto.setName(org.getName());
		dto.setAddressLine1(org.getAddressLine1());
		dto.setAddressLine2(org.getAddressLine2());
		dto.setCity(org.getCity());
		dto.setState(org.getState());
		dto.setCountry(org.getCountry());
		dto.setPinCode(org.getPinCode());
		dto.setGstNo(org.getGstNo());
		dto.setPanNo(org.getPanNo());
		dto.setCinNumber(org.getCinNumber());
		dto.setEstablishedDate(org.getEstablishedDate());
		dto.setOwnerName(org.getOwnerName());
		dto.setBankAccountPresent(org.isBankAccountPresent());
		dto.setAccountHolderName(org.getAccountHolderName());
		dto.setAccountNo(org.getAccountNo());
		dto.setIfscCode(org.getIfscCode());
		dto.setSwiftCode(org.getSwiftCode());
		dto.setBankName(org.getBankName());
		dto.setBranch(org.getBranch());
		dto.setUpiId(org.getUpiId());
		dto.setWebsite(org.getWebsite());
		dto.setPaymentPageLink(org.getPaymentPageLink());
		dto.setEstimateConditions(org.getEstimateConditions());
		dto.setLogoUrl(org.getLogoUrl());
		dto.setEmail(org.getEmail());
		dto.setPhone(org.getPhone());
		dto.setActive(org.isActive());
		dto.setCreatedAt(org.getCreatedAt());
		dto.setUpdatedAt(org.getUpdatedAt());

		if (org.getCreatedBy() != null) {
			dto.setCreatedById(org.getCreatedBy().getId());
		}
		if (org.getUpdatedBy() != null) {
			dto.setUpdatedById(org.getUpdatedBy().getId());
		}

		return dto;
	}
}