"""Kommandozeile für Wartungsaufgaben im Container.

    python -m app.cli import-recipekeeper /import/Export.zip           # Probelauf
    python -m app.cli import-recipekeeper /import/Export.zip --commit  # importieren
"""

import argparse
import json
import sys

from . import recipekeeper
from .bootstrap import bootstrap
from .db import session


def import_recipekeeper(path: str, commit: bool) -> int:
    with open(path, "rb") as f:
        data = f.read()
    with session() as db:
        bootstrap(db)
        try:
            plan = recipekeeper.plan(db, data)
        except recipekeeper.ImportError_ as e:
            print(f"Fehler: {e}", file=sys.stderr)
            return 1
        print(json.dumps(plan.report, ensure_ascii=False, indent=2))
        if commit:
            print(json.dumps(recipekeeper.commit(db, plan), ensure_ascii=False))
        else:
            print("Probelauf, nichts gespeichert. Mit --commit importieren.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(prog="python -m app.cli")
    sub = parser.add_subparsers(dest="cmd", required=True)
    imp = sub.add_parser("import-recipekeeper", help="Recipe-Keeper-Export (ZIP) importieren")
    imp.add_argument("path")
    imp.add_argument("--commit", action="store_true")
    args = parser.parse_args()
    if args.cmd == "import-recipekeeper":
        return import_recipekeeper(args.path, args.commit)
    return 2


if __name__ == "__main__":
    sys.exit(main())
