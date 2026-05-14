package com.legalcase.dto.request;

import lombok.Data;

@Data
public class UpdateDocumentMetadataRequest {

    private String description;
    private String tags;
}