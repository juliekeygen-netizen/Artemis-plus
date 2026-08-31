from pathlib import Path

# Keep the global Quick Menu editor beside the floating Quick Menu control instead of changing
# the first three initially-expanded UI Settings rows.
path = Path('app/src/main/res/xml/preferences.xml')
text = path.read_text(encoding='utf-8')
quick_menu_block = '''        <com.limelight.preferences.QuickMenuPreference\n            android:key="customize_quick_menu"\n            android:title="Customize Quick Menu"\n            android:summary="Choose, reorder, remove, and group the actions shown in the in-stream Quick Menu."\n            app:iconSpaceReserved="false" />\n'''
text = text.replace(quick_menu_block, '', 1)
floating_block = '''        <CheckBoxPreference\n            android:dependency="checkbox_enable_quit_dialog"\n            android:defaultValue="false"\n            android:key="checkbox_enable_floating_button"\n            android:summary="@string/summary_floating_button"\n            android:title="@string/title_floating_button"\n            app:iconSpaceReserved="false" />\n'''
if floating_block not in text:
    raise SystemExit('Floating Quick Menu preference marker not found')
text = text.replace(floating_block, floating_block + quick_menu_block, 1)
if text.count('android:key="customize_quick_menu"') != 1:
    raise SystemExit('Customize Quick Menu preference must appear exactly once')
path.write_text(text, encoding='utf-8')

# Quick Menu layout is global even though the same preferences XML is reused by profile editing.
# StreamSettings is a legacy CRLF file; preserve that convention exactly.
path = Path('app/src/main/java/com/limelight/preferences/StreamSettings.java')
raw = path.read_bytes().decode('utf-8')
text = raw.replace('\r\n', '\n')
old = '''            addPreferencesFromResource(R.xml.preferences);\n            PreferenceScreen screen = getPreferenceScreen();\n\n            ListPreference outsideOrientation = findPreference(OutsideStreamOrientationPolicy.PREF_KEY);'''
new = '''            addPreferencesFromResource(R.xml.preferences);\n            PreferenceScreen screen = getPreferenceScreen();\n\n            Preference quickMenuPreference = findPreference("customize_quick_menu");\n            if (quickMenuPreference != null && !(requireActivity() instanceof StreamSettings)) {\n                PreferenceGroup parent = quickMenuPreference.getParent();\n                if (parent != null) {\n                    parent.removePreference(quickMenuPreference);\n                }\n            }\n\n            ListPreference outsideOrientation = findPreference(OutsideStreamOrientationPolicy.PREF_KEY);'''
if old in text:
    text = text.replace(old, new, 1)
if text.count('Preference quickMenuPreference = findPreference("customize_quick_menu");') != 1:
    raise SystemExit('StreamSettings Quick Menu scoping must appear exactly once')
path.write_bytes(text.replace('\n', '\r\n').encode('utf-8'))

# Runtime subpages need a real Back action in addition to the dialog Cancel button.
path = Path('app/src/main/java/com/limelight/GameMenu.java')
text = path.read_text(encoding='utf-8')
old = '''    private void showConfiguredPage(QuickMenuConfig.Page page, GameInputDevice device) {\n        List<MenuOption> options = new ArrayList<>();\n        for (QuickMenuConfig.Node node : page.items) {'''
new = '''    private void showConfiguredPage(QuickMenuConfig.Page page, GameInputDevice device, Runnable backAction) {\n        List<MenuOption> options = new ArrayList<>();\n        if (backAction != null) {\n            options.add(new MenuOption("‹ Back", true, backAction));\n        }\n        for (QuickMenuConfig.Node node : page.items) {'''
if old in text:
    text = text.replace(old, new, 1)
text = text.replace(
    '''                options.add(new MenuOption(node.page.title, true,\n                        () -> showConfiguredPage(node.page, device)));''',
    '''                options.add(new MenuOption(node.page.title, true,\n                        () -> showConfiguredPage(node.page, device,\n                                () -> showConfiguredPage(page, device, backAction))));''')
text = text.replace(
    '''        showConfiguredPage(config.root, device);''',
    '''        showConfiguredPage(config.root, device, null);''')
if text.count('private void showConfiguredPage(QuickMenuConfig.Page page, GameInputDevice device, Runnable backAction)') != 1:
    raise SystemExit('GameMenu Back-navigation patch must appear exactly once')
path.write_text(text, encoding='utf-8')

# Keep parser and editor depth limits in one place, and count non-root page nodes when entering
# them so a deeply nested child cannot push the parsed tree one node beyond the global cap.
path = Path('app/src/main/java/com/limelight/quickmenu/QuickMenuConfig.java')
text = path.read_text(encoding='utf-8')
text = text.replace('    private static final int MAX_DEPTH = 6;',
                    '    public static final int MAX_PAGE_DEPTH = 6;')
text = text.replace('    private static final int MAX_TOTAL_NODES = 128;',
                    '    public static final int MAX_TOTAL_NODES = 128;')
text = text.replace('    private static int countNodes(Page page) {',
                    '    public static int countNodes(Page page) {')
old_parser = '''    private static Page pageFromJson(JSONObject object, int depth, Counter counter, boolean root) {\n        if (object == null || depth > MAX_DEPTH || counter.nodes >= MAX_TOTAL_NODES) return null;\n        String fallbackTitle = root ? "Quick Menu" : "Page";\n        Page page = new Page(root ? "root" : object.optString("id", null),\n                normalizeTitle(object.optString("title", fallbackTitle), fallbackTitle));\n        JSONArray items = object.optJSONArray("items");\n        if (items == null) return page;\n        for (int i = 0; i < items.length() && counter.nodes < MAX_TOTAL_NODES; i++) {\n            JSONObject item = items.optJSONObject(i);\n            if (item == null) continue;\n            String type = item.optString("type", "");\n            if (TYPE_ACTION.equals(type)) {\n                String actionId = item.optString("actionId", "");\n                if (StreamActionRegistry.contains(actionId)) {\n                    page.items.add(Node.action(actionId));\n                    counter.nodes++;\n                }\n            } else if (TYPE_PAGE.equals(type) && depth < MAX_DEPTH) {\n                Page child = pageFromJson(item.optJSONObject("page"), depth + 1, counter, false);\n                if (child != null) {\n                    page.items.add(Node.page(child));\n                    counter.nodes++;\n                }\n            }\n        }\n        return page;\n    }'''
new_parser = '''    private static Page pageFromJson(JSONObject object, int depth, Counter counter, boolean root) {\n        if (object == null || depth > MAX_PAGE_DEPTH) return null;\n        if (!root) {\n            if (counter.nodes >= MAX_TOTAL_NODES) return null;\n            counter.nodes++;\n        }\n        String fallbackTitle = root ? "Quick Menu" : "Page";\n        Page page = new Page(root ? "root" : object.optString("id", null),\n                normalizeTitle(object.optString("title", fallbackTitle), fallbackTitle));\n        JSONArray items = object.optJSONArray("items");\n        if (items == null) return page;\n        for (int i = 0; i < items.length() && counter.nodes < MAX_TOTAL_NODES; i++) {\n            JSONObject item = items.optJSONObject(i);\n            if (item == null) continue;\n            String type = item.optString("type", "");\n            if (TYPE_ACTION.equals(type)) {\n                String actionId = item.optString("actionId", "");\n                if (StreamActionRegistry.contains(actionId)) {\n                    page.items.add(Node.action(actionId));\n                    counter.nodes++;\n                }\n            } else if (TYPE_PAGE.equals(type) && depth < MAX_PAGE_DEPTH) {\n                Page child = pageFromJson(item.optJSONObject("page"), depth + 1, counter, false);\n                if (child != null) {\n                    page.items.add(Node.page(child));\n                }\n            }\n        }\n        return page;\n    }'''
if old_parser in text:
    text = text.replace(old_parser, new_parser, 1)
if 'public static final int MAX_PAGE_DEPTH = 6;' not in text:
    raise SystemExit('Shared Quick Menu depth limit missing')
if 'depth < MAX_DEPTH' in text or 'depth > MAX_DEPTH' in text:
    raise SystemExit('Legacy Quick Menu depth limit still referenced')
if 'counter.nodes++;\n                }\n            }\n        }\n        return page;' not in text:
    # This broad check only guards against accidentally deleting action-node accounting.
    raise SystemExit('Quick Menu parser action-node accounting missing')
path.write_text(text, encoding='utf-8')

# Editor uses the exact same depth constant as persistence and enforces the global tree cap.
path = Path('app/src/main/java/com/limelight/quickmenu/QuickMenuEditorDialog.java')
text = path.read_text(encoding='utf-8')
text = text.replace('    private static final int MAX_PAGE_DEPTH = 6;\n\n', '')
text = text.replace('pageStack.size() - 1 >= MAX_PAGE_DEPTH',
                    'pageStack.size() - 1 >= QuickMenuConfig.MAX_PAGE_DEPTH')
old = '''            addPage.setOnClickListener(v -> {\n                if (pageStack.size() - 1 >= QuickMenuConfig.MAX_PAGE_DEPTH) {'''
new = '''            addPage.setOnClickListener(v -> {\n                if (QuickMenuConfig.countNodes(config.root) >= QuickMenuConfig.MAX_TOTAL_NODES) {\n                    Toast.makeText(app, "Quick Menu item limit reached", Toast.LENGTH_SHORT).show();\n                    return;\n                }\n                if (pageStack.size() - 1 >= QuickMenuConfig.MAX_PAGE_DEPTH) {'''
if old in text:
    text = text.replace(old, new, 1)
old = '''            row.setOnClickListener(v -> {\n                if (QuickMenuConfig.addAction(currentPage(), action.id)) {'''
new = '''            row.setOnClickListener(v -> {\n                if (QuickMenuConfig.countNodes(config.root) >= QuickMenuConfig.MAX_TOTAL_NODES) {\n                    Toast.makeText(app, "Quick Menu item limit reached", Toast.LENGTH_SHORT).show();\n                    return;\n                }\n                if (QuickMenuConfig.addAction(currentPage(), action.id)) {'''
if old in text:
    text = text.replace(old, new, 1)
if text.count('Quick Menu item limit reached') != 2:
    raise SystemExit('QuickMenuEditorDialog global item-limit guard must appear twice')
if 'MAX_PAGE_DEPTH' in text and 'QuickMenuConfig.MAX_PAGE_DEPTH' not in text:
    raise SystemExit('Editor is not using shared Quick Menu depth limit')
path.write_text(text, encoding='utf-8')

# Remove an unused editor-only helper/import from the registry so it stays a pure stable catalog.
path = Path('app/src/main/java/com/limelight/quickmenu/StreamActionRegistry.java')
text = path.read_text(encoding='utf-8')
text = text.replace('import android.content.Context;\n\n', '')
text = text.replace('''\n    public static String editorLabel(Context context, String id) {\n        ActionDefinition definition = find(id);\n        return definition == null ? id : definition.label;\n    }\n''', '\n')
path.write_text(text, encoding='utf-8')

# Add direct coverage for the parser cap, including many nested page nodes mixed with actions.
path = Path('app/src/test/java/com/limelight/quickmenu/QuickMenuConfigTest.java')
text = path.read_text(encoding='utf-8')
anchor = '''    @Test\n    public void registryIdsAreUniqueAndResolvable() {'''
new_test = '''    @Test\n    public void parserNeverExceedsGlobalNodeLimit() throws Exception {\n        JSONObject root = new JSONObject();\n        root.put("id", "root");\n        root.put("title", "Oversized");\n        JSONArray items = new JSONArray();\n        for (int i = 0; i < QuickMenuConfig.MAX_TOTAL_NODES + 40; i++) {\n            JSONObject page = new JSONObject();\n            page.put("id", "page-" + i);\n            page.put("title", "Page " + i);\n            JSONArray childItems = new JSONArray();\n            childItems.put(new JSONObject()\n                    .put("type", "action")\n                    .put("actionId", StreamActionRegistry.TASK_MANAGER));\n            page.put("items", childItems);\n            items.put(new JSONObject().put("type", "page").put("page", page));\n        }\n        root.put("items", items);\n        JSONObject object = new JSONObject()\n                .put("version", QuickMenuConfig.CURRENT_VERSION)\n                .put("root", root);\n\n        QuickMenuConfig parsed = QuickMenuConfig.fromJson(object);\n        assertNotNull(parsed);\n        assertTrue(QuickMenuConfig.countNodes(parsed.root) <= QuickMenuConfig.MAX_TOTAL_NODES);\n    }\n\n'''
if 'public void parserNeverExceedsGlobalNodeLimit()' not in text:
    if anchor not in text:
        raise SystemExit('QuickMenuConfigTest insertion anchor missing')
    text = text.replace(anchor, new_test + anchor, 1)
path.write_text(text, encoding='utf-8')
