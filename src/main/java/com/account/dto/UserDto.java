package com.account.dto;

import java.util.List;


import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;


@Getter
@Setter
public class UserDto {
	
	private Long id;
	
    private String username;

    @NonNull
    private String email;

    @NonNull
    private String designation;

    @NonNull
    private String department;


	@NonNull
	private List<String> role;



	

}
