from pathlib import Path

path = Path('app/src/main/java/com/limelight/preferences/PreferenceConfiguration.java')
text = path.read_text(encoding='utf-8')

replacements = [
    ('''    public static int getDefaultBitrate(String resString, String fpsString) {
        int width = getWidthFromResolutionString(resString);
        int height = getHeightFromResolutionString(resString);
        int fps = Math.round(Float.parseFloat(fpsString));''', '''    public static int getDefaultBitrate(String resString, String fpsString) {
        PreferenceStringValues.Resolution resolution =
                PreferenceStringValues.parseResolution(resString, DEFAULT_RESOLUTION);
        int width = resolution.width;
        int height = resolution.height;
        int fps = Math.round(PreferenceStringValues.parsePositiveFiniteFloat(
                fpsString, Float.parseFloat(DEFAULT_FPS)));'''),
    ('''            config.width = PreferenceConfiguration.getWidthFromResolutionString(resStr);
            config.height = PreferenceConfiguration.getHeightFromResolutionString(resStr);
            config.fps = Float.parseFloat(prefs.getString(FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS));''', '''            PreferenceStringValues.Resolution resolution = PreferenceStringValues.parseResolution(
                    resStr, PreferenceConfiguration.DEFAULT_RESOLUTION);
            config.width = resolution.width;
            config.height = resolution.height;
            config.fps = PreferenceStringValues.parsePositiveFiniteFloat(
                    prefs.getString(FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS),
                    Float.parseFloat(PreferenceConfiguration.DEFAULT_FPS));'''),
    ('''        String renderMode = prefs.getString("render_mode_list", "0");
        int renderModeInt = Integer.parseInt(renderMode);
        config.renderMode = renderModeInt;

        // Read mouse mode and set touch settings accordingly
        String mouseMode = prefs.getString("mouse_mode_list", "0");
        int mouseModeInt = Integer.parseInt(mouseMode);''', '''        String renderMode = prefs.getString("render_mode_list", "0");
        config.renderMode = PreferenceStringValues.parseBoundedInt(renderMode, 0, 0, 2);

        // Read mouse mode and set touch settings accordingly
        String mouseMode = prefs.getString("mouse_mode_list", "0");
        int mouseModeInt = PreferenceStringValues.parseBoundedInt(mouseMode, 0, 0, 5);'''),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'Expected exactly one source block, found {count}: {old.splitlines()[0]}')
    text = text.replace(old, new, 1)

path.write_text(text, encoding='utf-8')
