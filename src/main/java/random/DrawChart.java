package random;

import help.Utilities;
import org.jetbrains.annotations.NotNull;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DrawChart extends ApplicationFrame {

    /**
     * A demonstration application showing an XY series containing a null value.
     *
     * @param title  the frame title.
     */
    public DrawChart(String title, List<Double> data) {
        super(title);
        drawChart(data);
    }

    private void drawChart(@NotNull List<Double> accuracy) {
        final XYSeries series = new XYSeries("Accuracy");
        double x = 1;

        for (double y : accuracy) {
            series.add(x,y);
            x ++;
        }

        final XYSeriesCollection data = new XYSeriesCollection(series);
        final JFreeChart chart = ChartFactory.createXYLineChart(
                "Accuracy",
                "Iterations",
                "Accuracy",
                data,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        final ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(500, 270));
        setContentPane(chartPanel);
    }

    /**
     * Starting point for the demonstration application.
     *
     * @param args  ignored.
     */
    public static void main(@NotNull final String[] args) {
        String accuracyFilePath = args[0];
        List<Double> accuracy = new ArrayList<>();

        try {
            accuracy = Utilities.readList(accuracyFilePath);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        final DrawChart demo = new DrawChart("Accuracy", accuracy);
        demo.pack();
        demo.setVisible(true);

    }

}