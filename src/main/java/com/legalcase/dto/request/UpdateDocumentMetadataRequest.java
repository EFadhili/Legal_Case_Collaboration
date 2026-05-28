package com.legalcase.dto.request;

import lombok.Data;

@Data
public class UpdateDocumentMetadataRequest {

    private String description;
    private String tags;
    private String reason;  // Optional reason for the edit
}