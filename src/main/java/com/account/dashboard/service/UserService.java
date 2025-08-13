package com.account.dashboard.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.account.dashboard.domain.User;
import com.account.dashboard.dto.UpdateUser;
import com.account.dashboard.dto.UserDto;
@Service
public interface UserService {

	List<User> getAllUsers();

	boolean isUserExistOrNot(Long userId) throws Exception;

	User createUser(UserDto user);

	User getUserById(Long id);

	void deleteUser(Long id);

	User updateUser(User existingUser, UpdateUser user);

	User createUserByEmail(String userName, String email, String role, Long id, String designation);

	User createUserByLead(UserDto user);

}
