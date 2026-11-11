# Required extended Material icons

These 75 vector definitions are copied unchanged from the official
`org.jetbrains.compose.material:material-icons-extended:1.7.3` sources JAR.
Each Kotlin file preserves its Android Open Source Project copyright and
Apache 2.0 license notice. Their package names, geometry, and auto-mirroring
behavior are unchanged. The smaller `material-icons-core` library supplies
common icons and the vector builders.

The full extended library contains thousands of unused icons and substantially
increases unminified/debug APK size. Only the extended icons referenced by this
project are included here.

Regenerate after adding or removing icon usages:

```sh
python3 scripts/sync-material-icons.py /path/to/material-icons-extended-1.7.3-sources.jar
```

The sources JAR is published in Maven Central under the coordinates above.
