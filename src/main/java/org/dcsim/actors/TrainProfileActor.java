package org.dcsim.actors;

import akka.actor.AbstractActor;
import akka.actor.Props;
import com.typesafe.config.Config;

import java.util.List;

/**
 * TrainProfileActor
 * - Reads dcsim.traffic (timetable + templates) and dcsim.powerProfiles.
 * - Logs a clear summary at startup so we can verify config content.
 * - Does not depend on any project-specific DTOs or loaders.
 */
public class TrainProfileActor extends AbstractActor {

    private final Config rootConfig;

    public static Props props(Config rootConfig) {
        return Props.create(TrainProfileActor.class, () -> new TrainProfileActor(rootConfig));
    }

    public TrainProfileActor(Config rootConfig) {
        this.rootConfig = rootConfig;
    }

    @Override
    public void preStart() {
        var log = getContext().getSystem().log();
        log.info("TrainProfileActor starting...");

        // Normalize to the dcsim block
        final Config dcsim = rootConfig.hasPath("dcsim") ? rootConfig.getConfig("dcsim") : rootConfig;

        // -----------------------------
        // 1) traffic.timetable.trains
        // -----------------------------
        if (dcsim.hasPath("traffic.timetable.trains")) {
            List<? extends Config> trains =
                    dcsim.getConfig("traffic").getConfig("timetable").getConfigList("trains");
            log.info("Found {} trains in dcsim.traffic.timetable.trains", Integer.valueOf(trains.size()));
            for (Config tr : trains) {
                String id         = tr.hasPath("id") ? tr.getString("id") : "<missing id>";
                String templateId = tr.hasPath("templateId") ? tr.getString("templateId") : "<missing templateId>";
                String departure  = tr.hasPath("departure") ? tr.getString("departure") : "<missing departure>";
                String headway    = tr.hasPath("headway") ? tr.getString("headway") : "<none>";
                int count         = tr.hasPath("count") ? tr.getInt("count") : 1;
                String signature  = tr.hasPath("signature") ? tr.getString("signature") : "<none>";

                log.info("Train: id={}, templateId={}, departure={}, headway={}, count={}, signature={}",
                        new Object[]{id, templateId, departure, headway, Integer.valueOf(count), signature});
            }
        } else {
            log.warning("No trains found at dcsim.traffic.timetable.trains");
        }

        // -----------------------------
        // 2) traffic.templates
        // -----------------------------
        if (dcsim.hasPath("traffic.templates")) {
            Config templates = dcsim.getConfig("traffic").getConfig("templates");
            var keys = templates.root().keySet();
            log.info("Found {} templates in dcsim.traffic.templates", Integer.valueOf(keys.size()));
            for (String key : keys) {
                Config tpl = templates.getConfig(key);
                List<? extends Config> stops = tpl.hasPath("stops") ? tpl.getConfigList("stops") : List.of();
                log.info("Template {} has {} stops", key, Integer.valueOf(stops.size()));
                for (Config st : stops) {
                    String sig = st.hasPath("signature") ? st.getString("signature") : "<no-signature>";
                    String arr = st.hasPath("arrival") ? st.getString("arrival") : "";
                    String dep = st.hasPath("departure") ? st.getString("departure") : "";
                    log.info("  stop: signature={}, arrival={}, departure={}", new Object[]{sig, arr, dep});
                }
            }
        } else {
            log.warning("No templates found at dcsim.traffic.templates");
        }

        // -----------------------------
        // 3) powerProfiles (summary)
        // -----------------------------
        if (dcsim.hasPath("powerProfiles")) {
            Config pp = dcsim.getConfig("powerProfiles");
            double aux = pp.hasPath("auxiliaryPower") ? pp.getDouble("auxiliaryPower") : 0.0;
            boolean sameModel = pp.hasPath("motoringAndAuxiliariesInSameModel")
                    && pp.getBoolean("motoringAndAuxiliariesInSameModel");
            log.info("powerProfiles: auxiliaryPower={} kW, motoringAndAuxiliariesInSameModel={}",
                    new Object[]{Double.valueOf(aux), Boolean.valueOf(sameModel)});

            if (pp.hasPath("templates")) {
                List<? extends Config> tpls = pp.getConfigList("templates");
                log.info("powerProfiles.templates size={}", Integer.valueOf(tpls.size()));
                for (Config t : tpls) {
                    String id = t.hasPath("id") ? t.getString("id") : "<no-id>";
                    String folder = t.hasPath("folder") ? t.getString("folder") : "<no-folder>";
                    int legs = t.hasPath("legs") ? t.getConfigList("legs").size() : 0;
                    log.info("  template id={} folder={} legs={}", new Object[]{id, folder, Integer.valueOf(legs)});
                }
            } else {
                log.warning("No powerProfiles.templates found");
            }
        } else {
            log.warning("No dcsim.powerProfiles found");
        }

        log.info("TrainProfileActor finished config scan.");
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchAny(msg -> getContext().getSystem().log().debug("TrainProfileActor received: {}", msg))
                .build();
    }
}
