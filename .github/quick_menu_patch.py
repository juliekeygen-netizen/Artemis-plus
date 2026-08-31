from pathlib import Path

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
