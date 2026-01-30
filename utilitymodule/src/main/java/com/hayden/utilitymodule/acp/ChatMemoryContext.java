package com.hayden.utilitymodule.acp;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface ChatMemoryContext {

    List<Message> getMessages(Object memoryId);
}
