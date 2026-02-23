package com.account.serviceImpl;

import com.account.domain.Contact;
import com.account.domain.contact.ContactCreationDto;
import com.account.dto.contact.ContactRequestDto;
import com.account.dto.contact.ContactResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.CompanyRepository;
import com.account.repository.CompanyUnitRepository;
import com.account.repository.ContactRepository;
import com.account.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ContactServiceImpl implements ContactService {

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyUnitRepository companyUnitRepository;

    @Override
    public ContactResponseDto createContact(ContactRequestDto dto) {

        // 1. Validate company exists
        if (!companyRepository.existsById(dto.getCompanyId())) {
            throw new ResourceNotFoundException(
                    "Company not found with id: " + dto.getCompanyId(),
                    "ERR_COMPANY_NOT_FOUND",
                    "Company",
                    dto.getCompanyId()
            );
        }

        // 2. Check if provided ID already exists
        if (contactRepository.existsById(dto.getId())) {
            throw new ValidationException(
                    "Contact with ID " + dto.getId() + " already exists",
                    "ERR_CONTACT_ID_ALREADY_EXISTS"
            );
        }

        // 3. Prevent duplicate contact number within the same company
        if (dto.getContactNo() != null && !dto.getContactNo().trim().isEmpty()) {
            String normalizedContactNo = dto.getContactNo().trim();
            if (contactRepository.existsByContactNoAndCompanyIdAndDeleteStatusFalse(
                    normalizedContactNo, dto.getCompanyId())) {
                throw new ValidationException(
                        "Contact number already exists for this company",
                        "ERR_DUPLICATE_CONTACT_NO"
                );
            }
        }

        // 4. Create entity
        Contact contact = new Contact();
        contact.setId(dto.getId());                    // ← client-provided ID
        contact.setName(dto.getName().trim());
        contact.setEmails(dto.getEmails() != null ? dto.getEmails().trim() : null);
        contact.setContactNo(dto.getContactNo() != null ? dto.getContactNo().trim() : null);
        contact.setWhatsappNo(dto.getWhatsappNo() != null ? dto.getWhatsappNo().trim() : null);
        contact.setDeleteStatus(false);

        Contact saved = contactRepository.save(contact);
        return mapToResponseDto(saved);
    }

    @Override
    public ContactResponseDto getContactById(Long id) {
        return contactRepository.findByIdAndDeleteStatusFalse(id)
                .map(this::mapToResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active contact not found with id: " + id,
                        "ERR_CONTACT_NOT_FOUND",
                        "Contact",
                        id
                ));
    }

    @Override
    public List<ContactResponseDto> getContactsByCompanyId(Long companyId) {
        return contactRepository.findByCompanyIdAndDeleteStatusFalse(companyId)
                .stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public ContactResponseDto updateContact(Long id, ContactRequestDto dto) {
        Contact contact = contactRepository.findByIdAndDeleteStatusFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active contact not found with id: " + id,
                        "ERR_CONTACT_NOT_FOUND",
                        "Contact",
                        id
                ));

        // Update allowed fields (ID & companyId usually not changeable)
        contact.setName(dto.getName() != null ? dto.getName().trim() : contact.getName());
        contact.setEmails(dto.getEmails() != null ? dto.getEmails().trim() : contact.getEmails());
        contact.setContactNo(dto.getContactNo() != null ? dto.getContactNo().trim() : contact.getContactNo());
        contact.setWhatsappNo(dto.getWhatsappNo() != null ? dto.getWhatsappNo().trim() : contact.getWhatsappNo());

        // companyId change is dangerous – usually forbidden
        // contact.setCompanyId(dto.getCompanyId());  // ← only if business allows

        Contact updated = contactRepository.save(contact);
        return mapToResponseDto(updated);
    }

    @Override
    public void softDeleteContact(Long id) {
        Contact contact = contactRepository.findByIdAndDeleteStatusFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active contact not found with id: " + id,
                        "ERR_CONTACT_NOT_FOUND",
                        "Contact",
                        id
                ));

        contact.setDeleteStatus(true);
        contactRepository.save(contact);
    }

    @Override
    public List<ContactResponseDto> getContactsByCompanyUnitId(Long companyUnitId) {

        // Optional validation
        if (!companyUnitRepository.existsById(companyUnitId)) {
            throw new ResourceNotFoundException(
                    "Company unit not found with id: " + companyUnitId,
                    "ERR_UNIT_NOT_FOUND",
                    "CompanyUnit",
                    companyUnitId
            );
        }

        return contactRepository
                .findByCompanyUnitIdAndDeleteStatusFalse(companyUnitId)
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Override
    public ContactResponseDto createAssociatedContact(ContactCreationDto dto) {

        if (dto.getId() == null) {
            throw new ValidationException(
                    "Contact ID must be provided",
                    "ERR_ID_REQUIRED"
            );
        }

        if (contactRepository.existsById(dto.getId())) {
            throw new ValidationException(
                    "Contact with ID " + dto.getId() + " already exists",
                    "ERR_CONTACT_ALREADY_EXISTS"
            );
        }

        if (dto.getCompanyId() == null && dto.getCompanyUnitId() == null) {
            throw new ValidationException(
                    "Either companyId or companyUnitId must be provided",
                    "ERR_ASSOCIATION_REQUIRED"
            );
        }

        Contact contact = new Contact();
        contact.setId(dto.getId());  // ← manual ID set here

        contact.setName(dto.getName().trim());
        contact.setTitle(dto.getTitle());
        contact.setEmails(dto.getEmails());
        contact.setContactNo(dto.getContactNo());
        contact.setWhatsappNo(dto.getWhatsappNo());
        contact.setDesignation(dto.getDesignation());

        contact.setPrimaryForCompany(dto.isMakePrimaryForCompany());
        contact.setSecondaryForCompany(dto.isMakeSecondaryForCompany());
        contact.setPrimaryForUnit(dto.isMakePrimaryForUnit());
        contact.setSecondaryForUnit(dto.isMakeSecondaryForUnit());

        contact.setDeleteStatus(false);

        if (dto.getCompanyId() != null) {
            var company = companyRepository.findById(dto.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Company not found",
                            "ERR_COMPANY_NOT_FOUND",
                            "Company",
                            dto.getCompanyId()
                    ));
            contact.setCompany(company);
        }

        if (dto.getCompanyUnitId() != null) {
            var unit = companyUnitRepository.findById(dto.getCompanyUnitId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Company unit not found",
                            "ERR_UNIT_NOT_FOUND",
                            "CompanyUnit",
                            dto.getCompanyUnitId()
                    ));
            contact.setCompanyUnit(unit);
        }

        Contact saved = contactRepository.save(contact);

        return mapToResponseDto(saved);
    }
    private ContactResponseDto mapToResponseDto(Contact contact) {

        ContactResponseDto dto = new ContactResponseDto();

        dto.setId(contact.getId());
        dto.setTitle(contact.getTitle());
        dto.setName(contact.getName());
        dto.setEmails(contact.getEmails());
        dto.setContactNo(contact.getContactNo());
        dto.setWhatsappNo(contact.getWhatsappNo());
        dto.setDesignation(contact.getDesignation());

        dto.setCompanyId(
                contact.getCompany() != null ? contact.getCompany().getId() : null
        );

        dto.setCompanyUnitId(
                contact.getCompanyUnit() != null ? contact.getCompanyUnit().getId() : null
        );

        dto.setPrimaryForCompany(contact.isPrimaryForCompany());
        dto.setSecondaryForCompany(contact.isSecondaryForCompany());
        dto.setPrimaryForUnit(contact.isPrimaryForUnit());
        dto.setSecondaryForUnit(contact.isSecondaryForUnit());

        dto.setDeleted(contact.isDeleted());

        return dto;
    }


}