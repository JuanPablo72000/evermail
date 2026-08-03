package com.juanpablo.evermail.util;

import com.juanpablo.evermail.exception.AttachmentException;
import com.juanpablo.evermail.exception.ErrorCode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileUtil {

    private static final String ATTACHMENT_STORAGE_ROOT = "attachments";

    private FileUtil() {
        // Prevents instantiation — this class only holds static methods.
    }

    public static void validateSize(long sizeBytes, long maxBytes) throws AttachmentException {
        if (sizeBytes > maxBytes) {
            throw new AttachmentException(
                    ErrorCode.ATTACHMENT_TOO_LARGE,
                    "Attachment size " + sizeBytes + " bytes exceeds the maximum of " + maxBytes + " bytes"
            );
        }
    }

    public static boolean exists(String filePath) {
        if (filePath == null) {
            return false;
        }
        return new File(filePath).exists();
    }

    public static File resolveOrThrow(String filePath) throws AttachmentException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new AttachmentException(
                    ErrorCode.ATTACHMENT_NOT_FOUND,
                    "Attachment file not found at path: " + filePath
            );
        }
        return file;
    }

    public static String detectMimeType(File file) throws AttachmentException {
        try {
            String mimeType = Files.probeContentType(file.toPath());
            return mimeType != null ? mimeType : "application/octet-stream";

        } catch (IOException e) {
            throw new AttachmentException(
                    ErrorCode.ATTACHMENT_NOT_FOUND,
                    "Failed to detect MIME type for file: " + file.getName(),
                    e
            );
        }
    }

    public static String generateStoragePath(String accountId, String fileName) {
        return Path.of(ATTACHMENT_STORAGE_ROOT, accountId, fileName).toString();
    }

    public static File downloadAttachment(String accountId, String fileName, InputStream remoteStream)
            throws AttachmentException {

        String storagePath = generateStoragePath(accountId, fileName);
        File targetFile = new File(storagePath);

        try {
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                remoteStream.transferTo(outputStream);
            }

            return targetFile;

        } catch (IOException e) {
            throw new AttachmentException(
                    ErrorCode.ATTACHMENT_NOT_FOUND,
                    "Failed to download attachment: " + fileName,
                    e
            );
        }
    }
}