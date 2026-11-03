package app.depositly.mobile.updates;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Shares exactly one private, verified APK through a temporary read URI grant. */
public final class UpdateApkProvider extends ContentProvider {
    public static final String AUTHORITY = "app.depositly.mobile.updates";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/update.apk");
    @Override public boolean onCreate() { return true; }
    public static File apk(android.content.Context context) { return new File(context.getFilesDir(), "updates/update.apk"); }
    private File file(Uri uri) throws FileNotFoundException {
        if (!URI.equals(uri)) throw new FileNotFoundException("Unknown update file");
        File file = apk(getContext());
        if (!file.isFile()) throw new FileNotFoundException("Download the update again");
        return file;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Update files are read only");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { return URI.equals(uri) ? "application/vnd.android.package-archive" : null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        try {
            File file = file(uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns, 1);
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "Depositly-update.apk";
                else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
            }
            cursor.addRow(row); return cursor;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException("Read only"); }
}
