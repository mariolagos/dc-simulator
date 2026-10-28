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
        return render(network,route,ohmPerM,Map.of(),Map.of(),context);
    }

    /** Shows every route in its own drawing lane, including shared solver branches. */
    public static String combinedSvg(CalculationNetwork network, List<Route> routes, Map<String,Double> ohmPerM) {
        return combinedSvg(network,routes,ohmPerM,Context.empty());
    }
    public static String combinedSvg(CalculationNetwork network,List<Route> routes,Map<String,Double> ohmPerM,Context context) {
        List<Route> ordered=new ArrayList<>(routes);
        ordered.sort(Comparator.comparing(Route::id));
        if (ordered.isEmpty()) throw new IllegalArgumentException("No routes selected");
        Map<String,List<String>> lanes=new LinkedHashMap<>();
        Map<String,String> routeLabels=new LinkedHashMap<>();
        Set<String> feed=new LinkedHashSet<>(), returning=new LinkedHashSet<>();
        for (int index=0;index<ordered.size();index++) {
            Route r=ordered.get(index);
            String lane=Integer.toString(index);
            routeLabels.put(lane,r.id());
            for (String id:new LinkedHashSet<>(r.feedingLineIds())) {
                lanes.computeIfAbsent(id,k->new ArrayList<>()).add(lane);
                feed.add(id);
            }
            for (String id:new LinkedHashSet<>(r.returnLineIds())) {
                if (r.feedingLineIds().contains(id))
                    throw new IllegalArgumentException("Line is both feeding and return: "+id);
                lanes.computeIfAbsent(id,k->new ArrayList<>()).add(lane);
                returning.add(id);
            }
        }
        if (!Collections.disjoint(feed,returning))
            throw new IllegalArgumentException("Routes disagree on feeding/return line classification");
        String title=ordered.stream().map(Route::id).reduce((a,b)->a+" + "+b).orElseThrow();
        // Shared branches have one drawing per route, but remain one solver branch.
        return render(network,new Route(title,new ArrayList<>(feed),new ArrayList<>(returning)),
                ohmPerM,lanes,routeLabels,context);
    }
    private static String render(CalculationNetwork network,Route route,Map<String,Double> ohmPerM,Map<String,List<String>> lanes,Map<String,String> routeLabels,Context context) {
        boolean combined=!lanes.isEmpty();
        int returnY=combined?Math.max(RETURN,FEED+80*routeLabels.size()+80):RETURN;
        int height=combined?Math.max(580,returnY+80*(routeLabels.size()-1)+100):580;
        Map<String,CalculationNode> nodes=new LinkedHashMap<>();
        for(var n:network.nodes()) { nodes.put(n.id(),n); }
        Map<String,List<CalculationBranch>> byLine=new LinkedHashMap<>();
        for(var b:network.branches()) { byLine.computeIfAbsent(b.sourceId(),k->new ArrayList<>()).add(b); }
        Set<String> selected=new LinkedHashSet<>(route.feedingLineIds());selected.addAll(route.returnLineIds());
        Layout layout=new Layout(nodes,network.branches(),selected,ohmPerM,context);
        for(var e:network.elements()) if(e instanceof IdealConnectionElement c)
            layout.joinDrawing(nodes.get(c.fromNodeId()),nodes.get(c.toNodeId()));
        for(var b:network.branches()) if(b.sourceId()!=null&&b.sourceId().startsWith("internal_"))
            layout.joinDrawing(nodes.get(b.fromNodeId()),nodes.get(b.toNodeId()));
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
            }
                for(var e2:network.elements()) if(e2 instanceof IdealConnectionElement c &&
                        (terminals.contains(c.fromNodeId())||terminals.contains(c.toNodeId()))) {
                    changed|=terminals.add(c.fromNodeId());changed|=terminals.add(c.toNodeId());
                }
            } while(changed);
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
        int step=Math.max(STEP,selected.stream().filter(Objects::nonNull)
                .mapToInt(id->id.length()*7+120).max().orElse(STEP));
        Map<String,Integer> x=new LinkedHashMap<>();int col=0;
        for(String p:places) { x.put(p,120+step*col++); }
        StringBuilder out=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"");
        out.append(Math.max(1100,step*(col-1)+240)).append("\" height=\"").append(height).append("\" viewBox=\"0 0 ").append(Math.max(1100,step*(col-1)+240)).append(" ").append(height).append("\">\n");
        out.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/><g font-family=\"Arial\">\n");
        text(out,20,28,"Route "+route.id()+(combined?" | combined topology":" | selected route, topology only"),14,"#222","start");
        text(out,20,50,combined?"Separate lanes per route in title order, alternating red/blue. Shared line IDs are views of the same solver branch.":"Red: feeding; blue: return. Layout not to distance scale.",12,"#333","start");
        text(out,20,68,network.elements().stream().anyMatch(e->e instanceof IdealConnectionElement)
                ? "Compiled station wiring; open internal connections remain disconnected. Internal wiring not drawn."
                : "Model assumption: all feeder breakers closed. Internal station connections not drawn.",9,"#555","start");
        Map<String,List<String>> columnReferences=new LinkedHashMap<>();
        for(var entry:x.entrySet()) {
            Set<String> references=new LinkedHashSet<>();Set<Double> metres=new LinkedHashSet<>();
            for(String id:routeNodes) {
                var node=nodes.get(id);
                if(!layout.place(node).equals(entry.getKey())) continue;
                var coordinate=context.railwayPositions().get(id);
                if(coordinate!=null) references.add(railwayLabel(coordinate));
                metres.add(node.positionM());
            }
            columnReferences.put(entry.getKey(),List.copyOf(references));
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
        // One physical transition symbol per drawing column; feeding and return
        // keep their own ports, but no longer repeat coordinate descriptions.
        Set<String> junctionColumns=new HashSet<>();
        for(String id:routeNodes) {
            CalculationNode node=nodes.get(id);
            if(!layout.isJunction(node))continue;
            String column=layout.place(node);
            if(!junctionColumns.add(column))continue;
            int jx=x.get(column),jy=(FEED+returnY)/2+(southOnly?80:0);
            boolean trackChange=false;
            for(var branch:network.branches()) {
                if(!layout.connectors.contains(branch.id()))continue;
                var a=nodes.get(branch.fromNodeId());var z=nodes.get(branch.toNodeId());
                if(!layout.place(a).equals(column))continue;
                var ca=context.railwayPositions().get(a.id());var cz=context.railwayPositions().get(z.id());
                String ta=ca==null?a.trackId():ca.getTrackId(),tz=cz==null?z.trackId():cz.getTrackId();
                if(ta!=null&&!ta.isBlank()&&tz!=null&&!tz.isBlank()&&!ta.equals(tz))trackChange=true;
            }
            out.append("<g class=\"physical-junction\"><title>")
                    .append(xml(String.join(" / ",columnReferences.getOrDefault(column,List.of()))))
                    .append("</title><path d=\"M ").append(jx).append(' ').append(jy-9)
                    .append(" L ").append(jx+9).append(' ').append(jy).append(" L ").append(jx).append(' ').append(jy+9)
                    .append(" L ").append(jx-9).append(' ').append(jy).append(" Z\" fill=\"white\" stroke=\"#475467\" stroke-width=\"1.5\"/>");
            if(trackChange)out.append("<path class=\"track-change-arrow\" d=\"M ").append(jx-5).append(' ').append(jy)
                    .append(" h 10 m -4 -3 l 4 3 -4 3\" fill=\"none\" stroke=\"#475467\"/>");
            text(out,jx,jy+27,trackChange?"Track change":"Section change",11,"#475467","middle");
            out.append("</g>\n");
        }
        for(Station s:stations) {
            int sx=x.get(layout.place(nodes.get(s.from())));
            out.append("<g><title>").append(xml(s.id()+" | "+s.type()+" | terminals: "+s.terminals())).append("</title><rect x=\"").append(sx-HALF).append("\" y=\"125\" width=\"160\" height=\"").append(height-180).append("\" rx=\"6\" fill=\"#f0fdf4\" stroke=\"#067647\"/></g>\n");
            text(out,sx,280,s.id(),14,"#067647","middle");text(out,sx,298,s.type(),9,"#067647","middle");
            String values=stationValues.get(s.id());
            if(values!=null) { String[] fields=values.split(" \\| ");text(out,sx,330,fields[0],9,"#067647","middle");text(out,sx,345,fields[1],9,"#067647","middle"); }
            // Omit internal connections: horizontal strokes could imply direct gap bridges.
            if(combined) {
                text(out,sx,365,"Common feeding bus",12,"#333","middle");
                text(out,sx,383,"Common return bus",12,"#333","middle");
            }
            text(out,sx,315,"rectifier / load",8,"#555","middle");
            // Only terminals connected to this route are drawn. Other route views
            // must not introduce extra station ports in this schematic.

        }
        Set<String> labels=new HashSet<>();int feedingCount=0,returnCount=0;
        for(String id:selected) {
            boolean feeding=route.feedingLineIds().contains(id);
            for (String lane:combined?lanes.get(id):List.of("0")) {
                int laneIndex=Integer.parseInt(lane);
                int y=feeding?FEED:returnY;
                String color=feeding?"#b42318":"#175cd3";
                if(southOnly) y+=80;
                if(combined) { y+=80*laneIndex;color=laneIndex%2==0?"#b42318":"#175cd3"; }
                for(var b:byLine.getOrDefault(id,List.of())) {
                    int a=x.get(layout.place(nodes.get(b.fromNodeId()))),z=x.get(layout.place(nodes.get(b.toNodeId())));
                    if(a==z) {
                        if(layout.connectors.contains(b.id())) continue;
                        throw new IllegalArgumentException("External line has coincident schematic locations: "+id);
                    }
                    int sign=a<z?1:-1;
                    int from=a+(owner.containsKey(b.fromNodeId())?sign*HALF:0);
                    int to=z-(owner.containsKey(b.toNodeId())?sign*HALF:0);
                    line(out,from,y,to,y,color,false,id+(combined?" | routes: "+routeLabels.get(lane):"")+" | total R="+b.resistanceOhm().asDouble()+" ohm");
                    int mid=(from+to)/2;
                    text(out,mid,y-36,id,8,color,"middle");
                    if(combined) text(out,mid,y-52,routeLabels.get(lane),9,color,"middle");
                    Double r=ohmPerM.get(id);
                    text(out,mid,y-23,r==null?"ohm/m unavailable":String.format(Locale.ROOT,"%.6g ohm/m",r),9,color,"middle");
                    endpoint(out,b.fromNodeId(),from,y,sign,owner.containsKey(b.fromNodeId()),color,labels,layout.isJunction(nodes.get(b.fromNodeId())),columnReferences.get(layout.place(nodes.get(b.fromNodeId()))));
                    endpoint(out,b.toNodeId(),to,y,-sign,owner.containsKey(b.toNodeId()),color,labels,layout.isJunction(nodes.get(b.toNodeId())),columnReferences.get(layout.place(nodes.get(b.toNodeId()))));
                    if(feeding) { feedingCount++; } else { returnCount++; }
                }
            }
        }
        text(out,20,height-22,(combined?"Branch views drawn: ":"External branches drawn: ")+" feeding="+feedingCount+", return="+returnCount+". Co-located junction connectors omitted: "+layout.connectors.size()+". Solver nodes unchanged.",10,"#555","start");
        return out.append("</g></svg>\n").toString();
    }
    private static void endpoint(StringBuilder out,String id,int x,int y,int direction,boolean station,String color,Set<String> labels,boolean junction,List<String> references) {
        // A physical junction can contain several distinct electrical terminals.
        // Deduplicate symbols by location, but retain every terminal label.
        String pointKey="point:"+(junction?"junction":id)+":"+x+":"+y;
        if(labels.add(pointKey)) {
            point(out,x,y,color,junction?"Physical junction; separate solver nodes retained":id);
        }
        String labelKey="label:"+id+":"+x+":"+y;
        if(labels.add(labelKey)) {
            // The station's left terminal faces left (direction < 0).
            int labelY=y+(station && direction>0?-8:15);
            text(out,x+(station?-direction*4:0),labelY,id,12,color,station?(direction>0?"end":"start"):"middle");
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
        final Set<String> junctionNodeIds=new HashSet<>();
        Layout(Map<String,CalculationNode> nodes,List<CalculationBranch> branches,Set<String> selected,
               Map<String,Double> ohmPerM,Context context) {
            for(var node:nodes.values()) parent.put(RouteSchematic.place(node),RouteSchematic.place(node));
            for(var b:branches) {
                // A station at a junction can have terminals on an unselected third leg.
                // Collapse all declared physical connector positions for layout, while
                // retaining selected-only connector counts and unchanged solver nodes.
                boolean visibleConnector = selected.contains(b.sourceId());
                if (b.schematicConnector()) {
                    String a=place(nodes.get(b.fromNodeId())),z=place(nodes.get(b.toNodeId()));
                    if(!a.equals(z)) parent.put(z,a);
                    if(visibleConnector) connectors.add(b.id());junctionNodeIds.add(b.fromNodeId());junctionNodeIds.add(b.toNodeId());
                    continue;
                }
                Double r=ohmPerM.get(b.sourceId());
                if(r==null||r>1e-8||r<=0||b.resistanceOhm().asDouble()>1e-8) continue;
                var from=context.railwayPositions().get(b.fromNodeId());
                var to=context.railwayPositions().get(b.toNodeId());
                if(from==null||to==null) continue;
                boolean explicit=context.junctions().stream().anyMatch(j->
                        (sameCoordinate(from,j.getFrom())&&sameCoordinate(to,j.getTo()))||
                                (sameCoordinate(to,j.getFrom())&&sameCoordinate(from,j.getTo())));
                // Older WE input encodes section joins only as near-ideal grid
                // lines. Recognize that drawing convention on the same track;
                // ordinary low-resistance lines still need an explicit junction.
                boolean gridSectionJoin=r<=1e-12
                        && !from.getSectionId().equals(to.getSectionId())
                        && Objects.equals(from.getTrackId(),to.getTrackId());
                if(!explicit && !gridSectionJoin) continue;
                String a=place(nodes.get(b.fromNodeId())),z=place(nodes.get(b.toNodeId()));
                if(!a.equals(z)) parent.put(z,a);
                if(visibleConnector) connectors.add(b.id());junctionNodeIds.add(b.fromNodeId());junctionNodeIds.add(b.toNodeId());
            }
        }
        void joinDrawing(CalculationNode from,CalculationNode to) {
            String a=place(from),b=place(to);if(!a.equals(b))parent.put(b,a);
        }
        String place(CalculationNode node) {
            String key=RouteSchematic.place(node);
            while(!parent.get(key).equals(key)) key=parent.get(key);
            return key;
        }
        boolean isJunction(CalculationNode node) {
            if(junctionNodeIds.contains(node.id()))return true;
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
