package com.account.dashboard.serviceImpl;
import org.apache.commons.csv.CSVFormat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.account.dashboard.dto.GraphDateFilter;
import com.account.dashboard.repository.RoleRepository;
import com.account.dashboard.repository.UserRepository;
import com.account.dashboard.service.ImportEstimateService;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.account.dashboard.domain.PaymentRegister;
import com.account.dashboard.domain.Role;
import com.account.dashboard.domain.User;
import com.account.dashboard.domain.User.*;


@Service
public class ImportEstimateServiceImpl implements ImportEstimateService{

	@Autowired
    UserRepository userRepository;
    
	@Autowired
    RoleRepository roleRepository;


	@Value("${aws.s3.bucket-name}")
	private String awsBucketName;

	@Value("${aws.accessKey}")
	private String accessKey;

	@Value("${aws.secretKey}")
	private String secretKey;

	@Value("${aws_path}")
	private String s3BaseUrl;
	
	@Autowired
	private AmazonS3 amazonS3Client;

//    ImportEstimateServiceImpl(UserRepository userRepository) {
//        this.userRepository = userRepository;
//    }

	@Override
	public List<Map<String, Object>> importEstimateData(String s3Url) {
		
		return null;
	}

	@Override
	public List<User> importUserData(String s3Url) {
		try {
			if (!s3Url.startsWith(s3BaseUrl)) {
				throw new RuntimeException("Invalid S3 URL: " + s3Url);
			}

			String fileKey = s3Url.replace(s3BaseUrl, "");

			S3Object s3Object = amazonS3Client.getObject(awsBucketName, fileKey);

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3Object.getObjectContent(), StandardCharsets.UTF_8))) {
				return parseCsvForUser(reader);
			}
		} catch (IOException e) {
			throw new RuntimeException("Error reading CSV file from S3: " + e.getMessage());
		}

	}
	
	public List<User> parseCsvForUser(BufferedReader reader)  throws IOException {
		List<User> users = new ArrayList<>();
		CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
		List<User> userList = new ArrayList<>();

		for (CSVRecord record : csvParser) {
//			List<User> userList = new ArrayList<>();

			String userName = record.get("userName").toString();
			String userEmail = record.get("userEmail").toString();
			String userDepartment = record.get("userDepartment").toString();
			String userDesignation = record.get("userDesignation").toString();
			String userRole = record.get("userRole").toString();
			if(userName!=null && (!userName.equals(""))) {
				User u=new User();
				u.setId(1l);
				u.setFullName(userName);
				u.setEmail(userEmail);
				u.setDepartment(userDepartment);
				u.setDesignation(userDesignation);
				Role role = roleRepository.findAllByName(userRole);
				List<Role>rList=new ArrayList<>();
				rList.add(role);
				u.setRole(Arrays.asList(userRole));
//				System.out.println("user role . "+userRole);
//				System.out.println("role . "+role);

				u.setUserRole(rList);
				userRepository.save(u);
			}

		}
		return userList;
	}

	@Override
	public List<PaymentRegister> importPaymentData(String s3Url) {

		return null;
	}

}
