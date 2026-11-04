package org.supply.track;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Independent expected values for short and long kilometres; no customer data. */
public class IrregularKilometreGeometryTest {
    private static PathSample p(double path,int railway,String track) {
        return new PathSample(path,c(railway,track));
    }
    private static RwyCoordinate c(int railway,String track) {
        return new RwyCoordinate("X",RwyCoordinate.formatKmShort(railway),railway,track);
    }
    private static RouteView view(String id,PathSample... points) {
        return new RouteView(id,id,List.of(points));
    }
    @Test public void detailedShortKilometreRetainsMetresBeforeBoardLabelJump() {
        var route=view("reverse",p(0,3200,"S"),p(200,3000,"S"),p(201,2849,"S"),p(1050,2000,"S"),p(3050,0,"S"));
        var section=TrackSectionsFromRouteViews.build(Map.of("reverse",route)).get("X");
        var m=new TrackSectionMapper();
        assertEquals(2500,m.toModel(section,c(2500,"S")).getPositionM());
        assertEquals(2848,m.toModel(section,c(2848,"S")).getPositionM());
        assertEquals(2849,m.toModel(section,c(2849,"S")).getPositionM());
        assertEquals(2850,m.toModel(section,c(3000,"S")).getPositionM());
        assertEquals(3050,m.toModel(section,c(3200,"S")).getPositionM());
        // The former manual board interpolates across the missing labels,
        // compressing an 848 m offset to 721 m (the observed 127 m difference).
        var manual=new TrackSection("X",List.of(new RouteSegment(0,c(2000,null),c(3000,null),2000,850)));
        assertEquals(2721,m.toModel(manual,c(2848,"S")).getPositionM());
    }
    @Test public void longKilometreKeepsRecordedIntermediateDistances() {
        var route=view("forward",p(0,0,"N"),p(2000,2000,"N"),p(2850,3000,"N"),
                p(2950,3100,"N"),p(3051,3201,"N"),p(4000,4000,"N"));
        var section=TrackSectionsFromRouteViews.build(Map.of("forward",route)).get("X");
        var m=new TrackSectionMapper();
        assertEquals(2850,m.toModel(section,c(3000,"N")).getPositionM());
        assertEquals(2950,m.toModel(section,c(3100,"N")).getPositionM());
        assertEquals(3051,m.toModel(section,c(3201,"N")).getPositionM());
        assertEquals(4000,m.toModel(section,c(4000,"N")).getPositionM());
    }
    @Test public void intermediatePathPositionsRoundTripWithinOneMetre() {
        var route=view("forward",p(0,0,"N"),p(2000,2000,"N"),p(2850,3000,"N"),
                p(2950,3100,"N"),p(3051,3201,"N"),p(4000,4000,"N"));
        var section=TrackSectionsFromRouteViews.build(Map.of("forward",route)).get("X");
        var mapper=new TrackSectionMapper();var pathMapper=new RouteViewMapper();
        for(int i=0;i<=16000;i++) {
            double path=i/4.0;
            int model=mapper.toModel(section,pathMapper.toRailway(route,path)).getPositionM();
            assertTrue("path="+path+", model="+model,Math.abs(model-path)<=1.0);
        }
    }
}
