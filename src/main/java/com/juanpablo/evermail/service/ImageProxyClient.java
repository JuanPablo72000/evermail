package com.juanpablo.evermail.service;

import com.google.gson.Gson;
import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.util.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Only the configured proxy receives requests or the function key. Never follows redirects. */
public final class ImageProxyClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final URI endpoint;
    private final String key;

    public ImageProxyClient(EnvConfig config) {
        String url = config.imageProxyUrl(), secret = config.imageProxyKey();
        URI candidate = null;
        if (HtmlMail.safeImageUrl(url)) {
            URI uri = URI.create(url);
            if (uri.getRawQuery() == null && uri.getRawFragment() == null) candidate = uri;
        }
        endpoint = candidate;
        key = secret == null ? "" : secret.strip();
    }

    // Local fake server for transport tests; production configuration always requires HTTPS.
    ImageProxyClient(URI endpoint, String key) { this.endpoint = endpoint; this.key = key; }

    public boolean configured() { return endpoint != null && !key.isBlank(); }

    public String download(String url, Deadline deadline) {
        if (!configured() || !HtmlMail.safeImageUrl(url)) return null;
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            long millis = Math.min(9000, deadline.remainingMillis() - 500L);
            if (millis <= 0) return null;
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofMillis(millis))
                    .header("x-functions-key", key).header("Content-Type", "application/json")
                    .header("Accept", "image/png,image/jpeg,image/gif")
                    .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(Map.of("url", url)))).build();
            pending = HTTP.sendAsync(request, info -> new LimitedBody());
            HttpResponse<byte[]> response = pending.get(millis, TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) return null;
            String type = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (!Set.of("image/png", "image/jpeg", "image/gif").contains(type)) return null;
            return InlineImages.dataUri(response.body());
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception unavailable) {
            // Do not log the URL, key, request, or response body.
            return null;
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long bytes;
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) bytes += buffer.remaining();
            if (bytes > AppConstants.MAX_INLINE_IMAGE_BYTES) {
                subscription.cancel();
                delegate.onError(new IOException("Image exceeds limit"));
            } else delegate.onNext(buffers);
        }
        public void onError(Throwable error) { delegate.onError(error); }
        public void onComplete() { delegate.onComplete(); }
    }
}
