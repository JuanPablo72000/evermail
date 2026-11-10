package com.juanpablo.evermail.service;

import com.google.gson.*;
import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.model.AvatarPhoto;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** Single-owner People directory. Tokens go only to people.googleapis.com, never to photo hosts. */
public final class GooglePeopleClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final int MAX_DIRECTORY_BYTES = 2 * 1024 * 1024, MAX_PHOTO_BYTES = 1024 * 1024;
    private static final int MAX_PAGES = 10, MAX_ADDRESSES = 10_000;
    record Response(int status, byte[] body) { }
    @FunctionalInterface interface Transport {
        Response get(URI uri, String bearer, int maximum, Deadline deadline) throws Exception;
    }
    private final Transport transport;
    private final Clock clock;
    private final Directory contacts = new Directory("people/me/connections?personFields=emailAddresses,photos", "connections");
    private final Directory others = new Directory("otherContacts?readMask=emailAddresses,photos"
            + "&sources=READ_SOURCE_TYPE_CONTACT&sources=READ_SOURCE_TYPE_PROFILE", "otherContacts");

    public GooglePeopleClient() { this(GooglePeopleClient::request, Clock.systemUTC()); }
    GooglePeopleClient(Transport transport, Clock clock) { this.transport = transport; this.clock = clock; }
    public void reset() { contacts.clear(); others.clear(); }

    /** Safe diagnostic metadata: no addresses, photo URLs, tokens or raw provider messages. */
    public record DirectoryReport(String source, int httpStatus, int people, int addressesWithPhotos,
                                  AvatarPhoto.Status status, String issue) { }
    public List<DirectoryReport> diagnose(String bearer, Deadline deadline) throws Exception {
        List<DirectoryReport> reports = new ArrayList<>();
        for (Directory directory : List.of(contacts, others)) {
            directory.load(bearer, deadline);
            reports.add(new DirectoryReport(directory.field, directory.httpStatus, directory.people,
                    directory.photos.size(), directory.status, directory.issue));
        }
        return List.copyOf(reports);
    }
    public AvatarPhoto.Status diagnoseSample(String bearer, Deadline deadline) throws Exception {
        for (Directory directory : List.of(contacts, others)) {
            if (!directory.photos.isEmpty()) return lookup(bearer, directory.photos.keySet().iterator().next(), deadline).status();
        }
        return AvatarPhoto.Status.ABSENT;
    }

    public AvatarPhoto lookup(String bearer, String email, Deadline deadline) throws Exception {
        String address = AvatarPhoto.address(email);
        boolean permission = false, unavailable = false;
        for (Directory directory : List.of(contacts, others)) {
            directory.load(bearer, deadline);
            permission |= directory.status == AvatarPhoto.Status.PERMISSION_REQUIRED;
            unavailable |= directory.status == AvatarPhoto.Status.UNAVAILABLE;
            URI photo = directory.photos.get(address);
            if (photo != null) {
                try {
                    Response response = transport.get(photo, null, MAX_PHOTO_BYTES, deadline);
                    if (response.status == 200) {
                        byte[] png = thumbnail(response.body);
                        if (png != null) return new AvatarPhoto(png, AvatarPhoto.Status.AVAILABLE);
                    }
                    unavailable = true;
                } catch (InterruptedException interrupted) { throw interrupted; }
                catch (Exception failure) { deadline.check(); unavailable = true; }
            }
        }
        return AvatarPhoto.empty(permission ? AvatarPhoto.Status.PERMISSION_REQUIRED
                : unavailable ? AvatarPhoto.Status.UNAVAILABLE : AvatarPhoto.Status.ABSENT);
    }

    private final class Directory {
        final String path, field;
        final Map<String, URI> photos = new HashMap<>();
        Instant expires = Instant.EPOCH;
        AvatarPhoto.Status status = AvatarPhoto.Status.ABSENT;
        int httpStatus, people;
        String issue = "NONE";
        Directory(String path, String field) { this.path = path; this.field = field; }
        void clear() { photos.clear(); expires = Instant.EPOCH; status = AvatarPhoto.Status.ABSENT; }
        void load(String bearer, Deadline deadline) throws Exception {
            if (clock.instant().isBefore(expires)) return;
            photos.clear();
            httpStatus = 0; people = 0; issue = "NETWORK_OR_RESPONSE";
            status = AvatarPhoto.Status.UNAVAILABLE;
            expires = clock.instant().plusSeconds(60); // No tight retries on rate limiting/network failure.
            String pageToken = "";
            try {
                for (int page = 0; page < MAX_PAGES; page++) {
                    deadline.check();
                    URI uri = URI.create("https://people.googleapis.com/v1/" + path + "&pageSize=1000"
                            + (pageToken.isEmpty() ? "" : "&pageToken=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8)));
                    Response response = transport.get(uri, bearer, MAX_DIRECTORY_BYTES, deadline);
                    httpStatus = response.status;
                    if (response.status != 200) {
                        String error = new String(response.body, StandardCharsets.UTF_8);
                        issue = error.contains("SERVICE_DISABLED") ? "API_DISABLED" : "HTTP_ERROR";
                        if (response.status == 401 || response.status == 403
                                && (error.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT") || error.contains("insufficientPermissions"))) {
                            status = AvatarPhoto.Status.PERMISSION_REQUIRED;
                            issue = "PERMISSION_REQUIRED";
                            expires = clock.instant().plusSeconds(600);
                        }
                        return;
                    }
                    JsonObject object = JsonParser.parseString(new String(response.body, StandardCharsets.UTF_8)).getAsJsonObject();
                    JsonArray people = object.getAsJsonArray(field);
                    if (people != null) for (JsonElement element : people) {
                        this.people++;
                        if (!element.isJsonObject()) continue;
                        addPerson(element.getAsJsonObject());
                        if (photos.size() >= MAX_ADDRESSES) return;
                    }
                    pageToken = string(object, "nextPageToken");
                    if (pageToken.isEmpty()) {
                        status = AvatarPhoto.Status.ABSENT;
                        issue = "NONE";
                        expires = clock.instant().plusSeconds(3600);
                        return;
                    }
                    if (pageToken.length() > 4096) return;
                }
                expires = clock.instant().plusSeconds(3600); // Bounded partial directory; do not call it complete.
            } catch (InterruptedException interrupted) { throw interrupted; }
            catch (Exception failure) { deadline.check(); }
        }
        void addPerson(JsonObject person) {
            JsonArray images = person.getAsJsonArray("photos"), emails = person.getAsJsonArray("emailAddresses");
            if (images == null || emails == null) return;
            URI photo = null;
            for (JsonElement element : images) {
                if (!element.isJsonObject()) continue;
                JsonObject image = element.getAsJsonObject();
                if (image.has("default") && image.get("default").getAsBoolean()) continue;
                URI candidate = photoUri(string(image, "url"));
                if (candidate != null) { photo = candidate; break; }
            }
            if (photo == null) return;
            for (JsonElement element : emails) {
                if (!element.isJsonObject() || photos.size() >= MAX_ADDRESSES) continue;
                String email = AvatarPhoto.address(string(element.getAsJsonObject(), "value"));
                if (email.contains("@") && email.length() <= 320) photos.putIfAbsent(email, photo);
            }
        }
    }

    static URI photoUri(String value) {
        if (value.length() > 4096) return null;
        try {
            URI uri = URI.create(value);
            String host = Objects.toString(uri.getHost(), "").toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || uri.getFragment() != null || uri.getPort() != -1 && uri.getPort() != 443) return null;
            return host.equals("googleusercontent.com") || host.endsWith(".googleusercontent.com") ? uri : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }

    static byte[] thumbnail(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_PHOTO_BYTES) return null;
        // A memory-only stream prevents ImageIO from creating decrypted temporary files.
        try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!Set.of("png", "jpeg", "jpg", "gif").contains(reader.getFormatName().toLowerCase(Locale.ROOT))) return null;
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 2048 || height > 2048 || (long) width * height > 4_000_000) return null;
                BufferedImage original = reader.read(0), small = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
                var graphics = small.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int side = Math.min(width, height), x = (width - side) / 2, y = (height - side) / 2;
                    graphics.drawImage(original, 0, 0, 96, 96, x, y, x + side, y + side, null);
                } finally { graphics.dispose(); original.flush(); }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (var imageOutput = new javax.imageio.stream.MemoryCacheImageOutputStream(output)) {
                    ImageIO.write(small, "png", imageOutput);
                } finally { small.flush(); }
                return output.size() <= 65_536 ? output.toByteArray() : null;
            } finally { reader.dispose(); }
        }
    }

    private static Response request(URI uri, String bearer, int maximum, Deadline deadline) throws Exception {
        if (bearer != null && (!"https".equals(uri.getScheme()) || !"people.googleapis.com".equals(uri.getHost())))
            throw new IOException("Invalid credential destination");
        if (bearer == null && photoUri(uri.toString()) == null) throw new IOException("Invalid photo destination");
        long millis = Math.min(8000, deadline.remainingMillis());
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(millis)).GET();
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        CompletableFuture<HttpResponse<byte[]>> pending = HTTP.sendAsync(builder.build(), ignored -> new LimitedBody(maximum));
        try {
            HttpResponse<byte[]> response = pending.get(millis, TimeUnit.MILLISECONDS);
            return new Response(response.statusCode(), response.body());
        } finally { if (!pending.isDone()) pending.cancel(true); }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int maximum;
        private Flow.Subscription subscription;
        private long received;
        LimitedBody(int maximum) { this.maximum = maximum; }
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) received += buffer.remaining();
            if (received > maximum) { subscription.cancel(); delegate.onError(new IOException("Response exceeds limit")); }
            else delegate.onNext(buffers);
        }
        public void onError(Throwable error) { delegate.onError(error); }
        public void onComplete() { delegate.onComplete(); }
    }
}
