package com.chatroom.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bot_active_modes")
public class BotActiveMode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long botUserId;
    private Integer enabled;
    private Integer intervalSeconds;
    private Long lastSentTime;
    private LocalDateTime updatedAt;
}
