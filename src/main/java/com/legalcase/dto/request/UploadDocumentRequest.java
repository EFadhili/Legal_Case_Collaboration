package com.legalcase.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadDocumentRequest {

    private MultipartFile file;
    private String description;
    private String tags;
}