package pages;

import model.BenchmarkEntry;
import service.ChartBuilder;

import io.javelit.core.Jt;
import io.javelit.core.JtContainer;
import io.javelit.components.chart.EchartsComponent;

import java.util.*;
import java.util.stream.Collectors;

public class ExplorerPage {

    private ExplorerPage() {}

    @SuppressWarnings("unchecked")
    public static void render() {
        Jt.title("Performance Explorer").use();

        List<BenchmarkEntry> benchmarks = (List<BenchmarkEntry>) Jt.sessionState()
                .getOrDefault("benchmarks", new ArrayList<>());

        if (benchmarks.isEmpty()) {
            Jt.warning("No benchmark data available. Please upload data on the Upload page first.").use();
            Jt.pageLink("/upload", "Go to Upload").use();
            return;
        }

        List<String> allModels = ChartBuilder.getDistinctModels(benchmarks);

        Jt.header("Filters").use(Jt.SIDEBAR);
        Jt.subheader("Models").use(Jt.SIDEBAR);
        List<String> selectedModels = new ArrayList<>();
        for (String model : allModels) {
            if (Jt.checkbox(model).value(true).use(Jt.SIDEBAR)) selectedModels.add(model);
        }

        Jt.subheader("Test Type").use(Jt.SIDEBAR);
        String testFilter = Jt.radio("Test Type", List.of("Prompt Processing", "Token Generation", "Both")).use(Jt.SIDEBAR);

        if (selectedModels.isEmpty()) {
            Jt.warning("Please select at least one model from the sidebar.").use();
            return;
        }

        var tabs = Jt.tabs(List.of("Prompt Processing", "Token Generation", "Comparison")).use();

        JtContainer ppTab = tabs.tab("Prompt Processing");
        if ("Prompt Processing".equals(testFilter) || "Both".equals(testFilter)) {
            Jt.subheader("Prompt Processing Throughput vs Context Depth").use(ppTab);
            Jt.echarts(ChartBuilder.buildPPChart(benchmarks, selectedModels)).height(450).theme(EchartsComponent.Theme.ROMA).use(ppTab);
        } else {
            Jt.text("Select 'Prompt Processing' or 'Both' in the sidebar to view this chart.").use(ppTab);
        }

        JtContainer tgTab = tabs.tab("Token Generation");
        if ("Token Generation".equals(testFilter) || "Both".equals(testFilter)) {
            Jt.subheader("Token Generation Throughput vs Context Depth").use(tgTab);
            Jt.echarts(ChartBuilder.buildTGChart(benchmarks, selectedModels)).height(450).theme(EchartsComponent.Theme.ROMA).use(tgTab);
        } else {
            Jt.text("Select 'Token Generation' or 'Both' in the sidebar to view this chart.").use(tgTab);
        }

        JtContainer compTab = tabs.tab("Comparison");
        Jt.subheader("PP vs TG Throughput Comparison").use(compTab);
        Jt.echarts(ChartBuilder.buildComparisonChart(benchmarks, selectedModels)).height(450).theme(EchartsComponent.Theme.ROMA).use(compTab);

        var sampleExpander = Jt.expander("Sample Details").use();
        buildSampleDetailsTable(benchmarks, selectedModels, testFilter, sampleExpander);

        Jt.pageLink("/report", "Generate Report").use();
    }

    private static void buildSampleDetailsTable(List<BenchmarkEntry> entries, List<String> selectedModels, String testFilter, JtContainer container) {
        List<BenchmarkEntry> filtered = entries.stream()
                .filter(e -> selectedModels.contains(e.getModelType()))
                .filter(e -> {
                    if ("Prompt Processing".equals(testFilter)) return "PP".equals(e.getTestType());
                    if ("Token Generation".equals(testFilter)) return "TG".equals(e.getTestType());
                    return true;
                }).collect(Collectors.toList());

        if (filtered.isEmpty()) { Jt.text("No matching entries for current filters.").use(container); return; }

        Map<String, List<Object>> columns = new LinkedHashMap<>();
        columns.put("Model", new ArrayList<>()); columns.put("Test", new ArrayList<>());
        columns.put("Depth", new ArrayList<>()); columns.put("Avg t/s", new ArrayList<>());
        columns.put("Stddev", new ArrayList<>()); columns.put("Samples (t/s)", new ArrayList<>());
        for (BenchmarkEntry e : filtered) {
            columns.get("Model").add(e.getModelType()); columns.get("Test").add(e.getTestType());
            columns.get("Depth").add(e.getNDepth()); columns.get("Avg t/s").add(String.format("%.2f", e.getAvgTs()));
            columns.get("Stddev").add(String.format("%.4f", e.getStddevTs()));
            if (e.getSamplesTs() != null) {
                columns.get("Samples (t/s)").add(Arrays.stream(e.getSamplesTs()).mapToObj(v -> String.format("%.2f", v)).collect(Collectors.joining(", ")));
            } else {
                columns.get("Samples (t/s)").add("-");
            }
        }
        Jt.tableFromListColumns(columns).use(container);
    }
}
