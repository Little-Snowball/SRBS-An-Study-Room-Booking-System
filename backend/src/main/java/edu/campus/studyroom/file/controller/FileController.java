package edu.campus.studyroom.file.controller;

import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.ErrorEnvelope;
import edu.campus.studyroom.file.dto.FileContent;
import edu.campus.studyroom.file.dto.FileUploadView;
import edu.campus.studyroom.file.service.FileService;
import edu.campus.studyroom.security.CurrentUser;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * Evidence upload (students) and private binary read (owner or admin).
 * Error paths still produce the JSON error envelope because the service runs
 * before any response body is written.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadView> upload(@RequestParam("file") MultipartFile file,
                                              @CurrentUser CurrentUserPrincipal user) {
        return ApiResponse.created(fileService.upload(file, user.userId()));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long id, @CurrentUser CurrentUserPrincipal user) {
        FileContent fileContent = fileService.openContent(id, user.userId(), user.isAdmin());
        MediaType mediaType = "image/png".equals(fileContent.mimeType()) ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(safeFileName(fileContent.fileName()), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(fileContent.content().length)
                .body(fileContent.content());
    }

    /** Multipart rejection happens during parameter binding, before the method runs. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorEnvelope> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of(ErrorCode.VALIDATION_ERROR, "单个凭证不能超过 5MiB"));
    }

    /** Display-only sanitization; the stored name is never used as a path. */
    private static String safeFileName(String originalName) {
        String name = originalName == null ? "" : originalName.trim();
        String cleaned = name.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff]", "_");
        if (cleaned.isBlank()) {
            return "evidence";
        }
        return cleaned.length() > 100 ? cleaned.substring(cleaned.length() - 100) : cleaned;
    }
}
