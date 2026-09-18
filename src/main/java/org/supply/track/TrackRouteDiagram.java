package org.supply.track;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

/** Linear track view. Distances come from RouteView, never from kilometre labels. */
public final class TrackRouteDiagram {
    private TrackRouteDiagram() { }
    public record ObjectPoint(String kind, String name, RwyCoordinate coordinate) { }
    private record Event(String kind, String name, RwyCoordinate coordinate) { }

    public static void write(LoadedTrackModel model, String selected, Path directory,
                             StringBuilder detail, List<String> warnings) throws IOException {
        write(model,selected,directory,detail,warnings,List.of());
    }
    public static void write(LoadedTrackModel model, String selected, Path directory,
                             StringBuilder detail, List<String> warnings, List<ObjectPoint> objects) throws IOException {
        Files.createDirectories(directory);
        List<RouteView> routes = model.getRouteViewsById().values().stream()
                .filter(r -> selected == null || selected.equals(r.getRouteId()))
                .sorted(Comparator.comparing(RouteView::getRouteId)).toList();
        if (routes.isEmpty()) {
            warnings.add("No track route view available" + (selected == null ? "" : " for " + selected));
        }
        int index = 0;
        for (RouteView route : routes) {
            String name = route.getRouteId().replaceAll("[^A-Za-z0-9_.-]", "_");
            Path file = directory.resolve(String.format(Locale.ROOT,"track_route_%03d_%s.svg", ++index, name));
            Files.writeString(file, bandSvg(model, route, objects), StandardCharsets.UTF_8);
            detail.append("Track route diagram: ").append(file).append('\n');
        }
    }

    public static String svg(LoadedTrackModel model, RouteView route) {
        return bandSvg(model,route,List.of());
    }

    public static String bandSvg(LoadedTrackModel model, RouteView route,List<ObjectPoint> objects) {
        TreeMap<Double,LinkedHashSet<Event>> rows=new TreeMap<>();
        List<PathSample> samples=route.getSamples();
        add(rows,samples.get(0).getPathPositionM(),new Event("boundary","Route start",samples.get(0).getRailwayCoordinate()));
        PathSample end=samples.get(samples.size()-1);
        add(rows,end.getPathPositionM(),new Event("boundary","Route end (not necessarily end of track)",end.getRailwayCoordinate()));
        for(Station station:model.getStations()) {
            for(double p:positions(route,station.getPosition())) add(rows,p,new Event("station",station.getName(),station.getPosition()));
        }
        for(TrackJunction junction:model.getJunctions()) {
            // Transitions below show both coordinate references once. Only show
            // configured connections separately when they are not a route transition.
            boolean transition=false;
            for(int i=0;i<samples.size()-1;i++) {
                var a=samples.get(i).getRailwayCoordinate();
                var b=samples.get(i+1).getRailwayCoordinate();
                if((at(a,junction.getFrom())&&at(b,junction.getTo())) ||
                   (at(b,junction.getFrom())&&at(a,junction.getTo()))) transition=true;
            }
            if(!transition) {
                String name="Connection: "+coordinate(junction.getFrom())+" <-> "+coordinate(junction.getTo());
                for(double p:positions(route,junction.getFrom())) add(rows,p,new Event("junction",name,junction.getFrom()));
                for(double p:positions(route,junction.getTo())) add(rows,p,new Event("junction",name,junction.getTo()));
            }
        }
        for(ObjectPoint object:objects) {
            for(double p:positions(route,object.coordinate())) add(rows,p,new Event(object.kind(),object.name(),object.coordinate()));
        }
        for(int i=0;i<samples.size()-1;i++) {
            PathSample a=samples.get(i),b=samples.get(i+1);
            if(!sameTrack(a.getRailwayCoordinate(),b.getRailwayCoordinate())) {
                // Keep a real intervening route distance explicit; co-located
                // references (including the synthetic 1 mm offset) form one marker.
                if(b.getPathPositionM()-a.getPathPositionM()<=0.002) {
                    add(rows,b.getPathPositionM(),new Event("junction",
                            "Junction: "+coordinate(a.getRailwayCoordinate())+" / "+coordinate(b.getRailwayCoordinate()),b.getRailwayCoordinate()));
                } else {
                    add(rows,a.getPathPositionM(),new Event("transition","Section/track exit",a.getRailwayCoordinate()));
                    add(rows,b.getPathPositionM(),new Event("transition","Section/track entry",b.getRailwayCoordinate()));
                }
            }
        }
        double first=samples.get(0).getPathPositionM();
        double length=end.getPathPositionM()-first;
        // A common scale across routes: 60 SVG pixels per kilometre of route distance.
        double scale=0.06, top=130, bottom=top+length*scale;
        double previousLabel=top-28;
        Map<Event,Double> labelPositions=new IdentityHashMap<>();
        for(var row:rows.entrySet()) {
            double y=top+(row.getKey()-first)*scale;
            for(Event event:row.getValue()) {
                double labelY=Math.max(y,previousLabel+28);
                labelPositions.put(event,labelY);previousLabel=labelY;
            }
        }
        int height=(int)Math.ceil(Math.max(bottom,previousLabel)+80);
        StringBuilder out=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1100\" height=\"");
        out.append(height).append("\" viewBox=\"0 0 1100 ").append(height).append("\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><g font-family=\"Arial\">\n");
        text(out,30,30,"Track route: "+route.getRouteId(),18);
        text(out,30,54,"Top to bottom: route direction. Scale: 60 SVG px / km. Symbols at exact route distances; labels may be offset.",12);
        text(out,30,76,"Circle: station | square: substation | slash: connection | bar: route boundary | diamond: coordinate transition",11);
        out.append("<line x1=\"370\" y1=\"").append(top).append("\" x2=\"370\" y2=\"").append(bottom).append("\" stroke=\"#333\" stroke-width=\"2\"/>\n");
        // Configured board coordinates are retained at section segment boundaries.
        TreeMap<Double,Set<String>> boards=new TreeMap<>();
        for(TrackSection section:model.getSectionsById().values()) {
            for(RouteSegment segment:section.getSegments()) {
                for(RwyCoordinate coordinate:List.of(segment.getStartRwy(),segment.getEndRwy())) {
                    for(double position:positions(route,coordinate)) {
                        boards.computeIfAbsent(position,k->new TreeSet<>()).add(coordinate.getSectionId()+" "+coordinate.getPositionText());
                    }
                }
            }
        }
        double lastBoardLabel=top-14;
        for(var board:boards.entrySet()) {
            double y=top+(board.getKey()-first)*scale;
            out.append("<path d=\"M 366 ").append(y).append(" H 374\" stroke=\"#aaa\" stroke-width=\"1\"><title>")
                    .append(xml(String.join(", ",board.getValue()))).append("</title></path>\n");
            // Tiny close boundary ticks keep their tooltips, but omit colliding labels.
            if(y-lastBoardLabel>=14) {
                out.append("<text x=\"900\" y=\"").append(y+3).append("\" font-size=\"12\" fill=\"#444\">")
                        .append(xml(String.join(" / ",board.getValue()))).append("</text>\n");
                lastBoardLabel=y;
            }
        }
        for(double distance=0;distance<=length;distance+=1000) {
            double y=top+distance*scale;
            out.append("<path d=\"M 760 ").append(y).append(" H 770\" stroke=\"#aaa\"/>\n");
            out.append("<text x=\"780\" y=\"").append(y+4).append("\" font-size=\"12\" fill=\"#444\">")
                    .append(number(first+distance)).append(" m</text>\n");
        }
        for(var row:rows.entrySet()) {
            double y=top+(row.getKey()-first)*scale;
            for(Event event:row.getValue()) {
                double ey=labelPositions.get(event);symbol(out,event.kind(),y);
                if(Math.abs(ey-y)>0.1) {
                    out.append("<path d=\"M 361 ").append(y).append(" L 350 ").append(ey)
                            .append(" M 379 ").append(y).append(" L 390 ").append(ey)
                            .append("\" fill=\"none\" stroke=\"#bbb\" stroke-width=\"0.8\"/>\n");
                }
                out.append("<text x=\"340\" y=\"").append(ey+4).append("\" text-anchor=\"end\" font-size=\"12\">").append(xml(event.name())).append("</text>\n");
                text(out,400,ey+4,coordinate(event.coordinate())+"  "+number(row.getKey())+" m",14);
            }
        }
        return out.append("</g></svg>\n").toString();
    }
    private static boolean at(RwyCoordinate a,RwyCoordinate b) {
        return matches(b,a)&&a.getPositionM()==b.getPositionM();
    }
    private static String coordinate(RwyCoordinate c) {
        int p=c.getPositionM();
        return c.getSectionId()+" "+(p/1000)+"+"+String.format(Locale.ROOT,"%03d",Math.abs(p%1000))+
                (c.getTrackId()==null||c.getTrackId().isBlank()?"":" "+c.getTrackId());
    }
    private static void add(TreeMap<Double,LinkedHashSet<Event>> rows,double position,Event event) {
        var set=rows.computeIfAbsent(position,k->new LinkedHashSet<>());
        if(set.stream().noneMatch(e->e.kind().equals(event.kind())&&e.name().equals(event.name()))) set.add(event);
    }
    private static void symbol(StringBuilder out,String kind,double y) {
        switch(kind) {
            case "station" -> out.append("<circle cx=\"370\" cy=\"").append(y).append("\" r=\"6\" fill=\"white\" stroke=\"#067647\" stroke-width=\"2\"/>\n");
            case "substation" -> out.append("<rect x=\"363\" y=\"").append(y-7).append("\" width=\"14\" height=\"14\" fill=\"#fff4ed\" stroke=\"#b42318\" stroke-width=\"2\"/>\n");
            case "junction" -> out.append("<path d=\"M 360 ").append(y+8).append(" L 380 ").append(y-8).append("\" stroke=\"#175cd3\" stroke-width=\"3\"/>\n");
            case "boundary" -> out.append("<path d=\"M 359 ").append(y).append(" H 381\" stroke=\"#333\" stroke-width=\"4\"/>\n");
            default -> out.append("<path d=\"M 370 ").append(y-6).append(" L 376 ").append(y).append(" L 370 ").append(y+6).append(" L 364 ").append(y).append(" Z\" fill=\"white\" stroke=\"#666\"/>\n");
        }
    }
    private static String horizontalSvg(LoadedTrackModel model, RouteView route) {
        List<PathSample> samples = route.getSamples();
        double first = samples.get(0).getPathPositionM();
        double last = samples.get(samples.size()-1).getPathPositionM();
        List<String> events = new ArrayList<>();
        for (Station station : model.getStations()) {
            for (double position : positions(route, station.getPosition())) {
                events.add("Station " + station.getName() + " | " + number(position) + " m | " + station.getPosition());
            }
        }
        for (TrackJunction junction : model.getJunctions()) {
            Set<Double> at = new TreeSet<>(positions(route, junction.getFrom()));
            at.addAll(positions(route, junction.getTo()));
            for (double position : at) {
                events.add("Junction | " + number(position) + " m | " + junction.getFrom() + " <-> " + junction.getTo());
            }
        }
        int height = 270 + 24 * (samples.size()-1 + events.size());
        StringBuilder out = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1200\" height=\"");
        out.append(height).append("\" viewBox=\"0 0 1200 ").append(height).append("\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><g font-family=\"Arial\">\n");
        text(out,30,30,"Track route: " + route.getRouteId(),18);
        text(out,30,52,Objects.toString(route.getDescription(),""),12);
        text(out,30,74,"Route distance increases left to right. Railway coordinates retain their original direction.",12);
        for (int i=0;i<samples.size()-1;i++) {
            PathSample a=samples.get(i), b=samples.get(i+1);
            double x1=60+1080*(a.getPathPositionM()-first)/(last-first);
            double x2=60+1080*(b.getPathPositionM()-first)/(last-first);
            out.append("<line x1=\"").append(x1).append("\" y1=\"120\" x2=\"").append(x2)
                    .append("\" y2=\"120\" stroke=\"").append(sameTrack(a.getRailwayCoordinate(),b.getRailwayCoordinate())?"#175cd3":"#b42318")
                    .append("\" stroke-width=\"4\"><title>").append(xml(a.getRailwayCoordinate()+" -> "+b.getRailwayCoordinate())).append("</title></line>\n");
        }
        for (Station station : model.getStations()) {
            for (double position : positions(route,station.getPosition())) {
                double x=60+1080*(position-first)/(last-first);
                out.append("<circle cx=\"").append(x).append("\" cy=\"120\" r=\"6\" fill=\"#067647\"><title>")
                        .append(xml(station.getName()+" | "+number(position)+" m | "+station.getPosition())).append("</title></circle>\n");
                out.append("<text x=\"").append(x).append("\" y=\"105\" font-size=\"12\" text-anchor=\"middle\">")
                        .append(xml(station.getName())).append("</text>\n");
            }
        }
        for (TrackJunction junction : model.getJunctions()) {
            Set<Double> at=new TreeSet<>(positions(route,junction.getFrom()));at.addAll(positions(route,junction.getTo()));
            for(double position:at) {
                double x=60+1080*(position-first)/(last-first);
                out.append("<circle cx=\"").append(x).append("\" cy=\"132\" r=\"4\" fill=\"#b42318\"><title>")
                        .append(xml("Junction | "+junction.getFrom()+" <-> "+junction.getTo())).append("</title></circle>\n");
            }
        }
        text(out,60,150,number(first)+" m",11);text(out,1030,150,number(last)+" m",11);
        text(out,30,185,"Route intervals (blue: same track; red: section/track transition)",13);
        int y=210;
        for (int i=0;i<samples.size()-1;i++) {
            PathSample a=samples.get(i), b=samples.get(i+1);
            text(out,30,y,number(a.getPathPositionM())+" - "+number(b.getPathPositionM())+" m | length "+number(b.getPathPositionM()-a.getPathPositionM())+" m | "+a.getRailwayCoordinate()+" -> "+b.getRailwayCoordinate(),11);
            y+=24;
        }
        text(out,30,y,"Stations and explicit junctions on this route",13);y+=24;
        for (String event:events) { text(out,30,y,event,11);y+=24; }
        text(out,30,y+12,"Intervals are RouteView mapping intervals, not inferred physical track segments. No gradient/speed data in this model.",10);
        return out.append("</g></svg>\n").toString();
    }

    private static boolean sameTrack(RwyCoordinate a,RwyCoordinate b) {
        return a.getSectionId().equals(b.getSectionId()) && Objects.equals(a.getTrackId(),b.getTrackId());
    }
    private static boolean matches(RwyCoordinate point,RwyCoordinate sample) {
        return point.getSectionId().equals(sample.getSectionId()) &&
                (point.getTrackId()==null || point.getTrackId().isBlank() || Objects.equals(point.getTrackId(),sample.getTrackId()));
    }
    static List<Double> positions(RouteView route,RwyCoordinate point) {
        Set<Double> result=new TreeSet<>();List<PathSample> samples=route.getSamples();
        for (PathSample sample:samples) {
            if(matches(point,sample.getRailwayCoordinate()) && point.getPositionM()==sample.getRailwayCoordinate().getPositionM()) result.add(sample.getPathPositionM());
        }
        for(int i=0;i<samples.size()-1;i++) {
            PathSample a=samples.get(i),b=samples.get(i+1);RwyCoordinate from=a.getRailwayCoordinate(),to=b.getRailwayCoordinate();
            if(!sameTrack(from,to)||!matches(point,from)||from.getPositionM()==to.getPositionM()) continue;
            double fraction=((double)point.getPositionM()-from.getPositionM())/((double)to.getPositionM()-from.getPositionM());
            if(fraction>0 && fraction<1) result.add(a.getPathPositionM()+fraction*(b.getPathPositionM()-a.getPathPositionM()));
        }
        return List.copyOf(result);
    }
    private static String number(double value) { return String.format(Locale.ROOT,"%.1f",value); }
    private static String xml(String value) { return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
    private static void text(StringBuilder out,int x,double y,String value,int size) {
        out.append("<text x=\"").append(x).append("\" y=\"").append(y).append("\" font-size=\"").append(size).append("\" fill=\"#222\">").append(xml(value)).append("</text>\n");
    }
}
