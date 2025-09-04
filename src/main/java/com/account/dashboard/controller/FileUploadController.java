package com.account.dashboard.controller;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.account.dashboard.service.FileUploadService;
import com.account.dashboard.util.UrlsMapping;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class FileUploadController {

	  @Autowired
	  FileUploadService storageService;
	  
	  @PostMapping("/uploadFileData")
	  public void uploadFilesData(@RequestParam MultipartFile files) {
		  if(files.isEmpty()) {	  
		  }
		  if(files.getContentType().equals("image/jpeg")) {
			  System.out.println("iiiiiiii");
		  }
		  storageService.uploadFilesData(files);
	  }
	  
	  @GetMapping("/getFileData")
	  public String[] getFileData() {	  
		  String[] s=storageService.getFilesData();
            return s;
	  }
	  
	  //=========================================Upload Image = = == = = = = = = = == = = = = = = = = = == =  = 
	
	  
	  @PostMapping(value = UrlsMapping.UPLOAD_IMAGE_TO_FILE, consumes = {"multipart/form-data"})
	  public String uploadimageToFileSystem(@RequestParam(name = "file", required = false) MultipartFile files) throws IllegalStateException, IOException {
		  String imageData=storageService.uploadImageToFileData(files);
           return imageData;
	  }
	  
	  @GetMapping("/downloadImageToFileSystem")
	  public byte[] downloadImageToFileSystem(@RequestParam String filePath) throws IllegalStateException, IOException {
		  byte[] imageData=storageService.downloadImageToFileSystem(filePath);
           return imageData;
	  }
	  
	  
	  @GetMapping(UrlsMapping.GET_IMAGE_TO_FILE)
	  public String getImageToFileSystem(@RequestParam String filePath) throws IllegalStateException, IOException {
		  String imageData=storageService.getImageToFileSystem(filePath);
           return imageData;
	  }

	  @PostMapping("/crm/uploadFile")
		public ResponseEntity<String> uploadFile(HttpServletRequest request, @RequestParam("fileName") String fileName) {

			// Default to "uploaded_file" if no filename is provided
			String finalFileName = (fileName != null) ? fileName : "uploaded_file";

			try (InputStream inputStream = request.getInputStream()) {
				return ResponseEntity.ok(storageService.uploadDocument(inputStream, finalFileName));
			} catch (IOException e) {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading file: " + e.getMessage());
			}
		}

}
