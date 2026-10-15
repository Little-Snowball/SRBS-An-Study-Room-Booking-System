package edu.campus.studyroom.file.service;

import edu.campus.studyroom.file.dto.FileContent;
import edu.campus.studyroom.file.dto.FileUploadView;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * Appeal evidence upload and private read. Uploads validate, decode-verify and
 * re-encode images outside any transaction; only the short metadata insert runs
 * afterwards. Filesystem paths, URLs and storage keys never come from clients.
 */
public interface FileService {

    /**
     * Validates and re-encodes an image, stores it under the configured upload
     * directory and registers a TEMP metadata row. Any failure removes the
     * freshly written file.
     */
    FileUploadView upload(MultipartFile file, Long ownerId);

    /**
     * Binds a TEMP file to an appeal: owner check, at most 3 files and 15MiB in
     * total per appeal, then an atomic TEMP to BOUND state change. Callers run
     * this inside their command transaction; standalone calls auto-commit per
     * statement.
     */
    void bindToAppeal(Long fileId, Long appealId, Long ownerId);

    /**
     * Opens file content for the owner or an admin. Non-owners get the same
     * NOT_FOUND as a missing id so existence never leaks.
     */
    FileContent openContent(Long fileId, Long viewerId, boolean viewerIsAdmin);

    /** Deletes TEMP files created before the given instant (disk + metadata). */
    int cleanupExpiredTemp(LocalDateTime before);
}
