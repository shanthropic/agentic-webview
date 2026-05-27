import { DomParser } from './domParser';

interface IframeTreeMessage {
    type: '__agentic_iframe_tree';
    url: string;
    tree: string; // JSON-stringified AccessibilityNode[]
    maxNodeId?: number;
    maxHighlightIndex?: number;
}

export class IframeMessageBus {
    private parser: DomParser;
    private isTopFrame: boolean;

    constructor(parser: DomParser) {
        this.parser = parser;
        this.isTopFrame = window.top === window;

        if (this.isTopFrame) {
            this.listenForSubframeTrees();
        } else {
            this.relayToParent();
        }
    }

    private listenForSubframeTrees(): void {
        window.addEventListener('message', (event: MessageEvent) => {
            if (event.data?.type === '__agentic_iframe_tree') {
                const msg = event.data as IframeTreeMessage;
                try {
                    this.parser.mergeSubframeTree(msg.url, JSON.parse(msg.tree));
                } catch {
                    // ignore parse errors
                }
            }
        });
    }

    private relayToParent(): void {
        // Subframe: listen for request from parent and send tree
        window.addEventListener('message', (event: MessageEvent) => {
            if (event.data?.type === '__agentic_request_tree') {
                this.sendTreeToParent();
            }
        });

        // Auto-send tree on load
        if (document.readyState === 'complete') {
            setTimeout(() => this.sendTreeToParent(), 100);
        } else {
            window.addEventListener('load', () => {
                setTimeout(() => this.sendTreeToParent(), 100);
            });
        }
    }

    private sendTreeToParent(): void {
        try {
            const result = this.parser.getAccessibilityTree();
            const msg: IframeTreeMessage = {
                type: '__agentic_iframe_tree',
                url: location.href,
                tree: JSON.stringify(result.tree),
                maxNodeId: result.maxNodeId,
                maxHighlightIndex: result.maxHighlightIndex,
            };
            window.parent.postMessage(msg, '*');
        } catch {
            // ignore errors
        }
    }

    requestSubframeTrees(): void {
        if (!this.isTopFrame) return;
        const iframes = document.querySelectorAll('iframe');
        for (const iframe of iframes) {
            try {
                iframe.contentWindow?.postMessage({ type: '__agentic_request_tree' }, '*');
            } catch {
                // cross-origin, can't post
            }
        }
    }
}
