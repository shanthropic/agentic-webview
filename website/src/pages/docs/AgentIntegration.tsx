import React from 'react';
import { CodeSnippet } from '../../components/CodeSnippet';
import { DocPage } from '../../components/DocPage';
import { InlineCode } from '../../components/InlineCode';

export default function AgentIntegration() {
  return (
    <DocPage 
      title="Agent Integration Guide"
      description={
        <>
          This guide explains how to use the <InlineCode className="text-tk-class">AgenticWebController</InlineCode> to build LLM-powered agents that can perceive and interact with web pages.
        </>
      }
      seoDescription="This guide explains how to use the AgenticWebController to build LLM-powered agents that can perceive and interact with web pages."
    >
        <section>
          <h2 className="text-xl font-medium mb-4 text-vp-fg">1. Capturing Page State</h2>
          <p className="text-vp-text mb-4 leading-relaxed">
            The <InlineCode className="text-tk-string">captureState()</InlineCode> method provides everything an agent needs to "see" the page.
          </p>
          <CodeSnippet code={`scope.launch {
    when (val result = controller.captureState()) {
        is AgentResult.Success -> {
            val state = result.data
            // 1. Pass the compact tree to your LLM text prompt
            val promptTree = state.compactTree 
            
            // 2. Pass the screenshot to your Vision model (if enabled)
            val screenshot = state.screenshotBase64
            
            println("Agent is looking at: \${state.title} (\${state.url})")
        }
        is AgentResult.Error -> {
            println("Failed to capture state: \${result.error}")
        }
    }
}`} />
          <h3 className="text-base font-medium mb-3 text-vp-fg mt-8">The Compact Tree</h3>
          <p className="text-vp-text mb-4 leading-relaxed">
            The <InlineCode className="text-tk-string">compactTree</InlineCode> is a token-optimized representation of the DOM. Each element has a <InlineCode className="text-tk-string">[highlightIndex]</InlineCode> that the agent uses to perform actions.
          </p>
          <CodeSnippet code={`[15]<button title="Submit" />
[16]<input type="text" placeholder="Search..." />`} />
        </section>

        <section>
          <h2 className="text-xl font-medium mb-4 text-vp-fg">2. Executing Actions</h2>
          <p className="text-vp-text mb-6 leading-relaxed">
            Once your agent decides on an action, execute it using <InlineCode className="text-tk-string">executeAction()</InlineCode>. The SDK handles retries and stability checks automatically.
          </p>

          <h3 className="text-base font-medium mt-8 mb-3 text-vp-fg">Navigation</h3>
          <ul className="list-disc pl-[18px] space-y-3 text-vp-text mb-8">
            <li><InlineCode>AgentAction.Navigate(url: String)</InlineCode>: Loads a new URL.</li>
            <li><InlineCode>AgentAction.GoBack</InlineCode>, <InlineCode>AgentAction.GoForward</InlineCode>, <InlineCode>AgentAction.Refresh</InlineCode>: Standard browser controls.</li>
            <li><InlineCode>AgentAction.Wait(durationMs: Long)</InlineCode>: Pauses the agent.</li>
          </ul>

          <h3 className="text-base font-medium mt-8 mb-3 text-vp-fg">Interaction</h3>
          <ul className="list-disc pl-[18px] space-y-3 text-vp-text mb-8">
            <li><InlineCode>AgentAction.Click(agentId: String)</InlineCode>: Native tap at the element's center.</li>
            <li><InlineCode>AgentAction.LongPress(agentId: String, durationMs: Long)</InlineCode>: Long press (default 500ms).</li>
            <li><InlineCode>AgentAction.InputText(agentId: String, text: String, clearFirst: Boolean)</InlineCode>: Focuses and types.</li>
            <li><InlineCode>AgentAction.SelectOption(agentId: String, value: String)</InlineCode>: Sets a <InlineCode>&lt;select&gt;</InlineCode> element's value directly.</li>
            <li><InlineCode>AgentAction.SendKeys(keys: String)</InlineCode>: Simulates keyboard shortcuts.</li>
          </ul>

          <h3 className="text-base font-medium mt-8 mb-3 text-vp-fg">Advanced Scrolling</h3>
          <ul className="list-disc pl-[18px] space-y-3 text-vp-text mb-8">
            <li><InlineCode>AgentAction.Scroll(direction: ScrollDirection, amount: Float)</InlineCode>: Scroll by fraction of viewport (0.5 = 50%).</li>
            <li><InlineCode>AgentAction.ScrollToPercent(yPercent: Float, agentId: String?)</InlineCode>: Scroll to specific %.</li>
            <li><InlineCode>AgentAction.ScrollToText(text: String, nth: Int)</InlineCode>: Find text and scroll it into view.</li>
            <li><InlineCode>AgentAction.ScrollToTop(agentId?)</InlineCode>, <InlineCode>AgentAction.ScrollToBottom(agentId?)</InlineCode>: Jump to extremes.</li>
            <li><InlineCode>AgentAction.PreviousPage(agentId: String?)</InlineCode>, <InlineCode>AgentAction.NextPage(agentId: String?)</InlineCode>: Scroll by exactly one viewport height.</li>
          </ul>

          <h3 className="text-base font-medium mt-8 mb-3 text-vp-fg">Dropdowns & Completion</h3>
          <ul className="list-disc pl-[18px] space-y-3 text-vp-text mb-4">
            <li><InlineCode>AgentAction.GetDropdownOptions(agentId: String)</InlineCode>: Enumerate all <InlineCode>&lt;option&gt;</InlineCode> elements of a <InlineCode>&lt;select&gt;</InlineCode>.</li>
            <li><InlineCode>AgentAction.SelectDropdownOption(agentId, text)</InlineCode>: Select <InlineCode>&lt;option&gt;</InlineCode> by visible text.</li>
            <li><InlineCode>AgentAction.Done(text: String, success: Boolean)</InlineCode>: Signal task completion.</li>
          </ul>
        </section>
        
        <section>
          <h2 className="text-xl font-medium mb-4 text-vp-fg">3. Monitoring Progress</h2>
          <p className="text-vp-text mb-4 leading-relaxed">
            You can observe <InlineCode className="text-tk-string">loadingProgress</InlineCode> (0-100) to show a progress bar in your UI.
          </p>
          <CodeSnippet code={`val progress by controller.loadingProgress.collectAsState()
if (progress < 100) {
    LinearProgressIndicator(progress = progress / 100f)
}`} />
        </section>

        <section>
          <h2 className="text-xl font-medium mb-4 text-vp-fg">4. Dropdown Handling</h2>
          <p className="text-vp-text mb-4 leading-relaxed">
            For <InlineCode>&lt;select&gt;</InlineCode> elements, you can retrieve all available options.
          </p>
          <CodeSnippet code={`val optionsResult = controller.getDropdownOptions(agentId = "22")
if (optionsResult is AgentResult.Success) {
    val options = optionsResult.data // List<DropdownOption>
}

// Select an option by its visible text
controller.executeAction(AgentAction.SelectDropdownOption(agentId = "22", text = "Option A"))`} />
        </section>
    </DocPage>
  );
}
