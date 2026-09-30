package com.damso.match.domain.event.service;

import com.damso.match.api.admin.event.dto.MessageDto;
import com.damso.match.domain.event.entity.Message;
import com.damso.match.domain.event.repository.CustomerRepository;
import com.damso.match.domain.event.repository.MessageRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public Message send(Long eventId, Long senderId, Long receiverId, String content, String senderContact) {
        return messageRepository.save(Message.builder()
                .eventId(eventId)
                .senderId(senderId)
                .receiverId(receiverId)
                .content(content)
                .senderContact(senderContact)
                .build());
    }

    public List<MessageDto> listWithNicknames(Long eventId) {
        List<Message> messages = messageRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        Map<Long, String> nicknames = new HashMap<>();
        customerRepository.findByEventIdOrderByCreatedAtAsc(eventId)
                .forEach(c -> nicknames.put(c.getId(), c.getNickname()));

        return messages.stream().map(m -> new MessageDto(
                m.getId(),
                m.getSenderId(), nicknames.getOrDefault(m.getSenderId(), "?"),
                m.getReceiverId(), nicknames.getOrDefault(m.getReceiverId(), "?"),
                m.getContent(), m.getSenderContact(), m.getCreatedAt()
        )).toList();
    }

    public long count(Long eventId) {
        return messageRepository.findByEventIdOrderByCreatedAtDesc(eventId).size();
    }
}
