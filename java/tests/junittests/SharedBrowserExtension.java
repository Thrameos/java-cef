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

    // Cleanup actions (matching remove*Handler() calls) queued by this
    // test's add*Handler() calls, run automatically in afterEach() -- the
    // "share common cleanup" half of the two-tier design, so migrated tests
    // don't need their own try/finally teardown boilerplate.
    private static final List<Runnable> cleanupActions_ = new ArrayList<>();

    private static class ResourceContent {
        final String content;
        final String mimeType;
        ResourceContent(String content, String mimeType) {
            this.content = content;
            this.mimeType = mimeType;
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

        client_.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public org.cef.handler.CefResourceRequestHandler getResourceRequestHandler(
                    CefBrowser browser, CefFrame frame, org.cef.network.CefRequest request,
                    boolean isNavigation, boolean isDownload, String requestInitiator,
                    org.cef.misc.BoolRef disableDefaultHandling) {
                return SHARED_RESOURCE_REQUEST_HANDLER;
            }
        });

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
                    return new TestResourceHandler(rc.content, rc.mimeType, null);
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
        return loadPage(html, "text/html");
    }

    public static synchronized String loadPage(String html, String mimeType) {
        String url = "http://test.com/shared/" + urlCounter_.incrementAndGet() + ".html";
        synchronized (resourceMap_) {
            resourceMap_.put(url, new ResourceContent(html, mimeType));
        }

        CountDownLatch loaded = new CountDownLatch(1);
        client_.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (!isLoading) loaded.countDown();
            }
        });
        try {
            browser_.loadURL(url);
            if (!loaded.await(30, TimeUnit.SECONDS)) {
                fail("SharedBrowserExtension.loadPage: page never finished loading: " + url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while awaiting page load");
        } finally {
            client_.removeLoadHandler();
        }
        return url;
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
                fail("SharedBrowserExtension.awaitLatch: timed out after " + timeoutSeconds + "s");
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

    private static void trackCleanup(Runnable remover) {
        synchronized (cleanupActions_) {
            cleanupActions_.add(remover);
        }
    }
}
