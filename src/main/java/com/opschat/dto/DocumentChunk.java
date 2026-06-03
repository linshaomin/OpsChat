package com.opschat.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentChunk {

    private String content;
    private String source;
    private int chunkIndex;
    private int totalChunks;
    private String metadata;

    public DocumentChunk() {
    }

    public DocumentChunk(String content, String source, int chunkIndex, int totalChunks) {
        this.content = content;
        this.source = source;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
    }
}