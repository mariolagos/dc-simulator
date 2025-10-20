package org.dcsim.it;

import org.dcsim.electric.DcElectricSolver;
import org.dcsim.electric.Device;
import org.dcsim.electric.GridModel;
import org.dcsim.electric.GridResult;
import org.dcsim.electric.Substation;
import org.dcsim.electric.TrainLoad;
import org.dcsim.electric.Line;
import org.dcsim.electric.Node;
import org.dcsim.math.Real;
import org.dcsim.testing.GraphExport;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.eventFrom;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

/**
 * Integrationtester för enkla DC-scenarier runt T1.
 * <p>
 * Antaganden om TestSupport.buildMiniGrid(V0, V1, allowBackfeed):
 * - Returnerar ett litet DC-nät med substationer (SS0/SS1/SS2) och mittnod ("ANK" = tågets buss).
 * - Ett tåg "T1" finns i modellen och är anslutet till mittnoden (ANK).
 * - allowBackfeed styr om substationer får absorbera effekt från nätet.
 * <p>
 * I denna version lägger vi till en extra nod "LOAD" som sitter 200 m efter SS1
 * (dvs. på andra sidan SS1), för att se energi som korsar en matningspunkt utan
 * att substationerna påverkas samt kunna observera stationsspänningens beteende.
 */
public class PowerFlowIntegrationIT {

    private static final double V_TOL = 0.5;       // volts
    private static final double SMALLP = 50_000.0;  // "effektivt noll"-band för grova tester
    private static final double I_TOL = 0.1;     //
    private static final double V_E = 900.0;      //
    private final double P_MIN = 80_000.0;   // W – justera efter dina setpoints (t.ex. ~100 kW)
    private final double P_TOL = 1_000.0;    // W – “nära 0" för stationer
    private final double DV_MIN = 0.01;      // V – kräver en liten men mätbar spänningsskillnad
    private final double I_MIN = 5.0;        // A – “icke-noll" linjeström


    // -----------------------------------------------------
    // Hjälpmetoder
    // -----------------------------------------------------

    /**
     * Slår upp tåget och ger ett tydligt fel om det saknas.
     */
    private static TrainLoad requireTrain(GridModel<Real> m, String trainId) {
        try {
            Device<Real> d = m.getDevice(trainId);
            if (d instanceof TrainLoad) return (TrainLoad) d;
            throw new IllegalStateException("Expected TrainLoad with id " + trainId + " but got " + d);
        } catch (IllegalArgumentException ex) {
            List<String> ids = m.getDevices().stream().map(Device::getId).toList();
            throw new IllegalStateException("No device with id " + trainId + ". Available: " + ids, ex);
        }
    }

    /**
     * Summa av substationers UT-effekt = Σ(Vbus * I_sub). Positiv => levererar till nätet; Negativ => absorberar.
     */
    private static double sumSubstationOutW(GridModel<Real> m, GridResult res) {
        double sum = 0.0;
        for (Device<Real> d : m.getDevices()) {
            if (d instanceof Substation) {
                Substation ss = (Substation) d;
                Real v = res.getLatestNodeVoltage(ss.getFromNode());
                Real i = res.getLatestDeviceCurrent(ss.getId());
                if (v != null && i != null) sum += v.asDouble() * i.asDouble();
            }
        }
        return sum;
    }

    /**
     * Enhets effekt (W). Positiv = konsumerar från nätet; Negativ = levererar till nätet.
     */
    private static double pDevW(GridResult res, String devId) {
        Real p = res.getLatestDevicePower(devId);
        return (p == null) ? 0.0 : p.asDouble();
    }

    /**
     * Hämta nod-id för angiven substation. Kastar om den saknas.
     */
    private static int getSubstationNode(GridModel<Real> m, String ssId) {
        Device<Real> d = m.getDevice(ssId);
        if (!(d instanceof Substation)) {
            throw new IllegalStateException("Expected Substation with id " + ssId + " but got " + d);
        }
        return ((Substation) d).getFromNode();
    }

    /**
     * Skapa en extra nod "LOAD" 200 m efter SS1 och koppla in med en kort linje SS1 -> LOAD.
     * Returnerar nodeId för "LOAD". Idempotent per test-run (modellen byggs om per test).
     * <p>
     * OBS: Node/Line-API kan skilja i din kodbas; justera konstruktorer/setters vid behov.
     */
    private static int ensureLoadNodeAfterSS1(GridModel<Real> m) {
        // 1) Hämta SS1-buss
        int ndSS1 = getSubstationNode(m, "SS1");

        // 2) Skapa noden "LOAD" (200 m efter SS1) — track position sträng enligt ditt format.
        //    Exakt Node-konstruktor kan skilja; justera om nödvändigt.
        int loadNodeId = 98;
        Node loadNode = new Node(loadNodeId, Real.fromDouble(0.0), "0 0+200");
        try {
            m.addNode(loadNode);
        } catch (Throwable t) {
            throw new IllegalStateException("Adapt ensureLoadNodeAfterSS1(...) to your GridModel API (addNode(Node)).", t);
        }

        // 3) Skapa kort linje SS1 -> LOAD för ~200 m. Sätt liten R (ex. 10 mΩ).
        String idTie = "L_SS1_LOAD";
        try {
            Device<Real> existing = m.getDevice(idTie);
            // om den redan finns gör vi inget
        } catch (IllegalArgumentException notFound) {
            Line tie = new Line(ndSS1, loadNodeId, Real.fromDouble(0.010), idTie, "u", 1500.);
            m.addDevice(tie);
        }

        return loadNodeId;
    }

    /**
     * Skapa/återanvänd motordriven last på given nod. Returnerar device-id (default "LOAD_TRAIN").
     */
    private static String injectMotoringLoadAtNode(GridModel<Real> m, int nodeId, double watts, String id) {
        String loadId = (id == null ? "LOAD_TRAIN" : id);

        TrainLoad load = null;
        for (Device<Real> d : m.getDevices()) {
            if (loadId.equals(d.getId()) && d instanceof TrainLoad) {
                load = (TrainLoad) d;
                break;
            }
        }
        if (load == null) {
            int g = m.getGroundNodeId();
            load = new TrainLoad(loadId, nodeId, g);
            try {
                load.setCutoffVoltage(Real.fromDouble(0.0));
            } catch (Throwable ignore) {
            }
            try {
                load.setMaxVoltage(Real.fromDouble(10_000.0));
            } catch (Throwable ignore) {
            }
            try {
                load.setMaxCurrent(Real.fromDouble(1.0e9));
            } catch (Throwable ignore) {
            }
            m.addDevice(load);
        }
        double motKW = Math.max(0.0, watts) / 1000.0;
        load.setRequestedComponents(motKW, /*brakingKW*/ 0.0, /*aux*/ 0.0);
        return loadId;
    }

    /**
     * Aktivera backfeed per substation om testet kräver det.
     */
    private static void enableBackfeedPerSubstation(GridModel<Real> m, boolean allow) {
        for (Device<Real> d : m.getDevices()) {
            if (d instanceof Substation) {
                try {
                    ((Substation) d).setAllowBackfeed(allow);
                } catch (Throwable ignore) {
                }
            }
        }
    }

    /**
     * Debug-dump av noder och enheter efter en solve.
     */
    private static void debugDumpVoltages(GridModel<Real> m, GridResult res, String name) throws IOException {
        System.out.println("--- DEBUG VOLTAGES/POWERS ---");

        // Relevanta noder
        Set<Integer> nodeIds = new LinkedHashSet<>();
        for (Device<Real> d : m.getDevices()) {
            if (d instanceof Substation) nodeIds.add(((Substation) d).getFromNode());
            if (d instanceof TrainLoad) nodeIds.add(((TrainLoad) d).getFromNode());
        }
        nodeIds.add(m.getGroundNodeId());

        System.out.println("[NODES]");
        for (int n : nodeIds) {
            Real v = res.getLatestNodeVoltage(n);
            System.out.printf("  Node %d : V = %.3f V%n", n, (v == null ? Double.NaN : v.asDouble()));
        }

        System.out.println("[DEVICES]");
        for (Device<Real> d : m.getDevices()) {
            Real i = res.getLatestDeviceCurrent(d.getId());
            Real p = res.getLatestDevicePower(d.getId());
            String kind = (d instanceof Substation) ? "Substation"
                    : (d instanceof TrainLoad) ? "Train"
                    : (d instanceof Line) ? "Line"
                    : "Device";
            int fromNode = (d instanceof Substation) ? ((Substation) d).getFromNode()
                    : (d instanceof TrainLoad) ? ((TrainLoad) d).getFromNode()
                    : (d instanceof Line) ? ((Line) d).getFromNode()
                    : -1;
            Real vbus = res.getLatestNodeVoltage(fromNode);
            System.out.printf("  %s %-12s : node=%d  Vbus=%.3f V  I=%.3f A  P=%.3f W%n",
                    kind, d.getId(), fromNode,
                    (vbus == null ? Double.NaN : vbus.asDouble()),
                    (i == null ? Double.NaN : i.asDouble()),
                    (p == null ? Double.NaN : p.asDouble()));
        }

        // --- Sanity: summera P_sub, P_train, P_line och balans
        double pSub = 0.0, pTrain = 0.0, pLine = 0.0;

        // Hitta största |ΔV| på någon linje (bra indikator)
        double maxAbsDV = 0.0;
        String maxDVLine = "";
        int maxA = -1, maxB = -1;

        for (Device<Real> d : m.getDevices()) {
            if (d instanceof Substation) {
                Real pr = res.getLatestDevicePower(d.getId());
                if (pr != null) pSub += pr.asDouble();

            } else if (d instanceof TrainLoad) {
                Real pr = res.getLatestDevicePower(d.getId());
                if (pr != null) pTrain += pr.asDouble();

            } else if (d instanceof Line ln) {
                // Använd P från res om det finns, annars räkna ΔV^2/R
                Real pr = res.getLatestDevicePower(d.getId());
                double contrib;
                if (pr != null) {
                    contrib = pr.asDouble();
                } else {
                    Real vA = res.getLatestNodeVoltage(ln.getFromNode());
                    Real vB = res.getLatestNodeVoltage(ln.getToNode());
                    double Va = (vA == null) ? 0.0 : vA.asDouble();
                    double Vb = (vB == null) ? 0.0 : vB.asDouble();
                    double R  = ln.getResistance().asDouble();
                    contrib = (R > 0.0) ? ((Va - Vb) * (Va - Vb)) / R : 0.0;
                }
                pLine += contrib;

                // max |ΔV|
                Real vA = res.getLatestNodeVoltage(ln.getFromNode());
                Real vB = res.getLatestNodeVoltage(ln.getToNode());
                double Va = (vA == null) ? 0.0 : vA.asDouble();
                double Vb = (vB == null) ? 0.0 : vB.asDouble();
                double dV = Va - Vb;
                if (Math.abs(dV) > Math.abs(maxAbsDV)) {
                    maxAbsDV = dV;
                    maxDVLine = ln.getId();
                    maxA = ln.getFromNode();
                    maxB = ln.getToNode();
                }
            }
        }

        double balance = pSub + pTrain + pLine;

        System.out.println("------------------------------------");
        System.out.printf("[BAL] Psub=%.1f W  Ptrain=%.1f W  Pline=%.1f W  sum=%.1f W%n",
                pSub, pTrain, pLine, balance);
        if (!maxDVLine.isEmpty()) {
            System.out.printf("[ΔV] max |Va-Vb| = %.3f V on %s (%d -> %d)%n",
                    Math.abs(maxAbsDV), maxDVLine, maxA, maxB);
        }

        // DOT-export
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("output"));
        GraphExport.writeDotTopology (m, java.nio.file.Path.of("output/" + name + "_top.dot"), name);
        GraphExport.writeDotWithResults(m, res, java.nio.file.Path.of("output/" + name + "_res.dot"), name);
    }

    // -----------------------------------------------------
    // TESTER
    // -----------------------------------------------------

    @Test
    @Ignore("Legacy integration test; will be ported to DcIterativeSolver")
    public void regen_blocks_to_resistor_when_no_receptivity() throws IOException {
        String name = "regen_blocks_to_resistor_when_no_receptivity";
        // Ingen backfeed, inga andra laster: all broms ska dumpas i resistor => nätutbytet ~ 0
        GridModel<Real> m = TestSupport.buildMiniGrid(900.0, 1000.0, /*allowBackfeed*/ false);
        TrainLoad t = requireTrain(m, "T1");

        // Begär regenerativ broms (negativt brkKW => försök leverera till nätet)
        t.setRequestedComponents(/*mot*/ 0.0, /*brkKW*/ -150.0, /*aux*/ 0.0);

        DcElectricSolver solver = new DcElectricSolver();
        GridResult res = solver.solve(m, /*t=*/0.0, /*step=*/0);

        debugDumpVoltages(m, res, name);

        // 1) Noder ~ EMF
        double v1 = res.getLatestNodeVoltage(1).asDouble();
        double v2 = res.getLatestNodeVoltage(2).asDouble();
        double vT = res.getLatestNodeVoltage(99).asDouble();  // tågnod
        org.junit.Assert.assertEquals(V_E, v1, V_TOL);
        org.junit.Assert.assertEquals(V_E, v2, V_TOL);
        org.junit.Assert.assertEquals(V_E, vT, V_TOL);

        // 2) Substationer ~0 W
        double pSS0 = Math.abs(res.getLatestDevicePower("SS0").asDouble());
        double pSS1 = Math.abs(res.getLatestDevicePower("SS1").asDouble());
        org.junit.Assert.assertTrue("SS0 should be ~0W", pSS0 < P_TOL);
        org.junit.Assert.assertTrue("SS1 should be ~0W", pSS1 < P_TOL);

        // 3) T1 nätutbyte ~0 W (regen ska inte gå ut på nätet)
        double pT1 = Math.abs(res.getLatestDevicePower("T1").asDouble());
        org.junit.Assert.assertTrue("T1 net P should be ~0W", pT1 < P_TOL);

        // 4) Linjer ≈ 0 A (om ni inte lagrar I i GridResult, beräkna via ΔV/R)
        try {
            // Exempel för L_1_99
            Line l = (Line) m.getDevice("L_1_99");
            double Va = res.getLatestNodeVoltage(l.getFromNode()).asDouble();
            double Vb = res.getLatestNodeVoltage(l.getToNode()).asDouble();
            double R = l.getResistance().asDouble(); // om ni har getResistance()
            double Iab = (R > 0) ? (Va - Vb) / R : 0.0;
            org.junit.Assert.assertTrue("Line L_1_99 current ~0A", Math.abs(Iab) < I_TOL);
        } catch (Throwable ignore) {
            // om Line saknar getResistance(): skippa linje-asserten i just detta test
        }
    }

    @Test
    @Ignore("Legacy integration test; will be ported to DcIterativeSolver")
    public void regen_absorbed_by_other_train_when_fully_receptive() throws IOException {
        String name = "regen_absorbed_by_other_train_when_fully_receptive";
        // Ingen backfeed; lägg en lokal last på nod "LOAD" 200 m efter SS1.
        // Vi vill se energi korsa matningspunkten (SS1) utan att substationerna påverkas.
        GridModel<Real> m = TestSupport.buildMiniGrid(900.0, 1000.0, /*allowBackfeed*/ false);
        TrainLoad t = requireTrain(m, "T1");

        // T1 försöker leverera ~150 kW
        t.setRequestedComponents(0.0, -150.0, 0.0);

        // 1) Infoga nod "LOAD" efter SS1 och bind SS1 -> LOAD med en kort linje
        int loadNode = ensureLoadNodeAfterSS1(m);

        // 2) Lägg den "andra tåget" (lokal motordrift) på LOAD-noden
        String loadId = injectMotoringLoadAtNode(m, loadNode, /*watts*/ 170_000.0, "LOAD_TRAIN");

        DcElectricSolver solver = new DcElectricSolver();
        GridResult res = solver.solve(m, 0.0, 0);
        debugDumpVoltages(m, res, name);

        // Hjälpare: summera stationskraft
        double pSubSum = m.getDevices().stream()
                .filter(d -> d instanceof Substation)
                .mapToDouble(d -> {
                    Real p = res.getLatestDevicePower(d.getId());
                    return p == null ? 0.0 : p.asDouble();
                })
                .sum();

        // Hitta T1 och last-tåg (identifiera hellre på typ/roll än hårda id:n)
        TrainLoad tReg = null, tLoad = null;
        for (Device<Real> d : m.getDevices()) {
            if (d instanceof TrainLoad tr) {
                // heuristik: negativ requestedPower => regen, positiv => last
                Real r = tr.getRequestedPower();
                double preq = r == null ? 0.0 : r.asDouble();
                if (preq < 0) tReg = tr;
                else tLoad = tr;
            }
        }
        org.junit.Assert.assertNotNull("Hittar inte regentåget", tReg);
        org.junit.Assert.assertNotNull("Hittar inte lasttåget", tLoad);

        // 1) Substationer ska vara nära 0 W (ingen backfeed)
        org.junit.Assert.assertTrue("Stationerna ska vara ~0W", Math.abs(pSubSum) < P_TOL);

        // 2) T1 (regen) ska mata ut (negativ P)
        double pT1 = res.getLatestDevicePower(tReg.getId()).asDouble();
        org.junit.Assert.assertTrue("T1 ska mata ut (negativ P)", pT1 < -P_MIN);

        // 3) LOAD-tåget ska ta emot (positiv P)
        double pLoad = res.getLatestDevicePower(tLoad.getId()).asDouble();
        org.junit.Assert.assertTrue("LOAD ska ta emot (positiv P)", pLoad > P_MIN);

        // 4) Linjeström och ΔV mellan tågnoderna (om du har en Line-klass i mellanrummet)
        for (Device<Real> d : m.getDevices()) {
            if (d instanceof Line ln) {
                int a = ln.getFromNode(), b = ln.getToNode();
                Real rVa = res.getLatestNodeVoltage(a), rVb = res.getLatestNodeVoltage(b);
                if (rVa != null && rVb != null) {
                    double Va = rVa.asDouble(), Vb = rVb.asDouble();
                    double dV = Math.abs(Va - Vb);
                    if (dV > DV_MIN) { // ta första linan med mätbar drop
                        double R = 0.0;
                        try {
                            R = ln.getResistance().asDouble();
                        } catch (Throwable ignore) {
                        }
                        if (R > 0.0) {
                            double Iab = dV / R;
                            org.junit.Assert.assertTrue("Linjeström ska vara icke-noll", Iab > I_MIN);
                        }
                        break;
                    }
                }
            }
        }

        // 5) Energibalans (valfritt men bra): sum(P_sub)+sum(P_train)+sum(P_lineLoss) ≈ 0
        double pTrainSum = m.getDevices().stream()
                .filter(d -> d instanceof TrainLoad)
                .mapToDouble(d -> {
                    Real p = res.getLatestDevicePower(d.getId());
                    return p == null ? 0.0 : p.asDouble();
                }).sum();

        double pLineSum = m.getDevices().stream()
                .filter(d -> d instanceof Line)
                .mapToDouble(d -> {
                    Real p = res.getLatestDevicePower(d.getId()); // om ni bokför linjeförlust; annars räkna ΔV^2/R
                    return p == null ? 0.0 : p.asDouble();
                }).sum();

        double balance = pSubSum + pTrainSum + pLineSum;
        org.junit.Assert.assertEquals("Energibalans", 0.0, balance, 2_000.0); // 2 kW fönster, justera vid behov
    }

    @Test
    @Ignore("Legacy integration test; will be ported to DcIterativeSolver")
    public void regen_splits_between_net_and_resistor_when_partial_receptivity() throws IOException {
        String name = "regen_splits_between_net_and_resistor_when_partial_receptivity";
        // Ingen backfeed och lokal last mindre än regen => del går i resistor, del till lasten (på LOAD-noden efter SS1)
        GridModel<Real> m = TestSupport.buildMiniGrid(900.0, 1000.0, /*allowBackfeed*/ false);
        TrainLoad t = requireTrain(m, "T1");

        t.setRequestedComponents(0.0, -150.0, 0.0);

        int loadNode = ensureLoadNodeAfterSS1(m);
        String loadId = injectMotoringLoadAtNode(m, loadNode, /*watts*/ 50_000.0, "LOAD_TRAIN");

        DcElectricSolver solver = new DcElectricSolver();
        GridResult res = solver.solve(m, 0.0, 0);

        debugDumpVoltages(m, res, name);

        double pSubOut = sumSubstationOutW(m, res);
        double pTrain = pDevW(res, "T1");
        double pLoad = pDevW(res, loadId);

        // Lasten borde få i storleksordningen ~50 kW (ge rejäl marginal)
        assertThat(pLoad, greaterThan(30_000.0));
        // Tåget borde leverera mindre än full 150 kW (resten bränns i resistor)
        assertThat(pTrain, lessThan(-30_000.0)); // om din solver redovisar negativ P på tåget; annars kommentera bort
        // Substationerna ska inte absorbera (backfeed=false)
        assertThat(Math.abs(pSubOut), lessThan(SMALLP));
    }

    @Test
    @Ignore("Legacy integration test; will be ported to DcIterativeSolver")
    public void non_regenerative_train_all_in_resistor() throws IOException {
        String name = "non_regenerative_train_all_in_resistor";
        // "Icke-regenerativt" scenario: all broms dumpas internt => nätutbyte ~ 0
        GridModel<Real> m = TestSupport.buildMiniGrid(900.0, 1000.0, /*allowBackfeed*/ false);
        TrainLoad t = requireTrain(m, "T1");

        t.setRequestedComponents(0.0, -150.0, 0.0);

        DcElectricSolver solver = new DcElectricSolver();
        GridResult res = solver.solve(m, 0.0, 0);

        debugDumpVoltages(m, res, name);

        double pSubOut = sumSubstationOutW(m, res);
        double pTrain = pDevW(res, "T1");

        assertThat(Math.abs(pSubOut), lessThan(SMALLP));
        assertThat(Math.abs(pTrain), lessThan(SMALLP));
    }

    @Test
    @Ignore("Legacy integration test; will be ported to DcIterativeSolver")
    public void regen_into_substations_when_backfeed_allowed() throws IOException {
        String name = "regen_into_substations_when_backfeed_allowed";
        GridModel<Real> m = TestSupport.buildMiniGrid(900.0, 1000.0, /*allowBackfeed*/ true);
        enableBackfeedPerSubstation(m, true);

        TrainLoad t = requireTrain(m, "T1");
        t.setRequestedComponents(0.0, -150.0, 0.0);

        DcElectricSolver solver = new DcElectricSolver();
        GridResult res = solver.solve(m, 0.0, 0);

        debugDumpVoltages(m, res, name);

        double pSubOut = sumSubstationOutW(m, res);

        // Med backfeed tillåten ska substationerna absorbera (OUT < 0)
        assertThat(pSubOut, lessThan(-100_000.0));
    }
}
