package pages;

import model.BenchmarkEntry;
import service.PdfReportGenerator;

import io.javelit.core.Jt;

import java.util.ArrayList;
import java.util.List;

public class ReportPage {

    private ReportPage() {}

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
