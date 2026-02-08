package com.hayden.multiagentide.tui;

import java.util.List;

public record TuiChatSearch(
        boolean active,
        String query,
        List<Integer> resultIndices,
        int selectedResultIndex
) {
    public TuiChatSearch {
        if (resultIndices == null) {
            resultIndices = List.of();
        }
    }

    public static TuiChatSearch inactive() {
        return new TuiChatSearch(false, "", List.of(), -1);
    }
}
