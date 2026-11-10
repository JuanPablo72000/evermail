package com.juanpablo.evermail.model;

import java.util.Locale;

/** Immutable, bounded PNG thumbnail. No remote URL or credential crosses the UI boundary. */
public record AvatarPhoto(byte[] png, Status status) {
    public enum Status { AVAILABLE, ABSENT, PERMISSION_REQUIRED, UNAVAILABLE }
    public AvatarPhoto {
        png = png == null ? new byte[0] : png.clone();
        if (png.length > 65_536) throw new IllegalArgumentException("Avatar exceeds limit");
    }
    @Override public byte[] png() { return png.clone(); }
    public static AvatarPhoto empty(Status status) { return new AvatarPhoto(null, status); }
    public static String address(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
    @Override public String toString() { return "AvatarPhoto[status=" + status + "]"; }
}
