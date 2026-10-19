package org.supply.solver.electrical;

import org.junit.Test;
import org.supply.domain.SystemParameters;
import org.supply.domain.TrainCurrentCurve;
import org.supply.solver.model.TrainLoadElement;
import org.supply.solver.testsupport.ElectricalTestCases;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import static org.junit.Assert.assertTrue;

/** Synthetic characteristic preview; no simulated operating points are included. */
public final class TrainPowerCurveImageTest {
    private static final Color[] COLORS = {
            new Color(35,100,185), new Color(0,145,170),
            new Color(140,85,180), new Color(210,120,30)
    };
    private static final double[] POWERS = {1e6,3e6,5e6,8e6};
    private static final int LEFT=85, TOP=110, PW=510, PH=510;
    private static final double IMAX=8000;

    @Test public void writesTrainPowerCurrentCurveImage() throws Exception {
        SystemParameters p=ElectricalTestCases.SYSTEM_PARAMETERS;
        BufferedImage image=new BufferedImage(1320,790,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0,0,1320,790);
        g.setColor(new Color(30,40,55)); g.setFont(new Font("SansSerif",Font.BOLD,23));
        g.drawString("Train current / voltage characteristics",65,36);
        g.setFont(new Font("SansSerif",Font.PLAIN,14));
        g.drawString("Synthetic requested-power curves; current limits evaluated by TrainLoadElement",65,63);
        panel(g,p,curve(1200,3500,6000),0,"70 m single train",6000);
        panel(g,p,curve(2400,7000,7000),650,"140 m coupled train — total current",7000);
        for(int i=0;i<POWERS.length;i++) {
            int x=90+i*245;
            g.setColor(COLORS[i]);g.setStroke(new BasicStroke(2));g.drawLine(x,694,x+30,694);
            g.setColor(Color.DARK_GRAY);g.drawString(String.format("±%.0f MW requested",POWERS[i]/1e6),x+40,699);
        }
        g.setColor(Color.DARK_GRAY);g.setFont(new Font("SansSerif",Font.PLAIN,13));
        g.drawString("Solid: traction     Dashed: regeneration     Heavy outline: model current envelope",90,734);
        g.drawString("Curves stop at u_min and u_max. No simulated operating points are shown.",90,759);
        g.dispose();
        File out=new File("build/reports/train-power-curves.png");out.getParentFile().mkdirs();
        assertTrue(ImageIO.write(image,"png",out));assertTrue(out.isFile());
    }
    private static TrainCurrentCurve curve(double i560,double i600,double i640) {
        return new TrainCurrentCurve(List.of(new TrainCurrentCurve.Point(400,0),
                new TrainCurrentCurve.Point(500,0),new TrainCurrentCurve.Point(560,i560),
                new TrainCurrentCurve.Point(600,i600),new TrainCurrentCurve.Point(640,i640),
                new TrainCurrentCurve.Point(900,i640)));
    }
    private static void panel(Graphics2D original,SystemParameters p,TrainCurrentCurve c,
                              int offset,String title,double maximum) {
        Graphics2D g=(Graphics2D)original.create();g.translate(offset,0);
        double low=p.uMinV(), high=p.uMaxV();
        g.setFont(new Font("SansSerif",Font.BOLD,17));g.setColor(new Color(30,40,55));
        g.drawString(title,LEFT,91);
        g.setFont(new Font("SansSerif",Font.PLAIN,12));
        for(int a=-8000;a<=8000;a+=2000) {
            int y=y(a);g.setColor(new Color(225,230,235));g.drawLine(LEFT,y,LEFT+PW,y);
            g.setColor(Color.DARK_GRAY);g.drawString(Integer.toString(a),LEFT-53,y+4);
        }
        for(double u=low;u<=high;u+=50) {
            int x=x(u,low,high);g.setColor(new Color(230,234,239));g.drawLine(x,TOP,x,TOP+PH);
            g.setColor(Color.DARK_GRAY);g.drawString(String.format("%.0f",u),x-11,TOP+PH+22);
        }
        g.setColor(Color.GRAY);g.drawRect(LEFT,TOP,PW,PH);g.drawLine(LEFT,y(0),LEFT+PW,y(0));
        g.drawString("Current [A]",LEFT,TOP-7);g.drawString("Voltage [V]",LEFT+PW-70,TOP+PH+46);
        g.drawString(String.format("u_min = %.0f V",low),LEFT,TOP+PH+65);
        g.drawString(String.format("u_max = %.0f V",high),LEFT+PW-100,TOP+PH+65);
        Shape old=g.getClip();g.clipRect(LEFT,TOP,PW+1,PH+1);
        for(int i=0;i<POWERS.length;i++)for(int sign:new int[]{1,-1}) {
            g.setColor(COLORS[i]);
            g.setStroke(sign>0?new BasicStroke(1.8f):new BasicStroke(1.8f,BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_ROUND,10,new float[]{7,5},0));
            path(g,p,c,maximum,sign*POWERS[i],low,high);
        }
        g.setColor(new Color(35,40,48));g.setStroke(new BasicStroke(3.2f));
        path(g,p,c,maximum,1e12,low,high);path(g,p,c,maximum,-1e12,low,high);
        g.setClip(old);g.dispose();
    }
    private static void path(Graphics2D g,SystemParameters p,TrainCurrentCurve c,double max,
                             double power,double low,double high) {
        Path2D path=new Path2D.Double();
        for(int k=0;k<=PW*2;k++) {
            double u=low+(high-low)*k/(PW*2.0);
            double current=new TrainLoadElement("preview","F","R",power,u,p,max,c).currentA();
            double x=x(u,low,high), y=y(current);
            if(k==0)path.moveTo(x,y);else path.lineTo(x,y);
        }
        g.draw(path);
    }
    private static int x(double u,double low,double high) {return LEFT+(int)Math.round((u-low)/(high-low)*PW);}
    private static int y(double i) {return TOP+(int)Math.round((IMAX-i)/(2*IMAX)*PH);}
}
