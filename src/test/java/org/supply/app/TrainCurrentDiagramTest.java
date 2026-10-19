package org.supply.app;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.supply.solver.io.LongTableWriter;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.Assert.*;

public class TrainCurrentDiagramTest {
    @Rule public TemporaryFolder tmp=new TemporaryFolder();
    private static DcReporter.LongTableRow row(Double t,String type,String id,String signal,String value) {
        return new DcReporter.LongTableRow(t,"project","study","hash",type,id,signal,value,"",t==null?"STATIC":"RESULT","");
    }
    static List<DcReporter.LongTableRow> fixture() {
        List<DcReporter.LongTableRow> rows=new ArrayList<>();
        rows.add(row(null,"SYSTEM","DC","u_min_V","500"));
        rows.add(row(null,"SYSTEM","DC","u_cutoff_V","600"));
        rows.add(row(null,"SYSTEM","DC","u_max_V","900"));
        for(String id:List.of("A&1","A2","B","scalar","older")) {
            if(!id.equals("older"))rows.add(row(null,"TRAIN",id,"i_train_max_A",id.equals("B")?"7000":"6000"));
            if(id.startsWith("A")||id.equals("B"))rows.add(row(null,"TRAIN",id,"i_train_limit_curve",
                    id.equals("B")?"400:0;500:0;560:2400;600:7000;640:7000;900:7000":
                            "400:0;500:0;560:1200;600:3500;640:6000;900:6000"));
            for(int n=0;n<40;n++) {
                rows.add(row((double)n,"TRAIN",id,"u_V",Double.toString(490+n*12)));
                rows.add(row((double)n,"TRAIN",id,"i_A",Double.toString(n%3==0?-1000:500+n*80)));
            }
        }
        rows.add(row(99.,"TRAIN","A2","u_V","800")); // incomplete pair excluded
        rows.add(row(100.,"TRAIN","A2","i_A","NaN"));
        rows.add(row(100.,"TRAIN","A2","u_V","800"));
        return rows;
    }
    @Test public void groupsRecordedProfilesAndPairsTrainAndTime() throws Exception {
        Path dir=tmp.newFolder().toPath();Path csv=dir.resolve("longtable.csv");
        try(LongTableWriter w=new LongTableWriter(csv.toString(),true,"project","study","hash")) {
            for(var r:fixture())w.signalRow(r.timeS(),r.objectType(),r.objectId(),r.signal(),r.value(),"",r.stage(),null,"");
        }
        TrainCurrentDiagram.write(DcReporter.readLongTable(csv),dir,"study");
        List<String> names=Files.readAllLines(dir.resolve("study_train_current_diagrams.txt"));
        assertEquals(4,names.size());
        String a=Files.readString(dir.resolve(names.get(0)));
        assertTrue(a.contains("2 trains; 80 recorded operating points"));
        assertTrue(a.contains("A&amp;1"));assertTrue(a.contains("t=0.0 s; U=490.0 V; I=-1000.0 A"));
        assertTrue(a.contains("stroke-width=\"1.6\""));
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        for(String name:names)factory.newDocumentBuilder().parse(dir.resolve(name).toFile());
        assertTrue(Files.readString(dir.resolve(names.get(3))).contains("operating points only"));
        assertFalse(Files.readString(dir.resolve(names.get(3))).contains("<path"));
        // Stale owned plots are removed, unrelated files remain.
        Files.writeString(dir.resolve("other.svg"),"keep");
        TrainCurrentDiagram.write(fixture().stream().filter(r->!r.objectType().equals("TRAIN")||r.objectId().startsWith("A")).toList(),dir,"study");
        assertFalse(Files.exists(dir.resolve(names.get(3))));assertTrue(Files.exists(dir.resolve("other.svg")));
    }
    @Test public void reporterCreatesDiagramsFromExistingLongtable() throws Exception {
        Path dir=tmp.newFolder().toPath();
        try(LongTableWriter w=new LongTableWriter(dir.resolve("study_longtable.csv").toString(),true,"project","study","hash")) {
            for(var r:fixture())w.signalRow(r.timeS(),r.objectType(),r.objectId(),r.signal(),r.value(),"",r.stage(),null,"");
        }
        var empty=com.typesafe.config.ConfigFactory.empty();
        DcReporter.run(new DcStudyContext(dir.resolve("study.conf"),dir,empty,empty,"study","study","",dir,dir));
        assertEquals(4,Files.readAllLines(dir.resolve("study_train_current_diagrams.txt")).size());
        assertTrue(Files.exists(dir.resolve("study_results_train.xlsx")));
    }
    @Test public void preservesRecordedCurveAndRejectsInvalidDescription() {
        var c=TrainCurrentDiagram.parseCurve("400:0;500:0;560:1200;600:3500;640:6000;900:6000");
        assertEquals(2350,c.currentLimitA(580),0);
        assertThrows(IllegalArgumentException.class,()->TrainCurrentDiagram.parseCurve("500:0;400:2"));
    }
    public static void main(String[] args) throws Exception {
        TrainCurrentDiagram.write(fixture(),Path.of(args[0]),"synthetic_preview");
    }
}
