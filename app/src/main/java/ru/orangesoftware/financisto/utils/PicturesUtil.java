package ru.orangesoftware.financisto.utils;

import android.content.Context;
import android.os.Environment;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PicturesUtil {

    private static final File LEGACY_PICTURES_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

    private static final SimpleDateFormat PICTURE_FILE_NAME_FORMAT = new SimpleDateFormat("yyyyMMddHHmmssSS");

    public static File createEmptyImageFile(Context context) {
        return pictureFile(context, PicturesUtil.PICTURE_FILE_NAME_FORMAT.format(new Date()) + ".jpg", false);
    }

    public static File pictureFile(Context context, String pictureFileName, boolean fallbackToLegacy) {
        File dir = context.getExternalFilesDir("pictures");
        if (dir != null && !dir.exists()) dir.mkdirs();
        File file = new File(dir != null ? dir : context.getFilesDir(), pictureFileName);
        if (fallbackToLegacy && !file.exists()) {
            file = new File(LEGACY_PICTURES_DIR, pictureFileName);
        }
        return file;
    }

    public static void showImage(Context context, ImageView imageView, String pictureFileName) {
        if (pictureFileName == null || imageView == null) return;
        File file = PicturesUtil.pictureFile(context, pictureFileName, true);
        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(context,
                ru.orangesoftware.financisto.BuildConfig.APPLICATION_ID,
                file);
        imageView.setImageTintList(null);
        imageView.clearColorFilter();
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(context)
                .load(uri)
                .transition(new DrawableTransitionOptions().crossFade())
                .into(imageView);
    }
}
