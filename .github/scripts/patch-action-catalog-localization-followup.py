from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
path = ROOT / "app/src/main/java/com/limelight/quickmenu/QuickMenuEditorDialog.java"
text = path.read_text(encoding="utf-8")
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
path.write_text(text.replace(old, new, 1), encoding="utf-8", newline="\n")
print("Remaining Quick Menu catalog row renderer localized.")
