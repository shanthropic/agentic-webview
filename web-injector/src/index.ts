import { DomParser } from './domParser';
import { InteractionHandler } from './interaction';
import { Bridge } from './bridge';
import { IframeMessageBus } from './iframeBus';
import { serializeTreeToText } from './serializer';
import { waitForElementStability } from './stability';

const parser = new DomParser();
const interaction = new InteractionHandler();
const bridge = new Bridge();

// ─── Engine Object ─────────────────────────────────────────────────
// CRITICAL: Assign to window FIRST, before any initialization code
// that could throw (MutationObserver, IframeMessageBus, anti-detection).
// If initialization crashes, at least the engine API is available.

export const AgenticEngine = {
    setSessionToken(token: string) {
        try { bridge.setSessionToken(token); } catch (e) { /* ignore */ }
    },

    getAccessibilityTree(maxElements?: number) {
        try {
            return JSON.stringify(parser.getAccessibilityTree(maxElements));
        } catch (e) {
            return JSON.stringify({ tree: [], truncated: false, selectorMap: {} });
        }
    },

    getViewportInfo() {
        try {
            const vvScale = window.visualViewport ? window.visualViewport.scale : 1;
            return JSON.stringify({
                devicePixelRatio: window.devicePixelRatio || 1,
                visualViewportScale: vvScale,
                scrollX: window.scrollX || 0,
                scrollY: window.scrollY || 0,
                viewportWidth: window.innerWidth || 0,
                viewportHeight: window.innerHeight || 0,
            });
        } catch (e) {
            return JSON.stringify({
                devicePixelRatio: 1, visualViewportScale: 1,
                scrollX: 0, scrollY: 0, viewportWidth: 0, viewportHeight: 0,
            });
        }
    },

    getSelectorMap() {
        try {
            const { selectorMap } = parser.getAccessibilityTree();
            return JSON.stringify(selectorMap);
        } catch (e) {
            return '{}';
        }
    },

    getCompactTree(maxElements?: number) {
        try {
            const { tree } = parser.getAccessibilityTree(maxElements);
            return serializeTreeToText(tree);
        } catch (e) {
            return '';
        }
    },

    async scrollIntoView(agentId: string, promiseId: string) {
        try {
            const el = parser.getElementById(agentId);
            if (el) {
                await interaction.scrollIntoViewAndWait(el);
                bridge.resolvePromise(promiseId, 'true');
                return true;
            }
            bridge.resolvePromise(promiseId, 'false');
            return false;
        } catch (e) {
            try { bridge.resolvePromise(promiseId, 'false'); } catch (_e) { /* ignore */ }
            return false;
        }
    },

    setInputValue(agentId: string, text: string) {
        try {
            const el = parser.getElementById(agentId);
            if (el instanceof HTMLElement) {
                return interaction.setInputValue(el, text);
            }
            return false;
        } catch (e) {
            return false;
        }
    },

    setSelectOption(agentId: string, value: string) {
        try {
            const el = parser.getElementById(agentId);
            if (el instanceof HTMLSelectElement) {
                return interaction.setSelectValue(el, value);
            }
            return false;
        } catch (e) {
            return false;
        }
    },

    getElementCenter(agentId: string) {
        try {
            const el = parser.getElementById(agentId);
            if (el) {
                return JSON.stringify(interaction.getPhysicalCenter(el));
            }
            return null;
        } catch (e) {
            return null;
        }
    },

    sendKeys(keys: string) {
        try { return interaction.sendKeys(keys); } catch (e) { return false; }
    },

    scrollToPercent(yPercent: number, agentId?: string) {
        try {
            const el = agentId ? parser.getElementById(agentId) : undefined;
            interaction.scrollToPercent(yPercent, el);
            return true;
        } catch (e) { return false; }
    },

    scrollToTop(agentId?: string) {
        try {
            const el = agentId ? parser.getElementById(agentId) : undefined;
            interaction.scrollToTop(el);
            return true;
        } catch (e) { return false; }
    },

    scrollToBottom(agentId?: string) {
        try {
            const el = agentId ? parser.getElementById(agentId) : undefined;
            interaction.scrollToBottom(el);
            return true;
        } catch (e) { return false; }
    },

    previousPage(agentId?: string) {
        try {
            const el = agentId ? parser.getElementById(agentId) : undefined;
            interaction.previousPage(el);
            return true;
        } catch (e) { return false; }
    },

    nextPage(agentId?: string) {
        try {
            const el = agentId ? parser.getElementById(agentId) : undefined;
            interaction.nextPage(el);
            return true;
        } catch (e) { return false; }
    },

    scrollToText(text: string, nth: number = 0) {
        try { return interaction.scrollToText(text, nth); } catch (e) { return false; }
    },

    getDropdownOptions(agentId: string) {
        try {
            const el = parser.getElementById(agentId);
            if (el) {
                return JSON.stringify(interaction.getDropdownOptions(el));
            }
            return '[]';
        } catch (e) {
            return '[]';
        }
    },

    selectDropdownOption(agentId: string, text: string) {
        try {
            const el = parser.getElementById(agentId);
            if (el) {
                return interaction.selectDropdownOption(el, text);
            }
            return false;
        } catch (e) {
            return false;
        }
    },

    async waitForStability(agentId: string, timeoutMs: number = 1000) {
        try {
            const el = parser.getElementById(agentId);
            if (!el) return false;
            return waitForElementStability(el, timeoutMs);
        } catch (e) {
            return false;
        }
    },

    dismissDialogs() {
        // Placeholder — Kotlin side handles via WebChromeClient overrides.
    },
};

// ─── Assign to window IMMEDIATELY ──────────────────────────────────
// This MUST happen before any code that could throw (MutationObserver,
// IframeMessageBus, anti-detection). If any of that crashes, the
// engine API is still available for Kotlin to call.
(window as any).__AgenticInternal = AgenticEngine;

// ─── Post-assignment initialization (safe to crash) ────────────────

function injectAntiDetection(): void {
    try {
        Object.defineProperty(navigator, 'webdriver', { get: function() { return undefined; } });
    } catch (e) { /* ignore */ }
    try {
        (window as any).chrome = { runtime: {} };
    } catch (e) { /* ignore */ }
    try {
        const origAttachShadow = Element.prototype.attachShadow;
        Element.prototype.attachShadow = function (opts: ShadowRootInit) {
            return origAttachShadow.call(this, Object.assign({}, opts, { mode: 'open' }));
        };
    } catch (e) { /* ignore */ }
}

function setupMutationObserverSafe(): void {
    if (!document.body) {
        // Body not available yet — defer until DOMContentLoaded
        document.addEventListener('DOMContentLoaded', function() {
            setupMutationObserverSafe();
        }, { once: true });
        return;
    }
    try {
        interaction.setupMutationObserver(function() {
            try {
                var result = parser.getAccessibilityTree();
                bridge.notifyDomUpdate(JSON.stringify({ tree: result.tree, selectorMap: result.selectorMap }));
            } catch (e) { /* ignore mutation observer errors */ }
        });
    } catch (e) {
        // MutationObserver setup failed — non-fatal, bridge updates won't fire
    }
}

// Initialize IframeMessageBus (safe — only adds event listeners)
try {
    new IframeMessageBus(parser);
} catch (e) { /* ignore */ }

injectAntiDetection();
setupMutationObserverSafe();
