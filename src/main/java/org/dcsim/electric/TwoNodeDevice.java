package org.dcsim.electric;

import org.dcsim.math.FieldElement;

public interface TwoNodeDevice<T extends FieldElement<T>> extends Device<T> {
    String getFromNode();

    String getToNode();
}
