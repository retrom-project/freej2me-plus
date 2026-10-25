package javax.microedition.rms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Synchronous delta commits; the existing .rms + numbered files remain the stable format. */
final class RecordStoreStorage {
    private final File root;
    private final String base;
    private final FileIO io;
    private Map<Integer, byte[]> saved = new HashMap<Integer, byte[]>();
    RecordStoreStorage(File root, String base) { this(root, base, new FileIO()); }
    RecordStoreStorage(File root, String base, FileIO io) { this.root = root; this.base = base; this.io = io; }
    private File path(String suffix) { return new File(root, base + suffix); }
    void remember(Map<Integer, byte[]> records) { saved = new HashMap<Integer, byte[]>(records); }

    synchronized void commit(byte[] metadata, Map<Integer, byte[]> records) throws IOException {
        recover();
        Map<Integer, byte[]> changed = new HashMap<Integer, byte[]>();
        for (Map.Entry<Integer, byte[]> entry : records.entrySet()) {
            if (!Arrays.equals(saved.get(entry.getKey()), entry.getValue())) changed.put(entry.getKey(), entry.getValue());
        }
        for (Integer id : saved.keySet()) if (!records.containsKey(id)) changed.put(id, null);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream journal = new DataOutputStream(bytes);
        journal.writeInt(0x524d5331);
        journal.writeInt(metadata.length);
        journal.write(metadata);
        journal.writeInt(changed.size());
        for (Integer id : changed.keySet()) {
            File file = path("." + id);
            byte[] previous = io.exists(file) ? io.read(file) : null;
            journal.writeInt(id);
            journal.writeInt(previous == null ? -1 : previous.length);
            if (previous != null) journal.write(previous);
        }
        // The undo journal becomes visible before any committed record changes.
        try {
            io.writeAtomic(path(".txn"), bytes.toByteArray());
            for (Map.Entry<Integer, byte[]> entry : changed.entrySet()) {
                File target = path("." + entry.getKey());
                if (entry.getValue() == null) io.delete(target);
                else io.writeAtomic(target, entry.getValue());
            }
            // Publishing metadata is the commit point, including add/delete/tag changes.
            io.writeAtomic(path(".rms"), metadata);
            io.delete(path(".txn"));
            remember(records);
        } catch (IOException error) {
            boolean committed = io.exists(path(".rms")) && Arrays.equals(io.read(path(".rms")), metadata);
            recover();
            if (committed) remember(records);
            else throw error;
        }
    }

    synchronized void recover() throws IOException {
        File transaction = path(".txn");
        if (io.exists(transaction)) {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(io.read(transaction)));
            if (input.readInt() != 0x524d5331) throw new IOException("Invalid RMS transaction");
            byte[] nextMetadata = readBytes(input, input.readInt());
            boolean committed = io.exists(path(".rms")) && Arrays.equals(io.read(path(".rms")), nextMetadata);
            int count = input.readInt();
            if (count < 0 || count > 1000000) throw new IOException("Invalid RMS transaction count");
            Map<Integer, byte[]> previousRecords = new HashMap<Integer, byte[]>();
            for (int i = 0; i < count; i++) {
                int id = input.readInt(), length = input.readInt();
                if (id <= 0 || length < -1 || previousRecords.containsKey(id)) throw new IOException("Invalid RMS transaction record");
                previousRecords.put(id, length == -1 ? null : readBytes(input, length));
            }
            if (input.available() != 0) throw new IOException("Trailing RMS transaction data");
            if (!committed) for (Map.Entry<Integer, byte[]> entry : previousRecords.entrySet()) {
                if (entry.getValue() == null) io.delete(path("." + entry.getKey()));
                else io.writeAtomic(path("." + entry.getKey()), entry.getValue());
            }
            io.delete(transaction);
        }
        // An interrupted staging write has never crossed its atomic rename.
        File[] temporary = root.listFiles();
        if (temporary != null) for (File file : temporary) {
            String name = file.getName();
            if (name.startsWith(base + ".")) {
                String suffix = name.substring(base.length() + 1);
                if (suffix.equals("txn.tmp") || suffix.equals("rms.tmp") || suffix.matches("[1-9][0-9]*\\.tmp")) io.delete(file);
            }
        }
    }

    private static byte[] readBytes(DataInputStream input, int length) throws IOException {
        if (length < 0 || length > input.available()) throw new IOException("Truncated RMS transaction");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return bytes;
    }

    /** The filesystem seam permits failures at actual write/rename boundaries in tests. */
    static class FileIO {
        boolean exists(File file) { return file.exists(); }
        byte[] read(File file) throws IOException {
            FileInputStream input = new FileInputStream(file);
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                return output.toByteArray();
            } finally { input.close(); }
        }
        void writeAtomic(File file, byte[] bytes) throws IOException {
            File temporary = new File(file.getPath() + ".tmp");
            FileOutputStream output = new FileOutputStream(temporary);
            try { output.write(bytes); output.getFD().sync(); }
            finally { output.close(); }
            if (!temporary.renameTo(file)) throw new IOException("Cannot publish RMS file");
        }
        void delete(File file) throws IOException {
            if (file.exists() && !file.delete()) throw new IOException("Cannot remove RMS file");
        }
    }
}
