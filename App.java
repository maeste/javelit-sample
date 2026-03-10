///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.javelit:javelit:0.86.0
//DEPS org.icepear.echarts:echarts-java:1.0.7
//DEPS com.google.code.gson:gson:2.11.0
//DEPS org.apache.pdfbox:pdfbox:3.0.4

//SOURCES model/BenchmarkEntry.java
//SOURCES service/JsonParser.java
//SOURCES service/ChartBuilder.java
//SOURCES service/PdfReportGenerator.java
//SOURCES pages/UploadPage.java
//SOURCES pages/ExplorerPage.java
//SOURCES pages/ReportPage.java

import io.javelit.core.Jt;

import pages.UploadPage;
import pages.ExplorerPage;
import pages.ReportPage;

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
}
