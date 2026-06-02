package com.chatroom.service;

import com.chatroom.mapper.UserMapper;
import com.chatroom.model.entity.Message;
import com.chatroom.model.entity.MessageOutbox;
import com.chatroom.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageOutboxProcessor {

    private final MessageOutboxService messageOutboxService;
    private final MessageService messageService;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelay = 5000)
    public void processPendingEvents() {
        List<MessageOutbox> events = messageOutboxService.claimPendingEvents(100);
        for (MessageOutbox event : events) {
            try {
                if (!MessageOutboxService.EVENT_TYPE_CHAT_MESSAGE.equals(event.getEventType())) {
                    messageOutboxService.markProcessed(event.getMessageId(), event.getEventType());
                    continue;
                }

                Message message = messageService.getById(event.getMessageId());
                if (message == null) {
                    messageOutboxService.markProcessed(event.getMessageId(), event.getEventType());
                    continue;
                }

                Map<String, Object> payload = buildWsPayload(message);
                if (message.getMessageType() == 0) {
                    messagingTemplate.convertAndSendToUser(String.valueOf(message.getSenderId()), "/queue/private/chat", payload);
                    messagingTemplate.convertAndSendToUser(String.valueOf(message.getTargetId()), "/queue/private/chat", payload);
                } else {
                    messagingTemplate.convertAndSend("/topic/group/" + message.getTargetId(), payload);
                }

                messageOutboxService.markProcessed(message.getId(), event.getEventType());
            } catch (Exception ex) {
                log.warn("Outbox event {} replay failed: {}", event.getId(), ex.getMessage());
                messageOutboxService.markRetry(event);
            }
        }
    }

    private Map<String, Object> buildWsPayload(Message message) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "CHAT");
        map.put("messageId", message.getId());
        map.put("messageType", message.getMessageType());
        map.put("senderId", message.getSenderId());
        map.put("targetId", message.getTargetId());
        map.put("replyToId", message.getReplyToId());
        map.put("content", message.getContent());
        map.put("contentType", message.getContentType());
        map.put("status", message.getStatus());
        map.put("clientMessageId", message.getClientMessageId());
        map.put("createdAt", message.getCreatedAt() != null ? message.getCreatedAt().toString() : null);

        User sender = userMapper.selectById(message.getSenderId());
        if (sender != null) {
            map.put("senderName", sender.getNickname());
            map.put("senderAvatar", sender.getAvatar());
        }
        return map;
    }
}
