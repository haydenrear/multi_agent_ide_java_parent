package com.hayden.multiagentidelib.prompt;

import java.util.List;

public interface PromptContributorFactory {

    List<PromptContributor> create(PromptContext context);
}
