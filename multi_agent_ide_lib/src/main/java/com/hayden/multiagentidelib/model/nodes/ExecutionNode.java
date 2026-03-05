package com.hayden.multiagentidelib.model.nodes;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface ExecutionNode {

    @JsonIgnore
    String agent();

}
