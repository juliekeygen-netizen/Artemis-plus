from pathlib import Path

# Settings entry (idempotent).
path = Path('app/src/main/res/xml/preferences.xml')
text = path.read_text(encoding='utf-8')
marker = '''    <PreferenceCategory\n        android:key="category_ui_settings"\n        android:title="@string/category_ui_settings"\n        app:initialExpandedChildrenCount="3"\n        app:iconSpaceReserved="false">\n'''
addition = marker + '''        <com.limelight.preferences.QuickMenuPreference\n            android:key="customize_quick_menu"\n            android:title="Customize Quick Menu"\n            android:summary="Choose, reorder, remove, and group the actions shown in the in-stream Quick Menu."\n            app:iconSpaceReserved="false" />\n'''
if 'android:key="customize_quick_menu"' not in text:
    if marker not in text:
        raise SystemExit('UI settings category marker not found')
    text = text.replace(marker, addition, 1)
    path.write_text(text, encoding='utf-8')
    print('Inserted Customize Quick Menu preference')
else:
    print('Customize Quick Menu preference already present')

# The settings XML is also reused by profile editing. Quick Menu layout is intentionally global,
# so remove this entry before search indexing when SettingsFragment is hosted elsewhere.
path = Path('app/src/main/java/com/limelight/preferences/StreamSettings.java')
text = path.read_text(encoding='utf-8')
old = '''            addPreferencesFromResource(R.xml.preferences);\n            PreferenceScreen screen = getPreferenceScreen();\n\n            ListPreference outsideOrientation = findPreference(OutsideStreamOrientationPolicy.PREF_KEY);'''
new = '''            addPreferencesFromResource(R.xml.preferences);\n            PreferenceScreen screen = getPreferenceScreen();\n\n            Preference quickMenuPreference = findPreference("customize_quick_menu");\n            if (quickMenuPreference != null && !(requireActivity() instanceof StreamSettings)) {\n                PreferenceGroup parent = quickMenuPreference.getParent();\n                if (parent != null) {\n                    parent.removePreference(quickMenuPreference);\n                }\n            }\n\n            ListPreference outsideOrientation = findPreference(OutsideStreamOrientationPolicy.PREF_KEY);'''
if old in text:
    text = text.replace(old, new, 1)
if 'Preference quickMenuPreference = findPreference("customize_quick_menu");' not in text:
    raise SystemExit('StreamSettings profile-scope patch did not apply')
path.write_text(text, encoding='utf-8')

# Runtime subpage navigation: preserve the existing Cancel button while adding an explicit Back row.
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
if 'private void showConfiguredPage(QuickMenuConfig.Page page, GameInputDevice device, Runnable backAction)' not in text:
    raise SystemExit('GameMenu Back-navigation patch did not apply')
path.write_text(text, encoding='utf-8')

# Make the safety cap visible to the editor and count the whole persisted tree.
path = Path('app/src/main/java/com/limelight/quickmenu/QuickMenuConfig.java')
text = path.read_text(encoding='utf-8')
text = text.replace('    private static final int MAX_TOTAL_NODES = 128;',
                    '    public static final int MAX_TOTAL_NODES = 128;')
text = text.replace('    private static int countNodes(Page page) {',
                    '    public static int countNodes(Page page) {')
if 'public static final int MAX_TOTAL_NODES = 128;' not in text or 'public static int countNodes(Page page)' not in text:
    raise SystemExit('QuickMenuConfig global node-count patch did not apply')
path.write_text(text, encoding='utf-8')

path = Path('app/src/main/java/com/limelight/quickmenu/QuickMenuEditorDialog.java')
text = path.read_text(encoding='utf-8')
old = '''            addPage.setOnClickListener(v -> {\n                if (pageStack.size() - 1 >= MAX_PAGE_DEPTH) {'''
new = '''            addPage.setOnClickListener(v -> {\n                if (QuickMenuConfig.countNodes(config.root) >= QuickMenuConfig.MAX_TOTAL_NODES) {\n                    Toast.makeText(app, "Quick Menu item limit reached", Toast.LENGTH_SHORT).show();\n                    return;\n                }\n                if (pageStack.size() - 1 >= MAX_PAGE_DEPTH) {'''
if old in text:
    text = text.replace(old, new, 1)
old = '''            row.setOnClickListener(v -> {\n                if (QuickMenuConfig.addAction(currentPage(), action.id)) {'''
new = '''            row.setOnClickListener(v -> {\n                if (QuickMenuConfig.countNodes(config.root) >= QuickMenuConfig.MAX_TOTAL_NODES) {\n                    Toast.makeText(app, "Quick Menu item limit reached", Toast.LENGTH_SHORT).show();\n                    return;\n                }\n                if (QuickMenuConfig.addAction(currentPage(), action.id)) {'''
if old in text:
    text = text.replace(old, new, 1)
if text.count('Quick Menu item limit reached') != 2:
    raise SystemExit('QuickMenuEditorDialog global item-limit patch did not apply')
path.write_text(text, encoding='utf-8')
