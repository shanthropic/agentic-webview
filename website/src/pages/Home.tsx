import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ArrowUpRight } from 'lucide-react';
import { CodeSnippet } from '../components/CodeSnippet';
import { Footer } from '../components/Footer';
import { Logo } from '../components/Logo';
import { SEO } from '../components/SEO';
import { ThemeToggle } from '../components/ThemeToggle';

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
            is BrowserResult.Success -> println("Navigated to: \${result.value.finalUrl}")
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
            configuration = AgenticBrowserConfiguration(
                navigation = NavigationPolicy(
                    allowedHosts = setOf(HostRule.DomainAndSubdomains("example.com")),
                ),
            ),
            delegate = BrowserHostDelegate.DenyAll,
        )

        setContentView(host.view)

        lifecycleScope.launch {
            when (val result = host.session.navigate(NavigationRequest("https://example.com"))) {
                is BrowserResult.Success -> println("Navigated to: \${result.value.finalUrl}")
                is BrowserResult.Failure -> println("Navigation error: \${result.error.message}")
            }
        }
    }

    override fun onDestroy() {
        host.close()
        super.onDestroy()
    }
}`;

const agentToolsQuickStart = `// Bind framework-neutral browser tools (MINIMAL, STANDARD, or ADVANCED profile)
val browserTools = StandardBrowserTools(
    session = host.session,
    profile = BrowserToolProfile.STANDARD,
)

// Export standard JSON Schema definitions to any LLM runtime
val definitions = browserTools.definitions

// Suspend invocation of tool from model function call arguments
suspend fun handleToolCall(name: String, arguments: JsonObject) {
    val result = browserTools.invoke(name, arguments)
    if (result.success) {
        println("Tool output: \${result.output}")
    } else {
        val code = result.output["code"]?.jsonPrimitive?.content
        val retryHint = result.output["recommendObservation"]
        println("Tool error: \$code (recommendObservation=\$retryHint)")
    }
}`;

const installationCode = `// build.gradle.kts
dependencies {
    // Current release line: 0.3.0-SNAPSHOT
    // 1. Core contracts and data models (Pure Kotlin / JVM)
    implementation("dev.shantoislam.agenticwebview:browser-api:0.3.0-SNAPSHOT")

    // 2. Android Views WebView host (Android AAR)
    implementation("dev.shantoislam.agenticwebview:browser-webview:0.3.0-SNAPSHOT")

    // 3. Jetpack Compose UI integration (Android AAR, optional)
    implementation("dev.shantoislam.agenticwebview:browser-compose:0.3.0-SNAPSHOT")

    // 4. Framework-neutral agent tool definitions & dispatcher (JVM, optional)
    implementation("dev.shantoislam.agenticwebview:agent-tools:0.3.0-SNAPSHOT")

    // 5. JetBrains Koog agent framework binding (JVM, optional)
    implementation("dev.shantoislam.agenticwebview:integration-koog:0.3.0-SNAPSHOT")

    // 6. Transport-neutral JSON-RPC 2.0 tool server (JVM, optional)
    implementation("dev.shantoislam.agenticwebview:integration-jsonrpc:0.3.0-SNAPSHOT")
}`;

const workflowCode = `lifecycleScope.launch {
    // 1. Navigate to target URL and await page readiness
    val navResult = host.session.navigate(
        NavigationRequest("https://example.com", readiness = WaitCondition.PageReady)
    )
    if (navResult is BrowserResult.Failure) return@launch

    // 2. Observe semantic DOM tree with stable element references
    val obsResult = host.session.observe(
        ObservationOptions(includeCompactText = true)
    )
    val observation = (obsResult as? BrowserResult.Success)?.value ?: return@launch

    // 3. Locate actionable element using semantic roles and accessibility labels
    val targetRef = observation.nodes.firstOrNull { node ->
        node.elementRef != null && node.role == "button" && 
            node.accessibleName?.contains("Get started", ignoreCase = true) == true
    }?.elementRef ?: return@launch

    // 4. Execute verified Click with non-occlusion and visibility validation
    when (val click = host.session.execute(BrowserCommand.Click(target = targetRef))) {
        is BrowserResult.Success -> {
            println("Click verified via \${click.value.strategy}")
        }
        is BrowserResult.Failure -> {
            when (val error = click.error) {
                is BrowserError.StaleElementReference -> println("Stale reference; re-observe required.")
                is BrowserError.ElementOccluded -> println("Occluded by: \${error.occludedBy}")
                else -> println("Click failed: \${error.message}")
            }
        }
    }
}`;

const artifacts = [
  {
    name: 'browser-api',
    format: 'JAR',
    description: 'Platform-neutral contracts, data models, configuration, commands, observations, errors, and events.',
  },
  {
    name: 'browser-webview',
    format: 'AAR',
    description: 'Android WebView host implementation, lifecycle state machine, policy interceptors, and bundled TypeScript page runtime.',
  },
  {
    name: 'browser-compose',
    format: 'AAR',
    description: 'Jetpack Compose UI bindings (rememberAgenticBrowserHost, AgenticBrowserView).',
  },
  {
    name: 'agent-tools',
    format: 'JAR',
    description: 'Framework-neutral JSON Schema tool definitions, dispatcher, and envelope serializer.',
  },
  {
    name: 'integration-koog',
    format: 'JAR',
    description: 'Reflective ToolSet adapter for the JetBrains Koog Agents library.',
  },
  {
    name: 'integration-jsonrpc',
    format: 'JAR',
    description: 'Transport-neutral JSON-RPC 2.0 tool endpoint for remote execution over HTTP, WebSocket, or stdio.',
  },
];

const docLinks = [
  { title: 'Getting started', path: '/documentation#getting-started-md', desc: 'Setup requirements, installation, and end-to-end navigate/observe/click recipe' },
  { title: 'Android Views integration', path: '/documentation#views-md', desc: 'AgenticBrowserHost, custom BrowserHostDelegate, Activity lifecycle, and events' },
  { title: 'Jetpack Compose integration', path: '/documentation#compose-md', desc: 'rememberAgenticBrowserHost, AgenticBrowserView, reactive StateFlow collection' },
  { title: 'Command reference', path: '/documentation#commands-md', desc: 'Recipes for all 7 commands, native touch simulation, await, and navigateHistory' },
  { title: 'Configuration cookbook', path: '/documentation#configuration-md', desc: 'Comprehensive reference for all 7 sub-configurations with real-world recipes' },
  { title: 'Observation model', path: '/documentation#observations-md', desc: 'Semantic DOM extraction, ElementRef identity, redaction, and metrics' },
  { title: 'Diagnostics & error catalog', path: '/documentation#diagnostics-md', desc: 'Catalog of all 23 BrowserError types and recovery recipes; Kotlin vs JSON codes' },
  { title: 'Lifecycle & event streams', path: '/documentation#lifecycle-md', desc: 'Detailed reference for the 11 session phases and 11 browser event types' },
  { title: 'Framework-neutral agent tools', path: '/documentation#agent-tools-md', desc: 'Schemas for all 12 tools, profiles (Minimal, Standard, Advanced), and JSON output' },
  { title: 'Koog agent integration', path: '/documentation#koog-md', desc: 'Step-by-step recipe for building autonomous agents using JetBrains Koog' },
  { title: 'JSON-RPC tool endpoint', path: '/documentation#jsonrpc-md', desc: 'Transport-neutral JSON-RPC 2.0 tool discovery and invocation over WebSocket/HTTP' },
  { title: 'Sample application guide', path: '/documentation#samples-md', desc: 'Architecture and runner guide for samples/android (Simulation & Autonomous Agent)' },
];

export default function Home() {
  return (
    <div className="min-h-screen bg-vp-bg text-vp-fg font-sans antialiased flex flex-col items-center">
      <SEO
        title="Agentic WebView"
        description="A typed, framework-neutral browser SDK for Android. Transforms an Android WebView into an autonomous agent-governed browsing environment with semantic page observations, document-scoped element references, verified action execution, navigation security policies, and structured error diagnostics."
        path="/"
      />

      <div className="flex w-full max-w-[1200px] px-4 sm:px-6 flex-1 items-start">
        <main className="flex-1 w-full min-w-0 pt-20 pb-8 md:pt-32 md:pb-12 md:px-8 lg:px-12 transition-all duration-300 ease-in-out opacity-100">
          <div className="max-w-[800px] mx-auto w-full">
            <header className="mb-16 sm:mb-20">
              <div className="flex items-center gap-3 sm:gap-4 mb-2">
                <Logo className="w-6 h-6 sm:w-7 sm:h-7 rounded-md flex-shrink-0" />
                <h1 className="text-xl sm:text-2xl font-medium tracking-tight text-vp-fg">Agentic WebView</h1>
              </div>
              <p className="text-vp-text text-[15px] sm:text-base leading-relaxed">
                A typed, framework-neutral browser SDK for Android. Transforms an Android <code>WebView</code> into an autonomous agent-governed browsing environment with semantic page observations, document-scoped element references, verified action execution, navigation security policies, and structured error diagnostics.
              </p>

              <div className="mt-6 flex flex-wrap items-center justify-between text-sm gap-4">
                <div className="flex items-center gap-6">
                  <a
                    href="https://github.com/shanthropic/agentic-webview"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-vp-subtle hover:text-vp-highlight transition-colors"
                  >
                    GitHub
                  </a>
                  <Link
                    to="/documentation"
                    className="text-vp-subtle hover:text-vp-highlight transition-colors"
                  >
                    Documentation
                  </Link>
                  <span className="text-vp-subtle">v0.3.0-SNAPSHOT</span>
                </div>
                <ThemeToggle />
              </div>
            </header>

            <div className="home-prose prose-vp max-w-none font-sans text-[15px] leading-[1.65] text-vp-fg antialiased">
              <section>
                <p>
                  One <code>AgenticBrowserSession</code> owns one <code>WebView</code>, one configuration, and one deterministic lifecycle. Instead of brittle JavaScript string evaluation or heavy raw HTML dumps, Agentic WebView gives agents structured semantic accessibility observations, mutation-safe element references, and verified actions with optional hardware-level touch simulation.
                </p>
                <CodeSnippet
                  tabs={[
                    { label: 'Jetpack Compose', code: composeQuickStart },
                    { label: 'Android Views', code: viewsQuickStart },
                    { label: 'Agent Tools', code: agentToolsQuickStart },
                  ]}
                  className="mt-6 mb-6"
                />
              </section>

              <section>
                <h2>Core Guarantees</h2>
                <ul>
                  <li><strong>Single Owner</strong> — One <code>AgenticBrowserSession</code> owns one WebView, one immutable configuration, and one deterministic lifecycle. No split-brain state or ambiguous ownership.</li>
                  <li><strong>Correlated Envelopes</strong> — All JavaScript communication uses correlated, versioned protocol envelopes with timeout, payload size, and pending-request limits before any script executes.</li>
                  <li><strong>Scrolled &amp; Verified Actions</strong> — Actions validate document identity, enabled state, visibility, and occlusion; commands verify postconditions before returning success.</li>
                  <li><strong>Native Touch Simulation</strong> — Optional Android hardware-like pointer synthesis (<code>ANDROID_NATIVE_POINTER</code>) creates trusted touch events for complex gesture-sensitive webapps and canvas interactions.</li>
                  <li><strong>Untrusted Page Content</strong> — Page content is marked untrusted; sensitive fields (such as <code>password</code> and <code>data-agentic-sensitive</code>) are automatically redacted before reaching models.</li>
                  <li><strong>No Direct LLM Dependency in Core</strong> — The browser core is framework-neutral. Agent tool adapters sit above the pure browser session.</li>
                </ul>
              </section>

              <section>
                <h2>Published Modules</h2>
                <p>
                  Add only the modules required by your Android app or agent service under Maven Central group <code>dev.shantoislam.agenticwebview</code>:
                </p>
                <table className="w-full text-left text-[13px] border-collapse m-0">
                  <thead>
                    <tr className="bg-vp-input/40 border-b border-vp-border">
                      <th className="py-2.5 px-3.5 font-medium text-vp-fg whitespace-nowrap">Module</th>
                      <th className="py-2.5 px-3.5 font-medium text-vp-fg whitespace-nowrap">Format</th>
                      <th className="py-2.5 px-3.5 font-medium text-vp-fg">Description</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-vp-border">
                    {artifacts.map((item) => (
                      <tr key={item.name} className="text-vp-text">
                        <td className="py-2.5 px-3.5 font-mono text-vp-fg text-[12px] whitespace-nowrap">{item.name}</td>
                        <td className="py-2.5 px-3.5 font-mono text-[11px] text-vp-subtle whitespace-nowrap">{item.format}</td>
                        <td className="py-2.5 px-3.5 text-vp-text">{item.description}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>

              <section>
                <h2>Installation</h2>
                <p>Configure your Gradle build script with the desired modules:</p>
                <CodeSnippet
                  code={installationCode}
                  label="build.gradle.kts"
                  className="mt-4 mb-6"
                />
              </section>

              <section>
                <h2>End-to-End Workflow</h2>
                <p>
                  Navigate, observe semantic page nodes, locate target elements, and execute verified actions with typed error recovery:
                </p>
                <CodeSnippet
                  code={workflowCode}
                  label="BrowserWorkflow.kt"
                  className="mt-4 mb-6"
                />
              </section>

              <section>
                <h2>Documentation Index</h2>
                <p>Explore the dedicated API usage and architecture guides:</p>
                <table className="w-full text-left text-[13px] border-collapse m-0">
                  <thead>
                    <tr className="bg-vp-input/40 border-b border-vp-border">
                      <th className="py-2.5 px-3.5 font-medium text-vp-fg whitespace-nowrap">Guide</th>
                      <th className="py-2.5 px-3.5 font-medium text-vp-fg">Description</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-vp-border">
                    {docLinks.map((item) => (
                      <tr key={item.title} className="text-vp-text">
                        <td className="py-2.5 px-3.5 whitespace-nowrap">
                          <Link to={item.path} className="text-vp-fg hover:text-vp-highlight font-medium">
                            {item.title}
                          </Link>
                        </td>
                        <td className="py-2.5 px-3.5 text-vp-text">{item.desc}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>

              <section className="pt-8 mb-16 border-t border-vp-border">
                <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4 sm:gap-6 mt-8">
                  <Link
                    to="/documentation"
                    className="inline-flex items-center text-vp-subtle hover:text-vp-fg transition-colors text-[14px]"
                  >
                    <span>Read full documentation</span>
                    <ArrowRight className="ml-1.5 w-[14px] h-[14px]" />
                  </Link>
                  <a
                    href="https://github.com/shanthropic/agentic-webview"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center text-vp-subtle hover:text-vp-fg transition-colors text-[14px]"
                  >
                    <span>Star on GitHub</span>
                    <ArrowUpRight className="ml-1.5 w-[14px] h-[14px]" />
                  </a>
                </div>
              </section>
            </div>

            <Footer className="mt-12 mb-12" />
          </div>
        </main>
      </div>
    </div>
  );
}
