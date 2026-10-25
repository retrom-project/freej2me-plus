package org.recompile.mobile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import javax.microedition.rms.RecordStore;

/** Real RMS API, with an untouched record's timestamp detecting full rewrites. */
public final class MiniJvmRecordStoreTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("file.encoding", Mobile.textEncoding);
        MobilePlatform platform = new MobilePlatform(240, 320);
        Mobile.setPlatform(platform, new Runnable() {public void run() {}});
        Path directory = Files.createTempDirectory("freej2me-rms-test-");
        platform.dataPath = directory.toString() + File.separator;
        platform.loader = new MIDletLoader(new File(args[0]).toURI().toURL(), Collections.<String, String>emptyMap());
        String name = "通用存档";
        RecordStore store = RecordStore.openRecordStore(name, true);
        int first = store.addRecord(new byte[] {1}, 0, 1);
        Path record = Files.walk(directory).filter(p -> p.toString().endsWith("." + first)).findFirst().get();
        FileTime sentinel = FileTime.fromMillis(1000000000000L);
        Files.setLastModifiedTime(record, sentinel);
        for (int i = 0; i < 100; i++) store.addRecord(new byte[] {(byte) i}, 0, 1);
        if (!Files.getLastModifiedTime(record).equals(sentinel)) throw new AssertionError("RMS rewrote an unchanged record");
        int empty = store.addRecord(null, 0, 0);
        store.setRecord(first, new byte[] {9, 8}, 0, 2);
        store.deleteRecord(2);
        RecordStore shared = RecordStore.openRecordStore(name, false);
        if (shared != store || shared.getRecord(first)[0] != 9) throw new AssertionError("Shared store lost a write");
        shared.closeRecordStore();
        store.closeRecordStore();
        store = RecordStore.openRecordStore(name, false);
        if (store.getRecord(first).length != 2 || store.getRecord(first)[1] != 8 || store.getRecordSize(empty) != 0 || store.getNumRecords() != 101)
            throw new AssertionError("RMS did not persist add/overwrite/delete/empty records");
        store.closeRecordStore();
        RecordStore.deleteRecordStore(name);
        System.out.println("RMS_DELTA_PERSISTENCE_VERIFIED");
    }
}
