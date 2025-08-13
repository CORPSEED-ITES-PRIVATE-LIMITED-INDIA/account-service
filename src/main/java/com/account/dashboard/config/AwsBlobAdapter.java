package com.account.dashboard.config;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
@Service
public class AwsBlobAdapter {

	

    @Autowired
    AmazonS3 amazonS3Client;

    @Value("${aws.s3.bucket-name}")
    private String awsBucketName;

    @Value("${aws.s3.bucket.crm.name}")
    private String awsCrmBucketName;

    public String uploadAws(MultipartFile file, long prefixName) {
        String fileName = null;

        if (file != null && file.getSize() > 0) {
            try {

                if (prefixName != 0) {
                    fileName = prefixName + file.getOriginalFilename().replace(" ", "_");
                } else {
                    fileName = file.getOriginalFilename().replace(" ", "_");
                }

                boolean fileExist = isFileExist(fileName);
                if (!fileExist) {
                    ObjectMetadata metadata = new ObjectMetadata();
                    metadata.setContentLength(file.getSize());

                    // Create the PutObjectRequest with public read access
                    PutObjectRequest putObjectRequest = new PutObjectRequest(awsBucketName, fileName, file.getInputStream(), metadata)
                            .withCannedAcl(CannedAccessControlList.PublicRead);

                    // Upload the file to the S3 bucket with public read access
                    amazonS3Client.putObject(putObjectRequest);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return fileName;
    }

    private boolean isFileExist(String fileName) {
        try {
            amazonS3Client.getObjectMetadata(awsBucketName, fileName);
            return true;
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }
    private boolean deleteFile(String fileName) {
        try {
            amazonS3Client.deleteObject(awsBucketName, fileName);
            return true;
        } catch (AmazonS3Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public String uploadDocumentOnAws(InputStream inputStream, String finalFilename) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.addUserMetadata("name", finalFilename);
        // Create the PutObjectRequest with public read access
        PutObjectRequest putObjectRequest = new PutObjectRequest(awsCrmBucketName,"crm/"+ finalFilename, inputStream, metadata)
                .withCannedAcl(CannedAccessControlList.PublicRead);

        // Upload the file to the S3 bucket with public read access
        amazonS3Client.putObject(putObjectRequest);
        return finalFilename;
    }



}
