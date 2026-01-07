package com.account.service;

import com.account.dto.contact.ContactRequestDto;
import com.account.dto.contact.ContactResponseDto;

import java.util.List;

public interface ContactService {

    ContactResponseDto createContact(ContactRequestDto dto);

    ContactResponseDto getContactById(Long id);

    List<ContactResponseDto> getContactsByCompanyId(Long companyId);

    ContactResponseDto updateContact(Long id, ContactRequestDto dto);

    void softDeleteContact(Long id);
}