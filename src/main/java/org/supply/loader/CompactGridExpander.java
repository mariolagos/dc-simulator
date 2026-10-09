package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.*;

/** Expands optional point/link input into the existing grid vocabulary. */
public final class CompactGridExpander {
    private static final List<String> TRACKS=List.of("NORTH","SOUTH");
    private static final List<String> TERMINALS=List.of("NORTH_POWER_LEFT","NORTH_POWER_RIGHT",
            "SOUTH_POWER_LEFT","SOUTH_POWER_RIGHT","NORTH_RETURN","SOUTH_RETURN");
    private CompactGridExpander() { }

    public static Config expand(Config dcsim) {
        dcsim=ElectricalObjectExpander.expand(dcsim);
        if(!dcsim.hasPath("grid")) return dcsim;
        Config grid=dcsim.getConfig("grid");
        if(!grid.hasPath("points") && !grid.hasPath("links")
                && (!grid.hasPath("power_installations") || grid.getConfigList("power_installations").stream().noneMatch(i->i.hasPath("positions"))))
            return dcsim;
        return dcsim.withValue("grid",ConfigFactory.parseMap(expandGrid(grid.root().unwrapped())).root());
    }

    // Pure map expansion also makes the generated vocabulary independently testable.
    static Map<String,Object> expandGrid(Map<String,Object> source) {
        Map<String,Object> grid=new LinkedHashMap<>(source);
        List<Map<String,Object>> nodes=objects(source,"nodes"), lines=objects(source,"lines");
        List<Map<String,Object>> installations=new ArrayList<>();
        Map<String,Map<String,String>> ports=new LinkedHashMap<>();
        Set<String> nodeIds=new HashSet<>(), lineIds=new HashSet<>();
        Set<Set<String>> connectedPairs=new HashSet<>();
        for(var node:nodes) unique(nodeIds,string(node,"node_id"),"node_id");
        for(var line:lines) {
            unique(lineIds,string(line,"line_id"),"line_id");
            connectedPairs.add(Set.of(string(line,"node_from_id"),string(line,"node_to_id")));
        }
        for(var original:objects(source,"power_installations")) {
            var inst=new LinkedHashMap<String,Object>(original);
            String id=string(inst,"installation_id");
            if(inst.containsKey("positions")) {
                if(!"SIX_TERMINAL_RECTIFIER".equals(inst.get("installation_model")))
                    throw new IllegalArgumentException("positions requires SIX_TERMINAL_RECTIFIER: "+id);
                Map<String,Object> positions=object(inst.get("positions"),"positions");
                exactKeys(positions,TRACKS,"positions for "+id);
                Map<String,Object> old=inst.containsKey("terminals")?object(inst.get("terminals"),"terminals"):Map.of();
                if(!old.isEmpty()) exactKeys(old,TERMINALS,"terminals for "+id);
                Map<String,Object> terminals=new LinkedHashMap<>();
                for(String name:TERMINALS) {
                    Object value=old.get(name);
                    Map<String,Object> spec=value instanceof String
                            ?new LinkedHashMap<>(Map.of("node_id",value))
                            :value==null?new LinkedHashMap<>():new LinkedHashMap<>(object(value,"terminal "+name));
                    spec.putIfAbsent("position_rwy",string(positions,name.startsWith("NORTH")?"NORTH":"SOUTH"));
                    terminals.put(name,spec);
                }
                inst.put("terminals",terminals);inst.remove("positions");
            }
            installations.add(inst);
            if("SIX_TERMINAL_RECTIFIER".equals(inst.get("installation_model"))) {
                Map<String,Object> terminals=inst.containsKey("terminals")?object(inst.get("terminals"),"terminals"):Map.of();
                if(!terminals.isEmpty()) exactKeys(terminals,TERMINALS,"terminals for "+id);
                Map<String,String> ids=new LinkedHashMap<>();
                for(String name:TERMINALS) {
                    Object value=terminals.get(name);
                    String nid=value instanceof String?(String)value:value instanceof Map<?,?>
                            ?optionalString(object(value,name),"node_id",defaultNode(id,name)):defaultNode(id,name);
                    ids.put(name,nid);
                    // Generated terminal nodes are added later by InstallationFactory.
                    if(!(value instanceof String)) unique(nodeIds,nid,"node_id");
                }
                putPorts(ports,id+".LEFT",Map.of("NORTH_F",ids.get("NORTH_POWER_LEFT"),"SOUTH_F",ids.get("SOUTH_POWER_LEFT"),
                        "NORTH_R",ids.get("NORTH_RETURN"),"SOUTH_R",ids.get("SOUTH_RETURN")));
                putPorts(ports,id+".RIGHT",Map.of("NORTH_F",ids.get("NORTH_POWER_RIGHT"),"SOUTH_F",ids.get("SOUTH_POWER_RIGHT"),
                        "NORTH_R",ids.get("NORTH_RETURN"),"SOUTH_R",ids.get("SOUTH_RETURN")));
            }
        }
        for(var point:objects(source,"points")) {
            String id=string(point,"point_id");
            if(id.contains(".")) throw new IllegalArgumentException("point_id must not contain '.': "+id);
            Map<String,Object> positions=object(point.get("positions"),"positions for point "+id);
            exactKeys(positions,TRACKS,"positions for point "+id);
            Map<String,Object> overrides=point.containsKey("node_ids")?object(point.get("node_ids"),"node_ids"):Map.of();
            if(!Set.of("NORTH_F","NORTH_R","SOUTH_F","SOUTH_R").containsAll(overrides.keySet()))
                throw new IllegalArgumentException("Unknown point node role: "+overrides.keySet());
            Map<String,String> ids=new LinkedHashMap<>();
            for(String track:TRACKS) for(String kind:List.of("F","R")) {
                String role=track+"_"+kind;
                String nid=optionalString(overrides,role,kind+"_"+id+"_"+(track.equals("NORTH")?"N":"S"));
                unique(nodeIds,nid,"node_id");
                nodes.add(new LinkedHashMap<>(Map.of("node_id",nid,"position_rwy",string(positions,track))));
                ids.put(role,nid);
            }
            putPorts(ports,id,ids);
        }
        Map<String,Object> defaults=source.containsKey("link_defaults")?object(source.get("link_defaults"),"link_defaults"):Map.of();
        for(var link:objects(source,"links")) {
            String from=string(link,"from"),to=string(link,"to");
            var start=ports.get(from);var end=ports.get(to);
            if(start==null||end==null) throw new IllegalArgumentException("Unknown grid link endpoint: "+from+" -> "+to);
            List<String> tracks=link.containsKey("tracks")?strings(link.get("tracks"),"tracks"):TRACKS;
            if(tracks.isEmpty()||!TRACKS.containsAll(tracks)||new HashSet<>(tracks).size()!=tracks.size())
                throw new IllegalArgumentException("tracks must contain distinct NORTH/SOUTH entries");
            List<String> feedIds=link.containsKey("feeding_line_ids")?strings(link.get("feeding_line_ids"),"feeding_line_ids"):null;
            List<String> returnIds=link.containsKey("return_line_ids")?strings(link.get("return_line_ids"),"return_line_ids"):null;
            if(feedIds!=null&&feedIds.size()!=tracks.size()||returnIds!=null&&returnIds.size()!=tracks.size())
                throw new IllegalArgumentException("Line ID list must match tracks for "+from+" -> "+to);
            for(int i=0;i<tracks.size();i++) for(String kind:List.of("F","R")) {
                String a=start.get(tracks.get(i)+"_"+kind),b=end.get(tracks.get(i)+"_"+kind);
                if(a.equals(b)) throw new IllegalArgumentException("Grid link connects same terminal: "+a);
                var ids=kind.equals("F")?feedIds:returnIds;
                String lid=ids==null?a+"-"+b:ids.get(i);
                unique(lineIds,lid,"line_id");
                if(!connectedPairs.add(Set.of(a,b)))
                    throw new IllegalArgumentException("Duplicate generated connection: "+a+" -> "+b);
                String key=kind.equals("F")?"feeding_resistance_ohm_per_m":"return_resistance_ohm_per_m";
                Object resistance=link.containsKey(key)?link.get(key):link.containsKey("resistance_ohm_per_m")?link.get("resistance_ohm_per_m"):
                        defaults.containsKey(key)?defaults.get(key):defaults.get("resistance_ohm_per_m");
                if(!(resistance instanceof Number number)||!Double.isFinite(number.doubleValue())||number.doubleValue()<=0)
                    throw new IllegalArgumentException("Positive finite "+key+" required for "+from+" -> "+to);
                lines.add(new LinkedHashMap<>(Map.of("line_id",lid,"node_from_id",a,"node_to_id",b,"resistance_ohm_per_m",((Number)resistance).doubleValue())));
            }
        }
        grid.put("nodes",nodes);grid.put("lines",lines);grid.put("power_installations",installations);
        grid.remove("points");grid.remove("links");grid.remove("link_defaults");
        return grid;
    }
    private static String defaultNode(String id,String terminal) {
        return switch(terminal) {
            case "NORTH_POWER_LEFT" -> "F_"+id+"_A";
            case "NORTH_POWER_RIGHT" -> "F_"+id+"_B";
            case "SOUTH_POWER_LEFT" -> "F_"+id+"_C";
            case "SOUTH_POWER_RIGHT" -> "F_"+id+"_D";
            case "NORTH_RETURN" -> "R_"+id+"_N";
            case "SOUTH_RETURN" -> "R_"+id+"_S";
            default -> throw new IllegalArgumentException("Unknown terminal: "+terminal);
        };
    }
    private static void putPorts(Map<String,Map<String,String>> ports,String id,Map<String,String> value) {
        if(ports.putIfAbsent(id,value)!=null) throw new IllegalArgumentException("Duplicate endpoint: "+id);
    }
    private static void unique(Set<String> ids,String id,String label) {
        if(!ids.add(id)) throw new IllegalArgumentException("Duplicate "+label+": "+id);
    }
    private static String string(Map<String,Object> values,String key) {
        Object v=values.get(key);
        if(!(v instanceof String s)||s.isBlank()) throw new IllegalArgumentException("Nonblank string required: "+key);
        return (String)v;
    }
    private static String optionalString(Map<String,Object> values,String key,String fallback) {
        return values.containsKey(key)?string(values,key):fallback;
    }
    private static void exactKeys(Map<String,Object> map,List<String> keys,String label) {
        if(!map.keySet().equals(new HashSet<>(keys))) throw new IllegalArgumentException("Expected "+keys+" in "+label);
    }
    private static Map<String,Object> object(Object value,String label) {
        if(!(value instanceof Map<?,?> map)) throw new IllegalArgumentException("Object required: "+label);
        Map<String,Object> out=new LinkedHashMap<>();
        map.forEach((k,v)->out.put((String)k,v));return out;
    }
    private static List<Map<String,Object>> objects(Map<String,Object> source,String key) {
        if(!source.containsKey(key)) return new ArrayList<>();
        if(!(source.get(key) instanceof List<?> list)) throw new IllegalArgumentException("List required: "+key);
        List<Map<String,Object>> out=new ArrayList<>();for(Object value:list) out.add(object(value,key));return out;
    }
    private static List<String> strings(Object value,String label) {
        if(!(value instanceof List<?> list)) throw new IllegalArgumentException("List required: "+label);
        List<String> out=new ArrayList<>();for(Object v:list) {
            if(!(v instanceof String s)||s.isBlank()) throw new IllegalArgumentException("Nonblank string required in "+label);
            out.add((String)v);
        }return out;
    }
}
