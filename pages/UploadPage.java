package pages;

import model.BenchmarkEntry;
import service.JsonParser;
import service.ChartBuilder;

import io.javelit.core.Jt;
import io.javelit.core.JtContainer;
import io.javelit.core.JtUploadedFile;
import io.javelit.components.media.FileUploaderComponent;

import java.util.*;
import java.util.stream.Collectors;

public class UploadPage {

    private UploadPage() {}

    @SuppressWarnings("unchecked")
    public static void render() {
        Jt.title("llama-bench Analyzer").use();
        Jt.markdown("Upload and analyze llama-bench benchmark results").use();

        List<BenchmarkEntry> benchmarks = (List<BenchmarkEntry>) Jt.sessionState()
                .computeIfAbsent("benchmarks", k -> new ArrayList<>());
        List<String> fileNames = (List<String>) Jt.sessionState()
                .computeIfAbsent("fileNames", k -> new ArrayList<>());

        List<JtUploadedFile> files = Jt.fileUploader("Upload llama-bench JSON files")
                .type(List.of(".json"))
                .acceptMultipleFiles(FileUploaderComponent.MultipleFiles.TRUE)
                .use();

        if (files != null && !files.isEmpty()) {
            for (JtUploadedFile file : files) {
                if (!fileNames.contains(file.filename())) {
                    try {
                        String content = new String(file.content());
                        List<BenchmarkEntry> entries = JsonParser.parse(content);
                        if (entries.isEmpty()) {
                            Jt.warning("No benchmark entries found in " + file.filename()).use();
                        } else {
                            benchmarks.addAll(entries);
                            fileNames.add(file.filename());
                            Jt.sessionState().put("benchmarks", benchmarks);
                            Jt.sessionState().put("fileNames", fileNames);
                            Jt.success("Loaded " + entries.size() + " entries from " + file.filename()).use();
                        }
                    } catch (Exception e) {
                        Jt.error("Error parsing " + file.filename() + ": " + e.getMessage()).use();
                    }
                }
            }
        }

        if (benchmarks.isEmpty()) {
            Jt.warning("No benchmark data uploaded yet. Please upload one or more llama-bench JSON files.").use();
            return;
        }

        List<String> models = ChartBuilder.getDistinctModels(benchmarks);
        List<String> hardware = ChartBuilder.getDistinctHardware(benchmarks);

        var cols = Jt.columns(3).use();
        Jt.header("Models").use(cols.col(0));
        Jt.text(String.valueOf(models.size())).use(cols.col(0));
        Jt.header("Tests").use(cols.col(1));
        Jt.text(String.valueOf(benchmarks.size())).use(cols.col(1));
        Jt.header("Hardware").use(cols.col(2));
        Jt.text(hardware.get(0)).use(cols.col(2));

        var hwExpander = Jt.expander("Hardware & Build Info").use();
        Set<String> seenHw = new HashSet<>();
        for (BenchmarkEntry e : benchmarks) {
            String key = e.getCpuInfo() + "|" + e.getGpuInfo();
            if (seenHw.add(key)) {
                Jt.markdown("**CPU:** " + e.getCpuInfo()).use(hwExpander);
                Jt.markdown("**GPU:** " + e.getGpuInfo()).use(hwExpander);
                Jt.markdown("**Backend:** " + e.getBackends()).use(hwExpander);
                Jt.markdown("**Build:** " + e.getBuildCommit() + " (#" + e.getBuildNumber() + ")").use(hwExpander);
                Jt.markdown("**Config:** Threads=" + e.getNThreads()
                        + ", GPU Layers=" + e.getNGpuLayers()
                        + ", Flash Attn=" + e.isFlashAttn()
                        + ", KV Cache=" + e.getTypeK() + "/" + e.getTypeV()).use(hwExpander);
            }
        }

        Jt.markdown("**Uploaded files:** " + String.join(", ", fileNames)).use();

        var tabs = Jt.tabs(List.of("Raw Data", "Summary Table")).use();
        buildRawDataTable(benchmarks, tabs.tab("Raw Data"));
        buildSummaryTable(benchmarks, tabs.tab("Summary Table"));

        Jt.pageLink("/explorer", "Go to Explorer").use();
    }

    private static void buildRawDataTable(List<BenchmarkEntry> entries, JtContainer container) {
        Map<String, List<Object>> columns = new LinkedHashMap<>();
        columns.put("Model", new ArrayList<>()); columns.put("Test", new ArrayList<>());
        columns.put("Depth", new ArrayList<>()); columns.put("Avg t/s", new ArrayList<>());
        columns.put("Stddev", new ArrayList<>());
        for (BenchmarkEntry e : entries) {
            columns.get("Model").add(e.getModelType()); columns.get("Test").add(e.getTestType());
            columns.get("Depth").add(e.getNDepth()); columns.get("Avg t/s").add(String.format("%.2f", e.getAvgTs()));
            columns.get("Stddev").add(String.format("%.4f", e.getStddevTs()));
        }
        Jt.tableFromListColumns(columns).use(container);
    }

    private static void buildSummaryTable(List<BenchmarkEntry> entries, JtContainer container) {
        List<Integer> depths = ChartBuilder.getSortedDepths(entries);
        List<String> models = ChartBuilder.getDistinctModels(entries);
        Map<String, List<Object>> columns = new LinkedHashMap<>();
        columns.put("Model", new ArrayList<>()); columns.put("Test", new ArrayList<>());
        for (int depth : depths) columns.put(String.valueOf(depth), new ArrayList<>());
        for (String model : models) {
            for (String testType : new String[]{"PP", "TG"}) {
                Map<Integer, Double> depthMap = entries.stream()
                        .filter(e -> e.getModelType().equals(model) && e.getTestType().equals(testType))
                        .collect(Collectors.toMap(BenchmarkEntry::getNDepth, BenchmarkEntry::getAvgTs, (a, b) -> a));
                if (depthMap.isEmpty()) continue;
                columns.get("Model").add(model); columns.get("Test").add(testType);
                for (int depth : depths) { Double val = depthMap.get(depth); columns.get(String.valueOf(depth)).add(val != null ? String.format("%.1f", val) : "-"); }
            }
        }
        Jt.tableFromListColumns(columns).use(container);
    }
}
