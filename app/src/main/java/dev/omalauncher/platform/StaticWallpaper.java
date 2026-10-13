package dev.omalauncher.platform;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;

/** Call from a worker. Decodes a bounded bitmap and preserves any active live wallpaper. */
public final class StaticWallpaper {
    private StaticWallpaper() { }
    private static boolean live(WallpaperManager manager) {
        return manager.getWallpaperInfo() != null || (android.os.Build.VERSION.SDK_INT >= 34
                && manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK) != null);
    }
    public static WallpaperOutcome apply(Context context, File image) {
        if (image == null || !image.isFile()) return WallpaperOutcome.INVALID_IMAGE;
        Bitmap bitmap = null;
        try {
            WallpaperManager manager = WallpaperManager.getInstance(context);
            if (!manager.isWallpaperSupported() || !manager.isSetWallpaperAllowed()) return WallpaperOutcome.BLOCKED;
            if (live(manager)) return WallpaperOutcome.LIVE_ACTIVE;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(image.getPath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return WallpaperOutcome.INVALID_IMAGE;
            options.inSampleSize = 1;
            long screenPixels = (long) context.getResources().getDisplayMetrics().widthPixels * context.getResources().getDisplayMetrics().heightPixels;
            long budget = Math.max(1, Math.min(8_000_000L, screenPixels * 2));
            while ((long) (options.outWidth / options.inSampleSize) * (options.outHeight / options.inSampleSize) > budget)
                options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            bitmap = BitmapFactory.decodeFile(image.getPath(), options);
            if (bitmap == null) return WallpaperOutcome.INVALID_IMAGE;
            Bitmap decoded = bitmap;
            // Recheck after decoding; the active wallpaper may have changed while the worker ran.
            if (live(manager)) return WallpaperOutcome.LIVE_ACTIVE;
            return WallpaperOutcome.apply(flags -> manager.setBitmap(decoded, null, true, flags));
        } catch (RuntimeException failure) { return WallpaperOutcome.FAILED; }
        finally { if (bitmap != null) bitmap.recycle(); }
    }
}
