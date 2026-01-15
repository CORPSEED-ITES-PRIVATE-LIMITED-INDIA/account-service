package com.account.controller.user;



import com.account.dto.NewSignupRequest;
import com.account.dto.UpdateUser;
import com.account.dto.UserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.account.domain.User;
import com.account.service.UserService;
import com.account.util.UrlsMapping;

import java.util.List;

@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
public class UserController {
	@Autowired
	 UserService userService;

	@GetMapping(UrlsMapping.GET_ALL_USER)
	public ResponseEntity<List<User>> getAllUserData()
	{

		List<User> allUser=userService.getAllUsers();
		if(!allUser.isEmpty())
		{
			return  new ResponseEntity<>(allUser,HttpStatus.OK);
		}
		else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}
	
	@GetMapping(UrlsMapping.IS_USER_EXIST_OR_NOT)
	public boolean isUserExistOrNot(@RequestParam Long userId) throws Exception
	{

		boolean userExistOrNot=userService.isUserExistOrNot(userId);
		return userExistOrNot;
	}


	@PostMapping(UrlsMapping.CREATE_USER)
	public ResponseEntity<User> createUser(@RequestBody UserDto user) {
		User createdUser = userService.createUser(user);
		return new ResponseEntity<>(createdUser, HttpStatus.CREATED);
	}
	
	@PostMapping(UrlsMapping.CREATE_USER_BY_LEAD)
	public User createUserByLead(@RequestBody UserDto user) {
		User createdUser = userService.createUserByLead(user);
		return createdUser;
	}


	@GetMapping(UrlsMapping.GET_USER)
	public ResponseEntity<User> getUserById(@RequestParam Long id) {
		User user = userService.getUserById(id);
		if (user != null) {
			return new ResponseEntity<>(user, HttpStatus.OK);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@PutMapping(UrlsMapping.UPDATE_USER)
	public ResponseEntity<User> updateUser(@RequestBody UpdateUser user) {
		User existingUser = userService.getUserById(user.getId());
		if (existingUser != null) {
			User updatedUser = userService.updateUser(existingUser,user);
			return new ResponseEntity<>(updatedUser, HttpStatus.OK);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	@DeleteMapping(UrlsMapping.DELETE_USER)
	public ResponseEntity<Void> deleteUser(@RequestParam Long id) {
		User existingUser = userService.getUserById(id);
		if (existingUser != null) {
			userService.deleteUser(id);
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}
	

	@PostMapping(UrlsMapping.CREATE_USER_BY_LEAD_SERVICES)
	public Boolean createUserByLeadServices() {
		Boolean createdUser = userService.createUserByLeadServices();
		return createdUser;
	}
}

