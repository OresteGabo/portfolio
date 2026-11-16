# Inzira — refined initial

Canonical vector reconstruction of the approved angled I concept. The contour and hexagonal frame have half-turn symmetry, rounded joins and equal opposing cuts. Both themes use identical geometry; no raster tracing is used at runtime.

`geometry.json` feeds `scripts/generate-inzira-brand.py`. Keep the prior concept in `refined-10` as design history; lettering remains unchanged. No bundle identifiers, widget kinds, app groups or stored data change.

Regenerate native assets from the repository root:

```sh
python3 scripts/generate-inzira-brand.py
swift scripts/generate-app-icons.swift
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
```

Widgets use the same vector master. The iOS host and NextWidget extension must be installed together: changing only the shared Kotlin framework cannot refresh the extension's bundled image. Existing widget kinds remain stable so saved widgets and timelines are preserved. iOS may retain an old gallery or Home Screen preview until its next refresh; remove and re-add the widget if necessary after updating the app.
