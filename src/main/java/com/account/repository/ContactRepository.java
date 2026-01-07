package com.account.repository;

import com.account.domain.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    // Find active (non-deleted) contact by ID
    Optional<Contact> findByIdAndDeleteStatusFalse(Long id);

    // Find all active contacts for a specific company
    List<Contact> findByCompanyIdAndDeleteStatusFalse(Long companyId);

    // Paginated active contacts for a company
    Page<Contact> findByCompanyIdAndDeleteStatusFalse(Long companyId, Pageable pageable);

    // Find all active contacts (system-wide, paginated)
    Page<Contact> findByDeleteStatusFalse(Pageable pageable);

    // Search contacts by name, email, or phone (case-insensitive)
    @Query("SELECT c FROM Contact c WHERE c.deleteStatus = false AND " +
            "(LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(c.emails) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(c.contactNo) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(c.whatsappNo) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Contact> searchByQuery(@Param("query") String query, Pageable pageable);

    // Check for duplicate contact number within the same company
    boolean existsByContactNoAndCompanyIdAndDeleteStatusFalse(String contactNo, Long companyId);

    // Optional: Check duplicate email within company (if needed)
    boolean existsByEmailsAndCompanyIdAndDeleteStatusFalse(String emails, Long companyId);

    // Existing ones you already have
    boolean existsByContactNoAndDeleteStatusFalse(String contactNo);
    boolean existsByEmailsAndDeleteStatusFalse(String emails);

    // Find primary contacts often used in estimates/units
    List<Contact> findByCompanyIdAndNameContainingIgnoreCaseAndDeleteStatusFalse(Long companyId, String namePart);




}