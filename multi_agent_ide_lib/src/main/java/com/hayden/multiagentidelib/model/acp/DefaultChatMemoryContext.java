package com.hayden.multiagentidelib.model.acp;

import com.hayden.acp_cdc_ai.acp.ChatMemoryContext;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultChatMemoryContext implements ChatMemoryContext {

    private final Map<Object, List<Message>> messageStore = new ConcurrentHashMap<>();

    @Override
    public List<Message> getMessages(Object memoryId) {
        if (memoryId == null) {
            return List.of();
        }
        return messageStore.getOrDefault(memoryId, List.of());
    }

    public void recordMessages(Object memoryId, List<Message> messages) {
        if (memoryId == null || messages == null || messages.isEmpty()) {
            return;
        }
        messageStore.put(memoryId, new ArrayList<>(messages));
    }
}
