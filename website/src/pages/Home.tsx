import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ArrowUpRight } from 'lucide-react';
import { CodeSnippet } from '../components/CodeSnippet';
import { Footer } from '../components/Footer';
import { Logo } from '../components/Logo';
import { SEO } from '../components/SEO';
import { ThemeToggle } from '../components/ThemeToggle';

const quickStart = `val host = rememberAgenticBrowserHost(
    AgenticBrowserConfiguration()
)

LaunchedEffect(host) {
    host.session.navigate(
        NavigationRequest("https://example.com")
    )
}

AgenticBrowserView(
    host = host,
    modifier = Modifier.fillMaxSize()
)`;

export default function Home() {
  const dependency = 'implementation("dev.shantoislam.agenticwebview:browser-compose:VERSION")';

  return (
    <div className="min-h-screen bg-vp-bg text-vp-fg font-sans antialiased flex flex-col items-center">
      <SEO
        title="Agentic WebView SDK"
        description="A typed, framework-neutral agentic browser SDK for Android WebView."
        path="/"
      />
      <header className="w-full max-w-[760px] px-4 sm:px-6 h-20 flex items-center justify-end">
        <ThemeToggle />
      </header>
      <main className="max-w-[760px] w-full px-4 sm:px-6 py-10 text-[15px] leading-[1.7]">
        <header className="mb-16">
          <div className="flex items-center gap-4 mb-3">
            <Logo className="w-10 h-10 rounded-lg" />
            <h1 className="text-2xl font-medium tracking-tight">Agentic WebView</h1>
          </div>
          <p className="text-vp-text">Typed browser sessions for any Android agent framework.</p>
          <div className="mt-6 flex gap-6 text-sm text-vp-subtle">
            <a href="https://github.com/shantoislamdev/agentic-webview">GitHub</a>
            <Link to="/documentation">Documentation</Link>
            <span>breaking development</span>
          </div>
        </header>

        <section className="mb-14">
          <h2 className="text-base font-medium mb-3">One browser boundary</h2>
          <p className="text-vp-text">
            The SDK separates pure Kotlin contracts, Android WebView hosting, Compose, generic JSON Schema tools,
            framework adapters, and a versioned TypeScript runtime. Agents receive semantic observations with
            document- and frame-scoped references, then execute typed commands with structured outcomes.
          </p>
        </section>

        <section className="mb-14">
          <h2 className="text-base font-medium mb-3">Built for hostile page state</h2>
          <ul className="list-disc pl-5 space-y-2 text-vp-text">
            <li>Correlated protocol requests with cancellation, timeouts, and size limits.</li>
            <li>Stable DOM identity across ordinary mutations and explicit stale-reference failures.</li>
            <li>Same-origin nested frames, open Shadow DOM, bounded traversal, and privacy redaction.</li>
            <li>Secure WebView defaults with host delegates for privileged behavior.</li>
            <li>Framework-neutral tools with optional Koog and JSON-RPC adapters.</li>
          </ul>
        </section>

        <section className="mb-14">
          <h2 className="text-base font-medium mb-3">Install and create a session</h2>
          <CodeSnippet code={dependency} className="my-4" />
          <CodeSnippet code={quickStart} className="my-4" />
        </section>

        <section className="pt-8 border-t border-vp-border flex gap-6 text-sm">
          <Link to="/documentation" className="inline-flex items-center text-vp-subtle hover:text-vp-fg">
            Read canonical docs <ArrowRight size={14} className="ml-1.5" />
          </Link>
          <a href="https://github.com/shantoislamdev/agentic-webview" className="inline-flex items-center text-vp-subtle hover:text-vp-fg">
            View source <ArrowUpRight size={14} className="ml-1.5" />
          </a>
        </section>
        <Footer className="mt-16 mb-8" />
      </main>
    </div>
  );
}
