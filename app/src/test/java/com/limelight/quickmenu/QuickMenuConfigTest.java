package com.limelight.quickmenu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class QuickMenuConfigTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        QuickMenuConfig.reset(context);
    }

    @Test
    public void defaultTreePreservesLegacyTopLevelAndAdvancedLayout() {
        QuickMenuConfig config = QuickMenuConfig.createDefault();
        assertEquals("Quick Menu", config.root.title);
        assertEquals(9, config.root.items.size());
        assertEquals(StreamActionRegistry.DISCONNECT, config.root.items.get(0).actionId);
        assertEquals(StreamActionRegistry.ROTATE_SCREEN, config.root.items.get(7).actionId);
        assertTrue(config.root.items.get(8).isPage());

        QuickMenuConfig.Page advanced = config.root.items.get(8).page;
        assertEquals("Advanced", advanced.title);
        assertEquals(10, advanced.items.size());
        assertEquals(StreamActionRegistry.SELECT_MOUSE_MODE, advanced.items.get(0).actionId);
        assertEquals(StreamActionRegistry.DEVICE_ACTIONS,
                advanced.items.get(advanced.items.size() - 1).actionId);
    }

    @Test
    public void nestedTreeRoundTripsThroughPreferences() {
        QuickMenuConfig config = QuickMenuConfig.createDefault();
        QuickMenuConfig.Page custom = QuickMenuConfig.addPage(config.root, "My tools");
        assertNotNull(custom);
        assertTrue(QuickMenuConfig.addAction(custom, StreamActionRegistry.TASK_MANAGER));
        QuickMenuConfig.rename(config.root, "Stream controls");
        QuickMenuConfig.save(context, config);

        QuickMenuConfig loaded = QuickMenuConfig.load(context);
        assertEquals("Stream controls", loaded.root.title);
        QuickMenuConfig.Node last = loaded.root.items.get(loaded.root.items.size() - 1);
        assertTrue(last.isPage());
        assertEquals("My tools", last.page.title);
        assertEquals(StreamActionRegistry.TASK_MANAGER, last.page.items.get(0).actionId);
    }

    @Test
    public void malformedStoredJsonFallsBackToDefault() {
        context.getSharedPreferences("quick_menu_config", Context.MODE_PRIVATE)
                .edit().putString("config_v1", "{definitely-not-json").commit();
        QuickMenuConfig loaded = QuickMenuConfig.load(context);
        assertEquals("Quick Menu", loaded.root.title);
        assertEquals(StreamActionRegistry.DISCONNECT, loaded.root.items.get(0).actionId);
    }

    @Test
    public void unknownActionIsSanitizedWithoutDiscardingValidSiblings() throws Exception {
        JSONObject root = new JSONObject();
        root.put("id", "root");
        root.put("title", "Quick Menu");
        JSONArray items = new JSONArray();
        items.put(new JSONObject().put("type", "action").put("actionId", "removed.future.action"));
        items.put(new JSONObject().put("type", "action").put("actionId", StreamActionRegistry.TASK_MANAGER));
        root.put("items", items);
        JSONObject object = new JSONObject().put("version", QuickMenuConfig.CURRENT_VERSION).put("root", root);

        QuickMenuConfig parsed = QuickMenuConfig.fromJson(object);
        assertNotNull(parsed);
        assertEquals(1, parsed.root.items.size());
        assertEquals(StreamActionRegistry.TASK_MANAGER, parsed.root.items.get(0).actionId);
    }

    @Test
    public void unsupportedVersionIsRejected() throws Exception {
        JSONObject object = new JSONObject()
                .put("version", QuickMenuConfig.CURRENT_VERSION + 1)
                .put("root", new JSONObject().put("title", "Future").put("items", new JSONArray()));
        assertNull(QuickMenuConfig.fromJson(object));
    }

    @Test
    public void moveRemoveAndDuplicateActionsAreDeterministic() {
        QuickMenuConfig.Page page = new QuickMenuConfig.Page("Test");
        assertTrue(QuickMenuConfig.addAction(page, StreamActionRegistry.TASK_MANAGER));
        assertTrue(QuickMenuConfig.addAction(page, StreamActionRegistry.TASK_MANAGER));
        assertTrue(QuickMenuConfig.addAction(page, StreamActionRegistry.SEND_KEYS));
        assertTrue(QuickMenuConfig.move(page, 2, 0));
        assertEquals(StreamActionRegistry.SEND_KEYS, page.items.get(0).actionId);
        assertTrue(QuickMenuConfig.remove(page, 1));
        assertEquals(2, page.items.size());
        assertFalse(QuickMenuConfig.move(page, -1, 0));
    }

    @Test
    public void parserNeverExceedsGlobalNodeLimit() throws Exception {
        JSONObject root = new JSONObject();
        root.put("id", "root");
        root.put("title", "Oversized");
        JSONArray items = new JSONArray();
        for (int i = 0; i < QuickMenuConfig.MAX_TOTAL_NODES + 40; i++) {
            JSONObject page = new JSONObject();
            page.put("id", "page-" + i);
            page.put("title", "Page " + i);
            JSONArray childItems = new JSONArray();
            childItems.put(new JSONObject()
                    .put("type", "action")
                    .put("actionId", StreamActionRegistry.TASK_MANAGER));
            page.put("items", childItems);
            items.put(new JSONObject().put("type", "page").put("page", page));
        }
        root.put("items", items);
        JSONObject object = new JSONObject()
                .put("version", QuickMenuConfig.CURRENT_VERSION)
                .put("root", root);

        QuickMenuConfig parsed = QuickMenuConfig.fromJson(object);
        assertNotNull(parsed);
        assertTrue(QuickMenuConfig.countNodes(parsed.root) <= QuickMenuConfig.MAX_TOTAL_NODES);
    }

    @Test
    public void parserCapsNestedPagesAtMaximumDepth() throws Exception {
        JSONObject root = new JSONObject()
                .put("id", "root")
                .put("title", "Root");
        JSONObject current = root;
        for (int depth = 1; depth <= QuickMenuConfig.MAX_PAGE_DEPTH + 3; depth++) {
            JSONObject child = new JSONObject()
                    .put("id", "page-" + depth)
                    .put("title", "Page " + depth);
            current.put("items", new JSONArray().put(new JSONObject()
                    .put("type", QuickMenuConfig.TYPE_PAGE)
                    .put("page", child)));
            current = child;
        }
        current.put("items", new JSONArray().put(new JSONObject()
                .put("type", QuickMenuConfig.TYPE_ACTION)
                .put("actionId", StreamActionRegistry.TASK_MANAGER)));

        QuickMenuConfig parsed = QuickMenuConfig.fromJson(new JSONObject()
                .put("version", QuickMenuConfig.CURRENT_VERSION)
                .put("root", root));

        assertNotNull(parsed);
        assertEquals(QuickMenuConfig.MAX_PAGE_DEPTH, deepestPageDepth(parsed.root));
        assertEquals(QuickMenuConfig.MAX_PAGE_DEPTH, QuickMenuConfig.countNodes(parsed.root));
    }

    @Test
    public void registryIdsAreUniqueAndResolvable() {
        Set<String> ids = new HashSet<>();
        for (StreamActionRegistry.ActionDefinition action : StreamActionRegistry.getAll()) {
            assertTrue("Duplicate registry ID: " + action.id, ids.add(action.id));
            assertNotNull(StreamActionRegistry.find(action.id));
            assertFalse(action.label.trim().isEmpty());
            assertFalse(action.category.trim().isEmpty());
        }
    }

    private static int deepestPageDepth(QuickMenuConfig.Page page) {
        int depth = 0;
        for (QuickMenuConfig.Node node : page.items) {
            if (node != null && node.isPage()) {
                depth = Math.max(depth, 1 + deepestPageDepth(node.page));
            }
        }
        return depth;
    }
}
