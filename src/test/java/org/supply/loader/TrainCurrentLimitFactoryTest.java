package org.supply.loader;

import com.typesafe.config.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TrainCurrentLimitFactoryTest {
    private Map<String,Double> resolve(String text) {
        return new TrainCurrentLimitFactory().build(ConfigFactory.parseString(text), 3000);
    }
    @Test public void resolvesHierarchyAndEveryRepetition() {
        Map<String,Double> limits = resolve("""
                templates { single {i_train_max_A=1500}, double {i_train_max_A=3200}, basic {} }
                timetable.trains = [
                  { id=S, template_id=single, count=2 },
                  { id=D, templateId=double, count=1 },
                  { id=X, template_id=single, count=2, i_train_max_A=4000 },
                  { id=F, template_id=basic, count=1 }
                ]
                """);
        assertEquals(Map.of("S-001",1500.,"S-002",1500.,"D",3200.,
                "X-001",4000.,"X-002",4000.,"F",3000.),limits);
    }
    @Test public void validatesAllSpecifiedLevelsEvenWhenOverridden() {
        for (String value : List.of("0", "-1", "\"NaN\"", "\"Infinity\"", "null")) {
            for (String text : List.of(
                    "templates { t { i_train_max_A="+value+" } }\ntimetable.trains=[{id=X,template_id=t,count=1,i_train_max_A=1000}]",
                    "templates {t {}}\ntimetable.trains=[{id=X,template_id=t,count=1,i_train_max_A="+value+"}]")) {
                try { resolve(text); fail("Expected invalid current limit: " + value); }
                catch (IllegalArgumentException | ConfigException expected) { }
            }
        }
    }
    @Test public void rejectsInvalidSystemDefault() {
        for (double value : List.of(0.,-1.,Double.NaN,Double.POSITIVE_INFINITY)) {
            try { new TrainCurrentLimitFactory().build(ConfigFactory.empty(),value); fail(); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void rejectsDuplicateExpandedIdentities() {
        try { resolve("templates {t {}}\ntimetable.trains=[{id=X,template_id=t,count=2},{id=X-001,template_id=t,count=1}]"); fail(); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Duplicate")); }
    }
}
