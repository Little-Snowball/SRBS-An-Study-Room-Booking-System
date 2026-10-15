package edu.campus.studyroom.file.service;

import edu.campus.studyroom.appeal.domain.AppealFile;
import edu.campus.studyroom.appeal.mapper.AppealFileMapper;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.file.domain.FileObject;
import edu.campus.studyroom.file.domain.FileState;
import edu.campus.studyroom.file.dto.FileContent;
import edu.campus.studyroom.file.dto.FileUploadView;
import edu.campus.studyroom.file.mapper.FileObjectMapper;
import edu.campus.studyroom.security.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Evidence upload and private read. All validation and re-encoding happens
 * outside a transaction; the metadata insert is a short auto-committed write.
 * A failed registration deletes the file created by this call.
 */
@Service
public class FileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 15L * 1024 * 1024;
    private static final int MAX_FILES_PER_APPEAL = 3;
    private static final int MAX_DIMENSION = 10_000;
    private static final long MAX_PIXELS = 25_000_000L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png");
    private static final float JPEG_QUALITY = 0.85f;

    private final FileObjectMapper fileObjectMapper;
    private final AppealFileMapper appealFileMapper;
    private final AppProperties appProperties;
    private final BusinessClock clock;

    public FileServiceImpl(FileObjectMapper fileObjectMapper, AppealFileMapper appealFileMapper,
                           AppProperties appProperties, BusinessClock clock) {
        this.fileObjectMapper = fileObjectMapper;
        this.appealFileMapper = appealFileMapper;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @Override
    public FileUploadView upload(MultipartFile file, Long ownerId) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.validation("上传文件不能为空");
        }
        String rawName = file.getOriginalFilename();
        if (rawName == null || rawName.isBlank()) {
            throw BusinessException.validation("上传文件缺少文件名");
        }
        // original_name is display-only: keep a basename and drop any path parts.
        String originalName = Path.of(rawName.trim()).getFileName().toString();
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw BusinessException.validation("仅支持 JPG/PNG 格式凭证");
        }
        String mimeType = normalizeMimeType(file.getContentType());
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw BusinessException.validation("仅支持 image/jpeg 与 image/png 类型凭证");
        }
        byte[] raw = readBytes(file);
        if (raw.length > MAX_FILE_BYTES) {
            throw BusinessException.validation("单个凭证不能超过 5MiB");
        }

        BufferedImage image = decodeImage(raw);
        if (image == null) {
            throw BusinessException.validation("文件内容不是有效的图片，疑似伪装格式");
        }
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw BusinessException.validation("图片尺寸无效");
        }
        if (image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
            throw BusinessException.validation("图片宽高均不能超过 10000 像素");
        }
        if ((long) image.getWidth() * image.getHeight() > MAX_PIXELS) {
            throw BusinessException.validation("图片像素总数不能超过 2500 万");
        }

        String storageExtension = "jpeg".equals(extension) ? "jpg" : extension;
        byte[] encoded = reencode(image, extension);
        String sha256 = sha256Hex(encoded);
        String storageKey = UUID.randomUUID().toString();
        LocalDateTime now = clock.nowLocal();

        Path filesDir = Path.of(appProperties.getUploadDir(), "files");
        try {
            Files.createDirectories(filesDir);
        } catch (IOException e) {
            throw BusinessException.internal("凭证存储目录创建失败");
        }
        Path target = filesDir.resolve(storageKey + "." + storageExtension);
        Path temp = null;
        try {
            temp = Files.createTempFile(filesDir, "upload-", ".tmp");
            Files.write(temp, encoded);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(target);
            if (temp != null) {
                deleteQuietly(temp);
            }
            throw BusinessException.internal("凭证落盘失败");
        }

        FileObject record = new FileObject();
        record.setOwnerId(ownerId);
        record.setStorageKey(storageKey);
        record.setOriginalName(truncate(originalName, 255));
        record.setMimeType(mimeType);
        record.setSizeBytes((long) encoded.length);
        record.setSha256(sha256);
        record.setState(FileState.TEMP);
        record.setCreatedAt(now);
        try {
            fileObjectMapper.insert(record);
        } catch (RuntimeException ex) {
            deleteQuietly(target);
            throw BusinessException.internal("凭证登记失败");
        }
        log.info("appeal evidence uploaded fileId={} ownerId={} sizeBytes={}",
                record.getId(), ownerId, encoded.length);
        return new FileUploadView(record.getId(), mimeType, (long) encoded.length);
    }

    @Override
    public void bindToAppeal(Long fileId, Long appealId, Long ownerId) {
        FileObject file = fileObjectMapper.selectById(fileId);
        if (file == null || !file.getOwnerId().equals(ownerId)) {
            throw BusinessException.notFound("凭证不存在");
        }
        if (file.getState() != FileState.TEMP) {
            throw BusinessException.invalidState("凭证已被绑定或状态不可用");
        }
        List<AppealFile> bound = appealFileMapper.selectByAppealId(appealId);
        if (bound.size() >= MAX_FILES_PER_APPEAL) {
            throw BusinessException.validation("每个申诉最多绑定 3 张凭证");
        }
        long totalBytes = file.getSizeBytes() == null ? 0L : file.getSizeBytes();
        for (AppealFile appealFile : bound) {
            FileObject existing = fileObjectMapper.selectById(appealFile.getFileId());
            if (existing != null && existing.getSizeBytes() != null) {
                totalBytes += existing.getSizeBytes();
            }
        }
        if (totalBytes > MAX_TOTAL_BYTES) {
            throw BusinessException.validation("申诉凭证合计大小不能超过 15MiB");
        }
        int updated = fileObjectMapper.updateState(fileId, FileState.TEMP, FileState.BOUND);
        if (updated != 1) {
            throw BusinessException.invalidState("凭证状态已变更，请刷新后重试");
        }
    }

    @Override
    public FileContent openContent(Long fileId, Long viewerId, boolean viewerIsAdmin) {
        FileObject file = fileObjectMapper.selectById(fileId);
        if (file == null) {
            throw BusinessException.notFound("凭证不存在");
        }
        if (!viewerIsAdmin && !file.getOwnerId().equals(viewerId)) {
            // Same response as a missing id: existence never leaks to strangers.
            throw BusinessException.notFound("凭证不存在");
        }
        byte[] content;
        try {
            content = Files.readAllBytes(storagePath(file));
        } catch (IOException e) {
            throw BusinessException.notFound("凭证内容不存在");
        }
        return new FileContent(content, file.getMimeType(), file.getOriginalName(), file.getSha256());
    }

    @Override
    public int cleanupExpiredTemp(LocalDateTime before) {
        List<FileObject> expired = fileObjectMapper.selectExpiredTemp(before);
        int removed = 0;
        for (FileObject file : expired) {
            if (!deleteQuietly(storagePath(file))) {
                // Keep the metadata so a later run can retry the disk delete.
                log.warn("expired temp file disk delete failed, will retry storageKey={}", file.getStorageKey());
                continue;
            }
            fileObjectMapper.deleteById(file.getId());
            removed++;
        }
        return removed;
    }

    private Path storagePath(FileObject file) {
        String extension = "image/png".equals(file.getMimeType()) ? "png" : "jpg";
        return Path.of(appProperties.getUploadDir(), "files", file.getStorageKey() + "." + extension);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw BusinessException.validation("上传文件读取失败");
        }
    }

    /** Decodes the payload; a null result means the bytes are not a real image. */
    private BufferedImage decodeImage(byte[] raw) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(raw)) {
            return ImageIO.read(in);
        } catch (IOException e) {
            throw BusinessException.validation("文件内容无法解析为图片");
        }
    }

    /** Re-encodes to strip metadata; JPEG is flattened onto white and quality-capped. */
    private byte[] reencode(BufferedImage image, String extension) {
        try {
            if ("png".equals(extension)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", out)) {
                    throw BusinessException.validation("PNG 重编码失败");
                }
                return out.toByteArray();
            }
            BufferedImage rgb = image;
            if (image.getColorModel().hasAlpha()) {
                rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = rgb.createGraphics();
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                graphics.drawImage(image, 0, 0, null);
                graphics.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                JPEGImageWriteParam param = new JPEGImageWriteParam(Locale.ENGLISH);
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
                writer.write(null, new IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw BusinessException.internal("凭证重编码失败");
        }
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeMimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int semicolon = contentType.indexOf(';');
        String base = semicolon < 0 ? contentType : contentType.substring(0, semicolon);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    /** Deletes a file; true when no disk obstacle remains (missing counts as done). */
    private static boolean deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
            return true;
        } catch (IOException e) {
            log.warn("file delete failed path={}", path, e);
            return false;
        }
    }

    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
