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
                is BrowserResult.Success -> println("Navigated: \${result.value.finalUrl}")
                is BrowserResult.Failure -> println("Navigation error: \${result.error.message}")
            }
        }
    }

    override fun onDestroy() {
        host.close()
        super.onDestroy()
    }
}`;

const agentToolsQuickStart = `// Bind framework-neutral tools (MINIMAL, STANDARD, or ADVANCED profile)
val browserTools = StandardBrowserTools(
    session = host.session,
    profile = BrowserToolProfile.STANDARD,
)

// Export JSON Schema definitions to any LLM runtime
val definitions = browserTools.definitions

// Suspend invocation of tool from LLM function call arguments
suspend fun executeLlmTool(name: String, arguments: JsonObject) {
    val result = browserTools.invoke(name, arguments)
    if (result.success) {
        println("Tool output: \${result.output}")
    } else {
        val code = result.output["code"]?.jsonPrimitive?.content
        println("Tool error: \$code (recommendObservation=\${result.output["recommendObservation"]})")
    }
}`;

const installationCode = `// build.gradle.kts
dependencies {
    // Current release line: 0.3.0-SNAPSHOT
    // Core contracts & data models (Pure Kotlin / JVM)
    implementation("dev.shantoislam.agenticwebview:browser-api:0.3.0-SNAPSHOT")

    // Android WebView host implementation & runtime bridge (Android AAR)
    implementation("dev.shantoislam.agenticwebview:browser-webview:0.3.0-SNAPSHOT")

    // Jetpack Compose UI integration (Android AAR, optional)
    implementation("dev.shantoislam.agenticwebview:browser-compose:0.3.0-SNAPSHOT")

    // Framework-neutral agent tool definitions & dispatcher (JVM, optional)
    implementation("dev.shantoislam.agenticwebview:agent-tools:0.3.0-SNAPSHOT")

    // JetBrains Koog agent framework binding (JVM, optional)
    implementation("dev.shantoislam.agenticwebview:integration-koog:0.3.0-SNAPSHOT")

    // Transport-neutral JSON-RPC 2.0 tool server (JVM, optional)
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
    description: 'Framework-neutral JSON Schema tool definitions (Minimal, Standard, Advanced profiles) and dispatcher.',
  },
  {
    name: 'integration-koog',
    format: 'JAR',
    description: 'Reflective ToolSet adapter for autonomous browser agents built on JetBrains Koog.',
  },
  {
    name: 'integration-jsonrpc',
    format: 'JAR',
    description: 'Transport-neutral JSON-RPC 2.0 tool endpoint for remote execution over HTTP, WebSocket, or stdio.',
  },
];

export default function Home() {
  return (
    <div className="min-h-screen bg-vp-bg text-vp-fg font-sans antialiased flex flex-col items-center">
      <SEO
        title="Agentic WebView SDK"
        description="A typed, framework-neutral agentic browser SDK for Android WebView with semantic DOM observations, verified action execution, and deterministic lifecycle control."
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
                Platform-grade Android WebView orchestration for AI agents with semantic DOM observations, verified action execution, and deterministic lifecycle control.
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
                  <code>Agentic WebView</code> provides a typed, deterministic bridge between Android applications and web pages for autonomous agents. Standard Android <code>WebView</code> exposes only untyped JavaScript string evaluation and uncoordinated callbacks, leaving agent automation fragile, prone to DOM mutation race conditions, and vulnerable to prompt injection. Agentic WebView replaces this with single-owner session lifecycles, structured semantic accessibility tree observations, verified action dispatch with non-occlusion checks, synthetic native hardware touch gestures, and hardened hostile page isolation.
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
                <h2>Architecture & Security</h2>
                <ul>
                  <li><strong>Single session ownership</strong> — One <code>AgenticBrowserSession</code> owns one WebView, one immutable configuration, and one deterministic lifecycle. No split-brain state or ambiguous ownership.</li>
                  <li><strong>Correlated protocol bridge</strong> — Typed, versioned JavaScript dispatch envelopes enforce strict timeout bounds, bounded payload sizes, and in-flight request limits before any script executes.</li>
                  <li><strong>Hostile page isolation</strong> — Page content is treated as untrusted; sensitive fields (such as <code>password</code> and <code>data-agentic-sensitive</code>) are automatically redacted before reaching models, and cross-origin frames report explicit security limitations.</li>
                  <li><strong>Navigation policy enforcement</strong> — Enforces strict host allowlisting/denylisting, HTTPS requirements, and privileged navigation delegation.</li>
                </ul>

                <h2>Observation & Verified Actions</h2>
                <ul>
                  <li><strong>Semantic DOM extraction</strong> — Extracts a compact accessibility tree with semantic roles, accessible names, and viewport bounding boxes optimized for LLM context windows and token budgets.</li>
                  <li><strong>Stable element references</strong> — Every <code>ElementRef</code> binds document ID, frame ID, node ID, and observed revision to eliminate stale-reference mutation races.</li>
                  <li><strong>Postcondition verification</strong> — Touch, click, and input commands verify visibility, pointer occlusion, and resulting page changes before returning a receipt.</li>
                  <li><strong>Hardware-level touch simulation</strong> — Supports both fast DOM pointer dispatch and synthetic Android <code>MotionEvent</code> touch gestures (<code>event.isTrusted === true</code>) for gesture-sensitive webapps and canvas interactions.</li>
                </ul>

                <h2>Framework Neutrality & Tooling</h2>
                <ul>
                  <li><strong>Zero LLM lock-in</strong> — Core contracts in <code>browser-api</code> have zero direct LLM dependencies and run cleanly on desktop JVM for fast, deterministic unit and regression testing.</li>
                  <li><strong>Typed errors vs tool codes</strong> — Idiomatic Kotlin pattern-matching on 23 <code>BrowserError</code> sealed subclasses, paired with serialized uppercase error codes and recovery hints for tool callers.</li>
                  <li><strong>Pluggable agent adapters</strong> — First-class integration modules for JetBrains Koog autonomous browser agents and transport-neutral JSON-RPC 2.0 servers over HTTP, WebSocket, or stdio.</li>
                </ul>
              </section>

              <section>
                <h2>Published Modules</h2>
                <p>
                  Modularized into six focused artifacts under Maven Central group <code>dev.shantoislam.agenticwebview</code>:
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
                  Navigate, observe semantic page nodes, and execute verified actions with typed error recovery:
                </p>
                <CodeSnippet
                  code={workflowCode}
                  label="BrowserWorkflow.kt"
                  className="mt-4 mb-6"
                />
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
