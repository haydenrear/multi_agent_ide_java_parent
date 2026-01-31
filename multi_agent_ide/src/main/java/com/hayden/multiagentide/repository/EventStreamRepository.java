package com.hayden.multiagentide.repository;

import com.hayden.acp_cdc_ai.acp.events.Events;

import java.util.List;

public interface EventStreamRepository {

    void save(Events.GraphEvent graphEvent);

    List<Events.GraphEvent> list();

}
