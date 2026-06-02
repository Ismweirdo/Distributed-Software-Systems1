package com.chatroom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chatroom.mapper.MessageOutboxMapper;
import com.chatroom.model.entity.MessageOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageOutboxService {

    public static final String EVENT_TYPE_CHAT_MESSAGE = "CHAT_MESSAGE";
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PROCESSED = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_PROCESSING = 3;
    private static final int MAX_RETRY_COUNT = 5;

    private final MessageOutboxMapper messageOutboxMapper;
    private final ObjectMapper objectMapper;

    public void createChatMessageEvent(Long messageId) {
        MessageOutbox existing = messageOutboxMapper.selectOne(new LambdaQueryWrapper<MessageOutbox>()
                .eq(MessageOutbox::getMessageId, messageId)
                .eq(MessageOutbox::getEventType, EVENT_TYPE_CHAT_MESSAGE)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }

        try {
            MessageOutbox outbox = new MessageOutbox();
            outbox.setMessageId(messageId);
            outbox.setEventType(EVENT_TYPE_CHAT_MESSAGE);
            outbox.setPayloadJson(objectMapper.writeValueAsString(Map.of("messageId", messageId)));
            outbox.setStatus(STATUS_PENDING);
            outbox.setRetryCount(0);
            outbox.setCreatedAt(LocalDateTime.now());
            messageOutboxMapper.insert(outbox);
        } catch (Exception ex) {
            throw new RuntimeException("创建消息 outbox 失败", ex);
        }
    }

    public void markProcessed(Long messageId, String eventType) {
        MessageOutbox outbox = messageOutboxMapper.selectOne(new LambdaQueryWrapper<MessageOutbox>()
                .eq(MessageOutbox::getMessageId, messageId)
                .eq(MessageOutbox::getEventType, eventType)
                .last("LIMIT 1"));
        if (outbox == null) {
            return;
        }
        outbox.setStatus(STATUS_PROCESSED);
        outbox.setProcessedAt(LocalDateTime.now());
        messageOutboxMapper.updateById(outbox);
    }

    public void markRetry(MessageOutbox outbox) {
        int nextRetryCount = (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
        outbox.setRetryCount(nextRetryCount);
        outbox.setStatus(nextRetryCount >= MAX_RETRY_COUNT ? STATUS_FAILED : STATUS_PENDING);
        outbox.setProcessedAt(null);
        messageOutboxMapper.updateById(outbox);
    }

    public List<MessageOutbox> getPendingEvents(int limit) {
        return messageOutboxMapper.selectList(new LambdaQueryWrapper<MessageOutbox>()
                .eq(MessageOutbox::getStatus, STATUS_PENDING)
                .orderByAsc(MessageOutbox::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    public List<MessageOutbox> claimPendingEvents(int limit) {
        List<MessageOutbox> candidates = getPendingEvents(limit);
        List<MessageOutbox> claimed = new ArrayList<>();
        for (MessageOutbox candidate : candidates) {
            int updated = messageOutboxMapper.update(null, new LambdaUpdateWrapper<MessageOutbox>()
                    .eq(MessageOutbox::getId, candidate.getId())
                    .eq(MessageOutbox::getStatus, STATUS_PENDING)
                    .set(MessageOutbox::getStatus, STATUS_PROCESSING));
            if (updated > 0) {
                candidate.setStatus(STATUS_PROCESSING);
                claimed.add(candidate);
            }
        }
        return claimed;
    }
}
