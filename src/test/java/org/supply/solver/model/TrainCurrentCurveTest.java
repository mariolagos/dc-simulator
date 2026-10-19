package org.supply.solver.model;

import org.junit.Test;
import org.supply.domain.*;
import java.util.*;
import static org.junit.Assert.*;

public class TrainCurrentCurveTest {
    public static TrainCurrentCurve curve(double... currents) {
        double[] u = {400,500,560,600,640,900};
        List<TrainCurrentCurve.Point> points = new ArrayList<>();
        for (int i=0;i<u.length;i++) points.add(new TrainCurrentCurve.Point(u[i],currents[i]));
        return new TrainCurrentCurve(points);
    }
    public static TrainCurrentCurve single() {return curve(0,0,1200,3500,6000,6000);}
    public static TrainCurrentCurve coupled() {return curve(0,0,2400,7000,7000,7000);}
    @Test public void reproducesAllSuppliedBreakpointsAndMidpoints() {
        for (TrainCurrentCurve c:List.of(single(),curve(0,0,1200,3500,3500,3500),coupled())) {
            for(var p:c.points()) assertEquals(p.currentA(),c.currentLimitA(p.voltageV()),1e-9);
            for(int i=1;i<c.points().size();i++) {
                var a=c.points().get(i-1);var b=c.points().get(i);
                assertEquals((a.currentA()+b.currentA())/2,c.currentLimitA((a.voltageV()+b.voltageV())/2),1e-9);
            }
        }
    }
    @Test public void holdsEndpointsWithoutExtrapolating() {
        assertEquals(0,single().currentLimitA(0),0);
        assertEquals(0,single().currentLimitA(450),0);
        assertEquals(6000,single().currentLimitA(1000),0);
        assertEquals(7000,coupled().currentLimitA(1000),0);
    }
    @Test public void usesTotalCoupledTrainCurveWithoutScalingAgain() {
        SystemParameters p = new SystemParameters(750,500,600,900,6000,1e7,1e7);
        assertEquals(2350,new TrainLoadElement("S","F","R",1e7,580,p,null,single()).currentA(),1e-9);
        assertEquals(4700,new TrainLoadElement("D","F","R",1e7,580,p,null,coupled()).currentA(),1e-9);
        assertEquals(6000,new TrainLoadElement("S","F","R",1e7,750,p,null,single()).currentA(),1e-9);
        assertEquals(7000,new TrainLoadElement("D","F","R",1e7,750,p,null,coupled()).currentA(),1e-9);
        assertEquals(1000./750,new TrainLoadElement("D","F","R",1000,750,p,null,coupled()).currentA(),1e-9);
    }
    @Test public void rejectsInvalidPointsAndCopiesInput() {
        var a=new TrainCurrentCurve.Point(500,0);var b=new TrainCurrentCurve.Point(600,100);
        var list=new ArrayList<>(List.of(a,b));var c=new TrainCurrentCurve(list);list.clear();assertEquals(2,c.points().size());
        for(List<TrainCurrentCurve.Point> points:List.of(List.<TrainCurrentCurve.Point>of(),List.of(a),List.of(b,a),List.of(a,a),
                List.of(a,new TrainCurrentCurve.Point(Double.NaN,100)),List.of(a,new TrainCurrentCurve.Point(600,-1)),
                List.of(a,new TrainCurrentCurve.Point(600,Double.POSITIVE_INFINITY)),List.of(a,new TrainCurrentCurve.Point(600,0)))) {
            try{new TrainCurrentCurve(points);fail("Expected invalid points");}catch(IllegalArgumentException expected){}
        }
    }
}
