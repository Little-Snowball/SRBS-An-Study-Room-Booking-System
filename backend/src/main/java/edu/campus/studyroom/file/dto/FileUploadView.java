package edu.campus.studyroom.file.dto;

/**
 * Upload result returned to the client. No disk path or storage key is exposed.
 */
public record FileUploadView(Long fileId, String mimeType, Long sizeBytes) {
}
