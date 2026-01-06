package com.account.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.domain.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
	@Query(value = "SELECT * FROM roles r WHERE r.name in(:name)", nativeQuery = true)
	List<Role> findAllByNameIn(List<String> name);
	
	@Query(value = "SELECT * FROM roles r WHERE r.name =:name limit 1", nativeQuery = true)
	Role findAllByName(String name);

}
