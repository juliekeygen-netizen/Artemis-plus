package com.limelight.quickmenu;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Versioned persisted tree for the customizable in-stream Quick Menu. */
public final class QuickMenuConfig {
    public static final int CURRENT_VERSION = 1;
    private static final String PREFS_NAME = "quick_menu_config";
    private static final String KEY_CONFIG = "config_v1";
    public static final int MAX_PAGE_DEPTH = 6;
    public static final int MAX_TOTAL_NODES = 128;
    private static final int MAX_TITLE_LENGTH = 48;

    public static final String TYPE_ACTION = "action";
    public static final String TYPE_PAGE = "page";

    public int version = CURRENT_VERSION;
    public final Page root;

    public QuickMenuConfig(Page root) {
        this.root = root == null ? defaultRoot() : root;
    }

    public static final class Page {
        public final String id;
        public String title;
        public final List<Node> items = new ArrayList<>();

        public Page(String id, String title) {
            this.id = normalizePageId(id);
            this.title = normalizeTitle(title, "Page");
        }

        public Page(String title) {
            this(UUID.randomUUID().toString(), title);
        }
    }

    public static final class Node {
        public final String type;
        public final String actionId;
        public final Page page;

        private Node(String type, String actionId, Page page) {
            this.type = type;
            this.actionId = actionId;
            this.page = page;
        }

        public static Node action(String actionId) {
            return new Node(TYPE_ACTION, actionId, null);
        }

        public static Node page(Page page) {
            return new Node(TYPE_PAGE, null, page);
        }

        public boolean isAction() {
            return TYPE_ACTION.equals(type);
        }

        public boolean isPage() {
            return TYPE_PAGE.equals(type) && page != null;
        }
    }

    public static QuickMenuConfig load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw;
        try {
            raw = prefs.getString(KEY_CONFIG, null);
        } catch (ClassCastException e) {
            return createDefault();
        }
        if (raw == null || raw.trim().isEmpty()) {
            return createDefault();
        }
        try {
            QuickMenuConfig parsed = fromJson(new JSONObject(raw));
            return parsed == null ? createDefault() : parsed;
        } catch (JSONException | RuntimeException e) {
            return createDefault();
        }
    }

    public static void save(Context context, QuickMenuConfig config) {
        if (context == null || config == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_CONFIG, config.toJson().toString()).apply();
    }

    public static void reset(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY_CONFIG).apply();
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("version", CURRENT_VERSION);
            object.put("root", pageToJson(root));
        } catch (JSONException ignored) {
            // JSONObject backed by in-memory values should not fail for these supported types.
        }
        return object;
    }

    public static QuickMenuConfig fromJson(JSONObject object) {
        if (object == null || object.optInt("version", 0) != CURRENT_VERSION) {
            return null;
        }
        Counter counter = new Counter();
        Page root = pageFromJson(object.optJSONObject("root"), 0, counter, true);
        if (root == null) return null;
        return new QuickMenuConfig(root);
    }

    public static QuickMenuConfig createDefault() {
        return new QuickMenuConfig(defaultRoot());
    }

    private static Page defaultRoot() {
        Page root = new Page("root", "Quick Menu");
        root.items.add(Node.action(StreamActionRegistry.DISCONNECT));
        root.items.add(Node.action(StreamActionRegistry.QUIT_SESSION));
        root.items.add(Node.action(StreamActionRegistry.UPLOAD_CLIPBOARD));
        root.items.add(Node.action(StreamActionRegistry.FETCH_CLIPBOARD));
        root.items.add(Node.action(StreamActionRegistry.SERVER_COMMANDS));
        root.items.add(Node.action(StreamActionRegistry.TOGGLE_KEYBOARD));
        root.items.add(Node.action(StreamActionRegistry.TOGGLE_ZOOM));
        root.items.add(Node.action(StreamActionRegistry.ROTATE_SCREEN));

        Page advanced = new Page("default-advanced", "Advanced");
        advanced.items.add(Node.action(StreamActionRegistry.SELECT_MOUSE_MODE));
        advanced.items.add(Node.action(StreamActionRegistry.TOGGLE_HUD));
        advanced.items.add(Node.action(StreamActionRegistry.TOGGLE_FLOATING_BUTTON));
        advanced.items.add(Node.action(StreamActionRegistry.TOGGLE_KEYBOARD_CONTROLLER));
        advanced.items.add(Node.action(StreamActionRegistry.TOGGLE_VIRTUAL_CONTROLLER));
        advanced.items.add(Node.action(StreamActionRegistry.TOGGLE_FULL_KEYBOARD));
        advanced.items.add(Node.action(StreamActionRegistry.TASK_MANAGER));
        advanced.items.add(Node.action(StreamActionRegistry.SEND_KEYS));
        advanced.items.add(Node.action(StreamActionRegistry.SWITCH_TOUCH_SENSITIVITY));
        advanced.items.add(Node.action(StreamActionRegistry.DEVICE_ACTIONS));
        root.items.add(Node.page(advanced));
        return root;
    }

    private static JSONObject pageToJson(Page page) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", page.id);
        object.put("title", page.title);
        JSONArray items = new JSONArray();
        for (Node node : page.items) {
            if (node == null) continue;
            JSONObject item = new JSONObject();
            if (node.isAction()) {
                item.put("type", TYPE_ACTION);
                item.put("actionId", node.actionId);
            } else if (node.isPage()) {
                item.put("type", TYPE_PAGE);
                item.put("page", pageToJson(node.page));
            } else {
                continue;
            }
            items.put(item);
        }
        object.put("items", items);
        return object;
    }

    private static Page pageFromJson(JSONObject object, int depth, Counter counter, boolean root) {
        if (object == null || depth > MAX_PAGE_DEPTH) return null;
        if (!root) {
            if (counter.nodes >= MAX_TOTAL_NODES) return null;
            counter.nodes++;
        }
        String fallbackTitle = root ? "Quick Menu" : "Page";
        Page page = new Page(root ? "root" : object.optString("id", null),
                normalizeTitle(object.optString("title", fallbackTitle), fallbackTitle));
        JSONArray items = object.optJSONArray("items");
        if (items == null) return page;
        for (int i = 0; i < items.length() && counter.nodes < MAX_TOTAL_NODES; i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String type = item.optString("type", "");
            if (TYPE_ACTION.equals(type)) {
                String actionId = item.optString("actionId", "");
                // Persisted IDs from a newer Artemis Plus build must remain opaque rather than
                // being destroyed by an older build. Runtime/editor lookup already treats unknown
                // IDs as unavailable/inert. Interactive addAction() remains registry-gated below.
                if (!actionId.trim().isEmpty()) {
                    page.items.add(Node.action(actionId));
                    counter.nodes++;
                }
            } else if (TYPE_PAGE.equals(type) && depth < MAX_PAGE_DEPTH) {
                Page child = pageFromJson(item.optJSONObject("page"), depth + 1, counter, false);
                if (child != null) {
                    page.items.add(Node.page(child));
                }
            }
        }
        return page;
    }

    public static boolean move(Page page, int from, int to) {
        if (page == null || from < 0 || to < 0 || from >= page.items.size() || to >= page.items.size() || from == to) {
            return false;
        }
        Node node = page.items.remove(from);
        page.items.add(to, node);
        return true;
    }

    public static boolean remove(Page page, int position) {
        if (page == null || position < 0 || position >= page.items.size()) return false;
        page.items.remove(position);
        return true;
    }

    public static boolean addAction(Page page, String actionId) {
        if (page == null || !StreamActionRegistry.contains(actionId) || countNodes(page) >= MAX_TOTAL_NODES) return false;
        page.items.add(Node.action(actionId));
        return true;
    }

    public static Page addPage(Page parent, String title) {
        if (parent == null || countNodes(parent) >= MAX_TOTAL_NODES) return null;
        Page child = new Page(title);
        parent.items.add(Node.page(child));
        return child;
    }

    public static void rename(Page page, String title) {
        if (page != null) page.title = normalizeTitle(title, page.title);
    }

    public static int countNodes(Page page) {
        if (page == null) return 0;
        int total = 0;
        for (Node node : page.items) {
            total++;
            if (node != null && node.isPage()) total += countNodes(node.page);
        }
        return total;
    }

    private static String normalizePageId(String id) {
        String value = id == null ? "" : id.trim();
        return value.isEmpty() ? UUID.randomUUID().toString() : value;
    }

    private static String normalizeTitle(String title, String fallback) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) value = fallback == null || fallback.trim().isEmpty() ? "Page" : fallback.trim();
        if (value.length() > MAX_TITLE_LENGTH) value = value.substring(0, MAX_TITLE_LENGTH);
        return value;
    }

    private static final class Counter {
        int nodes;
    }
}
