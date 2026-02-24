package org.dcsim.validation;

import java.util.List;
import java.util.Map;

public final class RunDomainValidator {

    public void validatePositions(List<Map<String, String>> runRows, double trackLength) {
        int rowIndex = 0;
        for (Map<String, String> r : runRows) {
            rowIndex++;
            String trackId = r.get("track_id");
            double pos = Double.parseDouble(r.get("position_m"));

            if (!(pos >= 0.0 && pos <= trackLength)) {
                throw new ValidationInputException("run.csv invalid position at row="
                        + rowIndex + ": track=" + trackId + " pos=" + pos + " allowed=[0.0, " + trackLength + "]");
            }
        }
    }
}
