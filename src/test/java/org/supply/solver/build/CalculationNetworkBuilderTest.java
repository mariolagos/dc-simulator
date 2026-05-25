package org.supply.solver.build;

import org.junit.Test;
import org.supply.domain.Line;
import org.supply.domain.Node;
import org.supply.math.Real;
import org.supply.model.GridModel;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationNodeType;
import org.supply.track.*;

import java.util.List;

import static org.junit.Assert.*;

public class CalculationNetworkBuilderTest {

    @Test
    public void buildBaseCreatesGridNodesAndBranches() {
        Node a = new Node("A", "1 0+0");
        Node b = new Node("B", "1 1+000");

        Line line = new Line(a, b, Real.fromDouble(0.01));

        GridModel grid = new GridModel();

        grid.addNode(a);
        grid.addNode(b);

        grid.addLine(line);
        TrackTransformService transform = new FakeTrackTransformService();

        CalculationNetwork network =
                new CalculationNetworkBuilder(transform).buildBase(grid);

        assertEquals(2, network.nodes().size());
        assertEquals(1, network.branches().size());

        assertEquals("A", network.nodes().get(0).id());
        assertEquals("A", network.nodes().get(0).sourceId());
        assertEquals("1", network.nodes().get(0).sectionId());
        assertEquals("SINGLE", network.nodes().get(0).trackId());
        assertEquals(0.0, network.nodes().get(0).positionM(), 1e-9);
        assertEquals(CalculationNodeType.GRID_NODE, network.nodes().get(0).type());

        assertEquals("B", network.nodes().get(1).id());
        assertEquals(1000.0, network.nodes().get(1).positionM(), 1e-9);

        assertEquals("A", network.branches().get(0).fromNodeId());
        assertEquals("B", network.branches().get(0).toNodeId());
        assertEquals(10.0, network.branches().get(0).resistanceOhm().asDouble(), 1e-9);
    }

    private static final class FakeTrackTransformService implements TrackTransformService {

        @Override
        public ModelCoordinate toModel(String routeId, RwyCoordinate railwayCoordinate) {
            return new ModelCoordinate(
                    railwayCoordinate.getSectionId(),
                    "SINGLE",
                    railwayCoordinate.getPositionM()
            );
        }

        @Override
        public RwyCoordinate toRailway(String routeId, ModelCoordinate modelCoordinate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelCoordinate pathToModel(String routeId, double pathPositionM) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RwyCoordinate pathToRailway(String routeId, double pathPositionM) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double distanceOnRoute(String routeId, RwyCoordinate from, RwyCoordinate to) {
            throw new UnsupportedOperationException();
        }
    }
}