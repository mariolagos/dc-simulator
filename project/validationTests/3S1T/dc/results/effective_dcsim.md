# Effective dcsim config

```hocon
dcsim dcsim {
    paths {
        longtable="${dcsim.projects.root}/${dcsim.run.project}/${dcsim.run.scenario}/longtable.csv"
        "pivots_dir"="${dcsim.projects.root}/${dcsim.run.project}/${dcsim.run.scenario}/pivots"
    }
}
electrics {
    substations {
        defaults {
            allowBackfeed=false
            maxCurrentA=0
        }
    }
}
export {
    csvEveryNthStep=1
    enabled=false
}
exportInputs="validationTests/3S1T"
exportRunExcel="project/validationTests/templates/T1/A-B.xlsx"
exportTrainId=T1
grid {
    anchorNodeId=99
    groundNodeId=0
    lines=[
        {
            from=1
            id=L0
            lengthM=1500
            rPerKm=0.03
            to=2
        },
        {
            from=2
            id=L1
            lengthM=1500
            rPerKm=0.03
            to=3
        },
        {
            from=3
            id=L2
            lengthM=100
            rPerKm=0.03
            to=99
        }
    ]
    nodes=[
        {
            id=0
            name=GND
            position="1 0+100"
        },
        {
            id=1
            name=N0
            position="1 0+000"
        },
        {
            id=2
            name=N1
            position="1 1+500"
        },
        {
            id=3
            name=N2
            position="1 3+000"
        },
        {
            id=99
            name=ANCHOR
            position="1 3+100"
        }
    ]
    substations=[
        {
            emf=900
            id=SS0
            internalResistance=0.1
            nodeId=1
            rectifierType=DIODE
        },
        {
            emf=900
            id=SS1
            internalResistance=0.1
            nodeId=2
            rectifierType=DIODE
        },
        {
            emf=900
            id=SS2
            internalResistance=0.1
            nodeId=3
            rectifierType=DIODE
        }
    ]
    trains=[
        {
            fromNode=2
            id=Train1
            toNode=0
        }
    ]
}
hash=hash
pivot {
    columns {
        I="I_A"
        P="P_W"
        "P_loss"="P_loss_W"
        V="V_V"
        VA="V_A_V"
        VB="V_B_V"
        hash="hash_tag"
        id=id
        kind=kind
        pos="pos_m"
        project=project
        req="req_W"
        scenario=scenario
        speed="speed_mps"
        time="time_s"
    }
    format {
        decimals=6
        locale=US
    }
    units {
        current=A
        energy=J
        power=W
        voltage=V
    }
}
powerProfiles {
    auxiliaryPower=0
    motoringAndAuxiliariesInSameModel=false
    templates=[
        {
            folder="project/validationTests/3S1T/T1"
            id=T1
            legs=[
                {
                    file="A-B.xlsx"
                    fromStation=A
                    toStation=B
                }
            ]
        }
    ]
}
projects {
    root=project
}
run {
    project="3S1T"
    scenario=scenario1
}
simulationControl {
    simulationEnd="00:02:00"
    simulationSpeed=FAST
    simulationStart="00:00:00"
    stopAfterSteps=10
    tickDurationSec=1
}
track {
    stations=[
        {
            abbreviation=A
            name=A
            position="1 0+000"
        },
        {
            abbreviation=B
            name=B
            position="1 3+000"
        }
    ]
}
traffic {
    templates {
        T1 {
            stops=[
                {
                    departure="00:00:00"
                    signature=A
                },
                {
                    arrival="00:02:00"
                    signature=B
                }
            ]
        }
    }
    timetable {
        trains=[
            {
                count=1
                departure="00:00:00"
                id=Train1
                templateId=T1
            }
        ]
    }
}
train {
    Rmin=5.0E-6
    epsFrac=1.0E-4
    pW=0
    vMS=25
}
trains {
    defaults {
        cutoffVoltage=600
        maxCurrentA=1.0E30
        maxVoltage=1000
        minVoltage=500
    }
}
verbose {
    all=true
}

```
