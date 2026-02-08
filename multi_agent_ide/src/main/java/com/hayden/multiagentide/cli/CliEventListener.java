package com.hayden.multiagentide.cli;

import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;

public class CliEventListener implements EventListener {

    private final CliEventFormatter formatter;
    private final CliOutputWriter outputWriter;

    public CliEventListener(CliEventFormatter formatter, CliOutputWriter outputWriter) {
        this.formatter = formatter;
        this.outputWriter = outputWriter;
    }

    @Override
    public String listenerId() {
        return "cli-event-listener";
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        String rendered = formatter.format(event);
        outputWriter.println(rendered);
    }
}
