# PRD: llama-bench Performance Analyzer

## 1. Product Overview

**Name**: llama-bench Analyzer
**Framework**: Javelit 0.86.0 (Java, JDK 21+)
**Runtime**: JBang — single command: `javelit run App.java`
**Purpose**: Interactive web app to upload, explore, and report on llama-bench benchmark results.
**UI Language**: English

### Problem Statement

`llama-bench` (from llama.cpp) produces JSON output with detailed performance metrics for LLM
inference on local hardware. Interpreting raw JSON across multiple benchmark runs, models, and
context depths is tedious. There is no visual tool to quickly compare throughput degradation
across context sizes or across different model quantizations.

### Solution

A 3-page Javelit app that:
1. Accepts one or more llama-bench JSON files via upload
2. Presents interactive charts for exploring prompt processing and token generation performance
3. Generates a downloadable PDF report summarizing the analysis

---

## 2. Input Data Format

Source: `llama-bench` JSON output (array of test entries). Each entry contains:

### Hardware & Build Context
| Field | Type | Example |
|-------|------|---------|
| `build_commit` | string | `"ff4affb4c"` |
| `build_number` | int | `8067` |
| `cpu_info` | string | `"AMD RYZEN AI MAX+ 395 w/ Radeon 8060S"` |
| `gpu_info` | string | `"Radeon 8060S Graphics"` |
| `backends` | string | `"ROCm"` |

### Model Info
| Field | Type | Example |
|-------|------|---------|
| `model_filename` | string | path to .gguf file |
| `model_type` | string | `"Step 3.5 Flash 199B.A11B Q4_K_S imatrix"` |
| `model_size` | long | `111493808640` (bytes) |
| `model_n_params` | long | `196956130432` |

### Test Configuration
| Field | Type | Description |
|-------|------|-------------|
| `n_batch` | int | Batch size |
| `n_ubatch` | int | Micro-batch size |
| `n_threads` | int | CPU threads |
| `n_gpu_layers` | int | Layers offloaded to GPU |
| `flash_attn` | boolean | Flash attention enabled |
| `type_k` / `type_v` | string | KV cache precision (`"f16"`) |
| `use_mmap` | boolean | Memory-mapped model loading |

### Test Parameters (define test type)
| Field | Type | Meaning |
|-------|------|---------|
| `n_prompt` | int | Tokens for prompt processing (>0 = prefill test) |
| `n_gen` | int | Tokens for generation (>0 = decode test) |
| `n_depth` | int | KV cache context depth (0, 8192, 16384, ..., 131072) |

**Test type logic**:
- `n_prompt > 0 && n_gen == 0` → **Prompt Processing** (prefill) benchmark
- `n_prompt == 0 && n_gen > 0` → **Token Generation** (decode) benchmark

### Results
| Field | Type | Description |
|-------|------|-------------|
| `test_time` | ISO 8601 | When the test ran |
| `avg_ts` | double | **Average tokens/second** (primary metric) |
| `stddev_ts` | double | Standard deviation of tokens/s |
| `avg_ns` | long | Average time in nanoseconds |
| `stddev_ns` | long | Standard deviation in nanoseconds |
| `samples_ts` | double[] | Individual sample tokens/s values |
| `samples_ns` | long[] | Individual sample times in nanoseconds |

### Key Observation
Performance degrades as `n_depth` increases. A typical benchmark file contains a matrix of
tests varying `n_depth` x test type for a single model+hardware combination. Multiple files
enable cross-model or cross-hardware comparison.

---

## 3. Application Architecture

### 3.1 Runtime & Dependencies

```
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
```

- **Javelit**: UI framework (runs on top of JBang)
- **echarts-java**: charting (via `Jt.echarts`)
- **Gson**: JSON parsing
- **Apache PDFBox**: PDF generation (simplest option, no commercial license needed)

### 3.2 Multi-File Classloading (CRITICAL)

Javelit uses JBang under the hood. When organizing code into packages (`model/`, `service/`,
`pages/`), there are **two separate compilation/classloading systems** at play, and both must
be satisfied:

#### System 1: JBang compilation (`//SOURCES`)

JBang only compiles files explicitly listed via `//SOURCES` directives in the main `App.java`.
Without these, JBang will compile `App.java` alone and fail on any `import model.BenchmarkEntry`
etc. Add one `//SOURCES` line per source file, using relative paths from `App.java`:

```java
//SOURCES model/BenchmarkEntry.java
//SOURCES service/JsonParser.java
```

#### System 2: Javelit's own compiler and runtime classpath (`-cp`)

Javelit has its **own** Java compiler that discovers and compiles `.java` files in subdirectories
to `target/javelit/classes/` (with correct package directory structure). However, Javelit's
runtime classloader does **NOT** automatically include `target/javelit/classes/` on its classpath.

This causes `NoClassDefFoundError` at runtime even though the `.class` files exist:
```
java.lang.NoClassDefFoundError: model/BenchmarkEntry
    at service.JsonParser.mapEntry(JsonParser.java:33)
```

**Fix**: Pass `-cp target/javelit/classes/` when running the app:
```bash
javelit run App.java -cp target/javelit/classes/
```

#### System 3: External library JARs for source files

Javelit's compiler resolves `//DEPS` declared in `App.java` for `App.java` itself, but those
dependencies are **NOT** available to source files in subdirectories during Javelit's compilation.
For example, `service/PdfReportGenerator.java` imports `org.apache.pdfbox.pdmodel.*`, but
Javelit's compiler cannot find these classes.

Adding `//DEPS` to the source file itself does NOT help — Javelit ignores `//DEPS` in non-main
files.

**Fix**: Place the required JAR files in a `lib/` directory and pass them via `-cp`:
```bash
javelit run App.java -cp "target/javelit/classes/:lib/pdfbox-3.0.4.jar:lib/fontbox-3.0.4.jar:lib/pdfbox-io-3.0.4.jar:lib/commons-logging-1.3.4.jar"
```

Note: `//DEPS` in `App.java` are still needed for JBang's compilation (System 1). The `lib/`
JARs are needed for Javelit's own compiler (System 2). Both must be present.

#### Summary of classloading requirements

| What | Why | How |
|------|-----|-----|
| `//SOURCES` in App.java | JBang needs explicit source file list | One line per .java file |
| `-cp target/javelit/classes/` | Javelit's classloader needs packaged classes | Pass via `-cp` flag |
| `lib/*.jar` on `-cp` | Javelit's compiler needs external deps for source files | Download JARs to `lib/`, pass via `-cp` |
| `//DEPS` in App.java | JBang needs Maven dependency resolution | Standard JBang syntax |

#### Common errors and their causes

| Error | Cause | Fix |
|-------|-------|-----|
| `NoClassDefFoundError: model/BenchmarkEntry` | `target/javelit/classes/` not on runtime classpath | Add `-cp target/javelit/classes/` |
| `package org.apache.pdfbox.pdmodel does not exist` | External JAR not available to Javelit's compiler | Add JAR to `lib/` and `-cp` |
| `IllegalStateException: No active execution context` | Running with `jbang run` instead of `javelit run` | Use `javelit run` or `java -jar javelit.jar run` |
| Stale classes from monolithic version | Old `App$InnerClass.class` files in `target/` | Run `rm -rf target/javelit/classes/` |

### 3.3 Page Structure

```
App.java (entry point with Jt.navigation)
├── Page 1: Upload & Overview    (/upload)     — HOME
├── Page 2: Performance Explorer (/explorer)
└── Page 3: Report Generator     (/report)
```

### 3.3 State Management

Parsed benchmark data is stored in `Jt.sessionState()` after upload, shared across all pages:

- `"benchmarks"` → `List<BenchmarkEntry>` — all parsed entries from all uploaded files
- `"fileNames"` → `List<String>` — names of uploaded files (for display/report)

---

## 4. Page Specifications

### 4.1 Page 1: Upload & Overview (HOME)

**Route**: `/upload`
**Purpose**: Upload JSON files, preview raw data, show summary statistics.

#### Layout

```
┌─────────────────────────────────────────────────┐
│  🔬 llama-bench Analyzer                        │
│  Upload and analyze llama-bench benchmark results│
├─────────────────────────────────────────────────┤
│  [File Uploader: .json, multiple files]         │
├─────────────────────────────────────────────────┤
│  ┌─ Summary Cards (3 columns) ───────────────┐  │
│  │ Models: N  │ Tests: N   │ Hardware: X     │  │
│  └───────────────────────────────────────────┘  │
├─────────────────────────────────────────────────┤
│  ┌─ Expander: Hardware & Build Info ─────────┐  │
│  │  CPU: ...  GPU: ...  Backend: ...         │  │
│  │  Build: commit / number                   │  │
│  └───────────────────────────────────────────┘  │
├─────────────────────────────────────────────────┤
│  ┌─ Tabs ────────────────────────────────────┐  │
│  │ [Raw Data] [Summary Table]                │  │
│  │                                           │  │
│  │ Raw Data: full table of all entries       │  │
│  │ Summary: grouped by model + test type     │  │
│  └───────────────────────────────────────────┘  │
├─────────────────────────────────────────────────┤
│  [▶ Go to Explorer]                             │
└─────────────────────────────────────────────────┘
```

#### Components

| Component | Javelit API | Details |
|-----------|-------------|---------|
| Title | `Jt.title()` | "llama-bench Analyzer" |
| Subtitle | `Jt.markdown()` | Description text |
| File uploader | `Jt.fileUploader().type([".json"]).acceptMultipleFiles(TRUE)` | Multiple JSON files |
| Summary columns | `Jt.columns(3)` | Distinct models count, total tests count, hardware configs |
| Hardware info | `Jt.expander()` | CPU, GPU, backend, build info per file |
| Data tabs | `Jt.tabs(["Raw Data", "Summary Table"])` | Raw = all entries; Summary = aggregated |
| Raw data table | `Jt.table()` | Columns: model_type, test_type (PP/TG), n_depth, avg_ts, stddev_ts |
| Summary table | `Jt.table()` | Grouped: model_type, test_type, depths as columns, avg_ts values |
| Navigate button | `Jt.pageLink("/explorer")` | Go to Explorer page |

#### Behavior

1. On file upload: parse JSON with Gson, extract entries, store in `sessionState`
2. Handle malformed JSON gracefully with `Jt.error()`
3. Support uploading additional files (append to existing data)
4. Show `Jt.warning()` if no data uploaded yet on summary/table sections

---

### 4.2 Page 2: Performance Explorer

**Route**: `/explorer`
**Purpose**: Interactive charts to explore and compare benchmark performance.

#### Layout

```
┌─────────────────────────────────────────────────┐
│  📊 Performance Explorer                        │
├───────────┬─────────────────────────────────────┤
│  SIDEBAR  │                                     │
│           │  ┌─ Tabs ────────────────────────┐  │
│  Models:  │  │ [Prompt Processing] [Token    │  │
│  ☑ Model1 │  │  Generation] [Comparison]     │  │
│  ☑ Model2 │  │                               │  │
│           │  │ Chart area (ECharts)           │  │
│  Test:    │  │                               │  │
│  ○ PP     │  │                               │  │
│  ○ TG     │  │                               │  │
│  ○ Both   │  │                               │  │
│           │  └───────────────────────────────┘  │
│           │                                     │
│           │  ┌─ Expander: Sample Details ────┐  │
│           │  │ Individual sample values table │  │
│           │  └───────────────────────────────┘  │
├───────────┴─────────────────────────────────────┤
│  [▶ Generate Report]                            │
└─────────────────────────────────────────────────┘
```

#### Sidebar Filters

| Filter | Component | Purpose |
|--------|-----------|---------|
| Model selection | `Jt.selectbox()` (multi-model via checkboxes) | Choose which models to display |
| Test type | `Jt.radio("Test Type", ["Prompt Processing", "Token Generation", "Both"])` | Filter by PP or TG |

Note: use multiple `Jt.checkbox()` for model selection (one per distinct `model_type`) since
`Jt.selectbox` is single-select.

#### Tab: Prompt Processing

**Chart**: Line chart — X axis: `n_depth` (context depth), Y axis: `avg_ts` (tokens/s)
- One line per selected model
- Error bars or shaded area showing `stddev_ts`
- Title: "Prompt Processing Throughput vs Context Depth"
- Tooltip showing exact values on hover

**Key insight this reveals**: How prefill speed degrades as KV cache grows.

#### Tab: Token Generation

**Chart**: Line chart — X axis: `n_depth`, Y axis: `avg_ts`
- One line per selected model
- Same error representation as PP tab
- Title: "Token Generation Throughput vs Context Depth"

**Key insight**: How decode speed degrades with context length.

#### Tab: Comparison

**Chart**: Grouped bar chart — X axis: `n_depth`, grouped bars per model
- Two series per model: PP throughput and TG throughput side by side
- Title: "PP vs TG Throughput Comparison"

**Alternative if single model**: Overlay PP and TG as two lines on same chart.

#### Expander: Sample Details

Table showing individual `samples_ts` values for the currently selected model(s) and test type,
to allow inspection of variance across benchmark runs.

#### Components

| Component | Javelit API | Details |
|-----------|-------------|---------|
| Title | `Jt.title()` | "Performance Explorer" |
| Model checkboxes | `Jt.checkbox()` in SIDEBAR | One per model_type |
| Test type radio | `Jt.radio()` in SIDEBAR | PP / TG / Both |
| Main tabs | `Jt.tabs(["Prompt Processing", "Token Generation", "Comparison"])` | 3 analysis views |
| PP chart | `Jt.echarts(Line)` | Line chart with error |
| TG chart | `Jt.echarts(Line)` | Line chart with error |
| Comparison chart | `Jt.echarts(Bar)` | Grouped bar chart |
| Sample expander | `Jt.expander()` + `Jt.table()` | Raw samples table |
| Navigate | `Jt.pageLink("/report")` | Go to Report page |

---

### 4.3 Page 3: Report Generator

**Route**: `/report`
**Purpose**: Configure and generate a PDF report summarizing the benchmark analysis.

#### Layout

```
┌─────────────────────────────────────────────────┐
│  📄 Report Generator                            │
├─────────────────────────────────────────────────┤
│  ┌─ Form ────────────────────────────────────┐  │
│  │ Report Title: [________________]          │  │
│  │                                           │  │
│  │ Sections to include:                      │  │
│  │ ☑ Hardware & Build Summary                │  │
│  │ ☑ Model Overview                          │  │
│  │ ☑ Prompt Processing Analysis              │  │
│  │ ☑ Token Generation Analysis               │  │
│  │ ☑ Performance Comparison Table            │  │
│  │ ☑ Statistical Details                     │  │
│  │                                           │  │
│  │ Notes: [                              ]   │  │
│  │        [           textarea            ]  │  │
│  │                                           │  │
│  │ [Generate PDF]                            │  │
│  └───────────────────────────────────────────┘  │
├─────────────────────────────────────────────────┤
│  ┌─ PDF Preview ─────────────────────────────┐  │
│  │  Jt.pdf(generatedBytes)                   │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

#### Form Fields

| Field | Component | Default |
|-------|-----------|---------|
| Report title | `Jt.textInput()` | "llama-bench Performance Report" |
| Sections | 6x `Jt.checkbox()` | All checked |
| Custom notes | `Jt.textArea()` | Empty |
| Submit | `Jt.formSubmitButton("Generate PDF")` | — |

#### PDF Content (generated with Apache PDFBox)

The PDF is a **text-based report** (no embedded chart images) with tables:

1. **Header**: Title, generation date, Javelit watermark
2. **Hardware & Build Summary**: CPU, GPU, backend, build info
3. **Model Overview**: Table of model names, sizes, param counts
4. **Prompt Processing Analysis**: Table of avg_ts per model per n_depth
5. **Token Generation Analysis**: Table of avg_ts per model per n_depth
6. **Performance Comparison**: Combined view, best/worst indicators
7. **Statistical Details**: stddev values, sample counts
8. **Notes**: User-provided custom text

#### Components

| Component | Javelit API | Details |
|-----------|-------------|---------|
| Title | `Jt.title()` | "Report Generator" |
| Form | `Jt.form()` | Wraps all inputs |
| Title input | `Jt.textInput()` in form | Report title |
| Section checkboxes | `Jt.checkbox()` x6 in form | Toggle report sections |
| Notes textarea | `Jt.textArea()` in form | Custom notes |
| Submit button | `Jt.formSubmitButton()` in form | Triggers PDF generation |
| PDF preview | `Jt.pdf(byte[])` | Inline PDF display |
| Success message | `Jt.success()` | "Report generated!" |

---

## 5. Data Model

### BenchmarkEntry (Java record or class)

```
BenchmarkEntry:
  // Build
  buildCommit: String
  buildNumber: int

  // Hardware
  cpuInfo: String
  gpuInfo: String
  backends: String

  // Model
  modelFilename: String
  modelType: String
  modelSize: long
  modelNParams: long

  // Config
  nBatch: int
  nUbatch: int
  nThreads: int
  nGpuLayers: int
  flashAttn: boolean
  typeK: String
  typeV: String

  // Test params
  nPrompt: int
  nGen: int
  nDepth: int

  // Results
  testTime: String
  avgTs: double
  stddevTs: double
  avgNs: long
  stddevNs: long
  samplesTs: double[]
  samplesNs: long[]
```

### Derived Properties

- **testType**: `nPrompt > 0 ? "PP" : "TG"` (Prompt Processing or Token Generation)
- **modelSizeGB**: `modelSize / 1_073_741_824.0` (bytes to GB)
- **modelParamsB**: `modelNParams / 1_000_000_000.0` (params to billions)

---

## 6. User Flows

### Flow 1: First Visit
1. User opens app → lands on Upload page
2. Uploads one or more llama-bench JSON files
3. Sees summary cards (N models, N tests, hardware info)
4. Reviews raw data in table
5. Clicks "Go to Explorer"

### Flow 2: Exploration
1. Explorer page loads with all uploaded data
2. User selects/deselects models via sidebar checkboxes
3. Switches between PP, TG, and Comparison tabs
4. Observes throughput degradation curves across context depths
5. Expands sample details for variance inspection
6. Clicks "Generate Report"

### Flow 3: Report Generation
1. Report page loads
2. User customizes title, selects sections, adds notes
3. Clicks "Generate PDF"
4. PDF renders inline via `Jt.pdf()`
5. User can download from browser's PDF viewer controls

### Flow 4: Adding More Data
1. User navigates back to Upload page
2. Uploads additional JSON files
3. New data appends to existing session data
4. Explorer and Report pages reflect combined dataset

---

## 7. Technical Constraints & Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| JSON parser | Gson | Simple, well-known, no annotation boilerplate |
| PDF library | Apache PDFBox 3.x | Simplest API, no commercial license, text+table sufficient |
| Charts | echarts-java via `Jt.echarts` | Built into Javelit, rich interactive charts |
| Multi-file upload | `acceptMultipleFiles(TRUE)` | Compare models/hardware from separate benchmark runs |
| State sharing | `Jt.sessionState()` | Standard Javelit pattern for cross-page data |
| No database | In-memory only | Demo app, data lives in session |
| Single Java file vs multi-file | Multi-file with packages | Cleaner organization; requires `-cp` workaround (see §3.2) |
| External JARs in `lib/` | PDFBox + transitive deps | Javelit's compiler can't resolve `//DEPS` for source files |

---

## 8. File Structure

```
javelit-sample/
├── App.java                          # Entry point: //DEPS, //SOURCES, Jt.navigation
├── run.sh                            # Launch script with correct -cp flags
├── pages/
│   ├── UploadPage.java               # Page 1: Upload & Overview
│   ├── ExplorerPage.java             # Page 2: Performance Explorer
│   └── ReportPage.java               # Page 3: Report Generator
├── model/
│   └── BenchmarkEntry.java           # Data model class
├── service/
│   ├── JsonParser.java               # Gson parsing of llama-bench JSON
│   ├── ChartBuilder.java             # ECharts chart construction
│   └── PdfReportGenerator.java       # PDFBox report generation
├── lib/                              # External JARs for Javelit's compiler (see §3.2)
│   ├── pdfbox-3.0.4.jar
│   ├── fontbox-3.0.4.jar
│   ├── pdfbox-io-3.0.4.jar
│   └── commons-logging-1.3.4.jar
├── target/javelit/classes/           # Auto-generated by Javelit compiler (do not commit)
├── lama-bench-rocm72-step35-imatrix.json  # Example data
└── PRD.md                            # This document
```

---

## 9. Non-Goals (Out of Scope)

- No cloud deployment (local JBang only)
- No database or persistence beyond session
- No user authentication
- No chart image export (PDF is text+tables only)
- No real-time benchmark execution
- No CSV/other format support (JSON only)
- No i18n (English only)

---

## 10. Success Criteria

1. App starts with `javelit run App.java` (after JBang install)
2. User can upload the provided example JSON file and see data
3. User can upload multiple files and compare models side by side
4. Charts correctly show throughput degradation across context depths
5. PP and TG benchmarks are clearly separated and labeled
6. PDF report generates with selected sections and downloads correctly
7. App is visually clean and suitable for a live demo/presentation

---

## 11. Verification

- Run: `./run.sh` or `javelit run App.java -cp "target/javelit/classes/:lib/*"` → browser opens at localhost:8080
- Upload: drag `lama-bench-rocm72-step35-imatrix.json` → summary appears
- Explore: navigate to Explorer → charts render with correct data
- Report: navigate to Report → generate PDF → preview renders inline

### Troubleshooting checklist

1. **Clean stale classes**: `rm -rf target/javelit/classes/` before first run after refactoring
2. **Clear JBang cache**: `jbang cache clear` if you changed `//DEPS` or `//SOURCES`
3. **Check `-cp` flag**: Must include both `target/javelit/classes/` AND any `lib/*.jar` files
4. **Use javelit, not jbang**: `jbang run App.java` will fail with `IllegalStateException` — always use `javelit run` or the javelit jar
