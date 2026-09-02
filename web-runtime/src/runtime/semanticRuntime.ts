import { ElementRegistry } from './elementRegistry';
import { DocumentRevisionTracker } from './revisionTracker';
import { SemanticObserver, SemanticObservationOptions } from './semanticObserver';
import { SemanticActionExecutor } from './actionExecutor';
import { FrameRegistry } from './frameRegistry';
import {
    ExperimentalPagePatchConfiguration,
    installExperimentalPagePatches,
} from './experimentalPagePatches';

export class SemanticRuntime {
    private readonly registry = new ElementRegistry('main');
    private readonly frames = new FrameRegistry();
    private readonly revisions = new DocumentRevisionTracker();
    private readonly observer = new SemanticObserver(this.registry, this.revisions, this.frames);
    private readonly actions = new SemanticActionExecutor(
        this.registry,
        this.revisions,
        () => this.activeDocumentId,
        (rootDocumentId, frameId, documentId) =>
            this.frames.validatesReference(rootDocumentId, frameId, documentId),
    );
    private activeDocumentId: string | null = null;

    constructor() {
        this.revisions.start(document);
    }

    public capture(documentId: string, options: Partial<SemanticObservationOptions> = {}) {
        this.activate(documentId);
        return this.observer.capture(documentId, options);
    }

    public configure(payload: Record<string, unknown>): void {
        const experimental = payload.experimental;
        if (experimental === undefined) return;
        if (typeof experimental !== 'object' || experimental === null || Array.isArray(experimental)) {
            throw new Error('experimental configuration must be an object');
        }
        installExperimentalPagePatches(experimental as ExperimentalPagePatchConfiguration);
    }

    public capabilities(): Record<string, boolean> {
        return {
            semanticObservation: true,
            stableElementReferences: true,
            openShadowDom: true,
            sameOriginFrames: true,
            nativePointerActions: true,
            screenshots: true,
        };
    }

    public activate(documentId: string): void {
        if (!documentId.trim()) throw new Error('documentId must not be blank');
        if (this.activeDocumentId === documentId) return;
        this.activeDocumentId = documentId;
        this.actions.reset();
        this.registry.reset();
        this.frames.reset();
        this.revisions.reset();
    }

    public resolve(documentId: string, elementId: string): Element | undefined {
        if (documentId !== this.activeDocumentId) return undefined;
        return this.registry.resolve(elementId);
    }

    public execute(documentId: string, command: Record<string, unknown>, options: Record<string, unknown> = {}) {
        return this.actions.execute(documentId, command, options);
    }

    public prepareNativeClick(documentId: string, target: unknown, options: Record<string, unknown> = {}) {
        return this.actions.prepareNativeClick(documentId, target, frameId => {
            const frame = this.frames.frame(frameId);
            return frame ? { left: frame.offsetLeftCssPx, top: frame.offsetTopCssPx } : null;
        }, options);
    }

    public verifyNativeClick(documentId: string, token: string) {
        return this.actions.verifyNativeClick(documentId, token);
    }

    public get revision(): number {
        return this.revisions.current;
    }

}
