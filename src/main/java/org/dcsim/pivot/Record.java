package org.dcsim.pivot;

/** Normalized longtable record (wide schema). */
final class Record {
    enum Kind { RUN, NODE, LINE, TRAIN, UNKNOWN; static Kind from(String s){
        return switch (s==null?"":s.toUpperCase()) {
            case "RUN" -> RUN; case "NODE" -> NODE; case "LINE" -> LINE; case "TRAIN" -> TRAIN; default -> UNKNOWN; };
    }}

    double time_s;
    Kind kind = Kind.UNKNOWN;
    String id;
    String project;
    String scenario;
    String hash_tag;

    // Common electrical
    Double V_V;    // node voltage or single-point
    Double I_A;
    Double P_W;

    // Lines
    Double P_loss_W;
    Double V_A_V;
    Double V_B_V;

    // Trains
    Double req_W;
    Double pos_m;
    Double speed_mps;
    Double P_aux_W;
    Double P_burn_W;
}
