package com.legalcase.util;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.Loader;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileUtils {

    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100 MB
    private static final String[] ALLOWED_EXTENSIONS = {"pdf", "docx", "txt", "xlsx"};
    private static final String[] ALLOWED_MIME_TYPES = {
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    };

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size exceeds " + (MAX_FILE_SIZE / (1024 * 1024)) + " MB limit");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new RuntimeException("Invalid filename");
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        boolean allowedExtension = false;
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equals(extension)) {
                allowedExtension = true;
                break;
            }
        }

        if (!allowedExtension) {
            throw new RuntimeException("File type not allowed. Allowed: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        String mimeType = file.getContentType();
        if (mimeType != null) {
            boolean allowedMime = false;
            for (String allowed : ALLOWED_MIME_TYPES) {
                if (allowed.equals(mimeType)) {
                    allowedMime = true;
                    break;
                }
            }
            if (!allowedMime && !extension.equals("txt")) {
                log.warn("Unexpected MIME type: {} for extension: {}", mimeType, extension);
            }
        }
    }

    public String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }

    public String sanitizeFilename(String filename) {
        if (filename == null) return null;

        // Remove path traversal
        String sanitized = filename.replaceAll("[/\\\\:*?\"<>|]", "_");
        // Replace spaces with underscores
        sanitized = sanitized.replace(' ', '_');
        // Limit length
        if (sanitized.length() > 200) {
            String extension = getFileExtension(sanitized);
            String nameWithoutExt = sanitized.substring(0, 200 - extension.length() - 1);
            sanitized = nameWithoutExt + "." + extension;
        }
        return sanitized;
    }

    public String generateStorageFileName(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String uniqueId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String nameWithoutExt = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
        String sanitizedBase = sanitizeFilename(nameWithoutExt);
        return uniqueId + "_" + sanitizedBase + "." + extension;
    }

    public String generateStoragePath(LegalCase legalCase, Task task, String fileName) {
        String basePath;
        if (legalCase != null) {
            basePath = "cases/" + legalCase.getId();
        } else if (task != null) {
            basePath = "tasks/" + task.getId();
        } else {
            basePath = "documents";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM");
        String datePath = LocalDateTime.now().format(formatter);

        return basePath + "/" + datePath + "/" + fileName;
    }

    // Overloaded method for backward compatibility
    public String generateStoragePath(Long caseId, Long taskId, String fileName) {
        String basePath;
        if (caseId != null) {
            basePath = "cases/" + caseId;
        } else if (taskId != null) {
            basePath = "tasks/" + taskId;
        } else {
            basePath = "documents";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM");
        String datePath = LocalDateTime.now().format(formatter);

        return basePath + "/" + datePath + "/" + fileName;
    }

    public String uploadToS3(MultipartFile file, String storagePath) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            metadata.setContentDisposition("inline; filename=\"" + file.getOriginalFilename() + "\"");

            PutObjectRequest request = new PutObjectRequest(
                    bucketName,
                    storagePath,
                    file.getInputStream(),
                    metadata
            );

            amazonS3.putObject(request);
            log.info("File uploaded to S3: {}/{}", bucketName, storagePath);

            return storagePath;

        } catch (Exception e) {
            log.error("Failed to upload to S3: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file to storage: " + e.getMessage());
        }
    }

    public InputStream downloadFromS3(String storagePath) {
        try {
            S3Object s3Object = amazonS3.getObject(bucketName, storagePath);
            return s3Object.getObjectContent();
        } catch (Exception e) {
            log.error("Failed to download from S3: {}", e.getMessage());
            throw new RuntimeException("Failed to download file: " + e.getMessage());
        }
    }

    public String generatePresignedUrl(String storagePath) {
        try {
            Date expiration = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000);
            URL url = amazonS3.generatePresignedUrl(bucketName, storagePath, expiration);
            return url.toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: {}", e.getMessage());
            return amazonS3.getUrl(bucketName, storagePath).toString();
        }
    }

    public void deleteFromS3(String storagePath) {
        try {
            amazonS3.deleteObject(bucketName, storagePath);
            log.info("File deleted from S3: {}/{}", bucketName, storagePath);
        } catch (Exception e) {
            log.error("Failed to delete from S3: {}", e.getMessage());
            throw new RuntimeException("Failed to delete file from storage: " + e.getMessage());
        }
    }

    public boolean existsInS3(String storagePath) {
        try {
            return amazonS3.doesObjectExist(bucketName, storagePath);
        } catch (Exception e) {
            log.error("Failed to check existence in S3: {}", e.getMessage());
            return false;
        }
    }

    public String getBucketName() {
        return bucketName;
    }

    public String extractTextFromFile(InputStream inputStream, String extension) throws Exception {
        switch (extension.toLowerCase()) {
            case "pdf":
                return extractTextFromPdf(inputStream);
            case "docx":
                return extractTextFromDocx(inputStream);
            case "xlsx":
                return extractTextFromXlsx(inputStream);
            case "txt":
                return extractTextFromTxt(inputStream);
            default:
                throw new RuntimeException("Unsupported file type: " + extension);
        }
    }

    private String extractTextFromPdf(InputStream inputStream) throws Exception {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text.length() > 5_000_000) {
                text = text.substring(0, 5_000_000);
            }
            return text;
        }
    }

    private String extractTextFromDocx(InputStream inputStream) throws Exception {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            if (text.length() > 5_000_000) {
                text = text.substring(0, 5_000_000);
            }
            return text;
        }
    }

    private String extractTextFromXlsx(InputStream inputStream) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                XSSFSheet sheet = workbook.getSheetAt(i);
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        switch (cell.getCellType()) {
                            case STRING:
                                text.append(cell.getStringCellValue()).append(" ");
                                break;
                            case NUMERIC:
                                text.append(cell.getNumericCellValue()).append(" ");
                                break;
                            case BOOLEAN:
                                text.append(cell.getBooleanCellValue()).append(" ");
                                break;
                            case FORMULA:
                                text.append(cell.getCellFormula()).append(" ");
                                break;
                            default:
                                text.append(" ");
                        }
                        if (text.length() > 5_000_000) {
                            log.warn("Extracted text exceeded 5MB limit, truncating");
                            return text.substring(0, 5_000_000);
                        }
                    }
                    text.append("\n");
                }
            }
            return text.toString();
        }
    }

    private String extractTextFromTxt(InputStream inputStream) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.length() > 5_000_000) {
            text = text.substring(0, 5_000_000);
        }
        return text;
    }
}