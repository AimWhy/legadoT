# Trust source-content WebViews

Source-content WebViews with a resolved current source are trusted execution environments and receive the complete `java`, `source`, and application-global `cache` bridge compatible with `legado-E/main@21855a7bf901becfd1caba5cf30a3c84fd1533e1`. This intentionally permits top-level pages to read source credentials, share application cache keys, execute Rhino scripts, access files, and make network requests because compatibility with existing source pages takes precedence over sandboxing; the bridge is installed only in `BottomWebViewDialog`, `WebViewActivity`, `ReadRssActivity`, and `BackstageWebView` when a source exists, and is excluded from login, editor, source-less, and error WebViews.

The stable `window.java`, `window.source`, and `window.cache` globals are available from document start across top-level navigations. Native bridge names remain internal, and stable globals are not guaranteed inside child frames.
