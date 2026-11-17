package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class RunCsvInputFactoryRegimeTest {
    private static Config timetable(String body) {
        return ConfigFactory.parseString(body);
    }

    @Test public void absentRegimeIsAllowed() {
        RunCsvInputFactory.validateRegime(timetable("trains=[]"));
    }

    @Test public void validRegimeIsAllowed() {
        RunCsvInputFactory.validateRegime(timetable("regime { id=WE_peak, duration=\"02:00:00\" }"));
    }

    @Test public void blankIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RunCsvInputFactory.validateRegime(
                timetable("regime { id=\"  \", duration=\"02:00:00\" }")));
    }

    @Test public void missingDurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RunCsvInputFactory.validateRegime(
                timetable("regime { id=WE_peak }")));
    }

    @Test public void zeroDurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RunCsvInputFactory.validateRegime(
                timetable("regime { id=WE_peak, duration=\"00:00:00\" }")));
    }

    @Test public void invalidMinuteIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RunCsvInputFactory.validateRegime(
                timetable("regime { id=WE_peak, duration=\"00:60:00\" }")));
    }
}
