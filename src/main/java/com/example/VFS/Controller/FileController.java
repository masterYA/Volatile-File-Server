package com.example.VFS.Controller;

import com.amazonaws.services.ram.model.MalformedArnException;
import com.example.VFS.Payload.UploadFileResponse;
import com.example.VFS.service.UploadFIleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;


@RestController
@RequestMapping("/")
public class FileController {
    private static Logger logger = LoggerFactory.getLogger(FileController.class);

    private static String s3BucketName;
    public static String s3Endpoint;
    final String tempDir = "/tmp/VFS-images/";

    @Autowired
    private Environment env;

    @Autowired
    private UploadFIleService uploadFIleService;

    @PostConstruct
    private void initializeBucket() {
        FileController.s3BucketName = env.getProperty("s3.bucket.name");
        FileController.s3Endpoint = env.getProperty("s3.bucket.endpoint");
    }

    @PostMapping("uploadFile/")
    public UploadFileResponse uploadFile(@RequestParam("file") MultipartFile file) {

        logger.info("inside uploadFile method");
        String imagePath = uploadFIleService.storeFile(file);
        String s3Link = "";

        if (uploadFIleService.putFileIns3(s3BucketName, imagePath)) {
            if (imagePath.startsWith(tempDir)) {
                imagePath = imagePath.replace(tempDir, "");
            }
            s3Link = s3Endpoint + s3BucketName + "/" + imagePath;

           if(uploadFIleService.deleteFile(tempDir,imagePath)){
                logger.info("Successfully delete file : " + tempDir+imagePath);
            }
            else{
                logger.info("Unable to delete file : " + tempDir+imagePath);
            }
        }

        return new UploadFileResponse(imagePath.substring(0,4), s3Link, file.getContentType(), file.getSize());
    }

    @GetMapping("/downloadFile/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, HttpServletRequest request) {


        Resource resource = uploadFIleService.loadFileAsResource(fileName,s3BucketName);


        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            logger.info("Could not determine file type.");
        }
        if(contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        }



}


