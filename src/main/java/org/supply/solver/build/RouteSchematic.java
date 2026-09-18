package org.supply.solver.build;

import org.supply.domain.Route;
import org.supply.solver.model.*;
import org.supply.track.RwyCoordinate;
import org.supply.track.TrackJunction;
import java.util.*;

/** Deterministic route schematic. Coordinates are layout coordinates, not railway scale. */
public final class RouteSchematic {
    private RouteSchematic() { }
    private static final int FEED=160, RETURN=400, STEP=340, HALF=80;
    private record Station(String id,String from,String to,String type,Set<String> terminals) { }
    /** Source coordinates and explicitly configured physical junctions, for drawing only. */
    public record Context(Map<String,RwyCoordinate> railwayPositions,List<TrackJunction> junctions) {
        public Context {
            railwayPositions=Map.copyOf(railwayPositions);
            junctions=List.copyOf(junctions);
        }
        public static Context empty() { return new Context(Map.of(),List.of()); }
    }

    public static String svg(CalculationNetwork network, Route route, Map<String,Double> ohmPerM) {
        return svg(network,route,ohmPerM,Context.empty());
    }
    public static String svg(CalculationNetwork network,Route route,Map<String,Double> ohmPerM,Context context) {
        return render(network,route,ohmPerM,Map.of(),context);
    }

    /** Combines opposite routes without duplicating their physical branches. */
    public static String combinedSvg(CalculationNetwork network, List<Route> routes, Map<String,Double> ohmPerM) {
        return combinedSvg(network,routes,ohmPerM,Context.empty());
    }
    public static String combinedSvg(CalculationNetwork network,List<Route> routes,Map<String,Double> ohmPerM,Context context) {
        Map<String,CalculationNode> nodes=new HashMap<>();
        for(var n:network.nodes()) { nodes.put(n.id(),n); }
        Map<String,String> side=new LinkedHashMap<>();
        List<Route> ordered=new ArrayList<>(routes);
        ordered.sort(Comparator.comparing(Route::id));
        boolean railwaySides=ordered.stream().allMatch(r->routeSide(network,r,nodes)!=null);
        if(railwaySides) ordered.sort(Comparator.comparing(r->routeSide(network,r,nodes)));
        if(!railwaySides && ordered.size()!=2) {
            throw new IllegalArgumentException("Without U/D track metadata, a combined schematic requires exactly two routes");
        }
        Set<String> feed=new LinkedHashSet<>(), returning=new LinkedHashSet<>();
        for(Route r:ordered) {
            String track=railwaySides?routeSide(network,r,nodes):(ordered.indexOf(r)==0?"U":"D");
            for(String line:r.feedingLineIds()) { assign(side,line,track+"F");feed.add(line); }
            for(String line:r.returnLineIds()) { assign(side,line,track+"R");returning.add(line); }
        }
        // U establishes the common physical left-to-right order, irrespective of D traversal.
        List<String> f=new ArrayList<>(feed);f.sort(Comparator.comparing(id->side.get(id).startsWith("U")?0:1));
        String title=ordered.stream().map(Route::id).reduce((a,b)->a+" + "+b).orElseThrow();
        return render(network,new Route(title,f,new ArrayList<>(returning)),ohmPerM,side,context);
    }
    private static void assign(Map<String,String> map,String id,String value) {
        String old=map.putIfAbsent(id,value);
        if(old!=null && !old.equals(value)) { throw new IllegalArgumentException("Shared line has conflicting U/D classification: "+id); }
    }
    private static String routeSide(CalculationNetwork network,Route route,Map<String,CalculationNode> nodes) {
        Set<String> sides=new HashSet<>();
        Set<String> lines=new HashSet<>(route.feedingLineIds());lines.addAll(route.returnLineIds());
        for(var b:network.branches()) { if(lines.contains(b.sourceId())) {
            for(String id:List.of(b.fromNodeId(),b.toNodeId())) {
                var n=nodes.get(id);String track=n==null?null:n.trackId();
                if(track!=null && track.matches("U[0-9]*")) { sides.add("U"); }
                if(track!=null && track.matches("D[0-9]*")) { sides.add("D"); }
            }
        } }
        if(sides.size()!=1) { return null; }
        return sides.iterator().next();
    }
    private static String render(CalculationNetwork network,Route route,Map<String,Double> ohmPerM,Map<String,String> lanes,Context context) {
        boolean combined=!lanes.isEmpty();
        Map<String,CalculationNode> nodes=new LinkedHashMap<>();
        for(var n:network.nodes()) { nodes.put(n.id(),n); }
        Map<String,List<CalculationBranch>> byLine=new LinkedHashMap<>();
        for(var b:network.branches()) { byLine.computeIfAbsent(b.sourceId(),k->new ArrayList<>()).add(b); }
        Set<String> selected=new LinkedHashSet<>(route.feedingLineIds());selected.addAll(route.returnLineIds());
        Layout layout=new Layout(nodes,network.branches(),selected,ohmPerM,context);
        LinkedHashSet<String> places=new LinkedHashSet<>();
        String previous=null;
        for(String id:route.feedingLineIds()) {
            for(var b:byLine.getOrDefault(id,List.of())) {
                String a=layout.place(nodes.get(b.fromNodeId())),z=layout.place(nodes.get(b.toNodeId()));
                if(previous==null) {
                    int ix=route.feedingLineIds().indexOf(id);
                    if(ix+1<route.feedingLineIds().size()) {
                        String next=route.feedingLineIds().get(ix+1);
                        if(byLine.getOrDefault(next,List.of()).stream().anyMatch(c->a.equals(layout.place(nodes.get(c.fromNodeId())))||a.equals(layout.place(nodes.get(c.toNodeId()))))) {
                            places.add(z);places.add(a);previous=a;continue;
                        }
                    }
                } else if(previous.equals(z)) { places.add(z);places.add(a);previous=a;continue; }
                places.add(a);places.add(z);previous=z;
            }
        }
        Set<String> routeNodes=new LinkedHashSet<>();
        for(String id:selected) { for(var b:byLine.getOrDefault(id,List.of())) {
            places.add(layout.place(nodes.get(b.fromNodeId())));places.add(layout.place(nodes.get(b.toNodeId())));
            routeNodes.add(b.fromNodeId());routeNodes.add(b.toNodeId());
        } }
        boolean southOnly=!combined && routeNodes.stream().anyMatch(t->t.endsWith("_C")||t.endsWith("_D")) &&
                routeNodes.stream().noneMatch(t->t.endsWith("_A")||t.endsWith("_B"));
        List<Station> stations=new ArrayList<>();Map<String,Station> owner=new HashMap<>();
        Map<String,String> stationValues=new HashMap<>();
        for(var e:network.elements()) {
            String id,from,to,type;
            if(e instanceof DiodeSubstationElement s) { id=s.id();from=s.feedingNodeId();to=s.returnNodeId();type="DIODE enabled="+s.enabled(); }
            else if(e instanceof ThyristorSubstationElement s) { id=s.id();from=s.feedingNodeId();to=s.returnNodeId();type="THYRISTOR enabled="+s.enabled(); }
            else if(e instanceof FixedLoadElement l) { id=l.id();from=l.feedingNodeId();to=l.returnNodeId();type="LOAD "+l.powerW()+" W"; }
            else { continue; }
            if(e instanceof DiodeSubstationElement s) { stationValues.put(id,String.format(Locale.ROOT,"EMF=%.6g V | Rint=%.6g ohm",s.emfV().asDouble(),s.internalResistanceOhm().asDouble())); }
            else if(e instanceof ThyristorSubstationElement s) { stationValues.put(id,String.format(Locale.ROOT,"EMF=%.6g V | Rint=%.6g ohm",s.emfV().asDouble(),s.internalResistanceOhm().asDouble())); }
            Set<String> terminals=new LinkedHashSet<>(List.of(from,to));boolean changed;
            do { changed=false;for(var b:network.branches()) {
                if(b.sourceId()!=null && b.sourceId().startsWith("internal_") && (terminals.contains(b.fromNodeId())||terminals.contains(b.toNodeId()))) {
                    changed|=terminals.add(b.fromNodeId());changed|=terminals.add(b.toNodeId());
                }
            } } while(changed);
            if(Collections.disjoint(terminals,routeNodes)) { continue; }
            Station station=new Station(id,from,to,type,terminals);
            for(String terminal:terminals) {
                if(owner.putIfAbsent(terminal,station)!=null) {
                    throw new IllegalArgumentException("Schematic cannot group shared installation bus; inspect DOT instead");
                }
                if(!layout.place(nodes.get(terminal)).equals(layout.place(nodes.get(from)))) {
                    throw new IllegalArgumentException("Station terminals have different locations: "+id);
                }
            }
            stations.add(station);
        }
        Map<String,Integer> x=new LinkedHashMap<>();int col=0;
        for(String p:places) { x.put(p,120+STEP*col++); }
        StringBuilder out=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"");
        out.append(Math.max(1100,STEP*(col-1)+240)).append("\" height=\"580\" viewBox=\"0 0 ").append(Math.max(1100,STEP*(col-1)+240)).append(" 580\">\n");
        out.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/><g font-family=\"Arial\">\n");
        text(out,20,28,"Route "+route.id()+" | selected route, topology only",14,"#222","start");
        text(out,20,50,combined?"Route lanes in title order: first red; second blue. Feeding above return; layout not to distance scale.":"Red: feeding; blue: return. Layout not to distance scale.",12,"#333","start");
        text(out,20,68,"Model assumption: all feeder breakers closed. Internal station connections not drawn.",9,"#555","start");
        for(var entry:x.entrySet()) {
            Set<String> references=new LinkedHashSet<>();Set<Double> metres=new LinkedHashSet<>();
            for(String id:routeNodes) {
                var node=nodes.get(id);
                if(!layout.place(node).equals(entry.getKey())) continue;
                var coordinate=context.railwayPositions().get(id);
                if(coordinate!=null) references.add(railwayLabel(coordinate));
                metres.add(node.positionM());
            }
            int labelY=85;
            if(references.isEmpty()) {
                text(out,entry.getValue(),labelY,"Model "+entry.getKey()+" m",12,"#333","middle");
            } else {
                for(String reference:references) {
                    text(out,entry.getValue(),labelY,reference,12,"#333","middle");labelY+=18;
                }
                // A junction can have different section-local model metres.
                // Do not present one of those as its unique physical position.
                if(references.size()==1&&metres.size()==1) {
                    text(out,entry.getValue(),labelY,metreLabel(metres.iterator().next())+" m",12,"#333","middle");
                }
            }
        }
        for(Station s:stations) {
            int sx=x.get(layout.place(nodes.get(s.from())));
            out.append("<g><title>").append(xml(s.id()+" | "+s.type()+" | terminals: "+s.terminals())).append("</title><rect x=\"").append(sx-HALF).append("\" y=\"125\" width=\"160\" height=\"400\" rx=\"6\" fill=\"#f0fdf4\" stroke=\"#067647\"/></g>\n");
            text(out,sx,280,s.id(),14,"#067647","middle");text(out,sx,298,s.type(),9,"#067647","middle");
            String values=stationValues.get(s.id());
            if(values!=null) { String[] fields=values.split(" \\| ");text(out,sx,330,fields[0],9,"#067647","middle");text(out,sx,345,fields[1],9,"#067647","middle"); }
            // Omit internal connections: horizontal strokes could imply direct gap bridges.
            if(combined) {
                text(out,sx,365,"Common feeding bus",12,"#333","middle");
                text(out,sx,383,"Common return bus",12,"#333","middle");
            }
            text(out,sx,315,"rectifier / load",8,"#555","middle");
            List<String> inactive=s.terminals().stream().filter(t->!routeNodes.contains(t)).sorted().toList();
            int y=210;
            for(String id:inactive) {
                // Compact terminal names follow the configured A/B/C/D contract.
                // Use the active same-side terminal to respect reversed route layout.
                String peer=id.endsWith("_C")?id.substring(0,id.length()-1)+"A":
                        id.endsWith("_D")?id.substring(0,id.length()-1)+"B":
                        id.endsWith("_A")?id.substring(0,id.length()-1)+"C":
                        id.endsWith("_B")?id.substring(0,id.length()-1)+"D":null;
                int side=id.endsWith("LEFT")?-1:1, terminalY=y;
                if(peer!=null && routeNodes.contains(peer)) {
                    for(String lineId:selected) for(var b:byLine.getOrDefault(lineId,List.of())) {
                        String other=peer.equals(b.fromNodeId())?b.toNodeId():peer.equals(b.toNodeId())?b.fromNodeId():null;
                        if(other!=null) side=Integer.compare(x.get(layout.place(nodes.get(other))),sx);
                    }
                    terminalY=FEED+((id.endsWith("_C")||id.endsWith("_D"))?80:0);
                } else if(id.startsWith("R_")&&id.endsWith("_S")) {
                    terminalY=RETURN+80;
                } else if(id.startsWith("R_")&&id.endsWith("_N")) {
                    terminalY=RETURN;
                }
                point(out,sx+side*HALF,terminalY,"#555",id+" | no external line on selected route");
                text(out,sx+side*(HALF-4),terminalY-8,id,12,"#333",side<0?"start":"end");y+=22;
            }
        }
        Set<String> labels=new HashSet<>();int feedingCount=0,returnCount=0;
        for(String id:selected) {
            boolean feeding=route.feedingLineIds().contains(id);int y=feeding?FEED:RETURN;String color=feeding?"#b42318":"#175cd3";
            if(southOnly) y+=80;
            if(combined) { String lane=lanes.get(id);boolean down=lane.startsWith("D");y=(feeding?FEED:RETURN)+(down?80:0);color=down?"#175cd3":"#b42318"; }
            for(var b:byLine.getOrDefault(id,List.of())) {
                int a=x.get(layout.place(nodes.get(b.fromNodeId()))),z=x.get(layout.place(nodes.get(b.toNodeId())));
                if(a==z) {
                    if(layout.connectors.contains(b.id())) continue;
                    throw new IllegalArgumentException("External line has coincident schematic locations: "+id);
                }
                int sign=a<z?1:-1;
                int from=a+(owner.containsKey(b.fromNodeId())?sign*HALF:0);
                int to=z-(owner.containsKey(b.toNodeId())?sign*HALF:0);
                line(out,from,y,to,y,color,false,id+" | total R="+b.resistanceOhm().asDouble()+" ohm");
                int mid=(from+to)/2;
                text(out,mid,y-36,id,8,color,"middle");
                Double r=ohmPerM.get(id);
                text(out,mid,y-23,r==null?"ohm/m unavailable":String.format(Locale.ROOT,"%.6g ohm/m",r),9,color,"middle");
                endpoint(out,b.fromNodeId(),from,y,sign,owner.containsKey(b.fromNodeId()),color,labels,layout.isJunction(nodes.get(b.fromNodeId())));
                endpoint(out,b.toNodeId(),to,y,-sign,owner.containsKey(b.toNodeId()),color,labels,layout.isJunction(nodes.get(b.toNodeId())));
                if(feeding) { feedingCount++; } else { returnCount++; }
            }
        }
        text(out,20,558,"External branches drawn: feeding="+feedingCount+", return="+returnCount+". Co-located junction connectors omitted: "+layout.connectors.size()+". Solver nodes unchanged.",10,"#555","start");
        return out.append("</g></svg>\n").toString();
    }
    private static void endpoint(StringBuilder out,String id,int x,int y,int direction,boolean station,String color,Set<String> labels,boolean junction) {
        String key=(junction?"junction":id)+":"+x+":"+y;
        if(labels.add(key)) {
            point(out,x,y,color,junction?"Physical junction; separate solver nodes retained":id);
            text(out,x+(station?-direction*4:0),y+15,junction?"Junction":id,12,color,station?(direction>0?"end":"start"):"middle");
        }
    }
    private static String metreLabel(double m) {
        return java.math.BigDecimal.valueOf(m).stripTrailingZeros().toPlainString();
    }
    private static String railwayLabel(RwyCoordinate c) {
        int m=c.getPositionM();
        return c.getSectionId()+" "+(m/1000)+"+"+String.format(Locale.ROOT,"%03d",Math.abs(m%1000))+
                (c.getTrackId()==null||c.getTrackId().isBlank()?"":" "+c.getTrackId());
    }
    /** Unions drawing columns, never electrical nodes or calculation branches. */
    private static final class Layout {
        final Map<String,String> parent=new LinkedHashMap<>();
        final Set<String> connectors=new HashSet<>();
        Layout(Map<String,CalculationNode> nodes,List<CalculationBranch> branches,Set<String> selected,
               Map<String,Double> ohmPerM,Context context) {
            for(var node:nodes.values()) parent.put(RouteSchematic.place(node),RouteSchematic.place(node));
            for(var b:branches) {
                if(!selected.contains(b.sourceId())) continue;
                Double r=ohmPerM.get(b.sourceId());
                if(r==null||r>1e-12||r<=0||b.resistanceOhm().asDouble()>1e-8) continue;
                var from=context.railwayPositions().get(b.fromNodeId());
                var to=context.railwayPositions().get(b.toNodeId());
                if(from==null||to==null) continue;
                boolean explicit=context.junctions().stream().anyMatch(j->
                        (sameCoordinate(from,j.getFrom())&&sameCoordinate(to,j.getTo()))||
                        (sameCoordinate(to,j.getFrom())&&sameCoordinate(from,j.getTo())));
                if(!explicit) continue;
                String a=place(nodes.get(b.fromNodeId())),z=place(nodes.get(b.toNodeId()));
                if(!a.equals(z)) parent.put(z,a);
                connectors.add(b.id());
            }
        }
        String place(CalculationNode node) {
            String key=RouteSchematic.place(node);
            while(!parent.get(key).equals(key)) key=parent.get(key);
            return key;
        }
        boolean isJunction(CalculationNode node) {
            String root=place(node);int count=0;
            for(String key:parent.keySet()) {
                String p=key;while(!parent.get(p).equals(p)) p=parent.get(p);
                if(root.equals(p)&&++count>1) return true;
            }
            return false;
        }
        private static boolean sameCoordinate(RwyCoordinate a,RwyCoordinate b) {
            return a.getSectionId().equals(b.getSectionId())&&a.getPositionM()==b.getPositionM()&&
                    (b.getTrackId()==null||b.getTrackId().isBlank()||Objects.equals(a.getTrackId(),b.getTrackId()));
        }
    }
    private static String place(CalculationNode n) {
        if(n==null) { throw new IllegalArgumentException("Unknown schematic node"); }
        return n.sectionId()+":"+n.positionM();
    }
    private static void line(StringBuilder out,int x1,int y1,int x2,int y2,String color,boolean internal,String title) {
        out.append("<line x1=\"").append(x1).append("\" y1=\"").append(y1).append("\" x2=\"").append(x2).append("\" y2=\"").append(y2).append("\" stroke=\"").append(color).append("\" stroke-width=\"2\"");
        if(internal) { out.append(" stroke-dasharray=\"4 3\""); }
        out.append("><title>").append(xml(title)).append("</title></line>\n");
    }
    private static void point(StringBuilder out,int x,int y,String color,String title) {
        out.append("<circle cx=\"").append(x).append("\" cy=\"").append(y).append("\" r=\"3\" fill=\"").append(color).append("\"><title>").append(xml(title)).append("</title></circle>\n");
    }
    private static void text(StringBuilder out,int x,int y,String value,int size,String color,String anchor) {
        out.append("<text x=\"").append(x).append("\" y=\"").append(y).append("\" font-size=\"").append(Math.max(12,size)).append("\" fill=\"").append(color.equals("#555")?"#333":color).append("\" text-anchor=\"").append(anchor).append("\">").append(xml(value)).append("</text>\n");
    }
    private static String xml(String s) { return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
}
