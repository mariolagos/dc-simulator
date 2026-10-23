package org.supply.loader;

import com.typesafe.config.*;
import org.junit.Test;
import org.supply.domain.Route;
import org.supply.model.GridModel;
import java.util.*;
import static org.junit.Assert.*;

public class BranchRouteGeneratorTest {
    private Config input() { return ConfigFactory.parseResources("branch-routes/we.conf").resolve().getConfig("dcsim.grid"); }
    private GridModel model(Config input) {
        var generated=BranchGridGenerator.generate(input);
        return new GridModelLoader().load(ConfigFactory.empty().withValue("grid",generated.root()));
    }
    private Map<String,Object> routes(Config input) { return BranchRouteGenerator.generate(input,model(input)); }
    private Route route(Config input,String id) {
        var config=ConfigFactory.empty().withValue("routes",ConfigValueFactory.fromMap(routes(input)));
        return new RouteFactory().build(config).stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();
    }
    private List<String> withRole(List<String> base,String role) { return base.stream().map(s->s+"."+role).toList(); }
    @Test public void generatesAllFourExpectedWeRoutesIncludingJunctions() {
        var westEast=List.of("B__101__1","B__101__2","B__101__3","J__J1__1","B__200__1","B__200__2","B__200__3","J__J2__1","B__102__1","B__102__2","B__102__3");
        var nwSe=List.of("B__201__1","J__J1__2","B__200__1","B__200__2","B__200__3","J__J2__2","B__202__1");
        assertEquals(4,routes(input()).size());
        assertEquals(withRole(westEast,"N.F"),route(input(),"WE").feedingLineIds());
        assertEquals(withRole(westEast,"N.R"),route(input(),"WE").returnLineIds());
        var ew=new ArrayList<>(westEast);Collections.reverse(ew);
        assertEquals(withRole(ew,"S.F"),route(input(),"EW").feedingLineIds());
        assertEquals(withRole(ew,"S.R"),route(input(),"EW").returnLineIds());
        assertEquals(withRole(nwSe,"N.F"),route(input(),"NwSe").feedingLineIds());
        assertEquals(withRole(nwSe,"N.R"),route(input(),"NwSe").returnLineIds());
        var reverse=new ArrayList<>(nwSe);Collections.reverse(reverse);
        assertEquals(withRole(reverse,"S.F"),route(input(),"SeNw").feedingLineIds());
        assertEquals(withRole(reverse,"S.R"),route(input(),"SeNw").returnLineIds());
    }
    @Test public void junctionStarTraversalFindsTwoLinksWhenNeitherSectionIsHub() {
        var conf=input().withValue("routeDefinitions",ConfigFactory.parseString("X {branches=[\"101\",\"-201\"],track=N}").root());
        var ids=route(conf,"X").feedingLineIds();
        assertEquals(List.of("B__101__1.N.F","B__101__2.N.F","B__101__3.N.F","J__J1__1.N.F","J__J1__2.N.F","B__201__1.N.F"),ids);
    }
    @Test public void reverseBranchDirectionIsIndependentOfReversedStationOrientation() {
        var conf=input().withValue("routeDefinitions",ConfigFactory.parseString("X {branches=[\"-101\"],track=N}").root());
        assertEquals(List.of("B__101__3.N.F","B__101__2.N.F","B__101__1.N.F"),route(conf,"X").feedingLineIds());
    }
    @Test public void rejectsUnknownBranchAndInvalidTrack() {
        assertThrows(IllegalArgumentException.class,()->routes(input().withValue("routeDefinitions",ConfigFactory.parseString("X {branches=[missing],track=N}").root())));
        assertThrows(IllegalArgumentException.class,()->routes(input().withValue("routeDefinitions",ConfigFactory.parseString("X {branches=[\"101\"],track=U}").root())));
    }
    @Test public void rejectsDisconnectedOrIncorrectlyDirectedBranchSequences() {
        for(String chain:List.of("[\"101\",\"102\"]","[\"101\",\"-200\"]","[\"-101\",\"200\"]")) {
            var conf=input().withValue("routeDefinitions",ConfigFactory.parseString("X {branches="+chain+",track=N}").root());
            assertThrows(IllegalArgumentException.class,()->routes(conf));
        }
    }
    @Test public void rejectsEmptyAndRepeatedRoutes() {
        for(String definition:List.of("{}","{X {branches=[],track=N}}","{X {branches=[\"101\",\"-101\"],track=N}}")) {
            var conf=input().withValue("routeDefinitions",ConfigFactory.parseString(definition).root());
            assertThrows(IllegalArgumentException.class,()->routes(conf));
        }
    }
    @Test public void supportsNamedBranchesWithExplicitSectionAndObjects() {
        var conf=input().withValue("branches.west",ConfigFactory.parseString("section=\"101\"\nobjects=[P1,\"-SS0\",SS1,J1]").root()).withoutPath("branches.101")
                .withValue("routeDefinitions",ConfigFactory.parseString("X {branches=[west,\"200\",\"102\"],track=N}").root());
        assertEquals("B__west__1.N.F",route(conf,"X").feedingLineIds().get(0));
    }
    @Test public void branchOnlyInputRemainsSupportedAndMetadataDoesNotLeakIntoGrid() {
        assertTrue(routes(input().withoutPath("routeDefinitions")).isEmpty());
        var generated=BranchGridGenerator.generate(input());
        assertFalse(generated.hasPath("routeDefinitions"));assertFalse(generated.hasPath("branches"));
    }
    @Test public void rejectsUnknownRouteFields() {
        var conf=input().withValue("routeDefinitions",ConfigFactory.parseString("X {branches=[\"101\"],track=N,typo=true}").root());
        assertThrows(IllegalArgumentException.class,()->routes(conf));
    }
}
