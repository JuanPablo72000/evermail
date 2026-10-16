package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.util.HtmlMail;
import com.juanpablo.evermail.config.AppConstants;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class MailPresentationService implements AutoCloseable {
    private final ImageProxyClient images;
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(24), Thread.ofPlatform().daemon().name("evermail-images-", 0).factory());
    public MailPresentationService() { this(new ImageProxyClient(new com.juanpablo.evermail.config.EnvConfig())); }
    public MailPresentationService(ImageProxyClient images) {
        this.images = images;
        pool.allowCoreThreadTimeOut(true);
    }
    @Override public void close() {
        pool.shutdownNow();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) throw new IllegalStateException("Image workers did not stop");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping image workers", interrupted);
        }
    }
    public MailPresentation prepareWithoutImages(MailContent content, Deadline deadline) throws EvermailException {
        return prepare(content, deadline, false);
    }
    public MailPresentation prepare(MailContent content, Deadline deadline) throws EvermailException {
        return prepare(content, deadline, true);
    }
    public MailPresentation prepare(MailContent content, Deadline deadline,
                                    java.util.function.Consumer<MailPresentation> progress) throws EvermailException {
        return prepare(content, deadline, true, progress);
    }
    private MailPresentation prepare(MailContent content, Deadline deadline, boolean download) throws EvermailException {
        return prepare(content, deadline, download, ignored -> {});
    }
    private MailPresentation prepare(MailContent content, Deadline deadline, boolean download,
                                     java.util.function.Consumer<MailPresentation> progress) throws EvermailException {
        deadline.check();
        if (content.getHtml() == null) return new MailPresentation(content, null);
        try {
            // Bound all image downloads together and leave time to render the text on failure.
            long budget = Math.min(15000, deadline.remainingMillis() - 1000L);
            Deadline imageDeadline = Deadline.after(java.time.Duration.ofMillis(Math.max(1, budget)));
            Map<String, String> downloaded = download && budget > 0 && images.configured()
                    ? downloadImages(content.getHtml(), imageDeadline, deadline, partial -> {
                        try { progress.accept(render(content, partial, deadline)); }
                        catch (EvermailException failure) { throw new CompletionException(failure); }
                    }) : Map.of();
            return render(content, downloaded, deadline);
        } catch (EvermailException e) { throw e; }
        catch (Exception e) {
            throw new MailFetchException(ErrorCode.IMAP_FETCH_FAILED, "Cannot prepare message for display", e);
        }
    }

    private MailPresentation render(MailContent content, Map<String, String> downloaded, Deadline deadline) throws EvermailException {
        try {
            var safe = HtmlMail.sanitize(content.getHtml(), cid -> null,
                    downloaded::get,
                    false, deadline);
            deadline.check();
            var displayed = new MailContent(content.getMailId(), content.getPlainText(), content.getHtml(),
                    safe.blockedRemoteImages() || (content.isBlockedRemoteImages() && content.isLegacyImageReferences()),
                    content.isLegacyTextOnly(), content.getRecipients());
            return new MailPresentation(displayed, HtmlMail.document(safe.html()));
        } catch (EvermailException e) { throw e; }
        catch (Exception e) {
            throw new MailFetchException(ErrorCode.IMAP_FETCH_FAILED, "Cannot prepare message for display", e);
        }
    }

    private Map<String, String> downloadImages(String html, Deadline imageDeadline, Deadline deadline,
                                              java.util.function.Consumer<Map<String, String>> progress) throws Exception {
        Set<String> urls = new LinkedHashSet<>();
        // Discover only resources accepted by the same sanitizer, including CSS backgrounds.
        HtmlMail.sanitize(html, cid -> null, url -> {
            if (urls.size() < 24) urls.add(url);
            return null;
        }, false, deadline);
        if (urls.isEmpty()) return Map.of();
        CompletionService<Map.Entry<String, String>> completed = new ExecutorCompletionService<>(pool);
        List<Future<Map.Entry<String, String>>> pending = new ArrayList<>();
        Map<String, String> result = new HashMap<>();
        AtomicInteger retainedChars = new AtomicInteger();
        int limit = AppConstants.MAX_INLINE_TOTAL_BYTES * 4 / 3;
        int published = 0;
        long lastPublished = System.nanoTime();
        try {
            for (String url : urls) pending.add(completed.submit(() -> {
                if (retainedChars.get() >= limit) return Map.entry(url, "");
                String data = images.download(url, imageDeadline);
                if (data == null) return Map.entry(url, "");
                int previous;
                do {
                    previous = retainedChars.get();
                    if (data.length() > limit - previous) return Map.entry(url, "");
                } while (!retainedChars.compareAndSet(previous, previous + data.length()));
                return Map.entry(url, data);
            }));
            for (int i = 0; i < pending.size();) {
                deadline.check();
                Future<Map.Entry<String, String>> ready = completed.poll(Math.min(150, imageDeadline.remainingMillis()), TimeUnit.MILLISECONDS);
                if (ready == null) {
                    if (result.size() > published) {
                        progress.accept(Map.copyOf(result));
                        published = result.size(); lastPublished = System.nanoTime();
                    }
                    continue;
                }
                i++;
                try {
                    var entry = ready.get();
                    if (!entry.getValue().isEmpty()) result.put(entry.getKey(), entry.getValue());
                } catch (ExecutionException failedImage) {
                    deadline.check(); // One bad resource must not discard other completed images.
                }
                if (result.size() > published && (result.size() - published >= 3
                        || System.nanoTime() - lastPublished >= TimeUnit.MILLISECONDS.toNanos(150))) {
                    progress.accept(Map.copyOf(result));
                    published = result.size();
                    lastPublished = System.nanoTime();
                }
            }
        } catch (RejectedExecutionException busy) {
            // Cancelled requests still unwinding may temporarily occupy the shared bounded pool.
        } catch (com.juanpablo.evermail.exception.SessionException expired) {
            deadline.check(); // Preserve cancellation/outer timeout, tolerate only the image budget expiring.
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            deadline.check();
        } finally {
            pending.forEach(task -> task.cancel(true));
            pool.purge();
        }
        return result;
    }
}
