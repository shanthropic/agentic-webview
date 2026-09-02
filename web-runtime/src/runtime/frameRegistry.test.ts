import { FrameRegistry } from './frameRegistry';

describe('FrameRegistry', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
    });

    it('keeps frame IDs stable and assigns distinct per-frame document IDs', () => {
        const iframe = document.createElement('iframe');
        document.body.appendChild(iframe);
        iframe.contentDocument!.body.innerHTML = '<button>Inside</button>';
        const registry = new FrameRegistry();

        const first = registry.snapshot('document-1', 8);
        const second = registry.snapshot('document-1', 8);

        expect(first).toHaveLength(2);
        expect(first[1].id).toBe(second[1].id);
        expect(first[1].documentId).toBe(second[1].documentId);
        expect(first[1].documentId).toMatch(new RegExp(`^document-1#${first[1].id}:d\\d+$`));
        expect(first[1].capability).toBe('OBSERVABLE_AND_ACTIONABLE');
    });

    it('reports sandboxed frames without traversing them', () => {
        const iframe = document.createElement('iframe');
        iframe.setAttribute('sandbox', 'allow-scripts');
        document.body.appendChild(iframe);
        const registry = new FrameRegistry();

        const frames = registry.snapshot('document-1', 8);

        expect(frames[1].capability).toBe('SANDBOX_RESTRICTED');
        expect(frames[1].document).toBeNull();
    });

    it('uses collision-free IDs for nested frames', () => {
        const outer = document.createElement('iframe');
        document.body.appendChild(outer);
        const inner = outer.contentDocument!.createElement('iframe');
        outer.contentDocument!.body.appendChild(inner);
        const registry = new FrameRegistry();

        const frames = registry.snapshot('document-1', 8);

        expect(frames.map(frame => frame.id)).toEqual(['main', 'main:f1', 'main:f2']);
        expect(frames[2].parentId).toBe('main:f1');
        expect(frames[2].depth).toBe(2);
    });

    it('reports when nested frames exceed the configured depth', () => {
        const outer = document.createElement('iframe');
        document.body.appendChild(outer);
        outer.contentDocument!.body.appendChild(outer.contentDocument!.createElement('iframe'));
        const registry = new FrameRegistry();

        const frames = registry.snapshot('document-1', 1);

        expect(frames).toHaveLength(2);
        expect(registry.didReachDepthLimit()).toBe(true);
    });

    it('bounds frame discovery work', () => {
        document.body.innerHTML = '<main><div></div><iframe></iframe></main>';
        const registry = new FrameRegistry();

        const frames = registry.snapshot('document-1', 8, 2);

        expect(frames).toHaveLength(1);
        expect(registry.didReachScanLimit()).toBe(true);
    });
});
