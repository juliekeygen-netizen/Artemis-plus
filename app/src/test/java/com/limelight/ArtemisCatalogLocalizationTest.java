package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.quickmenu.StreamActionRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class ArtemisCatalogLocalizationTest {
    private static final String[] ARTEMIS_ACTION_IDS = {
            "soft_keyboard", "full_keyboard", "rotate_screen", "quick_menu",
            "toggle_hud", "toggle_stats_overlay", "toggle_floating_menu",
            "touch_sensitivity", "send_clipboard", "fetch_clipboard", "mouse_mode",
            "toggle_zoom", "toggle_virtual_controller", "toggle_keyboard_controller"
    };

    private static final String[] QUICK_MENU_ACTION_IDS = {
            "session.disconnect", "session.quit", "clipboard.upload", "clipboard.fetch",
            "host.server_commands", "input.system_keyboard", "display.zoom_mode", "display.rotate",
            "input.mouse_mode", "overlay.performance_hud", "overlay.floating_menu_button",
            "overlay.special_keys", "overlay.virtual_controller", "overlay.full_keyboard",
            "windows.task_manager", "windows.send_keys", "input.touch_sensitivity",
            "dynamic.device_actions"
    };

    @Test
    public void artemisActionIdsRemainStableWhileLabelsResolveFromResources() {
        Context context = ApplicationProvider.getApplicationContext();
        ArtemisAction[] actions = ArtemisAction.values();
        assertEquals(ARTEMIS_ACTION_IDS.length, actions.length);

        for (int i = 0; i < actions.length; i++) {
            assertEquals(ARTEMIS_ACTION_IDS[i], actions[i].getId());
            assertTrue(actions[i].getLabelResId() != 0);
            String label = actions[i].getLabel(context);
            assertFalse(label.trim().isEmpty());
            assertEquals(context.getString(actions[i].getLabelResId()), label);
        }
    }

    @Test
    public void quickMenuIdsRemainStableAndDisplayMetadataResolvesFromResources() {
        Context context = ApplicationProvider.getApplicationContext();
        List<StreamActionRegistry.ActionDefinition> actions = StreamActionRegistry.getAll();
        assertEquals(QUICK_MENU_ACTION_IDS.length, actions.size());

        for (int i = 0; i < actions.size(); i++) {
            StreamActionRegistry.ActionDefinition action = actions.get(i);
            assertEquals(QUICK_MENU_ACTION_IDS[i], action.id);
            assertFalse(context.getString(action.labelResId).trim().isEmpty());
            assertFalse(context.getString(action.categoryResId).trim().isEmpty());
            assertFalse(context.getString(action.descriptionResId).trim().isEmpty());
        }
    }

    @Test
    public void quickMenuCategoryIdentityUsesUniqueResourceIds() {
        List<Integer> categories = StreamActionRegistry.getCategoryResIds();
        Set<Integer> unique = new HashSet<>(categories);
        assertEquals(categories.size(), unique.size());
        assertFalse(categories.contains(0));

        for (StreamActionRegistry.ActionDefinition action : StreamActionRegistry.getAll()) {
            assertTrue(categories.contains(action.categoryResId));
        }
    }
}
