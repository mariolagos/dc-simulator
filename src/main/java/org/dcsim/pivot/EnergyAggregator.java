package org.dcsim.pivot;

import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Path;

/** Scenario totals using trapz rules. */
final class EnergyAggregator {
    private final TrapzAccumulator trac = new TrapzAccumulator();
    private final TrapzAccumulator aux = new TrapzAccumulator();
    private final TrapzAccumulator regen = new TrapzAccumulator();
    private final TrapzAccumulator burn = new TrapzAccumulator();
    private final TrapzAccumulator subs = new TrapzAccumulator();

    void acceptTrain(Record r) {
        if (r.P_W != null) {
            double p = r.P_W;
            trac.accept(r.time_s, Math.max(p, 0));
            regen.accept(r.time_s, Math.max(-p, 0));
        }
        if (r.P_aux_W != null) aux.accept(r.time_s, r.P_aux_W);
        if (r.P_burn_W != null) burn.accept(r.time_s, r.P_burn_W);
    }

    void acceptNode(Record r) {
        if (r.kind == Record.Kind.NODE && r.P_W != null) {
            subs.accept(r.time_s, Math.max(r.P_W, 0));
        }
    }

    void write(Path out, LinesAggregator lines) throws IOException {
        double E_line_J = lines.sumLineLossJ();
        double E_trac_J = trac.getEnergyJ();
        double E_aux_J  = aux.getEnergyJ();
        double E_regen_J= regen.getEnergyJ();
        double E_burn_J = burn.getEnergyJ();
        double E_subs_J = subs.getEnergyJ();
        double E_total  = E_trac_J + E_aux_J - E_regen_J + E_burn_J + E_line_J;
        double imbalance = E_subs_J - E_total;

        try (CSVWriter w = Csvs.openCsv(out)) {
            w.writeNext(new String[] {"E_trac_J","E_aux_J","E_regen_J","E_burn_J","E_line_J","E_subs_J","E_total_J","imbalance_J"});
            w.writeNext(new String[] {
                Csvs.f(E_trac_J), Csvs.f(E_aux_J), Csvs.f(E_regen_J), Csvs.f(E_burn_J),
                Csvs.f(E_line_J), Csvs.f(E_subs_J), Csvs.f(E_total), Csvs.f(imbalance)
            });
        }
    }
}
