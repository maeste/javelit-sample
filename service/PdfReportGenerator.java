package service;

import model.BenchmarkEntry;

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

public class PdfReportGenerator {

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
