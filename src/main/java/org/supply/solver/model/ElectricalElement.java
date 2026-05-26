package org.supply.solver.model;


import org.supply.solver.electrical.AdmittanceStamp;

public interface ElectricalElement {

   public void stamp(AdmittanceStamp stamp);
}