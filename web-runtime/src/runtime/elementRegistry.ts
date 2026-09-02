export class ElementRegistry {
    private elementToId = new WeakMap<Element, string>();
    private idToElement = new Map<string, Element>();
    private nextIdByFrame = new Map<string, number>();

    constructor(private readonly frameId: string = 'main') {
        if (!frameId.trim()) throw new Error('frameId must not be blank');
    }

    public getOrCreate(element: Element, frameId: string = this.frameId): string {
        const existing = this.elementToId.get(element);
        if (existing) return existing;

        const sequence = this.nextIdByFrame.get(frameId) ?? 1;
        const id = `${frameId}:e${sequence}`;
        this.nextIdByFrame.set(frameId, sequence + 1);
        this.elementToId.set(element, id);
        this.idToElement.set(id, element);
        return id;
    }

    public resolve(id: string): Element | undefined {
        const element = this.idToElement.get(id);
        if (!element) return undefined;
        if (!element.isConnected) {
            this.idToElement.delete(id);
            return undefined;
        }
        return element;
    }

    public prune(): number {
        let removed = 0;
        for (const [id, element] of this.idToElement) {
            if (!element.isConnected) {
                this.idToElement.delete(id);
                removed++;
            }
        }
        return removed;
    }

    public reset(): void {
        this.elementToId = new WeakMap<Element, string>();
        this.idToElement.clear();
        this.nextIdByFrame.clear();
    }

    public get size(): number {
        return this.idToElement.size;
    }
}
