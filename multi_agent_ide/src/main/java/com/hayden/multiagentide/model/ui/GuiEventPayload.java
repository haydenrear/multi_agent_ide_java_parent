package com.hayden.multiagentide.model.ui;

public record GuiEventPayload(
        String renderer,
        String title,
        Object props,
        Object a2uiMessages,
        Object renderTree,
        String summary
) {

}
