package com.chatroom.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("group_bot_auto_chat")
public class GroupBotAutoChat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private Long botUserId;
    private LocalDateTime createdAt;
}
