package org.supply.loader;

import com.typesafe.config.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.supply.domain.ConnectionType;
import org.supply.track.RwyCoordinateParser;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Reads named Excel input tables and generates the established explicit grid vocabulary. */
public final class ExcelGridReader {
    public record Result(Config grid, Map<String,Object> routes, List<Map<String,Object>> bindings) { }
    private record InputRow(String location,Map<String,Object> cells) {
        Object value(String key) { return cells.get(key); }
        String text(String key) { Object v=value(key);if(!(v instanceof String s)||s.isBlank())throw bad(location+": text required in "+key);return s.trim(); }
        String label(String key) {
            Object v=value(key);
            if(v instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue()==Math.rint(n.doubleValue()))
                return java.math.BigDecimal.valueOf(n.doubleValue()).toBigIntegerExact().toString();
            return text(key);
        }
        String optional(String key) { return value(key)==null?"":text(key); }
        boolean flag(String key) { if(!(value(key) instanceof Boolean b))throw bad(location+": TRUE/FALSE required in "+key);return b; }
        double number(String key) { if(!(value(key) instanceof Number n)||!Double.isFinite(n.doubleValue()))throw bad(location+": finite number required in "+key);return n.doubleValue(); }
        int order(String key) { double n=number(key);if(n<1||n>Integer.MAX_VALUE||n!=Math.rint(n))throw bad(location+": positive integer required in "+key);return (int)n; }
    }
    private final Map<String,List<InputRow>> tables=new LinkedHashMap<>();
    private final Map<String,List<String>> headers=new HashMap<>();
    private ExcelGridReader(Path file) throws IOException {
        try(InputStream in=Files.newInputStream(file);XSSFWorkbook book=new XSSFWorkbook(in)) {
            FormulaEvaluator evaluator=book.getCreationHelper().createFormulaEvaluator();
            for(var sheet:book) for(XSSFTable table:((XSSFSheet)sheet).getTables()) {
                String name=table.getName();if(tables.containsKey(name))throw bad("Duplicate table: "+name);
                List<String> keys=new ArrayList<>();Row header=sheet.getRow(table.getStartRowIndex());
                for(int col=table.getStartColIndex();col<=table.getEndColIndex();col++) {
                    Object v=cell(header.getCell(col),evaluator);if(!(v instanceof String s)||s.isBlank()||keys.contains(s.trim()))throw bad("Invalid/duplicate column in "+name);
                    keys.add(s.trim());
                }
                List<InputRow> rows=new ArrayList<>();
                for(int ri=table.getStartRowIndex()+1;ri<=table.getEndRowIndex();ri++) {
                    Row row=sheet.getRow(ri);if(row==null)continue;Map<String,Object> values=new LinkedHashMap<>();
                    for(int ci=0;ci<keys.size();ci++) {Object v=cell(row.getCell(table.getStartColIndex()+ci),evaluator);if(v!=null)values.put(keys.get(ci),v);}
                    if(!values.isEmpty())rows.add(new InputRow(sheet.getSheetName()+" row "+(ri+1),values));
                }
                tables.put(name,List.copyOf(rows));headers.put(name,List.copyOf(keys));
            }
        }
    }
    public static Result read(Path file) throws IOException { return new ExcelGridReader(file).compile(); }
    private static Object cell(Cell c,FormulaEvaluator evaluator) {
        if(c==null)return null;
        CellValue v=evaluator.evaluate(c);if(v==null)return null;
        return switch(v.getCellType()) {
            case BLANK -> null;
            case STRING -> v.getStringValue().isBlank()?null:v.getStringValue().trim();
            case BOOLEAN -> v.getBooleanValue();
            case NUMERIC -> v.getNumberValue();
            default -> throw bad("Excel error at "+c.getSheet().getSheetName()+"!"+c.getAddress());
        };
    }
    private List<InputRow> table(String name,String... keys) {
        if(!tables.containsKey(name))throw bad("Missing Excel table: "+name);
        if(keys.length>0&&!headers.get(name).equals(List.of(keys)))throw bad("Unexpected columns in "+name+": "+headers.get(name));
        return tables.get(name);
    }
    private Result compile() {
        Map<String,Object> parameters=new LinkedHashMap<>();
        for(var r:table("NetworkSettings","Parameter","Value","Unit")) {
            String key=r.text("Parameter");if(!Set.of("u_nominal_V","u_min_V","u_cutoff_V","u_max_V","i_train_max_A","p_train_max_W","p_allow_W","reference_node_id").contains(key))throw bad(r.location+": unknown grid parameter "+key);
            if(parameters.putIfAbsent(key,r.value("Value"))!=null)throw bad(r.location+": duplicate parameter "+key);
        }
        Map<String,Object> objects=new LinkedHashMap<>();Map<String,Map<String,Object>> metadata=new LinkedHashMap<>();
        for(var r:table("NetworkObjects","Object","Kind","Junction","Port","N positionRwy","S positionRwy","Junction links")) {
            String id=r.text("Object"),kind=r.text("Kind");identifier(id);
            if(!Set.of("POINT","JUNCTION","SUBSTATION").contains(kind))throw bad(r.location+": invalid Kind "+kind);
            Map<String,Object> meta=metadata.computeIfAbsent(id,k->new LinkedHashMap<>(Map.of("kind",kind)));
            if(!meta.get("kind").equals(kind))throw bad(r.location+": conflicting object kind "+id);
            if(kind.equals("SUBSTATION")&&!r.optional("Junction").isEmpty()) {
                if(meta.putIfAbsent("junction",r.text("Junction"))!=null)throw bad(r.location+": duplicate station "+id);
                if(r.value("N positionRwy")!=null||r.value("S positionRwy")!=null)throw bad(r.location+": junction owns station positions");
                continue;
            }
            Map<String,Object> pair=Map.of("N",r.text("N positionRwy"),"S",r.text("S positionRwy"));
            for(String track:List.of("N","S"))if(!RwyCoordinateParser.parse((String)pair.get(track)).getTrackId().equals(track))throw bad(r.location+": position track mismatch");
            if(kind.equals("JUNCTION")) {
                Map<String,Object> spec=map(objects.computeIfAbsent(id,k->new LinkedHashMap<>(Map.of("kind","JUNCTION","positions",new LinkedHashMap<String,Object>()))));
                var ports=map(spec.get("positions"));if(ports.containsKey(r.label("Port")))throw bad(r.location+": duplicate junction port");
                ports.put(r.label("Port"),pair);spec.put("positions",ports);objects.put(id,spec);
                if(!r.optional("Junction links").isEmpty())spec.put("conductors",Arrays.stream(r.text("Junction links").split(",")).map(String::trim).toList());
            } else {
                if(objects.putIfAbsent(id,new LinkedHashMap<>(Map.of("kind","POINT","positions",pair)))!=null)throw bad(r.location+": duplicate object "+id);
            }
        }
        Map<String,List<String>> branches=new LinkedHashMap<>();Map<String,List<InputRow>> branchRows=group(table("Branches","Branch","Section","Order","Object"),"Branch");
        Map<String,Object> branchSpecs=new LinkedHashMap<>();
        for(var e:branchRows.entrySet()) {
            var sorted=ordered(e.getValue());String section=sorted.get(0).label("Section");
            if(sorted.stream().anyMatch(r->!r.label("Section").equals(section)))throw bad("Conflicting branch sections: "+e.getKey());
            List<String> chain=sorted.stream().map(r->r.text("Object")).toList();branches.put(e.getKey(),chain);
            branchSpecs.put(e.getKey(),Map.of("section",section,"objects",chain));
        }
        Map<String,Object> lineTypes=new LinkedHashMap<>();
        for(var r:table("LineTypes","Line type","Feeding [ohm/m]","Return [ohm/m]")) {
            put(lineTypes,r.text("Line type"),Map.of("feeding_resistance_ohm_per_m",positive(r,"Feeding [ohm/m]"),"return_resistance_ohm_per_m",positive(r,"Return [ohm/m]")),r);
        }
        // Expand reusable type rules into the same connection records used by the compiler.
        if(tables.containsKey("SwitchgearConnections")) expandSwitchgearTypes(branches, metadata);
        Map<String,InputRow> stations=new LinkedHashMap<>();
        for(var r:table("Substations","Substation","Rectifier type","Switchgear type","Feeding gap")) {
            String id=r.text("Substation");if(stations.putIfAbsent(id,r)!=null)throw bad(r.location+": duplicate substation "+id);
            if(!metadata.containsKey(id)||!metadata.get(id).get("kind").equals("SUBSTATION"))throw bad(r.location+": station absent from Network: "+id);
            var meta=metadata.get(id);if(meta.containsKey("junction")) {
                String junction=(String)meta.get("junction");if(!objects.containsKey(junction)||!"JUNCTION".equals(map(objects.get(junction)).get("kind")))throw bad("Unknown station junction: "+junction);
                // Station terminal bindings supply the connections; do not create bypass links.
                var spec=map(objects.get(junction));spec.put("conductors",List.of());objects.put(junction,spec);
            }
        }
        for(var e:metadata.entrySet())if(e.getValue().get("kind").equals("SUBSTATION")&&!stations.containsKey(e.getKey()))throw bad("Missing Substations row: "+e.getKey());
        Map<String,Object> base=new LinkedHashMap<>(parameters);base.remove("reference_node_id");
        base.put("electricalObjects",objects);base.put("lineTypes",lineTypes);base.put("branches",branchSpecs);
        Config generated=BranchGridGenerator.generate(config(base));
        Map<String,Object> expanded=ElectricalObjectExpander.inspect(generated.root().unwrapped()).expandedGrid();
        Map<String,Map<String,Object>> nodes=new LinkedHashMap<>(),lines=new LinkedHashMap<>();
        for(var n:list(expanded.get("nodes")))nodes.put((String)n.get("node_id"),n);
        for(var l:list(expanded.get("lines")))lines.put((String)l.get("line_id"),l);
        Map<String,InputRow> rectifiers=new LinkedHashMap<>();
        for(var r:table("RectifierTypes","Rectifier type","No-load voltage [V]","Internal resistance [ohm]","Model","Enabled")) {
            if(rectifiers.putIfAbsent(r.text("Rectifier type"),r)!=null)throw bad(r.location+": duplicate rectifier type");
            positive(r,"No-load voltage [V]");positive(r,"Internal resistance [ohm]");r.flag("Enabled");
            if(!Set.of("DIODE","THYRISTOR").contains(r.text("Model")))throw bad(r.location+": invalid rectifier model");
        }
        Map<String,Map<String,String>> types=new LinkedHashMap<>();Map<String,String> busPolarity=new HashMap<>();
        for(String name:List.of("FeedingTerminals","ReturnTerminals")) {
            for(var r:table(name)) {
                String id=r.text("Switchgear type");Map<String,String> type=types.computeIfAbsent(id,k->new LinkedHashMap<>());
                String polarity=name.equals("FeedingTerminals")?"FEEDING":"RETURN";
                for(String terminal:headers.get(name))if(!terminal.equals("Switchgear type")&&r.value(terminal)!=null) {
                    identifier(terminal);String bus=r.text(terminal);identifier(bus);
                    if(type.putIfAbsent(terminal,bus)!=null)throw bad(r.location+": duplicate terminal "+terminal);
                    String previous=busPolarity.putIfAbsent(id+"|"+bus,polarity);
                    if(previous!=null&&!previous.equals(polarity))throw bad(r.location+": bus mixes feeding and return");
                }
            }
        }
        var connectionRows=group(table("TerminalConnections","Substation","Terminal","Connects to","Enabled","Length [m]","Resistance [ohm/m]","Multiplicity"),"Substation");
        if(!stations.keySet().containsAll(connectionRows.keySet()))throw bad("Unknown substation in terminal connections");
        List<Map<String,Object>> ideals=new ArrayList<>(),sources=new ArrayList<>(),sourceConnections=new ArrayList<>(),gears=new ArrayList<>(),bindings=new ArrayList<>();
        Set<String> boundLineEnds=new HashSet<>();
        for(var entry:stations.entrySet()) {
            String id=entry.getKey();InputRow row=entry.getValue();String typeId=row.text("Switchgear type");
            if(!types.containsKey(typeId))throw bad(row.location+": unknown switchgear type "+typeId);
            if(!rectifiers.containsKey(row.text("Rectifier type")))throw bad(row.location+": unknown rectifier type");
            var type=types.get(typeId);List<InputRow> local=connectionRows.getOrDefault(id,List.of());Map<String,InputRow> byTerminal=new LinkedHashMap<>();
            for(var r:local)if(byTerminal.putIfAbsent(r.text("Terminal"),r)!=null)throw bad(r.location+": repeated terminal");
            if(!byTerminal.keySet().equals(type.keySet()))throw bad(row.location+": terminal rows must match type exactly; expected "+type.keySet()+", got "+byTerminal.keySet());
            List<Map<String,Object>> terminals=new ArrayList<>(),cables=new ArrayList<>();List<String> disconnected=new ArrayList<>();
            Map<String,String> terminalNodes=new LinkedHashMap<>();Map<String,String> positions=new LinkedHashMap<>();
            for(var r:local) {
                String terminal=r.text("Terminal");boolean returning=headers.get("ReturnTerminals").contains(terminal);
                String role=returning?"RETURN":"FEEDING";
                String[] dest=r.text("Connects to").split("\\s*/\\s*");
                if(dest.length!=3||!dest[2].matches("[NS]\\.[FR]"))throw bad(r.location+": expected branch / from → to / track.F or track.R");
                if(returning!=dest[2].endsWith("R"))throw bad(r.location+": terminal polarity conflicts with destination");
                String[] pair=dest[1].split("\\s*(?:→|->)\\s*");List<String> chain=branches.get(dest[0]);
                if(pair.length!=2||chain==null)throw bad(r.location+": unknown line segment");
                int index=chain.indexOf(pair[0]);if(index<0||index+1>=chain.size()||!chain.get(index+1).equals(pair[1]))throw bad(r.location+": segment is not adjacent in branch");
                String owner=(String)metadata.get(id).getOrDefault("junction",id);boolean left=owner.equals(pair[0]);
                if(left==owner.equals(pair[1]))throw bad(r.location+": destination must have one endpoint at this station");
                String lineId="B__"+dest[0]+"__"+(index+1)+"."+dest[2];var line=lines.get(lineId);
                if(line==null)throw bad(r.location+": generated line missing: "+lineId);
                String field=left?"node_from_id":"node_to_id";String end=id+"."+terminal;
                if(!boundLineEnds.add(lineId+"|"+field))throw bad(r.location+": line end already bound: "+lineId);
                if(nodes.containsKey(end))throw bad(r.location+": generated terminal node collision: "+end);
                String pos=(String)nodes.get((String)line.get(field)).get("position_rwy");nodes.put(end,new LinkedHashMap<>(Map.of("node_id",end,"position_rwy",pos)));line.put(field,end);
                terminalNodes.put(terminal,end);positions.put(terminal,pos);terminals.add(Map.of("id",terminal,"node_id",end,"connection_type",role));
                bindings.add(Map.of("substation",id,"terminal",terminal,"bus",type.get(terminal),"line_id",lineId,"line_end",left?"L":"R","node_id",end,"position_rwy",pos,"enabled",r.flag("Enabled")));
            }
            String anchor=positions.values().iterator().next();
            for(String pole:List.of("POS","NEG")) {String node=id+"."+pole,role=pole.equals("POS")?"FEEDING":"RETURN";addNode(nodes,node,anchor);terminals.add(Map.of("id",pole,"node_id",node,"connection_type",role));sourceConnections.add(Map.of("installation_id",id,"node_id",node,"connection_type",role));}
            for(String bus:new LinkedHashSet<>(type.values())) {
                String role=busPolarity.get(typeId+"|"+bus),node=id+".SG."+bus;addNode(nodes,node,anchor);terminals.add(Map.of("id",bus,"node_id",node,"connection_type",role));
                ideals.add(ideal(id+"__source_"+bus,id+(role.equals("FEEDING")?".POS":".NEG"),node,role));
            }
            for(var r:local) {
                String terminal=r.text("Terminal"),role=headers.get("ReturnTerminals").contains(terminal)?"RETURN":"FEEDING";
                int fields=0;for(String key:List.of("Length [m]","Resistance [ohm/m]","Multiplicity"))if(r.value(key)!=null)fields++;
                if(fields!=0&&fields!=3)throw bad(r.location+": incomplete internal cable (fill all three fields or leave all blank)");
                double length=0,resistance=0;int count=1;
                if(fields==3){length=positive(r,"Length [m]");resistance=positive(r,"Resistance [ohm/m]");count=r.order("Multiplicity");}
                if(!r.flag("Enabled")){disconnected.add(terminal);continue;}
                String bus=type.get(terminal);
                if(fields==0)ideals.add(ideal(id+"__terminal_"+terminal,id+".SG."+bus,terminalNodes.get(terminal),role));
                else cables.add(Map.of("id","to_"+terminal,"from",bus,"to",terminal,"length_m",length,"conductor_type","excel_"+id+"_"+terminal,"resistance_ohm_per_km",resistance*1000,"multiplicity",count));
            }
            if(!row.flag("Feeding gap")) {
                if(!type.keySet().containsAll(List.of("A","B","C","D")))throw bad(row.location+": no-gap type requires A/B and C/D terminal pairs");
                for(var pair:List.of(List.of("A","B"),List.of("C","D"))) {
                    String a=positions.get(pair.get(0)),b=positions.get(pair.get(1));if(!a.equals(b))throw bad(row.location+": no-gap terminals must share positionRwy");
                    ideals.add(ideal(id+"__no_gap_"+pair.get(0),terminalNodes.get(pair.get(0)),terminalNodes.get(pair.get(1)),"FEEDING"));
                }
            }
            var rectifier=rectifiers.get(row.text("Rectifier type"));
            sources.add(Map.of("installation_id",id,"installation_category","SUBSTATION","emf_V",rectifier.number("No-load voltage [V]"),"internal_resistance_ohm",rectifier.number("Internal resistance [ohm]"),"rectifier_type",rectifier.text("Model"),"enabled",rectifier.flag("Enabled")));
            gears.add(Map.of("installation_id",id,"terminals",terminals,"cables",cables,"disconnected_terminals",disconnected));
        }
        Set<String> used=new HashSet<>();lines.values().forEach(l->{used.add((String)l.get("node_from_id"));used.add((String)l.get("node_to_id"));});
        ideals.forEach(l->{used.add((String)l.get("from"));used.add((String)l.get("to"));});
        gears.forEach(g->list(g.get("terminals")).forEach(t->used.add((String)t.get("node_id"))));nodes.keySet().retainAll(used);
        String reference=(String)parameters.get("reference_node_id");if(reference==null||!nodes.containsKey(reference))throw bad("reference_node_id must name a generated node, e.g. SS0.NEG (got "+reference+")");
        Map<String,Object> output=new LinkedHashMap<>(parameters);output.put("nodes",new ArrayList<>(nodes.values()));output.put("lines",new ArrayList<>(lines.values()));output.put("power_installations",sources);output.put("installation_connections",sourceConnections);output.put("switchgears",gears);output.put("ideal_connections",ideals);
        Map<String,Object> routes=new LinkedHashMap<>();
        for(var e:group(table("Routes","Route","Track","Order","Branch","Direction"),"Route").entrySet()) {
            Map<String,Object> route=new LinkedHashMap<>();
            for(String conductor:List.of("F","R")) {
                List<String> ids=new ArrayList<>();String track=null;String previousEnd=null;
                for(var r:ordered(e.getValue())) {
                    if(!Set.of("N","S").contains(r.text("Track")))throw bad(r.location+": invalid route track");
                    if(track!=null&&!track.equals(r.text("Track")))throw bad(r.location+": route track must be constant");track=r.text("Track");
                    List<String> chain=branches.get(r.text("Branch"));if(chain==null)throw bad(r.location+": unknown route branch");
                    String direction=r.text("Direction");if(!Set.of("FORWARD","REVERSE").contains(direction))throw bad(r.location+": invalid direction");
                    List<Integer> segments=new ArrayList<>();for(int i=1;i<chain.size();i++)segments.add(i);if(direction.equals("REVERSE"))Collections.reverse(segments);
                    String firstId="B__"+r.text("Branch")+"__"+segments.get(0)+"."+track+"."+conductor;
                    String start=(String)lines.get(firstId).get(direction.equals("FORWARD")?"node_from_id":"node_to_id");
                    if(previousEnd!=null)ids.addAll(junctionPath(previousEnd,start,lines,ideals,cablesForRoutes(gears)));
                    for(int i:segments)ids.add("B__"+r.text("Branch")+"__"+i+"."+track+"."+conductor);
                    String lastId="B__"+r.text("Branch")+"__"+segments.get(segments.size()-1)+"."+track+"."+conductor;
                    previousEnd=(String)lines.get(lastId).get(direction.equals("FORWARD")?"node_to_id":"node_from_id");
                }
                route.put(conductor.equals("F")?"feeding_lines":"return_lines",ids);
            }
            routes.put(e.getKey(),route);
        }
        return new Result(config(output),Collections.unmodifiableMap(routes),List.copyOf(bindings));
    }
    /** BEFORE/AFTER follow the ordered branch, independently of railway kilometre direction. */
    private void expandSwitchgearTypes(Map<String,List<String>> branches, Map<String,Map<String,Object>> metadata) {
        if(tables.containsKey("TerminalConnections"))throw bad("Use SwitchgearConnections or TerminalConnections, not both");
        Map<String,InputRow> definitions=new LinkedHashMap<>();
        for(var r:table("SwitchgearTypes","Switchgear type","Feeding gap")) {
            if(definitions.putIfAbsent(r.text("Switchgear type"),r)!=null)throw bad(r.location+": duplicate switchgear type");
            r.flag("Feeding gap");
        }
        var rules=group(table("SwitchgearConnections","Switchgear type","Terminal","Side","Track","Conductor","Bus","Enabled","Length [m]","Resistance [ohm/m]","Multiplicity"),"Switchgear type");
        if(!definitions.keySet().equals(rules.keySet()))throw bad("SwitchgearTypes and SwitchgearConnections must define the same types");
        List<InputRow> stationRows=table("Substations","Substation","Rectifier type","Switchgear type");
        List<InputRow> stations=new ArrayList<>(),connections=new ArrayList<>(),feeding=new ArrayList<>(),returns=new ArrayList<>();
        for(var definition:definitions.entrySet()) {
            Map<String,Object> f=new LinkedHashMap<>(),ret=new LinkedHashMap<>();f.put("Switchgear type",definition.getKey());ret.put("Switchgear type",definition.getKey());
            Set<String> slots=new HashSet<>(),terminals=new HashSet<>(),sides=new HashSet<>();
            for(var rule:rules.get(definition.getKey())) {
                String terminal=rule.text("Terminal"),side=rule.text("Side"),track=rule.text("Track"),conductor=rule.text("Conductor");
                identifier(side);sides.add(side);
                if(!Set.of("N","S").contains(track)||!Set.of("F","R").contains(conductor))throw bad(rule.location+": expected N/S track and F/R conductor");
                if(!terminals.add(terminal)||!slots.add(side+"|"+track+"|"+conductor))throw bad(rule.location+": duplicate terminal or connection slot");
                (conductor.equals("F")?f:ret).put(terminal,rule.text("Bus"));rule.flag("Enabled");
            }
            for(String side:sides)for(String track:List.of("N","S"))for(String conductor:List.of("F","R"))
                if(!slots.contains(side+"|"+track+"|"+conductor))throw bad(definition.getValue().location+": each switchgear side requires N/S × F/R connections: "+side);
            feeding.add(new InputRow(definition.getValue().location,f));returns.add(new InputRow(definition.getValue().location,ret));
        }
        Map<String,Map<String,InputRow>> portMaps=new LinkedHashMap<>();
        if(tables.containsKey("StationPorts"))for(var port:table("StationPorts","Substation","Side","Branch","Swap tracks")) {
            port.flag("Swap tracks");
            if(portMaps.computeIfAbsent(port.text("Substation"),k->new LinkedHashMap<>()).putIfAbsent(port.text("Side"),port)!=null)
                throw bad(port.location+": duplicate station side mapping");
        }
        Set<String> stationIds=new HashSet<>();stationRows.forEach(r->stationIds.add(r.text("Substation")));
        if(!stationIds.containsAll(portMaps.keySet()))throw bad("Unknown substation in StationPorts");
        for(var station:stationRows) {
            String id=station.text("Substation"),type=station.text("Switchgear type");
            if(!definitions.containsKey(type))throw bad(station.location+": unknown switchgear type "+type);
            if(!metadata.containsKey(id))throw bad(station.location+": station absent from Network: "+id);
            boolean junction=metadata.get(id).containsKey("junction");
            String owner=(String)metadata.get(id).getOrDefault("junction",id);
            Map<String,InputRow> ports=portMaps.getOrDefault(id,Map.of());
            Set<String> sides=new LinkedHashSet<>();rules.get(type).forEach(r->sides.add(r.text("Side")));
            if(!sides.containsAll(ports.keySet()))throw bad(station.location+": StationPorts contains a side absent from switchgear type");
            if(junction&&!ports.keySet().equals(sides))throw bad(station.location+": junction station requires one StationPorts row for each type side: "+sides);
            var incident=new ArrayList<String>();
            for(var e:branches.entrySet())if(e.getValue().contains(owner))incident.add(e.getKey());
            if(!junction&&(incident.size()!=1||!sides.equals(Set.of("BEFORE","AFTER"))))
                throw bad(station.location+": interior station requires one branch and BEFORE/AFTER sides");
            Map<String,String> branchForSide=new LinkedHashMap<>();
            for(String side:sides) {
                String branch=ports.containsKey(side)?ports.get(side).label("Branch"):incident.get(0);
                List<String> chain=branches.get(branch);
                if(chain==null||Collections.frequency(chain,owner)!=1)throw bad(station.location+": side "+side+" must map to an incident branch containing "+owner+" exactly once");
                int i=chain.indexOf(owner);
                if(junction&&(i!=0&&i!=chain.size()-1))throw bad(station.location+": junction must be a branch endpoint");
                if(!junction&&(i==0||i==chain.size()-1))throw bad(station.location+": BEFORE/AFTER requires a neighbour on both sides");
                branchForSide.put(side,branch);
            }
            if(junction&&(new HashSet<>(branchForSide.values()).size()!=sides.size()||!new HashSet<>(branchForSide.values()).equals(new HashSet<>(incident))))
                throw bad(station.location+": map every incident junction branch exactly once");
            var values=new LinkedHashMap<>(station.cells);values.put("Feeding gap",definitions.get(type).flag("Feeding gap"));stations.add(new InputRow(station.location,values));
            for(var rule:rules.get(type)) {
                String side=rule.text("Side"),branch=branchForSide.get(side);var chain=branches.get(branch);int i=chain.indexOf(owner);
                int segment=junction?(i==0?0:i-1):(side.equals("BEFORE")?i-1:i);
                String track=rule.text("Track");
                if(ports.containsKey(side)&&ports.get(side).flag("Swap tracks"))track=track.equals("N")?"S":"N";
                var cells=new LinkedHashMap<>(rule.cells);cells.put("Substation",id);
                cells.put("Connects to",branch+" / "+chain.get(segment)+" → "+chain.get(segment+1)+" / "+track+"."+rule.text("Conductor"));
                connections.add(new InputRow(rule.location+" (station "+id+")",cells));
            }
        }
        tables.put("Substations",stations);headers.put("Substations",List.of("Substation","Rectifier type","Switchgear type","Feeding gap"));
        tables.put("TerminalConnections",connections);
        headers.put("TerminalConnections",List.of("Substation","Terminal","Connects to","Enabled","Length [m]","Resistance [ohm/m]","Multiplicity"));
        tables.put("FeedingTerminals",feeding);tables.put("ReturnTerminals",returns);
        for(var pair:List.of(Map.entry("FeedingTerminals",feeding),Map.entry("ReturnTerminals",returns))) {
            Set<String> columns=new LinkedHashSet<>();columns.add("Switchgear type");pair.getValue().forEach(r->columns.addAll(r.cells.keySet()));headers.put(pair.getKey(),List.copyOf(columns));
        }
    }
    private record Link(String to,String line) { }
    private static List<Map<String,Object>> cablesForRoutes(List<Map<String,Object>> gears) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(var g:gears) {
            Map<String,String> nodes=new HashMap<>();for(var t:list(g.get("terminals")))nodes.put((String)t.get("id"),(String)t.get("node_id"));
            for(var c:list(g.get("cables")))result.add(Map.of("from",nodes.get(c.get("from")),"to",nodes.get(c.get("to"))));
        }
        return result;
    }
    private static List<String> junctionPath(String from,String to,Map<String,Map<String,Object>> lines,List<Map<String,Object>> ideals,List<Map<String,Object>> cables) {
        Map<String,List<Link>> graph=new HashMap<>();
        for(var line:lines.values())if(((String)line.get("line_id")).startsWith("J__"))link(graph,(String)line.get("node_from_id"),(String)line.get("node_to_id"),(String)line.get("line_id"));
        for(var edge:ideals)link(graph,(String)edge.get("from"),(String)edge.get("to"),null);
        for(var edge:cables)link(graph,(String)edge.get("from"),(String)edge.get("to"),null);
        Map<String,List<String>> paths=new HashMap<>();paths.put(from,List.of());var queue=new ArrayDeque<String>();queue.add(from);
        while(!queue.isEmpty()) {
            String node=queue.remove();if(node.equals(to))return paths.get(node);
            for(var edge:graph.getOrDefault(node,List.of()))if(!paths.containsKey(edge.to)) {
                var path=new ArrayList<>(paths.get(node));if(edge.line!=null)path.add(edge.line);paths.put(edge.to,path);queue.add(edge.to);
            }
        }
        throw bad("Route branches are not electrically connected between "+from+" and "+to);
    }
    private static void link(Map<String,List<Link>> graph,String a,String b,String line) {graph.computeIfAbsent(a,k->new ArrayList<>()).add(new Link(b,line));graph.computeIfAbsent(b,k->new ArrayList<>()).add(new Link(a,line));}
    private static List<InputRow> ordered(List<InputRow> rows) {var sorted=new ArrayList<>(rows);sorted.sort(Comparator.comparingInt(r->r.order("Order")));for(int i=0;i<sorted.size();i++)if(sorted.get(i).order("Order")!=i+1)throw bad(sorted.get(i).location+": Order must be unique and consecutive from 1");return sorted;}
    private static Map<String,List<InputRow>> group(List<InputRow> rows,String key) {Map<String,List<InputRow>> result=new LinkedHashMap<>();for(var r:rows)result.computeIfAbsent(r.text(key),k->new ArrayList<>()).add(r);return result;}
    private static double positive(InputRow r,String field){double v=r.number(field);if(v<=0)throw bad(r.location+": "+field+" must be positive");return v;}
    private static void put(Map<String,Object> out,String id,Object value,InputRow r){if(out.putIfAbsent(id,value)!=null)throw bad(r.location+": duplicate ID "+id);}
    private static void identifier(String id){if(!id.matches("[\\p{L}\\p{Nd}_-]+"))throw bad("Invalid identifier: "+id);}
    private static void addNode(Map<String,Map<String,Object>> nodes,String id,String pos){if(nodes.putIfAbsent(id,new LinkedHashMap<>(Map.of("node_id",id,"position_rwy",pos)))!=null)throw bad("Generated node collision: "+id);}
    private static Map<String,Object> ideal(String id,String from,String to,String type){return Map.of("id",id,"from",from,"to",to,"connection_type",type);}
    private static Config config(Map<String,Object> values){return ConfigValueFactory.fromMap(values).toConfig();}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return new LinkedHashMap<>((Map<String,Object>)value);}
    private static List<Map<String,Object>> list(Object value){List<Map<String,Object>> result=new ArrayList<>();for(Object item:(List<?>)value)result.add(map(item));return result;}
    private static IllegalArgumentException bad(String message){return new IllegalArgumentException(message);}
}
