package org.supply.app;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class GraphicalTimetableViewsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private DcStudyContext context(String report) throws Exception {
        Config scenario = ConfigFactory.parseString("dcsim {}\n" + report);
        Path directory = temporary.newFolder().toPath();
        return new DcStudyContext(directory.resolve("study.conf"), directory,
                scenario, scenario.getConfig("dcsim"), "study", "Study", "",
                directory.resolve("exports"), directory.resolve("results"));
    }

    @Test public void createsTwoReportViewsWithoutChangingSimulationIdentity() throws Exception {
        DcStudyContext original = context("report.graphical-timetable { base-routes = ["
                + "{id=WE, sections=[101,200,102]}, {id=NwSe, sections=[201,200,202]}] }");
        List<DcStudyContext> views = DcReporter.graphicalTimetableContexts(original);
        assertEquals(2, views.size());
        assertEquals("study_WE", views.get(0).studyId());
        assertEquals("study_NwSe", views.get(1).studyId());
        for (DcStudyContext view : views) {
            assertSame(original.dcsim(), view.dcsim());
            assertEquals(original.resultDirectory(), view.resultDirectory());
            assertFalse(view.scenario().hasPath("report.graphical-timetable.base-routes"));
        }
        assertEquals("NwSe", views.get(1).scenario().getString("report.graphical-timetable.base-route.id"));
        assertTrue(original.scenario().hasPath("report.graphical-timetable.base-routes"));
    }

    @Test public void preservesSingleRouteAndDisabledReporting() throws Exception {
        DcStudyContext single = context("report.graphical-timetable { base-route {id=WE, sections=[101,200,102]} }");
        assertSame(single, DcReporter.graphicalTimetableContexts(single).get(0));
        DcStudyContext disabled = context("report.graphical-timetable {enabled=false, base-routes=[]}");
        assertSame(disabled, DcReporter.graphicalTimetableContexts(disabled).get(0));
    }

    @Test public void mapsSharedTrafficAndExcludesOtherBranches() throws Exception {
        Path segments = temporary.newFile("segments.csv").toPath();
        Files.writeString(segments, "section,from_rwy,to_rwy,start_model_m,length_m\n"
                + "101,101 12+400,101 18+750,0,6350\n"
                + "200,200 0+000,200 6+200,0,6200\n"
                + "102,102 41+300,102 48+100,0,6800\n"
                + "201,201 7+850,201 14+000,0,6150\n"
                + "202,202 23+600,202 30+050,0,6450\n");
        BaseRoutePositionMapper we = BaseRoutePositionMapper.load(segments,
                List.of("101", "200", "102"), Map.of());
        BaseRoutePositionMapper nwse = BaseRoutePositionMapper.load(segments,
                List.of("201", "200", "202"), Map.of());
        assertEquals(6450.0, we.map("200", 100.0), 0.0);
        assertEquals(6250.0, nwse.map("200", 100.0), 0.0);
        assertNull(we.map("201", 100.0));
        assertNull(nwse.map("101", 100.0));
        assertNull(we.map("202", 100.0));
        assertNull(nwse.map("102", 100.0));
        BaseRoutePositionMapper reversed = BaseRoutePositionMapper.load(segments,
                List.of("202", "200", "201"), Map.of("202", true, "200", true, "201", true));
        assertEquals(6450.0 + 6200.0 - 100.0, reversed.map("200", 100.0), 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateReportIds() throws Exception {
        DcReporter.graphicalTimetableContexts(context("report.graphical-timetable {base-routes=["
                + "{id=WE, sections=[101]}, {id=WE, sections=[201]}]}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyReportList() throws Exception {
        DcReporter.graphicalTimetableContexts(context("report.graphical-timetable {base-routes=[]}"));
    }
}
