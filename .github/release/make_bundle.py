"""Bundle a fresh Maven file deployment, omitting repository-level metadata."""

import hashlib
from pathlib import Path
import sys
from zipfile import ZIP_DEFLATED, ZipFile


def make_bundle(repository, version, output):
    repository = Path(repository)
    files = sorted(
        path
        for path in repository.rglob("*")
        if path.is_file() and path.parent.name == version
    )
    assert files, "No deployed files found"
    with ZipFile(output, "w", compression=ZIP_DEFLATED) as archive:
        for path in files:
            if path.suffix in (".md5", ".sha1", ".sha256", ".sha512"):
                continue
            name = path.relative_to(repository).as_posix()
            data = path.read_bytes()
            archive.writestr(name, data)
            for algorithm in ("md5", "sha1", "sha256", "sha512"):
                archive.writestr(
                    name + "." + algorithm, hashlib.new(algorithm, data).hexdigest()
                )


if __name__ == "__main__":
    make_bundle(*sys.argv[1:])
