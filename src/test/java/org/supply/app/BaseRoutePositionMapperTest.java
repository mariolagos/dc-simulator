package org.supply.app;
import java.nio.file.*;
import java.util.*;
public class BaseRoutePositionMapperTest {
    public static void main(String[] args) throws Exception {
        Path input = Files.createTempFile("segments", ".csv");
        Files.writeString(input, "section,from_rwy,to_rwy,start_model_m,length_m\n101,101 2+000 U,101 0+000 U,0,2000\n102,102 0+000 U,102 2+000 U,0,2000\n");
        var mapper = BaseRoutePositionMapper.load(input, List.of("101", "102"), Map.of());
        check(mapper.mapRailwayCoordinate("101", 1500), 500);
        check(mapper.mapRailwayCoordinate("102", 500), 2500);
        check(mapper.mapRailwayPosition("101 1+500 N"), 500);
        check(mapper.mapRailwayPosition("102 0+500 S"), 2500);
        var reverse = BaseRoutePositionMapper.load(input, List.of("102", "101"), Map.of("101", true, "102", true));
        check(reverse.mapRailwayCoordinate("101", 1500), 3500);
        check(reverse.mapRailwayCoordinate("102", 500), 1500);
        if (mapper.mapRailwayCoordinate("missing", 100) != null) throw new AssertionError("Unknown section");
        Files.delete(input);
        System.out.println("PASS: opposite railway coordinates, section offsets, reversed base route");
    }
    private static void check(Double value, double expected) {
        if (value == null || Math.abs(value - expected) > 1e-9) throw new AssertionError(value + " != " + expected);
    }
}
