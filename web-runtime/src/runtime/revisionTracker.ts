export class DocumentRevisionTracker {
    private revision = 0;
    private observers: MutationObserver[] = [];
    private listeners = new Set<(revision: number) => void>();

    public start(root: Node = document): void {
        this.stop();
        this.observe(root);
    }

    public setRoots(roots: Node[]): void {
        this.stop();
        roots.forEach(root => this.observe(root));
    }

    private observe(root: Node): void {
        const observer = new MutationObserver(() => this.bump());
        observer.observe(root, {
            subtree: true,
            childList: true,
            characterData: true,
            attributes: true,
            attributeFilter: [
                'aria-label', 'aria-labelledby', 'aria-describedby', 'aria-hidden',
                'aria-disabled', 'aria-expanded', 'aria-checked', 'aria-selected',
                'checked', 'class', 'disabled', 'hidden', 'href', 'placeholder',
                'readonly', 'role', 'selected', 'style', 'title', 'type', 'value',
            ],
        });
        this.observers.push(observer);
    }

    public stop(): void {
        this.observers.forEach(observer => observer.disconnect());
        this.observers = [];
    }

    public reset(): void {
        this.revision = 0;
    }

    public bump(): number {
        this.revision++;
        for (const listener of this.listeners) listener(this.revision);
        return this.revision;
    }

    public subscribe(listener: (revision: number) => void): () => void {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    public get current(): number {
        return this.revision;
    }
}
