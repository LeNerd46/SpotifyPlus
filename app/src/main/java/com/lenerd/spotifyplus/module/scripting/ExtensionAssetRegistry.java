package com.lenerd.spotifyplus.module.scripting;

import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ExtensionAssetRegistry {
    private static final String TAG = "SpotifyPlus:Assets";
    private static final String ASSET_SCHEME = "spotifyplus-asset://";
    private static final ConcurrentHashMap<String, AssetRecord> assets = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Typeface> typefaces = new ConcurrentHashMap<>();

    private ExtensionAssetRegistry() { }

    public static void register(JSONObject operation) throws Exception {
        String assetId = operation.getString("assetId");
        String scriptId = operation.getString("scriptId");
        int generation = operation.getInt("generation");
        String mimeType = operation.optString("mimeType", "application/octet-stream");
        String kind = operation.optString("kind", "asset");
        if (!assetId.startsWith(ASSET_SCHEME)) throw new SecurityException("Invalid extension asset ID");

        File root = new File(operation.getString("rootPath")).getCanonicalFile();
        File manifestFile = operation.has("manifestPath")
            ? new File(operation.getString("manifestPath")).getCanonicalFile()
            : new File(root, "manifest.json").getCanonicalFile();
        File file = new File(operation.getString("filePath")).getCanonicalFile();
        if (!file.isFile()) throw new IllegalArgumentException("Extension asset is not a file: " + file);
        if (!isInside(root, file)) throw new SecurityException("Extension asset escaped its registered root");
        validateManifestDeclaration(root, manifestFile, file, scriptId);

        assets.put(assetId, new AssetRecord(assetId, scriptId, generation, root, file, mimeType, kind));
    }

    public static File resolveFile(String assetId) {
        if (assetId == null || !assetId.startsWith(ASSET_SCHEME)) return null;
        AssetRecord record = assets.get(assetId);
        if (record == null) return null;
        try {
            File canonical = record.file.getCanonicalFile();
            if (!canonical.isFile() || !isInside(record.root, canonical)) {
                assets.remove(assetId);
                return null;
            }
            return canonical;
        } catch (Exception error) {
            Log.e(TAG, "Failed resolving " + assetId, error);
            return null;
        }
    }

    public static String uriFromValue(Object value) {
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof String stringValue) return stringValue;
        if (value instanceof JSONObject object) return object.optString("uri", null);
        return null;
    }

    public static Typeface resolveTypeface(
        Object familyValue,
        int requestedWeight,
        boolean italic,
        Typeface fallback
    ) {
        String assetId = chooseFontAssetId(familyValue, requestedWeight, italic);
        if (assetId == null) {
            if (familyValue instanceof String familyName && !familyName.startsWith(ASSET_SCHEME)) {
                Typeface named = Typeface.create(familyName, italic ? Typeface.ITALIC : Typeface.NORMAL);
                return applyWeight(named, requestedWeight, italic);
            }
            return applyWeight(fallback != null ? fallback : Typeface.DEFAULT, requestedWeight, italic);
        }

        String cacheKey = assetId + "#" + requestedWeight + "#" + italic;
        Typeface cached = typefaces.get(cacheKey);
        if (cached != null) return cached;

        File file = resolveFile(assetId);
        if (file == null) return applyWeight(fallback != null ? fallback : Typeface.DEFAULT, requestedWeight, italic);
        try {
            Typeface loaded = Typeface.createFromFile(file);
            Typeface weighted = applyWeight(loaded, requestedWeight, italic);
            typefaces.put(cacheKey, weighted);
            return weighted;
        } catch (Exception error) {
            Log.e(TAG, "Failed loading font asset " + assetId, error);
            return applyWeight(fallback != null ? fallback : Typeface.DEFAULT, requestedWeight, italic);
        }
    }

    public static Set<String> unregisterScript(String scriptId) {
        Set<String> removed = new HashSet<>();
        for (AssetRecord record : new ArrayList<>(assets.values())) {
            if (!record.scriptId.equals(scriptId)) continue;
            if (assets.remove(record.assetId, record)) removed.add(record.assetId);
        }
        if (!removed.isEmpty()) {
            typefaces.keySet().removeIf(key -> removed.stream().anyMatch(key::startsWith));
        }
        return removed;
    }

    private static String chooseFontAssetId(Object value, int requestedWeight, boolean italic) {
        if (value instanceof String stringValue) {
            return stringValue.startsWith(ASSET_SCHEME) ? stringValue : null;
        }
        if (!(value instanceof JSONObject object)) return null;
        if ("extension-asset".equals(object.optString("type"))) {
            return object.optString("uri", null);
        }
        if (!"extension-font-family".equals(object.optString("type"))) return null;

        JSONArray faces = object.optJSONArray("faces");
        if (faces == null || faces.length() == 0) return null;
        String bestAssetId = null;
        int bestScore = Integer.MAX_VALUE;
        for (int index = 0; index < faces.length(); index += 1) {
            JSONObject face = faces.optJSONObject(index);
            if (face == null) continue;
            int faceWeight = normalizeWeight(face.optInt("weight", 400));
            boolean faceItalic = "italic".equalsIgnoreCase(face.optString("style", "normal"));
            int score = Math.abs(faceWeight - requestedWeight) + (faceItalic == italic ? 0 : 1000);
            String candidate = assetIdFromValue(face.opt("source"));
            if (candidate == null || score >= bestScore) continue;
            bestScore = score;
            bestAssetId = candidate;
        }
        return bestAssetId;
    }

    private static String assetIdFromValue(Object value) {
        if (value instanceof String stringValue) return stringValue;
        if (value instanceof JSONObject object) return object.optString("uri", null);
        return null;
    }

    private static Typeface applyWeight(Typeface base, int weight, boolean italic) {
        int normalizedWeight = normalizeWeight(weight);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(base, normalizedWeight, italic);
        }
        int style = normalizedWeight >= 600 ? Typeface.BOLD : Typeface.NORMAL;
        if (italic) style |= Typeface.ITALIC;
        return Typeface.create(base, style);
    }

    private static int normalizeWeight(int value) {
        return Math.max(100, Math.min(900, Math.round(value / 100f) * 100));
    }

    private static boolean isInside(File root, File file) {
        String rootPath = root.getPath();
        String filePath = file.getPath();
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    private static void validateManifestDeclaration(
        File root,
        File manifestFile,
        File file,
        String scriptId
    ) throws Exception {
        File manifestRoot = manifestFile.getParentFile();
        if (
            manifestRoot == null
                || !"manifest.json".equals(manifestFile.getName())
                || !manifestFile.isFile()
                || !isInside(manifestRoot, root)
        ) {
            throw new SecurityException("Extension asset root has no valid manifest.json");
        }
        JSONObject manifest = new JSONObject(new String(
            Files.readAllBytes(manifestFile.toPath()),
            StandardCharsets.UTF_8
        ));
        if (!scriptId.equals(manifest.optString("id"))) {
            throw new SecurityException("Extension asset root does not belong to " + scriptId);
        }

        String relativePath = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
        JSONArray patterns = manifest.optJSONArray("assets");
        if (patterns == null) throw new SecurityException("Extension manifest does not declare assets");
        for (int index = 0; index < patterns.length(); index += 1) {
            if (matchesPattern(relativePath, patterns.optString(index, ""))) return;
        }
        throw new SecurityException("Asset is not declared by manifest.assets: " + relativePath);
    }

    private static boolean matchesPattern(String relativePath, String pattern) {
        if (pattern == null || pattern.isBlank() || pattern.startsWith("/")) return false;
        String normalized = pattern.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) return false;
        }
        StringBuilder expression = new StringBuilder("^");
        for (int index = 0; index < normalized.length(); index += 1) {
            char character = normalized.charAt(index);
            if (character == '*' && index + 1 < normalized.length() && normalized.charAt(index + 1) == '*') {
                boolean followedBySlash = index + 2 < normalized.length() && normalized.charAt(index + 2) == '/';
                expression.append(followedBySlash ? "(?:.*/)?" : ".*");
                index += followedBySlash ? 2 : 1;
            } else if (character == '*') {
                expression.append("[^/]*");
            } else if (character == '?') {
                expression.append("[^/]");
            } else {
                if (".[]{}()+-^$|\\".indexOf(character) >= 0) expression.append('\\');
                expression.append(character);
            }
        }
        expression.append('$');
        return relativePath.matches(expression.toString());
    }

    private static final class AssetRecord {
        final String assetId;
        final String scriptId;
        final int generation;
        final File root;
        final File file;
        final String mimeType;
        final String kind;

        AssetRecord(
            String assetId,
            String scriptId,
            int generation,
            File root,
            File file,
            String mimeType,
            String kind
        ) {
            this.assetId = assetId;
            this.scriptId = scriptId;
            this.generation = generation;
            this.root = root;
            this.file = file;
            this.mimeType = mimeType;
            this.kind = kind;
        }
    }
}
