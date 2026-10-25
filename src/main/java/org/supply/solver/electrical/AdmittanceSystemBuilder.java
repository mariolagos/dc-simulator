package org.supply.solver.electrical;

import org.supply.math.Real;
import org.supply.solver.model.CalculationBranch;
import org.supply.solver.model.CalculationNetwork;
import org.supply.solver.model.CalculationNode;
import org.supply.solver.model.ElectricalElement;
import org.supply.solver.model.SubstationElement;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class AdmittanceSystemBuilder {

    public AdmittanceSystem build(
            CalculationNetwork network,
            String referenceNodeId,
            List<CurrentInjection> currentInjections,
            Map<String, Real> previousVoltages
    ) {
        if (referenceNodeId == null || referenceNodeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Reference node id must be provided"
            );
        }

        List<String> nodeIds = activeNodeIds(network, referenceNodeId, currentInjections);

        Map<String, Integer> nodeIndexById = new LinkedHashMap<>();

        var groups = new org.supply.domain.IdealNodeGroups(network.nodes().stream().map(CalculationNode::id).toList());
        for(var e:network.elements()) if(e instanceof org.supply.solver.model.IdealConnectionElement link)
            groups.join(link.fromNodeId(),link.toNodeId());
        Map<String,Integer> indexByGroup=new LinkedHashMap<>();
        List<String> representatives=new java.util.ArrayList<>();
        for(String id:nodeIds) {
            String group=groups.group(id);
            Integer index=indexByGroup.get(group);
            if(index==null) { index=indexByGroup.size();indexByGroup.put(group,index);representatives.add(id); }
            nodeIndexById.put(id,index);
        }
        nodeIds=representatives;
        int size = nodeIds.size();

        if (!nodeIndexById.containsKey(referenceNodeId)) {
            throw new IllegalArgumentException(
                    "Reference node not found: " + referenceNodeId
            );
        }

        Real[][] matrix = zeroMatrix(size);
        Real[] vector = zeroVector(size);

        AdmittanceStamp stamp =
                new AdmittanceStamp(matrix, vector, nodeIndexById, previousVoltages);

        for (ElectricalElement element : network.elements()) {
            if (element instanceof org.supply.solver.model.IdealConnectionElement) continue;
            if (element instanceof SubstationElement substation
                    && !substation.enabled()) {
                continue;
            }

            element.stamp(stamp);
        }

        stampCurrentInjections(currentInjections, nodeIndexById, vector);

        return new AdmittanceSystem(
                nodeIndexById,
                nodeIds,
                matrix,
                vector,
                referenceNodeId
        );
    }

    public AdmittanceSystem build(
            CalculationNetwork network,
            String referenceNodeId
    ) {
        return build(network, referenceNodeId, List.of(), Map.of());
    }

    public AdmittanceSystem build(
            CalculationNetwork network,
            String referenceNodeId,
            List<CurrentInjection> currentInjections
    ) {
        return build(
                network,
                referenceNodeId,
                currentInjections,
                Map.of()
        );
    }

    private static Real[][] zeroMatrix(int size) {
        Real[][] matrix = new Real[size][size];

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                matrix[row][col] = Real.fromDouble(0.0);
            }
        }

        return matrix;
    }

    private static Real[] zeroVector(int size) {
        Real[] vector = new Real[size];

        for (int i = 0; i < size; i++) {
            vector[i] = Real.fromDouble(0.0);
        }

        return vector;
    }

    private static void add(
            Real[][] matrix,
            int row,
            int col,
            double value
    ) {
        double current = matrix[row][col].asDouble();

        matrix[row][col] = Real.fromDouble(current + value);
    }

    private static void stampCurrentInjections(
            List<CurrentInjection> currentInjections,
            Map<String, Integer> nodeIndexById,
            Real[] vector
    ) {
        for (CurrentInjection injection : currentInjections) {

            Integer index = nodeIndexById.get(injection.nodeId());

            if (index == null) {
                throw new IllegalArgumentException(
                        "Current injection node not found: "
                                + injection.nodeId()
                );
            }

            double current =
                    vector[index].asDouble()
                            + injection.currentAmpere().asDouble();

            vector[index] = Real.fromDouble(current);
        }
    }

    private static List<String> activeNodeIds(
            CalculationNetwork network,
            String referenceNodeId,
            List<CurrentInjection> currentInjections
    ) {
        Set<String> ids = new LinkedHashSet<>();

        ids.add(referenceNodeId);

        for (CalculationBranch b : network.branches()) {
            ids.add(b.fromNodeId());
            ids.add(b.toNodeId());
        }

        for (CurrentInjection i : currentInjections) {
            ids.add(i.nodeId());
        }
        for(var e:network.elements()) if(e instanceof org.supply.solver.model.IdealConnectionElement link) {
            ids.add(link.fromNodeId());ids.add(link.toNodeId());
        }

        return network.nodes().stream()
                .map(CalculationNode::id)
                .filter(ids::contains)
                .collect(Collectors.toList());
    }
}