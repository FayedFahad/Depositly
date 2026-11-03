package app.depositly.mobile;

import java.io.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Runs document-provider work away from the Activity thread and drops stale callbacks. */
public final class DocumentTransfer implements Closeable {
    public static final int MAX_BACKUP_BYTES = 20000000;
    public interface Work<T> { T run() throws Exception; }
    public interface Result<T> { void complete(T value, Exception error); }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Executor callbacks;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DocumentTransfer(Executor callbacks) { this.callbacks = callbacks; }

    public synchronized <T> void execute(Work<T> work, Result<T> result) {
        if (closed.get()) return;
        worker.execute(() -> {
            T value = null;
            Exception error = null;
            try { checkCanceled(closed::get); value = work.run(); }
            catch (Exception e) { error = e; }
            final T completed = value;
            final Exception failure = error;
            if (!closed.get()) callbacks.execute(() -> {
                if (!closed.get()) result.complete(completed, failure);
            });
        });
    }

    public static String read(InputStream in, BooleanSupplier canceled) throws IOException {
        if (in == null) throw new IOException("No file selected");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transfer(in, out, canceled, MAX_BACKUP_BYTES);
        return out.toString("UTF-8");
    }

    public static void copy(InputStream in, OutputStream out, BooleanSupplier canceled) throws IOException {
        if (in == null || out == null) throw new IOException("Could not open the file");
        transfer(in, out, canceled, -1);
    }

    private static void transfer(InputStream in, OutputStream out, BooleanSupplier canceled, int limit) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        while (true) {
            checkCanceled(canceled);
            int n = in.read(buffer);
            checkCanceled(canceled);
            if (n == -1) return;
            if (limit >= 0 && total + n > limit) throw new IOException("Backup is too large");
            out.write(buffer, 0, n);
            total += n;
        }
    }

    private static void checkCanceled(BooleanSupplier canceled) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || canceled.getAsBoolean()) throw new InterruptedIOException("File transfer canceled");
    }

    @Override public synchronized void close() { closed.set(true); worker.shutdownNow(); }
}
