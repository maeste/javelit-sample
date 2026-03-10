package model;

public class BenchmarkEntry {
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
