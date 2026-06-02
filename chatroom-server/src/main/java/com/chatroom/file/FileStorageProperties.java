package com.chatroom.file;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "chatroom.file-storage")
public class FileStorageProperties {

    /**
     * 图片资源桶目录。
     */
    private String imageBucket = "images";

    /**
     * 附件资源桶目录。
     */
    private String attachmentBucket = "attachments";
}
