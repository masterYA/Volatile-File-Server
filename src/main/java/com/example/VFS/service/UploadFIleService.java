package com.example.VFS.service;

import com.amazonaws.services.dynamodbv2.xspec.NULL;

import com.amazonaws.services.s3.AmazonS3ClientBuilder;


import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.example.VFS.Exception.MyFileNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import org.springframework.stereotype.Component;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.amazonaws.services.s3.AmazonS3;
import javax.annotation.PostConstruct;

@Service
public class UploadFIleService {

    private static Logger logger = LoggerFactory.getLogger(UploadFIleService.class);
    private AmazonS3 s3 = null;
    private final String tempDir = "/tmp/VFS-images/";

    @Value("${s3.bucket.region}")
    private String region;

    @PostConstruct
    private void initialize() {
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
        builder.setRegion(region);
        s3 = builder.build();
    }

    public String storeFile(MultipartFile multipartFile) {
        String fileName = StringUtils.cleanPath(multipartFile.getOriginalFilename());
        String globalUniqueVariable = UUID.randomUUID().toString();
        //String imagePath = "";

        try {
            if (multipartFile.isEmpty()) {
                throw new IOException(
                        "Failed to store empty file " + fileName);
            }

            File directory = new File(tempDir);
            if (!directory.exists()) {
                directory.mkdir();
            }

            String destination = directory + "/" + globalUniqueVariable + ".png";
            File file = new File(destination);
            multipartFile.transferTo(file);
            return destination;

        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public boolean putFileIns3(final String bucketName, final String filePath) {
        logger.info("inside putFile method");

        File file = new File(filePath);
        String fileTempPath = filePath;
        if (fileTempPath.startsWith(tempDir)) {
            fileTempPath = fileTempPath.replace(tempDir, "");
        }


        final String key = fileTempPath;
        try {
            s3.putObject(bucketName, key, file);
        } catch (Exception e) {
            logger.error("AmazonServiceException: {}", e);
            return false;
        }

        logger.info("exiting putFIle method");
        return true;

    }

    public boolean deleteFile(String directory, String filePath) {
        try {
            File file = new File(directory + filePath);
            if (file.delete()) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Error in deleteFile, Reason : " + e.getMessage());
        }
        return false;
    }

    private File gets3ObjectAsFile(String fileName,final String bucketName){
        String s3file="";
        try{
            List<S3ObjectSummary> s3objects = s3.listObjects(bucketName,fileName).getObjectSummaries();
            for(S3ObjectSummary summary:s3objects)
            {
                s3file=summary.getKey();
                System.out.println(summary.getKey());
            }
            S3Object object = s3.getObject(bucketName,s3file);

            InputStream reader = new BufferedInputStream(object.getObjectContent());
            File file = new File(tempDir+s3file);

            OutputStream writer = new BufferedOutputStream(new FileOutputStream(file));

            int read = -1;

            while ( ( read = reader.read() ) != -1 ) {
                writer.write(read);
            }

            writer.flush();
            writer.close();
            reader.close();

            return file;
        }catch (Exception ex){
            ex.printStackTrace();
            logger.error("Error in deleteFile, Reason : " + ex.getMessage());
            throw new MyFileNotFoundException("File not found " + fileName,ex);
        }
    }
    public Resource loadFileAsResource(String fileName,final String bucketName) {
        String s3file="";
        try {

            File file = gets3ObjectAsFile(fileName,bucketName);

            Resource resource = new UrlResource(file.toURI());
            if (resource.exists()) {
                return resource;
            } else {
                logger.error("Error in loadFileAsResource, Resource does not exists.");
                throw new MyFileNotFoundException("Resource does not exists.");
            }
        } catch (Exception ex){
             ex.printStackTrace();
             logger.error("Error in deleteFile, Reason : " + ex.getMessage());
             throw new MyFileNotFoundException("File not found " + fileName,ex);
        }
    }
}
