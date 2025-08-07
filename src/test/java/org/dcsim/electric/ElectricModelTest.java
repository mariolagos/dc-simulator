//package org.dcsim.electric;
//
//import org.dcsim.math.Real;
//
//public class ElectricModelTest {
//    public static void main(String[] args) {
//        // Spänning vid noden
//        Real voltage = Real.fromDouble(1000.0); // 1000 V
//
//        // Substation med id "S1", kopplad till nod 1
//        Substation substation = new Substation("S1", 1, Real.fromDouble(1000.0), Real.fromDouble(0.1));
//
//        // TrainLoad med id "T1", kopplad till nod 1
//        TrainLoad train = new TrainLoad("T1", 1);
//        train.setPower(Real.fromDouble(200_000.0)); // explicit effekt
//
//        // Beräkna strömmar
//        Real iSub = substation.computeCurrent(voltage, time);
//        Real iTrain = train.computeCurrent(voltage, time);
//
//        // Skriv ut resultat
//        System.out.println("Voltage: " + voltage.getValue() + " V");
//        System.out.println("Substation current: " + iSub.getValue() + " A");
//        System.out.println("Train current: " + iTrain.getValue() + " A");
//        System.out.println("Train power: " + train.getPower().getValue() + " W");
//    }
//}
