export type RuntimeFrameCapability =
    | 'OBSERVABLE_AND_ACTIONABLE'
    | 'INACCESSIBLE_CROSS_ORIGIN'
    | 'SANDBOX_RESTRICTED';

export interface RuntimeFrame {
    id: string;
    parentId: string | null;
    documentId: string;
    document: Document | null;
    window: Window | null;
    hostElement: HTMLIFrameElement | null;
    depth: number;
    offsetLeftCssPx: number;
    offsetTopCssPx: number;
    url: string | null;
    origin: string | null;
    capability: RuntimeFrameCapability;
}

export class FrameRegistry {
    private elementIds = new WeakMap<HTMLIFrameElement, string>();
    private documentIds = new WeakMap<Document, string>();
    private nextId = 1;
    private nextDocumentGeneration = 1;
    private rootDocumentId = '';
    private latestFrames = new Map<string, RuntimeFrame>();
    private depthLimitReached = false;
    private scanLimitReached = false;
    private remainingScanBudget = 0;

    public snapshot(
        rootDocumentId: string,
        maximumDepth: number,
        maximumScannedElements: number = 10_000,
    ): RuntimeFrame[] {
        if (!Number.isInteger(maximumDepth) || maximumDepth < 0) {
            throw new Error('maximumFrameDepth must be a non-negative integer');
        }
        if (!Number.isInteger(maximumScannedElements) || maximumScannedElements < 1) {
            throw new Error('maximumScannedElements must be a positive integer');
        }
        const frames: RuntimeFrame[] = [];
        this.rootDocumentId = rootDocumentId;
        this.depthLimitReached = false;
        this.scanLimitReached = false;
        this.remainingScanBudget = maximumScannedElements;
        const mainWindow = document.defaultView ?? window;
        const main: RuntimeFrame = {
            id: 'main',
            parentId: null,
            documentId: rootDocumentId,
            document,
            window: mainWindow,
            hostElement: null,
            depth: 0,
            offsetLeftCssPx: 0,
            offsetTopCssPx: 0,
            url: safeUrl(mainWindow),
            origin: safeOrigin(mainWindow),
            capability: 'OBSERVABLE_AND_ACTIONABLE',
        };
        frames.push(main);
        this.discoverChildren(main, maximumDepth, frames);
        this.latestFrames = new Map(frames.map(frame => [frame.id, frame]));
        return frames;
    }

    public validatesReference(rootDocumentId: string, frameId: string, documentId: string): boolean {
        const frame = this.latestFrames.get(frameId);
        return frame?.capability === 'OBSERVABLE_AND_ACTIONABLE' &&
            frame.documentId === documentId &&
            (frameId !== 'main' || documentId === rootDocumentId);
    }

    public frame(frameId: string): RuntimeFrame | undefined {
        return this.latestFrames.get(frameId);
    }

    public didReachDepthLimit(): boolean {
        return this.depthLimitReached;
    }

    public didReachScanLimit(): boolean {
        return this.scanLimitReached;
    }

    public reset(): void {
        this.elementIds = new WeakMap<HTMLIFrameElement, string>();
        this.documentIds = new WeakMap<Document, string>();
        this.nextId = 1;
        this.nextDocumentGeneration = 1;
        this.rootDocumentId = '';
        this.scanLimitReached = false;
        this.remainingScanBudget = 0;
        this.latestFrames.clear();
    }

    private discoverChildren(parent: RuntimeFrame, maximumDepth: number, output: RuntimeFrame[]): void {
        if (!parent.document) return;
        if (parent.depth >= maximumDepth) {
            if (this.discoverIframes(parent.document).length > 0) this.depthLimitReached = true;
            return;
        }
        for (const iframe of this.discoverIframes(parent.document)) {
            const id = this.idFor(iframe);
            const frame = this.describeFrame(iframe, id, parent);
            output.push(frame);
            if (frame.capability === 'OBSERVABLE_AND_ACTIONABLE') {
                this.discoverChildren(frame, maximumDepth, output);
            }
        }
    }

    private describeFrame(iframe: HTMLIFrameElement, id: string, parent: RuntimeFrame): RuntimeFrame {
        const sandboxTokens = (iframe.getAttribute('sandbox') || '')
            .split(/\s+/)
            .filter(Boolean);
        const sandboxRestricted = iframe.hasAttribute('sandbox') &&
            !sandboxTokens.includes('allow-same-origin');
        const rect = iframe.getBoundingClientRect();
        const base = {
            id,
            parentId: parent.id,
            hostElement: iframe,
            depth: parent.depth + 1,
            offsetLeftCssPx: parent.offsetLeftCssPx + finite(rect.left) + iframe.clientLeft,
            offsetTopCssPx: parent.offsetTopCssPx + finite(rect.top) + iframe.clientTop,
        };
        if (sandboxRestricted) {
            return {
                ...base,
                documentId: this.unavailableDocumentId(id),
                document: null,
                window: null,
                url: safeAttributeUrl(iframe),
                origin: null,
                capability: 'SANDBOX_RESTRICTED',
            };
        }
        try {
            const childDocument = iframe.contentDocument;
            const childWindow = iframe.contentWindow;
            if (!childDocument || !childWindow) throw new Error('Frame document is unavailable');
            void childDocument.documentElement;
            return {
                ...base,
                documentId: this.idForDocument(childDocument, id),
                document: childDocument,
                window: childWindow,
                url: safeUrl(childWindow),
                origin: safeOrigin(childWindow),
                capability: 'OBSERVABLE_AND_ACTIONABLE',
            };
        } catch {
            return {
                ...base,
                documentId: this.unavailableDocumentId(id),
                document: null,
                window: null,
                url: safeAttributeUrl(iframe),
                origin: null,
                capability: 'INACCESSIBLE_CROSS_ORIGIN',
            };
        }
    }

    private idFor(iframe: HTMLIFrameElement): string {
        const existing = this.elementIds.get(iframe);
        if (existing) return existing;
        const id = `main:f${this.nextId++}`;
        this.elementIds.set(iframe, id);
        return id;
    }

    private idForDocument(target: Document, frameId: string): string {
        const existing = this.documentIds.get(target);
        if (existing) return existing;
        const id = `${this.rootDocumentId}#${frameId}:d${this.nextDocumentGeneration++}`;
        this.documentIds.set(target, id);
        return id;
    }

    private unavailableDocumentId(frameId: string): string {
        return `${this.rootDocumentId}#${frameId}:unavailable`;
    }

    private discoverIframes(root: Document | ShadowRoot): HTMLIFrameElement[] {
        const frames: HTMLIFrameElement[] = [];
        const initial = root instanceof Document
            ? (root.documentElement ? [root.documentElement] : [])
            : Array.from(root.children);
        const stack = [...initial].reverse();
        while (stack.length > 0) {
            if (this.remainingScanBudget <= 0) {
                this.scanLimitReached = true;
                break;
            }
            this.remainingScanBudget--;
            const element = stack.pop()!;
            if (element.tagName.toLowerCase() === 'iframe') frames.push(element as HTMLIFrameElement);
            const descendants = [
                ...Array.from(element.children),
                ...(element.shadowRoot ? Array.from(element.shadowRoot.children) : []),
            ];
            for (let index = descendants.length - 1; index >= 0; index--) stack.push(descendants[index]);
        }
        return frames;
    }
}

function safeUrl(target: Window): string | null {
    try { return target.location.href || null; } catch { return null; }
}

function safeOrigin(target: Window): string | null {
    try { return target.location.origin || null; } catch { return null; }
}

function safeAttributeUrl(iframe: HTMLIFrameElement): string | null {
    const value = iframe.getAttribute('src');
    return value && value.trim() ? value : null;
}

function finite(value: number): number {
    return Number.isFinite(value) ? value : 0;
}
