package com.hayden.multiagentidelib.prompt;

import java.util.List;
import java.util.Set;

public interface PromptContributorFactory {

    List<PromptContributor> create(PromptContext context);

    Set<PromptContributorDescriptor> descriptors();
}
