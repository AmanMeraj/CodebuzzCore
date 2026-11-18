package com.codebuzzers.codebuzzcore.lead;

import java.util.List;

public final class RoundRobinPicker {

    private RoundRobinPicker() {
    }

    public static Long next(List<Long> eligibleOrdered, Long lastAssigned) {
        if (eligibleOrdered == null || eligibleOrdered.isEmpty()) {
            return null;
        }
        if (lastAssigned == null) {
            return eligibleOrdered.get(0);
        }
        int index = eligibleOrdered.indexOf(lastAssigned);
        if (index < 0) {
            return eligibleOrdered.get(0);
        }
        return eligibleOrdered.get((index + 1) % eligibleOrdered.size());
    }
}
