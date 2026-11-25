"""Agrega a la app los botones de AYN compartidos en un aviso (issue) de GitHub.

Solo acepta claves y valores con formato seguro, y solo los botones conocidos.
"""
import json
import os
import re
import sys

ALLOWED = {"Auto lock", "Estilo de control", "Direct power supply", "Force landscape", "Clean memory", "Vibración", "Luz ambiental",
           "Botón extra 1", "Botón extra 2", "Botón extra 3"}
KEY = re.compile(r"^(system|global|secure):[A-Za-z0-9_.]{1,80}$")
VAL = re.compile(r"^[A-Za-z0-9_.,:-]{1,80}$")
PATH = "app/src/main/assets/ayn_buttons.json"


def out(name, value):
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as f:
        f.write(f"{name}={value}\n")


body = os.environ.get("BODY", "")
m = re.search(r"```json\s*(\{.*?\})\s*```", body, re.S)
if not m:
    out("result", "sin-datos")
    sys.exit(0)
try:
    data = json.loads(m.group(1))
except json.JSONDecodeError:
    out("result", "json-invalido")
    sys.exit(0)

clean = {}
for name, v in (data.get("buttons") or {}).items():
    if name not in ALLOWED or not isinstance(v, dict):
        continue
    on, off = v.get("on") or {}, v.get("off") or {}
    if not on or not all(KEY.match(k) and VAL.match(str(x)) for k, x in on.items()):
        continue
    if not all(KEY.match(k) and VAL.match(str(x)) for k, x in off.items()):
        continue
    clean[name] = {"on": {k: str(x) for k, x in on.items()}, "off": {k: str(x) for k, x in off.items()}}

if not clean:
    out("result", "nada-valido")
    sys.exit(0)

with open(PATH, encoding="utf-8") as f:
    current = json.load(f)
before = json.dumps(current, sort_keys=True)
current.setdefault("buttons", {}).update(clean)
current["version"] = int(current.get("version", 1)) + 1
if json.dumps(current, sort_keys=True) == before:
    out("result", "sin-cambios")
    sys.exit(0)
with open(PATH, "w", encoding="utf-8") as f:
    json.dump(current, f, ensure_ascii=False, indent=2)
    f.write("\n")
out("result", "agregado")
out("names", ", ".join(clean))
