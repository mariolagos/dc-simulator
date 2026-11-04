package org.supply.app;

import org.supply.track.TrackdataChecker;
import org.supply.track.TrackdataReview;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Standalone Excel trackdata diagnostics, independent of study configuration and grid. */
public final class TrackdataCheck {
    private TrackdataCheck() { }
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException(
                "Usage: TrackdataCheck <trackdata.xlsx> [--output report.txt] [--sheet name]... [--review approvals.conf]");
        Path input = Path.of(args[0]).toAbsolutePath().normalize();
        String stem = input.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        Path output = Path.of("build", "reports", "trackdata", stem + "_trackdata_check.txt");
        List<String> sheets = new ArrayList<>();
        Path reviewFile = null;
        for (int i = 1; i < args.length; i++) {
            String option = args[i];
            if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for " + option);
            if (option.equals("--output")) output = Path.of(args[++i]);
            else if (option.equals("--review")) reviewFile = Path.of(args[++i]);
            else if (option.equals("--sheet")) sheets.add(args[++i]);
            else throw new IllegalArgumentException("Unknown option: " + option);
        }
        output = output.toAbsolutePath().normalize();
        if (input.equals(output) || (Files.exists(output) && Files.isSameFile(input, output)))
            throw new IllegalArgumentException("Report output must not overwrite the input workbook");
        if (reviewFile != null) {
            reviewFile = reviewFile.toAbsolutePath().normalize();
            if (reviewFile.equals(input) || reviewFile.equals(output)
                    || (Files.exists(reviewFile) && (Files.isSameFile(reviewFile,input)
                    || (Files.exists(output) && Files.isSameFile(reviewFile,output)))))
                throw new IllegalArgumentException("Review file, input workbook and report must be separate files");
        }
        boolean createReview = reviewFile != null && !Files.exists(reviewFile);
        TrackdataReview review = reviewFile == null || createReview ? TrackdataReview.empty() : TrackdataReview.read(reviewFile);
        TrackdataChecker.Report report = TrackdataChecker.check(input, sheets, review);
        if (createReview) {
            TrackdataReview.createTemplate(reviewFile, report.coordinateIntervals());
            System.out.println("Created review template (all intervals unaccepted): " + reviewFile);
        }
        String text = "Input: " + input + "\n" + report.text();
        Files.createDirectories(output.getParent());
        Files.writeString(output, text, StandardCharsets.UTF_8);
        System.out.print(text);
        System.out.println("Report: " + output);
        if (reviewFile != null) System.out.println("Review file: " + reviewFile);
        if (report.errors() > 0) throw new IllegalArgumentException("Trackdata check failed: " + report.errors() + " error(s)");
    }
}
