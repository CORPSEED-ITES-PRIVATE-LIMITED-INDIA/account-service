package com.account.dashboard.service;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public interface FileUploadService {

	boolean uploadFilesData(MultipartFile files);

	String[] getFilesData();

	String uploadImageToFileData(MultipartFile files) throws IllegalStateException, IOException;

	byte[] downloadImageToFileSystem(String filePath) throws IOException;

	String getImageToFileSystem(String filePath);
	String uploadDocument(InputStream inputStream, String finalFileName);
}
