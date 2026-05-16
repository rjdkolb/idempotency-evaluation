# /// script
# requires-python = ">=3.11"
# dependencies = ["httpx", "pytest", "pytest-asyncio"]
# ///
"""
Conformance suite for draft-ietf-httpapi-idempotency-key-header-07.

Each test maps to one row in the README comparison table. The RFC 2119
keyword and section number are attached via ``@pytest.mark.spec`` and
printed next to each outcome. Per-implementation known divergences are
pre-marked ``xfail(strict=True)`` so the suite exits 0 while still
surfacing every mismatch as XFAIL.

Run:
    uv run test_idempotency.py --impl=arun0009lib -v
    IDEMPOTENCY_BASE_URL=$(cat aws-powertools/url.txt) \\
        uv run test_idempotency.py --impl=aws-powertools -v
"""
from __future__ import annotations

import asyncio
import os
import sys
import uuid
from typing import AsyncIterator

import httpx
import pytest
import pytest_asyncio

BASE_URL = os.environ.get("IDEMPOTENCY_BASE_URL", "http://localhost:8080").rstrip("/")
URL = f"{BASE_URL}/orders/idempotent"

IMPLS = ("arun0009lib", "aws-powertools")

# Per-implementation divergences from draft-07. Keys are test function names;
# matching tests are pre-marked xfail(strict=True) so a fixed divergence will
# surface as XPASS instead of silently turning green.
KNOWN_DIVERGENCES: dict[str, dict[str, str]] = {
    "arun0009lib": {
        "test_in_flight_duplicate_returns_conflict":
            "blocks until first completes, then replays 200",
        "test_key_reuse_different_payload_returns_422":
            "key reuse not validated; replays original 200",
    },
    "aws-powertools": {
        "test_missing_key_returns_400":
            "falls back to SHA-256 of body, returns 200",
        "test_key_reuse_different_payload_returns_422":
            "replays original 200, ignores payload change",
    },
}

pytestmark = pytest.mark.asyncio


# ---------- pytest plumbing ----------

def pytest_addoption(parser: pytest.Parser) -> None:
    parser.addoption(
        "--impl",
        action="store",
        choices=IMPLS,
        default=None,
        help="Implementation under test; selects the xfail set",
    )


def pytest_configure(config: pytest.Config) -> None:
    config.addinivalue_line(
        "markers",
        "spec(keyword, section): RFC 2119 keyword + section in draft-07",
    )


def pytest_sessionstart(session: pytest.Session) -> None:
    if session.config.option.collectonly:
        return
    try:
        httpx.get(f"{BASE_URL}/orders", timeout=15)
    except (httpx.ConnectError, httpx.TimeoutException) as exc:
        print(
            f"\nNo implementation is running at {BASE_URL}.\n"
            f"Start arun0009lib with:\n"
            f"  cd arun0009lib && docker compose up --build\n\n"
            f"Or point at a deployed implementation:\n"
            f"  IDEMPOTENCY_BASE_URL=https://… uv run test_idempotency.py --impl=...\n\n"
            f"{exc}",
            file=sys.stderr,
        )
        pytest.exit("harness not reachable", returncode=2)


def pytest_collection_modifyitems(
    config: pytest.Config, items: list[pytest.Item]
) -> None:
    impl = config.getoption("--impl")
    divergences = KNOWN_DIVERGENCES.get(impl, {})
    for item in items:
        reason = divergences.get(item.name)
        if reason:
            item.add_marker(
                pytest.mark.xfail(reason=f"{impl}: {reason}", strict=True)
            )


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    report = outcome.get_result()
    marker = item.get_closest_marker("spec")
    if marker and len(marker.args) >= 2:
        report.spec_label = f"{marker.args[0]} {marker.args[1]}"


def pytest_report_teststatus(report, config):
    if report.when != "call":
        return None
    label = getattr(report, "spec_label", None)
    if not label:
        return None
    if hasattr(report, "wasxfail"):
        if report.skipped:
            return "xfailed", "x", f"XFAIL [{label}] ({report.wasxfail})"
        if report.passed:
            return "xpassed", "X", f"XPASS [{label}]"
    if report.passed:
        return "passed", ".", f"PASSED [{label}]"
    if report.failed:
        return "failed", "F", f"FAILED [{label}]"
    if report.skipped:
        return "skipped", "s", f"SKIPPED [{label}]"
    return None


# ---------- fixtures ----------

@pytest_asyncio.fixture
async def client() -> AsyncIterator[httpx.AsyncClient]:
    async with httpx.AsyncClient(timeout=30) as c:
        yield c


def _key() -> str:
    return uuid.uuid4().hex


def _ref() -> str:
    return f"customer-{uuid.uuid4().hex[:8]}"


# ---------- tests (one per row in README comparison table) ----------

@pytest.mark.spec("MUST", "§2.1")
@pytest.mark.skip(reason="Client-side: the header is a Structured Header String by construction.")
async def test_key_is_structured_header_string():
    pass


@pytest.mark.spec("RECOMMENDED", "§2.2")
@pytest.mark.skip(reason="Client-side: tests use uuid4() so the recommendation holds.")
async def test_uuid_recommended_for_key():
    pass


@pytest.mark.spec("SHOULD", "§2.3")
@pytest.mark.skip(reason="Documentation check: arun0009lib uses PT1H; aws-powertools uses 1h DynamoDB TTL.")
async def test_expiration_policy_published():
    pass


@pytest.mark.spec("MAY", "§2.4")
@pytest.mark.skip(reason="Optional; neither implementation supports a separate fingerprint.")
async def test_fingerprint_supported():
    pass


@pytest.mark.spec("MUST", "§2.5.2")
@pytest.mark.skip(reason="Documentation check: arun0009lib publishes Swagger; aws-powertools does not publish a spec.")
async def test_server_publishes_idempotency_spec():
    pass


@pytest.mark.spec("SHOULD", "§2.6")
async def test_first_request_processes_normally(client: httpx.AsyncClient):
    response = await client.post(
        URL,
        params={"delayMillis": 0},
        headers={"Idempotency-Key": _key()},
        json={"customerOrderReference": _ref()},
    )
    assert response.status_code == 200, response.text


@pytest.mark.spec("SHOULD", "§2.6")
async def test_completed_duplicate_replays_original(client: httpx.AsyncClient):
    key = _key()
    payload = {"customerOrderReference": _ref()}

    first = await client.post(
        URL, params={"delayMillis": 0},
        headers={"Idempotency-Key": key}, json=payload,
    )
    assert first.status_code == 200, first.text

    second = await client.post(
        URL, params={"delayMillis": 0},
        headers={"Idempotency-Key": key}, json=payload,
    )
    assert second.status_code == 200, second.text
    assert second.text == first.text, "duplicate must replay the original body"


@pytest.mark.spec("SHOULD", "§2.6")
async def test_in_flight_duplicate_returns_conflict(client: httpx.AsyncClient):
    key = _key()
    payload = {"customerOrderReference": _ref()}

    async def _post() -> httpx.Response:
        return await client.post(
            URL, params={"delayMillis": 5000},
            headers={"Idempotency-Key": key}, json=payload,
        )

    responses = await asyncio.gather(_post(), _post(), _post())
    statuses = [r.status_code for r in responses]
    assert any(s == 200 for s in statuses), \
        f"expected a 200 for the first call, got {statuses}"
    assert any(s == 409 for s in statuses), \
        f"expected 409 for in-flight duplicate, got {statuses}"


@pytest.mark.spec("SHOULD", "§2.7")
async def test_missing_key_returns_400(client: httpx.AsyncClient):
    response = await client.post(
        URL, params={"delayMillis": 0},
        json={"customerOrderReference": _ref()},
    )
    assert response.status_code == 400, \
        f"expected 400 for missing Idempotency-Key, got {response.status_code}: {response.text}"


@pytest.mark.spec("SHOULD", "§2.7")
async def test_key_reuse_different_payload_returns_422(client: httpx.AsyncClient):
    key = _key()
    first = await client.post(
        URL, params={"delayMillis": 0},
        headers={"Idempotency-Key": key},
        json={"customerOrderReference": _ref()},
    )
    assert first.status_code == 200, first.text

    second = await client.post(
        URL, params={"delayMillis": 0},
        headers={"Idempotency-Key": key},
        json={"customerOrderReference": _ref()},
    )
    assert second.status_code == 422, \
        f"expected 422 for reused key with different payload, got {second.status_code}: {second.text}"


if __name__ == "__main__":
    # Load this file as a plugin so the hooks above (pytest_addoption, etc.)
    # are picked up — pytest only auto-discovers hooks in conftest.py or plugins.
    sys.exit(pytest.main([__file__, "-p", "test_idempotency", *sys.argv[1:]]))
