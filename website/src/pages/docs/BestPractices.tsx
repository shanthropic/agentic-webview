import React from 'react';
import { CodeSnippet } from '../../components/CodeSnippet';
import { DocPage } from '../../components/DocPage';
import { InlineCode } from '../../components/InlineCode';

export default function BestPractices() {
  return (
    <DocPage 
      title="Best Practices & Troubleshooting"
      description="Tips for building robust and reliable web agents using the Agentic WebView SDK."
    >
        <section>
          <h2 className="text-xl font-medium mb-4 text-vp-fg">1. Page Lifecycle States</h2>
          <p className="text-vp-text mb-6 leading-relaxed">
            Understanding the <InlineCode className="text-tk-string">PageLifecycleState</InlineCode> is key to knowing when it's safe to interact with the page.
          </p>
          
          <div className="overflow-x-auto mb-8 border border-vp-border rounded-sm bg-vp-bg">
            <table className="w-full text-[14px] text-left">
              <thead className="bg-vp-input">
                <tr>
                  <th className="px-5 py-3 font-medium text-vp-fg border-b border-vp-border">State</th>
                  <th className="px-5 py-3 font-medium text-vp-fg border-b border-vp-border w-[70%]">Description</th>
                </tr>
              </thead>
              <tbody className="text-vp-text divide-y divide-vp-border">
                <tr><td className="px-5 py-3"><InlineCode>IDLE</InlineCode></td><td className="px-5 py-3">Initial state before any navigation has started.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>LOADING</InlineCode></td><td className="px-5 py-3">Page is currently loading.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>INTERACTIVE</InlineCode></td><td className="px-5 py-3">DOM is ready, but sub-resources (images, scripts) might still be loading.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>COMPLETE</InlineCode></td><td className="px-5 py-3">Page is fully settled and stable. <strong className="text-vp-fg font-medium">Capture state now.</strong></td></tr>
                <tr><td className="px-5 py-3"><InlineCode>ERROR</InlineCode></td><td className="px-5 py-3">A navigation error occurred (e.g., 404, DNS).</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>CRASHED</InlineCode></td><td className="px-5 py-3">The WebView renderer process crashed.</td></tr>
              </tbody>
            </table>
          </div>

          <CodeSnippet code={`controller.state.collect { state ->
    if (state?.pageState == PageLifecycleState.COMPLETE) {
        // Safe to capture state or execute actions
    }
}`} />
        </section>

        <section>
          <h2 className="text-xl font-medium mb-4 text-vp-fg">2. Comprehensive Error Handling</h2>
          <p className="text-vp-text mb-6 leading-relaxed">
            The SDK uses <InlineCode className="text-tk-string">AgentResult</InlineCode> to provide detailed feedback on why an operation failed.
          </p>
          
          <div className="overflow-x-auto mb-8 border border-vp-border rounded-sm bg-vp-bg">
            <table className="w-full text-[14px] text-left">
              <thead className="bg-vp-input">
                <tr>
                  <th className="px-5 py-3 font-medium text-vp-fg border-b border-vp-border">Error Type</th>
                  <th className="px-5 py-3 font-medium text-vp-fg border-b border-vp-border w-[65%]">Description</th>
                </tr>
              </thead>
              <tbody className="text-vp-text divide-y divide-vp-border">
                <tr><td className="px-5 py-3"><InlineCode>ElementNotFound</InlineCode></td><td className="px-5 py-3">The requested agentId does not exist in the current DOM.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>ElementOccluded</InlineCode></td><td className="px-5 py-3">The element is covered by a modal, overlay, or another element.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>NavigationFailed</InlineCode></td><td className="px-5 py-3">Page failed to load. Includes the httpCode if available.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>JsEvaluationFailed</InlineCode></td><td className="px-5 py-3">The internal JS engine failed.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>JsEvaluationTimeout</InlineCode></td><td className="px-5 py-3">JavaScript execution exceeded the configured timeout.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>ScreenshotFailed</InlineCode></td><td className="px-5 py-3">Screenshot capture failed (e.g., PixelCopy error).</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>PageNotReady</InlineCode></td><td className="px-5 py-3">The page is not in a valid state for the operation.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>FileUploaderDetected</InlineCode></td><td className="px-5 py-3">Clicking a file input is blocked.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>NoNavigationHistory</InlineCode></td><td className="px-5 py-3">GoBack/GoForward called with no history.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>Timeout</InlineCode></td><td className="px-5 py-3">The operation exceeded the config timeout.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>WebViewCrashed</InlineCode></td><td className="px-5 py-3">The renderer process died.</td></tr>
              </tbody>
            </table>
          </div>

          <CodeSnippet code={`when (val result = controller.executeAction(action)) {
    is AgentResult.Success -> { /* Proceed */ }
    is AgentResult.Error -> {
        val error = result.error
        if (error is AgentError.ElementOccluded) {
            println("Element \${error.agentId} is hidden by \${error.occludedBy}")
        }
    }
}`} />
        </section>

        <section>
          <h2 className="text-xl font-medium mb-4 text-vp-fg">3. Debugging</h2>
          <h3 className="text-base font-medium pt-2 mb-3 text-vp-fg">Enable SDK Logging</h3>
          <p className="text-vp-text mb-4 leading-relaxed">
            Set <InlineCode className="text-tk-string">enableDebugLogging</InlineCode> to <InlineCode className="text-tk-keyword">true</InlineCode> in your config to see detailed logs in Logcat.
          </p>
          <CodeSnippet code={`val config = AgenticWebViewConfig.Builder()
    .setEnableDebugLogging(true)
    .build()`} />
          <h3 className="text-base font-medium pt-8 mb-3 text-vp-fg">Chrome Remote Debugging</h3>
          <p className="text-vp-text mb-4 leading-relaxed">
            Since the SDK uses a standard WebView, you can inspect the page using Chrome DevTools.
          </p>
          <CodeSnippet code={`if (BuildConfig.DEBUG) {
    WebView.setWebContentsDebuggingEnabled(true)
}`} />
        </section>

        <section>
          <h2 className="text-xl font-medium mb-4 text-vp-fg">4. Performance Tips</h2>
          <ul className="list-disc pl-[18px] space-y-4 text-vp-text mb-4 leading-relaxed">
            <li><strong className="text-vp-fg font-medium">Limit DOM Nodes:</strong> Use <InlineCode>maxDomElements</InlineCode> to keep the accessibility tree small and save LLM tokens.</li>
            <li><strong className="text-vp-fg font-medium">Viewport Expansion:</strong> Avoid setting <InlineCode>viewportExpansion = -1</InlineCode> (full page) unless absolutely necessary.</li>
          </ul>
        </section>
    </DocPage>
  );
}
