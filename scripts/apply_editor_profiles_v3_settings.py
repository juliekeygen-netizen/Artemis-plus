from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


preferences = "app/src/main/res/xml/preferences.xml"
replace_once(
    preferences,
    '''        <ListPreference
            android:defaultValue="OSC_Keyboard"
            android:dependency="checkbox_enable_keyboard"
            android:entries="@array/keyboard_axi_names"
            android:entryValues="@array/keyboard_axi_values"
            android:key="keyboard_axi_list"
            android:summary="@string/summary_default_layout_scheme"
            android:title="@string/title_default_layout_scheme"
            app:iconSpaceReserved="false" />''',
    '''        <com.limelight.preferences.KeyboardProfilesPreference
            android:dependency="checkbox_enable_keyboard"
            android:key="manage_keyboard_profiles"
            android:summary="Create, switch, rename, duplicate, reorder, and delete special-key layouts."
            android:title="Profiles"
            app:iconSpaceReserved="false" />'''
)

settings = "app/src/main/java/com/limelight/preferences/StreamSettings.java"
p = Path(settings)
text = p.read_text(encoding="utf-8")

import_anchor = "import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader;"
if "import com.limelight.binding.input.virtual_controller.keyboard.KeyboardProfilesManager;" not in text:
    if import_anchor not in text:
        raise SystemExit("StreamSettings import anchor missing")
    text = text.replace(
        import_anchor,
        import_anchor + "\nimport com.limelight.binding.input.virtual_controller.keyboard.KeyboardProfilesManager;",
        1,
    )

old_import = '''                    String name = getPrefs().getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
                    SharedPreferences.Editor prefEditor = requireActivity().getSharedPreferences(name, Activity.MODE_PRIVATE).edit();
                    JSONObject object = new JSONObject(json);
                    Iterator it = object.keys();
                    prefEditor.clear();
                    while (it.hasNext()) {
                        String key = (String) it.next();// 获得key
                        String value = object.getString(key);// 获得value
                        prefEditor.putString(key, value);
                    }
                    prefEditor.apply();
                    Toast.makeText(getActivity(), getString(R.string.pref_import_success), Toast.LENGTH_SHORT).show();'''
new_import = '''                    int importedProfiles = KeyboardProfilesManager.importProfiles(requireActivity(), json);
                    if (importedProfiles <= 0) {
                        Toast.makeText(getActivity(), "No keyboard profiles found in file", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getActivity(),
                                "Imported " + importedProfiles + (importedProfiles == 1 ? " keyboard profile" : " keyboard profiles"),
                                Toast.LENGTH_SHORT).show();
                    }'''
if old_import not in text:
    raise SystemExit("Old destructive keyboard import block missing")
text = text.replace(old_import, new_import, 1)

old_export_method = '''        private File getJsonContent(Context context,File file){
            String name = getPrefs().getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
            SharedPreferences pref = context.getSharedPreferences(name, Activity.MODE_PRIVATE);
            Map<String,?> map = pref.getAll();
            File file1= new File(file,name+".json");
            String jsonStr=new Gson().toJson(map);
            if(!FileUriUtils.writerFileString(file1,jsonStr)){
                return null;
            }
            return file1;
        }'''
new_export_method = '''        private File getJsonContent(Context context, File file){
            try {
                File file1 = new File(file, "artemis-keyboard-profiles.json");
                String jsonStr = KeyboardProfilesManager.exportProfiles(context).toString(2);
                if (!FileUriUtils.writerFileString(file1, jsonStr)) {
                    return null;
                }
                return file1;
            } catch (Exception e) {
                LimeLog.warning("Failed to export keyboard profiles: " + e.getMessage());
                return null;
            }
        }'''
if old_export_method not in text:
    raise SystemExit("Old keyboard export method missing")
text = text.replace(old_export_method, new_export_method, 1)
p.write_text(text, encoding="utf-8")

# android.R.string.closeButtonLabel isn't available on every SDK level used by this project.
profile_dialog = Path("app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyboardProfilesDialog.java")
text = profile_dialog.read_text(encoding="utf-8")
text = text.replace('.setNegativeButton(android.R.string.closeButtonLabel, null)', '.setNegativeButton("Close", null)')
profile_dialog.write_text(text, encoding="utf-8")
