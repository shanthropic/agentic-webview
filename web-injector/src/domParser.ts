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

        // Don't clear elementMap here to maintain stability.
        // We will prune it later if needed, or just let it grow as long as elements exist in DOM.
        traverse(document.body);
        this.pruneElementMap();
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

        const centerX = rect.left + rect.width / 2;
        const centerY = rect.top + rect.height / 2;

        const hitElement = document.elementFromPoint(centerX, centerY);
        if (!hitElement) return false;

        return !el.contains(hitElement) && !hitElement.contains(el);
    }
}
