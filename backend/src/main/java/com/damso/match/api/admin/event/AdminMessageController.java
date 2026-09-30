package com.damso.match.api.admin.event;

import com.damso.match.api.admin.event.dto.MessageDto;
import com.damso.match.domain.event.entity.Message;
import com.damso.match.domain.event.service.MessageService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/messages")
@RequiredArgsConstructor
public class AdminMessageController {

    private final MessageService messageService;

    @GetMapping
    public List<MessageDto> list(@RequestParam Long eventId) {
        return messageService.listWithNicknames(eventId);
    }

    @PostMapping
    public Message send(@RequestBody SendRequest req) {
        return messageService.send(req.eventId, req.senderId, req.receiverId, req.content, req.senderContact);
    }

    public static class SendRequest {
        public Long eventId;
        public Long senderId;
        public Long receiverId;
        public String content;
        public String senderContact;
    }
}
