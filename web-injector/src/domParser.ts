export interface ElementBounds {
    left: number;
    top: number;
    width: number;
    height: number;
}

export interface AccessibilityNode {
    id: string;
    tag: string;
    text: string;
    role: string;
    bounds: ElementBounds;
    attributes: { [key: string]: string };
    occluded: boolean;
    inIframe: boolean;
}

export class DomParser {
    private elementMap = new Map<string, Element>();
    private nextId = 1;

    public getAccessibilityTree(maxElements: number = 500): { tree: AccessibilityNode[], truncated: boolean } {
        const nodes: AccessibilityNode[] = [];
        let truncated = false;

        try {
            const traverse = (root: ParentNode, inIframe: boolean = false) => {
                if (nodes.length >= maxElements) {
                    truncated = true;
                    return;
                }

                const children = Array.from(root.children);
                for (const child of children) {
                    if (nodes.length >= maxElements) {
                        truncated = true;
                        return;
                    }

                    if (this.isInteractive(child)) {
                        nodes.push(this.serializeNode(child, inIframe));
                    }

                    if (child.shadowRoot) {
                        traverse(child.shadowRoot, inIframe);
                    }

                    if (child.tagName === 'IFRAME') {
                        try {
                            const iframe = child as HTMLIFrameElement;
                            if (iframe.contentDocument) {
                                traverse(iframe.contentDocument, true);
                            }
                        } catch (e) {
                            console.warn('Cannot access cross-origin iframe');
                        }
                    } else {
                        traverse(child, inIframe);
                    }
                }
            };

            traverse(document.body);
            this.pruneElementMap();
        } catch (e: any) {
            console.error('Error during accessibility tree extraction', e);
            if (window.AgenticBridge) {
                try {
                    window.AgenticBridge.onError(JSON.stringify({
                        error: e.message || 'Tree walk failure',
                        stack: e.stack || ''
                    }));
                } catch (bridgeErr) {
                    // Fallback
                }
            }
        }
        return { tree: nodes, truncated };
    }

    public getElementById(id: string): Element | undefined {
        return this.elementMap.get(id);
    }

    private isInteractive(el: Element): boolean {
        if (!(el instanceof HTMLElement)) return false;

        const style = window.getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity) === 0) {
            return false;
        }

        if (el.offsetWidth === 0 && el.offsetHeight === 0) {
            return false;
        }

        const interactiveTags = ['BUTTON', 'A', 'INPUT', 'TEXTAREA', 'SELECT', 'OPTION', 'SUMMARY', 'DETAILS'];
        if (interactiveTags.includes(el.tagName)) return true;

        if (el.hasAttribute('onclick') || el.hasAttribute('role') || el.getAttribute('contenteditable') === 'true') {
            return true;
        }

        const role = el.getAttribute('role');
        const interactiveRoles = ['button', 'link', 'checkbox', 'menuitem', 'option', 'radio', 'switch', 'tab', 'textbox'];
        if (role && interactiveRoles.includes(role.toLowerCase())) return true;

        if (style.cursor === 'pointer') return true;

        // TabIndex focusability rule (excluding body & html tags)
        if (el.tabIndex >= 0 && el.tagName !== 'BODY' && el.tagName !== 'HTML') return true;

        return false;
    }

    private serializeNode(el: Element, inIframe: boolean): AccessibilityNode {
        let id = el.getAttribute('data-agent-id');
        if (!id || !this.elementMap.has(id)) {
            id = (this.nextId++).toString();
            el.setAttribute('data-agent-id', id);
            this.elementMap.set(id, el);
        }

        const rect = el.getBoundingClientRect();
        const attributes: { [key: string]: string } = {};
        for (const attr of Array.from(el.attributes)) {
            attributes[attr.name] = attr.value;
        }

        return {
            id,
            tag: el.tagName,
            text: (el as HTMLElement).innerText?.trim() || el.getAttribute('aria-label') || el.getAttribute('placeholder') || '',
            role: el.getAttribute('role') || '',
            bounds: {
                left: rect.left,
                top: rect.top,
                width: rect.width,
                height: rect.height
            },
            attributes,
            occluded: this.isOccluded(el, rect),
            inIframe
        };
    }

    private pruneElementMap() {
        for (const [id, el] of this.elementMap.entries()) {
            if (!document.body.contains(el)) {
                this.elementMap.delete(id);
            }
        }
    }

    private isOccluded(el: Element, rect: DOMRect): boolean {
        if (rect.width === 0 || rect.height === 0) return false;

        // Calculate small corner offsets
        const insetX = Math.min(rect.width * 0.1, 5);
        const insetY = Math.min(rect.height * 0.1, 5);

        // 5-point layout layout layout layout
        const points = [
            { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }, // Center
            { x: rect.left + insetX, y: rect.top + insetY },                // Top-Left
            { x: rect.right - insetX, y: rect.top + insetY },               // Top-Right
            { x: rect.left + insetX, y: rect.bottom - insetY },             // Bottom-Left
            { x: rect.right - insetX, y: rect.bottom - insetY }             // Bottom-Right
        ];

        let hitCount = 0;
        let testedPoints = 0;

        for (const pt of points) {
            // Ignore points outside the viewport
            if (pt.x < 0 || pt.y < 0 || pt.x > window.innerWidth || pt.y > window.innerHeight) {
                continue;
            }

            testedPoints++;
            const hitElement = document.elementFromPoint(pt.x, pt.y);
            if (!hitElement || el.contains(hitElement) || hitElement.contains(el)) {
                hitCount++;
            }
        }

        // If we tested points and none hit our target or its nested contents, it's occluded.
        return testedPoints > 0 && hitCount === 0;
    }
}
