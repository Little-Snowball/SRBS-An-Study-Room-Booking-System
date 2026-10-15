package edu.campus.studyroom.appeal.domain;


/** Entity AppealFile (table: appeal_file). Plain Java class per implementation manual. */
public class AppealFile {

    private Long appealId;
    private Long fileId;

    public Long getAppealId() {
        return appealId;
    }

    public void setAppealId(Long appealId) {
        this.appealId = appealId;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

}
