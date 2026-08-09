package org.supply.app;

import com.typesafe.config.Config;
import org.supply.io.export.NetworkInputCsvWriter;
import org.supply.io.export.RunCsvWriter;
import org.supply.loader.DcSimConfigLoader;
import org.supply.loader.GridModelLoader;
import org.supply.model.GridModel;
import org.supply.track.LoadedTrackModel;
import org.supply.track.TrackConfigLoader;


import java.nio.file.Path;

public final class DcExporter {

    public static void main(String[] args) throws Exception {
        DcStudyContext context = DcStudyContextLoader.load(args[0]);
        run(context);
    }

    public static void run(DcStudyContext context) throws Exception {
        Config dcsim = context.dcsim();

        GridModel model =
                new GridModelLoader().load(dcsim);

        LoadedTrackModel trackModel =
                new TrackConfigLoader().load(dcsim);

        new NetworkInputCsvWriter().writeAll(
                dcsim,
                model,
                trackModel,
                context.exportDirectory()
        );

        new RunCsvWriter().write(
                dcsim,
                context.confFile(),
                context.exportDirectory()
        );

        // Temporary sanity output during integration
        System.out.println(
                "Loaded track sections: "
                        + trackModel.getSectionsById().keySet()
        );
        System.out.println(
                "Loaded track junctions: "
                        + trackModel.getJunctions().size()
        );
        System.out.println(
                "Loaded track stations: "
                        + trackModel.getStations().size()
        );
    }
}