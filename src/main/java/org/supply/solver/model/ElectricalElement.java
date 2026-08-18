package org.supply.solver.model;


import org.supply.solver.electrical.AdmittanceStamp;
import org.supply.solver.io.LongTableWriter;

public interface ElectricalElement {

   default boolean enabled() {
      return true;
   }

   public void stamp(AdmittanceStamp stamp);

   default void saveStaticResult(LongTableWriter writer) {
      // Nothing to save by default.
   }
}