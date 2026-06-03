package com.opschat.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@ConfigurationProperties(prefix = "opschat.rag.file.upload")
public class FileUploadConfig {

    private String path = "./uploads";
    private String allowedExtensions = "txt,md";
}