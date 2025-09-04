package com.account.dashboard.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.domain.Role;
import com.account.dashboard.repository.RoleRepository;
import com.account.dashboard.util.UrlsMapping;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
public class RoleController {
	
	@Autowired
	RoleRepository roleRepository;
	
	@GetMapping(UrlsMapping.CREATE_ROLE)
	public 	Role createRole(@RequestParam String name) {
		Role role = new Role();
		role.setName(name);
		role.setDeleted(false);
		Role r = roleRepository.save(role);
		return r;
	}
	@GetMapping(UrlsMapping.GET_ALL_ROLE)
	public List<Role>getAllRole(){
		List<Role> roles = roleRepository.findAll().stream().filter(i->!(i.isDeleted())).collect(Collectors.toList());
		return roles;
	}
	
	
	@PostMapping(UrlsMapping.CREATE_ALL_ROLE_BY_LEAD)
	public Boolean createAllRoleByLead(@RequestBody List<Role>roles){
		Boolean flag=false;
		for(Role r :roles) {
			Role role = new Role();
			role.setId(r.getId());;
			role .setName(r.getName());
			role.setDeleted(false);
			roleRepository.save(role);
			flag=true;
		}
		return flag;
	}

}
