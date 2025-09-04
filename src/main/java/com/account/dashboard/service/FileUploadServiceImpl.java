package com.account.dashboard.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Date;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.account.dashboard.config.AwsBlobAdapter;
import com.account.dashboard.domain.FileData;
import com.account.dashboard.repository.FileDataRepository;

@Service
public class FileUploadServiceImpl implements FileUploadService{
	
	


	//	@Autowired
	//    private AzureBlobAdapter azureAdapter;
	//	
	@Autowired
	AwsBlobAdapter awsBlobAdapter;

	@Autowired
	FileDataRepository fileDataRepository;

	//	public String UPLOAD_DIR="/Users/aryanchaurasia/Documents/Corpseed-img";
	public String UPLOAD_DIR="C:/Users/user/Documents/imageTest/image (1)";
	//    public final String FOLDER_PATH="C:/Users/user/Documents/images/";
	//    public final String PROD_PATH="https://demo003.blob.core.windows.net/test3/";
	//    public final String PROD_PATH="https://recordplus.blob.core.windows.net/eeptest/";
	//    public final String PROD_PATH="https://corpseeds.blob.core.windows.net/corpseed-erp/";
	public final String PROD_PATH= "https://erp-corpseed.s3.ap-south-1.amazonaws.com/";
	public final String PROD_PATH2="https://corpseed-crm.s3.ap-south-1.amazonaws.com/crm/";

	public boolean uploadFilesData( MultipartFile multipartFile) {

		boolean f=false;
		try {
			InputStream ls=multipartFile.getInputStream();
			byte data[]=new byte[ls.available()];
			ls.read(data);

			FileOutputStream fos=new FileOutputStream(UPLOAD_DIR+File.separator+multipartFile.getOriginalFilename());
			fos.write(data);
			fos.flush();
			fos.close();
			f=true;

		}catch(Exception e) {

		}

		return f;


	}

	@Override
	public String[] getFilesData() { 
		String folderPath = System.getProperty("user.dir") +"/Uploads"; 
		File directory= new File(UPLOAD_DIR); 
		String[] filenames = directory.list(); 
		return filenames;		
	}





	public String uploadImageToFileData(MultipartFile file) throws IllegalStateException, IOException {

		//		String filePath=PROD_PATH+file.getOriginalFilename();

		//		String s=azureAdapter.uploadv2(file, 0);
		long d = new Date().getTime();
		String s =awsBlobAdapter.uploadAws(file, d);
		String filePath=PROD_PATH+s;
		FileData fileData = new FileData();
		fileData.setName(s);
		fileData.setType(file.getContentType());
		fileData.setFilePath(filePath);
		String fs = isFileExist(s);
		if(isFileExist(s)==null) {
			fileDataRepository.save(fileData);
		}else {
			filePath=PROD_PATH+fs;

		}

		//		file.transferTo(new File(filePath));
		if(filePath!=null) {
			return filePath;
		}
		return null;

	}


	public String uploadImageToFileDataV2(MultipartFile file) throws IllegalStateException, IOException {
		String filePath=PROD_PATH+file.getOriginalFilename();

		//		String filePath=PROD_PATH+file.getOriginalFilename();

		//		FileData fileData = new FileData();
		//		fileData.setName(file.getOriginalFilename());
		//		fileData.setType(file.getContentType());
		//		fileData.setFilePath(filePath);
		//		fileDataRepository.save(fileData);
		//		String s=azureAdapter.upload(file, 0);
		//		System.out.println("UPLOAD IMAGE .."+s);
		////		file.transferTo(new File(filePath));
		//		if(filePath!=null) {
		//			return filePath;
		//		}
		return null;
		

		
	}

	public byte[] downloadImageToFileSystem(String fileName) throws IOException {
		Optional<FileData> fileData = fileDataRepository.findByName(fileName);
		String filePath = fileData.get().getFilePath();
		byte[] images = Files.readAllBytes(new File(filePath).toPath());
		return images;
	}

	@Override
	public String getImageToFileSystem(String fileName) {
		Optional<FileData> fileData = fileDataRepository.findByName(fileName);
		String image=null;
		if(fileData!=null && fileData.get()!=null) {
			image=fileData.get().getFilePath();
		}
		return image;
	}

	private String isFileExist(String fileName) {

		FileData fileData = fileDataRepository.findByNameExist(fileName);
		String image=null;
		if(fileData!=null) {
			image=fileData.getName();
		}
		return image;
	}

	@Override
	public String uploadDocument(InputStream inputStream, String finalFileName) {
		String documentName = awsBlobAdapter.uploadDocumentOnAws(inputStream, finalFileName);
		String filePath=PROD_PATH2+documentName;
		FileData fileData = new FileData();
		fileData.setName(documentName);
		fileData.setType("application/octet-stream");
		fileData.setFilePath(filePath);
		if(isFileExist(documentName)==null) {
			fileDataRepository.save(fileData);
		}
		return documentName;
	}
	    

}
