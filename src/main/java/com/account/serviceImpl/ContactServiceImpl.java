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

        // Validate that the company exists
        if (!companyRepository.existsById(dto.getCompanyId())) {
            throw new ResourceNotFoundException(
                    "Company not found with id: " + dto.getCompanyId(),
                    "ERR_COMPANY_NOT_FOUND",
                    "Company",
                    dto.getCompanyId()
            );
        }

        // Prevent duplicate contact number within the same company (optional but recommended)
        if (dto.getContactNo() != null && !dto.getContactNo().isBlank()) {
            if (contactRepository.existsByContactNoAndCompanyIdAndDeleteStatusFalse(
                    dto.getContactNo(), dto.getCompanyId())) {
                throw new ValidationException(
                        "Contact number already exists for this company",
                        "ERR_DUPLICATE_CONTACT_NO"
                );
            }
        }

        Contact contact = new Contact();
        contact.setName(dto.getName());
        contact.setEmails(dto.getEmails());
        contact.setContactNo(dto.getContactNo());
        contact.setWhatsappNo(dto.getWhatsappNo());
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

        // Update allowed fields
        contact.setName(dto.getName());
        contact.setEmails(dto.getEmails());
        contact.setContactNo(dto.getContactNo());
        contact.setWhatsappNo(dto.getWhatsappNo());

        // WARNING: Changing companyId can break data integrity (e.g., estimates linked to old company)
        // Only uncomment if your business logic explicitly allows transferring contacts between companies
        // contact.setCompanyId(dto.getCompanyId());

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