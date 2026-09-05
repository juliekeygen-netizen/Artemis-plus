from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

editor = ROOT / "app/src/main/java/com/limelight/quickmenu/QuickMenuEditorDialog.java"
text = editor.read_text(encoding="utf-8")
old = '''                    holder.name.setText(action == null ? node.actionId : action.label);
                    holder.type.setText(action == null
                            ? ui.getString(R.string.artemis_quick_menu_unavailable_action)
                            : action.category);
'''
new = '''                    holder.name.setText(action == null
                            ? node.actionId : ui.getString(action.labelResId));
                    holder.type.setText(action == null
                            ? ui.getString(R.string.artemis_quick_menu_unavailable_action)
                            : ui.getString(action.categoryResId));
'''
if text.count(old) != 1:
    raise SystemExit(f"Expected exactly one remaining catalog row renderer, found {text.count(old)}")
editor.write_text(text.replace(old, new, 1), encoding="utf-8", newline="\n")

config_test = ROOT / "app/src/test/java/com/limelight/quickmenu/QuickMenuConfigTest.java"
text = config_test.read_text(encoding="utf-8")
old = '''            assertNotNull(StreamActionRegistry.find(action.id));
            assertFalse(action.label.trim().isEmpty());
            assertFalse(action.category.trim().isEmpty());
'''
new = '''            assertNotNull(StreamActionRegistry.find(action.id));
            assertTrue(action.labelResId != 0);
            assertTrue(action.categoryResId != 0);
            assertTrue(action.descriptionResId != 0);
'''
if text.count(old) != 1:
    raise SystemExit(f"Expected exactly one legacy registry metadata assertion, found {text.count(old)}")
config_test.write_text(text.replace(old, new, 1), encoding="utf-8", newline="\n")

print("Remaining Quick Menu catalog consumers migrated to resource metadata.")
