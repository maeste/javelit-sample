///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.javelit:javelit:0.86.0
//DEPS org.icepear.echarts:echarts-java:1.0.7
//DEPS com.google.code.gson:gson:2.11.0
//DEPS org.apache.pdfbox:pdfbox:3.0.4

import io.javelit.core.Jt;
import io.javelit.core.JtContainer;
import io.javelit.core.JtUploadedFile;
import io.javelit.components.media.FileUploaderComponent;
import io.javelit.components.chart.EchartsComponent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.icepear.echarts.Bar;
import org.icepear.echarts.Line;
import org.icepear.echarts.components.coord.cartesian.CategoryAxis;
import org.icepear.echarts.components.coord.cartesian.ValueAxis;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class App {
    public static void main(String[] args) {
        var currentPage = Jt.navigation(
                Jt.page("/upload", UploadPage::render)
                        .title("Upload & Overview")
                        .icon("\uD83D\uDD2C")
                        .home(),
                Jt.page("/explorer", ExplorerPage::render)
                        .title("Performance Explorer")
                        .icon("\uD83D\uDCCA"),
                Jt.page("/report", ReportPage::render)
                        .title("Report Generator")
                        .icon("\uD83D\uDCC4")
        ).use();

        currentPage.run();
    }

    // =========================================================================
    // MODEL
    // =========================================================================

    public static class BenchmarkEntry {
        private String buildCommit;
        private int buildNumber;
        private String cpuInfo;
        private String gpuInfo;
        private String backends;
        private String modelFilename;
        private String modelType;
        private long modelSize;
        private long modelNParams;
        private int nBatch;
        private int nUbatch;
        private int nThreads;
        private int nGpuLayers;
        private boolean flashAttn;
        private String typeK;
        private String typeV;
        private boolean useMmap;
        private int nPrompt;
        private int nGen;
        private int nDepth;
        private String testTime;
        private double avgTs;
        private double stddevTs;
        private long avgNs;
        private long stddevNs;
        private double[] samplesTs;
        private long[] samplesNs;

        public String getTestType() { return nPrompt > 0 ? "PP" : "TG"; }
        public String getTestTypeLabel() { return nPrompt > 0 ? "Prompt Processing" : "Token Generation"; }
        public double getModelSizeGB() { return modelSize / 1_073_741_824.0; }
        public double getModelParamsB() { return modelNParams / 1_000_000_000.0; }

        public String getBuildCommit() { return buildCommit; }
        public int getBuildNumber() { return buildNumber; }
        public String getCpuInfo() { return cpuInfo; }
        public String getGpuInfo() { return gpuInfo; }
        public String getBackends() { return backends; }
        public String getModelFilename() { return modelFilename; }
        public String getModelType() { return modelType; }
        public long getModelSize() { return modelSize; }
        public long getModelNParams() { return modelNParams; }
        public int getNBatch() { return nBatch; }
        public int getNUbatch() { return nUbatch; }
        public int getNThreads() { return nThreads; }
        public int getNGpuLayers() { return nGpuLayers; }
        public boolean isFlashAttn() { return flashAttn; }
        public String getTypeK() { return typeK; }
        public String getTypeV() { return typeV; }
        public boolean isUseMmap() { return useMmap; }
        public int getNPrompt() { return nPrompt; }
        public int getNGen() { return nGen; }
        public int getNDepth() { return nDepth; }
        public String getTestTime() { return testTime; }
        public double getAvgTs() { return avgTs; }
        public double getStddevTs() { return stddevTs; }
        public long getAvgNs() { return avgNs; }
        public long getStddevNs() { return stddevNs; }
        public double[] getSamplesTs() { return samplesTs; }
        public long[] getSamplesNs() { return samplesNs; }

        public void setBuildCommit(String v) { this.buildCommit = v; }
        public void setBuildNumber(int v) { this.buildNumber = v; }
        public void setCpuInfo(String v) { this.cpuInfo = v; }
        public void setGpuInfo(String v) { this.gpuInfo = v; }
        public void setBackends(String v) { this.backends = v; }
        public void setModelFilename(String v) { this.modelFilename = v; }
        public void setModelType(String v) { this.modelType = v; }
        public void setModelSize(long v) { this.modelSize = v; }
        public void setModelNParams(long v) { this.modelNParams = v; }
        public void setNBatch(int v) { this.nBatch = v; }
        public void setNUbatch(int v) { this.nUbatch = v; }
        public void setNThreads(int v) { this.nThreads = v; }
        public void setNGpuLayers(int v) { this.nGpuLayers = v; }
        public void setFlashAttn(boolean v) { this.flashAttn = v; }
        public void setTypeK(String v) { this.typeK = v; }
        public void setTypeV(String v) { this.typeV = v; }
        public void setUseMmap(boolean v) { this.useMmap = v; }
        public void setNPrompt(int v) { this.nPrompt = v; }
        public void setNGen(int v) { this.nGen = v; }
        public void setNDepth(int v) { this.nDepth = v; }
        public void setTestTime(String v) { this.testTime = v; }
        public void setAvgTs(double v) { this.avgTs = v; }
        public void setStddevTs(double v) { this.stddevTs = v; }
        public void setAvgNs(long v) { this.avgNs = v; }
        public void setStddevNs(long v) { this.stddevNs = v; }
        public void setSamplesTs(double[] v) { this.samplesTs = v; }
        public void setSamplesNs(long[] v) { this.samplesNs = v; }
    }

    // =========================================================================
    // SERVICE: JSON PARSER
    // =========================================================================

    public static class JsonParser {

        public static List<BenchmarkEntry> parse(String content) {
            if (content == null || content.isBlank()) {
                return new ArrayList<>();
            }
            String json = extractJsonArray(content);
            if (json == null) {
                throw new RuntimeException("No JSON array found in file content");
            }
            JsonArray array = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
            List<BenchmarkEntry> entries = new ArrayList<>();
            for (JsonElement element : array) {
                entries.add(mapEntry(element.getAsJsonObject()));
            }
            return entries;
        }

        private static BenchmarkEntry mapEntry(JsonObject o) {
            BenchmarkEntry e = new BenchmarkEntry();
            e.setBuildCommit(getString(o, "build_commit"));
            e.setBuildNumber(getInt(o, "build_number"));
            e.setCpuInfo(getString(o, "cpu_info"));
            e.setGpuInfo(getString(o, "gpu_info"));
            e.setBackends(getString(o, "backends"));
            e.setModelFilename(getString(o, "model_filename"));
            e.setModelType(getString(o, "model_type"));
            e.setModelSize(getLong(o, "model_size"));
            e.setModelNParams(getLong(o, "model_n_params"));
            e.setNBatch(getInt(o, "n_batch"));
            e.setNUbatch(getInt(o, "n_ubatch"));
            e.setNThreads(getInt(o, "n_threads"));
            e.setNGpuLayers(getInt(o, "n_gpu_layers"));
            e.setFlashAttn(getBoolean(o, "flash_attn"));
            e.setTypeK(getString(o, "type_k"));
            e.setTypeV(getString(o, "type_v"));
            e.setUseMmap(getBoolean(o, "use_mmap"));
            e.setNPrompt(getInt(o, "n_prompt"));
            e.setNGen(getInt(o, "n_gen"));
            e.setNDepth(getInt(o, "n_depth"));
            e.setTestTime(getString(o, "test_time"));
            e.setAvgTs(getDouble(o, "avg_ts"));
            e.setStddevTs(getDouble(o, "stddev_ts"));
            e.setAvgNs(getLong(o, "avg_ns"));
            e.setStddevNs(getLong(o, "stddev_ns"));
            e.setSamplesTs(getDoubleArray(o, "samples_ts"));
            e.setSamplesNs(getLongArray(o, "samples_ns"));
            return e;
        }

        private static String getString(JsonObject o, String key) {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
        }
        private static int getInt(JsonObject o, String key) {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : 0;
        }
        private static long getLong(JsonObject o, String key) {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : 0L;
        }
        private static double getDouble(JsonObject o, String key) {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : 0.0;
        }
        private static boolean getBoolean(JsonObject o, String key) {
            return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
        }
        private static double[] getDoubleArray(JsonObject o, String key) {
            if (!o.has(key) || !o.get(key).isJsonArray()) return new double[0];
            JsonArray arr = o.getAsJsonArray(key);
            double[] result = new double[arr.size()];
            for (int i = 0; i < arr.size(); i++) result[i] = arr.get(i).getAsDouble();
            return result;
        }
        private static long[] getLongArray(JsonObject o, String key) {
            if (!o.has(key) || !o.get(key).isJsonArray()) return new long[0];
            JsonArray arr = o.getAsJsonArray(key);
            long[] result = new long[arr.size()];
            for (int i = 0; i < arr.size(); i++) result[i] = arr.get(i).getAsLong();
            return result;
        }
        private static String extractJsonArray(String content) {
            int bracketIndex = content.indexOf('[');
            return bracketIndex >= 0 ? content.substring(bracketIndex) : null;
        }
    }

    // =========================================================================
    // SERVICE: CHART BUILDER
    // =========================================================================

    public static class ChartBuilder {

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

    // =========================================================================
    // SERVICE: PDF REPORT GENERATOR
    // =========================================================================

    public static class PdfReportGenerator {

        private static final float MARGIN = 50;
        private static final float LINE_HEIGHT = 14;
        private static final float HEADER_HEIGHT = 20;
        private static final float TABLE_CELL_HEIGHT = 16;

        private PDType1Font fontRegular;
        private PDType1Font fontBold;
        private PDType1Font fontItalic;

        public PdfReportGenerator() {
            fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            fontItalic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        }

        public byte[] generate(List<BenchmarkEntry> entries, String reportTitle,
                boolean includeHardware, boolean includeModelOverview,
                boolean includePPAnalysis, boolean includeTGAnalysis,
                boolean includeComparison, boolean includeStatistics, String notes) throws IOException {
            try (PDDocument document = new PDDocument()) {
                float pageWidth = PDRectangle.A4.getWidth();
                float contentWidth = pageWidth - 2 * MARGIN;
                float[] yRef = {PDRectangle.A4.getHeight() - MARGIN};
                PDPage[] currentPage = {newPage(document)};
                PDPageContentStream[] cs = {new PDPageContentStream(document, currentPage[0])};

                drawText(cs[0], fontBold, 18, MARGIN, yRef[0], reportTitle);
                yRef[0] -= HEADER_HEIGHT;
                String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                drawText(cs[0], fontItalic, 9, MARGIN, yRef[0], "Generated: " + dateStr + "  |  Powered by Javelit");
                yRef[0] -= LINE_HEIGHT * 2;

                List<String> models = ChartBuilder.getDistinctModels(entries);
                List<Integer> depths = ChartBuilder.getSortedDepths(entries);

                if (includeHardware) {
                    yRef[0] = checkPageBreak(document, cs, currentPage, yRef[0], 100);
                    drawText(cs[0], fontBold, 13, MARGIN, yRef[0], "Hardware & Build Summary");
                    yRef[0] -= HEADER_HEIGHT;
                    Set<String> seen = new HashSet<>();
                    for (BenchmarkEntry e : entries) {
                        String key = e.getCpuInfo() + e.getGpuInfo();
                        if (seen.add(key)) {
                            drawText(cs[0], fontRegular, 10, MARGIN, yRef[0], "CPU: " + e.getCpuInfo());
                            yRef[0] -= LINE_HEIGHT;
                            drawText(cs[0], fontRegular, 10, MARGIN, yRef[0], "GPU: " + e.getGpuInfo() + "  |  Backend: " + e.getBackends());
                            yRef[0] -= LINE_HEIGHT;
                            drawText(cs[0], fontRegular, 10, MARGIN, yRef[0], "Build: " + e.getBuildCommit() + " (#" + e.getBuildNumber() + ")");
                            yRef[0] -= LINE_HEIGHT;
                            drawText(cs[0], fontRegular, 10, MARGIN, yRef[0],
                                    "Threads: " + e.getNThreads() + "  |  GPU Layers: " + e.getNGpuLayers()
                                            + "  |  Flash Attn: " + e.isFlashAttn()
                                            + "  |  KV: " + e.getTypeK() + "/" + e.getTypeV());
                            yRef[0] -= LINE_HEIGHT * 2;
                        }
                    }
                }

                if (includeModelOverview) {
                    yRef[0] = checkPageBreak(document, cs, currentPage, yRef[0], 60 + models.size() * TABLE_CELL_HEIGHT);
                    drawText(cs[0], fontBold, 13, MARGIN, yRef[0], "Model Overview");
                    yRef[0] -= HEADER_HEIGHT;
                    String[] headers = {"Model", "Size (GB)", "Params (B)"};
                    float[] colWidths = {contentWidth * 0.6f, contentWidth * 0.2f, contentWidth * 0.2f};
                    yRef[0] = drawTableRow(cs[0], fontBold, 9, MARGIN, yRef[0], headers, colWidths);
                    for (String model : models) {
                        BenchmarkEntry sample = entries.stream().filter(e -> e.getModelType().equals(model)).findFirst().orElse(null);
                        if (sample != null) {
                            String[] row = {model, String.format("%.1f", sample.getModelSizeGB()), String.format("%.1f", sample.getModelParamsB())};
                            yRef[0] = checkPageBreak(document, cs, currentPage, yRef[0], TABLE_CELL_HEIGHT);
                            yRef[0] = drawTableRow(cs[0], fontRegular, 9, MARGIN, yRef[0], row, colWidths);
                        }
                    }
                    yRef[0] -= LINE_HEIGHT;
                }

                if (includePPAnalysis) {
                    yRef[0] = drawThroughputTable(document, cs, currentPage, yRef, entries, models, depths,
                            "Prompt Processing Analysis (avg tokens/s)", "PP", contentWidth);
                }
                if (includeTGAnalysis) {
                    yRef[0] = drawThroughputTable(document, cs, currentPage, yRef, entries, models, depths,
                            "Token Generation Analysis (avg tokens/s)", "TG", contentWidth);
                }

                if (includeComparison) {
                    yRef[0] = checkPageBreak(document, cs, currentPage, yRef[0], 80 + models.size() * TABLE_CELL_HEIGHT * 2);
                    drawText(cs[0], fontBold, 13, MARGIN, yRef[0], "Performance Comparison");
                    yRef[0] -= HEADER_HEIGHT;
                    for (String model : models) {
                        yRef[0] = checkPageBreak(document, cs, currentPage, yRef[0], TABLE_CELL_HEIGHT * 4);
                        drawText(cs[0], fontBold, 10, MARGIN, yRef[0], truncate(model, 80));
                        yRef[0] -= LINE_HEIGHT;
                        OptionalDouble bestPP = entries.stream().filter(e -> e.getModelType().equals(model) && e.getTestType().equals("PP")).mapToDouble(BenchmarkEntry::getAvgTs).max();
                        OptionalDouble worstPP = entries.stream().filter(e -> e.getModelType().equals(model) && e.getTestType().equals("PP")).mapToDouble(BenchmarkEntry::getAvgTs).min();
                        OptionalDouble bestTG = entries.stream().filter(e -> e.getModelType().equals(model) && e.getTestType().equals("TG")).mapToDouble(BenchmarkEntry::getAvgTs).max();
                        OptionalDouble worstTG = entries.stream().filter(e -> e.getModelType().equals(model) && e.getTestType().equals("TG")).mapToDouble(BenchmarkEntry::getAvgTs).min();
                        if (bestPP.isPresent()) {
                            double degradation = (1.0 - worstPP.getAsDouble() / bestPP.getAsDouble()) * 100;
                            drawText(cs[0], fontRegular, 9, MARGIN + 10, yRef[0],
                                    String.format("PP: Best %.1f t/s -> Worst %.1f t/s (%.0f%% degradation)", bestPP.getAsDouble(), worstPP.getAsDouble(), degradation));
                            yRef[0] -= LINE_HEIGHT;
                        }
                        if (bestTG.isPresent()) {
                            double degradation = (1.0 - worstTG.getAsDouble() / bestTG.getAsDouble()) * 100;
                            drawText(cs[0], fontRegular, 9, MARGIN + 10, yRef[0],
                                    String.format("TG: Best %.1f t/s -> Worst %.1f t/s (%.0f%% degradation)", bestTG.getAsDouble(), worstTG.getAsDouble(), degradation));
                            yRef[0] -= LINE_HEIGHT;
                        }
                        yRef[0] -= LINE_HEIGHT / 2;
                    }
                }

                if (includeStatistics) {
                    yRef[0] = checkPageBreak(document, cs, currentPage, yRef[0], 60);
                    drawText(cs[0], fontBold, 13, MARGIN, yRef[0], "Statistical Details");
                    yRef[0] -= HEADER_HEIGHT;
                    String[] statHeaders = {"Model", "Test", "Depth", "Avg t/s", "Stddev", "Samples"};
                    float colW = contentWidth / 6;
                    float[] statColWidths = {colW * 1.5f, colW * 0.6f, colW * 0.6f, colW * 0.8f, colW * 0.7f, colW * 0.8f};
                    yRef[0] = drawTableRow(cs[0], fontBold, 8, MARGIN, yRef[0], statHeaders, statColWidths);
                    for (BenchmarkEntry e : entries) {
                        yRef[0] = checkPageBreak(document, cs, currentPage, yRef[0], TABLE_CELL_HEIGHT);
                        String[] row = {truncate(e.getModelType(), 35), e.getTestType(), String.valueOf(e.getNDepth()),
                                String.format("%.2f", e.getAvgTs()), String.format("%.4f", e.getStddevTs()),
                                String.valueOf(e.getSamplesTs() != null ? e.getSamplesTs().length : 0)};
                        yRef[0] = drawTableRow(cs[0], fontRegular, 8, MARGIN, yRef[0], row, statColWidths);
                    }
                    yRef[0] -= LINE_HEIGHT;
                }

                if (notes != null && !notes.isBlank()) {
                    yRef[0] = checkPageBreak(document, cs, currentPage, yRef[0], 60);
                    drawText(cs[0], fontBold, 13, MARGIN, yRef[0], "Notes");
                    yRef[0] -= HEADER_HEIGHT;
                    for (String line : notes.split("\n")) {
                        yRef[0] = checkPageBreak(document, cs, currentPage, yRef[0], LINE_HEIGHT);
                        drawText(cs[0], fontRegular, 10, MARGIN, yRef[0], line);
                        yRef[0] -= LINE_HEIGHT;
                    }
                }

                cs[0].close();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                document.save(baos);
                return baos.toByteArray();
            }
        }

        private float drawThroughputTable(PDDocument document, PDPageContentStream[] cs,
                PDPage[] currentPage, float[] yRef, List<BenchmarkEntry> entries,
                List<String> models, List<Integer> depths, String title, String testType, float contentWidth) throws IOException {
            int numCols = 1 + depths.size();
            float modelColWidth = contentWidth * 0.35f;
            float depthColWidth = (contentWidth - modelColWidth) / depths.size();
            yRef[0] = checkPageBreak(document, cs, currentPage, yRef[0], 60 + models.size() * TABLE_CELL_HEIGHT);
            drawText(cs[0], fontBold, 13, MARGIN, yRef[0], title);
            yRef[0] -= HEADER_HEIGHT;
            String[] headers = new String[numCols];
            float[] colWidths = new float[numCols];
            headers[0] = "Model"; colWidths[0] = modelColWidth;
            for (int i = 0; i < depths.size(); i++) { headers[i+1] = String.valueOf(depths.get(i)); colWidths[i+1] = depthColWidth; }
            yRef[0] = drawTableRow(cs[0], fontBold, 8, MARGIN, yRef[0], headers, colWidths);
            for (String model : models) {
                yRef[0] = checkPageBreak(document, cs, currentPage, yRef[0], TABLE_CELL_HEIGHT);
                Map<Integer, Double> depthMap = entries.stream()
                        .filter(e -> e.getModelType().equals(model) && e.getTestType().equals(testType))
                        .collect(Collectors.toMap(BenchmarkEntry::getNDepth, BenchmarkEntry::getAvgTs, (a, b) -> a));
                String[] row = new String[numCols]; row[0] = truncate(model, 40);
                for (int i = 0; i < depths.size(); i++) { Double val = depthMap.get(depths.get(i)); row[i+1] = val != null ? String.format("%.1f", val) : "-"; }
                yRef[0] = drawTableRow(cs[0], fontRegular, 8, MARGIN, yRef[0], row, colWidths);
            }
            yRef[0] -= LINE_HEIGHT;
            return yRef[0];
        }

        private PDPage newPage(PDDocument document) { PDPage page = new PDPage(PDRectangle.A4); document.addPage(page); return page; }

        private float checkPageBreak(PDDocument document, PDPageContentStream[] cs, PDPage[] currentPage, float y, float needed) throws IOException {
            if (y - needed < MARGIN) { cs[0].close(); currentPage[0] = newPage(document); cs[0] = new PDPageContentStream(document, currentPage[0]); return PDRectangle.A4.getHeight() - MARGIN; }
            return y;
        }

        private void drawText(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text) throws IOException {
            if (text == null) text = "";
            cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y); cs.showText(text); cs.endText();
        }

        private float drawTableRow(PDPageContentStream cs, PDType1Font font, float size, float startX, float y, String[] cells, float[] colWidths) throws IOException {
            float x = startX;
            for (int i = 0; i < cells.length; i++) { drawText(cs, font, size, x, y, cells[i] != null ? cells[i] : ""); x += colWidths[i]; }
            return y - TABLE_CELL_HEIGHT;
        }

        private String truncate(String text, int maxLen) { if (text == null) return ""; return text.length() > maxLen ? text.substring(0, maxLen - 2) + ".." : text; }
    }

    // =========================================================================
    // PAGE: UPLOAD
    // =========================================================================

    public static class UploadPage {

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

    // =========================================================================
    // PAGE: EXPLORER
    // =========================================================================

    public static class ExplorerPage {

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

    // =========================================================================
    // PAGE: REPORT
    // =========================================================================

    public static class ReportPage {

        @SuppressWarnings("unchecked")
        public static void render() {
            Jt.title("Report Generator").use();

            List<BenchmarkEntry> benchmarks = (List<BenchmarkEntry>) Jt.sessionState()
                    .getOrDefault("benchmarks", new ArrayList<>());

            if (benchmarks.isEmpty()) {
                Jt.warning("No benchmark data available. Please upload data on the Upload page first.").use();
                Jt.pageLink("/upload", "Go to Upload").use();
                return;
            }

            var form = Jt.form().border(true).use();
            var reportTitle = Jt.textInput("Report Title").value("llama-bench Performance Report").use(form);
            Jt.subheader("Sections to include:").use(form);
            boolean includeHardware = Jt.checkbox("Hardware & Build Summary").value(true).use(form);
            boolean includeModels = Jt.checkbox("Model Overview").value(true).use(form);
            boolean includePP = Jt.checkbox("Prompt Processing Analysis").value(true).use(form);
            boolean includeTG = Jt.checkbox("Token Generation Analysis").value(true).use(form);
            boolean includeComparison = Jt.checkbox("Performance Comparison Table").value(true).use(form);
            boolean includeStats = Jt.checkbox("Statistical Details").value(true).use(form);
            var notes = Jt.textArea("Notes").placeholder("Add custom notes to include in the report...").use(form);

            if (Jt.formSubmitButton("Generate PDF").use(form)) {
                try {
                    PdfReportGenerator generator = new PdfReportGenerator();
                    byte[] pdfBytes = generator.generate(benchmarks,
                            reportTitle != null ? reportTitle : "llama-bench Performance Report",
                            includeHardware, includeModels, includePP, includeTG, includeComparison, includeStats, notes);
                    Jt.success("Report generated successfully!").use();
                    Jt.pdf(pdfBytes).use();
                } catch (Exception e) {
                    Jt.error("Failed to generate PDF: " + e.getMessage()).use();
                }
            }
        }
    }
}
