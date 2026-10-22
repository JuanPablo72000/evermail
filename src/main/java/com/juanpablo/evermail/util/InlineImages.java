package com.juanpablo.evermail.util;

import com.juanpablo.evermail.config.AppConstants;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Set;
import javax.imageio.ImageIO;

/** Accept only bounded raster images, never SVG, file paths, or remote URLs. */
public final class InlineImages {
    private InlineImages() { }

    public static String dataUri(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > AppConstants.MAX_INLINE_IMAGE_BYTES) return null;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(input, false, true);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg", "gif").contains(format)) return null;
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 8_000_000) return null;
                if (format.equals("gif")) {
                    int frames = reader.getNumImages(true);
                    if (frames > 32 || (long) frames * width * height > 8_000_000) return null;
                }
                return "data:image/" + (format.equals("jpg") ? "jpeg" : format) + ";base64," + Base64.getEncoder().encodeToString(bytes);
            } finally { reader.dispose(); }
        } catch (Exception ignored) { return null; }
    }

    public static String validateDataUri(String uri) {
        if (uri == null || uri.length() > AppConstants.MAX_INLINE_IMAGE_BYTES * 4 / 3 + 100
                || !uri.matches("(?s)data:image/(png|jpeg|gif);base64,[A-Za-z0-9+/=]+")) return null;
        try { return dataUri(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1))); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
