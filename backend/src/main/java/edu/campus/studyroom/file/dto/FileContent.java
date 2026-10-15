package edu.campus.studyroom.file.dto;

/**
 * Private file content for the owner or an admin. The file name is display-only
 * and never used to build a filesystem path.
 */
public record FileContent(byte[] content, String mimeType, String fileName, String sha256) {
}
