package edu.campus.studyroom.file.service;

import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.file.service.FileService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Maintenance entry point for schedulers: removes TEMP evidence that was never
 * bound within the 24h retention window (disk + metadata). Bound evidence is
 * never deleted.
 */
@Service
public class FileMaintenanceService {

    private static final long TEMP_RETENTION_HOURS = 24;

    private final FileService fileService;
    private final BusinessClock clock;

    public FileMaintenanceService(FileService fileService, BusinessClock clock) {
        this.fileService = fileService;
        this.clock = clock;
    }

    public int cleanupExpiredTempFiles() {
        LocalDateTime before = clock.nowLocal().minusHours(TEMP_RETENTION_HOURS);
        return fileService.cleanupExpiredTemp(before);
    }
}
