package service;

import model.BenchmarkEntry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class JsonParser {

    private JsonParser() {}

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
