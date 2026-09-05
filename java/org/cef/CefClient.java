// Copyright (c) 2013 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef;

import org.cef.browser.*;
import org.cef.callback.CefAuthCallback;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefCallback;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefDownloadItemCallback;
import org.cef.callback.CefDragData;
import org.cef.callback.Disposable;
import org.cef.callback.CefFileDialogCallback;
import org.cef.callback.CefJSDialogCallback;
import org.cef.callback.CefMediaAccessCallback;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefPermissionPromptCallback;
import org.cef.callback.CefPrintDialogCallback;
import org.cef.callback.CefPrintJobCallback;
import org.cef.handler.CefClientHandler;
import org.cef.handler.CefContextMenuHandler;
import org.cef.handler.CefDialogHandler;
import org.cef.handler.CefDisplayHandler;
import org.cef.handler.CefDownloadHandler;
import org.cef.handler.CefDragHandler;
import org.cef.handler.CefFindHandler;
import org.cef.handler.CefFocusHandler;
import org.cef.handler.CefFrameHandler;
import org.cef.handler.CefJSDialogHandler;
import org.cef.handler.CefKeyboardHandler;
import org.cef.handler.CefLifeSpanHandler;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefPermissionHandler;
import org.cef.handler.CefPrintHandler;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefRequestHandler;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefScreenInfo;
import org.cef.handler.CefWindowHandler;
import org.cef.misc.BoolRef;
import org.cef.misc.CefPrintSettings;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefRequest.TransitionType;
import org.cef.network.CefResponse;
import org.cef.network.CefURLRequest;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

/**
 * Client that owns a browser and renderer.
 */
public class CefClient extends CefClientHandler
        implements CefContextMenuHandler, CefDialogHandler, CefDisplayHandler, CefDownloadHandler,
                   CefDragHandler, CefFindHandler, CefFocusHandler, CefFrameHandler,
                   CefJSDialogHandler, CefKeyboardHandler, CefLifeSpanHandler, CefLoadHandler,
                   CefPermissionHandler, CefPrintHandler, CefRenderHandler, CefRequestHandler,
                   CefWindowHandler, Disposable {
    private static final Logger LOGGER = Logger.getLogger(CefClient.class.getName());
    private HashMap<Integer, CefBrowser> browser_ = new HashMap<Integer, CefBrowser>();
    private CefContextMenuHandler contextMenuHandler_ = null;
    private CefDialogHandler dialogHandler_ = null;
    private CefDisplayHandler displayHandler_ = null;
    private CefDownloadHandler downloadHandler_ = null;
    private CefDragHandler dragHandler_ = null;
    private CefFindHandler findHandler_ = null;
    private CefFocusHandler focusHandler_ = null;
    private CefFrameHandler frameHandler_ = null;
    private CefJSDialogHandler jsDialogHandler_ = null;
    private CefKeyboardHandler keyboardHandler_ = null;
    private CefLifeSpanHandler lifeSpanHandler_ = null;
    private CefLoadHandler loadHandler_ = null;
    private CefPermissionHandler permissionHandler_ = null;
    private CefPrintHandler printHandler_ = null;
    private CefRequestHandler requestHandler_ = null;
    private boolean isDisposed_ = false;
    // See cleanupBrowser()'s own comment: guards its handler-removal +
    // super.dispose() block against running more than once. isDisposed_
    // alone doesn't work as that guard -- it's set true and never reset, so
    // cleanupBrowser() being invoked again afterward (e.g. a second/late
    // onBeforeClose() callback while browser_ is already empty) would
    // re-enter and re-run the whole block, double-removing every handler --
    // see Thrameos/java-cef#22 (SetCefForJNIObjectHelper::Release SIGSEGV,
    // confirmed live this session via native/jcef_trace.h's varName-tagged
    // ref trace: the exact same CefFocusHandler native pointer released
    // twice, ~83ms apart, with no intervening AddRef).
    private boolean handlersRemoved_ = false;
    private volatile CefBrowser focusedBrowser_ = null;
    private final PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (focusedBrowser_ != null) {
                Component browserUI = focusedBrowser_.getUIComponent();
                Object oldUI = evt.getOldValue();
                if (isPartOf(oldUI, browserUI)) {
                    focusedBrowser_.setFocus(false);
                    focusedBrowser_ = null;
                }
            }
        }
    };

    /**
     * The CTOR is only accessible within this package.
     * Use CefApp.createClient() to create an instance of
     * this class.
     * @see org.cef.CefApp.createClient()
     */
    CefClient() throws UnsatisfiedLinkError {
        super();

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(
                propertyChangeListener);
    }

    private boolean isPartOf(Object obj, Component browserUI) {
        if (obj == browserUI) return true;
        if (obj instanceof Container) {
            Component childs[] = ((Container) obj).getComponents();
            for (Component child : childs) {
                return isPartOf(child, browserUI);
            }
        }
        return false;
    }

    @Override
    public void dispose() {
        isDisposed_ = true;
        cleanupBrowser(-1);
    }

    // CefClientHandler

    public CefBrowser createBrowser(
            String url, boolean isOffscreenRendered, boolean isTransparent) {
        return createBrowser(url, isOffscreenRendered, isTransparent, null);
    }

    public CefBrowser createBrowser(String url, boolean isOffscreenRendered, boolean isTransparent,
            CefRequestContext context) {
        if (isDisposed_)
            throw new IllegalStateException("Can't create browser. CefClient is disposed");
        return CefBrowserFactory.create(
                this, url, isOffscreenRendered, isTransparent, context, null);
    }

    public CefBrowser createBrowser(String url, boolean isOffscreenRendered, boolean isTransparent,
            CefRequestContext context, CefBrowserSettings settings) {
        if (isDisposed_)
            throw new IllegalStateException("Can't create browser. CefClient is disposed");
        return CefBrowserFactory.create(
                this, url, isOffscreenRendered, isTransparent, context, settings);
    }

    @Override
    protected CefBrowser getBrowser(int identifier) {
        synchronized (browser_) {
            return browser_.get(new Integer(identifier));
        }
    }

    @Override
    protected Object[] getAllBrowser() {
        synchronized (browser_) {
            return browser_.values().toArray();
        }
    }

    @Override
    protected CefContextMenuHandler getContextMenuHandler() {
        return this;
    }

    @Override
    protected CefDialogHandler getDialogHandler() {
        return this;
    };

    @Override
    protected CefDisplayHandler getDisplayHandler() {
        return this;
    }

    @Override
    protected CefDownloadHandler getDownloadHandler() {
        return this;
    }

    @Override
    protected CefDragHandler getDragHandler() {
        return this;
    }

    @Override
    protected CefFindHandler getFindHandler() {
        return this;
    }

    @Override
    protected CefFocusHandler getFocusHandler() {
        return this;
    }

    @Override
    protected CefFrameHandler getFrameHandler() {
        return this;
    }

    @Override
    protected CefJSDialogHandler getJSDialogHandler() {
        return this;
    }

    @Override
    protected CefKeyboardHandler getKeyboardHandler() {
        return this;
    }

    @Override
    protected CefLifeSpanHandler getLifeSpanHandler() {
        return this;
    }

    @Override
    protected CefLoadHandler getLoadHandler() {
        return this;
    }

    @Override
    protected CefPermissionHandler getPermissionHandler() {
        return this;
    }

    @Override
    protected CefPrintHandler getPrintHandler() {
        return this;
    }

    @Override
    protected CefRenderHandler getRenderHandler() {
        return this;
    }

    @Override
    protected CefRequestHandler getRequestHandler() {
        return this;
    }

    @Override
    protected CefWindowHandler getWindowHandler() {
        return this;
    }

    // CefContextMenuHandler

    public CefClient addContextMenuHandler(CefContextMenuHandler handler) {
        if (contextMenuHandler_ == null) contextMenuHandler_ = handler;
        return this;
    }

    public void removeContextMenuHandler() {
        contextMenuHandler_ = null;
    }

    @Override
    public void onBeforeContextMenu(
            CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
        if (contextMenuHandler_ != null && browser != null)
            contextMenuHandler_.onBeforeContextMenu(browser, frame, params, model);
    }

    @Override
    public boolean onContextMenuCommand(CefBrowser browser, CefFrame frame,
            CefContextMenuParams params, int commandId, int eventFlags) {
        if (contextMenuHandler_ != null && browser != null)
            return contextMenuHandler_.onContextMenuCommand(
                    browser, frame, params, commandId, eventFlags);
        return false;
    }

    @Override
    public void onContextMenuDismissed(CefBrowser browser, CefFrame frame) {
        if (contextMenuHandler_ != null && browser != null)
            contextMenuHandler_.onContextMenuDismissed(browser, frame);
    }

    // CefDialogHandler

    public CefClient addDialogHandler(CefDialogHandler handler) {
        if (dialogHandler_ == null) dialogHandler_ = handler;
        return this;
    }

    public void removeDialogHandler() {
        dialogHandler_ = null;
    }

    @Override
    public boolean onFileDialog(CefBrowser browser, FileDialogMode mode, String title,
            String defaultFilePath, Vector<String> acceptFilters, Vector<String> acceptExtensions,
            Vector<String> acceptDescriptions, CefFileDialogCallback callback) {
        if (dialogHandler_ != null && browser != null) {
            return dialogHandler_.onFileDialog(browser, mode, title, defaultFilePath, acceptFilters,
                    acceptExtensions, acceptDescriptions, callback);
        }
        return false;
    }

    // CefDisplayHandler

    public CefClient addDisplayHandler(CefDisplayHandler handler) {
        if (displayHandler_ == null) displayHandler_ = handler;
        return this;
    }

    public void removeDisplayHandler() {
        displayHandler_ = null;
    }

    @Override
    public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
        if (displayHandler_ != null && browser != null)
            displayHandler_.onAddressChange(browser, frame, url);
    }

    @Override
    public void onTitleChange(CefBrowser browser, String title) {
        if (displayHandler_ != null && browser != null)
            displayHandler_.onTitleChange(browser, title);
    }

    @Override
    public void onFullscreenModeChange(CefBrowser browser, boolean fullscreen) {
        if (displayHandler_ != null && browser != null)
            displayHandler_.onFullscreenModeChange(browser, fullscreen);
    }

    @Override
    public boolean onTooltip(CefBrowser browser, String text) {
        if (displayHandler_ != null && browser != null) {
            return displayHandler_.onTooltip(browser, text);
        }
        return false;
    }

    @Override
    public void onStatusMessage(CefBrowser browser, String value) {
        if (displayHandler_ != null && browser != null) {
            displayHandler_.onStatusMessage(browser, value);
        }
    }

    @Override
    public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level,
            String message, String source, int line) {
        if (displayHandler_ != null && browser != null) {
            return displayHandler_.onConsoleMessage(browser, level, message, source, line);
        }
        return false;
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        if (browser == null) {
            return false;
        }

        if (displayHandler_ != null && displayHandler_.onCursorChange(browser, cursorType)) {
            return true;
        }

        CefRenderHandler realHandler = browser.getRenderHandler();
        if (realHandler != null) {
            return realHandler.onCursorChange(browser, cursorType);
        }

        return false;
    }

    // CefDownloadHandler

    public CefClient addDownloadHandler(CefDownloadHandler handler) {
        if (downloadHandler_ == null) downloadHandler_ = handler;
        return this;
    }

    public void removeDownloadHandler() {
        downloadHandler_ = null;
    }

    @Override
    public boolean onBeforeDownload(CefBrowser browser, CefDownloadItem downloadItem,
            String suggestedName, CefBeforeDownloadCallback callback) {
        if (downloadHandler_ != null && browser != null)
            return downloadHandler_.onBeforeDownload(
                    browser, downloadItem, suggestedName, callback);
        return false;
    }

    @Override
    public void onDownloadUpdated(
            CefBrowser browser, CefDownloadItem downloadItem, CefDownloadItemCallback callback) {
        if (downloadHandler_ != null && browser != null)
            downloadHandler_.onDownloadUpdated(browser, downloadItem, callback);
    }

    // CefDragHandler

    public CefClient addDragHandler(CefDragHandler handler) {
        if (dragHandler_ == null) dragHandler_ = handler;
        return this;
    }

    public void removeDragHandler() {
        dragHandler_ = null;
    }

    @Override
    public boolean onDragEnter(CefBrowser browser, CefDragData dragData, int mask) {
        if (dragHandler_ != null && browser != null)
            return dragHandler_.onDragEnter(browser, dragData, mask);
        return false;
    }

    // CefFindHandler

    public CefClient addFindHandler(CefFindHandler handler) {
        if (findHandler_ == null) findHandler_ = handler;
        return this;
    }

    public void removeFindHandler() {
        findHandler_ = null;
    }

    @Override
    public void onFindResult(CefBrowser browser, int identifier, int count,
            Rectangle selectionRect, int activeMatchOrdinal, boolean finalUpdate) {
        if (findHandler_ != null && browser != null)
            findHandler_.onFindResult(
                    browser, identifier, count, selectionRect, activeMatchOrdinal, finalUpdate);
    }

    // CefFocusHandler

    public CefClient addFocusHandler(CefFocusHandler handler) {
        if (focusHandler_ == null) focusHandler_ = handler;
        return this;
    }

    public void removeFocusHandler() {
        focusHandler_ = null;
    }

    @Override
    public void onTakeFocus(CefBrowser browser, boolean next) {
        if (browser == null) return;

        browser.setFocus(false);
        Container parent = browser.getUIComponent().getParent();
        if (parent != null) {
            FocusTraversalPolicy policy = null;
            while (parent != null) {
                policy = parent.getFocusTraversalPolicy();
                if (policy != null) break;
                parent = parent.getParent();
            }
            if (policy != null) {
                Component nextComp = next
                        ? policy.getComponentAfter(parent, browser.getUIComponent())
                        : policy.getComponentBefore(parent, browser.getUIComponent());
                if (nextComp == null) {
                    policy.getDefaultComponent(parent).requestFocus();
                } else {
                    nextComp.requestFocus();
                }
            }
        }
        focusedBrowser_ = null;
        if (focusHandler_ != null) focusHandler_.onTakeFocus(browser, next);
    }

    @Override
    public boolean onSetFocus(final CefBrowser browser, FocusSource source) {
        if (browser == null) return false;

        boolean alreadyHandled = false;
        if (focusHandler_ != null) alreadyHandled = focusHandler_.onSetFocus(browser, source);
        return alreadyHandled;
    }

    @Override
    public void onGotFocus(CefBrowser browser) {
        if (browser == null) return;

        // browser.setFocus(true) below is CEF's OnSetFocus() path, which
        // synchronously re-notifies WebContents focus (OnWebContentsFocused)
        // and so re-enters this same onGotFocus() callback for the same
        // browser -- unlike CEF's own OnSetFocus() callback, which guards
        // itself against reentrant SetFocus() calls (is_in_onsetfocus_ in
        // CefBrowserContentsDelegate), OnGotFocus/OnWebContentsFocused has
        // no such guard. Without this check, an already-focused browser
        // recurses onGotFocus<->setFocus(true) synchronously until the
        // thread's stack overflows. Only call setFocus() on an actual
        // focus transition (guarded by focusedBrowser_, cleared in
        // onTakeFocus() below).
        if (focusedBrowser_ != browser) {
            focusedBrowser_ = browser;
            browser.setFocus(true);
        }
        if (focusHandler_ != null) focusHandler_.onGotFocus(browser);
    }

    // CefFrameHandler

    public CefClient addFrameHandler(CefFrameHandler handler) {
        if (frameHandler_ == null) frameHandler_ = handler;
        return this;
    }

    public void removeFrameHandler() {
        frameHandler_ = null;
    }

    @Override
    public void onFrameCreated(CefBrowser browser, CefFrame frame) {
        if (frameHandler_ != null && browser != null) frameHandler_.onFrameCreated(browser, frame);
    }

    @Override
    public void onFrameDestroyed(CefBrowser browser, CefFrame frame) {
        if (frameHandler_ != null && browser != null)
            frameHandler_.onFrameDestroyed(browser, frame);
    }

    @Override
    public void onFrameAttached(CefBrowser browser, CefFrame frame, boolean reattached) {
        if (frameHandler_ != null && browser != null)
            frameHandler_.onFrameAttached(browser, frame, reattached);
    }

    @Override
    public void onFrameDetached(CefBrowser browser, CefFrame frame) {
        if (frameHandler_ != null && browser != null)
            frameHandler_.onFrameDetached(browser, frame);
    }

    @Override
    public void onMainFrameChanged(CefBrowser browser, CefFrame oldFrame, CefFrame newFrame) {
        if (frameHandler_ != null && browser != null)
            frameHandler_.onMainFrameChanged(browser, oldFrame, newFrame);
    }

    // CefJSDialogHandler

    public CefClient addJSDialogHandler(CefJSDialogHandler handler) {
        if (jsDialogHandler_ == null) jsDialogHandler_ = handler;
        return this;
    }

    public void removeJSDialogHandler() {
        jsDialogHandler_ = null;
    }

    @Override
    public boolean onJSDialog(CefBrowser browser, String origin_url, JSDialogType dialog_type,
            String message_text, String default_prompt_text, CefJSDialogCallback callback,
            BoolRef suppress_message) {
        if (jsDialogHandler_ != null && browser != null)
            return jsDialogHandler_.onJSDialog(browser, origin_url, dialog_type, message_text,
                    default_prompt_text, callback, suppress_message);
        return false;
    }

    @Override
    public boolean onBeforeUnloadDialog(CefBrowser browser, String message_text, boolean is_reload,
            CefJSDialogCallback callback) {
        if (jsDialogHandler_ != null && browser != null)
            return jsDialogHandler_.onBeforeUnloadDialog(
                    browser, message_text, is_reload, callback);
        return false;
    }

    @Override
    public void onResetDialogState(CefBrowser browser) {
        if (jsDialogHandler_ != null && browser != null)
            jsDialogHandler_.onResetDialogState(browser);
    }

    @Override
    public void onDialogClosed(CefBrowser browser) {
        if (jsDialogHandler_ != null && browser != null) jsDialogHandler_.onDialogClosed(browser);
    }

    // CefKeyboardHandler

    public CefClient addKeyboardHandler(CefKeyboardHandler handler) {
        if (keyboardHandler_ == null) keyboardHandler_ = handler;
        return this;
    }

    public void removeKeyboardHandler() {
        keyboardHandler_ = null;
    }

    @Override
    public boolean onPreKeyEvent(
            CefBrowser browser, CefKeyEvent event, BoolRef is_keyboard_shortcut) {
        if (keyboardHandler_ != null && browser != null)
            return keyboardHandler_.onPreKeyEvent(browser, event, is_keyboard_shortcut);
        return false;
    }

    @Override
    public boolean onKeyEvent(CefBrowser browser, CefKeyEvent event) {
        if (keyboardHandler_ != null && browser != null)
            return keyboardHandler_.onKeyEvent(browser, event);
        return false;
    }

    // CefLifeSpanHandler

    public CefClient addLifeSpanHandler(CefLifeSpanHandler handler) {
        if (lifeSpanHandler_ == null) lifeSpanHandler_ = handler;
        return this;
    }

    public void removeLifeSpanHandler() {
        lifeSpanHandler_ = null;
    }

    @Override
    public boolean onBeforePopup(
            CefBrowser browser, CefFrame frame, String target_url, String target_frame_name) {
        if (isDisposed_) return true;
        if (lifeSpanHandler_ != null && browser != null)
            return lifeSpanHandler_.onBeforePopup(browser, frame, target_url, target_frame_name);
        return false;
    }

    @Override
    public void onAfterCreated(CefBrowser browser) {
        if (browser == null) return;

        // keep browser reference
        Integer identifier = browser.getIdentifier();
        synchronized (browser_) {
            browser_.put(identifier, browser);
        }
        if (lifeSpanHandler_ != null) lifeSpanHandler_.onAfterCreated(browser);
    }

    @Override
    public void onAfterParentChanged(CefBrowser browser) {
        if (browser == null) return;
        if (lifeSpanHandler_ != null) lifeSpanHandler_.onAfterParentChanged(browser);
    }

    @Override
    public boolean doClose(CefBrowser browser) {
        if (browser == null) return false;
        if (lifeSpanHandler_ != null) return lifeSpanHandler_.doClose(browser);
        return browser.doClose();
    }

    @Override
    public void onBeforeClose(CefBrowser browser) {
        if (browser == null) return;
        LOGGER.fine(() -> "CefClient.onBeforeClose() ENTER browser_id=" + browser.getIdentifier());
        if (lifeSpanHandler_ != null) lifeSpanHandler_.onBeforeClose(browser);
        browser.onBeforeClose();

        // remove browser reference
        cleanupBrowser(browser.getIdentifier());
        LOGGER.fine(() -> "CefClient.onBeforeClose() EXIT browser_id=" + browser.getIdentifier());
    }

    private void cleanupBrowser(int identifier) {
        LOGGER.fine(() -> "CefClient.cleanupBrowser(" + identifier + ") ENTER");
        synchronized (browser_) {
            if (identifier >= 0) {
                // Remove the specific browser that closed.
                browser_.remove(identifier);
            } else if (!browser_.isEmpty()) {
                // Close all browsers. Snapshot before iterating: browser.close(true)
                // synchronously triggers onBeforeClose() -> cleanupBrowser(identifier)
                // -> browser_.remove(identifier), which would otherwise mutate
                // browser_.values() while this loop is still iterating it
                // (ConcurrentModificationException, timing-dependent on when CEF's
                // callback fires relative to the loop).
                Collection<CefBrowser> browserList = new ArrayList<>(browser_.values());
                for (CefBrowser browser : browserList) {
                    browser.close(true);
                }
                LOGGER.fine(() -> "CefClient.cleanupBrowser(" + identifier + ") EXIT (closed all)");
                return;
            }

            if (browser_.isEmpty() && isDisposed_ && !handlersRemoved_) {
                // See handlersRemoved_'s own field comment -- this guard is the
                // actual fix for issue #22's confirmed double-release. Each
                // remove*Handler() call below is still traced individually so a
                // future crash in here pinpoints exactly which handler removal
                // it was inside of.
                handlersRemoved_ = true;
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(
                        propertyChangeListener);
                LOGGER.fine("cleanupBrowser: removeContextMenuHandler CALL");
                removeContextMenuHandler(this);
                LOGGER.fine("cleanupBrowser: removeDialogHandler CALL");
                removeDialogHandler(this);
                LOGGER.fine("cleanupBrowser: removeDisplayHandler CALL");
                removeDisplayHandler(this);
                LOGGER.fine("cleanupBrowser: removeDownloadHandler CALL");
                removeDownloadHandler(this);
                LOGGER.fine("cleanupBrowser: removeDragHandler CALL");
                removeDragHandler(this);
                LOGGER.fine("cleanupBrowser: removeFindHandler CALL");
                removeFindHandler(this);
                LOGGER.fine("cleanupBrowser: removeFocusHandler CALL");
                removeFocusHandler(this);
                LOGGER.fine("cleanupBrowser: removeFrameHandler CALL");
                removeFrameHandler(this);
                LOGGER.fine("cleanupBrowser: removeJSDialogHandler CALL");
                removeJSDialogHandler(this);
                LOGGER.fine("cleanupBrowser: removeKeyboardHandler CALL");
                removeKeyboardHandler(this);
                LOGGER.fine("cleanupBrowser: removeLifeSpanHandler CALL");
                removeLifeSpanHandler(this);
                LOGGER.fine("cleanupBrowser: removeLoadHandler CALL");
                removeLoadHandler(this);
                LOGGER.fine("cleanupBrowser: removePermissionHandler CALL");
                removePermissionHandler(this);
                LOGGER.fine("cleanupBrowser: removePrintHandler CALL");
                removePrintHandler(this);
                LOGGER.fine("cleanupBrowser: removeRenderHandler CALL");
                removeRenderHandler(this);
                LOGGER.fine("cleanupBrowser: removeRequestHandler CALL");
                removeRequestHandler(this);
                LOGGER.fine("cleanupBrowser: removeWindowHandler CALL");
                removeWindowHandler(this);
                LOGGER.fine("cleanupBrowser: all remove*Handler calls done, calling super.dispose()");
                super.dispose();

                CefApp.getInstance().clientWasDisposed(this);
            }
        }
        LOGGER.fine(() -> "CefClient.cleanupBrowser(" + identifier + ") EXIT");
    }

    // CefLoadHandler

    public CefClient addLoadHandler(CefLoadHandler handler) {
        if (loadHandler_ == null) loadHandler_ = handler;
        return this;
    }

    public void removeLoadHandler() {
        loadHandler_ = null;
    }

    @Override
    public void onLoadingStateChange(
            CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
        if (loadHandler_ != null && browser != null)
            loadHandler_.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward);
    }

    @Override
    public void onLoadStart(CefBrowser browser, CefFrame frame, TransitionType transitionType) {
        if (loadHandler_ != null && browser != null)
            loadHandler_.onLoadStart(browser, frame, transitionType);
    }

    @Override
    public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
        if (loadHandler_ != null && browser != null)
            loadHandler_.onLoadEnd(browser, frame, httpStatusCode);
    }

    @Override
    public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode,
            String errorText, String failedUrl) {
        if (loadHandler_ != null && browser != null)
            loadHandler_.onLoadError(browser, frame, errorCode, errorText, failedUrl);
    }

    // CefPermissionHandler

    public CefClient addPermissionHandler(CefPermissionHandler handler) {
        if (permissionHandler_ == null) permissionHandler_ = handler;
        return this;
    }

    public void removePermissionHandler() {
        permissionHandler_ = null;
    }

    @Override
    public boolean onRequestMediaAccessPermission(CefBrowser browser, CefFrame frame,
            String requestingOrigin, int requestedPermissions,
            CefMediaAccessCallback callback) {
        if (permissionHandler_ != null && browser != null)
            return permissionHandler_.onRequestMediaAccessPermission(
                    browser, frame, requestingOrigin, requestedPermissions, callback);
        return false;
    }

    @Override
    public boolean onShowPermissionPrompt(CefBrowser browser, long promptId,
            String requestingOrigin, int requestedPermissions,
            CefPermissionPromptCallback callback) {
        if (permissionHandler_ != null && browser != null)
            return permissionHandler_.onShowPermissionPrompt(
                    browser, promptId, requestingOrigin, requestedPermissions, callback);
        return false;
    }

    @Override
    public void onDismissPermissionPrompt(CefBrowser browser, long promptId, int result) {
        if (permissionHandler_ != null && browser != null)
            permissionHandler_.onDismissPermissionPrompt(browser, promptId, result);
    }

    // CefPrintHandler

    public CefClient addPrintHandler(CefPrintHandler handler) {
        if (printHandler_ == null) printHandler_ = handler;
        return this;
    }

    public void removePrintHandler() {
        printHandler_ = null;
    }

    @Override
    public void onPrintStart(CefBrowser browser) {
        if (printHandler_ != null && browser != null) printHandler_.onPrintStart(browser);
    }

    @Override
    public void onPrintSettings(
            CefBrowser browser, CefPrintSettings settings, boolean getDefaults) {
        if (printHandler_ != null && browser != null)
            printHandler_.onPrintSettings(browser, settings, getDefaults);
    }

    @Override
    public boolean onPrintDialog(
            CefBrowser browser, boolean hasSelection, CefPrintDialogCallback callback) {
        if (printHandler_ != null && browser != null)
            return printHandler_.onPrintDialog(browser, hasSelection, callback);
        return false;
    }

    @Override
    public boolean onPrintJob(CefBrowser browser, String documentName, String pdfFilePath,
            CefPrintJobCallback callback) {
        if (printHandler_ != null && browser != null)
            return printHandler_.onPrintJob(browser, documentName, pdfFilePath, callback);
        return false;
    }

    @Override
    public void onPrintReset(CefBrowser browser) {
        if (printHandler_ != null && browser != null) printHandler_.onPrintReset(browser);
    }

    @Override
    public Dimension getPdfPaperSize(CefBrowser browser, int deviceUnitsPerInch) {
        if (printHandler_ != null && browser != null)
            return printHandler_.getPdfPaperSize(browser, deviceUnitsPerInch);
        return null;
    }

    // CefMessageRouter

    @Override
    public synchronized void addMessageRouter(CefMessageRouter messageRouter) {
        super.addMessageRouter(messageRouter);
    }

    @Override
    public synchronized void removeMessageRouter(CefMessageRouter messageRouter) {
        super.removeMessageRouter(messageRouter);
    }

    // CefRenderHandler

    @Override
    public Rectangle getViewRect(CefBrowser browser) {
        if (browser == null) return new Rectangle(0, 0, 0, 0);

        CefRenderHandler realHandler = browser.getRenderHandler();
        if (realHandler != null) return realHandler.getViewRect(browser);
        return new Rectangle(0, 0, 0, 0);
    }

    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
        if (browser == null) return new Point(0, 0);

        CefRenderHandler realHandler = browser.getRenderHandler();
        if (realHandler != null) return realHandler.getScreenPoint(browser, viewPoint);
        return new Point(0, 0);
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        if (browser == null) return;

        CefRenderHandler realHandler = browser.getRenderHandler();
        if (realHandler != null) realHandler.onPopupShow(browser, show);
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        if (browser == null) return;

        CefRenderHandler realHandler = browser.getRenderHandler();
        if (realHandler != null) realHandler.onPopupSize(browser, size);
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects,
            ByteBuffer buffer, int width, int height) {
        if (browser == null) return;

        CefRenderHandler realHandler = browser.getRenderHandler();
        if (realHandler != null)
            realHandler.onPaint(browser, popup, dirtyRects, buffer, width, height);
    }

    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> listener) {}

    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> listener) {}

    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> listener) {}

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        if (browser == null) return false;

        CefRenderHandler realHandler = browser.getRenderHandler();
        if (realHandler != null) return realHandler.startDragging(browser, dragData, mask, x, y);
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {
        if (browser == null) return;

        CefRenderHandler realHandler = browser.getRenderHandler();
        if (realHandler != null) realHandler.updateDragCursor(browser, operation);
    }

    // CefRequestHandler

    public CefClient addRequestHandler(CefRequestHandler handler) {
        if (requestHandler_ == null) requestHandler_ = handler;
        return this;
    }

    public void removeRequestHandler() {
        requestHandler_ = null;
    }

    @Override
    public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request,
            boolean user_gesture, boolean is_redirect) {
        if (requestHandler_ != null && browser != null)
            return requestHandler_.onBeforeBrowse(
                    browser, frame, request, user_gesture, is_redirect);
        return false;
    }

    @Override
    public boolean onOpenURLFromTab(
            CefBrowser browser, CefFrame frame, String target_url, boolean user_gesture) {
        if (isDisposed_) return true;
        if (requestHandler_ != null && browser != null)
            return requestHandler_.onOpenURLFromTab(browser, frame, target_url, user_gesture);
        return false;
    }

    @Override
    public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame,
            CefRequest request, boolean isNavigation, boolean isDownload, String requestInitiator,
            BoolRef disableDefaultHandling) {
        if (requestHandler_ != null && browser != null) {
            return requestHandler_.getResourceRequestHandler(browser, frame, request, isNavigation,
                    isDownload, requestInitiator, disableDefaultHandling);
        }
        return null;
    }

    @Override
    public boolean getAuthCredentials(CefBrowser browser, String origin_url, boolean isProxy,
            String host, int port, String realm, String scheme, CefAuthCallback callback) {
        if (requestHandler_ != null && browser != null)
            return requestHandler_.getAuthCredentials(
                    browser, origin_url, isProxy, host, port, realm, scheme, callback);
        return false;
    }

    @Override
    public boolean onCertificateError(
            CefBrowser browser, ErrorCode cert_error, String request_url, CefCallback callback) {
        if (requestHandler_ != null)
            return requestHandler_.onCertificateError(browser, cert_error, request_url, callback);
        return false;
    }

    @Override
    public void onRenderProcessTerminated(
            CefBrowser browser, TerminationStatus status, int error_code, String error_string) {
        if (requestHandler_ != null)
            requestHandler_.onRenderProcessTerminated(browser, status, error_code, error_string);
    }

    // CefWindowHandler

    @Override
    public Rectangle getRect(CefBrowser browser) {
        if (browser == null) return new Rectangle(0, 0, 0, 0);

        CefWindowHandler realHandler = browser.getWindowHandler();
        if (realHandler != null) return realHandler.getRect(browser);
        return new Rectangle(0, 0, 0, 0);
    }

    @Override
    public void onMouseEvent(
            CefBrowser browser, int event, int screenX, int screenY, int modifier, int button) {
        if (browser == null) return;

        CefWindowHandler realHandler = browser.getWindowHandler();
        if (realHandler != null)
            realHandler.onMouseEvent(browser, event, screenX, screenY, modifier, button);
    }

    @Override
    public boolean getScreenInfo(CefBrowser arg0, CefScreenInfo arg1) {
        return false;
    }
}
