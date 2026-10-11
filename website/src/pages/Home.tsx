import React from 'react';
import { Link } from 'react-router-dom';
import { ThemeToggle } from '../components/ThemeToggle';
import { Logo } from '../components/Logo';
import { CodeSnippet } from '../components/CodeSnippet';
import { Footer } from '../components/Footer';
import { ArrowRight, ArrowUpRight } from 'lucide-react';
import { SEO } from '../components/SEO';

const composeQuickStart = `@Composable
fun AgenticBrowserApp() {
    val host = rememberAgenticBrowserHost(
        configuration = AgenticBrowserConfiguration(
            navigation = NavigationPolicy(
                allowedHosts = setOf(HostRule.DomainAndSubdomains("example.com")),
            ),
        ),
    )

    LaunchedEffect(host) {
        when (val result = host.session.navigate(NavigationRequest("https://example.com"))) {
            is BrowserResult.Success -> println("Navigated: \${result.value.finalUrl}")
            is BrowserResult.Failure -> println("Navigation error: \${result.error.message}")
        }
    }

    AgenticBrowserView(
        host = host,
        modifier = Modifier.fillMaxSize(),
    )
}`;

const viewsQuickStart = `class MainActivity : ComponentActivity() {
    private lateinit var host: AgenticBrowserHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        host = AgenticBrowserHost.create(
            context = this,
            configuration = AgenticBrowserConfiguration(),
            delegate = BrowserHostDelegate.DenyAll,
        )
        setContentView(host.view)

        lifecycleScope.launch {
            host.session.navigate(NavigationRequest("https://example.com"))
        }
    }

    override fun onResume() { super.onResume(); host.onResume() }
    override fun onPause() { host.onPause(); super.onPause() }
    override fun onDestroy() { host.close(); super.onDestroy() }
}`;

const inputCode = `[main/15]<button role="button" name="Submit">
[main/16]<input role="textbox" name="Search...">
[main/22]<select role="combobox" name="Country">
  [main/23]<option role="option" name="USA"> USA
  [main/24]<option role="option" name="Canada"> Canada`;

const outputCode = `val result = host.session.execute(
    BrowserCommand.TypeText(
        target = elementRef,
        text = "Machine Learning",
        mode = TextInputMode.REPLACE_ALL,
    )
)`;

const gettingStartedCode = `// build.gradle.kts
dependencies {
    implementation("dev.shantoislam.agenticwebview:browser-webview:0.3.1")
    implementation("dev.shantoislam.agenticwebview:browser-compose:0.3.1") // optional
    implementation("dev.shantoislam.agenticwebview:agent-tools:0.3.1") // optional
}`;

export default function Home() {
  return (
    <div className="min-h-screen bg-vp-bg text-vp-fg font-sans antialiased flex flex-col items-center">
      <SEO
        title="Agentic WebView"
        description="A typed, framework-neutral browser SDK for Android. Transforms an Android WebView into an autonomous agent-governed browsing environment with semantic page observations, document-scoped element references, verified action execution, navigation security policies, and structured error diagnostics."
        path="/"
      />

      <header className="w-full max-w-[700px] px-4 sm:px-6 h-16 sm:h-20 flex items-center justify-end">
        <ThemeToggle />
      </header>

      <main className="max-w-[700px] w-full mx-auto px-4 sm:px-6 py-8 sm:py-12 font-sans text-[15px] leading-[1.65] text-vp-fg antialiased">
        <header className="mb-16 sm:mb-20">
          <div className="flex items-center gap-3 sm:gap-4 mb-2">
            <Logo className="w-8 h-8 sm:w-10 sm:h-10 shadow-sm rounded-lg shrink-0" />
            <h1 className="text-xl sm:text-2xl font-medium tracking-tight">Agentic WebView</h1>
          </div>
          <p className="text-vp-text">
            A typed, framework-neutral browser SDK for Android. Transforms an Android WebView into an autonomous agent-governed browsing environment with semantic page observations, document-scoped element references, verified action execution, navigation security policies, and structured error diagnostics.
          </p>

          <div className="mt-6 flex gap-6 text-sm">
            <a
              href="https://github.com/shanthropic/agentic-webview"
              target="_blank"
              rel="noopener noreferrer"
              className="text-vp-subtle hover:text-vp-highlight active:text-vp-highlight transition-colors"
            >
              GitHub
            </a>
            <Link
              to="/documentation"
              className="text-vp-subtle hover:text-vp-highlight active:text-vp-highlight transition-colors"
            >
              Documentation
            </Link>
            <span className="text-vp-subtle cursor-default">v0.3.1</span>
          </div>
        </header>

        <section className="mb-16">
          <h2 className="text-base font-medium mb-3 text-vp-fg">Abstract</h2>
          <p className="mb-4 text-vp-text">
            Standard Android WebViews are hostile to autonomous agents. Fragile JavaScript string evaluation, uncoordinated callbacks, and massive raw HTML dumps burn context windows and break on dynamic pages. Agentic WebView provides a typed, deterministic bridge between Android applications and web pages. It replaces ad-hoc script injection with single-session lifecycles, structured semantic accessibility observations, and verified action execution.
          </p>
          <p className="text-vp-text">
            The SDK provides document-scoped element references that resist DOM mutation drift, optional hardware-level Android touch simulation (<code className="text-tk-string px-1.5 py-0.5 rounded bg-vp-input font-mono text-[13px]">MotionEvent</code>), automatic sensitive data redaction, and strict navigation security policies.
          </p>
        </section>

        <section className="mb-16">
          <h2 className="text-base font-medium mb-3 text-vp-fg">Capabilities</h2>
          <ul className="list-disc pl-[18px] space-y-2 text-vp-text">
            <li>
              <span className="text-vp-fg font-medium">Semantic DOM extraction:</span> Converts web pages into a token-optimized accessibility tree with roles, names, and bounds—reducing token usage by &gt;90% compared to raw HTML.
            </li>
            <li>
              <span className="text-vp-fg font-medium">Stable element references:</span> Every <code className="text-tk-string px-1.5 py-0.5 rounded bg-vp-input font-mono text-[13px]">ElementRef</code> binds document ID, frame ID, element ID, and revision to prevent clicking mutated or stale elements.
            </li>
            <li>
              <span className="text-vp-fg font-medium">Verified action execution:</span> Commands verify visibility, pointer occlusion, and resulting page changes before returning success receipts.
            </li>
            <li>
              <span className="text-vp-fg font-medium">Hardware-level touch simulation:</span> Synthesizes authentic Android <code className="text-tk-string px-1.5 py-0.5 rounded bg-vp-input font-mono text-[13px]">MotionEvent</code> gestures (<code className="text-tk-string px-1.5 py-0.5 rounded bg-vp-input font-mono text-[13px]">ACTION_DOWN</code> / <code className="text-tk-string px-1.5 py-0.5 rounded bg-vp-input font-mono text-[13px]">ACTION_UP</code>) directly into the WebView for gesture-sensitive webapps.
            </li>
            <li>
              <span className="text-vp-fg font-medium">Hostile page isolation:</span> Enforces HTTPS by default, domain allowlists/denylists, and automatically redacts passwords and sensitive fields.
            </li>
          </ul>
        </section>

        <section className="mb-16">
          <h2 className="text-base font-medium mb-3 text-vp-fg">Integration</h2>
          <p className="mb-4 text-vp-text">
            The SDK provides first-class Jetpack Compose wrappers and classic Android Views support, exposing standard Kotlin coroutines with deterministic lifecycle management.
          </p>
          <CodeSnippet
            tabs={[
              { label: 'Jetpack Compose', code: composeQuickStart },
              { label: 'Android Views', code: viewsQuickStart },
            ]}
            className="mt-4 mb-6"
          />
        </section>

        <section className="mb-16">
          <h2 className="text-base font-medium mb-3 text-vp-fg">Agent Topology</h2>
          <p className="mb-4 text-vp-text">
            The observation engine extracts structured accessibility trees that feed directly into LLM prompts. Actions are dispatched with verified postconditions and typed error handling.
          </p>
          <div className="grid sm:grid-cols-2 gap-4">
            <CodeSnippet code={inputCode} label="Input (Agent Perception)" className="m-0 h-full" />
            <CodeSnippet code={outputCode} label="Output (Agent Action)" className="m-0 h-full" />
          </div>
        </section>

        <section className="pt-8 mb-16 border-t border-vp-border">
          <h2 className="text-base font-medium mb-3 text-vp-fg">Getting Started</h2>
          <p className="mb-4 text-vp-text">Add the dependencies to your build script:</p>
          <CodeSnippet code={gettingStartedCode} label="build.gradle.kts" className="mt-4 mb-4" />
          <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4 sm:gap-6 mt-8">
            <Link
              to="/documentation"
              className="inline-flex items-center text-vp-subtle hover:text-vp-fg transition-colors text-[14px]"
            >
              <span>Read full documentation</span>
              <ArrowRight size={14} className="ml-1.5" />
            </Link>
            <a
              href="https://github.com/shanthropic/agentic-webview"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center text-vp-subtle hover:text-vp-fg transition-colors text-[14px]"
            >
              <span>Star on GitHub</span>
              <ArrowUpRight size={14} className="ml-1.5" />
            </a>
          </div>
        </section>

        <Footer className="mb-8" />
      </main>
    </div>
  );
}
