package org.supply.domain;

import java.util.List;
import java.util.Objects;

public record Route(
        String id,
        List<String> feedingLineIds,
        List<String> returnLineIds
) {
    public Route {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(feedingLineIds, "feedingLineIds");
        Objects.requireNonNull(returnLineIds, "returnLineIds");

        feedingLineIds = List.copyOf(feedingLineIds);
        returnLineIds = List.copyOf(returnLineIds);
    }
}