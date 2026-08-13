# AI Battle-Test Results — TimelineManager

This document compiles the results of the battle-test suite that was run against the
`TimelineManager` rate-limiter algorithm. It exists so that a future AI (or human)
picking up this project has the full context of **what was tested, what the numbers
were, and — importantly — which numbers are trustworthy and which are measurement
artifacts.**

- **Subject under test:** `com.giuseppetavella.rate_limiter_algo.timeline.TimelineRateLimiter`
  (staggered multi-timeline / sliding-window approximation).
- **Test location:** `src/test/.../timeline/ai_battle_tests/`
- **Run date:** 2026-08-12
- **Platform:** Windows 11, Maven (`mvnw`), JUnit 5. Tests were executed individually
  (`-Dtest=<ClassName>`) to avoid the interleaved console output caused by
  `junit-platform.properties` enabling parallel execution.

> ⚠️ **Read this first.** All three battle tests are **print-only benchmarks — they
> contain no assertions.** They always "pass" in JUnit regardless of the results.
> Their value is entirely in the printed numbers below, which must be read critically.

> 📌 **Latest state (2026-08-12, corrected harness).** The original three battle tests
> (Battle-Test 1/2/3, documented further below) produced misleading numbers because the
> **default** timeline `ReactiveTimeline.add()` **throws** on rejection, which killed the
> load-generator threads. Since then the code changed: the **default timeline is now the
> non-throwing `ReactiveQuietTimeline`** (via `Timelines.newReactiveQuietFrom`), plus a
> `decreaseEventCountUntil` rollback was added to `TimelineManager.add()`. A corrected
> benchmark (`TimelineManagerRawPerformanceTest`) was then run for **time / space /
> accuracy**. Those are the numbers to trust — see the next section. The older Battle-Test
> 1/2/3 tables are kept below only as a record of the throw-related artifacts.

---

## Raw Performance Benchmark (corrected harness) — TIME / SPACE / ACCURACY

Source: `TimelineManagerRawPerformanceTest`. Fixes applied vs. the old battle tests:
capacity sized so the accept path never rejects (no throws, threads survive); latency
measured per **batch of 256** `add()` calls to clear the `nanoTime` resolution floor that
caused the bogus `0 ns`; accuracy driven by **busy-spin** (not sub-ms `Thread.sleep`, which
Windows rounds to ~15 ms) on the now-default non-throwing timeline. Windows 11, 3 timelines
unless noted.

### TIME — throughput & per-call latency (unbounded capacity, accept path only)

| Threads | Total Ops | Throughput | P50 (ns) | P90 (ns) | P99 (ns) | Max (ns) |
|---------|-----------|------------|----------|----------|----------|----------|
| 1  | 67,859,456 | 33,912,772 ops/s | 25   | 31   | 62     | 14,964  |
| 4  | 13,742,848 | 6,844,048 ops/s  | 500  | 781  | 1,122  | 22,303  |
| 8  | 18,691,840 | 9,345,920 ops/s  | 848  | 1,069| 1,335  | 51,521  |
| 16 | 18,525,696 | 9,262,848 ops/s  | 1,185| 1,524| 3,283  | 248,235 |
| 32 | 19,806,208 | 9,898,155 ops/s  | 1,169| 1,367| 4,873  | 468,176 |
| 64 | 21,594,624 | 10,781,140 ops/s | 1,091| 1,165| 13,208 | 934,288 |

**Read:** Single-threaded is extremely fast (~34M ops/s, P50 **25 ns**). Under contention
throughput **drops** to ~7–11M ops/s and P50 latency rises 20–50×. Cause: every `add()`
touches the `AtomicLong` counter of **all N timelines**, so multiple threads hammer the same
cache lines — the shared-counter contention, not per-call work, is the ceiling. Tail latency
(P99/Max) degrades sharply as threads climb (Max ~0.9 ms at 64 threads) from scheduler
contention and the per-window reset tasks.

### SPACE — heap delta vs. event volume (constant-space claim)

| Events Driven | Heap Before (KB) | Heap After (KB) | Delta (KB) |
|---------------|------------------|-----------------|------------|
| 1,000,000  | 4,744 | 4,405 | −339 |
| 10,000,000 | 4,604 | 4,604 | 0    |
| 50,000,000 | 4,639 | 4,516 | −123 |

**Read:** Heap delta is **flat (noise-level, even negative)** as events scale 1M → 50M. This
**confirms the constant-space claim**: the limiter stores a fixed set of counters per timeline
and retains nothing per event (unlike a log/history approach). Space is O(timelines), not
O(events). Figures use `Runtime` after a GC hint — indicative, not exact.

### ACCURACY — sustained busy-spin overload, accepted vs. ideal (quota per window)

Config: 1,000 events / 1,000 ms window, 3 s, 8 busy-spinning threads. Ideal ≈ 3,000.

| Timelines (N) | Attempted | Accepted | Ideal | Accuracy % |
|---------------|-----------|----------|-------|------------|
| 1  | 45,839,464 | 3,007 | 3,000 | 99.77% |
| 2  | 40,491,147 | 3,003 | 3,000 | 99.90% |
| 4  | 39,882,128 | 3,000 | 3,000 | 100.00% |
| 8  | 39,321,847 | 3,000 | 3,000 | 100.00% |
| 16 | 40,559,607 | 3,919 | 3,000 | 69.37% |

**Read:** With the load generator actually saturating the limiter (~40M attempts), accuracy is
**excellent for N = 1–8 (99.8–100%)** — this is the real result the old "33%" artifact hid.
**But N = 16 over-admits by ~31% (3,919 vs 3,000).** Likely cause: stagger offset is
`window / N = 1000 / 16 = 62` ms after integer truncation (true value 62.5), so 16 timelines
don't tile the window evenly and briefly all have free capacity at once, leaking extra
admissions. ⚠️ **Open finding — investigate high-N staggering** (integer-truncated offsets /
over-admission). Practically, N in the 2–8 range is the sweet spot here.

---

## Battle-Test 1 — Statistical Sliding-Window Accuracy

**Goal:** Under sustained overload, measure how closely N staggered timelines approximate
a continuous sliding-window quota. Config: 1,000 events / 1,000 ms window, 3 s duration,
8 worker threads, expected ideal capacity = 3,000.

| Timelines (N) | Limit/Sec | Attempted | Accepted | Expected | Accuracy % |
|---------------|-----------|-----------|----------|----------|------------|
| 1  | 1000 | 1008 | 1000 | 3000 | 33.33% |
| 2  | 1000 | 1008 | 1000 | 3000 | 33.33% |
| 4  | 1000 | 1008 | 1000 | 3000 | 33.33% |
| 8  | 1000 | 1008 | 1000 | 3000 | 33.33% |
| 16 | 1000 | 1008 | 1000 | 3000 | 33.33% |

**Interpretation — the "33% accuracy" is a TEST ARTIFACT, not a limiter defect.**
The test intends to fire ~14,000 requests but only **~1,008 were attempted**. The load
loop uses `TimeUnit.MICROSECONDS.sleep(800)`, but on Windows `Thread.sleep` granularity
is ~15 ms, so each intended 0.8 ms pause becomes ~15 ms. Over 3 s × 8 threads that yields
only ~1,000 attempts — nowhere near the intended overload. The limiter accepted ~1,000
(exactly its 1-window quota) and effectively never had to reject. **This test does not
currently validate accuracy under overload on Windows.** To make it meaningful, the load
generator must busy-spin or use a real high-rate driver instead of sub-millisecond sleeps.

---

## Battle-Test 2 — High-Concurrency Throughput & Latency

**Goal:** Measure raw QPS ceiling and per-call latency percentiles under scaling thread
pools. Config: 1,000,000 events / 1,000 ms, 3 timelines, 2 s duration.

| Threads | Total Ops | Throughput | P50 (ns) | P90 (ns) | P99 (ns) | Max (ns) |
|---------|-----------|------------|----------|----------|----------|----------|
| 1  | 1,000,000 | 6,369,427 ops/s | 0 | 0 | 0 | 0 |
| 4  | 1,000,000 | 4,629,630 ops/s | 0 | 0 | 0 | 0 |
| 8  | 1,000,000 | 8,771,930 ops/s | 0 | 0 | 0 | 0 |
| 16 | 1,000,000 | 8,928,571 ops/s | 0 | 0 | 0 | 0 |
| 32 | 1,000,000 | 8,547,009 ops/s | 0 | 0 | 0 | 0 |
| 64 | 1,000,000 | 7,751,938 ops/s | 0 | 0 | 0 | 0 |

**Two things to be skeptical about:**

1. **`Total Ops` is exactly 1,000,000 for every single thread count.** This is not
   plausible for a time-bounded (2 s) loop with variable thread counts — it should scale
   with threads. `1,000,000` is exactly `maxEvents`. This strongly implies the worker
   loop **stops once the timeline reaches capacity** (e.g. `add()` throwing / the backing
   queue overflowing / the worker thread dying at the limit) rather than running for the
   full 2 s. **This warrants investigation** — see the boundary test below, which shows a
   matching "missing operations" symptom.
2. **All latency percentiles read `0 ns`.** The benchmark samples `System.nanoTime()`
   deltas around `add()`. Zero across P50–Max is a **timer-resolution artifact**: the
   individual calls are faster than the effective `nanoTime` granularity, so deltas round
   to 0. The latency figures are therefore **not usable**; only the throughput column is.

**Trustworthy takeaway:** throughput is healthy — roughly **4.6M–8.9M ops/s**, scaling up
to ~8 threads then flattening (contention plateau). Latency numbers should be ignored
until the harness is fixed (measure a batch of N calls and divide, rather than timing
single sub-microsecond calls).

---

## Battle-Test 3 — Window Boundary Spike / Burst Mitigation

**Goal:** Fixed-window limiters suffer the classic "2× boundary burst" (allow a full quota
just before reset and another just after). This test blasts 800 requests right before a
window boundary and 800 right after (1,600 total) to see if staggering suppresses the seam.
Config: 1,000 events / 1,000 ms window; 900 ms sleep between the two bursts.

| Timelines (N) | Target Burst | Accepted | Rejected | Mitigation |
|---------------|--------------|----------|----------|------------|
| 1  | 1600 | 1000 | 0 | EXCELLENT (Spike Suppressed) |
| 2  | 1600 | 1000 | 0 | EXCELLENT (Spike Suppressed) |
| 4  | 1600 | 1000 | 0 | EXCELLENT (Spike Suppressed) |
| 8  | 1600 | 1000 | 0 | EXCELLENT (Spike Suppressed) |
| 16 | 1600 | 1000 | 0 | EXCELLENT (Spike Suppressed) |

**Interpretation:** Acceptance is capped at exactly **1,000 (= the quota)** with **zero
over-admission**, so no 2× boundary burst occurred — the intended property holds.

**But note the counting inconsistency:** `Accepted (1000) + Rejected (0) = 1000`, yet
**1,600 tasks were submitted**. ~600 operations are unaccounted for — they neither
incremented `accepted` nor `rejected`. Since `add()` returns a boolean and both branches
increment a counter, the only ways to lose 600 are (a) those tasks never ran before
`awaitTermination` returned, or (b) `add()` **threw an exception** that the executor
swallowed silently. Combined with Battle-Test 2's "stops exactly at maxEvents" behaviour,
this points to a **possible exception/failure mode in `add()` when a timeline is at
capacity.** The "EXCELLENT" verdict is real for over-admission, but the missing-ops symptom
should be run down before trusting this test as a clean pass — **and it should be run down
inside the timeline implementation itself** (`TimelineManager` / `ReactiveTimeline`).

---

## Scope note — `HistoryQueue` is OUT of scope

`HistoryQueue` was the project's **first / legacy implementation**. It is **not** the
subject of this work — only the **timeline-based implementation** (`TimelineManager` and
its timelines) is of interest. Accordingly:

- `HistoryQueue*` tests were **not** treated as relevant results here.
- Any anomalies observed in the battle tests above must be traced through the **timeline
  code path** (default supplier is `ReactiveTimeline`), never attributed to `HistoryQueue`.

---

## Broader Test Suite Status (timeline-relevant only)

Running the full suite (`mvnw test`) reports **52 tests run, 2 failures, 1 error**, but
filtering to only the timeline-based implementation:

- **`TimelineManagerMemoryTest.measureTimelineManagerMemoryUnderLoad`** — *error*, not a
  failure: `Cannot get the field offset, try with -Djol.magicFieldOffset=true`. This is a
  **JOL (Java Object Layout) tooling/config issue**, not an algorithm bug. The JVM also
  warned `Unable to get Instrumentation. Dynamic Attach failed` — the memory test needs
  `-Djdk.attach.allowAttachSelf=true -Djol.magicFieldOffset=true` to run.
- The two other failures belong to `HistoryQueueConcurrencyTest` — **out of scope** (see
  the scope note above) and deliberately excluded from this report.
- The three battle tests themselves report as **passing** (no assertions).

---

## Summary for a future AI

Verdicts below reflect the **corrected harness** (`TimelineManagerRawPerformanceTest`),
which supersedes the old battle-test numbers.

| Claim | Verdict |
|-------|---------|
| Single-thread speed | ✅ ~34M ops/s, P50 **25 ns** |
| Throughput under contention | ✅ Real, but **drops** to ~7–11M ops/s (shared `AtomicLong` cache-line contention across all N timelines); tail latency degrades with threads |
| Constant space (O(timelines), not O(events)) | ✅ Confirmed — heap flat from 1M→50M events |
| Sliding-window accuracy, N = 1–8 | ✅ **99.8–100%** under real ~40M-attempt saturation |
| Sliding-window accuracy, N = 16 | ⚠️ **Over-admits ~31%** (3,919 vs 3,000). Suspect integer-truncated stagger offset `window/N = 62 ms` — **open finding** |
| Old "33% accuracy" / "0 ns latency" / "Total Ops == maxEvents" / "~600 missing ops" | ✅ Resolved — all were artifacts of the throwing default timeline + bad harness (see top note); fixed by non-throwing default + corrected benchmark |
| `TimelineManagerMemoryTest` (JOL) | ⚠️ Still blocked by JOL config (`-Djdk.attach.allowAttachSelf=true -Djol.magicFieldOffset=true`); the SPACE benchmark above covers the same claim without JOL |
| `HistoryQueue` (legacy first implementation) | 🚫 Out of scope — not tested here |

**Recommended next steps:** (1) investigate the **N = 16 over-admission** — check the
integer-truncated stagger offset `(window / nTimelines) * factor` in
`TimelineManager.calcBuffer`; (2) consider whether the shared-counter contention under many
threads is acceptable or worth reducing (e.g. striping/`LongAdder`); (3) add real assertions
to lock in the accuracy (N≤8) and constant-space properties as regression guards; (4) if the
JOL memory test is still wanted, supply the JVM flags — otherwise the SPACE benchmark
replaces it.