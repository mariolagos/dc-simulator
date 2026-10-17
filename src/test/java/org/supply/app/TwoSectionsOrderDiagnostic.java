package org.supply.app;

import com.typesafe.config.*;
import org.supply.domain.*;
import org.supply.loader.*;
import org.supply.math.Real;
import org.supply.solver.build.*;
import org.supply.solver.electrical.*;
import org.supply.solver.model.*;
import org.supply.track.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Replays the supplied failing prefix; never changes solver or study configuration. */
public final class TwoSectionsOrderDiagnostic {
    private static final String ROOT = "two-sections-order/";
    private record Infra(CalculationNetwork base, SystemParameters parameters, List<Route> routes) { }
    private record Sample(double time, CalculationTrainPosition train, double oldU, double newU) { }
    private record Outcome(Map<String,Real> previous, Map<String,Double> trainVoltages) { }
    private static Map<String,String> nodeNames;
    private static Map<String,String> lineNames;
    private TwoSectionsOrderDiagnostic() { }

    private static Infra infrastructure(String name) {
        Config input = CompactGridExpander.expand(ConfigFactory.parseResources(ROOT + name + ".conf").getConfig("dcsim"));
        var grid = new GridModelLoader().load(input);
        List<KilometerBoard> boards = new ArrayList<>();
        for (Config b : input.getConfigList("track.kilometer_boards")) {
            String km = b.getString("km"); String[] parts = km.split("\\+");
            int metres = Integer.parseInt(parts[0]) * 1000 + Integer.parseInt(parts[1]);
            boards.add(new KilometerBoard(b.getString("section"), km, metres, (int)b.getDouble("length")));
        }
        List<TrackJunction> junctions = new ArrayList<>();
        if (input.hasPath("track.junctions")) {
            for (Config junction : input.getConfigList("track.junctions")) {
                junctions.add(new TrackJunction(
                        RwyCoordinateParser.parse(junction.getString("from_position_rwy")),
                        RwyCoordinateParser.parse(junction.getString("to_position_rwy"))));
            }
        }
        var transform = new DefaultTrackTransformService(new TrackLoader().load(boards, junctions, List.of()));
        var g = input.getConfig("grid");
        var parameters = new SystemParameters(g.getDouble("u_nominal_V"), g.getDouble("u_min_V"),
                g.getDouble("u_cutoff_V"), g.getDouble("u_max_V"), g.getDouble("i_train_max_A"),
                g.getDouble("p_train_max_W"), g.getDouble("p_allow_W"), g.getString("reference_node_id"));
        return new Infra(new CalculationNetworkBuilder(transform).buildBase(grid), parameters, new RouteFactory().build(input.getConfig("traffic")));
    }
    private static Map<String,String> stringMap(Config config) {
        Map<String,String> out = new HashMap<>(); config.root().unwrapped().forEach((k,v) -> out.put(k,(String)v)); return out;
    }
    /** Generated boundary names include a branch index; compare their actual source and geometry. */
    private static void mapGeneratedBoundaryNodes(CalculationNetwork old, CalculationNetwork newer) {
        Set<String> mappedOldIds = new HashSet<>(nodeNames.values());
        Set<String> explicitOldIds = new HashSet<>();
        for (var n : old.nodes()) explicitOldIds.add(n.id());
        for (var n : newer.nodes()) {
            if (nodeNames.containsKey(n.id()) || explicitOldIds.contains(n.id())) continue;
            var candidates = old.nodes().stream().filter(expected ->
                    !mappedOldIds.contains(expected.id())
                            && Objects.equals(expected.sourceId(), line(n.sourceId()))
                            && Objects.equals(expected.sectionId(), n.sectionId())
                            && Objects.equals(expected.trackId(), n.trackId())
                            && expected.type() == n.type()
                            && Math.abs(expected.positionM() - n.positionM()) <= 1e-9).toList();
            if (candidates.size() != 1) throw new AssertionError(
                    "Cannot uniquely map generated boundary node: " + n.id());
            String originalId = candidates.get(0).id();
            nodeNames.put(n.id(), originalId);
            mappedOldIds.add(originalId);
        }
    }
    private static String node(String id) { return nodeNames.getOrDefault(id,id); }
    private static String line(String id) { return lineNames.getOrDefault(id,id); }
    private static String edge(CalculationBranch b, boolean mapped) {
        return (mapped ? line(b.sourceId()) : b.sourceId()) + "|"
                + (mapped ? node(b.fromNodeId()) : b.fromNodeId()) + "|" + (mapped ? node(b.toNodeId()) : b.toNodeId());
    }
    private static void verifyEquivalent(CalculationNetwork old, CalculationNetwork newer) {
        Map<String,CalculationNode> oldNodes = new HashMap<>(); for(var n:old.nodes()) oldNodes.put(n.id(),n);
        if(old.nodes().size()!=newer.nodes().size()) throw new AssertionError("Different node counts");
        for(var n:newer.nodes()) {
            var expected=oldNodes.get(node(n.id()));
            if(expected==null || !Objects.equals(expected.sectionId(),n.sectionId())
                    || !Objects.equals(expected.trackId(),n.trackId()) || Math.abs(expected.positionM()-n.positionM())>1e-9)
                throw new AssertionError("Different node geometry: "+n.id());
        }
        Map<String,CalculationBranch> oldBranches=new HashMap<>();for(var b:old.branches())oldBranches.put(edge(b,false),b);
        if(old.branches().size()!=newer.branches().size())throw new AssertionError("Different branch counts");
        for(var b:newer.branches()) {
            var expected=oldBranches.get(edge(b,true));
            if(expected==null || Math.abs(expected.resistanceOhm().asDouble()-b.resistanceOhm().asDouble())>1e-12)
                throw new AssertionError("Different branch or resistance: "+edge(b,true));
        }
        Map<String,DiodeSubstationElement> stations=new HashMap<>();
        for(var e:old.elements())if(e instanceof DiodeSubstationElement station)stations.put(station.id(),station);
        int stationCount=0;
        for(var e:newer.elements())if(e instanceof DiodeSubstationElement station) {
            stationCount++;var expected=stations.get(station.id());
            if(expected==null || !expected.feedingNodeId().equals(node(station.feedingNodeId()))
                    || !expected.returnNodeId().equals(node(station.returnNodeId()))
                    || expected.emfV().asDouble()!=station.emfV().asDouble()
                    || expected.internalResistanceOhm().asDouble()!=station.internalResistanceOhm().asDouble()
                    || expected.enabled()!=station.enabled())throw new AssertionError("Different substation: "+station.id());
        }
        if(stationCount!=stations.size())throw new AssertionError("Different substation counts");
        Map<String,CalculationTrainLoad> loads=new HashMap<>();for(var l:old.trainLoads())loads.put(l.trainId(),l);
        if(loads.size()!=newer.trainLoads().size())throw new AssertionError("Different load counts");
        for(var l:newer.trainLoads()) {
            var expected=loads.get(l.trainId());
            if(expected==null || !expected.feedingNodeId().equals(node(l.feedingNodeId()))
                    || !expected.returnNodeId().equals(node(l.returnNodeId())) || expected.pReqW().asDouble()!=l.pReqW().asDouble())
                throw new AssertionError("Different train attachment/load: "+l.trainId());
        }
    }
    private static CalculationNetwork originalOrder(CalculationNetwork old, CalculationNetwork newer) { return ordered(old,newer,true,true); }
    private static CalculationNetwork ordered(CalculationNetwork old, CalculationNetwork newer, boolean reorderNodes, boolean reorderBranches) {
        Map<String,Integer> nodeOrder=new HashMap<>(),branchOrder=new HashMap<>();
        for(int i=0;i<old.nodes().size();i++)nodeOrder.put(old.nodes().get(i).id(),i);
        for(int i=0;i<old.branches().size();i++)branchOrder.put(edge(old.branches().get(i),false),i);
        var nodes=new ArrayList<>(newer.nodes());if(reorderNodes)nodes.sort(Comparator.comparingInt(n->nodeOrder.get(node(n.id()))));
        var branches=new ArrayList<>(newer.branches());if(reorderBranches)branches.sort(Comparator.comparingInt(b->branchOrder.get(edge(b,true))));
        List<ElectricalElement> elements=new ArrayList<>(branches);
        for(var e:newer.elements())if(!(e instanceof CalculationBranch))elements.add(e);
        return new CalculationNetwork(nodes,branches,newer.trainLoads(),elements);
    }
    private static Outcome solve(Infra infra, CalculationNetwork network, Map<String,Real> previous) {
        Map<String,Double> requested=new HashMap<>();for(var l:network.trainLoads())requested.put(l.trainId(),l.pReqW().asDouble());
        var solved=DcSolver.solveWithFallback(infra.parameters(),network,requested,previous);
        Map<String,Double> voltages=new LinkedHashMap<>();
        for(var l:network.trainLoads())voltages.put(l.trainId(),solved.networkResult().voltages().get(l.feedingNodeId()).asDouble()
                -solved.networkResult().voltages().get(l.returnNodeId()).asDouble());
        return new Outcome(solved.networkResult().voltages(),voltages);
    }
    private static List<Sample> samples() throws IOException {
        var stream=TwoSectionsOrderDiagnostic.class.getClassLoader().getResourceAsStream(ROOT+"prefix.csv");
        if(stream==null)throw new IOException("Missing prefix.csv");List<Sample> out=new ArrayList<>();
        try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
            reader.readLine();String row;
            while((row=reader.readLine())!=null) {
                String[] c=row.split(",",-1);
                var train=new CalculationTrainPosition(c[1],c[3],c[4],c[2],Double.parseDouble(c[6]),Double.parseDouble(c[5]),Real.fromDouble(Double.parseDouble(c[7])));
                out.add(new Sample(Double.parseDouble(c[0]),train,Double.parseDouble(c[8]),Double.parseDouble(c[9])));
            }
        }
        return out;
    }
    public static void main(String[] args) throws Exception {
        Path output=Path.of(args.length==0?"build/diagnostics/two-sections-order":args[0]);Files.createDirectories(output);
        Config mapping=ConfigFactory.parseResources(ROOT+"mapping.conf");nodeNames=stringMap(mapping.getConfig("nodes"));lineNames=stringMap(mapping.getConfig("lines"));
        Infra old=infrastructure("original"),newer=infrastructure("objects");
        mapGeneratedBoundaryNodes(old.base(), newer.base());
        Map<Double,List<Sample>> times=new LinkedHashMap<>();for(var s:samples())times.computeIfAbsent(s.time(),ignored->new ArrayList<>()).add(s);
        Map<String,Real> oldPrevious=Map.of(),newPrevious=Map.of(),orderedPrevious=Map.of();
        StringBuilder table=new StringBuilder("time_s,train_id,original_V,objects_V,objects_original_order_V,recorded_original_V,recorded_objects_V\n");
        double firstDifference=Double.NaN,maxOrderedDifference=0,maxOldReplayDifference=0,maxNewReplayDifference=0;
        CalculationNetwork lastOld=null,lastNew=null,lastOrdered=null;
        for(var entry:times.entrySet()) {
            var trains=entry.getValue().stream().map(Sample::train).toList();
            var oldNetwork=new TrainNodeInserter(old.parameters(),old.routes()).insertTrainNodes(old.base(),trains);
            var newNetwork=new TrainNodeInserter(newer.parameters(),newer.routes()).insertTrainNodes(newer.base(),trains);
            verifyEquivalent(oldNetwork,newNetwork);var ordered=originalOrder(oldNetwork,newNetwork);
            var a=solve(old,oldNetwork,oldPrevious);var b=solve(newer,newNetwork,newPrevious);var c=solve(newer,ordered,orderedPrevious);
            for(var sample:entry.getValue()) {
                String train=sample.train().trainId();double av=a.trainVoltages().get(train),bv=b.trainVoltages().get(train),cv=c.trainVoltages().get(train);
                if(Double.isNaN(firstDifference)&&Math.abs(av-bv)>1)firstDifference=entry.getKey();
                maxOrderedDifference=Math.max(maxOrderedDifference,Math.abs(av-cv));
                maxOldReplayDifference=Math.max(maxOldReplayDifference,Math.abs(av-sample.oldU()));maxNewReplayDifference=Math.max(maxNewReplayDifference,Math.abs(bv-sample.newU()));
                table.append(String.format(Locale.ROOT,"%.9g,%s,%.12g,%.12g,%.12g,%.12g,%.12g%n",entry.getKey(),train,av,bv,cv,sample.oldU(),sample.newU()));
            }
            oldPrevious=a.previous();newPrevious=b.previous();orderedPrevious=c.previous();lastOld=oldNetwork;lastNew=newNetwork;lastOrdered=ordered;
        }
        var coldOld=solve(old,lastOld,Map.of());var coldNew=solve(newer,lastNew,Map.of());var coldOrdered=solve(newer,lastOrdered,Map.of());
        String summary="Structurally equivalent networks and train attachments at all "+times.size()+" timesteps.\n"
                +"First replayed voltage difference >1 V: "+firstDifference+" s\n"
                +"Maximum difference after restoring original node/branch order: "+maxOrderedDifference+" V\n"
                +"Maximum replay difference vs recorded original: "+maxOldReplayDifference+" V\n"
                +"Maximum replay difference vs recorded objects: "+maxNewReplayDifference+" V\n"
                +"Cold final step original: "+coldOld.trainVoltages()+"\nCold final step objects: "+coldNew.trainVoltages()+"\nCold final step objects/original order: "+coldOrdered.trainVoltages()+"\n";
        orderExperiment(output,old,newer,lastOld,lastNew);
        Files.writeString(output.resolve("voltage-comparison.csv"),table);Files.writeString(output.resolve("summary.txt"),summary);
        System.out.print(summary);System.out.println("Diagnostics: "+output.toAbsolutePath());
    }
    // Diagnostic replay uses production stamping and linear solving. Reflection keeps production unchanged.
    private static void trace(Path output,String label,Infra infra,CalculationNetwork base) throws Exception {
        var solver=new SingleTimestepSolver(infra.parameters(),new LinearSystemSolver());
        var initial=SingleTimestepSolver.class.getDeclaredMethod("initialVoltageGuesses",CalculationNetwork.class,Map.class);
        var loads=SingleTimestepSolver.class.getDeclaredMethod("withTrainLoads",CalculationNetwork.class,Map.class,Map.class);
        initial.setAccessible(true);loads.setAccessible(true);
        var targetMethod=SingleTimestepSolver.class.getDeclaredMethod("iterationVoltageTarget",
                double.class,double.class,double.class,double.class);
        targetMethod.setAccessible(true);
        Map<String,Double> powers=new HashMap<>();for(var l:base.trainLoads())powers.put(l.trainId(),l.pReqW().asDouble());
        @SuppressWarnings("unchecked") Map<String,Double> guesses=(Map<String,Double>)initial.invoke(solver,base,powers);
        Map<String,Real> previous=Map.of(),voltages=Map.of();boolean converged=false;int iterations=0;
        StringBuilder csv=new StringBuilder("iteration,train_id,guess_V,solved_V,change_V,regen_mode,regen_current_guess_A,diode_states,event\n");
        for(int i=1;i<=200;i++) {
            iterations=i;StringJoiner states=new StringJoiner(";");
            for(var e:base.elements())if(e instanceof DiodeSubstationElement d) {
                Real f=previous.get(d.feedingNodeId()),r=previous.get(d.returnNodeId());
                states.add(d.id()+":"+(f!=null&&r!=null&&f.asDouble()-r.asDouble()>d.emfV().asDouble()+1e-3?"BLOCKED":"CONDUCTING"));
            }
            var network=(CalculationNetwork)loads.invoke(solver,base,powers,guesses);
            var system=new AdmittanceSystemBuilder().build(network,infra.parameters().referenceNodeId(),List.of(),previous);
            boolean regenerationOff = false;
            for (var load : base.trainLoads()) {
                if (powers.get(load.trainId()) < 0.0
                        && guesses.get(load.trainId()) >= infra.parameters().uMaxV()) regenerationOff = true;
            }
            if (regenerationOff) dumpMatrixProbe(output, label, i, system);
            try { voltages=new LinearSystemSolver().solveVoltages(system); }
            catch(IllegalArgumentException ex) {
                if(!ex.getMessage().startsWith("Singular admittance matrix"))throw ex;
                csv.append(i+",,,,,,,"+states+",SINGULAR\n");voltages=previous;break;
            }
            double max=0;Map<String,Double> next=new LinkedHashMap<>();
            for(var l:base.trainLoads()) {
                double guess=guesses.get(l.trainId()),u=voltages.get(l.feedingNodeId()).asDouble()-voltages.get(l.returnNodeId()).asDouble();
                double target=(double)targetMethod.invoke(solver,guess,u,powers.get(l.trainId()),1e-3);
                max=Math.max(max,Math.abs(target-guess));next.put(l.trainId(),guess+0.1*(target-guess));
                double requested=powers.get(l.trainId());String mode=requested>=0?"LOAD":guess<850?"CONSTANT_POWER":guess>=infra.parameters().uMaxV()?"OFF":"DROOP";
                double current=requested<0?RegenerativeTrainElement.regenerativeCurrentA(requested,guess,850,infra.parameters().uMaxV()):0;
                csv.append(String.format(Locale.ROOT,"%d,%s,%.17g,%.17g,%.17g,%s,%.17g,%s,ITERATION%n",i,l.trainId(),guess,u,u-guess,mode,current,states));
            }
            if(max<=1e-3){converged=true;break;}guesses=next;previous=voltages;
        }
        // Guard against drift in this test-only iteration replay.
        var actual=solver.solve(base,infra.parameters().referenceNodeId(),powers,200,1e-3,Map.of());
        if(actual.converged()!=converged||actual.iterations()!=iterations)throw new AssertionError("Iteration replay differs: "+label);
        for(var entry:actual.voltages().entrySet())if(entry.getValue().asDouble()!=voltages.get(entry.getKey()).asDouble())throw new AssertionError("Voltage replay differs: "+label);
        Files.writeString(output.resolve(label+"-iterations.csv"),csv);
    }
    private static void orderExperiment(Path output,Infra old,Infra newer,CalculationNetwork a,CalculationNetwork b) throws Exception {
        var variants=List.of(a,b,ordered(a,b,true,false),ordered(a,b,false,true),ordered(a,b,true,true));
        var names=List.of("original","objects","objects_nodes_restored","objects_branches_restored","objects_both_restored");
        StringBuilder report=new StringBuilder("Cold 176.9 s; production solveWithFallback, independent runs.\n");
        for(int i=0;i<variants.size();i++) {
            var infra=i==0?old:newer;var network=variants.get(i);Map<String,Double> powers=new HashMap<>();
            for(var l:network.trainLoads())powers.put(l.trainId(),l.pReqW().asDouble());
            var raw=new SingleTimestepSolver(infra.parameters(),new LinearSystemSolver()).solve(network,infra.parameters().referenceNodeId(),powers,200,1e-3,Map.of());
            var finalResult=DcSolver.solveWithFallback(infra.parameters(),network,powers,Map.of());
            var u=solve(infra,network,Map.of()).trainVoltages();
            report.append(names.get(i)+": raw_converged="+raw.converged()+", raw_iterations="+raw.iterations()+", final_U="+u+", allocated_W="+finalResult.allocatedPowersW()+"\n");
            trace(output,names.get(i),infra,network);
        }
        Files.writeString(output.resolve("order-isolation.txt"),report);System.out.print(report);
    }


    /** Test-only probe; production matrix and solver are left untouched. */
    private static void dumpMatrixProbe(Path output, String label, int iteration,
                                        AdmittanceSystem system) throws Exception {
        int n = system.size(), ref = system.referenceNodeIndex();
        double[][] a = new double[n - 1][n - 1];
        double[] rhs = new double[n - 1];
        StringBuilder matrix = new StringBuilder("row_node,col_node,conductance_S\n");
        StringBuilder report = new StringBuilder("iteration=" + iteration + "\nreference="
                + system.nodeIds().get(ref) + "\n");
        int rr = 0;
        double scale = 0.0, maxCurrent = 0.0;
        for (int row = 0; row < n; row++) {
            double j = system.currentVector()[row].asDouble();
            maxCurrent = Math.max(maxCurrent, Math.abs(j));
            report.append("current ").append(system.nodeIds().get(row)).append("=").append(j).append(" A\n");
            for (int col = 0; col < n; col++) {
                double g = system.conductanceMatrix()[row][col].asDouble();
                matrix.append(system.nodeIds().get(row)).append(',').append(system.nodeIds().get(col))
                        .append(',').append(Double.toString(g)).append('\n');
            }
            if (row == ref) continue;
            int cc = 0;
            for (int col = 0; col < n; col++) {
                if (col == ref) continue;
                double g = system.conductanceMatrix()[row][col].asDouble();
                a[rr][cc++] = g;
                scale = Math.max(scale, Math.abs(g));
            }
            rhs[rr++] = j;
        }
        report.append("max_abs_matrix_entry_S=").append(scale).append("\nmax_abs_current_A=")
                .append(maxCurrent).append("\npivot,absolute_S,relative_to_matrix_scale\n");
        for (int pivot = 0; pivot < a.length; pivot++) {
            int best = pivot;
            for (int row = pivot + 1; row < a.length; row++)
                if (Math.abs(a[row][pivot]) > Math.abs(a[best][pivot])) best = row;
            double magnitude = Math.abs(a[best][pivot]);
            report.append(pivot).append(',').append(magnitude).append(',')
                    .append(scale == 0.0 ? Double.NaN : magnitude / scale).append('\n');
            if (magnitude < 1e-12) { report.append("PRODUCTION_SINGULAR_THRESHOLD\n"); break; }
            double[] tmp = a[pivot]; a[pivot] = a[best]; a[best] = tmp;
            double t = rhs[pivot]; rhs[pivot] = rhs[best]; rhs[best] = t;
            for (int row = pivot + 1; row < a.length; row++) {
                double factor = a[row][pivot] / a[pivot][pivot];
                if (factor == 0.0) continue;
                a[row][pivot] = 0.0;
                for (int col = pivot + 1; col < a.length; col++) a[row][col] -= factor * a[pivot][col];
                rhs[row] -= factor * rhs[pivot];
            }
        }
        String prefix = label + "-off-" + iteration;
        Files.writeString(output.resolve(prefix + "-matrix.csv"), matrix);
        Files.writeString(output.resolve(prefix + "-pivots.txt"), report);
    }
}
