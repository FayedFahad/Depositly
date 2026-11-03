package app.depositly.mobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class DocumentTransferTest {
    @Test public void aSlowProviderDoesNotBlockTheCallerAndResultUsesTheCallbackExecutor() throws Exception {
        BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        CountDownLatch reading = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        AtomicReference<String> imported = new AtomicReference<>();
        Thread caller = Thread.currentThread();
        try (DocumentTransfer transfer = new DocumentTransfer(callbacks::add)) {
            transfer.execute(() -> {
                workerThread.set(Thread.currentThread());
                reading.countDown();
                assertTrue(release.await(2, TimeUnit.SECONDS));
                return DocumentTransfer.read(new ByteArrayInputStream("backup".getBytes(StandardCharsets.UTF_8)), () -> false);
            }, (value, error) -> { assertNull(error); assertSame(caller, Thread.currentThread()); imported.set(value); });
            assertTrue(reading.await(2, TimeUnit.SECONDS));
            assertNotSame(caller, workerThread.get());
            assertNull("A blocked provider cannot already have delivered a result", callbacks.poll());
            assertNull(imported.get());
            release.countDown();
            Runnable callback = callbacks.poll(2, TimeUnit.SECONDS);
            assertNotNull(callback);
            assertNull("Completion must wait for the callback executor", imported.get());
            callback.run();
            assertEquals("backup", imported.get());
        } finally { release.countDown(); }
    }

    @Test public void destroyingTheOwnerInterruptsWorkClosesItsStreamAndDropsCallbacks() throws Exception {
        BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        CountDownLatch reading = new CountDownLatch(1), closed = new CountDownLatch(1);
        AtomicBoolean delivered = new AtomicBoolean();
        InputStream slow = new InputStream() {
            @Override public int read() throws IOException {
                reading.countDown();
                try { new CountDownLatch(1).await(); return -1; }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InterruptedIOException(); }
            }
            @Override public void close() { closed.countDown(); }
        };
        DocumentTransfer transfer = new DocumentTransfer(callbacks::add);
        transfer.execute(() -> { try (InputStream in = slow) { return DocumentTransfer.read(in, () -> false); } },
            (value, error) -> delivered.set(true));
        assertTrue(reading.await(2, TimeUnit.SECONDS));
        transfer.close();
        assertTrue("Cancellation must leave the stream's try-with-resources block", closed.await(2, TimeUnit.SECONDS));
        Runnable queued;
        while ((queued = callbacks.poll()) != null) queued.run();
        assertFalse(delivered.get());
    }

    @Test public void aCompletedResultQueuedBeforeDestructionIsAlsoDiscarded() throws Exception {
        BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        AtomicBoolean delivered = new AtomicBoolean();
        DocumentTransfer transfer = new DocumentTransfer(callbacks::add);
        transfer.execute(() -> "finished", (value, error) -> delivered.set(true));
        Runnable callback = callbacks.poll(2, TimeUnit.SECONDS);
        assertNotNull(callback);
        transfer.close();
        callback.run();
        assertFalse(delivered.get());
    }

    @Test public void providerFailuresReachTheCallbackInsteadOfPretendingToSucceed() throws Exception {
        BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        IOException denied = new IOException("Provider denied access");
        try (DocumentTransfer transfer = new DocumentTransfer(callbacks::add)) {
            transfer.execute(() -> { throw denied; }, (value, error) -> { assertNull(value); failure.set(error); });
            Runnable callback = callbacks.poll(2, TimeUnit.SECONDS);
            assertNotNull(callback);
            callback.run();
            assertSame(denied, failure.get());
        }
    }

    @Test public void backupSizeLimitAndUtf8SurviveTheWorkerRefactor() throws Exception {
        String text = "Account,বাংলা\r\n001,৳100";
        assertEquals(text, DocumentTransfer.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), () -> false));
        byte[] allowed = new byte[DocumentTransfer.MAX_BACKUP_BYTES];
        assertEquals(allowed.length, DocumentTransfer.read(new ByteArrayInputStream(allowed), () -> false).length());
        try {
            DocumentTransfer.read(new ByteArrayInputStream(new byte[DocumentTransfer.MAX_BACKUP_BYTES + 1]), () -> false);
            fail("Oversized backups must remain rejected");
        } catch (IOException expected) { assertEquals("Backup is too large", expected.getMessage()); }
    }

    @Test public void canceledCopiesStopBeforeWritingAnotherChunk() throws Exception {
        AtomicBoolean canceled = new AtomicBoolean();
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        InputStream source = new ByteArrayInputStream(new byte[16384]) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                int count = super.read(bytes, offset, length);
                canceled.set(true);
                return count;
            }
        };
        try { DocumentTransfer.copy(source, destination, canceled::get); fail("Canceled copy continued"); }
        catch (InterruptedIOException expected) { assertEquals(0, destination.size()); }
    }

    @Test public void exportsCanExceedTheImportSizeLimitWithoutTruncation() throws Exception {
        final long exportSize = DocumentTransfer.MAX_BACKUP_BYTES + 12345L;
        final long[] remaining = {exportSize}, written = {0};
        InputStream generated = new InputStream() {
            @Override public int read() {
                if (remaining[0] == 0) return -1;
                remaining[0]--;
                return 0x5a;
            }
            @Override public int read(byte[] bytes, int offset, int length) {
                if (remaining[0] == 0) return -1;
                int count = (int) Math.min(length, remaining[0]);
                java.util.Arrays.fill(bytes, offset, offset + count, (byte) 0x5a);
                remaining[0] -= count;
                return count;
            }
        };
        OutputStream counted = new OutputStream() {
            @Override public void write(int value) { assertEquals(0x5a, value); written[0]++; }
            @Override public void write(byte[] bytes, int offset, int length) {
                assertEquals((byte) 0x5a, bytes[offset]);
                assertEquals((byte) 0x5a, bytes[offset + length - 1]);
                written[0] += length;
            }
        };
        DocumentTransfer.copy(generated, counted, () -> false);
        assertEquals(exportSize, written[0]);
        assertEquals(0L, remaining[0]);
    }
}
