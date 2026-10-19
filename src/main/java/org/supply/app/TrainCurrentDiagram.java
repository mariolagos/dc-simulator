package org.supply.app;

import org.supply.domain.SystemParameters;
import org.supply.domain.TrainCurrentCurve;
import org.supply.solver.model.TrainLoadElement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.supply.app.DcReporter.LongTableRow;

/** Standalone SVG reports based solely on recorded longtable values. */
final class TrainCurrentDiagram {
    record Sample(String train, double time, double voltage, double current) {}
    record Profile(TrainCurrentCurve curve, Double maximum) {}
    record Group(Profile profile, List<Sample> samples, Set<String> trains) {}
    private record Key(String train, double time) {}
    private static final int L=90,T=105,W=1000,H=510;

    static void write(List<LongTableRow> rows, Path directory, String study) throws IOException {
        Map<String,Double> system=new HashMap<>();
        Map<String,Double> maxima=new HashMap<>();
        Map<String,TrainCurrentCurve> curves=new HashMap<>();
        Map<Key,double[]> pairs=new LinkedHashMap<>();
        for(LongTableRow r:rows) {
            if("SYSTEM".equals(r.objectType()) && r.timeS()==null &&
                    Set.of("u_min_V","u_cutoff_V","u_max_V").contains(r.signal()))
                system.put(r.signal(),number(r.value()));
            if(!"TRAIN".equals(r.objectType())) continue;
            if(r.timeS()==null) {
                if("i_train_max_A".equals(r.signal())) maxima.put(r.objectId(),number(r.value()));
                if("i_train_limit_curve".equals(r.signal())) curves.put(r.objectId(),parseCurve(r.value()));
            } else if("RESULT".equals(r.stage()) &&
                    ("u_V".equals(r.signal()) || "i_A".equals(r.signal()))) {
                double[] pair=pairs.computeIfAbsent(new Key(r.objectId(),r.timeS()),
                        ignored->new double[]{Double.NaN,Double.NaN});
                pair["u_V".equals(r.signal())?0:1]=number(r.value());
            }
        }
        Map<Profile,Group> groups=new LinkedHashMap<>();
        for(var e:pairs.entrySet()) {
            double[] v=e.getValue();if(!Double.isFinite(v[0]) || !Double.isFinite(v[1])) continue;
            String id=e.getKey().train();TrainCurrentCurve curve=curves.get(id);
            Profile p=new Profile(curve,curve==null?maxima.get(id):Double.valueOf(curve.maximumA()));
            Group group=groups.computeIfAbsent(p,k->new Group(k,new ArrayList<>(),new TreeSet<>()));
            group.trains().add(id);group.samples().add(new Sample(id,e.getKey().time(),v[0],v[1]));
        }
        if(groups.isEmpty()) return;
        Double min=system.get("u_min_V"),cut=system.get("u_cutoff_V"),max=system.get("u_max_V");
        if(min==null || cut==null || max==null || !(min>0 && min<cut && cut<max))
            throw new IllegalArgumentException("Current diagrams require recorded u_min_V < u_cutoff_V < u_max_V");
        SystemParameters params=new SystemParameters(cut,min,cut,max,1,1,0);
        Files.createDirectories(directory);
        // Build all outputs first; remove only older diagrams owned by this report.
        Map<String,String> outputs=new LinkedHashMap<>();int index=0;
        for(Group group:groups.values()) {
            String name=study+"_train_current_"+String.format(Locale.ROOT,"%03d",++index)+".svg";
            outputs.put(name,render(group,params,study,index));
        }
        Path manifest=directory.resolve(study+"_train_current_diagrams.txt");
        if(Files.exists(manifest))for(String old:Files.readAllLines(manifest)) {
            if(old.matches(java.util.regex.Pattern.quote(study)+"_train_current_[0-9]+\\.svg") && !outputs.containsKey(old))
                Files.deleteIfExists(directory.resolve(old));
        }
        for(var e:outputs.entrySet())Files.writeString(directory.resolve(e.getKey()),e.getValue(),StandardCharsets.UTF_8);
        Files.write(manifest,outputs.keySet(),StandardCharsets.UTF_8);
    }
    private static double number(String value) {return Double.parseDouble(value);}
    static TrainCurrentCurve parseCurve(String text) {
        List<TrainCurrentCurve.Point> points=new ArrayList<>();
        for(String point:text.split(";")) {
            String[] p=point.split(":",-1);
            if(p.length!=2)throw new IllegalArgumentException("Invalid recorded current curve: "+text);
            points.add(new TrainCurrentCurve.Point(number(p[0]),number(p[1])));
        }
        return new TrainCurrentCurve(points);
    }
    private static String render(Group group,SystemParameters p,String study,int index) {
        double xmin=.9*p.uMinV(),xmax=1.1*p.uMaxV();
        double ymin=0,ymax=group.profile().maximum()==null?0:group.profile().maximum();
        for(Sample s:group.samples()) {xmin=Math.min(xmin,s.voltage());xmax=Math.max(xmax,s.voltage());
            ymin=Math.min(ymin,s.current());ymax=Math.max(ymax,s.current());}
        double step=niceStep(Math.max(1,ymax-ymin)/8);
        ymin=Math.floor((ymin<0?ymin*1.08:-step)/step)*step;
        ymax=Math.ceil(Math.max(step,ymax*1.08)/step)*step;
        StringBuilder b=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1200\" height=\"800\" viewBox=\"0 0 1200 800\">\n");
        b.append("<rect width=\"1200\" height=\"800\" fill=\"white\"/><g font-family=\"sans-serif\" fill=\"#263445\">\n");
        text(b,75,35,22,study+" — Train current / voltage, group "+index);
        text(b,75,61,13,group.trains().size()+" trains; "+group.samples().size()+" recorded operating points");
        text(b,75,82,12,group.profile().maximum()==null?"Current characteristic not recorded":
                "Traction limit: "+fmt(group.profile().maximum())+" A; "+
                        (group.profile().curve()==null?"system voltage ramp":group.profile().curve().points().size()+" voltage breakpoints"));
        b.append("<defs><clipPath id=\"plot\"><rect x=\"90\" y=\"105\" width=\"1000\" height=\"510\"/></clipPath></defs>");
        for(double v=ymin;v<=ymax+step*.1;v+=step) {
            double y=y(v,ymin,ymax);line(b,L,y,L+W,y,"#e2e7ed",1);
            text(b,24,y+4,12,fmt(v));
        }
        double us=niceStep((xmax-xmin)/9);
        for(double v=Math.ceil(xmin/us)*us;v<=xmax;v+=us) {
            double x=x(v,xmin,xmax);line(b,x,T,x,T+H,"#e2e7ed",1);text(b,x-14,T+H+23,12,fmt(v));
        }
        line(b,L,y(0,ymin,ymax),L+W,y(0,ymin,ymax),"#8190a0",1);
        text(b,L,T-5,13,"Current [A]");text(b,L+W-80,T+H+47,13,"Voltage [V]");
        for(double u:new double[]{p.uMinV(),p.uMaxV()}) {
            double x=x(u,xmin,xmax);line(b,x,T,x,T+H,"#a0a8b2",1);
            text(b,x+4,T+17,12,(u==p.uMinV()?"u_min = ":"u_max = ")+fmt(u)+" V");
        }
        b.append("<g clip-path=\"url(#plot)\">");
        if(group.profile().maximum()!=null) {
            // Mark each voltage interval where the traction limit ramps.
            List<double[]> ramps=new ArrayList<>();
            if(group.profile().curve()==null) ramps.add(new double[]{p.uMinV(),p.uCutoffV()});
            else {
                var points=group.profile().curve().points();
                for(int n=1;n<points.size();n++) {
                    var a=points.get(n-1);var z=points.get(n);
                    if(a.currentA()!=z.currentA()) {
                        double lo=Math.max(p.uMinV(),a.voltageV()),hi=Math.min(p.uMaxV(),z.voltageV());
                        if(hi>lo)ramps.add(new double[]{lo,hi});
                    }
                }
            }
            for(double[] interval:ramps) {
                double a=x(interval[0],xmin,xmax),z=x(interval[1],xmin,xmax);
                b.append("<rect class=\"ramp-region\" x=\"").append(a).append("\" y=\"").append(T)
                        .append("\" width=\"").append(z-a).append("\" height=\"").append(H)
                        .append("\" fill=\"#7496b8\" fill-opacity=\"0.07\"><title>Traction current ramp: ")
                        .append(fmt(interval[0])).append("–").append(fmt(interval[1])).append(" V</title></rect>");
                b.append("<g stroke-dasharray=\"3 4\">");
                line(b,a,T,a,T+H,"#8c9caa",.8);line(b,z,T,z,T+H,"#8c9caa",.8);b.append("</g>");
            }
            double iy=y(group.profile().maximum(),ymin,ymax);
            b.append("<g class=\"i-max-reference\" stroke-dasharray=\"6 4\">");
            line(b,x(p.uMinV(),xmin,xmax),iy,x(p.uMaxV(),xmin,xmax),iy,"#687482",.9);
            b.append("</g>");
            text(b,x(p.uMaxV(),xmin,xmax)-125,iy-9,12,"i_max = "+fmt(group.profile().maximum())+" A");
        }
        for(Sample s:group.samples()) {
            b.append("<circle cx=\"").append(x(s.voltage(),xmin,xmax)).append("\" cy=\"")
                    .append(y(s.current(),ymin,ymax)).append("\" r=\"2.5\" fill=\"")
                    .append(s.current()<0?"#d98232":"#287eb5").append("\" fill-opacity=\"0.42\"><title>")
                    .append(xml(s.train()+"; t="+s.time()+" s; U="+s.voltage()+" V; I="+s.current()+" A"))
                    .append("</title></circle>\n");
        }
        if(group.profile().maximum()!=null) {
            b.append("<path fill=\"none\" stroke=\"#202832\" stroke-width=\"1.6\" d=\"");
            for(int k=0;k<=1000;k++) {
                double u=p.uMinV()+(p.uMaxV()-p.uMinV())*k/1000.;
                double i=new TrainLoadElement("diagram","F","R",Double.MAX_VALUE,u,p,
                        group.profile().maximum(),group.profile().curve()).currentA();
                b.append(k==0?"M":" L").append(x(u,xmin,xmax)).append(' ').append(y(i,ymin,ymax));
            }
            b.append("\"/>");
        }
        b.append("</g>");
        text(b,90,680,13,"Blue: traction / zero current   Orange: regeneration   Black: traction current envelope");
        text(b,90,700,12,"Shaded bands: voltage ramp intervals   Dashed horizontal: i_max reference");
        text(b,90,720,12,"Points use recorded u_V and i_A. Hover over a point for train and time. Points outside limits remain visible.");
        text(b,90,741,12,group.profile().maximum()==null?
                "No recorded current characteristic: operating points only (older longtable).":
                "Regeneration points are shown without a lower envelope; traction limits do not define regenerative limits.");
        String ids=String.join(", ",group.trains());
        text(b,90,765,12,"Trains: "+(ids.length()>145?ids.substring(0,142)+"...":ids));
        b.append("<desc>").append(xml("Trains: "+ids+"; profile: "+group.profile())).append("</desc></g></svg>");
        return b.toString();
    }
    private static double niceStep(double raw) {double unit=Math.pow(10,Math.floor(Math.log10(raw)));double v=raw/unit;return (v<=1?1:v<=2?2:v<=5?5:10)*unit;}
    private static double x(double u,double lo,double hi) {return L+(u-lo)/(hi-lo)*W;}
    private static double y(double i,double lo,double hi) {return T+(hi-i)/(hi-lo)*H;}
    private static String fmt(double value) {return String.format(Locale.ROOT,"%.0f",value);}
    private static String xml(String s) {return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static void text(StringBuilder b,double x,double y,int size,String value) {b.append("<text x=\"").append(x).append("\" y=\"").append(y).append("\" font-size=\"").append(size).append("\">").append(xml(value)).append("</text>\n");}
    private static void line(StringBuilder b,double x1,double y1,double x2,double y2,String color,double width) {b.append("<line x1=\"").append(x1).append("\" y1=\"").append(y1).append("\" x2=\"").append(x2).append("\" y2=\"").append(y2).append("\" stroke=\"").append(color).append("\" stroke-width=\"").append(width).append("\"/>\n");}
}
