import React from 'react';
import { CopyButton } from '../components/CopyButton';
import { Link } from 'react-router-dom';
import { ThemeToggle } from '../components/ThemeToggle';
import { Logo } from '../components/Logo';
import { CodeSnippet } from '../components/CodeSnippet';
import { InlineCode } from '../components/InlineCode';
import { Footer } from '../components/Footer';
import { ArrowRight, ArrowUpRight } from 'lucide-react';
import { useLatestRelease } from '../hooks/useLatestRelease';
import { SEO } from '../components/SEO';

const integrationCode = `// initialize the controller
val controller = remember { AgenticWebController() }

// attach to compose
AgenticWebViewComposable(
    controller = controller,
    modifier = Modifier.fillMaxSize(),
    config = AgenticWebViewConfig(enableDebugLogging = true)
)

// listen to standard browser events
controller.state.collect { state ->
    state?.let { 
        println("Tracking location: " + it.url)
    }
}

// perform agentic actions
val result = controller.executeAction(
    AgentAction.Navigate("https://google.com")
)`;

const inputCode = `[15]<button title="Submit" />
[16]<input type="text" />
[22]<select aria-label="Country">
  [23]<option>USA</option>
  [24]<option>Canada</option>
</select>`;

const outputCode = `val action = AgentAction.InputText(
    agentId = "16",
    text = "Machine Learning",
    clearFirst = true
)

controller.executeAction(action)`;

export default function Home() {
  const { version, tagVersion } = useLatestRelease();
  const gettingStartedCode = `implementation("dev.shantoislam:agentic-webview:${version}")`;

  return (
    <div className="min-h-screen bg-vp-bg text-vp-fg font-sans antialiased flex flex-col items-center">
      <SEO 
        title="Agentic WebView SDK"
        description="Bridging large language models and Android WebViews for seamless agentic interactions on device."
        path="/"
      />
      <header className="w-full max-w-[700px] px-4 sm:px-6 h-16 sm:h-20 flex items-center justify-end">
        <ThemeToggle />
      </header>
      <main className="max-w-[700px] w-full mx-auto px-4 sm:px-6 py-8 sm:py-12 font-sans text-[15px] leading-[1.65] text-vp-fg antialiased">
        <header className="mb-16 sm:mb-20">
        <div className="flex items-center gap-3 sm:gap-4 mb-2">
          <Logo className="w-8 h-8 sm:w-10 sm:h-10 shadow-sm rounded-lg shrink-0" />
          <h1 className="text-xl sm:text-2xl font-medium tracking-tight">Agentic WebView SDK</h1>
        </div>
        <p className="text-vp-text">Bridging large language models and Android WebViews.</p>
        
        <div className="mt-6 flex gap-6 text-sm">
          <a href="https://github.com/shantoislamdev/agentic-webview" className="text-vp-subtle hover:text-vp-highlight active:text-vp-highlight transition-colors">GitHub</a>
          <Link to="/documentation" className="text-vp-subtle hover:text-vp-highlight active:text-vp-highlight transition-colors">Documentation</Link>
          <span className="text-vp-subtle cursor-default">{tagVersion}</span>
        </div>
      </header>

      <section className="mb-16">
        <h2 className="text-base font-medium mb-3 text-vp-fg">Abstract</h2>
        <p className="mb-4 text-vp-text">
          Traditional WebViews are hostile to automated agents. The Agentic WebView SDK gives 
          LLM-powered agents real web-browsing capabilities inside Android applications. It accomplishes this by injecting 
          an orchestration engine that parses, simplifies, and translates the DOM into an 
          accessibility tree designed specifically for LLM token-efficiency.
        </p>
        <p className="text-vp-text">
          Simultaneously, the library captures hardware-accelerated viewport screenshots for vision models, 
          resolves framework-specific interactivity barriers (such as React, Vue, or Angular event systems), and maps LLM tool 
          calls into native, simulated touch coordinates.
        </p>
      </section>

      <section className="mb-16">
        <h2 className="text-base font-medium mb-3 text-vp-fg">Capabilities</h2>
        <ul className="list-disc pl-[18px] space-y-2 text-vp-text">
          <li><span className="text-vp-fg font-medium">Simplified DOM representation:</span> Converts HTML into a clean, token-optimized JSON array with stable interaction tags that persist across state mutations.</li>
          <li><span className="text-vp-fg font-medium">Hardware-accelerated vision:</span> Synchronous viewport screenshots utilizing <InlineCode className="text-tk-string px-2">PixelCopy</InlineCode>, ensuring WebGL canvases and video layers are captured.</li>
          <li><span className="text-vp-fg font-medium">Occlusion resolution:</span> Dynamically identifies if target elements are hidden behind z-indexed modals, floating elements, or out of the scrolling viewport.</li>
          <li><span className="text-vp-fg font-medium">Framework safety:</span> Translates programmatic agent interactions into simulated native physical touch models, bypassing common front-end protections.</li>
        </ul>
      </section>

      <section className="mb-16">
        <h2 className="text-base font-medium mb-3 text-vp-fg">Integration</h2>
        <p className="mb-4 text-vp-text">
          The SDK establishes a bidirectional bridge over the <InlineCode className="text-tk-string px-2">@JavascriptInterface</InlineCode>, exposing standard Kotlin coroutine patterns and providing first-class Jetpack Compose wrappers.
        </p>
        <CodeSnippet code={integrationCode} className="mt-4 mb-6" />
      </section>

      <section className="mb-16">
        <h2 className="text-base font-medium mb-3 text-vp-fg">Agent Topology</h2>
        <p className="mb-4 text-vp-text">
          The API abstracts coordinate mathematics and frame polling. The state capture mechanism 
          returns a structural representation capable of being fed directly into the system prompt of modern LLMs.
        </p>
        <div className="grid sm:grid-cols-2 gap-4">
          <CodeSnippet code={inputCode} label="Input (Agent Perception)" className="m-0 h-full" />
          <CodeSnippet code={outputCode} label="Output (Agent Action)" className="m-0 h-full" />
        </div>
      </section>

      <section className="pt-8 mb-16 border-t border-vp-border">
        <h2 className="text-base font-medium mb-3 text-vp-fg">Getting Started</h2>
        <CodeSnippet code={gettingStartedCode} className="mt-4 mb-4" />
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4 sm:gap-6 mt-8">
          <Link to="/documentation" className="inline-flex items-center text-vp-subtle hover:text-vp-fg transition-colors text-[14px]">
            Read full documentation
            <ArrowRight size={14} className="ml-1.5" />
          </Link>
          <a href="https://github.com/shantoislamdev/agentic-webview" target="_blank" rel="noopener noreferrer" className="inline-flex items-center text-vp-subtle hover:text-vp-fg transition-colors text-[14px]">
            Star on GitHub
            <ArrowUpRight size={14} className="ml-1.5" />
          </a>
        </div>
      </section>

      <Footer className="mb-8" />
    </main>
    </div>
  );
}
