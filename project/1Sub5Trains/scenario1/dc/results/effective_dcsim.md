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
}
grid {
    anchorNodeId=99
    groundNodeId=0
    lines=[
        {
            from=1
            id=L1
            lengthM=100
            rPerKm=0.1
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
            id=99
            name=ANCHOR
            position="1 0+100"
        }
    ]
    substations=[
        {
            emf=900
            id=SS0
            internalResistance=0.1
            nodeId=1
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
            folder="project/1sub5trains/scenario1//T1"
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
    project="3subs5trains"
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
            },
            {
                count=1
                departure="00:00:10"
                id=Train2
                templateId=T1
            },
            {
                count=1
                departure="00:00:20"
                id=Train3
                templateId=T1
            },
            {
                count=1
                departure="00:00:30"
                id=Train4
                templateId=T1
            },
            {
                count=1
                departure="00:00:40"
                id=Train5
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
        "V_derate1"=500
        "V_derate2"=600
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
