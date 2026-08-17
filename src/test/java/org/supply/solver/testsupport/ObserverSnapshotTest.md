# ObserverSnapshotTest -- Mermaid diagrams

## Class diagram

``` mermaid
classDiagram
    class ObserverSnapshotTest
    class Observer {
        <<interface>>
        +snapshot() Snapshot
    }
    class Snapshot {
        <<interface>>
        +publish(Collector collector)
    }
    class Collector {
        <<interface>>
        +add(String id, double value)
    }
    class Registry {
        -List~Observer~ observers
        -List~Snapshot~ snapshots
        +register(Observer observer)
        +capture()
        +publish(Collector collector)
    }
    class Caller {
        -Registry registry
        -FpiObserver observerA
        -FpiObserver observerB
        +iterateObservers()
        +capture()
        +publish(Collector collector)
    }
    class FpiObserver {
        -String id
        -double x
        +FpiObserver(String id, Registry registry, double initialValue)
        +iterate()
        +value() double
        +snapshot() Snapshot
    }
    class ScalarSnapshot {
        <<record>>
        +String id
        +double value
        +publish(Collector collector)
    }
    class RecordingCollector {
        -List~Value~ values
        +add(String id, double value)
        +valueOf(String id) double
    }
    class Value {
        <<record>>
        +String id
        +double value
    }

    Observer <|.. FpiObserver
    Snapshot <|.. ScalarSnapshot
    Collector <|.. RecordingCollector
    Registry o-- Observer : registered
    Registry o-- Snapshot : captured
    Caller --> Registry
    Caller --> FpiObserver
    FpiObserver --> Registry : self-registers
    FpiObserver ..> ScalarSnapshot : creates
    ScalarSnapshot --> Collector : publishes to
    RecordingCollector o-- Value
    ObserverSnapshotTest ..> Registry
    ObserverSnapshotTest ..> Caller
    ObserverSnapshotTest ..> FpiObserver
    ObserverSnapshotTest ..> RecordingCollector
```
| Mermaid     | Betydelse                                  |
| ----------- | ------------------------------------------ |
| `A --> B`   | Association – A känner/har referens till B |
| `A ..> B`   | Dependency – A använder B tillfälligt      |
| `A o-- B`   | Aggregation – A innehåller/samlar B        |
| `A *-- B`   | Composition – A äger B starkt              |
| `A <\|.. B` | B implementerar interface A                |
| `A <\|-- B` | B ärver från A                             |

## Sequence diagram

``` mermaid
sequenceDiagram
    participant T as ObserverSnapshotTest
    participant R as Registry
    participant A as FpiObserver A
    participant B as FpiObserver B
    participant C as Caller
    participant SA as ScalarSnapshot A
    participant SB as ScalarSnapshot B
    participant RC as RecordingCollector

    T->>R: new Registry()
    T->>A: new FpiObserver("A", R, 1.0)
    A->>R: register(this)
    T->>B: new FpiObserver("B", R, 2.0)
    B->>R: register(this)
    T->>C: new Caller(R, A, B)

    Note over C,B: Numerical iterations before accepted state

    T->>C: iterateObservers()
    C->>A: iterate()
    A->>A: x = sqrt(15 - x)
    C->>B: iterate()
    B->>B: x = sqrt(15 - x)

    T->>C: iterateObservers()
    C->>A: iterate()
    A->>A: x = sqrt(15 - x)
    C->>B: iterate()
    B->>B: x = sqrt(15 - x)

    Note over T,B: Caller decides current state is accepted

    T->>C: capture()
    C->>R: capture()
    R->>A: snapshot()
    A-->>R: new ScalarSnapshot(id, x)
    R->>B: snapshot()
    B-->>R: new ScalarSnapshot(id, x)

    Note over R,SB: Accepted state is now frozen

    T->>C: iterateObservers()
    C->>A: iterate()
    C->>B: iterate()
    T->>C: iterateObservers()
    C->>A: iterate()
    C->>B: iterate()

    Note over A,B: Live observer state has changed

    T->>RC: new RecordingCollector()
    T->>C: publish(RC)
    C->>R: publish(RC)
    R->>SA: publish(RC)
    SA->>RC: add("A", acceptedA)
    R->>SB: publish(RC)
    SB->>RC: add("B", acceptedB)

    Note over RC: Published values are from capture time,<br/>not observers' current state
```
# From test to DcSimulator
| Experiment | DC-simulator                                                                                             |
| --- |----------------------------------------------------------------------------------------------------------|
| Caller | den beräkningsnivå som vet när state är accepterat, initialt solveTimestep() / senare ev. FPI-controller |
| Registry | ny LongTableRegistry, ägd av Caller                                                                      |
| Observer | generellt interface för objekt som kan skapa ett snapshot                                                |
| konkreta observers | t.ex. Train, Substation, senare Line                                                                     |
| Snapshot	 | mutable fryst state, t.ex. TrainSnapshot                                                                 |
| Collector | LongTableWriter via ett tunt interface |
| RecordingCollector | endast testklass, för unit tests |