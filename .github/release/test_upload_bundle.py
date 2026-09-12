"""Exercise release safety without contacting Central or using real credentials."""

import io
import json
from pathlib import Path
import tempfile
import unittest
import urllib.request
from unittest.mock import patch
from zipfile import ZipFile

from upload_bundle import SafeRedirectHandler, upload


class UploadTests(unittest.TestCase):
    def run_upload(self, state="VALIDATED", downloaded=b"synthetic artifact"):
        calls = []

        def respond(request, timeout):
            calls.append(request)
            if "/upload?" in request.full_url:
                return io.BytesIO(b"00000000-0000-4000-8000-000000000001")
            if "/status?" in request.full_url:
                return io.BytesIO(json.dumps({"deploymentState": state}).encode())
            return io.BytesIO(downloaded)

        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory) / "bundle.zip"
            with ZipFile(bundle, "w") as archive:
                archive.writestr(
                    "example/fixture/1.0.0/fixture-1.0.0.jar", b"synthetic artifact"
                )
            with (
                patch.dict(
                    "os.environ",
                    {"CENTRAL_USERNAME": "synthetic", "CENTRAL_PASSWORD": "synthetic"},
                ),
                patch("urllib.request.OpenerDirector.open", side_effect=respond),
            ):
                upload(bundle)
        return calls

    def test_upload_requires_manual_publication_and_checks_download(self):
        calls = self.run_upload()
        self.assertEqual(len(calls), 3)
        self.assertIn("publishingType=USER_MANAGED", calls[0].full_url)
        self.assertEqual([call.method for call in calls], ["POST", "POST", "GET"])

    def test_failed_validation_stops(self):
        with self.assertRaises(SystemExit):
            self.run_upload(state="FAILED")

    def test_unexpected_publication_stops(self):
        with self.assertRaises(SystemExit):
            self.run_upload(state="PUBLISHED")

    def test_changed_remote_artifact_stops(self):
        with self.assertRaisesRegex(SystemExit, "differ"):
            self.run_upload(downloaded=b"different synthetic artifact")

    def test_redirect_does_not_forward_credentials_to_storage(self):
        request = urllib.request.Request(
            "https://central.sonatype.com/example",
            headers={"Authorization": "Bearer synthetic"},
        )
        redirected = SafeRedirectHandler().redirect_request(
            request, None, 302, "Found", {}, "https://storage.example.invalid/artifact"
        )
        self.assertIsNone(redirected.get_header("Authorization"))


if __name__ == "__main__":
    unittest.main()
