package com.hayden.multiagentidelib.agent;

import com.hayden.multiagentidelib.events.DegenerateLoopException;

import java.util.Optional;

public interface DegenerateLoopPolicy {

    Optional<DegenerateLoopException> detectLoop(BlackboardHistory history, String actionName, Object input);
}
