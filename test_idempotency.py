# /// script
# requires-python = ">=3.11"
# dependencies = ["httpx", "rich"]
# ///
"""
Idempotency test for the arun0009lib harness.

Hits POST /orders/idempotent?delayMillis=5000 with two scenarios:
  1. in-flight  — 5 duplicate requests sent so they overlap during the 5s window
  2. completed  — a duplicate request sent AFTER the first one has returned

An idempotent endpoint should return the same 200 response in both cases.
"""
import asyncio
import os
import time
import uuid

import httpx
from rich.console import Console
from rich.panel import Panel
from rich.table import Table

BASE_URL = os.environ.get("IDEMPOTENCY_BASE_URL", "http://localhost:8080").rstrip("/")
URL = f"{BASE_URL}/orders/idempotent"
DELAY_MS = 5000
N_DUPLICATES = 5

console = Console()


# ---------- One HTTP request ----------

async def send_request(client: httpx.AsyncClient, reference: str, idempotency_key: str) -> dict:
    """Send one POST and return what came back."""
    started = time.perf_counter()
    response = await client.post(
        URL,
        params={"delayMillis": DELAY_MS},
        headers={"Idempotency-Key": idempotency_key},
        json={"customerOrderReference": reference},
    )
    return {
        "status": response.status_code,
        "body": response.text,
        "elapsed": time.perf_counter() - started,
    }


# ---------- The two scenarios ----------

async def scenario_in_flight(client: httpx.AsyncClient) -> list[dict]:
    """Fire 5 duplicates at the same time so they all overlap in flight."""
    reference = f"customer-{uuid.uuid4().hex[:8]}"
    key = uuid.uuid4().hex
    duplicates = [send_request(client, reference, key) for _ in range(N_DUPLICATES)]
    return await asyncio.gather(*duplicates)


async def scenario_completed(client: httpx.AsyncClient) -> list[dict]:
    """Fire one request, wait for it to finish, then fire a duplicate."""
    reference = f"customer-{uuid.uuid4().hex[:8]}"
    key = uuid.uuid4().hex
    first = await send_request(client, reference, key)
    duplicate = await send_request(client, reference, key)
    return [first, duplicate]


# ---------- Display ----------

def show_results(title: str, results: list[dict]) -> None:
    table = Table(title=title)
    table.add_column("#", justify="right")
    table.add_column("Status", justify="right")
    table.add_column("Elapsed", justify="right")
    table.add_column("Response body")
    for i, r in enumerate(results, start=1):
        body = r["body"] if len(r["body"]) <= 70 else r["body"][:67] + "…"
        status_style = "green" if r["status"] == 200 else "red"
        table.add_row(
            str(i),
            f"[{status_style}]{r['status']}[/{status_style}]",
            f"{r['elapsed']:.2f}s",
            body,
        )
    console.print(table)

    # If any request didn't succeed, show its full body so the cause is visible.
    failures = [r for r in results if r["status"] != 200]
    for i, r in enumerate(results, start=1):
        if r in failures:
            console.print(f"  [red]#{i} full body:[/red] {r['body']}")
    if failures:
        console.print()

    all_200 = all(r["status"] == 200 for r in results)
    one_body = len({r["body"] for r in results}) == 1
    if all_200 and one_body:
        console.print("  → [bold green]idempotent ✓[/bold green]\n")
    else:
        console.print("  → [bold red]NOT idempotent ✗[/bold red]\n")


def show_app_not_started(error: Exception) -> None:
    console.print(Panel(
        f"[bold red]No implementation is running at {BASE_URL}.[/bold red]\n\n"
        f"Start one (e.g. arun0009lib) with:\n"
        f"  [bold]cd arun0009lib && docker compose up --build[/bold]\n\n"
        f"Or point at a deployed implementation:\n"
        f"  [bold]IDEMPOTENCY_BASE_URL=https://… uv run test_idempotency.py[/bold]\n\n"
        f"[dim]{error}[/dim]",
        title="App not started",
        border_style="red",
    ))


def show_request_failed(error: Exception) -> None:
    console.print(Panel(
        f"[bold red]Request failed: {error.__class__.__name__}[/bold red]\n\n"
        f"[dim]{error}[/dim]",
        title="Error",
        border_style="red",
    ))


# ---------- Main ----------

async def main() -> int:
    async with httpx.AsyncClient(timeout=30) as client:
        # 1. Check the harness is up before we start.
        try:
            await client.get(f"{BASE_URL}/orders", timeout=15)
        except (httpx.ConnectError, httpx.TimeoutException) as exc:
            show_app_not_started(exc)
            return 2

        console.print(Panel.fit(
            f"Testing [bold]POST {URL}?delayMillis={DELAY_MS}[/bold]\n"
            f"Each scenario uses a fresh customerOrderReference.",
            title="Idempotency test",
            border_style="blue",
        ))

        # 2. Run scenario 1: duplicates while the first is still processing.
        try:
            console.print(f"\n[cyan]Scenario 1[/cyan]: {N_DUPLICATES} duplicates "
                          f"fired together (all in flight during the {DELAY_MS}ms window)")
            with console.status("waiting for responses…"):
                results = await scenario_in_flight(client)
            show_results("In-flight duplicates", results)
        except httpx.HTTPError as exc:
            show_request_failed(exc)
            return 1

        # 3. Run scenario 2: duplicate sent AFTER the first one completes.
        try:
            console.print("[cyan]Scenario 2[/cyan]: duplicate fired AFTER the "
                          "first request has finished")
            with console.status("waiting for responses…"):
                results = await scenario_completed(client)
            show_results("Post-completion duplicates", results)
        except httpx.HTTPError as exc:
            show_request_failed(exc)
            return 1

        return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
