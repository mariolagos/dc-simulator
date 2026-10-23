package org.supply.loader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.supply.track.RwyCoordinateParser;
import java.util.*;

/** Compiles electrical object descriptions into the established grid vocabulary. */
public final class ElectricalObjectExpander {
    private static final List<String> ROLES=List.of("N.F","S.F","N.R","S.R");
    private static final List<String> STATION_TERMINALS=List.of("A","B","C","D","N","S");
    private static final List<String> LEGACY_TERMINALS=List.of("NORTH_POWER_LEFT","NORTH_POWER_RIGHT",
            "SOUTH_POWER_LEFT","SOUTH_POWER_RIGHT","NORTH_RETURN","SOUTH_RETURN");
    public record Island(List<String> nodes,List<String> objects,List<String> enabledSources,boolean hasReference) { }
    public record Inspection(Map<String,Object> expandedGrid,List<String> incompleteConnections,List<Island> islands) {
        public void requireCalculationReady() {
            if(!incompleteConnections.isEmpty()) throw new IllegalArgumentException("Incomplete electrical connections: "+incompleteConnections);
            if(islands.size()!=1) throw new IllegalArgumentException("Possible electrical islands (current solver requires one connected network): "+islands);
            if(islands.get(0).enabledSources().isEmpty()) throw new IllegalArgumentException("Electrical network has no enabled substation: "+islands.get(0));
            if(!islands.get(0).hasReference()) throw new IllegalArgumentException("Electrical island has no potential reference: "+islands.get(0));
        }
    }
    private record Port(String key,String conductor,String position,String object,String kind) { }
    private ElectricalObjectExpander() { }

    public static Config expand(Config dcsim) {
        if(!dcsim.hasPath("grid.electricalObjects")) return dcsim;
        Inspection result=inspect(dcsim.getConfig("grid").root().unwrapped());
        result.requireCalculationReady();
        return dcsim.withValue("grid",ConfigFactory.parseMap(result.expandedGrid()).root());
    }

    /** Description inspection permits declared but unconnected lines. */
    public static Inspection inspect(Map<String,Object> input) {
        input=SwitchGearTypeExpander.expand(ConfigFactory.parseMap(input)).root().unwrapped();
        for(String key:List.of("nodes","lines","power_installations","installation_connections","points","links","switchgears"))
            if(input.containsKey(key)) throw new IllegalArgumentException("electricalObjects cannot be mixed with "+key);
        var objects=map(input.get("electricalObjects"),"electricalObjects");
        var stationTypes=optionalMap(input,"substationTypes");var lineTypes=optionalMap(input,"lineTypes");
        Map<String,Map<String,Object>> specs=new TreeMap<>();Set<String> ids=new HashSet<>();
        Map<String,Port> ports=new TreeMap<>();Map<String,List<String>> addresses=new HashMap<>();
        Map<String,String> parent=new HashMap<>();Set<String> associated=new HashSet<>();
        for(String name:new TreeSet<>(objects.keySet())) {
            identifier(name,"object reference");var local=map(objects.get(name),name);
            String kind=string(local,"kind");if(!Set.of("POINT","LINE","SUBSTATION").contains(kind)) throw new IllegalArgumentException("Unsupported electrical object kind: "+kind);
            Map<String,Object> spec=new LinkedHashMap<>();
            if(local.containsKey("type")) {
                String type=string(local,"type");var types=kind.equals("LINE")?lineTypes:kind.equals("SUBSTATION")?stationTypes:Map.<String,Object>of();
                if(!types.containsKey(type)) throw new IllegalArgumentException("Unknown "+kind+" type: "+type);
                var defaults=map(types.get(type),"type "+type);
                for(String forbidden:List.of("positions","id","kind","type")) if(defaults.containsKey(forbidden)) throw new IllegalArgumentException("Type must not define "+forbidden+": "+type);
                spec.putAll(defaults);
            }
            spec.putAll(local);
            Set<String> allowed=new HashSet<>(List.of("kind","id","type"));
            if(kind.equals("LINE")) allowed.addAll(List.of("conductors","feeding_resistance_ohm_per_m","return_resistance_ohm_per_m"));
            else allowed.add("positions");
            if(kind.equals("SUBSTATION")) allowed.addAll(List.of("terminal_model","emf_V","internal_resistance_ohm","rectifier_type","enabled","switchgear","orientation"));
            for(String field:spec.keySet()) if(!allowed.contains(field)) throw new IllegalArgumentException("Unknown field "+field+" for "+name);
            String id=spec.containsKey("id")?string(spec,"id"):name;identifier(id,"id");
            if(!ids.add(id)) throw new IllegalArgumentException("Duplicate object id: "+id);
            spec.put("id",id);specs.put(name,spec);
            if(kind.equals("LINE")) {
                List<String> roles=spec.containsKey("conductors")?strings(spec.get("conductors"),"conductors"):ROLES;
                if(roles.isEmpty()||!ROLES.containsAll(roles)||new HashSet<>(roles).size()!=roles.size()) throw new IllegalArgumentException("Invalid conductors for "+name);
                roles=ROLES.stream().filter(roles::contains).toList();
                spec.put("conductors",roles);
                for(String side:List.of("L","R")) {
                    List<String> group=new ArrayList<>();
                    for(String role:roles) {String key=name+"."+side+"."+role;addPort(ports,parent,addresses,new Port(key,role.endsWith("F")?"F":"R",null,name,kind));group.add(key);}
                    addresses.put(name+"."+side,List.copyOf(group));
                }
                for(String role:roles) positive(spec,role.endsWith("F")?"feeding_resistance_ohm_per_m":"return_resistance_ohm_per_m");
            } else {
                var positions=map(spec.get("positions"),"positions for "+name);
                if(!positions.keySet().equals(Set.of("N","S"))) throw new IllegalArgumentException("positions requires N and S: "+name);
                for(String track:List.of("N","S")) RwyCoordinateParser.parse(string(positions,track));
                if(kind.equals("POINT")) {
                    List<String> group=new ArrayList<>();for(String role:ROLES) {String key=name+"."+role;addPort(ports,parent,addresses,new Port(key,role.endsWith("F")?"F":"R",string(positions,role.substring(0,1)),name,kind));group.add(key);}
                    addresses.put(name,List.copyOf(group));
                } else {
                    if(!spec.containsKey("switchgear")&&!"SIX_TERMINAL_RECTIFIER".equals(string(spec,"terminal_model"))) throw new IllegalArgumentException("Only SIX_TERMINAL_RECTIFIER is supported: "+name);
                    positive(spec,"emf_V");positive(spec,"internal_resistance_ohm");
                    if(!Set.of("DIODE","THYRISTOR").contains(string(spec,"rectifier_type"))) throw new IllegalArgumentException("Unknown rectifier_type for "+name);
                    if(spec.containsKey("enabled")&&!(spec.get("enabled") instanceof Boolean)) throw new IllegalArgumentException("enabled must be boolean: "+name);
                    for(String terminal:STATION_TERMINALS) {String key=name+"."+terminal;String track=Set.of("A","B","N").contains(terminal)?"N":"S";
                        String orientation=spec.containsKey("orientation")?string(spec,"orientation"):"NORMAL";
                        if(!Set.of("NORMAL","REVERSED").contains(orientation)) throw new IllegalArgumentException("Unknown orientation: "+orientation);
                        if(orientation.equals("REVERSED")) track=track.equals("N")?"S":"N";
                        addPort(ports,parent,addresses,new Port(key,Set.of("N","S").contains(terminal)?"R":"F",string(positions,track),name,kind));}
                    boolean reversed="REVERSED".equals(spec.get("orientation"));
                    addresses.put(name+".L",reversed?List.of(name+".C",name+".A",name+".S",name+".N"):List.of(name+".A",name+".C",name+".N",name+".S"));
                    addresses.put(name+".R",reversed?List.of(name+".D",name+".B",name+".S",name+".N"):List.of(name+".B",name+".D",name+".N",name+".S"));
                    if(spec.containsKey("switchgear")) {
                        var names=SwitchGearCompiler.terminalIds(map(spec.get("switchgear"),"switchgear"));
                        if(!SwitchGearCompiler.hasFeedingGap(map(spec.get("switchgear"),"switchgear"))) {
                            // Continuous contact rail: a single electrical node per track, no artificial resistor.
                            union(parent,name+".A",name+".B");union(parent,name+".C",name+".D");
                        }
                        for(int i=0;i<LEGACY_TERMINALS.size();i++) {
                            String alias=name+"."+names.get(LEGACY_TERMINALS.get(i));
                            String port=name+"."+STATION_TERMINALS.get(i);
                            if(addresses.containsKey(alias)&&!addresses.get(alias).equals(List.of(port)))
                                throw new IllegalArgumentException("Terminal alias collides with existing address: "+alias);
                            addresses.put(alias,List.of(port));
                        }
                    }
                }
            }
        }
        Set<Set<String>> pairs=new HashSet<>();
        for(var association:objectList(input,"electricalAssociations")) {
            for(String field:association.keySet()) if(!Set.of("from","to").contains(field)) throw new IllegalArgumentException("Unknown association field: "+field);
            String from=string(association,"from"),to=string(association,"to");var a=addresses.get(from);var b=addresses.get(to);
            if(a==null||b==null) throw new IllegalArgumentException("Unknown electrical association: "+from+" -> "+to);
            if(a.size()!=b.size()) throw new IllegalArgumentException("Association groups have different sizes: "+from+" -> "+to);
            for(int i=0;i<a.size();i++) {
                String x=a.get(i),y=b.get(i);
                if(x.equals(y)||!pairs.add(Set.of(x,y))) throw new IllegalArgumentException("Duplicate/self electrical association: "+x+" -> "+y);
                if(!ports.get(x).conductor().equals(ports.get(y).conductor())) throw new IllegalArgumentException("Cannot associate feeding with return: "+x+" -> "+y);
                associated.add(x);associated.add(y);union(parent,x,y);
            }
        }
        Map<String,List<Port>> groups=new TreeMap<>();for(var p:ports.values()) groups.computeIfAbsent(root(parent,p.key()),k->new ArrayList<>()).add(p);
        Map<String,String> nodeForPort=new HashMap<>();Map<String,String> positionForNode=new TreeMap<>();Map<String,Set<String>> owners=new TreeMap<>();List<String> incomplete=new ArrayList<>();
        for(var group:groups.values()) {
            List<Port> physical=group.stream().filter(p->p.position()!=null).sorted(Comparator.comparing((Port p)->p.kind().equals("SUBSTATION")?0:1).thenComparing(Port::key)).toList();
            if(physical.isEmpty()) {for(var p:group) incomplete.add(p.key()+" has no positioned connection");continue;}
            Port first=physical.get(0);String identity=coordinate(first.position());
            for(var p:physical) if(!identity.equals(coordinate(p.position()))) throw new IllegalArgumentException("Association joins different railway positions: "+first.key()+" and "+p.key());
            String ref=first.key();String name=first.object();String node=specs.get(name).get("id")+ref.substring(name.length());
            positionForNode.put(node,first.position());Set<String> owning=new TreeSet<>();
            for(var p:group) {nodeForPort.put(p.key(),node);owning.add(p.object());}owners.put(node,owning);
        }
        for(var p:ports.values()) if(p.kind().equals("LINE")&&!associated.contains(p.key())) incomplete.add(p.key()+" is not connected");
        Map<String,Object> output=new LinkedHashMap<>(input);List<Map<String,Object>> nodes=new ArrayList<>(),lines=new ArrayList<>(),stations=new ArrayList<>(),switchgears=new ArrayList<>(),connections=new ArrayList<>();Map<String,Set<String>> graph=new TreeMap<>();Map<String,String> sourceNode=new TreeMap<>();
        positionForNode.forEach((id,pos)->{nodes.add(Map.of("node_id",id,"position_rwy",pos));graph.put(id,new TreeSet<>());});
        for(var entry:specs.entrySet()) {
            String name=entry.getKey();var spec=entry.getValue();String kind=string(spec,"kind"),id=string(spec,"id");
            if(kind.equals("LINE")) {
                for(String role:spec.containsKey("conductors")?strings(spec.get("conductors"),"conductors"):ROLES) {
                    String a=nodeForPort.get(name+".L."+role),b=nodeForPort.get(name+".R."+role);
                    if(a==null||b==null) continue;if(a.equals(b)) throw new IllegalArgumentException("Line has identical endpoints: "+name+"."+role);
                    lines.add(Map.of("line_id",id+"."+role,"node_from_id",a,"node_to_id",b,"resistance_ohm_per_m",positive(spec,role.endsWith("F")?"feeding_resistance_ohm_per_m":"return_resistance_ohm_per_m")));edge(graph,a,b);
                }
            } else if(kind.equals("SUBSTATION")&&spec.containsKey("switchgear")) {
                Map<String,String> external=new LinkedHashMap<>();
                for(int i=0;i<LEGACY_TERMINALS.size();i++) external.put(LEGACY_TERMINALS.get(i),nodeForPort.get(name+"."+STATION_TERMINALS.get(i)));
                var compiled=SwitchGearCompiler.compile(id,map(spec.get("switchgear"),"switchgear"),
                        optionalMap(input,"conductorTypes"),external,string(map(spec.get("positions"),"positions"),"N"));
                for(var node:compiled.additionalNodePositions().entrySet()) {
                    if(positionForNode.putIfAbsent(node.getKey(),node.getValue())!=null) throw new IllegalArgumentException("Switchgear node collision: "+node.getKey());
                    nodes.add(Map.of("node_id",node.getKey(),"position_rwy",node.getValue()));
                    graph.put(node.getKey(),new TreeSet<>());owners.put(node.getKey(),Set.of(name));
                }
                switchgears.add(SwitchGearCompiler.generated(compiled.gear()));
                Map<String,String> terminalNodes=new HashMap<>();compiled.gear().terminals().forEach(t->terminalNodes.put(t.id(),t.nodeId()));
                compiled.gear().cables().forEach(c->edge(graph,terminalNodes.get(c.fromTerminalId()),terminalNodes.get(c.toTerminalId())));
                String positive=terminalNodes.get("POS"),negative=terminalNodes.get("NEG");
                Map<String,Object> station=new LinkedHashMap<>();station.put("installation_id",id);station.put("installation_category","SUBSTATION");
                for(String field:List.of("emf_V","internal_resistance_ohm","rectifier_type")) station.put(field,spec.get(field));
                station.put("enabled",spec.getOrDefault("enabled",true));stations.add(station);
                connections.add(Map.of("installation_id",id,"node_id",positive,"connection_type","FEEDING"));
                connections.add(Map.of("installation_id",id,"node_id",negative,"connection_type","RETURN"));
                if(Boolean.TRUE.equals(station.get("enabled"))) {edge(graph,positive,negative);sourceNode.put(id,positive);}
            } else if(kind.equals("SUBSTATION")) {
                Map<String,Object> terminals=new LinkedHashMap<>();for(int i=0;i<STATION_TERMINALS.size();i++) terminals.put(LEGACY_TERMINALS.get(i),nodeForPort.get(name+"."+STATION_TERMINALS.get(i)));
                Map<String,Object> station=new LinkedHashMap<>();station.put("installation_id",id);station.put("installation_category","SUBSTATION");station.put("installation_model","SIX_TERMINAL_RECTIFIER");station.put("terminals",terminals);
                for(String field:List.of("emf_V","internal_resistance_ohm","rectifier_type")) station.put(field,spec.get(field));station.put("enabled",spec.getOrDefault("enabled",true));stations.add(station);
                for(String t:List.of("B","C","D")) edge(graph,nodeForPort.get(name+".A"),nodeForPort.get(name+"."+t));edge(graph,nodeForPort.get(name+".N"),nodeForPort.get(name+".S"));
                // Enabled voltage-source devices potentially couple the two buses.
                // Actual diode conduction/receptivity remains the solver's task.
                if(Boolean.TRUE.equals(station.get("enabled"))) {edge(graph,nodeForPort.get(name+".A"),nodeForPort.get(name+".N"));sourceNode.put(id,nodeForPort.get(name+".A"));}
            }
        }
        String reference;
        if(input.containsKey("reference_node_id")) {
            String requested=string(input,"reference_node_id");var address=addresses.get(requested);
            reference=address!=null&&address.size()==1?nodeForPort.get(address.get(0)):positionForNode.containsKey(requested)?requested:null;
            if(reference==null) throw new IllegalArgumentException("Unknown reference terminal/node: "+requested);
        } else reference=ports.values().stream().filter(p->p.conductor().equals("R")&&nodeForPort.containsKey(p.key())).map(p->nodeForPort.get(p.key())).sorted().findFirst().orElse(null);
        output.put("nodes",nodes);output.put("lines",lines);output.put("power_installations",stations);output.put("installation_connections",connections);
        if(!switchgears.isEmpty()) output.put("switchgears",switchgears);
        if(reference!=null) output.put("reference_node_id",reference);
        for(String key:List.of("electricalObjects","electricalAssociations","substationTypes","lineTypes","conductorTypes","switchgearTypes")) output.remove(key);
        List<Island> islands=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(String start:graph.keySet()) if(seen.add(start)) {
            Set<String> component=new TreeSet<>(),objectNames=new TreeSet<>();Deque<String> queue=new ArrayDeque<>();queue.add(start);
            while(!queue.isEmpty()) {String n=queue.removeFirst();component.add(n);objectNames.addAll(owners.get(n));for(String next:graph.get(n)) if(seen.add(next)) queue.add(next);}
            List<String> sources=sourceNode.entrySet().stream().filter(e->component.contains(e.getValue())).map(Map.Entry::getKey).toList();
            islands.add(new Island(List.copyOf(component),List.copyOf(objectNames),sources,component.contains(reference)));
        }
        return new Inspection(Collections.unmodifiableMap(output),List.copyOf(new TreeSet<>(incomplete)),List.copyOf(islands));
    }
    private static void addPort(Map<String,Port> ports,Map<String,String> parent,Map<String,List<String>> addresses,Port p) {ports.put(p.key(),p);parent.put(p.key(),p.key());addresses.put(p.key(),List.of(p.key()));}
    private static String root(Map<String,String> parent,String key) {String p=parent.get(key);while(!p.equals(parent.get(p))) p=parent.get(p);return p;}
    private static void union(Map<String,String> parent,String a,String b) {String x=root(parent,a),y=root(parent,b);if(!x.equals(y)) parent.put(x.compareTo(y)<0?y:x,x.compareTo(y)<0?x:y);}
    private static void edge(Map<String,Set<String>> graph,String a,String b) {graph.get(a).add(b);graph.get(b).add(a);}
    private static String coordinate(String text) {var c=RwyCoordinateParser.parse(text);return c.getSectionId()+":"+c.getPositionM()+":"+Objects.toString(c.getTrackId(),"");}
    private static void identifier(String id,String field) {if(!id.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException(field+" must use letters, digits, '_' or '-': "+id);}
    private static double positive(Map<String,Object> map,String field) {Object v=map.get(field);if(!(v instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<=0) throw new IllegalArgumentException("Positive finite value required: "+field);return ((Number)v).doubleValue();}
    private static String string(Map<String,Object> map,String field) {Object v=map.get(field);if(!(v instanceof String s)||s.isBlank()) throw new IllegalArgumentException("Nonblank string required: "+field);return (String)v;}
    private static Map<String,Object> optionalMap(Map<String,Object> map,String key) {return map.containsKey(key)?map(map.get(key),key):Map.of();}
    private static Map<String,Object> map(Object value,String field) {if(!(value instanceof Map<?,?> m)) throw new IllegalArgumentException("Object required: "+field);Map<String,Object> out=new LinkedHashMap<>();m.forEach((k,v)->out.put((String)k,v));return out;}
    private static List<Map<String,Object>> objectList(Map<String,Object> input,String key) {if(!input.containsKey(key)) return List.of();if(!(input.get(key) instanceof List<?> list)) throw new IllegalArgumentException("List required: "+key);return list.stream().map(v->map(v,key)).toList();}
    private static List<String> strings(Object value,String field) {if(!(value instanceof List<?> list)) throw new IllegalArgumentException("List required: "+field);List<String> out=new ArrayList<>();for(var v:list) {if(!(v instanceof String s)||s.isBlank()) throw new IllegalArgumentException("String required: "+field);out.add((String)v);}return out;}
}
