package org.supply.solver.testsupport;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;

public class ObserverSnapshotTest {

    @Test
    public void snapshotPreservesAcceptedStateWhileObserversContinueIterating() {

        Registry registry = new Registry();

        FpiObserver observerA =
                new FpiObserver("A", registry, 1.0);

        FpiObserver observerB =
                new FpiObserver("B", registry, 2.0);

        Caller caller =
                new Caller(registry, observerA, observerB);

        // Temporary numerical iterations.
        caller.iterateObservers();
        caller.iterateObservers();

        double acceptedA = observerA.value();
        double acceptedB = observerB.value();

        // Caller decides that THIS state is accepted.
        caller.capture();

        // Observers continue to change afterwards.
        caller.iterateObservers();
        caller.iterateObservers();

        assertThat(
                observerA.value() == acceptedA,
                is(false)
        );

        assertThat(
                observerB.value() == acceptedB,
                is(false)
        );

        RecordingCollector collector =
                new RecordingCollector();

        caller.publish(collector);

        assertThat(collector.values.size(), is(2));

        assertThat(
                collector.valueOf("A.value"),
                closeTo(acceptedA, 1e-12)
        );

        assertThat(
                collector.valueOf("B.value"),
                closeTo(acceptedB, 1e-12)
        );
    }


    @Test
    public void registryHandlesDifferentObserverAndSnapshotTypes() {

        Registry registry = new Registry();

        ScalarObserver scalar =
                new ScalarObserver(
                        "scalar",
                        registry,
                        10.0
                );

        ComplexObserver complex =
                new ComplexObserver(
                        "complex",
                        registry,
                        20.0,
                        3.0
                );

        scalar.calculate();
        complex.calculate();

        double acceptedScalar = scalar.value();
        double acceptedVoltage = complex.voltage();
        double acceptedCurrent = complex.current();

        // Caller accepts the current state of ALL registered observers.
        registry.capture();

        // Producers continue to change afterwards.
        scalar.calculate();
        complex.calculate();

        RecordingCollector collector =
                new RecordingCollector();

        registry.publish(collector);

        assertThat(
                collector.valueOf("scalar.value"),
                closeTo(acceptedScalar, 1e-12)
        );

        assertThat(
                collector.valueOf("complex.voltage"),
                closeTo(acceptedVoltage, 1e-12)
        );

        assertThat(
                collector.valueOf("complex.current"),
                closeTo(acceptedCurrent, 1e-12)
        );
    }

    private interface Observer {

        Snapshot snapshot();
    }


    private interface Snapshot {

        void publish(Collector collector);
    }


    private interface Collector {

        void add(String id, double value);
    }


    private static final class Registry {

        private final List<Observer> observers =
                new ArrayList<>();

        private final List<Snapshot> snapshots =
                new ArrayList<>();

        void register(Observer observer) {
            observers.add(observer);
        }

        void capture() {
            snapshots.clear();

            for (Observer observer : observers) {
                snapshots.add(observer.snapshot());
            }
        }

        void publish(Collector collector) {
            for (Snapshot snapshot : snapshots) {
                snapshot.publish(collector);
            }
        }
    }


    private static final class Caller {

        private final Registry registry;
        private final FpiObserver observerA;
        private final FpiObserver observerB;

        Caller(
                Registry registry,
                FpiObserver observerA,
                FpiObserver observerB
        ) {
            this.registry = registry;
            this.observerA = observerA;
            this.observerB = observerB;
        }

        void iterateObservers() {
            observerA.iterate();
            observerB.iterate();
        }

        void capture() {
            registry.capture();
        }

        void publish(Collector collector) {
            registry.publish(collector);
        }
    }


    private static final class FpiObserver
            implements Observer {

        private final String id;
        private double x;

        FpiObserver(
                String id,
                Registry registry,
                double initialValue
        ) {
            this.id = id;
            this.x = initialValue;

            // Observer registers itself.
            registry.register(this);
        }

        void iterate() {
            x = Math.sqrt(15.0 - x);
        }

        double value() {
            return x;
        }

        @Override
        public Snapshot snapshot() {
            return new ScalarSnapshot(id, x);
        }
    }

    private static final class ScalarObserver
            implements Observer {

        private final String id;
        private double value;

        ScalarObserver(
                String id,
                Registry registry,
                double initialValue
        ) {
            this.id = id;
            this.value = initialValue;

            registry.register(this);
        }

        void calculate() {
            value += 1.0;
        }

        double value() {
            return value;
        }

        @Override
        public Snapshot snapshot() {
            return new ScalarSnapshot(
                    id,
                    value
            );
        }
    }

    private static final class ComplexObserver
            implements Observer {

        private final String id;

        private double voltage;
        private double current;

        ComplexObserver(
                String id,
                Registry registry,
                double voltage,
                double current
        ) {
            this.id = id;
            this.voltage = voltage;
            this.current = current;

            registry.register(this);
        }

        void calculate() {
            voltage += 10.0;
            current += 2.0;
        }

        double voltage() {
            return voltage;
        }

        double current() {
            return current;
        }

        @Override
        public Snapshot snapshot() {
            return new ComplexSnapshot(
                    id,
                    voltage,
                    current
            );
        }
    }

    private record ScalarSnapshot(
            String id,
            double value
    ) implements Snapshot {

        @Override
        public void publish(Collector collector) {
            collector.add(
                    id + ".value",
                    value
            );
        }
    }

    private record ComplexSnapshot(
            String id,
            double voltage,
            double current
    ) implements Snapshot {

        @Override
        public void publish(Collector collector) {

            collector.add(
                    id + ".voltage",
                    voltage
            );

            collector.add(
                    id + ".current",
                    current
            );
        }
    }

    private static final class RecordingCollector
            implements Collector {

        private final List<Value> values =
                new ArrayList<>();

        @Override
        public void add(
                String id,
                double value
        ) {
            values.add(
                    new Value(id, value)
            );
        }

        double valueOf(String id) {
            return values.stream()
                    .filter(v -> v.id().equals(id))
                    .findFirst()
                    .orElseThrow()
                    .value();
        }
    }

    private record Value(
            String id,
            double value
    ) {
    }
}