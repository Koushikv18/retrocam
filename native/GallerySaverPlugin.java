package com.koushik.retrocam;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.OutputStream;

/** Saves a base64 JPEG into the system gallery (Pictures/RetroCam) via MediaStore. */
@CapacitorPlugin(name = "GallerySaver")
public class GallerySaverPlugin extends Plugin {
    @PluginMethod
    public void savePhoto(PluginCall call) {
        String b64 = call.getString("data");
        String name = call.getString("name", "RetroCam_" + System.currentTimeMillis() + ".jpg");
        if (b64 == null || b64.isEmpty()) { call.reject("No image data"); return; }
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) {
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RetroCam");
                v.put(MediaStore.Images.Media.IS_PENDING, 1);
            }
            Uri uri = getContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) { call.reject("MediaStore insert failed"); return; }
            try (OutputStream os = getContext().getContentResolver().openOutputStream(uri)) {
                if (os == null) { call.reject("Could not open output stream"); return; }
                os.write(bytes);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues u = new ContentValues();
                u.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContext().getContentResolver().update(uri, u, null, null);
            }
            JSObject r = new JSObject();
            r.put("uri", uri.toString());
            call.resolve(r);
        } catch (Exception e) {
            call.reject(e.getMessage() == null ? "Save failed" : e.getMessage());
        }
    }
}
