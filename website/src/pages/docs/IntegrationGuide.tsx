import React from 'react';
import { CodeSnippet } from '../../components/CodeSnippet';
import { DocPage } from '../../components/DocPage';
import { useLatestRelease } from '../../hooks/useLatestRelease';
import { InlineCode } from '../../components/InlineCode';

export default function IntegrationGuide() {
  const { version } = useLatestRelease();

  return (
    <DocPage 
      title="Integration Guide"
      description="This guide covers how to add the Agentic WebView SDK to your Android project and initialize it."
    >
        <section>
          <h2 className="text-xl font-medium mb-4 text-vp-fg">1. Add Dependency</h2>
          <CodeSnippet code={`dependencies {
    implementation("com.shantoislamdev:agentic-webview:${version}")
}`} />
        </section>

        <section>
          <h2 className="text-xl font-medium mb-4 text-vp-fg">2. Basic Setup (Views)</h2>
          <h3 className="text-base font-medium mb-3 text-vp-fg">Layout XML</h3>
          <CodeSnippet code={`<dev.shantoislam.agenticwebview.AgenticWebView
    android:id="@+id/agentic_webview"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />`} />
          <h3 className="text-base font-medium mt-8 mb-3 text-vp-fg">Activity/Fragment</h3>
          <CodeSnippet code={`val webView = findViewById<AgenticWebView>(R.id.agentic_webview)
val controller = AgenticWebController()

// Attach the controller to the WebView
controller.attach(webView)

// Load a URL
webView.loadUrl("https://www.example.com")`} />
        </section>

        <section>
          <h2 className="text-xl font-medium mb-4 text-vp-fg">3. Jetpack Compose Integration</h2>
          <CodeSnippet code={`val controller = remember { AgenticWebController() }

AgenticWebViewComposable(
    controller = controller,
    modifier = Modifier.fillMaxSize(),
    config = AgenticWebViewConfig.Builder()
        .setEnableDebugLogging(true)
        .build()
)`} />
        </section>

        <section>
          <h2 className="text-xl font-medium mb-4 text-vp-fg">4. Configuration Options</h2>
          <p className="text-vp-text mb-6 leading-relaxed">
            You can customize the SDK's behavior using <InlineCode className="text-tk-string">AgenticWebViewConfig</InlineCode>.
          </p>
          
          <div className="overflow-x-auto mb-8 border border-vp-border rounded-sm bg-vp-bg">
            <table className="w-full text-[14px] text-left">
              <thead className="bg-vp-input">
                <tr>
                  <th className="px-5 py-3 font-medium text-vp-fg border-b border-vp-border">Option</th>
                  <th className="px-5 py-3 font-medium text-vp-fg border-b border-vp-border">Default</th>
                  <th className="px-5 py-3 font-medium text-vp-fg border-b border-vp-border w-[50%]">Description</th>
                </tr>
              </thead>
              <tbody className="text-vp-text divide-y divide-vp-border">
                <tr><td className="px-5 py-3"><InlineCode>screenshotEnabled</InlineCode></td><td className="px-5 py-3"><InlineCode className="text-tk-keyword">true</InlineCode></td><td className="px-5 py-3">Capture screenshots during state capture.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>maxDomElements</InlineCode></td><td className="px-5 py-3"><InlineCode className="text-tk-number">500</InlineCode></td><td className="px-5 py-3">Limit the number of nodes in the tree.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>enableAntiDetection</InlineCode></td><td className="px-5 py-3"><InlineCode className="text-tk-keyword">true</InlineCode></td><td className="px-5 py-3">Hide WebDriver flags.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>viewportExpansion</InlineCode></td><td className="px-5 py-3"><InlineCode className="text-tk-number">0</InlineCode></td><td className="px-5 py-3">Px to capture outside viewport.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>jsEvaluationTimeoutMs</InlineCode></td><td className="px-5 py-3"><InlineCode className="text-tk-number">5000</InlineCode></td><td className="px-5 py-3">Timeout for JavaScript execution.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>pageSettleTimeoutMs</InlineCode></td><td className="px-5 py-3"><InlineCode className="text-tk-number">10000</InlineCode></td><td className="px-5 py-3">Max wait time for page complete state.</td></tr>
                <tr><td className="px-5 py-3"><InlineCode>elementStabilityTimeoutMs</InlineCode></td><td className="px-5 py-3"><InlineCode className="text-tk-number">1000</InlineCode></td><td className="px-5 py-3">Wait time for element positions to stabilize.</td></tr>
              </tbody>
            </table>
          </div>
        </section>

        <section>
          <h2 className="text-xl font-medium mb-4 text-vp-fg">5. Security & Domain Control</h2>
          <p className="text-vp-text mb-4 leading-relaxed">
            For production apps, you should restrict where the agent can navigate using domain allow-lists or deny-lists.
          </p>
          <CodeSnippet code={`val config = AgenticWebViewConfig.Builder()
    .setAllowedHosts(setOf("google.com", "github.com"))
    .setDeniedHosts(setOf("malicious-site.com"))
    .setHomeUrl("https://my-safe-homepage.com")
    .build()`} />
          <ul className="list-disc pl-[18px] space-y-3 text-vp-text mt-8 leading-relaxed">
            <li><strong className="text-vp-fg font-medium">allowedHosts:</strong> If set, the WebView will block any navigation to hosts not in this set.</li>
            <li><strong className="text-vp-fg font-medium">deniedHosts:</strong> If set, navigation to these hosts will be blocked.</li>
            <li><strong className="text-vp-fg font-medium">homeUrl:</strong> If a navigation is blocked, the WebView can optionally redirect the user here.</li>
          </ul>
        </section>
    </DocPage>
  );
}
