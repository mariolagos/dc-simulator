package org.supply.loader;
import org.junit.Test;
import com.typesafe.config.*;
import static org.junit.Assert.*;

public class TrainCurrentCurveFactoryTest {
    private TrainCurrentLimitFactory.Limits resolve(String text) {
        return new TrainCurrentLimitFactory().buildForStudy(ConfigFactory.parseString(text),6000);
    }
    @Test public void inheritsEntireCurveAndAllowsScalarToReplaceInheritedCurve() {
        var limits=resolve("""
            grid {i_train_max_A=6000, i_train_limit_curve=[[500,0],[600,5000]]}
            traffic {
              templates { own {i_train_limit_curve=[[500,0],[600,7000]]}, basic {} }
              timetable.trains=[{id=S,template_id=basic,count=1},
                {id=T,template_id=own,count=2},
                {id=X,template_id=own,count=1,i_train_max_A=3000},
                {id=C,template_id=own,count=1,i_train_limit_curve=[[500,0],[600,9000]]}]
            }
            """);
        assertEquals(5000.,limits.maxima().get("S"),0);
        assertEquals(7000.,limits.maxima().get("T-001"),0);
        assertSame(limits.curves().get("T-001"),limits.curves().get("T-002"));
        assertEquals(3000.,limits.maxima().get("X"),0);assertFalse(limits.curves().containsKey("X"));
        assertEquals(9000.,limits.maxima().get("C"),0);
        assertEquals(4500,limits.curves().get("C").currentLimitA(550),0);
    }
    @Test public void templateScalarReplacesSystemCurve() {
        var limits=resolve("grid {i_train_max_A=6000,i_train_limit_curve=[[500,0],[600,5000]]}\ntraffic {templates {t {i_train_max_A=2000}}\ntimetable.trains=[{id=T,template_id=t,count=1}]}");
        assertTrue(limits.curves().isEmpty());assertEquals(2000.,limits.maxima().get("T"),0);
    }
    @Test public void rejectsMalformedOrConflictingCurveSpecifications() {
        for(String value:java.util.List.of("[]","[[500,0]]","[[600,0],[500,100]]","[[500,0],[500,100]]","[[500,0],[600,-1]]","[[500,0],[600]]","[[500,0],[600,\"100\"]]","null")) {
            try{resolve("grid {i_train_max_A=6000}\ntraffic {templates {t {i_train_limit_curve="+value+"}}\ntimetable.trains=[{id=T,template_id=t,count=1}]}");fail(value);}
            catch(IllegalArgumentException|ConfigException expected){}
        }
        try{resolve("grid {i_train_max_A=6000}\ntraffic {templates {t {i_train_max_A=100,i_train_limit_curve=[[500,0],[600,100]]}}\ntimetable.trains=[{id=T,template_id=t,count=1}]}");fail();}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("not both"));}
    }
}
