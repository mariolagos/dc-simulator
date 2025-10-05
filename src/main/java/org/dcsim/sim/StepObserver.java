package org.dcsim.sim;

public interface StepObserver {
    void onStep(
            double t,
            int edgeIndex,
            double xM,
            double Va,
            double[] V // hela nodspänningsvektorn om du vill analysera mer
    );
}