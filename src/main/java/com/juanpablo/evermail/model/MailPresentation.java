package com.juanpablo.evermail.model;

/** Sanitized display result, prepared off the FX thread. Deliberately no body in toString. */
public final class MailPresentation {
    private final MailContent content;
    private final String document;
    private final DisplayNode body;
    public record DisplayNode(String tag, String text, java.util.Map<String, String> attributes,
                              java.util.List<DisplayNode> children) { }
    public MailPresentation(MailContent content, String document) {
        this.content = content;
        this.document = document;
        this.body = document == null ? null : snapshot(org.jsoup.Jsoup.parse(document).body());
    }
    /** Immutable sanitized DOM snapshot, prepared on the backend for incremental image updates. */
    private static DisplayNode snapshot(org.jsoup.nodes.Node root) {
        var order = new java.util.ArrayList<org.jsoup.nodes.Node>();
        var pending = new java.util.ArrayDeque<org.jsoup.nodes.Node>();
        pending.add(root);
        while (!pending.isEmpty()) {
            var node = pending.removeLast();
            order.add(node);
            pending.addAll(node.childNodes());
        }
        var snapshots = new java.util.IdentityHashMap<org.jsoup.nodes.Node, DisplayNode>();
        for (int i = order.size() - 1; i >= 0; i--) {
            var node = order.get(i);
            snapshots.put(node, snapshotNode(node, snapshots));
        }
        return snapshots.get(root);
    }
    private static DisplayNode snapshotNode(org.jsoup.nodes.Node node,
            java.util.Map<org.jsoup.nodes.Node, DisplayNode> snapshots) {
        if (node instanceof org.jsoup.nodes.Element element) {
            java.util.Map<String, String> attributes = new java.util.LinkedHashMap<>();
            element.attributes().forEach(a -> attributes.put(a.getKey(), a.getValue()));
            return new DisplayNode(element.normalName(), null, java.util.Map.copyOf(attributes),
                    element.childNodes().stream().map(snapshots::get).toList());
        }
        String text = node instanceof org.jsoup.nodes.TextNode value ? value.getWholeText()
                : node instanceof org.jsoup.nodes.DataNode data ? data.getWholeData() : "";
        return new DisplayNode(null, text, java.util.Map.of(), java.util.List.of());
    }
    public DisplayNode getBody() { return body; }
    public MailContent getContent() { return content; }
    public String getDocument() { return document; }
}
