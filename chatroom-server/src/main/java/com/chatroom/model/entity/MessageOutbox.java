package com.chatroom.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message_outbox")
public class MessageOutbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private String eventType;
    private String payloadJson;
    private Integer status;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
