// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefContextMenuHandler;
import org.cef.handler.CefDisplayHandler;
import org.cef.handler.CefDownloadHandler;
import org.cef.handler.CefFocusHandler;
import org.cef.handler.CefJSDialogHandler;
import org.cef.handler.CefKeyboardHandler;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefPrintHandler;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JFrame;

// Tier 1 of the two-tier test harness (see plan/roadmap.md's "two-tier test
// harness" entry, 2026-08-31): a single, persistent, shared OSR browser
// reused across every test class that opts in, instead of TestFrame's
// one-browser-per-test pattern. Motivation: issue #4/#23's all_.empty()
// shutdown DCHECK is confirmed to reproduce with as few as 2 browser
// create/dispose cycles in one process -- a batch of N tests sharing ONE
// browser (1 cycle total for the whole batch) instead of each getting its
// own (N cycles) should let that batch run reliably even with #4/#23 still
// open, and reduces overall exposure for combined runs generally.
//
// Only suitable for tests that load a page and assert on a handler callback
// -- NOT for anything that tests creation/close/popup/multi-browser/
// DevTools/windowed semantics themselves (those need TestFrame's real
// per-test lifecycle; see plan/roadmap.md for the full migration criteria).
//
// Usage:
//   @ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
//   class FooTest {
//       @Test
//       void bar() {
//           SharedBrowserExtension.loadPage("<html>...</html>");
//           boolean[] fired = {false};
//           CountDownLatch done = new CountDownLatch(1);
//           SharedBrowserExtension.addFocusHandler(new CefFocusHandlerAdapter() {
//               @Override public void onTakeFocus(CefBrowser b, boolean next) {
//                   fired[0] = true;
//                   done.countDown();
//               }
//           });
//           // ... trigger the interaction here ...
//           SharedBrowserExtension.awaitLatch(done, 10);
//           assertTrue(fired[0]);
//       }
//   }
//
// THREADING (read this before writing interaction code after loadPage()/
// navigateTo()): those methods block the *calling* thread (the JUnit test
// thread) until loading finishes, then return control to that same thread.
// TestFrame's older pattern instead crammed "page is ready, now do X" code
// directly inside onLoadingStateChange() itself, an EDT callback -- which
// made calls like setFocus() EDT-consistent for free, but only as an
// accident of that structure. A real embedding application doesn't get that
// for free either: it loads a page once, then responds to some *later*,
// independent event (a button click, a menu action, a timer) to interact
// with the browser, and has to be deliberate about EDT dispatch at that
// point same as this harness does. So this isn't a workaround to route
// around -- it's the harness matching real usage more closely than
// TestFrame's pattern did. Any call here that needs to be EDT-consistent
// with CEF's own state (setFocus(), dispatching synthetic AWT events, etc.)
// must be wrapped in SwingUtilities.invokeAndWait() explicitly -- omitting
// this reproduced as intermittent hangs/timeouts (caught on
// CefFocusHandlerCoverageTest's setFocus() call during the first migration),
// not a reliable failure, which is what made it easy to miss in a small
// number of validation runs. Handler callbacks themselves (onTakeFocus,
// onTitleChange, etc.) still fire on the EDT as normal; it's only code the
// *test* runs directly after loadPage()/navigateTo() returns that needs
// this.
//
// Order matters in @ExtendWith: TestSetupExtension first, then
// SharedBrowserExtension. Both register themselves as
// ExtensionContext.Store.CloseableResource in the GLOBAL store the first
// time they're used (TestSetupExtension is used by literally every test
// class, so it always registers first); JUnit closes GLOBAL-store resources
// in reverse insertion order, so this harness's close() -- which fully
// disposes the shared browser -- always runs before
// TestSetupExtension.close()'s CefApp.dispose(), keeping the shared
// browser's teardown a normal, isolated create/dispose cycle rather than
// something racing final CefShutdown().
public class SharedBrowserExtension implements BeforeAllCallback, BeforeEachCallback,
                                               AfterEachCallback,
                                               ExtensionContext.Store.CloseableResource {
    private static boolean initialized_ = false;
    private static CefClient client_;
    private static CefBrowser browser_;
    private static final CountDownLatch closedLatch_ = new CountDownLatch(1);
    private static final AtomicInteger urlCounter_ = new AtomicInteger();
    private static final HashMap<String, ResourceContent> resourceMap_ = new HashMap<>();

    // Backs loadPage()'s blocking wait -- set right before navigating,
    // counted down by the permanent internal CefLoadHandler installed in
    // initializeSharedBrowser() below, cleared right after. volatile: read
    // from the CEF UI thread (same thread loadPage() itself runs on in this
    // harness, so this is really just documentation of intent, not a real
    // cross-thread race).
    private static volatile CountDownLatch pendingLoadLatch_;

    // The URL loadPage()/navigateTo() is actually waiting on, set right
    // before calling browser_.loadURL(). Three approaches were tried here,
    // in order, each rejected by a real reproduction before landing on this
    // one -- see issue_4_23_mental_model / shared_browser_test_harness
    // memory for the full investigation (UpstreamIssue398Test and
    // CefBrowserNavigationHistoryTest's flakes):
    //   1. Just count down on the next onLoadingStateChange(false), no
    //      correlation at all -- a belated/stale event from the PREVIOUS
    //      test's page (still settling async work) can satisfy a
    //      freshly-set latch before THIS navigation has even started.
    //   2. Compare browser.getURL() at the moment of the event -- rejected,
    //      not reliably in sync with onLoadingStateChange (delivered via
    //      CefDisplayHandler's onAddressChange, a separate native dispatch
    //      with no ordering guarantee relative to CefLoadHandler's own
    //      callbacks; failed even on the very first-ever navigation).
    //   3. "Arm" on isLoading=true, only honor a subsequent isLoading=false
    //      once armed -- rejected too: two loadPage() calls back-to-back
    //      can have CEF abort trailing resource activity (e.g. a favicon
    //      fetch) for the FIRST page as the SECOND navigation starts,
    //      without the browser's isLoading flag ever dropping back to
    //      false in between -- so the second navigation gets no discrete
    //      true edge to arm against at all.
    // What actually works: onLoadStart's frame.getURL() reliably reflects
    // the TARGET url being navigated to (confirmed empirically), so arm on
    // a main-frame onLoadStart matching this field, and count down on the
    // NEXT main-frame onLoadEnd or onLoadError once armed (either is a
    // valid "this navigation is done" signal -- onLoadError also lets
    // navigateTo() work for LoadErrorTest/UpstreamIssue365Test's
    // deliberately-failing navigations).
    private static volatile String pendingLoadUrl_;
    private static volatile boolean pendingLoadArmed_;

    // Always-on, bounded ring buffer of every event the harness's permanent
    // internal handlers observe (load state/start/end/error, resource
    // lookups) -- cheap enough to leave on unconditionally, and exactly the
    // kind of visibility a flake investigation otherwise has to bolt on by
    // hand each time (see the UpstreamIssue398Test/CefBrowserNavigationHistoryTest
    // flake investigations this was built for). Dumped automatically by
    // awaitLatch()/loadPage()/navigateTo() on timeout via dumpDiagnostics()
    // -- a timeout failure message should never need a follow-up debugging
    // session just to see what else was happening on the shared browser at
    // the time.
    private static final int EVENT_LOG_CAPACITY = 100;
    private static final List<String> eventLog_ = new ArrayList<>();

    private static void logEvent(String fmt, Object... args) {
        String entry = System.currentTimeMillis() + " " + String.format(fmt, args);
        synchronized (eventLog_) {
            eventLog_.add(entry);
            if (eventLog_.size() > EVENT_LOG_CAPACITY) eventLog_.remove(0);
        }
    }

    // Human-readable snapshot of harness state -- appended to timeout
    // failure messages so a flake shows its own recent history (including
    // events that arrived for a DIFFERENT test's page/navigation, e.g. a
    // belated title/load-state change) without needing ad hoc
    // instrumentation added and removed by hand each time.
    private static String dumpDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- SharedBrowserExtension diagnostics ---\n");
        sb.append("pendingLoadUrl_=").append(pendingLoadUrl_)
                .append(" pendingLoadArmed_=").append(pendingLoadArmed_)
                .append(" pendingLoadLatch_=").append(pendingLoadLatch_ != null ? "set" : "null")
                .append(" userLoadHandler_=").append(userLoadHandler_ != null)
                .append(" userRequestHandler_=").append(userRequestHandler_ != null)
                .append(" browser.getURL()=")
                .append(browser_ != null ? browser_.getURL() : "n/a")
                .append(" browser.isLoading()=")
                .append(browser_ != null ? browser_.isLoading() : "n/a")
                .append('\n');
        sb.append("recent events (oldest first):\n");
        synchronized (eventLog_) {
            for (String entry : eventLog_) {
                sb.append("  ").append(entry).append('\n');
            }
        }
        sb.append("--- end diagnostics ---");
        return sb.toString();
    }

    // Optional test-registered CefLoadHandler, forwarded every event by the
    // permanent internal handler below -- lets a test observe onLoadStart/
    // onLoadEnd/onLoadError/etc. (e.g. LoadErrorTest) without taking over
    // the slot loadPage() itself depends on. Set/cleared via
    // addLoadHandler()'s tracked cleanup.
    private static volatile CefLoadHandler userLoadHandler_;

    // Same idea as userLoadHandler_ above, for CefRequestHandler --
    // getResourceRequestHandler() itself stays fixed/harness-owned (see the
    // permanent request handler in initializeSharedBrowser()); every other
    // event (onBeforeBrowse, onOpenURLFromTab, getAuthCredentials,
    // onCertificateError, onRenderProcessTerminated) is forwarded.
    private static volatile org.cef.handler.CefRequestHandler userRequestHandler_;

    // Cleanup actions (matching remove*Handler() calls) queued by this
    // test's add*Handler() calls, run automatically in afterEach() -- the
    // "share common cleanup" half of the two-tier design, so migrated tests
    // don't need their own try/finally teardown boilerplate.
    private static final List<Runnable> cleanupActions_ = new ArrayList<>();

    private static class ResourceContent {
        final String content;
        final String mimeType;
        final HashMap<String, String> headers;
        ResourceContent(String content, String mimeType, HashMap<String, String> headers) {
            this.content = content;
            this.mimeType = mimeType;
            this.headers = headers;
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        if (initialized_) return;
        initialized_ = true;
        context.getRoot().getStore(GLOBAL).put("jcef_shared_browser", this);
        initializeSharedBrowser();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        synchronized (cleanupActions_) {
            cleanupActions_.clear();
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        synchronized (cleanupActions_) {
            for (Runnable cleanup : cleanupActions_) {
                try {
                    cleanup.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            cleanupActions_.clear();
        }
    }

    // Executed once, after every test in the whole suite has completed --
    // see the class comment for why this runs before CefApp.dispose().
    @Override
    public void close() {
        if (client_ == null) return;
        browser_.close(true);
        try {
            if (!closedLatch_.await(10, TimeUnit.SECONDS)) {
                System.out.println(
                        "SharedBrowserExtension.close(): the shared browser never finished "
                        + "closing -- proceeding anyway.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        client_.dispose();
    }

    private static void initializeSharedBrowser() {
        client_ = CefApp.getInstance().createClient();
        assertNotNull(client_);

        client_.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(
                    CefBrowser browser, CefFrame frame, String targetUrl, String targetFrameName) {
                // Not supported by this harness -- popups create a new
                // browser, which is exactly what Tier 1 exists to avoid. A
                // test that needs to exercise popups belongs on TestFrame.
                return true; // Cancel.
            }

            @Override
            public void onBeforeClose(CefBrowser browser) {
                closedLatch_.countDown();
            }

            // doClose() is left at the adapter's default (always allow, see
            // CefLifeSpanHandlerAdapter): this harness only ever force-closes
            // once, from close() above, in a fully controlled way -- no
            // negotiation dance is needed here the way TestFrame needs one
            // to simulate a real user closing a real window.
        });

        // Permanent (never removed) load handler: drives loadPage()'s
        // blocking wait via pendingLoadLatch_, and forwards every event to
        // an optional test-registered delegate (see addLoadHandler()) --
        // this is what lets a test observe onLoadStart/onLoadEnd/
        // onLoadError itself while loadPage() still works normally.
        client_.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                logEvent("onLoadingStateChange(isLoading=%s) url=%s", isLoading, browser.getURL());
                CefLoadHandler delegate = userLoadHandler_;
                if (delegate != null) {
                    delegate.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward);
                }
            }

            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame,
                    org.cef.network.CefRequest.TransitionType type) {
                logEvent("onLoadStart frame.isMain=%s frame.getURL()=%s armed=%s latchPending=%s",
                        frame.isMain(), frame.getURL(), pendingLoadArmed_,
                        pendingLoadLatch_ != null);
                if (frame.isMain() && pendingLoadLatch_ != null
                        && frame.getURL().equals(pendingLoadUrl_)) {
                    pendingLoadArmed_ = true;
                }
                CefLoadHandler delegate = userLoadHandler_;
                if (delegate != null) delegate.onLoadStart(browser, frame, type);
            }

            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                logEvent("onLoadEnd frame.isMain=%s frame.getURL()=%s status=%d", frame.isMain(),
                        frame.getURL(), httpStatusCode);
                if (frame.isMain()) {
                    CountDownLatch latch = pendingLoadLatch_;
                    if (latch != null && pendingLoadArmed_) latch.countDown();
                }
                CefLoadHandler delegate = userLoadHandler_;
                if (delegate != null) delegate.onLoadEnd(browser, frame, httpStatusCode);
            }

            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame,
                    CefLoadHandler.ErrorCode errorCode, String errorText, String failedUrl) {
                logEvent("onLoadError frame.isMain=%s failedUrl=%s errorCode=%s", frame.isMain(),
                        failedUrl, errorCode);
                // Unlike onLoadEnd, onLoadError carries the failed URL
                // directly -- some failures (e.g. an early DNS-style
                // resolution failure for a registered-but-unhandled
                // resource, see LoadErrorTest/UpstreamIssue365Test) never
                // reach onLoadStart at all, so this can't rely on
                // pendingLoadArmed_ alone the way onLoadEnd does.
                if (frame.isMain()) {
                    CountDownLatch latch = pendingLoadLatch_;
                    if (latch != null && (pendingLoadArmed_ || failedUrl.equals(pendingLoadUrl_))) {
                        latch.countDown();
                    }
                }
                CefLoadHandler delegate = userLoadHandler_;
                if (delegate != null) {
                    delegate.onLoadError(browser, frame, errorCode, errorText, failedUrl);
                }
            }
        });

        // Permanent (never removed) request handler: getResourceRequestHandler()
        // stays fixed (routing decisions are harness-owned, see the
        // resource-map lookup below) -- but every other CefRequestHandler
        // event is forwarded to an optional test-registered delegate, same
        // pattern as the load handler above.
        client_.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame,
                    org.cef.network.CefRequest request, boolean userGesture, boolean isRedirect) {
                org.cef.handler.CefRequestHandler delegate = userRequestHandler_;
                return delegate != null
                        && delegate.onBeforeBrowse(browser, frame, request, userGesture, isRedirect);
            }

            @Override
            public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String targetUrl,
                    boolean userGesture) {
                org.cef.handler.CefRequestHandler delegate = userRequestHandler_;
                return delegate != null
                        && delegate.onOpenURLFromTab(browser, frame, targetUrl, userGesture);
            }

            @Override
            public org.cef.handler.CefResourceRequestHandler getResourceRequestHandler(
                    CefBrowser browser, CefFrame frame, org.cef.network.CefRequest request,
                    boolean isNavigation, boolean isDownload, String requestInitiator,
                    org.cef.misc.BoolRef disableDefaultHandling) {
                return SHARED_RESOURCE_REQUEST_HANDLER;
            }

            @Override
            public boolean getAuthCredentials(CefBrowser browser, String originUrl,
                    boolean isProxy, String host, int port, String realm, String scheme,
                    org.cef.callback.CefAuthCallback callback) {
                org.cef.handler.CefRequestHandler delegate = userRequestHandler_;
                return delegate != null
                        && delegate.getAuthCredentials(
                                browser, originUrl, isProxy, host, port, realm, scheme, callback);
            }

            @Override
            public boolean onCertificateError(CefBrowser browser,
                    CefLoadHandler.ErrorCode certError, String requestUrl,
                    org.cef.callback.CefCallback callback) {
                org.cef.handler.CefRequestHandler delegate = userRequestHandler_;
                return delegate != null
                        && delegate.onCertificateError(browser, certError, requestUrl, callback);
            }

            @Override
            public void onRenderProcessTerminated(CefBrowser browser,
                    org.cef.handler.CefRequestHandler.TerminationStatus status, int errorCode,
                    String errorString) {
                org.cef.handler.CefRequestHandler delegate = userRequestHandler_;
                if (delegate != null) {
                    delegate.onRenderProcessTerminated(browser, status, errorCode, errorString);
                }
            }
        });

        // createBrowser() triggers an implicit, asynchronous initial
        // navigation to "about:blank" -- arm for it explicitly BEFORE
        // calling createBrowser(), the same way loadPage()/navigateTo() arm
        // for their own navigations, so the warmup load below only fires
        // once that initial navigation has genuinely completed. Skipping
        // this wait (calling loadPage() for the warmup immediately after
        // createBrowser(), as an earlier version of this method did) races
        // the browser's own native peer readiness: the warmup's loadURL()
        // call can be issued before the browser is actually ready to accept
        // a new navigation and gets silently lost, which the OLD, looser
        // onLoadingStateChange-based completion check masked (it counted
        // down on ANY isLoading edge, including about:blank's own,
        // regardless of whether it was really the warmup navigation) --
        // this explicit wait plus the onLoadStart-correlated completion
        // check (see pendingLoadUrl_'s comment) surfaced the race for real
        // instead of accidentally tolerating it.
        CountDownLatch initialLoad = new CountDownLatch(1);
        pendingLoadArmed_ = false;
        pendingLoadUrl_ = "about:blank";
        pendingLoadLatch_ = initialLoad;

        browser_ =
                client_.createBrowser("about:blank", true /* useOSR */, false /* isTransparent */);
        assertNotNull(browser_);

        // Realize the browser's UI component so OSR painting and real input
        // delivery (focus/key/mouse events) behave the same as TestFrame's
        // tests, which rely on being added to a real, shown JFrame.
        JFrame frame = new JFrame();
        frame.getContentPane().add(browser_.getUIComponent(), BorderLayout.CENTER);
        frame.pack();
        frame.setSize(800, 600);
        frame.setVisible(true);

        try {
            if (!initialLoad.await(30, TimeUnit.SECONDS)) {
                fail("SharedBrowserExtension: initial about:blank navigation never finished"
                        + dumpDiagnostics());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while awaiting the initial browser navigation");
        } finally {
            pendingLoadLatch_ = null;
            pendingLoadUrl_ = null;
        }

        // Warm up the shared browser with one real page load before any
        // test runs -- mirrors TestSetupExtension.warmUpBrowserProcess()'s
        // own precedent for the same class of problem. Found empirically:
        // a test whose *first* navigation on a freshly-created browser
        // deliberately fails (e.g. LoadErrorTest, testing onLoadError) did
        // not reliably fire that callback when it was the very first
        // navigation ever attempted (starting cold from "about:blank"); the
        // identical navigation reliably worked once any other test had
        // already loaded a real page first. A cheap, always-run warmup load
        // here removes the ordering dependency entirely.
        loadPage("<html><body>warmup</body></html>");
    }

    private static final org.cef.handler.CefResourceRequestHandler SHARED_RESOURCE_REQUEST_HANDLER =
            new CefResourceRequestHandlerAdapter() {
                @Override
                public org.cef.handler.CefResourceHandler getResourceHandler(
                        CefBrowser browser, CefFrame frame, org.cef.network.CefRequest request) {
                    String url = request.getURL();
                    int idx = url.indexOf('?');
                    if (idx > 0) url = url.substring(0, idx);
                    ResourceContent rc;
                    synchronized (resourceMap_) {
                        rc = resourceMap_.get(url);
                    }
                    if (rc == null) return null;
                    return new TestResourceHandler(rc.content, rc.mimeType, rc.headers);
                }
            };

    // Loads fresh HTML content (a never-reused URL, so no state can leak
    // between tests) into the shared browser and blocks the calling test
    // thread until that navigation finishes, returning the URL it was
    // loaded under (register any handlers that need to observe the
    // navigation itself, e.g. onAddressChange, *before* calling this --
    // the returned URL is for comparing against what such a handler
    // captured, since it isn't known until this call assigns it). Most
    // migrated tests won't need their own load-handling boilerplate at all
    // as a result.
    public static String loadPage(String html) {
        return loadPage(html, "text/html", null);
    }

    public static String loadPage(String html, String mimeType) {
        return loadPage(html, mimeType, null);
    }

    // Like loadPage(html, mimeType), but also serves the given response
    // headers (e.g. a real Set-Cookie header -- see UpstreamIssue405Test)
    // for the same navigation.
    public static synchronized String loadPage(
            String html, String mimeType, HashMap<String, String> headers) {
        String url = "http://test.com/shared/" + urlCounter_.incrementAndGet() + ".html";
        synchronized (resourceMap_) {
            resourceMap_.put(url, new ResourceContent(html, mimeType, headers));
        }

        CountDownLatch loaded = new CountDownLatch(1);
        pendingLoadArmed_ = false;
        pendingLoadUrl_ = url;
        pendingLoadLatch_ = loaded;
        try {
            browser_.loadURL(url);
            if (!loaded.await(30, TimeUnit.SECONDS)) {
                fail("SharedBrowserExtension.loadPage: page never finished loading: " + url
                        + dumpDiagnostics());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while awaiting page load");
        } finally {
            pendingLoadLatch_ = null;
            pendingLoadUrl_ = null;
        }
        return url;
    }

    // Loads a URL directly (no addResource() entry, e.g. to deliberately
    // trigger onLoadError -- see LoadErrorTest) and blocks until that
    // navigation's loading-state transition completes, exactly like
    // loadPage(), without registering any content for it.
    public static synchronized void navigateTo(String url) {
        CountDownLatch loaded = new CountDownLatch(1);
        pendingLoadArmed_ = false;
        pendingLoadUrl_ = url;
        pendingLoadLatch_ = loaded;
        try {
            browser_.loadURL(url);
            if (!loaded.await(30, TimeUnit.SECONDS)) {
                fail("SharedBrowserExtension.navigateTo: navigation never finished: " + url
                        + dumpDiagnostics());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while awaiting navigation");
        } finally {
            pendingLoadLatch_ = null;
            pendingLoadUrl_ = null;
        }
    }

    // Registers a delegate to observe onLoadStart/onLoadEnd/onLoadError/
    // onLoadingStateChange for the duration of the current test, forwarded
    // by the permanent internal load handler installed in
    // initializeSharedBrowser() -- loadPage()/navigateTo() keep working
    // normally alongside it. Auto-removed by afterEach(), like the other
    // add*Handler() wrappers.
    public static void addLoadHandler(CefLoadHandler handler) {
        userLoadHandler_ = handler;
        trackCleanup(() -> userLoadHandler_ = null);
    }

    public static CefBrowser browser() {
        return browser_;
    }

    public static CefClient client() {
        return client_;
    }

    // Blocks until `latch` counts down or `timeoutSeconds` elapses, failing
    // the test on timeout -- a thin wrapper so migrated tests keep the same
    // CountDownLatch + captured-boolean-array idiom already used throughout
    // the suite, rather than learning a new one.
    public static void awaitLatch(CountDownLatch latch, long timeoutSeconds) {
        try {
            if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                fail("SharedBrowserExtension.awaitLatch: timed out after " + timeoutSeconds + "s"
                        + dumpDiagnostics());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while awaiting test condition");
        }
    }

    // add*Handler wrappers: each records the matching remove*Handler() call
    // to run automatically in afterEach(). Deliberately NOT provided for
    // CefLifeSpanHandler/CefLoadHandler/CefRequestHandler/
    // CefResourceRequestHandler -- this harness owns those permanently for
    // its own bookkeeping (resource map, loadPage(), close()); a test that
    // needs one of those belongs on TestFrame instead.

    public static void addContextMenuHandler(CefContextMenuHandler handler) {
        client_.addContextMenuHandler(handler);
        trackCleanup(client_::removeContextMenuHandler);
    }

    public static void addDialogHandler(org.cef.handler.CefDialogHandler handler) {
        client_.addDialogHandler(handler);
        trackCleanup(client_::removeDialogHandler);
    }

    public static void addDisplayHandler(CefDisplayHandler handler) {
        client_.addDisplayHandler(handler);
        trackCleanup(client_::removeDisplayHandler);
    }

    public static void addDownloadHandler(CefDownloadHandler handler) {
        client_.addDownloadHandler(handler);
        trackCleanup(client_::removeDownloadHandler);
    }

    public static void addFocusHandler(CefFocusHandler handler) {
        client_.addFocusHandler(handler);
        trackCleanup(client_::removeFocusHandler);
    }

    public static void addJSDialogHandler(CefJSDialogHandler handler) {
        client_.addJSDialogHandler(handler);
        trackCleanup(client_::removeJSDialogHandler);
    }

    public static void addKeyboardHandler(CefKeyboardHandler handler) {
        client_.addKeyboardHandler(handler);
        trackCleanup(client_::removeKeyboardHandler);
    }

    public static void addPrintHandler(CefPrintHandler handler) {
        client_.addPrintHandler(handler);
        trackCleanup(client_::removePrintHandler);
    }

    // Unlike the other add*Handler() wrappers, a client can have multiple
    // message routers at once, so removal needs the same instance back
    // (CefClient.removeMessageRouter() takes it as an argument, unlike the
    // other no-arg remove*Handler() methods) -- and, since this client
    // persists across tests (unlike TestFrame's one-shot client, which
    // never bothered removing its router before disposing the whole
    // client), also needs an explicit dispose() so the router's own native
    // resources don't outlive the test that created it.
    public static void addMessageRouter(org.cef.browser.CefMessageRouter router) {
        client_.addMessageRouter(router);
        trackCleanup(() -> {
            client_.removeMessageRouter(router);
            router.dispose();
        });
    }

    // Unlike the other add*Handler() wrappers, CefClient only allows one
    // CefRequestHandler total, and it's already permanently installed above
    // (forwarding to userRequestHandler_) so getResourceRequestHandler()
    // stays fixed to SHARED_RESOURCE_REQUEST_HANDLER regardless of what a
    // test installs here. This just sets/clears the delegate.
    public static void addRequestHandler(org.cef.handler.CefRequestHandler handler) {
        userRequestHandler_ = handler;
        trackCleanup(() -> userRequestHandler_ = null);
    }

    private static void trackCleanup(Runnable remover) {
        synchronized (cleanupActions_) {
            cleanupActions_.add(remover);
        }
    }
}
