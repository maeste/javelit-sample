package service;

import model.BenchmarkEntry;

import org.icepear.echarts.Bar;
import org.icepear.echarts.Line;
import org.icepear.echarts.components.coord.cartesian.CategoryAxis;
import org.icepear.echarts.components.coord.cartesian.ValueAxis;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ChartBuilder {

    private ChartBuilder() {}

    public static Line buildPPChart(List<BenchmarkEntry> entries, List<String> selectedModels) {
        return buildLineChart(entries, selectedModels, "PP");
    }

    public static Line buildTGChart(List<BenchmarkEntry> entries, List<String> selectedModels) {
        return buildLineChart(entries, selectedModels, "TG");
    }

    private static Line buildLineChart(List<BenchmarkEntry> entries, List<String> selectedModels, String testType) {
        String[] depths = getDepthLabels(entries, testType);
        Line line = new Line()
                .setLegend()
                .setTooltip("axis")
                .addXAxis(new CategoryAxis().setData(depths).setName("Context Depth"))
                .addYAxis(new ValueAxis().setName("Tokens/s"));
        for (String model : selectedModels) {
            line.addSeries(model, getSeriesData(entries, model, testType, depths));
        }
        return line;
    }

    public static Bar buildComparisonChart(List<BenchmarkEntry> entries, List<String> selectedModels) {
        Set<Integer> depthSet = entries.stream()
                .filter(e -> selectedModels.contains(e.getModelType()))
                .map(BenchmarkEntry::getNDepth)
                .collect(Collectors.toCollection(TreeSet::new));
        String[] depths = depthSet.stream().map(String::valueOf).toArray(String[]::new);

        Bar bar = new Bar().setLegend().setTooltip("axis").addXAxis(depths).addYAxis();
        for (String model : selectedModels) {
            bar.addSeries(model + " (PP)", getSeriesData(entries, model, "PP", depths));
            bar.addSeries(model + " (TG)", getSeriesData(entries, model, "TG", depths));
        }
        return bar;
    }

    private static String[] getDepthLabels(List<BenchmarkEntry> entries, String testType) {
        return entries.stream()
                .filter(e -> e.getTestType().equals(testType))
                .map(BenchmarkEntry::getNDepth).distinct().sorted()
                .map(String::valueOf).toArray(String[]::new);
    }

    private static Number[] getSeriesData(List<BenchmarkEntry> entries, String model, String testType, String[] depths) {
        Map<String, Double> dataMap = entries.stream()
                .filter(e -> e.getModelType().equals(model) && e.getTestType().equals(testType))
                .collect(Collectors.toMap(e -> String.valueOf(e.getNDepth()), BenchmarkEntry::getAvgTs, (a, b) -> a));
        Number[] values = new Number[depths.length];
        for (int i = 0; i < depths.length; i++) values[i] = dataMap.getOrDefault(depths[i], 0.0);
        return values;
    }

    public static List<String> getDistinctModels(List<BenchmarkEntry> entries) {
        return entries.stream().map(BenchmarkEntry::getModelType).distinct().sorted().collect(Collectors.toList());
    }

    public static List<String> getDistinctHardware(List<BenchmarkEntry> entries) {
        return entries.stream()
                .map(e -> e.getCpuInfo() + " / " + e.getGpuInfo() + " (" + e.getBackends() + ")")
                .distinct().collect(Collectors.toList());
    }

    public static List<Integer> getSortedDepths(List<BenchmarkEntry> entries) {
        return entries.stream().map(BenchmarkEntry::getNDepth).distinct().sorted().collect(Collectors.toList());
    }
}
