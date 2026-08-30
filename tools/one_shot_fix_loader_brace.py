from pathlib import Path

path = Path('app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardControllerConfigurationLoader.java')
text = path.read_text(encoding='utf-8')
old = '''            if (jsonConfig != null) {
                try {
                    element.loadConfiguration(new JSONObject(jsonConfig));
                } catch (JSONException e) {
                    e.printStackTrace();

                    // Remove the corrupt element from the preferences
                    pref.edit().remove(prefKey).apply();
                }
            }
                element.setVisibility(View.GONE);
            }
        }
    }
}'''
new = '''            if (jsonConfig != null) {
                try {
                    element.loadConfiguration(new JSONObject(jsonConfig));
                } catch (JSONException e) {
                    e.printStackTrace();

                    // Remove the corrupt element from the preferences
                    pref.edit().remove(prefKey).apply();
                }
            }
        }
    }
}'''
if old not in text:
    raise SystemExit('Malformed loader tail not found')
path.write_text(text.replace(old, new, 1), encoding='utf-8', newline='\n')
Path('tools/one_shot_fix_loader_brace.py').unlink()
wf = Path('.github/workflows/one-shot-fix-loader-brace.yml')
if wf.exists():
    wf.unlink()
print('Fixed loader brace tail')
