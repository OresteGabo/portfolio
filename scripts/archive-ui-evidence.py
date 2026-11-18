#!/usr/bin/env python3
"""Preserve compiler report names inside one upload-safe archive."""
import argparse
from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[1]
REPORTS = (
    'app/shared/build/reports/compose*/',
    'app/shared/build/reports/tests/',
    'core/build/reports/tests/',
    'app/androidApp/build/reports/lint-results-debug.*',
)


def archive(root, output):
    root, output = root.resolve(), output.resolve()
    files = set()
    for pattern in REPORTS:
        for match in root.glob(pattern):
            for candidate in match.rglob('*') if match.is_dir() else [match]:
                if (candidate.is_file() and not candidate.is_symlink()
                        and candidate.resolve().is_relative_to(root)
                        and candidate.resolve() != output):
                    files.add(candidate)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(output, 'w:gz') as result:
        for source in sorted(files):
            result.add(source, arcname=source.relative_to(root).as_posix(), recursive=False)
    return len(files)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    print(f'Archived {archive(ROOT, args.output)} report files into {args.output}')
