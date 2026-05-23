import { DomParser } from './domParser';
import { InteractionHandler } from './interaction';
import { Bridge, BRIDGE_VERSION } from './bridge';

const parser = new DomParser();
const interaction = new InteractionHandler();
const bridge = new Bridge();

// Throttled mutation observer to notify Kotlin of DOM changes
interaction.setupMutationObserver(() => {
    const { tree } = parser.getAccessibilityTree();
    bridge.notifyDomUpdate(JSON.stringify(tree));
});

export const AgenticEngine = {
    getAccessibilityTree(maxElements?: number) {
        return JSON.stringify(parser.getAccessibilityTree(maxElements));
    },

    getViewportInfo() {
        return JSON.stringify({
            devicePixelRatio: window.devicePixelRatio,
            visualViewportScale: window.visualViewport?.scale ?? 1,
            scrollX: window.scrollX,
            scrollY: window.scrollY,
            viewportWidth: window.innerWidth,
            viewportHeight: window.innerHeight
        });
    },

    async scrollIntoView(agentId: string, promiseId: string) {
        const el = parser.getElementById(agentId);
        if (el) {
            await interaction.scrollIntoViewAndWait(el);
            bridge.resolvePromise(promiseId, "true");
            return true;
        }
        bridge.resolvePromise(promiseId, "false");
        return false;
    },

    setInputValue(agentId: string, text: string) {
        const el = parser.getElementById(agentId);
        if (el instanceof HTMLElement) {
            return interaction.setInputValue(el, text);
        }
        return false;
    },

    setSelectOption(agentId: string, value: string) {
        const el = parser.getElementById(agentId);
        if (el instanceof HTMLSelectElement) {
            return interaction.setSelectValue(el, value);
        }
        return false;
    },

    getElementCenter(agentId: string) {
        const el = parser.getElementById(agentId);
        if (el) {
            return JSON.stringify(interaction.getPhysicalCenter(el));
        }
        return null;
    },

    dismissDialogs() {
        // This is a placeholder as JS alerts block execution.
        // Kotlin side handles this via WebChromeClient overrides.
    },

    getBridgeVersion() {
        return BRIDGE_VERSION;
    }
};

// Expose to global scope for evaluateJavascript
(window as any).__AgenticInternal = AgenticEngine;
