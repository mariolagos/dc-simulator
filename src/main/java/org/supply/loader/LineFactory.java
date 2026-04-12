package org.supply.model;

public class LineFactory {

    public void build(Model model, Grid grid) {
        for (GridLine gLine : grid.getLines()) {

            Node from = model.getNode(gLine.getFromId());
            Node to   = model.getNode(gLine.getToId());

            if (from == null || to == null) {
                throw new IllegalStateException("Line refererar till okänd node");
            }

            Line line = new Line(from, to);
            line.setResistance(gLine.getResistance());

            model.addLine(line);
        }
    }
}