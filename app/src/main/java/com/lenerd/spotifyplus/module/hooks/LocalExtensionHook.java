package com.lenerd.spotifyplus.module.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.XposedHooker;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;

@XposedHooker
public class LocalExtensionHook extends SpotifyHook {
    private static final String TAG = "SpotifyPlus:LocalExtensions";
    private static final String PREFS = "spotifyplus_elevated";
    private static final String KEY_DEVELOPER_MODE = "developer_mode";
    private static final String KEY_LOCAL_TREE_URI = "local_extensions_tree_uri";
    private static final String KEY_LOCAL_TREE_NAME = "local_extensions_tree_name";
    private static final int REQUEST_LOCAL_EXTENSIONS_FOLDER = 0x5350;

    @Override
    protected void hookSetup() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException {
        SpotifyNativeBridge.registerHandler("elevated", this);
        hook(Activity.class.getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class));
    }

    @Override
    protected void beforeHook(SpotifyCallback callback) { }

    @AfterInvocation
    public static void after(XposedInterface.AfterHookCallback callback) {
        LocalExtensionHook hook = getHook(LocalExtensionHook.class);
        if (hook == null) return;
        hook.afterHook(buildCallback(callback));
    }

    @Override
    protected void afterHook(SpotifyCallback callback) {
        try {
            Object[] args = callback.getArgs();
            if (args.length < 3 || !(args[0] instanceof Integer)) return;
            int requestCode = (Integer) args[0];
            if (requestCode != REQUEST_LOCAL_EXTENSIONS_FOLDER) return;

            int resultCode = args[1] instanceof Integer ? (Integer) args[1] : Activity.RESULT_CANCELED;
            Intent data = args[2] instanceof Intent ? (Intent) args[2] : null;
            handlePickerResult(currentActivity, resultCode, data);
        } catch (Exception e) {
            logError(e);
        }
    }

    @Override
    public Object handle(String command, Object[] args) {
        Activity activity = currentActivity;
        return switch (command) {
            case "getDeveloperMode" -> activity != null && getDeveloperMode(activity);
            case "setDeveloperMode" -> {
                if (activity != null) setDeveloperMode(activity, args.length > 0 && (Boolean) args[0]);
                yield null;
            }
            case "pickLocalExtensionsFolder" -> activity != null && pickLocalExtensionsFolder(activity);
            case "getLocalExtensionsFolderDisplayName" -> activity == null ? null : getLocalExtensionsFolderDisplayName(activity);
            case "listLocalExtensions" -> activity == null ? "[]" : listLocalExtensions(activity);
            case "refreshLocalExtensions" -> activity == null ? "[]" : refreshLocalExtensions(activity);
            default -> null;
        };
    }

    public static boolean getDeveloperMode(Context context) {
        return prefs(context).getBoolean(KEY_DEVELOPER_MODE, false);
    }

    public static void setDeveloperMode(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply();
    }

    public static File getLocalExtensionCacheDir(Context context) {
        return new File(context.getFilesDir(), "spotifyplus-local-extensions");
    }

    public static boolean pickLocalExtensionsFolder(Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_LOCAL_EXTENSIONS_FOLDER);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to open local extensions picker", e);
            return false;
        }
    }

    public static String getLocalExtensionsFolderDisplayName(Context context) {
        String name = prefs(context).getString(KEY_LOCAL_TREE_NAME, null);
        return name == null || name.isBlank() ? null : name;
    }

    public static String listLocalExtensions(Context context) {
        JSONArray result = new JSONArray();
        File cache = getLocalExtensionCacheDir(context);
        File[] entries = cache.listFiles(File::isDirectory);
        if (entries == null) return result.toString();

        for (File entry : entries) {
            try {
                File manifestFile = new File(entry, "manifest.json");
                if (!manifestFile.isFile()) continue;

                JSONObject manifest = new JSONObject(new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8));
                JSONObject item = new JSONObject();
                item.put("id", manifest.optString("id", entry.getName()));
                item.put("name", manifest.optString("name", manifest.optString("id", entry.getName())));
                item.put("version", manifest.optString("version", ""));
                item.put("description", manifest.optString("description", ""));
                item.put("author", manifest.optString("author", ""));
                item.put("path", entry.getAbsolutePath());
                item.put("installedAt", Instant.ofEpochMilli(entry.lastModified()).toString());
                result.put(item);
            } catch (Exception e) {
                Log.e(TAG, "Failed to read local extension manifest in " + entry.getAbsolutePath(), e);
            }
        }

        return result.toString();
    }

    public static String refreshLocalExtensions(Context context) {
        File cache = getLocalExtensionCacheDir(context);
        deleteRecursive(cache);
        if (!getDeveloperMode(context)) return listLocalExtensions(context);

        String tree = prefs(context).getString(KEY_LOCAL_TREE_URI, null);
        if (tree == null || tree.isBlank()) return listLocalExtensions(context);

        try {
            Uri treeUri = Uri.parse(tree);
            String rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
            copyExtensionChildren(context, treeUri, rootDocumentId, cache);
        } catch (Exception e) {
            Log.e(TAG, "Failed to refresh local extensions", e);
        }

        return listLocalExtensions(context);
    }

    private static void handlePickerResult(Context context, int resultCode, Intent data) {
        if (context == null || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;

        Uri treeUri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        try {
            context.getContentResolver().takePersistableUriPermission(treeUri, flags);
        } catch (Exception e) {
            Log.w(TAG, "Could not persist local extension folder permission", e);
        }

        prefs(context).edit()
                .putString(KEY_LOCAL_TREE_URI, treeUri.toString())
                .putString(KEY_LOCAL_TREE_NAME, readDisplayName(context, treeUri, DocumentsContract.getTreeDocumentId(treeUri)))
                .apply();

        refreshLocalExtensions(context);
    }

    private static void copyExtensionChildren(Context context, Uri treeUri, String parentDocumentId, File cache) throws Exception {
        JSONArray children = listChildren(context, treeUri, parentDocumentId);
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.getJSONObject(i);
            if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(child.optString("mimeType"))) continue;

            String childId = child.getString("documentId");
            if (!hasChildNamed(context, treeUri, childId, "manifest.json")) continue;

            File destination = new File(cache, sanitizeFileName(child.optString("displayName", childId)));
            copyDocumentTree(context, treeUri, childId, destination);
        }
    }

    private static void copyDocumentTree(Context context, Uri treeUri, String documentId, File destination) throws Exception {
        JSONArray children = listChildren(context, treeUri, documentId);
        if (!destination.exists() && !destination.mkdirs()) throw new IllegalStateException("Failed to create " + destination);

        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.getJSONObject(i);
            String childId = child.getString("documentId");
            String name = sanitizeFileName(child.optString("displayName", childId));
            File target = new File(destination, name);

            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(child.optString("mimeType"))) {
                copyDocumentTree(context, treeUri, childId, target);
            } else {
                copyDocumentFile(context, DocumentsContract.buildDocumentUriUsingTree(treeUri, childId), target);
            }
        }
    }

    private static void copyDocumentFile(Context context, Uri documentUri, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Failed to create " + parent);

        try (InputStream in = context.getContentResolver().openInputStream(documentUri);
             FileOutputStream out = new FileOutputStream(target, false)) {
            if (in == null) throw new IllegalStateException("Could not open " + documentUri);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
        }
    }

    private static boolean hasChildNamed(Context context, Uri treeUri, String parentDocumentId, String name) throws Exception {
        JSONArray children = listChildren(context, treeUri, parentDocumentId);
        for (int i = 0; i < children.length(); i++) {
            if (name.equals(children.getJSONObject(i).optString("displayName"))) return true;
        }
        return false;
    }

    private static JSONArray listChildren(Context context, Uri treeUri, String parentDocumentId) throws Exception {
        JSONArray result = new JSONArray();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId);
        String[] projection = new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        try (Cursor cursor = context.getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor == null) return result;
            while (cursor.moveToNext()) {
                JSONObject child = new JSONObject();
                child.put("documentId", cursor.getString(0));
                child.put("displayName", cursor.getString(1));
                child.put("mimeType", cursor.getString(2));
                result.put(child);
            }
        }

        return result;
    }

    private static String readDisplayName(Context context, Uri treeUri, String documentId) {
        try {
            Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
            String[] projection = new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME };
            try (Cursor cursor = context.getContentResolver().query(documentUri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read local extensions folder name", e);
        }
        return "Local extensions";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String sanitizeFileName(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isEmpty() ? "extension" : sanitized;
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursive(child);
        }
        if (!file.delete()) Log.w(TAG, "Failed to delete " + file.getAbsolutePath());
    }
}
