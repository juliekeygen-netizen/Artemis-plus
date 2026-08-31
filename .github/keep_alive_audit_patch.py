from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    data = p.read_bytes()
    nl = b'\r\n' if b'\r\n' in data else b'\n'
    text = data.decode('utf-8').replace('\r\n', '\n')
    if new in text:
        return
    if old not in text:
        raise SystemExit(f'anchor missing: {path}: {old!r}')
    text = text.replace(old, new, 1)
    p.write_bytes(text.replace('\n', nl.decode()).encode('utf-8'))


# Background streaming intentionally retains Game while hidden. Android's noHistory contract
# would finish the Activity as soon as the user navigates away, defeating both Fast Resume and
# Keep Connection Alive. Disabled mode still finishes explicitly in Game.onStop(), and Game is
# excluded from Recents, so removing this flag doesn't change the normal stream exit behavior.
replace_once(
    'app/src/main/AndroidManifest.xml',
    '            android:noHistory="true"\n',
    '')

print('Keep Alive lifecycle audit patch applied')
