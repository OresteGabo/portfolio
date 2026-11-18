# Retired JavaScript engine — test reference only

This directory is outside every Android/iPhone source set and resource tree. Nothing here is packaged or executed by the app. Shared Kotlin owns live document rules and the simulator.

The archived TypeScript and generated JavaScript preserve the previous engine and its regression tests as a comparison reference. PDF.js/Tesseract and their licenses are retained to keep the original reader tests runnable; the app now uses native PDF extraction and OCR.

For reference maintenance only: `npm ci --ignore-scripts`, `npm run check:reference`, `npm test`. Explicit `npm run generate:reference` regenerates test-only JavaScript from this directory's source, never mobile assets. Do not change frozen Kotlin migration outputs merely to accept a behavioral difference. Add deliberate native regressions when changing supported formats or calculations.
