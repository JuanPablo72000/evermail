package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.util.HtmlMail;
import com.juanpablo.evermail.config.AppConstants;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class MailPresentationService {
    private final ImageProxyClient images;
    public MailPresentationService() { this(new ImageProxyClient(new com.juanpablo.evermail.config.EnvConfig())); }
    public MailPresentationService(ImageProxyClient images) { this.images = images; }
    public MailPresentation prepareWithoutImages(MailContent content, Deadline deadline) throws EvermailException {
        return prepare(content, deadline, false);
    }
    public MailPresentation prepare(MailContent content, Deadline deadline) throws EvermailException {
        return prepare(content, deadline, true);
    }
    private MailPresentation prepare(MailContent content, Deadline deadline, boolean download) throws EvermailException {
        deadline.check();
        if (content.getHtml() == null) return new MailPresentation(content, null);
        try {
            // Bound all image downloads together and leave time to render the text on failure.
            long budget = Math.min(15000, deadline.remainingMillis() - 1000L);
            Deadline imageDeadline = Deadline.after(java.time.Duration.ofMillis(Math.max(1, budget)));
            Map<String, String> downloaded = download && budget > 0 && images.configured()
                    ? downloadImages(content.getHtml(), imageDeadline, deadline) : Map.of();
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

    private Map<String, String> downloadImages(String html, Deadline imageDeadline, Deadline deadline) throws Exception {
        Set<String> urls = new LinkedHashSet<>();
        // Discover only resources accepted by the same sanitizer, including CSS backgrounds.
        HtmlMail.sanitize(html, cid -> null, url -> {
            if (urls.size() < 24) urls.add(url);
            return null;
        }, false, deadline);
        if (urls.isEmpty()) return Map.of();
        ExecutorService pool = Executors.newFixedThreadPool(3,
                Thread.ofPlatform().daemon().name("evermail-images-", 0).factory());
        Map<String, Future<String>> pending = new LinkedHashMap<>();
        Map<String, String> result = new HashMap<>();
        AtomicInteger retainedChars = new AtomicInteger();
        int limit = AppConstants.MAX_INLINE_TOTAL_BYTES * 4 / 3;
        try {
            for (String url : urls) pending.put(url, pool.submit(() -> {
                if (retainedChars.get() >= limit) return null;
                String data = images.download(url, imageDeadline);
                if (data == null) return null;
                int previous;
                do {
                    previous = retainedChars.get();
                    if (data.length() > limit - previous) return null;
                } while (!retainedChars.compareAndSet(previous, previous + data.length()));
                return data;
            }));
            for (var entry : pending.entrySet()) {
                deadline.check();
                String data = entry.getValue().get(imageDeadline.remainingMillis(), TimeUnit.MILLISECONDS);
                if (data != null) result.put(entry.getKey(), data);
            }
        } catch (TimeoutException expired) {
            // Already downloaded images remain useful; the body never depends on this stage.
        } catch (com.juanpablo.evermail.exception.SessionException expired) {
            deadline.check(); // Preserve cancellation/outer timeout, tolerate only the image budget expiring.
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            deadline.check();
        } finally {
            pending.values().forEach(task -> task.cancel(true));
            pool.shutdownNow();
        }
        return result;
    }
}
