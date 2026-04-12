package org.supply.model;

public class TrainFactory {

    public void build(Model model, Grid grid, List<TrainInput> trains) {

        for (TrainInput t : trains) {

            Train train = new Train(t.getId());

            train.setRoute(t.getRoute());
            train.setPowerProfile(t.getPowerProfile()); // viktigt enligt din fil :contentReference[oaicite:0]{index=0}

            model.addTrain(train);
        }
    }
}