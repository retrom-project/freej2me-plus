package javax.microedition.rms;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public final class RecordStoreStorageTest {
    static final class InterruptedCommit extends Error { }
    static final class FaultIO extends RecordStoreStorage.FileIO {
        String interruptAfter;
        String failBefore;
        int recordWrites;
        void writeAtomic(File file, byte[] bytes) throws IOException {
            if (file.getName().equals(failBefore)) { failBefore = null; throw new IOException("injected write failure"); }
            super.writeAtomic(file, bytes);
            if (file.getName().matches("store\\.[0-9]+")) recordWrites++;
            if (file.getName().equals(interruptAfter)) { interruptAfter = null; throw new InterruptedCommit(); }
        }
    }
    public static void main(String[] args) throws Exception {
        for (String point : new String[] {"store.1", "store.rms"}) {
            File root = Files.createTempDirectory("rms-recovery-").toFile();
            FaultIO io = new FaultIO();
            RecordStoreStorage storage = new RecordStoreStorage(root, "store", io);
            Map<Integer, byte[]> old = records(1, 1, 2, 2);
            storage.commit(new byte[] {1}, old);
            Map<Integer, byte[]> next = records(1, 9, 3, 3);
            next.put(4, new byte[0]);
            io.interruptAfter = point;
            try { storage.commit(new byte[] {2}, next); throw new AssertionError("interruption did not execute"); }
            catch (InterruptedCommit expected) { }
            new RecordStoreStorage(root, "store").recover();
            boolean committed = point.equals("store.rms");
            check(Arrays.equals(io.read(new File(root, "store.rms")), new byte[] {(byte) (committed ? 2 : 1)}), "metadata commit point");
            check(Arrays.equals(io.read(new File(root, "store.1")), new byte[] {(byte) (committed ? 9 : 1)}), "record rollback");
            check(new File(root, "store.2").exists() != committed && new File(root, "store.3").exists() == committed, "add/delete rollback");
            if (committed) check(io.read(new File(root, "store.4")).length == 0, "empty record");
            check(!new File(root, "store.txn").exists(), "journal cleanup");
        }
        File root = Files.createTempDirectory("rms-delta-").toFile();
        FaultIO io = new FaultIO();
        RecordStoreStorage storage = new RecordStoreStorage(root, "store", io);
        Map<Integer, byte[]> records = new HashMap<Integer, byte[]>();
        for (int i = 1; i <= 101; i++) {
            records.put(i, new byte[] {(byte) i});
            storage.commit(new byte[] {(byte) i}, records);
        }
        check(io.recordWrites == 101, "unchanged records were rewritten: " + io.recordWrites);
        io.failBefore = "store.rms";
        records.put(1, new byte[] {9});
        try { storage.commit(new byte[] {102}, records); throw new AssertionError("I/O error was suppressed"); }
        catch (IOException expected) { }
        check(Arrays.equals(io.read(new File(root, "store.1")), new byte[] {1}), "failed commit retained new data");
        System.out.println("RMS atomic recovery, I/O failure and 101 delta record writes verified.");
    }
    private static Map<Integer, byte[]> records(int... pairs) {
        Map<Integer, byte[]> values = new HashMap<Integer, byte[]>();
        for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], new byte[] {(byte) pairs[i + 1]});
        return values;
    }
    private static void check(boolean value, String message) {if (!value) throw new AssertionError(message);}
}
