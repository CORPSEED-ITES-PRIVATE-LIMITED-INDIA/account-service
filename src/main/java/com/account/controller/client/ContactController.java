package com.account.controller.client;

import com.account.dto.contact.ContactRequestDto;
import com.account.dto.contact.ContactResponseDto;
import com.account.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accountService/api/v1")
public class ContactController {

    @Autowired
    private ContactService contactService;

    @PostMapping("/contacts")
    public ResponseEntity<ContactResponseDto> createContact(@Valid @RequestBody ContactRequestDto dto) {
        ContactResponseDto response = contactService.createContact(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContactResponseDto> getContactById(@PathVariable Long id) {
        ContactResponseDto response = contactService.getContactById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<List<ContactResponseDto>> getContactsByCompanyId(@PathVariable Long companyId) {
        List<ContactResponseDto> contacts = contactService.getContactsByCompanyId(companyId);
        return ResponseEntity.ok(contacts);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContactResponseDto> updateContact(
            @PathVariable Long id,
            @Valid @RequestBody ContactRequestDto dto) {
        ContactResponseDto response = contactService.updateContact(id, dto);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable Long id) {
        contactService.softDeleteContact(id);
        return ResponseEntity.noContent().build();
    }
}