import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ArrowUpRight, Check, Shield, Layers, Smartphone, Terminal, Cpu } from 'lucide-react';
import { CodeSnippet } from '../components/CodeSnippet';
import { Footer } from '../components/Footer';
import { Logo } from '../components/Logo';
import { SEO } from '../components/SEO';
import { ThemeToggle } from '../components/ThemeToggle';

const dependenciesCode = `// build.gradle.kts
dependencies {
    // Current release line: 0.3.0-SNAPSHOT
    // 1. Core contracts and data models (Pure Kotlin / JVM)
    implementation("dev.shantoislam.agenticwebview:browser-api:0.3.0-SNAPSHOT")

    // 2. Android WebView host implementation & runtime bridge (Android AAR)
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

const viewsQuickStart = `// Option A: Android Views (Activity / Fragment)
class MainActivity : ComponentActivity() {
    private lateinit var host: AgenticBrowserHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Create a host that owns and manages its WebView
        host = AgenticBrowserHost.create(
            context = this,
            configuration = AgenticBrowserConfiguration(
                navigation = NavigationPolicy(
                    allowedHosts = setOf(HostRule.DomainAndSubdomains("example.com")),
                ),
            ),
            delegate = BrowserHostDelegate.DenyAll,
        )

        // 2. Attach host.view to your Android View hierarchy
        setContentView(host.view)

        // 3. Issue navigation within a coroutine lifecycle scope
        lifecycleScope.launch {
            when (val result = host.session.navigate(NavigationRequest("https://example.com"))) {
                is BrowserResult.Success -> println("Navigated to: \${result.value.finalUrl}")
                is BrowserResult.Failure -> println("Navigation error: \${result.error.message}")
            }
        }
    }

    override fun onDestroy() {
        host.close() // Cancels pending operations and disposes WebView
        super.onDestroy()
    }
}`;

const composeQuickStart = `// Option B: Jetpack Compose (browser-compose)
@Composable
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

const endToEndWorkflow = `lifecycleScope.launch {
    // 1. Navigate to target URL and await page readiness
    val navRequest = NavigationRequest(
        url = "https://example.com",
        readiness = WaitCondition.PageReady,
    )

    when (val navResult = host.session.navigate(navRequest)) {
        is BrowserResult.Success -> {
            println("Navigated to: \${navResult.value.finalUrl} (Doc ID: \${navResult.value.documentId.value})")
        }
        is BrowserResult.Failure -> {
            when (val error = navResult.error) {
                is BrowserError.NavigationBlocked -> println("Navigation blocked: \${error.url} (\${error.reason})")
                is BrowserError.NavigationFailed -> println("Navigation failed: HTTP \${error.httpStatus}")
                else -> println("Navigation error: \${error.message}")
            }
            return@launch
        }
    }

    // 2. Observe the webpage semantic DOM tree
    val obsOptions = ObservationOptions(
        includeScreenshot = false,
        includeCompactText = true,
        includeOffscreenContent = false,
    )

    when (val observation = host.session.observe(obsOptions)) {
        is BrowserResult.Success -> {
            println("Page title: \${observation.value.document.title}")
            println("Revision: \${observation.value.revision.value}")

            // 3. Locate an actionable element using semantic nodes
            val targetNode = observation.value.nodes.firstOrNull { node ->
                node.elementRef != null && (
                    node.accessibleName?.contains("Get started", ignoreCase = true) == true ||
                    node.role == "button"
                )
            }
            val targetRef = targetNode?.elementRef ?: return@launch

            // 4. Execute typed Click with postcondition verification
            when (val clickResult = host.session.execute(BrowserCommand.Click(target = targetRef))) {
                is BrowserResult.Success -> {
                    val receipt = clickResult.value
                    println("Strategy: \${receipt.strategy}, verified: \${receipt.verified}, changed: \${receipt.pageChanged}")
                }
                is BrowserResult.Failure -> {
                    when (val error = clickResult.error) {
                        is BrowserError.StaleElementReference -> println("Stale reference! Re-observe required.")
                        is BrowserError.ElementOccluded -> println("Occluded by: \${error.occludedBy}")
                        is BrowserError.ActionNotVerified -> println("Not verified: \${error.message}")
                        else -> println("Click failed: \${error.message}")
                    }
                }
            }
        }
        is BrowserResult.Failure -> println("Observation failed: \${observation.error.message}")
    }
}`;

const nativePointerCode = `// Configure hardware-level Android MotionEvent touch simulation
val configuration = AgenticBrowserConfiguration(
    actions = ActionConfiguration(
        // PointerActionStrategy.DOM_ONLY (default) or NATIVE_PREFERRED
        pointerStrategy = PointerActionStrategy.NATIVE_PREFERRED,
        geometryStableCycles = 2,
        geometryTolerancePx = 1.0,
    ),
)`;

const agentToolsCode = `// Bind framework-neutral tools to any LLM runtime
val browserTools = StandardBrowserTools(
    session = host.session,
    profile = BrowserToolProfile.STANDARD, // MINIMAL (4), STANDARD (10), ADVANCED (12)
)

// Export JSON Schema definitions for LLM function calling
val definitions = browserTools.definitions

// Suspend invocation of tool from LLM function call arguments
suspend fun executeLlmTool(name: String, arguments: JsonObject) {
    val result = browserTools.invoke(name, arguments)

    if (result.success) {
        val summary = result.output["summary"]?.jsonPrimitive?.content
        println("Tool succeeded: \$summary")
    } else {
        val code = result.output["code"]?.jsonPrimitive?.content
        val recommendObservation = result.output["recommendObservation"]?.jsonPrimitive?.booleanOrNull
        println("Tool error: \$code, recommend re-observation: \$recommendObservation")
    }
}`;

const artifacts = [
  {
    name: 'browser-api',
    format: 'JAR',
    description: 'Pure Kotlin contracts, data models, configuration, commands, observations, errors, and lifecycle events.',
  },
  {
    name: 'browser-webview',
    format: 'AAR',
    description: 'Android WebView host implementation, lifecycle state machine, security policy interceptors, and bundled TypeScript page runtime.',
  },
  {
    name: 'browser-compose',
    format: 'AAR',
    description: 'Jetpack Compose UI bindings including rememberAgenticBrowserHost and AgenticBrowserView.',
  },
  {
    name: 'agent-tools',
    format: 'JAR',
    description: 'Framework-neutral JSON Schema definitions for 12 browser tools (Minimal, Standard, Advanced profiles) and dispatcher.',
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
  const [quickStartTab, setQuickStartTab] = React.useState<'compose' | 'views'>('compose');

  return (
    <div className="min-h-screen bg-vp-bg text-vp-fg font-sans antialiased flex flex-col items-center">
      <SEO
        title="Agentic WebView SDK"
        description="A typed, framework-neutral agentic browser SDK for Android WebView with semantic DOM observations, verified action execution, and deterministic lifecycle control."
        path="/"
      />
      <header className="w-full max-w-[760px] px-4 sm:px-6 h-20 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Logo className="w-8 h-8 rounded-lg" />
          <span className="font-medium tracking-tight">Agentic WebView</span>
        </div>
        <ThemeToggle />
      </header>

      <main className="max-w-[760px] w-full px-4 sm:px-6 py-10 text-[15px] leading-[1.7]">
        {/* Hero */}
        <header className="mb-16">
          <div className="flex items-center gap-4 mb-3">
            <Logo className="w-10 h-10 rounded-lg" />
            <h1 className="text-2xl font-medium tracking-tight">Agentic WebView</h1>
          </div>
          <p className="text-vp-text text-base">
            A typed, framework-neutral browser SDK for Android. Transforms an Android <code className="font-mono text-sm bg-vp-border/50 px-1 py-0.5 rounded text-vp-fg">WebView</code> into an autonomous agent-governed browsing environment with semantic page observations, stable element references, verified action dispatch, and deterministic lifecycle control.
          </p>
          <div className="mt-6 flex flex-wrap gap-4 sm:gap-6 text-sm items-center">
            <a
              href="https://github.com/shantoislamdev/agentic-webview"
              target="_blank"
              rel="noopener noreferrer"
              className="text-vp-subtle hover:text-vp-fg transition-colors inline-flex items-center gap-1"
            >
              GitHub <ArrowUpRight size={13} />
            </a>
            <Link to="/documentation" className="text-vp-subtle hover:text-vp-fg transition-colors inline-flex items-center gap-1">
              Documentation <ArrowRight size={13} />
            </Link>
            <span className="text-xs font-mono text-vp-subtle bg-vp-border/40 px-2 py-0.5 rounded border border-vp-border">
              v0.3.0-SNAPSHOT • Active development
            </span>
          </div>
        </header>

        {/* System requirements */}
        <section className="mb-14 border border-vp-border rounded-lg p-5 bg-vp-border/10">
          <h2 className="text-base font-medium mb-3 flex items-center gap-2 text-vp-fg">
            <Cpu size={16} className="text-vp-highlight" /> System requirements
          </h2>
          <ul className="grid sm:grid-cols-2 gap-2 text-sm text-vp-text">
            <li className="flex items-center gap-2">
              <Check size={14} className="text-vp-highlight flex-shrink-0" />
              <span><strong>Android API 28+</strong> (Android 9.0 Pie or newer)</span>
            </li>
            <li className="flex items-center gap-2">
              <Check size={14} className="text-vp-highlight flex-shrink-0" />
              <span><strong>Java 17 bytecode</strong> (JDK 17+ across all modules)</span>
            </li>
            <li className="flex items-center gap-2">
              <Check size={14} className="text-vp-highlight flex-shrink-0" />
              <span><strong>Kotlin 2.0+</strong> with Coroutines support</span>
            </li>
            <li className="flex items-center gap-2">
              <Check size={14} className="text-vp-highlight flex-shrink-0" />
              <span>Host capable of rendering an Android <code className="font-mono text-xs">WebView</code></span>
            </li>
          </ul>
        </section>

        {/* Architecture */}
        <section className="mb-14">
          <h2 className="text-base font-medium mb-3 flex items-center gap-2 text-vp-fg">
            <Layers size={16} className="text-vp-highlight" /> One browser boundary
          </h2>
          <p className="text-vp-text mb-4">
            The SDK separates pure Kotlin contracts, Android WebView hosting, Jetpack Compose, generic JSON Schema tools,
            framework adapters, and a versioned TypeScript runtime. One <code className="font-mono text-xs text-vp-fg">AgenticBrowserSession</code> owns
            one configuration and lifecycle.
          </p>
          <ul className="list-disc pl-5 space-y-2 text-vp-text text-sm">
            <li><strong>Platform-neutral core:</strong> <code className="font-mono text-xs">browser-api</code> has zero Android SDK dependencies, enabling clean unit testing on desktop JVM.</li>
            <li><strong>Single session ownership:</strong> One host owns one WebView, one immutable configuration, and one deterministic lifecycle.</li>
            <li><strong>Correlated protocol bridge:</strong> JavaScript dispatch uses typed, correlated envelopes with cancellation, timeout bounds, and payload size limits.</li>
            <li><strong>Stable element references:</strong> References bind document, frame, element ID, and observed revision to eliminate stale interaction bugs.</li>
            <li><strong>Hostile page isolation:</strong> Sensitive values are automatically redacted, while same-origin and cross-origin frame boundaries are strictly enforced.</li>
          </ul>
        </section>

        {/* 6 Published Maven Central Artifacts */}
        <section className="mb-14">
          <h2 className="text-base font-medium mb-3 flex items-center gap-2 text-vp-fg">
            <Terminal size={16} className="text-vp-highlight" /> Published Maven Central artifacts
          </h2>
          <p className="text-vp-text mb-4 text-sm">
            Modularized into six published artifacts under group ID <code className="font-mono text-xs bg-vp-border/40 px-1 py-0.5 rounded text-vp-fg">dev.shantoislam.agenticwebview</code>. Add only what your project needs:
          </p>
          <div className="space-y-3">
            {artifacts.map((artifact) => (
              <div key={artifact.name} className="border border-vp-border rounded-md p-3.5 bg-vp-bg hover:border-vp-subtle transition-colors">
                <div className="flex items-center justify-between gap-2 mb-1">
                  <span className="font-mono text-sm text-vp-fg font-medium">
                    {artifact.name}
                  </span>
                  <span className="text-[11px] font-mono text-vp-subtle bg-vp-border/60 px-1.5 py-0.5 rounded">
                    {artifact.format}
                  </span>
                </div>
                <p className="text-[13px] text-vp-text leading-relaxed">
                  {artifact.description}
                </p>
              </div>
            ))}
          </div>
        </section>

        {/* Dependency setup */}
        <section className="mb-14">
          <h2 className="text-base font-medium mb-3 text-vp-fg">Dependency setup</h2>
          <p className="text-vp-text text-sm mb-2">
            Configure your Gradle build script with the desired modules:
          </p>
          <CodeSnippet code={dependenciesCode} label="build.gradle.kts" className="my-4" />
        </section>

        {/* Quick Start: Android Views and Jetpack Compose */}
        <section className="mb-14">
          <div className="flex items-center justify-between flex-wrap gap-2 mb-3">
            <h2 className="text-base font-medium flex items-center gap-2 text-vp-fg">
              <Smartphone size={16} className="text-vp-highlight" /> Quick start: create a host & session
            </h2>
            <div className="flex rounded-md border border-vp-border p-0.5 bg-vp-border/20 text-xs">
              <button
                type="button"
                onClick={() => setQuickStartTab('compose')}
                className={`px-3 py-1 rounded transition-colors ${quickStartTab === 'compose' ? 'bg-vp-bg text-vp-fg font-medium shadow-sm' : 'text-vp-subtle hover:text-vp-fg'}`}
              >
                Jetpack Compose
              </button>
              <button
                type="button"
                onClick={() => setQuickStartTab('views')}
                className={`px-3 py-1 rounded transition-colors ${quickStartTab === 'views' ? 'bg-vp-bg text-vp-fg font-medium shadow-sm' : 'text-vp-subtle hover:text-vp-fg'}`}
              >
                Android Views
              </button>
            </div>
          </div>
          <p className="text-vp-text text-sm mb-2">
            {quickStartTab === 'compose'
              ? 'Render an agent-governed browser host with declarative Compose UI lifecycle synchronization:'
              : 'Embed an SDK-owned WebView host directly into an Activity or Fragment view hierarchy:'}
          </p>
          {quickStartTab === 'compose' ? (
            <CodeSnippet code={composeQuickStart} label="AgenticBrowserApp.kt" className="my-4" />
          ) : (
            <CodeSnippet code={viewsQuickStart} label="MainActivity.kt" className="my-4" />
          )}
        </section>

        {/* End-to-end recipe */}
        <section className="mb-14">
          <h2 className="text-base font-medium mb-3 text-vp-fg">
            End-to-end recipe: Navigate, Observe, and Click
          </h2>
          <p className="text-vp-text text-sm mb-2">
            Session methods return typed <code className="font-mono text-xs text-vp-fg">BrowserResult&lt;T&gt;</code>. Errors are structured values (<code className="font-mono text-xs text-vp-fg">BrowserResult.Failure</code>) rather than thrown exceptions, allowing safe pattern matching on typed <code className="font-mono text-xs text-vp-fg">BrowserError</code> subclasses:
          </p>
          <CodeSnippet code={endToEndWorkflow} label="BrowserInteraction.kt" className="my-4" />
        </section>

        {/* Pointer Action Strategies */}
        <section className="mb-14">
          <h2 className="text-base font-medium mb-3 flex items-center gap-2 text-vp-fg">
            <Shield size={16} className="text-vp-highlight" /> Action execution strategies & trusted events
          </h2>
          <p className="text-vp-text text-sm mb-3">
            Pointer actions support two distinct execution strategies configured via <code className="font-mono text-xs text-vp-fg">ActionConfiguration.pointerStrategy</code>:
          </p>
          <div className="grid sm:grid-cols-2 gap-3 mb-4">
            <div className="border border-vp-border rounded-md p-4">
              <div className="flex items-center justify-between gap-2 mb-1.5">
                <h3 className="font-medium text-sm text-vp-fg">DOM_ONLY (Default)</h3>
                <span className="text-[11px] font-mono text-vp-subtle bg-vp-border/60 px-1.5 py-0.5 rounded">
                  DOM_POINTER
                </span>
              </div>
              <p className="text-[13px] text-vp-text leading-relaxed">
                Dispatches synthetic <code className="font-mono text-xs text-vp-fg">PointerEvent</code> and <code className="font-mono text-xs text-vp-fg">MouseEvent</code> sequences directly inside the DOM JavaScript context. Fast, headless-friendly, and emits receipt strategy <code className="font-mono text-xs text-vp-fg">ActionExecutionStrategy.DOM_POINTER</code>.
              </p>
            </div>
            <div className="border border-vp-border rounded-md p-4 border-vp-highlight/40">
              <div className="flex items-center justify-between gap-2 mb-1.5">
                <h3 className="font-medium text-sm text-vp-fg">NATIVE_PREFERRED</h3>
                <span className="text-[11px] font-mono text-vp-highlight bg-vp-highlight/10 px-1.5 py-0.5 rounded">
                  ANDROID_NATIVE_POINTER
                </span>
              </div>
              <p className="text-[13px] text-vp-text leading-relaxed">
                Synthesizes hardware-level Android <code className="font-mono text-xs text-vp-fg">MotionEvent</code> touch gestures directly into the WebView window. Produces genuine <code className="font-mono text-xs text-vp-fg">event.isTrusted === true</code> to satisfy anti-bot mitigations, returning receipt strategy <code className="font-mono text-xs text-vp-fg">ActionExecutionStrategy.ANDROID_NATIVE_POINTER</code>.
              </p>
            </div>
          </div>
          <CodeSnippet code={nativePointerCode} label="NativePointerConfig.kt" className="my-4" />
        </section>

        {/* Typed Error Handling Architecture */}
        <section className="mb-14">
          <h2 className="text-base font-medium mb-3 text-vp-fg">
            Typed errors vs LLM agent tool codes
          </h2>
          <p className="text-vp-text text-sm mb-3">
            The SDK cleanly distinguishes between idiomatic Kotlin type safety and LLM function calling formats:
          </p>
          <div className="border border-vp-border rounded-md p-4 space-y-3 text-sm">
            <div>
              <h3 className="font-medium text-vp-fg">Kotlin SDK consumers</h3>
              <p className="text-[13px] text-vp-text mt-0.5">
                Pattern match against 23 strongly-typed <code className="font-mono text-xs text-vp-fg">BrowserError</code> sealed subclasses (such as <code className="font-mono text-xs text-vp-fg">StaleElementReference</code>, <code className="font-mono text-xs text-vp-fg">ElementOccluded</code>, <code className="font-mono text-xs text-vp-fg">NavigationBlocked</code>, <code className="font-mono text-xs text-vp-fg">RendererTerminated</code>) to access properties like <code className="font-mono text-xs text-vp-fg">error.occludedBy</code> or <code className="font-mono text-xs text-vp-fg">error.target</code>.
              </p>
            </div>
            <div className="pt-2 border-t border-vp-border">
              <h3 className="font-medium text-vp-fg">LLM agent tools (JSON)</h3>
              <p className="text-[13px] text-vp-text mt-0.5">
                Envelopes emitted by <code className="font-mono text-xs text-vp-fg">agent-tools</code> and JSON-RPC serialize stable uppercase string error codes (<code className="font-mono text-xs text-vp-fg">"STALE_ELEMENT"</code>, <code className="font-mono text-xs text-vp-fg">"PAGE_NOT_READY"</code>, <code className="font-mono text-xs text-vp-fg">"ELEMENT_OCCLUDED"</code>) alongside actionable recovery hints (<code className="font-mono text-xs text-vp-fg">recommendObservation: true</code>).
              </p>
            </div>
          </div>
        </section>

        {/* Framework Integrations */}
        <section className="mb-14">
          <h2 className="text-base font-medium mb-3 text-vp-fg">
            Framework-neutral tools, Koog, and JSON-RPC 2.0
          </h2>
          <p className="text-vp-text text-sm mb-3">
            Expose browser actions to any agent framework with 12 typed tools organized into 3 profiles (<code className="font-mono text-xs text-vp-fg">MINIMAL</code>: 4 tools, <code className="font-mono text-xs text-vp-fg">STANDARD</code>: 10 tools, <code className="font-mono text-xs text-vp-fg">ADVANCED</code>: 12 tools):
          </p>
          <CodeSnippet code={agentToolsCode} label="AgentToolsExample.kt" className="my-4" />
          <div className="grid sm:grid-cols-2 gap-3 mt-4 text-sm">
            <div className="border border-vp-border rounded-md p-3.5">
              <h3 className="font-medium text-vp-fg mb-1">JetBrains Koog</h3>
              <p className="text-[13px] text-vp-text leading-relaxed">
                Use <code className="font-mono text-xs text-vp-fg">integration-koog</code> for reflection-based <code className="font-mono text-xs text-vp-fg">ToolSet</code> bindings in autonomous Kotlin agents.
              </p>
            </div>
            <div className="border border-vp-border rounded-md p-3.5">
              <h3 className="font-medium text-vp-fg mb-1">JSON-RPC 2.0 server</h3>
              <p className="text-[13px] text-vp-text leading-relaxed">
                Use <code className="font-mono text-xs text-vp-fg">integration-jsonrpc</code> to expose browser tools over WebSocket, HTTP, or stdio to Python, Node.js, or Go agent processes.
              </p>
            </div>
          </div>
        </section>

        {/* Reference Sample App */}
        <section className="mb-14">
          <h2 className="text-base font-medium mb-3 text-vp-fg">
            Reference sample application
          </h2>
          <p className="text-vp-text text-sm mb-3">
            The repository includes a reference implementation in <code className="font-mono text-xs bg-vp-border/40 px-1 py-0.5 rounded text-vp-fg">samples/android</code> featuring a split-screen assistant with two operating modes:
          </p>
          <div className="grid sm:grid-cols-2 gap-3 text-sm">
            <div className="border border-vp-border rounded-md p-3.5">
              <h3 className="font-medium text-vp-fg mb-1">Offline Simulation Mode</h3>
              <p className="text-[13px] text-vp-text leading-relaxed">
                No API key or external network required. Uses deterministic heuristics to evaluate prompts (multi-step navigation, scrolling, DOM tree capture) with simulated reasoning delays.
              </p>
            </div>
            <div className="border border-vp-border rounded-md p-3.5">
              <h3 className="font-medium text-vp-fg mb-1">Autonomous Agent Mode</h3>
              <p className="text-[13px] text-vp-text leading-relaxed">
                Powered by JetBrains Koog. Connects to OpenAI, Ollama, or OpenRouter for multi-step exploration loops with streaming reasoning and automatic re-observation.
              </p>
            </div>
          </div>
        </section>

        {/* Documentation Link Callout */}
        <section className="pt-8 border-t border-vp-border flex flex-wrap gap-6 text-sm">
          <Link to="/documentation" className="inline-flex items-center text-vp-subtle hover:text-vp-fg transition-colors">
            Read all 18 canonical guides <ArrowRight size={14} className="ml-1.5" />
          </Link>
          <a
            href="https://github.com/shantoislamdev/agentic-webview"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center text-vp-subtle hover:text-vp-fg transition-colors"
          >
            View source on GitHub <ArrowUpRight size={14} className="ml-1.5" />
          </a>
        </section>

        <Footer className="mt-16 mb-8" />
      </main>
    </div>
  );
}
