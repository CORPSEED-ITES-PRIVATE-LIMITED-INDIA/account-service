package com.account.serviceImpl;

import com.account.domain.Contact;
import com.account.dto.contact.ContactRequestDto;
import com.account.dto.contact.ContactResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.CompanyRepository;
import com.account.repository.ContactRepository;
import com.account.service.ContactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ContactServiceImpl implements ContactService {

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private CompanyRepository companyRepository;

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
        contact.setCompanyId(dto.getCompanyId());
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

    private ContactResponseDto mapToResponseDto(Contact contact) {
        return new ContactResponseDto(
                contact.getId(),
                contact.getName(),
                contact.getEmails(),
                contact.getContactNo(),
                contact.getWhatsappNo(),
                contact.getCompanyId()
        );
    }
}