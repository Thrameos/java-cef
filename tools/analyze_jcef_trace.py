#!/usr/bin/env python3
# Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
# reserved. Use of this source code is governed by a BSD-style license that
# can be found in the LICENSE file.

"""Post-processes a native/jcef_trace.h REF-trace log (produced by a
JCEF_ENABLE_TRACE build run with JCEF_TRACE=1 set -- see native/jcef_trace.h)
to flag two classes of bug mechanically instead of hand-reading the log:

  - use-after-free-shaped events: a RELEASE/DEL for a pointer whose tracked
    refcount is already zero (more releases seen than acquires for that
    pointer so far).
  - leaked-reference-shaped events: a pointer with an outstanding positive
    refcount (more acquires than releases) when the trace ends.

Four ref kinds are tracked, each independently (see jni_scoped_helpers.h/.cpp
for the call sites): CEF_ADDREF/CEF_RELEASE (CefBaseRefCounted refcounting),
JNI_GREF_NEW/JNI_GREF_DEL (JNI global references), and JNI_LREF_NEW/
JNI_LREF_DEL (local refs owned by a ScopedJNIBase-derived type -- every
acquisition site calls the shared TraceAcquired() helper, and every release
goes through ~ScopedJNIBase()'s single destructor choke point, so this pair
is pair-matched by pointer exactly like the other two, not just a same-
pointer-twice heuristic). A same-pointer double-DEL with no matching NEW in
between is a real double-DeleteLocalRef -- local ref slot addresses ARE
legitimately reused across separate acquisitions once freed, which is
exactly why the old heuristic-only version of this check was noisy; proper
NEW/DEL pairing removes that ambiguity.

The separate analyze_local_ref_deletes() heuristic below (same-pointer-DEL-
twice-in-a-row) is now redundant with the JNI_LREF_NEW/DEL pair above and
kept only as a secondary cross-check in case some acquisition site is ever
added without going through TraceAcquired().

IMPORTANT CAVEAT: if the traced run crashed (rather than exiting normally),
outstanding "leaked" refs at end-of-trace are expected (the process died
before it could finish releasing them) and are NOT real leaks -- this script
detects whether the log's tail looks like a crash and downgrades end-of-trace
imbalance findings to informational in that case. Only trust "leak" findings
from a trace of a run that completed normally.

Usage:
  JCEF_ENABLE_TRACE build, then:
    JCEF_TRACE=1 <run your repro, capture stdout+stderr to a file>
    tools/analyze_jcef_trace.py <log file>
"""

import re
import sys
from collections import defaultdict

REF_RE = re.compile(r"REF kind=(\w+) ptr=(0x[0-9a-fA-F]+)")
CRASH_MARKERS = (
    "FATAL:",
    "DCHECK failed",
    "Check failed",
    "A fatal error has been detected",
    "SIGSEGV",
    "SIGABRT",
    "SIGTRAP",
)

# (acquire_kind, release_kind, label)
PAIRS = [
    ("CEF_ADDREF", "CEF_RELEASE",
     "CEF ref-counted object via SetCefForJNIObjectHelper (JNI-wrapper "
     "association only, NOT every CefRefPtr<T> -- see CEF_REFPTR_* below)"),
    ("JNI_GREF_NEW", "JNI_GREF_DEL", "JNI global reference"),
    ("JNI_LREF_NEW", "JNI_LREF_DEL", "JNI local reference (ScopedJNIBase-owned)"),
    ("CEF_REFPTR_ADDREF", "CEF_REFPTR_RELEASE",
     "CefRefPtr<T> usage traced via JCefRefPtr<T> (native/jcef_ref_ptr.h) "
     "-- only wherever a call site opted in, not every CefRefPtr<T> in the "
     "codebase"),
]


def looks_like_crash_tail(lines, tail_n=40):
    tail = "\n".join(lines[-tail_n:])
    return any(marker in tail for marker in CRASH_MARKERS)


def analyze_pair(events, acquire_kind, release_kind, label, crashed):
    """events: list of (line_no, kind, ptr) in file order."""
    balance = defaultdict(int)  # ptr -> outstanding count
    use_after_free = []  # (line_no, ptr)
    for line_no, kind, ptr in events:
        if kind == acquire_kind:
            balance[ptr] += 1
        elif kind == release_kind:
            if balance[ptr] <= 0:
                use_after_free.append((line_no, ptr))
            else:
                balance[ptr] -= 1

    leaked = {ptr: count for ptr, count in balance.items() if count > 0}

    print(f"=== {label} ===")
    print(f"  acquire ({acquire_kind}) events: "
          f"{sum(1 for _, k, _ in events if k == acquire_kind)}")
    print(f"  release ({release_kind}) events: "
          f"{sum(1 for _, k, _ in events if k == release_kind)}")

    if use_after_free:
        print(f"  ** {len(use_after_free)} USE-AFTER-FREE-shaped event(s) "
              f"(release with no matching outstanding acquire): **")
        for line_no, ptr in use_after_free[:20]:
            print(f"     line {line_no}: {release_kind} ptr={ptr} with no "
                  f"outstanding {acquire_kind}")
        if len(use_after_free) > 20:
            print(f"     ... and {len(use_after_free) - 20} more")
    else:
        print("  no use-after-free-shaped events")

    if leaked:
        severity = "informational (trace ends in a crash -- expected)" if crashed \
            else "** LIKELY REAL LEAK(S) **"
        print(f"  {len(leaked)} pointer(s) with outstanding acquires at "
              f"end of trace [{severity}]:")
        for ptr, count in list(leaked.items())[:20]:
            print(f"     ptr={ptr} outstanding={count}")
        if len(leaked) > 20:
            print(f"     ... and {len(leaked) - 20} more")
    else:
        print("  no outstanding (unreleased) references at end of trace")
    print()


def analyze_local_ref_deletes(events):
    """JNI_LREF_DEL only -- no creation event exists to pair against, so this
    can only flag the same pointer being deleted twice in a row with nothing
    else for it in between, as a heuristic candidate for a double-
    DeleteLocalRef bug (see render_handler.cpp's GetJNIScreenInfo() fix this
    same session for a real example of this exact bug class)."""
    last_del_line = {}
    suspects = []
    for line_no, kind, ptr in events:
        if kind != "JNI_LREF_DEL":
            continue
        if ptr in last_del_line:
            suspects.append((last_del_line[ptr], line_no, ptr))
        last_del_line[ptr] = line_no

    print("=== JNI local references (JNI_LREF_DEL only -- heuristic) ===")
    print(f"  delete events: {sum(1 for _, k, _ in events if k == 'JNI_LREF_DEL')}")
    if suspects:
        print(f"  {len(suspects)} pointer(s) deleted more than once with no "
              f"intervening event for that pointer -- CANDIDATE double-"
              f"DeleteLocalRef, but NOT proof (local ref slots are reused by "
              f"the JVM; only trust this if the two line numbers are close "
              f"together and clearly within the same call):")
        for first_line, second_line, ptr in suspects[:20]:
            print(f"     ptr={ptr}: deleted at line {first_line}, again at "
                  f"line {second_line}")
        if len(suspects) > 20:
            print(f"     ... and {len(suspects) - 20} more")
    else:
        print("  no repeated-delete candidates found")
    print()


def main():
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} <jcef_trace log file>", file=sys.stderr)
        sys.exit(1)

    path = sys.argv[1]
    with open(path, "r", errors="replace") as f:
        lines = f.readlines()

    crashed = looks_like_crash_tail(lines)

    events_by_kind_pair = []
    local_del_events = []
    for line_no, line in enumerate(lines, start=1):
        m = REF_RE.search(line)
        if not m:
            continue
        kind, ptr = m.group(1), m.group(2)
        events_by_kind_pair.append((line_no, kind, ptr))
        if kind == "JNI_LREF_DEL":
            local_del_events.append((line_no, kind, ptr))

    if not events_by_kind_pair:
        print("No 'REF kind=... ptr=...' lines found in this log -- was it "
              "captured from a JCEF_ENABLE_TRACE build run with JCEF_TRACE=1 "
              "set? See native/jcef_trace.h.", file=sys.stderr)
        sys.exit(1)

    print(f"Analyzing {path}: {len(events_by_kind_pair)} REF events, "
          f"{len(lines)} total lines.")
    print(f"Trace tail looks like a crash: {crashed} "
          f"({'end-of-trace leak findings are informational only' if crashed else 'end-of-trace leak findings are meaningful'})")
    print()

    for acquire_kind, release_kind, label in PAIRS:
        analyze_pair(events_by_kind_pair, acquire_kind, release_kind, label,
                     crashed)

    analyze_local_ref_deletes(local_del_events)


if __name__ == "__main__":
    main()
