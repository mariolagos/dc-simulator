package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.supply.model.GridModel;
import org.supply.validation.GridModelValidator;

public class GridModelLoader {

    private final NodeFactory nodeFactory = new NodeFactory();
    private final LineFactory lineFactory = new LineFactory();
    private final InstallationFactory installationFactory = new InstallationFactory();
    private final GridModelValidator validator = new GridModelValidator();

    public GridModel load(Config config) {
        Config grid = config.getConfig("grid");

        GridModel model = new GridModel();

        nodeFactory.build(model, grid);
        lineFactory.build(model, grid);
        installationFactory.build(model, grid);

        validator.validate(model);

        return model;
    }

    public static void main(String[] args) {
        Config config = ConfigFactory.parseString("""
            grid {
              nodes = [
                { node_id = "1", position_rwy = "23 0+000 U" },
                { node_id = "2", position_rwy = "23 1+200 U" },
                { node_id = "3", position_rwy = "23 1+200 D" }
              ]

              lines = [
                { node_from_id = "1", node_to_id = "2", resistance_ohm_per_m = 0.00012 }
              ]

              power_installations = [
                {
                  installation_id = "PS1"
                  installation_type = SUBSTATION
                  emf_V = 750.0
                  internal_resistance_ohm = 0.02
                  rectifier_type = DIODE
                },
                {
                  installation_id = "P1"
                  installation_type = POINT
                  rectifier_type = DIODE
                }
              ]

              installation_connections = [
                {
                  installation_id = "PS1"
                  node_id = "1"
                  connection_type = FEEDING
                },
                {
                  installation_id = "PS1"
                  node_id = "3"
                  connection_type = RETURN
                },
                {
                  installation_id = "P1"
                  node_id = "2"
                  connection_type = FEEDING
                }
              ]
            }
        """);

        GridModelLoader loader = new GridModelLoader();
        GridModel model = loader.load(config);

        System.out.println("Loaded nodes: " + model.getNodes().size());
        System.out.println("Loaded lines: " + model.getLines().size());
        System.out.println("Loaded installations: " + model.getInstallations().size());
        System.out.println("Loaded power connections: " + model.getInstallationConnections().size());

        model.getLines().forEach(line ->
                System.out.println(
                        line.getFromNode().getNodeId()
                                + " -> "
                                + line.getToNode().getNodeId()
                                + " R=" + line.getResistanceOhmPerM().asDouble()
                )
        );

        model.getInstallations().forEach(inst ->
                System.out.println(
                        inst.getInstallationId()
                                + " type=" + inst.getInstallationType()
                                + " emf=" + inst.getEmfV().asDouble()
                                + " Rint=" + inst.getInternalResistanceOhm().asDouble()
                                + " rectifier=" + inst.getRectifierType()
                )
        );

        model.getInstallationConnections().forEach(conn ->
                System.out.println(
                        conn.getInstallationId()
                                + " -> "
                                + conn.getNodeId()
                                + " (" + conn.getConnectionType() + ")"
                )
        );
    }
}