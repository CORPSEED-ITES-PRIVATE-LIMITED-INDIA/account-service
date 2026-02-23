package com.account.repository;

import com.account.domain.Contact;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByIdAndDeleteStatusFalse(Long id);

    List<Contact> findByCompanyIdAndDeleteStatusFalse(Long companyId);

    boolean existsByContactNoAndCompanyIdAndDeleteStatusFalse(String contactNo, Long companyId);

    // Optional: useful for create with manual ID
    boolean existsById(Long id);

    List<Contact> findByCompanyUnitIdAndDeleteStatusFalse(Long companyUnitId);


}