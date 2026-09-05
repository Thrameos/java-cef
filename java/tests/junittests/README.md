# Test file naming convention

This directory mixes plain JUnit tests, native-coverage-closing supplements,
and shared test infrastructure. As of the 2026-09-04 naming audit
(`plan/tasks/20260903-18-normalize-test-file-naming.md`), the convention is:

## `<Class>Test.java` — direct test of one class

A straightforward JUnit test of `org.cef.../<Class>.java`. This is the
default and should be preferred whenever a class's public API is small
enough to test in one file. Example: `CefCookieTest.java` tests
`org.cef.network.CefCookie`.

## `<Class>CoverageTest.java` / `<Class>GapCoverageTest.java` — coverage-gap supplement

Used in two situations, both born out of this project's native-line-coverage
push:

- **Supplementing an existing `<Class>Test.java`** that's awkward to extend
  directly for a specific native code path (e.g. the shared-browser-harness
  friction that motivated `CefPostDataGapCoverageTest.java` alongside
  `CefPostDataTest.java`).
- **Closing gaps directly on a class that has no standalone `<Class>Test.java`**
  at all (e.g. `CefFocusHandlerCoverageTest.java`,
  `CefFrameHandlerCoverageTest.java`, `CefPermissionHandlerCoverageTest.java`,
  `CefRequestHandlerCoverageTest.java` — these handler interfaces are
  exercised incidentally by many other tests, so a dedicated
  `*HandlerTest.java` never existed; the coverage test is the only direct
  test).

`GapCoverageTest` vs. `CoverageTest` is not a meaningful distinction beyond
naming history — both mean "closes native coverage gaps for `<Class>`".
Prefer `CoverageTest` for new files; `GapCoverageTest` survives on a couple
of older files (`CefBrowserApiGapCoverageTest.java`,
`CefPostDataGapCoverageTest.java`) that predate the settled name.

## `<Class>ApiTest.java` / `<Class>ModernApiTest.java` — API-area split

Reserved for classes whose public API is too large or too varied for one
test file, where the split is by API *area* rather than by scenario:
`CefBrowserApiTest.java`, `CefBrowserApiDebugSafeTest.java` (the
debug-build-safe subset), `CefFrameApiTest.java`,
`CefResourceHandlerModernApiTest.java` (the modern vs. legacy
`CefResourceHandler` API). `CefBrowserKeyEventCoverageTest.java` and
`CefBrowserNavigationHistoryTest.java` are the same idea applied to
`CefBrowser` — named after the API area (key events, navigation history)
rather than suffixed `ApiTest`, since they close coverage gaps in that area
rather than being a general API test.

## `<Class><Scenario>Test.java` — narrow edge-case variant

A single, named edge case that doesn't belong in the main `<Class>Test.java`
(often because it needs different test setup/teardown). Examples:
`CefPostDataElementFirstNativeObjectTest.java`,
`CefDragDataFileContentsTest.java`. Generic (not class-specific) edge-case
sweeps use a feature name instead: `MalformedInputEdgeCaseTest.java`,
`NullParameterEdgeCaseTest.java`, `ModifiedUtf8Test.java`.

## Multi-class integration tests — named by feature, not by class

No single source class maps 1:1, so the file is named after the feature or
behavior under test: `CefContextMenuTest.java`, `CefLifeSpanPopupTest.java`,
`CefDevToolsRegistrationTest.java`, `CefRequestContextPreferencesTest.java`,
`DragTargetTest.java`, `LeakSweepTest.java`, `LoadErrorTest.java`,
`OsrSmokeTest.java`, `RefTypesTest.java` (covers `BoolRef`/`IntRef`/
`LongRef`/`StringRef` together), `ResourceRedirectTest.java`,
`WindowlessFrameRateTest.java`. These are a deliberate, documented exception
to the `<Class>Test.java` pattern, not drift — don't try to force them into
class-name matches.

## `UpstreamIssue<N>Test.java` — regression test for an upstream JCEF issue

Named after the upstream issue number it's a regression test for, not after
any source class: `UpstreamIssue26Test.java`, `UpstreamIssue365Test.java`,
`UpstreamIssue392Test.java`, `UpstreamIssue398Test.java`,
`UpstreamIssue405Test.java`, `UpstreamIssue445Test.java`.

## Test infrastructure — no `Test` suffix, not run standalone

Shared helpers, JUnit extensions, and fixtures live alongside the tests but
aren't tests themselves and don't take a `Test` suffix: `CefTestHelper.java`,
`CoverageTestHelper.java`, `JniNoOpProbe.java`, `LeakChecker.java`,
`LeakTarget.java`, `LeakTargets.java`, `SharedBrowserExtension.java`,
`TestFrame.java`, `TestResourceHandler.java`, `TestSetupContext.java`,
`TestSetupExtension.java`. (`TestFrameTest.java` is the one meta exception:
it's a real JUnit test, of the `TestFrame` helper itself, so it does take the
suffix.)

## History

The 2026-09-04 audit renamed two pre-existing files that had drifted from
this convention (they tested a `Cef`-prefixed class but were missing the
prefix themselves, with no naming rationale for the inconsistency):
`DisplayHandlerTest.java` → `CefDisplayHandlerTest.java`,
`DragDataTest.java` → `CefDragDataTest.java`. `DragDataFileContentsTest.java`
was renamed to `CefDragDataFileContentsTest.java` at the same time for the
same reason (it's a `CefDragData` edge-case variant, see above). No other
files were renamed — everything else already matched one of the patterns
above once the pattern was made explicit.
