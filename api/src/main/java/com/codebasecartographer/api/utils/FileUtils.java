package com.codebasecartographer.api.utils;

public final class FileUtils {

    private FileUtils() {}

    public static String extractFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) return filePath;
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0
                ? filePath.substring(lastSlash + 1)
                : filePath;
    }
}
