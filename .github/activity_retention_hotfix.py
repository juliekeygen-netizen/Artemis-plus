from pathlib import Path

manifest = Path('app/src/main/AndroidManifest.xml')
text = manifest.read_text(encoding='utf-8')
old = '            android:noHistory="true"\n            android:supportsPictureInPicture="true"'
new = '            android:supportsPictureInPicture="true"'
if old not in text:
    raise SystemExit('Game noHistory anchor not found')
manifest.write_text(text.replace(old, new, 1), encoding='utf-8')

test = Path('app/src/test/java/com/limelight/GameActivityManifestTest.java')
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text('''package com.limelight;\n\nimport static org.junit.Assert.assertEquals;\n\nimport android.content.ComponentName;\nimport android.content.Context;\nimport android.content.pm.ActivityInfo;\n\nimport androidx.test.core.app.ApplicationProvider;\n\nimport org.junit.Test;\nimport org.junit.runner.RunWith;\nimport org.robolectric.RobolectricTestRunner;\nimport org.robolectric.annotation.Config;\n\n@RunWith(RobolectricTestRunner.class)\n@Config(sdk = 34)\npublic class GameActivityManifestTest {\n    @Test\n    public void gameActivityIsRetainedWhenUserNavigatesAway() throws Exception {\n        Context context = ApplicationProvider.getApplicationContext();\n        ActivityInfo info = context.getPackageManager().getActivityInfo(\n                new ComponentName(context, Game.class), 0);\n\n        assertEquals(\"Game must not use noHistory because background streaming retains it\",\n                0, info.flags & ActivityInfo.FLAG_NO_HISTORY);\n    }\n}\n''', encoding='utf-8')
