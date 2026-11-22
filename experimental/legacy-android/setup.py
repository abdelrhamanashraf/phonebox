"""Provision the experimental GeckoView application over USB."""
import json
import pathlib
import subprocess
import sys
import shutil
import tempfile
import urllib.request
import uuid

adb = shutil.which("adb")
if not adb:
    raise SystemExit("ADB was not found on PATH. Install Android platform-tools first.")
with urllib.request.urlopen("http://127.0.0.1:8765/api/pair", timeout=5) as response:
    pair = json.load(response)
if not pair.get("enabled") or not pair.get("hosts") or not pair.get("token"):
    raise SystemExit("Enable Wi-Fi connections in PhoneBox settings first.")
package = "local.phonebox.geckotest"
args = sys.argv[1:]
if len(args) > 1:
    raise SystemExit("Usage: python setup.py [pc-address]")
if args:
    host = args[0]
    if host not in pair["hosts"]:
        raise SystemExit("The requested address is not advertised by the PhoneBox agent.")
    pair["hosts"] = [host] + [h for h in pair["hosts"] if h != host]
data = json.dumps({k: pair[k] for k in ("hosts", "port", "token", "fingerprint")}).encode("utf-8")
remote = "/data/local/tmp/pb-" + uuid.uuid4().hex
local = None
try:
    with tempfile.NamedTemporaryFile(delete=False) as tmp:
        local = pathlib.Path(tmp.name)
        tmp.write(data)
    subprocess.run([str(adb), "push", str(local), remote], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, timeout=30)
    # Android 4.1's shell does not reliably close stdin; use a short-lived random
    # staging file, then immediately remove it. The final file is owner-only.
    private = "/data/data/" + package + "/files/pair.json"
    command = "umask 077; rm -f " + private + "; cat " + remote + " > " + private + "; chmod 600 " + private + "; test -s " + private + " && echo PAIR_OK"
    result = subprocess.run([str(adb), "shell", "run-as", package, "/system/bin/sh", "-c", "'" + command + "'"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
    if result.returncode or b"PAIR_OK" not in result.stdout:
        raise SystemExit("Could not provision pairing in app-private storage: " + result.stderr.decode("utf-8", "replace"))
finally:
    subprocess.run([str(adb), "shell", "rm", "-f", remote], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
    if local:
        local.unlink(missing_ok=True)
print("Pairing data sent privately to PhoneBox Engine Test. Open or restart the app on the tablet.")
