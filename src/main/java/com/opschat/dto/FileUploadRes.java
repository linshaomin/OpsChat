package com.opschat.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FileUploadRes {
    private String fileName;
    private String filePath;
    private long fileSize;

    public FileUploadRes() {
    }

    public FileUploadRes(String fileName, String filePath, long fileSize) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
    }
}