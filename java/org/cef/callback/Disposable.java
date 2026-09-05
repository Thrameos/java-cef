// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.callback;

/**
 * Implemented by JCEF objects that hold a native resource and must be
 * explicitly released via {@link #dispose()}. Extends {@link AutoCloseable}
 * so implementors can be used directly in a try-with-resources statement
 * without each one declaring its own close() alias.
 */
public interface Disposable extends AutoCloseable {
    /**
     * Releases the native resource backing this object.
     */
    void dispose();

    /**
     * Alias for {@link #dispose()}.
     */
    @Override
    default void close() {
        dispose();
    }
}
