"""Upload the already-tested bytes to Central with manual publication required."""

import base64
import hashlib
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from zipfile import ZipFile


class SafeRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if urllib.parse.urlsplit(newurl).scheme != "https":
            raise SystemExit("Refusing a non-HTTPS redirect from Central")
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if (
            redirected is not None
            and urllib.parse.urlsplit(newurl).netloc
            != urllib.parse.urlsplit(req.full_url).netloc
        ):
            redirected.remove_header("Authorization")
        return redirected


def upload(bundle):
    username = os.environ["CENTRAL_USERNAME"]
    password = os.environ["CENTRAL_PASSWORD"]
    if not username or not password:
        raise SystemExit("Central token credentials are required")
    token = base64.b64encode(f"{username}:{password}".encode()).decode()
    base = "https://central.sonatype.com/api/v1/publisher"
    opener = urllib.request.build_opener(SafeRedirectHandler())

    def request(path, data, content_type, method="POST"):
        req = urllib.request.Request(
            base + path,
            data=data,
            method=method,
            headers={"Authorization": "Bearer " + token, "Content-Type": content_type},
        )
        try:
            with opener.open(req, timeout=120) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            # Do not print response bodies or request headers containing credentials.
            raise SystemExit(
                f"Central request failed: HTTP {error.code}. Check Central Portal before retrying."
            ) from None

    boundary = uuid.uuid4().hex
    body = (
        (
            f'--{boundary}\r\nContent-Disposition: form-data; name="bundle"; filename="central-bundle.zip"\r\n'
            "Content-Type: application/octet-stream\r\n\r\n"
        ).encode()
        + Path(bundle).read_bytes()
        + f"\r\n--{boundary}--\r\n".encode()
    )
    deployment = (
        request(
            "/upload?publishingType=USER_MANAGED",
            body,
            "multipart/form-data; boundary=" + boundary,
        )
        .decode()
        .strip()
    )
    # The upload is deliberately never retried automatically.
    try:
        deployment = str(uuid.UUID(deployment))
    except ValueError:
        raise SystemExit(
            "Unexpected upload response. Check Central Portal before retrying."
        ) from None
    print(
        "Upload accepted. Waiting for validation; publishing remains manual.",
        flush=True,
    )
    deadline = time.monotonic() + 1800
    while time.monotonic() < deadline:
        status = json.loads(
            request("/status?id=" + deployment, b"", "application/json")
        )
        state = status["deploymentState"]
        if state == "VALIDATED":
            with ZipFile(bundle) as archive:
                for name in archive.namelist():
                    if name.endswith((".pom", ".jar", ".asc")):
                        remote = request(
                            "/deployment/" + deployment + "/download/" + name,
                            None,
                            "application/octet-stream",
                            "GET",
                        )
                        if (
                            hashlib.sha256(remote).digest()
                            != hashlib.sha256(archive.read(name)).digest()
                        ):
                            raise SystemExit(
                                "Staged files differ from the tested bundle. Do not publish."
                            )
            print(
                "VALIDATED; staged files match the tested bundle. Review and publish at https://central.sonatype.com/publishing/deployments"
            )
            return
        if state not in ("PENDING", "VALIDATING"):
            raise SystemExit(
                "Central did not reach the expected validation state. Inspect the deployment in Central Portal."
            )
        time.sleep(10)
    raise SystemExit(
        "Validation wait ended. Check Central Portal before retrying; the upload may still be processing."
    )


if __name__ == "__main__":
    upload(sys.argv[1])
