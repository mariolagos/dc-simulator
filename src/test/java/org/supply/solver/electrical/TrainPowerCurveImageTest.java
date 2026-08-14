package org.supply.solver.electrical;

import org.junit.Test;
import org.supply.domain.SystemParameters;
import org.supply.solver.model.TrainLoadElement;
import org.supply.solver.testsupport.ElectricalTestCases;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import static org.junit.Assert.assertTrue;


public final class TrainPowerCurveImageTest {

    @Test
    public void writesTrainPowerCurrentCurveImage() throws Exception {

        SystemParameters p = ElectricalTestCases.SYSTEM_PARAMETERS;

        int width = 1200;
        int height = 800;

        BufferedImage image =
                new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = image.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        int left = 90;
        int right = 260;
        int top = 40;
        int bottom = 80;

        double uMinPlot = p.uMinV() * 0.9;
        double uMaxPlot = p.uMaxV() * 1.1;
        double iMinPlot = -p.iMaxA() * 1.1;
        double iMaxPlot = p.iMaxA() * 1.1;

        drawZeroAxes(g, 0, uMaxPlot, iMinPlot, iMaxPlot,
                left, right, top, bottom, width, height);
        drawAxes(g, left, top, width - right, height - bottom);

        drawVerticalMarker(g, p.uMinV(), uMinPlot, uMaxPlot, left, right, top, bottom, width, height, "u_min1");
        drawVerticalMarker(g, p.uCutoffV(), uMinPlot, uMaxPlot, left, right, top, bottom, width, height, "u_min2");
        drawVerticalMarker(g, p.uMaxV(), uMinPlot, uMaxPlot, left, right, top, bottom, width, height, "u_max");

        double[] powersW = {
                1_000_000.0,
                3_000_000.0,
                5_000_000.0,
                8_000_000.0,
                -1_000_000.0,
                -3_000_000.0,
                -5_000_000.0,
                -8_000_000.0
        };

        Color[] colors = {
                new Color(0, 80, 200),
                new Color(0, 130, 230),
                new Color(0, 170, 255),
                new Color(0, 220, 255),
                new Color(0, 130, 60),
                new Color(0, 170, 90),
                new Color(0, 210, 120),
                new Color(0, 240, 120)
        };

        for (int i = 0; i < powersW.length; i++) {
            drawCurve(
                    g,
                    powersW[i],
                    p,
                    uMinPlot,
                    uMaxPlot,
                    iMinPlot,
                    iMaxPlot,
                    left,
                    right,
                    top,
                    bottom,
                    width,
                    height,
                    colors[i]
            );
        }

        drawCurrentLimitCurves(
                g,
                p,
                uMinPlot,
                uMaxPlot,
                iMinPlot,
                iMaxPlot,
                left,
                right,
                top,
                bottom,
                width,
                height
        );
        drawTitle(g, "Train current curves from requested power and voltage");
        drawLegend(g, powersW, colors, width);

        File out = new File("build/reports/train-power-curves.png");
        out.getParentFile().mkdirs();

        ImageIO.write(image, "png", out);

        assertTrue(out.isFile());
    }

    private static void drawZeroAxes(
            Graphics2D g,
            double uMinPlot,
            double uMaxPlot,
            double iMinPlot,
            double iMaxPlot,
            int left,
            int right,
            int top,
            int bottom,
            int width,
            int height
    ) {
        Stroke oldStroke = g.getStroke();

        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2.0f));

        // I = 0
        int y0 = y(
                0.0,
                iMinPlot,
                iMaxPlot,
                top,
                bottom,
                height
        );

        g.drawLine(
                left,
                y0,
                width - right,
                y0
        );

        // U = 0
        if (uMinPlot <= 0.0 && 0.0 <= uMaxPlot) {

            int x0 = x(
                    0.0,
                    uMinPlot,
                    uMaxPlot,
                    left,
                    right,
                    width
            );

            g.drawLine(
                    x0,
                    top,
                    x0,
                    height - bottom
            );
        }

        g.setStroke(oldStroke);
    }

    private static void drawCurrentLimitCurves(
            Graphics2D g,
            SystemParameters p,
            double uMinPlot,
            double uMaxPlot,
            double iMinPlot,
            double iMaxPlot,
            int left,
            int right,
            int top,
            int bottom,
            int width,
            int height
    ) {
        g.setStroke(new BasicStroke(4.0f));

        // Traction limit: ramp from u_min1 to u_min2, then +i_max.
        g.setColor(Color.BLACK);

        int lastX = -1;
        int lastY = -1;

        for (int px = left; px <= width - right; px++) {
            double u =
                    uMinPlot
                            + (uMaxPlot - uMinPlot)
                            * (px - left)
                            / (double) (width - left - right);

            double currentA = tractionCurrentLimitA(u, p);
            int py = y(currentA, iMinPlot, iMaxPlot, top, bottom, height);

            if (lastX >= 0) {
                g.drawLine(lastX, lastY, px, py);
            }

            lastX = px;
            lastY = py;
        }

        // Regenerative limit: -i_max until u_max, then 0.
        g.setColor(Color.DARK_GRAY);

        lastX = -1;
        lastY = -1;

        for (int px = left; px <= width - right; px++) {
            double u =
                    uMinPlot
                            + (uMaxPlot - uMinPlot)
                            * (px - left)
                            / (double) (width - left - right);

            double currentA = regenerativeCurrentLimitA(u, p);
            int py = y(currentA, iMinPlot, iMaxPlot, top, bottom, height);

            if (lastX >= 0) {
                g.drawLine(lastX, lastY, px, py);
            }

            lastX = px;
            lastY = py;
        }

        g.setStroke(new BasicStroke(1.0f));
    }

    private static double trainCurrentA(
            double requestedPowerW,
            double voltageV,
            SystemParameters p
    ) {
        return new TrainLoadElement(
                "T1",
                "F_TRAIN",
                "R_TRAIN",
                requestedPowerW,
                voltageV,
                p
        ).currentA();
    }


    private static double tractionCurrentLimitA(
            double voltageV,
            SystemParameters p
    ) {
        if (voltageV <= p.uMinV()) {
            return 0.0;
        }

        if (voltageV < p.uCutoffV()) {
            return p.iMaxA()
                    * (voltageV - p.uMinV())
                    / (p.uCutoffV() - p.uMinV());
        }

        return p.iMaxA();
    }

    private static double regenerativeCurrentLimitA(
            double voltageV,
            SystemParameters p
    ) {
        if (voltageV >= p.uMaxV()) {
            return 0.0;
        }

        return -p.iMaxA();
    }

    private static void drawCurve(
            Graphics2D g,
            double requestedPowerW,
            SystemParameters p,
            double uMinPlot,
            double uMaxPlot,
            double iMinPlot,
            double iMaxPlot,
            int left,
            int right,
            int top,
            int bottom,
            int width,
            int height,
            Color color
    ) {
        g.setColor(color);
        g.setStroke(new BasicStroke(2.0f));

        int lastX = -1;
        int lastY = -1;

        for (int px = left; px <= width - right; px++) {
            double u =
                    uMinPlot
                            + (uMaxPlot - uMinPlot)
                            * (px - left)
                            / (double) (width - left - right);

            double currentA = trainCurrentA(requestedPowerW, u, p);

            int py = y(currentA, iMinPlot, iMaxPlot, top, bottom, height);

            if (lastX >= 0) {
                g.drawLine(lastX, lastY, px, py);
            }

            lastX = px;
            lastY = py;
        }
    }

    private static void drawAxes(
            Graphics2D g,
            int left,
            int top,
            int right,
            int bottom
    ) {
        g.setColor(Color.RED);
        g.setStroke(new BasicStroke(1.5f));

        g.drawLine(left, bottom, right, bottom);
        g.drawLine(left, top, left, bottom);

        g.drawString("Voltage [V]", right - 90, bottom + 35);
        g.drawString("Current [A]", left - 70, top + 15);
    }

    private static void drawVerticalMarker(
            Graphics2D g,
            double voltage,
            double uMinPlot,
            double uMaxPlot,
            int left,
            int right,
            int top,
            int bottom,
            int width,
            int height,
            String label
    ) {
        int x = x(voltage, uMinPlot, uMaxPlot, left, right, width);

        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(1.0f));
        g.drawLine(x, top, x, height - bottom);

        g.setColor(Color.DARK_GRAY);
        g.drawString(label + " = " + (int) voltage + " V", x + 4, top + 20);
    }

    private static void drawTitle(Graphics2D g, String title) {
        g.setColor(Color.BLACK);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 18f));
        g.drawString(title, 90, 28);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
    }

    private static void drawLegend(
            Graphics2D g,
            double[] powersW,
            Color[] colors,
            int width
    ) {
        int x = width - 250;
        int y = 70;

        g.setFont(g.getFont().deriveFont(Font.PLAIN, 13f));

        for (int i = 0; i < powersW.length; i++) {
            g.setColor(colors[i]);
            g.setStroke(new BasicStroke(3.0f));
            g.drawLine(x, y + i * 22, x + 30, y + i * 22);

            g.setColor(Color.BLACK);
            g.drawString(
                    String.format("%.0f MW", powersW[i] / 1_000_000.0),
                    x + 40,
                    y + 5 + i * 22
            );
        }

        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(4.0f));
        g.drawLine(x, y + powersW.length * 22, x + 30, y + powersW.length * 22);
        g.drawString("+i_train_max / traction limit", x + 40, y + 5 + powersW.length * 22);

        g.setColor(Color.DARK_GRAY);
        g.drawLine(x, y + (powersW.length + 1) * 22, x + 30, y + (powersW.length + 1) * 22);
        g.drawString("-i_train_max / regen limit", x + 40, y + 5 + (powersW.length + 1) * 22);
    }

    private static int x(
            double voltage,
            double uMinPlot,
            double uMaxPlot,
            int left,
            int right,
            int width
    ) {
        return left + (int) Math.round(
                (voltage - uMinPlot)
                        / (uMaxPlot - uMinPlot)
                        * (width - left - right)
        );
    }

    private static int y(
            double current,
            double iMinPlot,
            double iMaxPlot,
            int top,
            int bottom,
            int height
    ) {
        return top + (int) Math.round(
                (iMaxPlot - current)
                        / (iMaxPlot - iMinPlot)
                        * (height - top - bottom)
        );
    }

}