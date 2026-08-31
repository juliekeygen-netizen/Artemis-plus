package com.limelight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.quickmenu.QuickMenuConfig;
import com.limelight.quickmenu.StreamActionRegistry;
import com.limelight.ui.ArtemisEditorUi;
import com.limelight.utils.KeyConfigHelper;
import com.limelight.utils.KeyMapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides the customizable Quick Menu for an ongoing Game stream.
 *
 * The persisted tree contains only stable action IDs and subpages. This class keeps runtime
 * availability/execution tied to the active Game so saved layouts remain portable across hosts,
 * displays, and controllers.
 */
public class GameMenu implements Game.GameMenuCallbacks {

    public static final long KEY_UP_DELAY = 25;
    private static final long TEST_GAME_FOCUS_DELAY = 10;

    public static final String PREF_NAME = "specialPrefs";
    public static final String KEY_NAME = "special_key";

    public static class MenuOption {
        private final String label;
        private final boolean withGameFocus;
        private final Runnable runnable;

        public MenuOption(String label, boolean withGameFocus, Runnable runnable) {
            this.label = label;
            this.withGameFocus = withGameFocus;
            this.runnable = runnable;
        }

        public MenuOption(String label, Runnable runnable) {
            this(label, false, runnable);
        }
    }

    private final Game game;
    private final Context dialogScreenContext;
    private AlertDialog currentDialog;
    private View currentOverlay;

    public GameMenu(Game game, Context dialogScreenContext) {
        this.game = game;
        this.dialogScreenContext = dialogScreenContext;
    }

    public GameMenu(Game game) {
        this.game = game;
        this.dialogScreenContext = game;
    }

    private String getString(int id) {
        return game.getResources().getString(id);
    }

    private void sendKeys(short[] keys) {
        game.sendKeys(keys);
    }

    private void runWithGameFocus(Runnable runnable) {
        if (game.isFinishing()) return;
        if (!game.hasWindowFocus() && dialogScreenContext instanceof Game) {
            new Handler().postDelayed(() -> runWithGameFocus(runnable), TEST_GAME_FOCUS_DELAY);
            return;
        }
        runnable.run();
    }

    private void run(MenuOption option) {
        if (option == null || option.runnable == null) return;
        if (option.withGameFocus) runWithGameFocus(option.runnable);
        else option.runnable.run();
    }

    private boolean shouldUseSidewaysOverlay() {
        return dialogScreenContext == game && game.isSidewaysStreamActive() &&
                game.getStreamVisualRoot() != null;
    }

    private void showMenuDialog(String title, MenuOption[] options) {
        if (shouldUseSidewaysOverlay()) {
            showSidewaysOverlay(title, null, options, getString(R.string.game_menu_cancel));
            return;
        }

        Context ui = ArtemisEditorUi.context(dialogScreenContext);
        LinearLayout rows = new LinearLayout(ui);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(ArtemisEditorUi.dp(ui, 10), ArtemisEditorUi.dp(ui, 6),
                ArtemisEditorUi.dp(ui, 10), ArtemisEditorUi.dp(ui, 6));
        String cancelLabel = getString(R.string.game_menu_cancel);
        for (MenuOption option : options) {
            if (option.runnable == null) {
                cancelLabel = option.label;
                continue;
            }
            TextView row = ArtemisEditorUi.menuRow(ui, option.label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 46));
            lp.setMargins(0, ArtemisEditorUi.dp(ui, 2), 0, ArtemisEditorUi.dp(ui, 2));
            rows.addView(row, lp);
            row.setOnClickListener(v -> {
                hideMenu();
                run(option);
            });
        }
        ScrollView scroll = new ScrollView(ui);
        scroll.addView(rows);
        AlertDialog.Builder builder = ArtemisEditorUi.builder(ui, title)
                .setView(scroll)
                .setNegativeButton(cancelLabel, (dialog, which) -> currentDialog = null)
                .setOnCancelListener(dialog -> hideMenu());
        if (currentDialog != null) currentDialog.dismiss();
        currentDialog = builder.create();
        currentDialog.setOnShowListener(ignored ->
                ArtemisEditorUi.styleDialog(currentDialog, dialogScreenContext, 460));
        currentDialog.show();
    }

    private void showSidewaysOverlay(String title,
                                     String message,
                                     MenuOption[] options,
                                     String fallbackCancelLabel) {
        FrameLayout root = game.getStreamVisualRoot();
        if (root == null) {
            return;
        }

        hideMenu();

        Context ui = ArtemisEditorUi.context(game);
        FrameLayout overlay = new FrameLayout(ui);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setBackgroundColor(0xB8000000);
        overlay.setOnClickListener(v -> hideMenu());

        int logicalWidth = root.getWidth() > 0
                ? root.getWidth() : game.getResources().getDisplayMetrics().heightPixels;
        int logicalHeight = root.getHeight() > 0
                ? root.getHeight() : game.getResources().getDisplayMetrics().widthPixels;
        int widthCap = Math.max(ArtemisEditorUi.dp(ui, 280),
                Math.round(logicalWidth * 0.90f));
        int panelWidth = Math.min(ArtemisEditorUi.dp(ui, 460), widthCap);
        panelWidth = Math.min(panelWidth, Math.max(1, logicalWidth - ArtemisEditorUi.dp(ui, 24)));
        int panelHeight = Math.min(ArtemisEditorUi.dp(ui, 620),
                Math.max(ArtemisEditorUi.dp(ui, 220), Math.round(logicalHeight * 0.86f)));
        panelHeight = Math.min(panelHeight, Math.max(1, logicalHeight - ArtemisEditorUi.dp(ui, 24)));

        LinearLayout panel = new LinearLayout(ui);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true); // Do not let taps inside the card hit the dismissing scrim.
        panel.setBackground(ArtemisEditorUi.rounded(
                ui, ArtemisEditorUi.SURFACE, 14, 1, ArtemisEditorUi.BORDER));

        TextView header = ArtemisEditorUi.header(ui, title);
        panel.addView(header);

        if (message != null && !message.isEmpty()) {
            TextView body = ArtemisEditorUi.label(
                    ui, message, 14f, ArtemisEditorUi.TEXT_SECONDARY);
            body.setPadding(ArtemisEditorUi.dp(ui, 20), ArtemisEditorUi.dp(ui, 14),
                    ArtemisEditorUi.dp(ui, 20), ArtemisEditorUi.dp(ui, 14));
            panel.addView(body, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout rows = new LinearLayout(ui);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(ArtemisEditorUi.dp(ui, 10), ArtemisEditorUi.dp(ui, 6),
                ArtemisEditorUi.dp(ui, 10), ArtemisEditorUi.dp(ui, 6));
        String cancelLabel = fallbackCancelLabel;
        if (options != null) {
            for (MenuOption option : options) {
                if (option == null) continue;
                if (option.runnable == null) {
                    cancelLabel = option.label;
                    continue;
                }
                TextView row = ArtemisEditorUi.menuRow(ui, option.label);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 46));
                rowParams.setMargins(0, ArtemisEditorUi.dp(ui, 2), 0,
                        ArtemisEditorUi.dp(ui, 2));
                rows.addView(row, rowParams);
                row.setOnClickListener(v -> {
                    hideMenu();
                    run(option);
                });
            }
        }

        ScrollView scroll = new ScrollView(ui);
        scroll.setFillViewport(false);
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView cancel = ArtemisEditorUi.menuRow(ui, cancelLabel);
        cancel.setTextColor(ArtemisEditorUi.TEXT_SECONDARY);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 46));
        cancelParams.setMargins(ArtemisEditorUi.dp(ui, 10), ArtemisEditorUi.dp(ui, 4),
                ArtemisEditorUi.dp(ui, 10), ArtemisEditorUi.dp(ui, 10));
        panel.addView(cancel, cancelParams);
        cancel.setOnClickListener(v -> hideMenu());

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(panelWidth, panelHeight);
        panelParams.gravity = Gravity.CENTER;
        overlay.addView(panel, panelParams);

        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        overlay.bringToFront();
        currentOverlay = overlay;
    }

    private void showSidewaysMessage(String title, String message) {
        showSidewaysOverlay(title, message, new MenuOption[0],
                getString(android.R.string.ok));
    }

    private void showSpecialKeysMenu() {
        List<MenuOption> options = new ArrayList<>();

        if (!PreferenceConfiguration.readPreferences(game).disableDefaultExtraKeys) {
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_esc),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_ESCAPE})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_f11),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_F11})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_f4),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_F4})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_enter),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_RETURN})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_v),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_V})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_d),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_g),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_TAB})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_shift_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_shift_left),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_LEFT})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f1),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F1})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f12),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F12})));
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_b),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_B})));
        }

        SharedPreferences preferences = game.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE);
        String value = preferences.getString(KEY_NAME, "");
        if (!TextUtils.isEmpty(value)) {
            try {
                KeyConfigHelper.ShortcutFile shortcutFile = KeyConfigHelper.parseShortcutFile(value);
                if (shortcutFile != null && shortcutFile.data != null && !shortcutFile.data.isEmpty()) {
                    for (KeyConfigHelper.Shortcut sc : shortcutFile.data) {
                        List<String> keys = sc.keys;
                        short[] keyCodes = new short[keys.size()];
                        for (int i = 0; i < keys.size(); i++) {
                            String code = keys.get(i);
                            int keycode;
                            if (code.startsWith("0x")) {
                                keycode = Integer.parseInt(code.substring(2), 16);
                            } else if (code.startsWith("VK_")) {
                                Field field = KeyMapper.class.getDeclaredField(code);
                                keycode = field.getInt(null);
                            } else {
                                throw new IllegalArgumentException("Unknown key code: " + code);
                            }
                            keyCodes[i] = (short) keycode;
                        }
                        options.add(new MenuOption(sc.name, () -> sendKeys(keyCodes)));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(game, getString(R.string.wrong_import_format), Toast.LENGTH_SHORT).show();
            }
        }
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));
        showMenuDialog(getString(R.string.game_menu_send_keys), options.toArray(new MenuOption[0]));
    }

    private void showServerCmd(ArrayList<String> serverCmds) {
        List<MenuOption> options = new ArrayList<>();
        AtomicInteger index = new AtomicInteger(0);
        for (String str : serverCmds) {
            final int finalI = index.getAndIncrement();
            options.add(new MenuOption("> " + str, true, () -> game.sendExecServerCmd(finalI)));
        }
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));
        showMenuDialog(getString(R.string.game_menu_server_cmd), options.toArray(new MenuOption[0]));
    }

    private void openServerCommands() {
        ArrayList<String> serverCmds = game.getServerCmds();
        if (serverCmds.isEmpty()) {
            if (shouldUseSidewaysOverlay()) {
                showSidewaysMessage(
                        getString(R.string.game_dialog_title_server_cmd_empty),
                        getString(R.string.game_dialog_message_server_cmd_empty));
                return;
            }
            int themeResId = game.getApplicationInfo().theme;
            Context themedContext = new ContextThemeWrapper(dialogScreenContext, themeResId);
            AlertDialog emptyDialog = ArtemisEditorUi.builder(
                            themedContext, getString(R.string.game_dialog_title_server_cmd_empty))
                    .setMessage(R.string.game_dialog_message_server_cmd_empty)
                    .setNegativeButton(android.R.string.ok, null)
                    .create();
            emptyDialog.setOnShowListener(ignored ->
                    ArtemisEditorUi.styleDialog(emptyDialog, themedContext, 440));
            emptyDialog.show();
        } else {
            showServerCmd(serverCmds);
        }
    }

    private MenuOption buildActionOption(String actionId) {
        switch (actionId) {
            case StreamActionRegistry.DISCONNECT:
                return new MenuOption(getString(R.string.game_menu_disconnect), game::disconnect);
            case StreamActionRegistry.QUIT_SESSION:
                return new MenuOption(getString(R.string.game_menu_quit_session), game::quit);
            case StreamActionRegistry.UPLOAD_CLIPBOARD:
                return new MenuOption(getString(R.string.game_menu_upload_clipboard), true,
                        () -> game.sendClipboard(true));
            case StreamActionRegistry.FETCH_CLIPBOARD:
                return new MenuOption(getString(R.string.game_menu_fetch_clipboard), true,
                        () -> game.getClipboard(0));
            case StreamActionRegistry.SERVER_COMMANDS:
                return new MenuOption(getString(R.string.game_menu_server_cmd), true, this::openServerCommands);
            case StreamActionRegistry.TOGGLE_KEYBOARD:
                return new MenuOption(getString(R.string.game_menu_toggle_keyboard), true, game::toggleKeyboard);
            case StreamActionRegistry.TOGGLE_ZOOM:
                return new MenuOption(getString(game.isZoomModeEnabled()
                        ? R.string.game_menu_disable_zoom_mode
                        : R.string.game_menu_enable_zoom_mode), true, game::toggleZoomMode);
            case StreamActionRegistry.ROTATE_SCREEN:
                if (dialogScreenContext != game || game.isSidewaysStreamActive()) return null;
                return new MenuOption(getString(R.string.game_menu_rotate_screen), true,
                        () -> ArtemisOrientationHelper.rotate(game));
            case StreamActionRegistry.SELECT_MOUSE_MODE:
                if (!game.allowChangeMouseMode) return null;
                return new MenuOption(getString(R.string.game_menu_select_mouse_mode), true,
                        () -> game.selectMouseMode(dialogScreenContext));
            case StreamActionRegistry.TOGGLE_HUD:
                return new MenuOption(getString(R.string.game_menu_toggle_hud), true, game::toggleHUD);
            case StreamActionRegistry.TOGGLE_FLOATING_BUTTON:
                return new MenuOption(getString(R.string.game_menu_toggle_floating_button), true,
                        game::toggleFloatingButtonVisibility);
            case StreamActionRegistry.TOGGLE_KEYBOARD_CONTROLLER:
                return new MenuOption(getString(R.string.game_menu_toggle_keyboard_model), true,
                        game::toggleKeyboardController);
            case StreamActionRegistry.TOGGLE_VIRTUAL_CONTROLLER:
                if (game.isOnExternalDisplay()) return null;
                return new MenuOption(getString(R.string.game_menu_toggle_virtual_model), true,
                        game::toggleVirtualController);
            case StreamActionRegistry.TOGGLE_FULL_KEYBOARD:
                return new MenuOption(getString(R.string.game_menu_toggle_virtual_keyboard_model), true,
                        game::toggleFullKeyboard);
            case StreamActionRegistry.TASK_MANAGER:
                return new MenuOption(getString(R.string.game_menu_task_manager), true,
                        () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,
                                KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_ESCAPE}));
            case StreamActionRegistry.SEND_KEYS:
                return new MenuOption(getString(R.string.game_menu_send_keys), this::showSpecialKeysMenu);
            case StreamActionRegistry.SWITCH_TOUCH_SENSITIVITY:
                return new MenuOption(getString(R.string.game_menu_switch_touch_sensitivity_model), true,
                        game::switchTouchSensitivity);
            case StreamActionRegistry.DEVICE_ACTIONS:
                // This reserved dynamic slot is expanded directly while rendering a page.
                return null;
            default:
                return null;
        }
    }

    private void showConfiguredPage(QuickMenuConfig.Page page, GameInputDevice device, Runnable backAction) {
        List<MenuOption> options = new ArrayList<>();
        if (backAction != null) {
            options.add(new MenuOption("‹ Back", true, backAction));
        }
        for (QuickMenuConfig.Node node : page.items) {
            if (node == null) continue;
            if (node.isPage()) {
                options.add(new MenuOption(node.page.title, true,
                        () -> showConfiguredPage(node.page, device,
                                () -> showConfiguredPage(page, device, backAction))));
            } else if (node.isAction()) {
                if (StreamActionRegistry.DEVICE_ACTIONS.equals(node.actionId)) {
                    if (device != null) {
                        List<MenuOption> deviceOptions = device.getGameMenuOptions();
                        if (deviceOptions != null) options.addAll(deviceOptions);
                    }
                } else {
                    MenuOption option = buildActionOption(node.actionId);
                    if (option != null) options.add(option);
                }
            }
        }
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));
        showMenuDialog(page.title, options.toArray(new MenuOption[0]));
    }

    public void showMenu(GameInputDevice device) {
        QuickMenuConfig config = QuickMenuConfig.load(game);
        showConfiguredPage(config.root, device, null);
    }

    @Override
    public void hideMenu() {
        if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();
        currentDialog = null;
        if (currentOverlay != null) {
            ViewParent parent = currentOverlay.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(currentOverlay);
            }
            currentOverlay = null;
        }
    }

    @Override
    public boolean isMenuOpen() {
        return (currentDialog != null && currentDialog.isShowing()) ||
                (currentOverlay != null && currentOverlay.getParent() != null);
    }
}
