package com.account.serviceImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.account.dto.UpdateUser;
import com.account.dto.UserDto;
import com.account.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

import com.account.config.LeadFeignClient;
import com.account.domain.Role;
import com.account.domain.User;
import com.account.repository.RoleRepository;
import com.account.repository.UserRepository;
import com.account.service.UserService;

@Service
public class UserServiceImpl implements UserService {
	@Autowired
	UserRepository userRepository;

	@Autowired
	MailSendSerivceImpl mailSendSerivceImpl;

	@Autowired
	RoleRepository roleRepository;

	@Autowired
	LeadFeignClient leadFeignClient;

	@Override
	public User createUser(UserDto user) {
		User u =new User();
		u.setId(user.getId());
		u.setFullName(user.getUsername());
		u.setEmail(user.getEmail());
		u.setDesignation(user.getDesignation());
		u.setDepartment(user.getDepartment());
		List<Role> roleList = roleRepository.findAllByNameIn(user.getRole());
		u.setUserRole(roleList);
		u.setRole(user.getRole());
		return userRepository.save(u);
	}

	@Override
	public User getUserById(Long id) {
		return userRepository.findById(id).orElse(null);
	}

	@Override
	public User updateUser(User existingUser, UpdateUser user) {

		existingUser.setEmail(user.getEmail());
		existingUser.setDesignation(user.getDesignation());
		existingUser.setDepartment(user.getDepartment());
		List<Role> roleList = roleRepository.findAllByNameIn(user.getRole());
		existingUser.setUserRole(roleList);
		existingUser.setRole(user.getRole());
		return userRepository.save(existingUser);
	}

	@Override
	public void deleteUser(Long id) {
		userRepository.deleteById(id);
	}

	@Override
	public List<User> getAllUsers() {
		return userRepository.findAll();
	}

	@Override
	public boolean isUserExistOrNot(Long userId) throws Exception {
		boolean flag=false;
		try {
			Optional<User> user = userRepository.findById(userId);
			if(user!=null && user.get()!=null) {
				flag=true;
			}
		}catch(Exception e) {
			throw new Exception("User Does not exist");
		}
		return flag;
	}
	public StringBuilder getRandomNumber() {
		String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
		StringBuilder sb = new StringBuilder();
		Random random = new Random();
		int length = 7;
		for(int i = 0; i < length; i++) {
			int index = random.nextInt(alphabet.length());
			char randomChar = alphabet.charAt(index);
			sb.append(randomChar);
		}
		return sb;
	}

	public boolean isUserEmailExistOrNot(String email) {
		boolean flag=false;
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found", "USER_NOT_FOUND"));
		if(user!=null) {
			flag=true;
		}
		return flag;
	}



	@Override
	public User createUserByEmail(String userName, String email, String role, Long userId, String designation) {

		String[] emailTo= {"aryan.chaurasia@corpseed.com"};
		String randomPass = getRandomNumber().toString();
		boolean isExistOrNot = isUserEmailExistOrNot(email);
		if(!isExistOrNot) {
			User u = new User();
			u.setId(userId);
			u.setFullName(userName);
			u.setEmail(email);

			List<String>listRole = new ArrayList();
			listRole.add(role);
			u.setRole(listRole);

			List<Role> roleList = roleRepository.findAllByNameIn(listRole);
			u.setUserRole(roleList);
			u.setDesignation(designation);
			String feedbackStatusURL = "https://corpseed.com" ;

					Context context = new Context();
					context.setVariable("userName", "Aryan Chaurasia");
					context.setVariable("email", email);
					context.setVariable("Rurl", feedbackStatusURL);
					context.setVariable("currentYear", LocalDateTime.now().getYear());
			String subject="Corpseed pvt ltd send a request for adding on team please go and set password and accept";
			String text="CLICK ON THIS link and set password";
			userRepository.save(u);
			String[] ccPersons= {email};
			mailSendSerivceImpl.sendEmail(emailTo, ccPersons,ccPersons, subject,text,context,"newUserCreate.html");
			return u;
		}else {
			User u = userRepository.findByEmail(email)
					.orElseThrow(() -> new ResourceNotFoundException("User not found", "USER_NOT_FOUND"));
			List<String>listRole = new ArrayList();
			listRole.add(role);
			u.setRole(listRole);
			String feedbackStatusURL = "http://localhost:3000/erp/login" ;

					Context context = new Context();
					context.setVariable("userName", "Aryan Chaurasia");
					context.setVariable("email", email);
					context.setVariable("Rurl", feedbackStatusURL);
					context.setVariable("currentYear", LocalDateTime.now().getYear());
			String subject="Corpseed pvt ltd send a request for adding on team please go and Accept";
			String text="CLICK ON THIS link and set password";
			userRepository.save(u);
			String[] ccPersons= {email};
			mailSendSerivceImpl.sendEmail(emailTo, ccPersons,ccPersons, subject,text,context,"TeamAdd.html");

			return u;

	}
	}

	@Override
	public User createUserByLead(UserDto userDto) {
     	User user = new User();
		user.setId(userDto.getId());
		user.setEmail(userDto.getEmail());
		user.setFullName(userDto.getUsername());
		List<Role> roleList = roleRepository.findAllByNameIn(userDto.getRole());
		user.setUserRole(roleList);
		user.setRole(userDto.getRole());
		user.setDepartment(userDto.getDepartment());
		user.setDesignation(userDto.getDesignation());
		userRepository.save(user);
		return user;

	}


	public Boolean createUserByLeadServices() {
		Boolean flag=false;
		List<Map<String,Object>> feignUser=leadFeignClient.getAllUserForAccount();
		for(Map<String,Object> u:feignUser) {
			User user = new User();
			user.setId(Long.parseLong((u.get("id").toString())));
			user.setEmail(u.get("email").toString());
			user.setFullName(u.get("fullName").toString());
			List<String>role=new ArrayList<>();
			role.add("USER");
			List<Role> roleList = roleRepository.findAllByNameIn(user.getRole());
			user.setUserRole(roleList);
			user.setDepartment(u.get("department").toString());
			user.setDesignation(u.get("designation").toString());
			userRepository.save(user);
			flag=true;
		}
		return flag;
	}

}
