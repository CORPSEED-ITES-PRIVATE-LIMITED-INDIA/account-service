package com.account.dashboard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.dashboard.domain.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
	@Query(value = "SELECT * FROM roles r WHERE r.name in(:name)", nativeQuery = true)
	List<Role> findAllByNameIn(List<String> name);

}
