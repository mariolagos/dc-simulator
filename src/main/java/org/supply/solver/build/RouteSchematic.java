package org.supply.solver.build;

import org.supply.domain.Route;
import org.supply.solver.model.*;
import java.util.*;

/** Deterministic route schematic. Coordinates are layout coordinates, not railway scale. */
public final class RouteSchematic {
    private RouteSchematic() { }
    private static final int FEED=160, RETURN=400, STEP=340, HALF=80;
    private record Station(String id,String from,String to,String type,Set<String> terminals) { }

    public static String svg(CalculationNetwork network, Route route, Map<String,Double> ohmPerM) {
        return render(network, route, ohmPerM, Map.of());
    }

    /** Combines opposite routes without duplicating their physical branches. */
    public static String combinedSvg(CalculationNetwork network, List<Route> routes, Map<String,Double> ohmPerM) {
        Map<String,CalculationNode> nodes=new HashMap<>();
        for(var n:network.nodes()) { nodes.put(n.id(),n); }
        Map<String,String> side=new LinkedHashMap<>();
        List<Route> ordered=new ArrayList<>(routes);
        ordered.sort(Comparator.comparing(r->routeSide(network,r,nodes)));
        Set<String> feed=new LinkedHashSet<>(), returning=new LinkedHashSet<>();
        for(Route r:ordered) {
            String track=routeSide(network,r,nodes);
            for(String line:r.feedingLineIds()) { assign(side,line,track+"F");feed.add(line); }
            for(String line:r.returnLineIds()) { assign(side,line,track+"R");returning.add(line); }
        }
        // U establishes the common physical left-to-right order, irrespective of D traversal.
        List<String> f=new ArrayList<>(feed);f.sort(Comparator.comparing(id->side.get(id).startsWith("U")?0:1));
        return render(network,new Route("U + D",f,new ArrayList<>(returning)),ohmPerM,side);
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
        if(sides.size()!=1) { throw new IllegalArgumentException("Combined schematic requires an unambiguous U or D track per route: "+route.id()); }
        return sides.iterator().next();
    }
    private static String render(CalculationNetwork network,Route route,Map<String,Double> ohmPerM,Map<String,String> lanes) {
        boolean combined=!lanes.isEmpty();
        Map<String,CalculationNode> nodes=new LinkedHashMap<>();
        for(var n:network.nodes()) { nodes.put(n.id(),n); }
        Map<String,List<CalculationBranch>> byLine=new LinkedHashMap<>();
        for(var b:network.branches()) { byLine.computeIfAbsent(b.sourceId(),k->new ArrayList<>()).add(b); }
        LinkedHashSet<String> places=new LinkedHashSet<>();
        String previous=null;
        for(String id:route.feedingLineIds()) {
            for(var b:byLine.getOrDefault(id,List.of())) {
                String a=place(nodes.get(b.fromNodeId())),z=place(nodes.get(b.toNodeId()));
                if(previous==null) {
                    int ix=route.feedingLineIds().indexOf(id);
                    if(ix+1<route.feedingLineIds().size()) {
                        String next=route.feedingLineIds().get(ix+1);
                        if(byLine.getOrDefault(next,List.of()).stream().anyMatch(c->a.equals(place(nodes.get(c.fromNodeId())))||a.equals(place(nodes.get(c.toNodeId()))))) {
                            places.add(z);places.add(a);previous=a;continue;
                        }
                    }
                } else if(previous.equals(z)) { places.add(z);places.add(a);previous=a;continue; }
                places.add(a);places.add(z);previous=z;
            }
        }
        Set<String> selected=new LinkedHashSet<>(route.feedingLineIds());selected.addAll(route.returnLineIds());
        Set<String> routeNodes=new HashSet<>();
        for(String id:selected) { for(var b:byLine.getOrDefault(id,List.of())) {
            places.add(place(nodes.get(b.fromNodeId())));places.add(place(nodes.get(b.toNodeId())));
            routeNodes.add(b.fromNodeId());routeNodes.add(b.toNodeId());
        } }
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
                if(!place(nodes.get(terminal)).equals(place(nodes.get(from)))) {
                    throw new IllegalArgumentException("Station terminals have different locations: "+id);
                }
            }
            stations.add(station);
        }
        Map<String,Integer> x=new LinkedHashMap<>();int col=0;
        for(String p:places) { x.put(p,120+STEP*col++); }
        StringBuilder out=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"");
        out.append(Math.max(500,STEP*(col-1)+240)).append("\" height=\"580\" viewBox=\"0 0 ").append(Math.max(500,STEP*(col-1)+240)).append(" 580\">\n");
        out.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/><g font-family=\"Arial\">\n");
        text(out,20,28,"Route "+route.id()+" | selected route, topology only",14,"#222","start");
        text(out,20,50,combined?"Red: U; blue: D. Separate feeding and return lanes; layout not to distance scale.":"Red: feeding; blue: return. Internal buses retained; layout not to distance scale.",11,"#555","start");
        for(var entry:x.entrySet()) { text(out,entry.getValue(),85,entry.getKey()+" m",9,"#555","middle"); }
        for(Station s:stations) {
            int sx=x.get(place(nodes.get(s.from())));
            out.append("<g><title>").append(xml(s.id()+" | "+s.type()+" | terminals: "+s.terminals())).append("</title><rect x=\"").append(sx-HALF).append("\" y=\"125\" width=\"160\" height=\"400\" rx=\"6\" fill=\"#f0fdf4\" stroke=\"#067647\"/></g>\n");
            text(out,sx,280,s.id(),14,"#067647","middle");text(out,sx,298,s.type(),9,"#067647","middle");
            String values=stationValues.get(s.id());
            if(values!=null) { String[] fields=values.split(" \\| ");text(out,sx,330,fields[0],9,"#067647","middle");text(out,sx,345,fields[1],9,"#067647","middle"); }
            // Separate straight internal feeding and return buses; never join them directly.
            line(out,sx-HALF,FEED,sx+HALF,FEED,"#b42318",true,"internal feeding bus | all feeding terminals interconnected");
            line(out,sx-HALF,RETURN,sx+HALF,RETURN,combined?"#b42318":"#175cd3",true,"internal return bus | left/right attachments are the same return node");
            if(combined) {
                line(out,sx-HALF,240,sx+HALF,240,"#175cd3",true,"D feeding | shares internal feeding bus with U");
                line(out,sx-HALF,480,sx+HALF,480,"#175cd3",true,"D return | shares internal return bus with U");
                text(out,sx,365,"U/D: common feeding bus",7,"#555","middle");
                text(out,sx,378,"U/D: common return bus",7,"#555","middle");
            }
            text(out,sx,315,"rectifier / load",8,"#555","middle");
            List<String> inactive=s.terminals().stream().filter(t->!routeNodes.contains(t)).sorted().toList();
            int y=210;
            for(String id:inactive) {
                int side=id.endsWith("LEFT")?-1:1;
                point(out,sx+side*HALF,y,"#999",id+" | no external line on selected route");
                text(out,sx+side*(HALF-4),y-6,id,7,"#666",side<0?"start":"end");y+=22;
            }
        }
        Set<String> labels=new HashSet<>();int feedingCount=0,returnCount=0;
        for(String id:selected) {
            boolean feeding=route.feedingLineIds().contains(id);int y=feeding?FEED:RETURN;String color=feeding?"#b42318":"#175cd3";
            if(combined) { String lane=lanes.get(id);boolean down=lane.startsWith("D");y=(feeding?FEED:RETURN)+(down?80:0);color=down?"#175cd3":"#b42318"; }
            for(var b:byLine.getOrDefault(id,List.of())) {
                int a=x.get(place(nodes.get(b.fromNodeId()))),z=x.get(place(nodes.get(b.toNodeId())));
                if(a==z) { throw new IllegalArgumentException("External line has coincident schematic locations: "+id); }
                int sign=a<z?1:-1;
                int from=a+(owner.containsKey(b.fromNodeId())?sign*HALF:0);
                int to=z-(owner.containsKey(b.toNodeId())?sign*HALF:0);
                line(out,from,y,to,y,color,false,id+" | total R="+b.resistanceOhm().asDouble()+" ohm");
                int mid=(from+to)/2;
                text(out,mid,y-36,id,8,color,"middle");
                Double r=ohmPerM.get(id);
                text(out,mid,y-23,r==null?"ohm/m unavailable":String.format(Locale.ROOT,"%.6g ohm/m",r),9,color,"middle");
                endpoint(out,b.fromNodeId(),from,y,sign,owner.containsKey(b.fromNodeId()),color,labels);
                endpoint(out,b.toNodeId(),to,y,-sign,owner.containsKey(b.toNodeId()),color,labels);
                if(feeding) { feedingCount++; } else { returnCount++; }
            }
        }
        text(out,20,558,"External branches drawn: feeding="+feedingCount+", return="+returnCount+". Return attachments on both sides represent one node.",10,"#555","start");
        return out.append("</g></svg>\n").toString();
    }
    private static void endpoint(StringBuilder out,String id,int x,int y,int direction,boolean station,String color,Set<String> labels) {
        point(out,x,y,color,id);
        String key=id+":"+x+":"+y;
        if(labels.add(key)) { text(out,x+(station?-direction*4:0),y+15,id,7,color,station?(direction>0?"end":"start"):"middle"); }
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
        out.append("<text x=\"").append(x).append("\" y=\"").append(y).append("\" font-size=\"").append(size).append("\" fill=\"").append(color).append("\" text-anchor=\"").append(anchor).append("\">").append(xml(value)).append("</text>\n");
    }
    private static String xml(String s) { return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
}
