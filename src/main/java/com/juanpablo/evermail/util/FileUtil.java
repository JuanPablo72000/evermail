package com.juanpablo.evermail.util;

import com.juanpablo.evermail.config.AppConstants;
import com.juanpablo.evermail.exception.AttachmentException;
import com.juanpablo.evermail.exception.ErrorCode;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Filesystem helper for attachments. Fully static — holds no state of its
 * own, and stays blind to the originating protocol (IMAP/Jakarta Mail):
 * it only ever receives an InputStream, never opens a mail session itself.
 */
public final class FileUtil {

    // Attachments are stored alongside the SQLite database, under an
    // "attachments" subfolder — reuses the same OS-appropriate base
    // directory already resolved for AppConstants.DB_PATH, instead of
    // introducing a second, independent storage root.
    private static final String ATTACHMENTS_SUBFOLDER = "attachments";

    private FileUtil() {
        // Prevents instantiation — this class only holds static utility methods.
    }

    public static void validateSize(long sizeBytes, long maxBytes) throws AttachmentException {
        if (sizeBytes > maxBytes) {
            throw new AttachmentException(
                    ErrorCode.ATTACHMENT_TOO_LARGE,
                    "Attachment size " + sizeBytes + " bytes exceeds the maximum of " + maxBytes + " bytes."
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
            throw new AttachmentException(ErrorCode.ATTACHMENT_NOT_FOUND, "No file found at path: " + filePath);
        }
        return file;
    }

    public static String detectMimeType(File file) throws AttachmentException {
        try {
            String mimeType = Files.probeContentType(file.toPath());
            return mimeType != null ? mimeType : "application/octet-stream";
        } catch (IOException e) {
            throw new AttachmentException(ErrorCode.ATTACHMENT_NOT_FOUND, "Failed to detect MIME type for: " + file.getPath(), e);
        }
    }

    public static String generateStoragePath(String accountId, String fileName) {
        Path dbParent = Paths.get(AppConstants.DB_PATH).getParent();
        Path attachmentsDir = dbParent.resolve(ATTACHMENTS_SUBFOLDER).resolve(accountId);
        return attachmentsDir.resolve(fileName).toString();
    }

    /**
     * Downloads an attachment on demand: reads the full remote stream into
     * memory, encrypts it via SecurityUtil, and writes the ciphertext to
     * disk. Content is never persisted unencrypted, matching the at-rest
     * encryption standard already applied to tokens and mail bodies.
     * <p>
     * Both {@code key} and {@code securityUtil} are received as parameters
     * (not resolved internally) so this class stays fully static and holds
     * no reference to the stateful keyring dependency.
     */
    public static File downloadAttachment(String accountId, String fileName, InputStream remoteStream,
                                          SecretKey key, SecurityUtil securityUtil) throws AttachmentException {
        try {
            String storagePath = generateStoragePath(accountId, fileName);
            File targetFile = new File(storagePath);

            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                throw new AttachmentException(
                        ErrorCode.ATTACHMENT_NOT_FOUND,
                        "Failed to create attachment storage directory: " + parentDir.getPath()
                );
            }

            byte[] rawBytes = remoteStream.readAllBytes();
            byte[] encryptedBytes = securityUtil.encrypt(rawBytes, key);

            Files.write(targetFile.toPath(), encryptedBytes);

            return targetFile;

        } catch (IOException e) {
            throw new AttachmentException(ErrorCode.ATTACHMENT_NOT_FOUND, "Failed to download attachment: " + fileName, e);
        } catch (Exception e) {
            throw new AttachmentException(ErrorCode.ATTACHMENT_NOT_FOUND, "Failed to encrypt attachment: " + fileName, e);
        }
    }
}