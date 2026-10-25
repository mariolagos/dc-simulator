package org.supply.app;

import com.typesafe.config.*;
import org.supply.domain.*;
import org.supply.loader.ExcelGridReader;
import org.supply.model.GridModel;
import org.supply.track.RwyCoordinateParser;
import java.util.*;

/** Reports the actual loaded model, including the electrical quotient of ideal links. */
public final class ExcelNetworkReport {
    private ExcelNetworkReport() { }
    public static Map<String,String> render(GridModel model,ExcelGridReader.Result input) {
        var groups=new IdealNodeGroups(model.getNodes().stream().map(Node::getNodeId).toList());
        model.getIdealConnections().forEach(c->groups.join(c.fromNodeId(),c.toNodeId()));
        List<Map<String,Object>> nodes=new ArrayList<>(),edges=new ArrayList<>();
        StringBuilder nodeCsv=new StringBuilder("physical_node_id,electrical_group,position_rwy\n");
        model.getNodes().stream().sorted(Comparator.comparing(Node::getNodeId)).forEach(n->{
            nodes.add(Map.of("id",n.getNodeId(),"group",groups.group(n.getNodeId()),"position_rwy",n.getPositionRwy()));
            row(nodeCsv,n.getNodeId(),groups.group(n.getNodeId()),n.getPositionRwy());
        });
        StringBuilder connectionCsv=new StringBuilder("kind,id,from_node,to_node,from_group,to_group,resistance_ohm\n");
        for(var line:model.getLines()) {
            var a=RwyCoordinateParser.parse(line.getFromNode().getPositionRwy());var b=RwyCoordinateParser.parse(line.getToNode().getPositionRwy());
            double length=a.getSectionId().equals(b.getSectionId())?Math.abs(b.getPositionM()-a.getPositionM()):0;
            edge(edges,connectionCsv,groups,"LINE",line.getLineId(),line.getFromNode().getNodeId(),line.getToNode().getNodeId(),line.getResistanceOhmPerM().asDouble()*length);
        }
        for(var gear:model.getSwitchGears()) {
            Map<String,String> ids=new HashMap<>();gear.terminals().forEach(t->ids.put(t.id(),t.nodeId()));
            for(var c:gear.cables())edge(edges,connectionCsv,groups,"CABLE",gear.installationId()+"."+c.id(),ids.get(c.fromTerminalId()),ids.get(c.toTerminalId()),c.resistanceOhm());
        }
        for(var c:model.getIdealConnections())edge(edges,connectionCsv,groups,"IDEAL",c.id(),c.fromNodeId(),c.toNodeId(),0);
        StringBuilder sourceCsv=new StringBuilder("substation,feeding_node,return_node,feeding_group,return_group,emf_V,internal_resistance_ohm,enabled\n");
        List<Map<String,Object>> sources=new ArrayList<>();
        for(var s:model.getInstallations()) {
            String f=model.getInstallationConnections().stream().filter(c->c.getInstallationId().equals(s.getInstallationId())&&c.getConnectionType()==ConnectionType.FEEDING).findFirst().orElseThrow().getNodeId();
            String r=model.getInstallationConnections().stream().filter(c->c.getInstallationId().equals(s.getInstallationId())&&c.getConnectionType()==ConnectionType.RETURN).findFirst().orElseThrow().getNodeId();
            row(sourceCsv,s.getInstallationId(),f,r,groups.group(f),groups.group(r),s.getEmfV().asDouble(),s.getInternalResistanceOhm().asDouble(),s.isEnabled());
            sources.add(Map.of("id",s.getInstallationId(),"feeding_group",groups.group(f),"return_group",groups.group(r),"emf_V",s.getEmfV().asDouble(),"internal_resistance_ohm",s.getInternalResistanceOhm().asDouble(),"enabled",s.isEnabled()));
        }
        StringBuilder bindingCsv=new StringBuilder("substation,terminal,bus,line_id,line_end,node_id,electrical_group,position_rwy,enabled\n");
        for(var b:input.bindings())row(bindingCsv,b.get("substation"),b.get("terminal"),b.get("bus"),b.get("line_id"),b.get("line_end"),b.get("node_id"),groups.group((String)b.get("node_id")),b.get("position_rwy"),b.get("enabled"));
        long electricalNodes=nodes.stream().map(n->n.get("group")).distinct().count();
        String summary="EXCEL NETWORK CHECK\nPhysical nodes: "+nodes.size()+"\nElectrical nodes after ideal reduction: "+electricalNodes
                +"\nExternal conductor lines: "+model.getLines().size()+"\nInternal cables: "+model.getSwitchGears().stream().mapToInt(g->g.cables().size()).sum()
                +"\nIdeal connections: "+model.getIdealConnections().size()+"\nSubstations: "+sources.size()+"\nRoutes: "+input.routes().size()+"\nTerminal bindings: "+input.bindings().size()
                +"\nNo timestep was solved by the preprocessor. Ideal links are exact node reductions, not small resistors.\n";
        var grid=input.grid();
        if(grid.hasPath("u_nominal_V")&&sources.stream().anyMatch(s->Math.abs((double)s.get("emf_V")-grid.getDouble("u_nominal_V"))>grid.getDouble("u_nominal_V")*.25))
            summary+="WARNING: Rectifier EMF and nominal grid voltage differ substantially. Review voltage parameters before a study.\n";
        Map<String,String> result=new LinkedHashMap<>();result.put("network_nodes.csv",nodeCsv.toString());result.put("network_connections.csv",connectionCsv.toString());result.put("network_sources.csv",sourceCsv.toString());result.put("terminal_bindings.csv",bindingCsv.toString());result.put("network_summary.txt",summary);
        result.put("network.json",ConfigValueFactory.fromMap(Map.of("nodes",nodes,"edges",edges,"sources",sources)).render(ConfigRenderOptions.concise().setJson(true)));
        return result;
    }
    private static void edge(List<Map<String,Object>> edges,StringBuilder csv,IdealNodeGroups groups,String kind,String id,String from,String to,double resistance) {
        row(csv,kind,id,from,to,groups.group(from),groups.group(to),resistance);
        edges.add(Map.of("kind",kind,"id",id,"from",from,"to",to,"from_group",groups.group(from),"to_group",groups.group(to),"resistance_ohm",resistance));
    }
    private static void row(StringBuilder out,Object... values) {for(int i=0;i<values.length;i++){if(i>0)out.append(',');out.append('"').append(String.valueOf(values[i]).replace("\"","\"\"")).append('"');}out.append('\n');}
}
