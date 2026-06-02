package com.chatroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatroom.common.Constants;
import com.chatroom.mapper.MessageMapper;
import com.chatroom.mapper.UserMapper;
import com.chatroom.model.dto.ChatMessageDTO;
import com.chatroom.model.entity.Message;
import com.chatroom.model.entity.User;
import com.chatroom.model.vo.MessageVO;
import com.chatroom.service.MessageOutboxService;
import com.chatroom.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final MessageOutboxService messageOutboxService;

    @Override
    @Transactional
    public MessageVO sendAndSaveMessage(Long senderId, ChatMessageDTO dto) {
        String clientMessageId = normalizeClientMessageId(dto.getClientMessageId());
        if (clientMessageId != null) {
            Message existing = findExistingMessage(senderId, clientMessageId);
            if (existing != null) {
                return toMessageVO(existing);
            }
        }

        Message msg = buildMessage(senderId, dto, clientMessageId);
        messageMapper.insert(msg);
        if (!Boolean.TRUE.equals(dto.getSuppressOutbox())) {
            messageOutboxService.createChatMessageEvent(msg.getId());
        }

        return toMessageVO(msg);
    }

    @Override
    public List<MessageVO> getPrivateHistory(Long userId, Long friendId, int page, int size) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getMessageType, Constants.MSG_TYPE_PRIVATE)
                .and(w -> w
                    .and(w1 -> w1.eq(Message::getSenderId, userId).eq(Message::getTargetId, friendId))
                    .or(w2 -> w2.eq(Message::getSenderId, friendId).eq(Message::getTargetId, userId))
                )
                .ge(Message::getCreatedAt, LocalDateTime.now().minusDays(Constants.HISTORY_RETENTION_DAYS))
                .orderByDesc(Message::getId);

        Page<Message> pageResult = new Page<>(page, size);
        Page<Message> result = messageMapper.selectPage(pageResult, wrapper);
        return toChronologicalMessageVOs(result.getRecords());
    }

    @Override
    public List<MessageVO> getGroupHistory(Long groupId, int page, int size) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getMessageType, Constants.MSG_TYPE_GROUP)
                .eq(Message::getTargetId, groupId)
                .ge(Message::getCreatedAt, LocalDateTime.now().minusDays(Constants.HISTORY_RETENTION_DAYS))
                .orderByDesc(Message::getId);

        Page<Message> pageResult = new Page<>(page, size);
        Page<Message> result = messageMapper.selectPage(pageResult, wrapper);
        return toChronologicalMessageVOs(result.getRecords());
    }

    @Override
    @Transactional
    public int clearPrivateHistory(Long userId, Long friendId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getMessageType, Constants.MSG_TYPE_PRIVATE)
                .and(w -> w
                    .and(w1 -> w1.eq(Message::getSenderId, userId).eq(Message::getTargetId, friendId))
                    .or(w2 -> w2.eq(Message::getSenderId, friendId).eq(Message::getTargetId, userId))
                );
        return messageMapper.delete(wrapper);
    }

    @Override
    @Transactional
    public void recallMessage(Long messageId, Long userId) {
        Message msg = messageMapper.selectById(messageId);
        if (msg == null) {
            throw new RuntimeException("消息不存在");
        }
        if (!msg.getSenderId().equals(userId)) {
            throw new RuntimeException("只能撤回自己的消息");
        }

        long elapsed = java.time.Duration.between(msg.getCreatedAt(), LocalDateTime.now()).toMillis();
        if (elapsed > Constants.RECALL_WINDOW_MS) {
            throw new RuntimeException("超过2分钟的消息无法撤回");
        }

        msg.setContent("[消息已撤回]");
        msg.setContentType(Constants.CONTENT_TYPE_TEXT);
        messageMapper.updateById(msg);
    }

    @Override
    @Transactional
    public void permanentDelete(Long messageId, Long userId) {
        Message msg = messageMapper.selectById(messageId);
        if (msg == null) throw new RuntimeException("消息不存在");
        if (!msg.getSenderId().equals(userId)) throw new RuntimeException("只能删除自己的消息");
        messageMapper.deleteById(messageId);
    }

    @Override
    public MessageVO getMessageContext(Long messageId) {
        Message msg = messageMapper.selectById(messageId);
        if (msg == null) {
            throw new RuntimeException("消息不存在");
        }
        return toMessageVO(msg);
    }

    @Override
    public Message getById(Long messageId) {
        return messageMapper.selectById(messageId);
    }

    @Override
    public List<MessageVO> getRecentMessages(Long userId, Long friendId, int count) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getMessageType, Constants.MSG_TYPE_PRIVATE)
                .and(w -> w
                    .and(w1 -> w1.eq(Message::getSenderId, userId).eq(Message::getTargetId, friendId))
                    .or(w2 -> w2.eq(Message::getSenderId, friendId).eq(Message::getTargetId, userId))
                )
                .ge(Message::getCreatedAt, LocalDateTime.now().minusDays(Constants.HISTORY_RETENTION_DAYS))
                .orderByDesc(Message::getId)
                .last("LIMIT " + Math.min(count, 200));

        List<Message> records = messageMapper.selectList(wrapper);
        return toChronologicalMessageVOs(records);
    }

    @Override
    public List<MessageVO> getRecentGroupMessages(Long groupId, int count) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getMessageType, Constants.MSG_TYPE_GROUP)
                .eq(Message::getTargetId, groupId)
                .orderByDesc(Message::getId)
                .last("LIMIT " + Math.min(count, 100));
        List<Message> records = messageMapper.selectList(wrapper);
        return toChronologicalMessageVOs(records);
    }

    @Override
    public List<MessageVO> batchSaveMessages(Long senderId, List<com.chatroom.model.dto.ChatMessageDTO> dtos) {
        Map<String, Message> resolvedByClientMessageId = findExistingMessages(senderId, dtos);
        List<MessageVO> vos = new ArrayList<>();
        for (ChatMessageDTO dto : dtos) {
            String clientMessageId = normalizeClientMessageId(dto.getClientMessageId());
            if (clientMessageId != null && resolvedByClientMessageId.containsKey(clientMessageId)) {
                vos.add(toMessageVO(resolvedByClientMessageId.get(clientMessageId)));
                continue;
            }

            Message message = buildMessage(senderId, dto, clientMessageId);
            messageMapper.insert(message);
            if (!Boolean.TRUE.equals(dto.getSuppressOutbox())) {
                messageOutboxService.createChatMessageEvent(message.getId());
            }
            if (clientMessageId != null) {
                resolvedByClientMessageId.put(clientMessageId, message);
            }
            vos.add(toMessageVO(message));
        }
        return vos;
    }

    public MessageVO toMessageVO(Message msg) {
        List<MessageVO> vos = toMessageVOs(Collections.singletonList(msg), false);
        return vos.isEmpty() ? null : vos.get(0);
    }

    private String normalizeClientMessageId(String clientMessageId) {
        if (clientMessageId == null) {
            return null;
        }
        String normalized = clientMessageId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Message findExistingMessage(Long senderId, String clientMessageId) {
        return messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                .eq(Message::getSenderId, senderId)
                .eq(Message::getClientMessageId, clientMessageId)
                .last("LIMIT 1"));
    }

    private Map<String, Message> findExistingMessages(Long senderId, List<ChatMessageDTO> dtos) {
        Set<String> clientMessageIds = new HashSet<>();
        for (ChatMessageDTO dto : dtos) {
            String clientMessageId = normalizeClientMessageId(dto.getClientMessageId());
            if (clientMessageId != null) {
                clientMessageIds.add(clientMessageId);
            }
        }
        if (clientMessageIds.isEmpty()) {
            return new HashMap<>();
        }

        List<Message> existingMessages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getSenderId, senderId)
                .in(Message::getClientMessageId, clientMessageIds));
        Map<String, Message> result = new HashMap<>();
        for (Message existing : existingMessages) {
            result.put(existing.getClientMessageId(), existing);
        }
        return result;
    }

    private Message buildMessage(Long senderId, ChatMessageDTO dto, String clientMessageId) {
        Message msg = new Message();
        msg.setMessageType(dto.getMessageType());
        msg.setSenderId(senderId);
        msg.setTargetId(dto.getTargetId());
        msg.setReplyToId(dto.getReplyToId());
        msg.setContent(dto.getContent());
        msg.setContentType(dto.getContentType() != null ? dto.getContentType() : Constants.CONTENT_TYPE_TEXT);
        msg.setStatus(Constants.MSG_STATUS_SENT);
        msg.setClientMessageId(clientMessageId);
        msg.setCreatedAt(LocalDateTime.now());
        return msg;
    }

    private List<MessageVO> toChronologicalMessageVOs(List<Message> records) {
        return toMessageVOs(records, true);
    }

    private List<MessageVO> toMessageVOs(List<Message> messages, boolean chronologicalOrder) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> senderIds = new HashSet<>();
        Set<Long> replyIds = new HashSet<>();
        for (Message message : messages) {
            senderIds.add(message.getSenderId());
            if (message.getReplyToId() != null) {
                replyIds.add(message.getReplyToId());
            }
        }

        Map<Long, User> usersById = loadUsers(senderIds);
        Map<Long, Message> repliesById = loadMessages(replyIds);

        Set<Long> replySenderIds = new HashSet<>();
        for (Message replied : repliesById.values()) {
            if (replied.getSenderId() != null) {
                replySenderIds.add(replied.getSenderId());
            }
        }
        usersById.putAll(loadUsers(replySenderIds));

        List<MessageVO> vos = new ArrayList<>(messages.size());
        if (chronologicalOrder) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                vos.add(buildMessageVO(messages.get(i), usersById, repliesById));
            }
            return vos;
        }

        for (Message message : messages) {
            vos.add(buildMessageVO(message, usersById, repliesById));
        }
        return vos;
    }

    private Map<Long, User> loadUsers(Set<Long> userIds) {
        Map<Long, User> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        for (User user : users) {
            result.put(user.getId(), user);
        }
        return result;
    }

    private Map<Long, Message> loadMessages(Set<Long> messageIds) {
        Map<Long, Message> result = new HashMap<>();
        if (messageIds == null || messageIds.isEmpty()) {
            return result;
        }
        List<Message> messages = messageMapper.selectBatchIds(messageIds);
        for (Message message : messages) {
            result.put(message.getId(), message);
        }
        return result;
    }

    private MessageVO buildMessageVO(Message msg, Map<Long, User> usersById, Map<Long, Message> repliesById) {
        MessageVO vo = new MessageVO();
        vo.setId(msg.getId());
        vo.setMessageType(msg.getMessageType());
        vo.setSenderId(msg.getSenderId());
        vo.setTargetId(msg.getTargetId());
        vo.setReplyToId(msg.getReplyToId());
        vo.setContent(msg.getContent());
        vo.setContentType(msg.getContentType());
        vo.setStatus(msg.getStatus());
        vo.setClientMessageId(msg.getClientMessageId());
        vo.setCreatedAt(msg.getCreatedAt());

        User sender = usersById.get(msg.getSenderId());
        if (sender != null) {
            vo.setSenderName(sender.getNickname());
            vo.setSenderAvatar(sender.getAvatar());
        }

        if (msg.getReplyToId() != null) {
            Message replied = repliesById.get(msg.getReplyToId());
            if (replied != null) {
                vo.setReplyToContent(replied.getContent());
                User replySender = usersById.get(replied.getSenderId());
                if (replySender != null) {
                    vo.setReplyToSenderName(replySender.getNickname());
                }
            }
        }

        return vo;
    }
}
