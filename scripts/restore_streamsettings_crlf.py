from pathlib import Path

path = Path("app/src/main/java/com/limelight/preferences/StreamSettings.java")
data = path.read_bytes()
# Normalize once, then restore the CRLF convention used by this inherited source file.
data = data.replace(b"\r\n", b"\n").replace(b"\r", b"\n")
path.write_bytes(data.replace(b"\n", b"\r\n"))
