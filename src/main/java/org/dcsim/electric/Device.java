package org.dcsim.electric;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.math.FieldElement;
import org.dcsim.math.Real;

import java.util.Map;

public interface Device<T extends FieldElement<T>> {
    String getId();
    int getConnectedNode();
    T getPower();
    T computeCurrent(T nodeVoltage, double time);
    public Real getCurrent();  // ev. abstract
    public void stamp(RealMatrix yMatrix, RealVector jVector, RealVector xVector, int timestep, Map<Integer, Integer> nodeIndexMap);



}
