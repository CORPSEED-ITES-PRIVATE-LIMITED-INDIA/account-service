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
import com.account.dashboard.repository.PaymentRegisterRepository;
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

    private final PaymentRegisterRepository paymentRegisterRepository;

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

    ImportEstimateServiceImpl(PaymentRegisterRepository paymentRegisterRepository) {
        this.paymentRegisterRepository = paymentRegisterRepository;
    }

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
				users.add(u);
			}

		}
		return userList;
	}

	@Override
	public List<PaymentRegister> importPaymentData(String s3Url) {

		try {
			if (!s3Url.startsWith(s3BaseUrl)) {
				throw new RuntimeException("Invalid S3 URL: " + s3Url);
			}

			String fileKey = s3Url.replace(s3BaseUrl, "");

			S3Object s3Object = amazonS3Client.getObject(awsBucketName, fileKey);

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3Object.getObjectContent(), StandardCharsets.UTF_8))) {
				return parseCsvForPaymentData(reader);
			}
		} catch (IOException e) {
			throw new RuntimeException("Error reading CSV file from S3: " + e.getMessage());
		}

	
	}

	private List<PaymentRegister> parseCsvForPaymentData(BufferedReader reader) throws IOException {

		List<PaymentRegister> paymentRegisterList = new ArrayList<>();
		CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);

		for (CSVRecord record : csvParser) {
//			List<User> userList = new ArrayList<>();

			String leadId = record.get("leadId").toString();
			Long lId = Long.valueOf(leadId);

			String estimateId = record.get("estimateId").toString();
			Long eId = Long.valueOf(estimateId);

			String name = record.get("name").toString();
			String emails = record.get("emails").toString();
			String contactNo = record.get("contactNo").toString();
			String whatsappNo = record.get("whatsappNo").toString();
			String registerBy = record.get("registerBy").toString();
			Long rById = Long.valueOf(registerBy);

			
			String createdById = record.get("createdById").toString();
			Long cById = Long.valueOf(createdById);

			String transactionId = record.get("transactionId").toString();
			String serviceName = record.get("serviceName").toString();
			String professionalFees = record.get("professionalFees").toString();
			Long profFees = Long.valueOf(professionalFees);
			
			String profesionalGst = record.get("profesionalGst").toString();
			Double profGst = Double.valueOf(profesionalGst);

			String professionalGstPercent = record.get("professionalGstPercent").toString();
			Integer profGstPercent = Integer.valueOf(professionalGstPercent);

			String professionalGstAmount = record.get("professionalGstAmount").toString();
			Long profGstAmount = Long.valueOf(professionalGstAmount);

			String totalAmount = record.get("totalAmount").toString();
			Long totAmount = Long.valueOf(totalAmount);
			

			String paymentDate = record.get("paymentDate").toString();
			String estimateNo = record.get("estimateNo").toString();
			String status = record.get("status").toString();
			String companyName = record.get("companyName").toString();
			String termOfDelivery = record.get("termOfDelivery").toString();
			String productType = record.get("productType").toString();
			String comment = record.get("comment").toString();

			if(transactionId!=null &&"".equals(transactionId)) {
				PaymentRegister paymentRegister=new PaymentRegister();
				paymentRegister.setLeadId(lId);
				paymentRegister.setEstimateId(eId);
				paymentRegister.setName(name);
				paymentRegister.setEmails(emails);
				paymentRegister.setContactNo(contactNo);
				paymentRegister.setWhatsappNo(whatsappNo);
				paymentRegister.setRegisterBy(registerBy);
				paymentRegister.setCreatedById(cById);
				paymentRegister.setTransactionId(transactionId);
				paymentRegister.setServiceName(serviceName);
				paymentRegister.setProfessionalFees(profFees);
				paymentRegister.setProfesionalGst(profGst);
				paymentRegister.setProfessionalGstPercent(profGstPercent);
				paymentRegister.setProfessionalGstAmount(profGstAmount);
				paymentRegister.setTotalAmount(totAmount);
//				paymentRegister.setPaymentDate(paymentDate);//PaymentDate
				paymentRegister.setEstimateNo(estimateNo);
				paymentRegister.setStatus(status);
				paymentRegister.setCompanyName(companyName);
				paymentRegister.setTermOfDelivery(termOfDelivery);
				paymentRegister.setProductType(productType);
				paymentRegister.setComment(comment);
				paymentRegisterRepository.save(paymentRegister);
				paymentRegisterList.add(paymentRegister);
				
			}

		}
		return paymentRegisterList;
	
	}

}
