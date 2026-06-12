package com.tfajfar.walkietalkie.data;

import android.os.Environment;

import java.io.File;
import java.io.FileFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordingRepository {
    private static RecordingRepository instance;

    public static synchronized RecordingRepository getInstance() {
        if (instance == null) {
            instance = new RecordingRepository();
        }
        return instance;
    }

    private RecordingRepository() {
    }

    public File getRecordingDir() {
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        return new File(musicDir, "P2PWalkieTalkie");
    }

    public String createOutgoingRecordingPath() {
        return new File(getRecordingDir(), createTimestamp() + "_OUT.amr").getAbsolutePath();
    }

    public List<File> getRecordings() {
        File dir = getRecordingDir();
        if (!dir.exists()) {
            return new ArrayList<File>();
        }

        File[] files = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isFile() && file.getName().toLowerCase().endsWith(".amr");
            }
        });

        if (files == null || files.length == 0) {
            return new ArrayList<File>();
        }

        List<File> recordings = new ArrayList<File>(Arrays.asList(files));
        Collections.sort(recordings, new Comparator<File>() {
            @Override
            public int compare(File first, File second) {
                return Long.valueOf(second.lastModified()).compareTo(first.lastModified());
            }
        });
        return recordings;
    }

    private String createTimestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    }
}
