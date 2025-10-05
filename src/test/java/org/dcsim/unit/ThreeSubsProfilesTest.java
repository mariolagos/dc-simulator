package org.dcsim.unit;

@org.junit.Rule
public org.junit.rules.TemporaryFolder tmp = new org.junit.rules.TemporaryFolder();

@Test
public void threeSubs_oneRegenTrain_profiles() throws Exception {
    Path csv = tmp.newFile("profiles.csv").toPath();
    Files.writeString(csv, String.join("\n",
            "time_s,train_id,req_W,iMax_A,cut_V,vmax_V",
            "0,T1,-100000,300,850,1000",
            "5,T1,-120000,300,850,1000",
            "10,T1,-80000,300,850,1000"
    ));
    // passera in csv till din loader/solver:
    runScenarioWithProfiles(csv);
}
