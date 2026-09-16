package org.supply.solver.build;

import org.supply.domain.Route;
import org.supply.solver.model.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Graphviz topology only: undirected edges never imply calculated current. */
public final class ElectricalTopologyGraph {
    private ElectricalTopologyGraph() { }

    public static String dot(CalculationNetwork network, Route route) {
        Set<String> lines = new HashSet<>(route.feedingLineIds());
        lines.addAll(route.returnLineIds());
        Set<String> nodes = new HashSet<>();
        for (CalculationBranch b : network.branches()) {
            if (lines.contains(b.sourceId())) { nodes.add(b.fromNodeId()); nodes.add(b.toNodeId()); }
        }
        boolean changed;
        do {
            changed = false;
            for (CalculationBranch b : network.branches()) {
                if (internal(b) && (nodes.contains(b.fromNodeId()) || nodes.contains(b.toNodeId()))) {
                    changed |= nodes.add(b.fromNodeId()); changed |= nodes.add(b.toNodeId());
                }
            }
        } while (changed);
        List<String[]> installations = new ArrayList<>();
        for (ElectricalElement e : network.elements()) {
            String id, from, to, type;
            if (e instanceof DiodeSubstationElement s) {
                id=s.id(); from=s.feedingNodeId(); to=s.returnNodeId(); type="DIODE enabled="+s.enabled();
            } else if (e instanceof ThyristorSubstationElement s) {
                id=s.id(); from=s.feedingNodeId(); to=s.returnNodeId(); type="THYRISTOR enabled="+s.enabled();
            } else if (e instanceof FixedLoadElement l) {
                id=l.id(); from=l.feedingNodeId(); to=l.returnNodeId(); type="FIXED LOAD "+l.powerW()+" W";
            } else { continue; }
            if (nodes.contains(from) || nodes.contains(to)) {
                installations.add(new String[]{id,from,to,type});
            }
        }
        for (String[] i : installations) { nodes.add(i[1]); nodes.add(i[2]); }
        if (!installations.isEmpty()) {
            String compact = stationDot(network, route, installations, nodes, lines);
            if (compact != null) { return compact; }
        }
        StringBuilder out = new StringBuilder("digraph topology {\n  rankdir=LR; overlap=false; splines=polyline; nodesep=0.18; ranksep=0.35;\n");
        out.append("  label=").append(q("Route "+route.id()+" | topology only\nRed: feeding; blue: return; dashed: internal; green: installation")).append("; labelloc=t;\n");
        out.append("  node [shape=box, fontname=\"Arial\", fontsize=10, margin=\"0.04,0.03\"];\n  edge [fontname=\"Arial\", fontsize=8, dir=none, constraint=false];\n");
        Map<String, CalculationNode> byId = new LinkedHashMap<>();
        for (CalculationNode n : network.nodes()) { byId.put(n.id(), n); }
        // Model coordinates are grouped by section, never by numerical position alone.
        LinkedHashSet<String> places = new LinkedHashSet<>();
        String previous = null;
        for (String line : route.feedingLineIds()) {
            List<CalculationBranch> bs = network.branches().stream().filter(b -> line.equals(b.sourceId())).toList();
            for (CalculationBranch b : bs) {
                CalculationNode a=byId.get(b.fromNodeId()), z=byId.get(b.toNodeId());
                if (a==null || z==null) { continue; }
                String first=place(a), last=place(z);
                if (previous==null) {
                    int ix=route.feedingLineIds().indexOf(line);
                    if (ix+1<route.feedingLineIds().size()) {
                        String next=route.feedingLineIds().get(ix+1);
                        boolean firstTouches=network.branches().stream().filter(x -> next.equals(x.sourceId()))
                                .anyMatch(x -> samePlace(byId.get(x.fromNodeId()),a) || samePlace(byId.get(x.toNodeId()),a));
                        if (firstTouches && !first.equals(last)) { String swap=first; first=last; last=swap; }
                    }
                } else if (previous.equals(last)) { String swap=first; first=last; last=swap; }
                places.add(first); places.add(last); previous=last;
            }
        }
        for (CalculationNode n : network.nodes()) { if (nodes.contains(n.id())) { places.add(place(n)); } }
        int column=0;
        for (String location : places) {
            String anchor="__layout_column_"+column;
            out.append("  ").append(q(anchor)).append(" [shape=point, width=0, height=0, label=\"\", style=invis];\n");
            if (column>0) { out.append("  ").append(q("__layout_column_"+(column-1))).append(" -> ").append(q(anchor)).append(" [style=invis, constraint=true, weight=100];\n"); }
            List<String> vertical=new ArrayList<>();
            for (CalculationNode n : network.nodes()) { if (nodes.contains(n.id()) && place(n).equals(location) && n.id().startsWith("F")) { vertical.add(n.id()); } }
            for (int j=0;j<installations.size();j++) { if (sameLocation(byId.get(installations.get(j)[1]),location)) { vertical.add("__layout_installation_"+j); } }
            for (CalculationNode n : network.nodes()) { if (nodes.contains(n.id()) && place(n).equals(location) && !n.id().startsWith("F")) { vertical.add(n.id()); } }
            out.append("  { rank=same; ").append(q(anchor)).append(';');
            for (String id : vertical) { out.append(q(id)).append(';'); }
            out.append(" }\n");
            for (int j=1;j<vertical.size();j++) { out.append("  ").append(q(vertical.get(j-1))).append(" -> ").append(q(vertical.get(j))).append(" [style=invis, weight=100];\n"); }
            column++;
        }
        for (CalculationNode n : network.nodes()) {
            if (!nodes.contains(n.id())) { continue; }
            String color = n.id().startsWith("F") ? "#b42318" : n.id().startsWith("R") ? "#175cd3" : "#666666";
            out.append("  ").append(q(n.id())).append(" [color=").append(q(color)).append(", label=")
                    .append(q(shortName(n.id()))).append(", tooltip=")
                    .append(q(n.id()+" | section="+n.sectionId()+" track="+Objects.toString(n.trackId(),"shared")+" position="+n.positionM()+" m")).append("];\n");
        }
        for (CalculationBranch b : network.branches()) {
            if (!(lines.contains(b.sourceId()) || (internal(b) && nodes.contains(b.fromNodeId()) && nodes.contains(b.toNodeId())))) { continue; }
            String color = internal(b) ? "#888888" : route.feedingLineIds().contains(b.sourceId()) ? "#b42318" : "#175cd3";
            out.append("  ").append(q(b.fromNodeId())).append(" -> ").append(q(b.toNodeId()))
                    .append(" [color=").append(q(color)).append(", style=").append(internal(b)?"dashed":"solid")
                    .append(", label=").append(q(internal(b)?"":String.format(Locale.ROOT,"%.4g ohm",b.resistanceOhm().asDouble())))
                    .append(", tooltip=").append(q(b.sourceId()+" | "+b.id()+" | R="+b.resistanceOhm().asDouble()+" ohm")).append("];\n");
        }
        for (int j=0;j<installations.size();j++) {
            String[] i=installations.get(j);
            String symbol="__layout_installation_"+j;
            out.append("  ").append(q(symbol)).append(" [shape=box, color=\"#067647\", label=").append(q(i[0])).append(", tooltip=").append(q(i[3]+" | "+i[1]+" / "+i[2])).append("];\n");
            out.append("  ").append(q(i[1])).append(" -> ").append(q(symbol)).append(" -> ").append(q(i[2]))
                    .append(" [color=\"#067647\", penwidth=2];\n");
        }
        return out.append("}\n").toString();
    }

    public static void write(CalculationNetwork network, List<Route> routes, String selected,
                             Path directory, StringBuilder report, List<String> warnings) throws IOException {
        write(network, routes, selected, directory, report, warnings, Map.of());
    }

    public static void write(CalculationNetwork network, List<Route> routes, String selected,
                             Path directory, StringBuilder report, List<String> warnings,
                             Map<String,Double> ohmPerM) throws IOException {
        Files.createDirectories(directory);
        List<Route> ordered = new ArrayList<>(routes);
        ordered.sort(Comparator.comparing(Route::id));
        int index=0;
        for (Route route : ordered) {
            index++;
            if (selected != null && !selected.equals(route.id())) { continue; }
            String stem = String.format(Locale.ROOT,"route_%03d_",index)+route.id().replaceAll("[^A-Za-z0-9_-]","_");
            Path dot=directory.resolve(stem+".dot"), svg=directory.resolve(stem+".svg");
            Files.writeString(dot, dot(network,route), StandardCharsets.UTF_8);
            // Remove only this derived output so a failed render cannot leave a stale SVG.
            Files.deleteIfExists(svg);
            report.append("Topology DOT: ").append(dot).append('\n');
            try {
                Files.writeString(svg, RouteSchematic.svg(network, route, ohmPerM), StandardCharsets.UTF_8);
                report.append("Topology SVG: ").append(svg).append('\n');
            } catch (IllegalArgumentException | IOException failure) {
                Files.deleteIfExists(svg);
                warnings.add("Route "+route.id()+": schematic not generated: "+failure.getMessage()+"; inspect DOT");
            }
        }
        if (selected == null && routes.size() > 1) {
            Path combined = directory.resolve("routes_combined.svg");
            Files.deleteIfExists(combined);
            try {
                Files.writeString(combined, RouteSchematic.combinedSvg(network, routes, ohmPerM), StandardCharsets.UTF_8);
                report.append("Combined U/D topology SVG: ").append(combined).append('\n');
            } catch (IllegalArgumentException | IOException failure) {
                Files.deleteIfExists(combined);
                warnings.add("Combined U/D schematic not generated: " + failure.getMessage());
            }
        }
    }


    /** Internal connectors are represented inside station symbols, not merged in the solver. */
    private static String stationDot(CalculationNetwork network, Route route, List<String[]> stations,
                                     Set<String> selectedNodes, Set<String> lines) {
        Map<String,String> endpoint=new LinkedHashMap<>();
        Map<String,String> owner=new LinkedHashMap<>();
        Map<String,CalculationNode> byId=new LinkedHashMap<>();
        for (CalculationNode n:network.nodes()) { byId.put(n.id(),n); }
        StringBuilder out=new StringBuilder("digraph topology {\n rankdir=LR; splines=polyline; nodesep=0.15; ranksep=0.5;\n");
        out.append("label=").append(q("Route "+route.id()+" | selected route only; topology, no solved currents\nRed: feeding; blue: return. Internal connections shown in station symbols.")).append("; labelloc=t;\n");
        out.append("node [fontname=\"Arial\", fontsize=10]; edge [fontname=\"Arial\", fontsize=8, dir=none, constraint=false];\n");
        Map<String,List<String>> stationNodes=new LinkedHashMap<>();
        for (int j=0;j<stations.size();j++) {
            String[] station=stations.get(j);
            String symbol="__station_"+j;
            Set<String> terminals=new LinkedHashSet<>();
            terminals.add(station[1]); terminals.add(station[2]);
            boolean changed;
            do {
                changed=false;
                for (CalculationBranch b:network.branches()) {
                    if (internal(b) && (terminals.contains(b.fromNodeId()) || terminals.contains(b.toNodeId()))) {
                        changed |= terminals.add(b.fromNodeId()); changed |= terminals.add(b.toNodeId());
                    }
                }
            } while(changed);
            // Shared internal buses between installations need the detailed view to avoid hiding topology.
            for (String id:terminals) { if (owner.putIfAbsent(id,symbol)!=null) { return null; } }
            List<String> feeding=new ArrayList<>(), returning=new ArrayList<>();
            for(String id:terminals) {
                if (id.startsWith("F")) { feeding.add(id); } else { returning.add(id); }
            }
            Collections.sort(feeding); Collections.sort(returning);
            stationNodes.put(symbol,new ArrayList<>(terminals));
            int port=0;
            Map<String,String> ports=new LinkedHashMap<>();
            for(String id:terminals) {
                String p="t"+port++; ports.put(id,p); endpoint.put(id,q(symbol)+":"+p);
            }
            int columns=Math.max(1,feeding.size())*Math.max(1,returning.size());
            out.append(q(symbol)).append(" [shape=plain, tooltip=").append(q(station[3]+" | internal terminal connections retained in calculation network")).append(", label=<");
            out.append("<TABLE BORDER=\"1\" CELLBORDER=\"0\" CELLSPACING=\"0\" CELLPADDING=\"4\" COLOR=\"#067647\">");
            terminalRow(out,feeding,ports,columns,"#b42318");
            out.append("<TR><TD COLSPAN=\"").append(columns).append("\" BGCOLOR=\"#ecfdf3\"><B>").append(html(station[0])).append("</B><BR/>").append(html(station[3])).append("<BR/><FONT POINT-SIZE=\"8\">feeding bus | rectifier/load | return bus</FONT></TD></TR>");
            terminalRow(out,returning,ports,columns,"#175cd3");
            out.append("</TABLE>>];\n");
        }
        for(CalculationNode n:network.nodes()) {
            if (!selectedNodes.contains(n.id()) || endpoint.containsKey(n.id())) { continue; }
            endpoint.put(n.id(),q(n.id()));
            out.append(q(n.id())).append(" [shape=circle, width=0.12, height=0.12, fixedsize=true, label=\"\", xlabel=").append(q(n.id()))
                    .append(", color=").append(q(n.id().startsWith("F")?"#b42318":"#175cd3"))
                    .append(", tooltip=").append(q(n.id()+" | section="+n.sectionId()+" position="+n.positionM()+" m")).append("];\n");
        }
        LinkedHashMap<String,List<String>> locations=new LinkedHashMap<>();
        String previous=null;
        for(String line:route.feedingLineIds()) {
            for(CalculationBranch b:network.branches()) {
                if(!line.equals(b.sourceId())) { continue; }
                CalculationNode a=byId.get(b.fromNodeId()),z=byId.get(b.toNodeId());
                if(a==null || z==null) { continue; }
                String first=place(a),last=place(z);
                if(previous==null) {
                    int ix=route.feedingLineIds().indexOf(line);
                    if(ix+1<route.feedingLineIds().size()) {
                        String next=route.feedingLineIds().get(ix+1);
                        if(network.branches().stream().filter(x->next.equals(x.sourceId())).anyMatch(x->samePlace(byId.get(x.fromNodeId()),a)||samePlace(byId.get(x.toNodeId()),a))) {
                            String swap=first; first=last; last=swap;
                        }
                    }
                } else if(previous.equals(last)) { String swap=first;first=last;last=swap; }
                locations.putIfAbsent(first,new ArrayList<>());locations.putIfAbsent(last,new ArrayList<>()); previous=last;
            }
        }
        for(CalculationNode n:network.nodes()) {
            if(selectedNodes.contains(n.id()) && !owner.containsKey(n.id())) {
                locations.computeIfAbsent(place(n),key->new ArrayList<>()).add(n.id());
            }
        }
        for(var entry:stationNodes.entrySet()) {
            CalculationNode n=byId.get(entry.getValue().get(0));
            if(n!=null) { locations.computeIfAbsent(place(n),key->new ArrayList<>()).add(entry.getKey()); }
        }
        int col=0;
        for(var entry:locations.entrySet()) {
            String anchor="__layout_column_"+col;
            out.append(q(anchor)).append(" [shape=plaintext,label=").append(q(entry.getKey()+" m")).append(",fontcolor=\"#666666\",fontsize=8];\n");
            if(col>0) { out.append(q("__layout_column_"+(col-1))).append(" -> ").append(q(anchor)).append(" [style=invis,constraint=true,weight=100];\n"); }
            List<String> vertical=new ArrayList<>(entry.getValue());
            vertical.sort(Comparator.comparingInt(id->id.startsWith("F")?0:id.startsWith("__station_")?1:2));
            out.append("{rank=same;").append(q(anchor)).append(';');
            for(String id:vertical) { out.append(q(id)).append(';'); }
            out.append("}\n");
            List<String> chain=new ArrayList<>();chain.add(anchor);chain.addAll(vertical);
            for(int k=1;k<chain.size();k++) {
                out.append(q(chain.get(k-1))).append(" -> ").append(q(chain.get(k))).append(" [style=invis,weight=100];\n");
            }
            col++;
        }
        for(CalculationBranch b:network.branches()) {
            if(!lines.contains(b.sourceId())) { continue; }
            String from=endpoint.get(b.fromNodeId()),to=endpoint.get(b.toNodeId());
            if(from==null || to==null) { continue; }
            out.append(from).append(" -> ").append(to).append(" [color=")
                    .append(q(route.feedingLineIds().contains(b.sourceId())?"#b42318":"#175cd3"))
                    .append(",label=").append(q(b.sourceId()+"\n"+String.format(Locale.ROOT,"%.4g ohm",b.resistanceOhm().asDouble())))
                    .append(",tooltip=").append(q(b.id()+" | "+b.fromNodeId()+" / "+b.toNodeId())).append("];\n");
        }
        return out.append("}\n").toString();
    }
    private static void terminalRow(StringBuilder out,List<String> ids,Map<String,String> ports,int columns,String color) {
        if(ids.isEmpty()) { return; }
        out.append("<TR>");
        for(String id:ids) {
            out.append("<TD PORT=\"").append(ports.get(id)).append("\" COLSPAN=\"").append(columns/ids.size())
                    .append("\"><FONT COLOR=\"").append(color).append("\" POINT-SIZE=\"8\">").append(html(id)).append("</FONT></TD>");
        }
        out.append("</TR>");
    }
    private static String html(String value) { return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }

    private static boolean internal(CalculationBranch b) { return b.sourceId()!=null && b.sourceId().startsWith("internal_"); }
    private static String place(CalculationNode n) { return n.sectionId()+":"+n.positionM(); }
    private static boolean samePlace(CalculationNode a, CalculationNode b) { return a!=null && b!=null && place(a).equals(place(b)); }
    private static boolean sameLocation(CalculationNode a, String location) { return a!=null && place(a).equals(location); }
    private static String shortName(String id) { return id.replaceFirst("^[FR]_", "").replace("_LEFT"," L").replace("_RIGHT"," R"); }
    private static String q(String value) {
        return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\r","").replace("\n","\\n")+"\"";
    }
}
