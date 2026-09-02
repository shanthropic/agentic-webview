import { RuntimeHandlerError } from '../protocol/dispatcher';
import { ElementRegistry } from './elementRegistry';
import { DocumentRevisionTracker } from './revisionTracker';

interface ElementReferencePayload {
    documentId: string;
    frameId: string;
    elementId: string;
    observedAtRevision: number;
}

interface CommandReceipt {
    commandType: string;
    strategy: string;
    documentId: string;
    revisionBefore: number;
    revisionAfter: number;
    dispatched: boolean;
    verified: boolean;
    pageChanged: boolean;
}

interface ActionExecutionOptions {
    geometryStableCycles: number;
    geometryTolerancePx: number;
}

const DEFAULT_ACTION_OPTIONS: ActionExecutionOptions = {
    geometryStableCycles: 2,
    geometryTolerancePx: 1,
};

export class SemanticActionExecutor {
    private nextNativePointerToken = 1;
    private readonly nativePointerPreparations = new Map<string, {
        element: Element;
        listener: EventListener;
        observed: boolean;
        before: VerificationSnapshot;
        timeoutId: number;
    }>();
    constructor(
        private readonly registry: ElementRegistry,
        private readonly revisions: DocumentRevisionTracker,
        private readonly activeDocumentId: () => string | null,
        private readonly validatesReference: (
            rootDocumentId: string,
            frameId: string,
            documentId: string,
        ) => boolean = (rootDocumentId, frameId, documentId) =>
            frameId === 'main' && documentId === rootDocumentId,
    ) {}

    public async execute(
        documentId: string,
        command: Record<string, unknown>,
        requestedOptions: Partial<ActionExecutionOptions> = {},
    ): Promise<CommandReceipt> {
        if (documentId !== this.activeDocumentId()) {
            throw new RuntimeHandlerError('STALE_DOCUMENT', 'Command belongs to an inactive document');
        }

        const type = requireString(command.type, 'command.type');
        const options = actionOptions(requestedOptions);
        const revisionBefore = this.revisions.current;
        const urlBefore = location.href;
        let dispatched = false;
        let verified = false;
        let strategy = 'DOM_POINTER';

        switch (type) {
            case 'click': {
                const element = this.resolveTarget(documentId, command.target);
                requireEnabled(element);
                const before = verificationSnapshot(element, this.revisions);
                scrollElementIntoView(element, 'center');
                await waitForStableGeometry(element, options);
                requireNotOccluded(element);
                const button = pointerButton(command.button);
                let clickObserved = false;
                const verificationEvent = button === 2 ? 'contextmenu' : 'click';
                element.addEventListener(verificationEvent, () => { clickObserved = true; }, { once: true, capture: true });
                dispatchPointerSequence(element, button, false);
                dispatched = true;
                await settleMutationDelivery();
                verified = clickObserved && hasObservableEffect(before, element, this.revisions);
                break;
            }
            case 'long_press': {
                const element = this.resolveTarget(documentId, command.target);
                requireEnabled(element);
                const before = verificationSnapshot(element, this.revisions);
                scrollElementIntoView(element, 'center');
                await waitForStableGeometry(element, options);
                requireNotOccluded(element);
                const durationMs = requireFiniteNumber(command.durationMs, 'command.durationMs');
                if (!Number.isInteger(durationMs) || durationMs < 1 || durationMs > 60_000) {
                    throw new RuntimeHandlerError('INVALID_ACTION', 'command.durationMs must be an integer within 1..60000');
                }
                let downObserved = false;
                let upObserved = false;
                element.addEventListener('pointerdown', () => { downObserved = true; }, { once: true, capture: true });
                element.addEventListener('pointerup', () => { upObserved = true; }, { once: true, capture: true });
                dispatchPointerDown(element, 0);
                dispatched = true;
                await delay(durationMs);
                dispatchPointerUp(element, 0);
                await settleMutationDelivery();
                verified = downObserved && upObserved && hasObservableEffect(before, element, this.revisions);
                break;
            }
            case 'type_text': {
                strategy = 'DOM_NATIVE_SETTER';
                const element = this.resolveTarget(documentId, command.target);
                requireEnabled(element);
                const text = requireString(command.text, 'command.text', true);
                const mode = requireString(command.mode, 'command.mode');
                const expected = setText(element, text, mode);
                dispatched = true;
                await settleMutationDelivery();
                verified = currentTextValue(element) === expected;
                break;
            }
            case 'select_option': {
                strategy = 'DOM_SELECT';
                const element = this.resolveTarget(documentId, command.target);
                if (element.tagName.toLowerCase() !== 'select') {
                    throw new RuntimeHandlerError('ELEMENT_NOT_ACTIONABLE', 'Target is not a select element');
                }
                const select = element as HTMLSelectElement;
                requireEnabled(element);
                const option = requireRecord(command.option, 'command.option');
                const selected = selectOption(select, option);
                const view = element.ownerDocument.defaultView ?? window;
                select.value = selected.value;
                select.dispatchEvent(new view.Event('input', { bubbles: true }));
                select.dispatchEvent(new view.Event('change', { bubbles: true }));
                dispatched = true;
                await settleMutationDelivery();
                verified = select.value === selected.value;
                break;
            }
            case 'scroll': {
                strategy = 'DOM_SCROLL';
                const delta = requireRecord(command.delta, 'command.delta');
                const x = requireFiniteNumber(delta.xCssPx, 'command.delta.xCssPx');
                const y = requireFiniteNumber(delta.yCssPx, 'command.delta.yCssPx');
                const target = requireRecord(command.target, 'command.target');
                const targetType = requireString(target.type, 'command.target.type');
                const scrollContainer = targetType === 'element'
                    ? nearestScrollable(this.resolveTarget(documentId, target.target))
                    : document.scrollingElement || document.documentElement;
                const beforeX = scrollContainer.scrollLeft;
                const beforeY = scrollContainer.scrollTop;
                scrollContainer.scrollBy({ left: x, top: y, behavior: 'auto' });
                dispatched = true;
                await settleMutationDelivery();
                verified = scrollContainer.scrollLeft !== beforeX || scrollContainer.scrollTop !== beforeY || (x === 0 && y === 0);
                break;
            }
            case 'scroll_into_view': {
                strategy = 'DOM_SCROLL';
                const element = this.resolveTarget(documentId, command.target);
                const alignment = requireString(command.alignment, 'command.alignment').toLowerCase();
                const block = alignment === 'start' || alignment === 'end' || alignment === 'center'
                    ? alignment
                    : 'nearest';
                scrollElementIntoView(element, block);
                dispatched = true;
                await settleMutationDelivery();
                verified = intersectsViewport(
                    element.getBoundingClientRect(),
                    element.ownerDocument.defaultView ?? window,
                );
                break;
            }
            case 'press_keys': {
                strategy = 'DOM_KEYBOARD';
                const chord = requireRecord(command.chord, 'command.chord');
                const key = requireString(chord.key, 'command.chord.key');
                const target = deepActiveElement(document) ?? document.body;
                if (!target) throw new RuntimeHandlerError('ELEMENT_NOT_ACTIONABLE', 'No active keyboard target is available');
                const before = verificationSnapshot(target, this.revisions);
                let keyDownObserved = false;
                let keyUpObserved = false;
                target.addEventListener('keydown', () => { keyDownObserved = true; }, { once: true, capture: true });
                target.addEventListener('keyup', () => { keyUpObserved = true; }, { once: true, capture: true });
                dispatchKey(target, 'keydown', key, chord);
                dispatchKey(target, 'keyup', key, chord);
                dispatched = true;
                await settleMutationDelivery();
                verified = keyDownObserved && keyUpObserved && hasObservableEffect(before, target, this.revisions);
                break;
            }
            default:
                throw new RuntimeHandlerError('UNSUPPORTED_ACTION', `Unsupported command type: ${type}`, { type });
        }

        return {
            commandType: type,
            strategy,
            documentId,
            revisionBefore,
            revisionAfter: this.revisions.current,
            dispatched,
            verified,
            pageChanged: location.href !== urlBefore,
        };
    }

    public async prepareNativeClick(
        documentId: string,
        candidate: unknown,
        frameOffset: (frameId: string) => { left: number; top: number } | null,
        requestedOptions: Partial<ActionExecutionOptions> = {},
    ): Promise<Record<string, unknown>> {
        if (documentId !== this.activeDocumentId()) {
            throw new RuntimeHandlerError('STALE_DOCUMENT', 'Command belongs to an inactive document');
        }
        const target = parseElementReference(candidate);
        const element = this.resolveTarget(documentId, candidate);
        requireEnabled(element);
        scrollElementIntoView(element, 'center');
        await waitForStableGeometry(element, actionOptions(requestedOptions));
        requireNotOccluded(element);
        const rect = element.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0) {
            throw new RuntimeHandlerError('ELEMENT_NOT_ACTIONABLE', 'Element has no actionable geometry');
        }
        const offset = frameOffset(target.frameId);
        if (!offset) throw new RuntimeHandlerError('UNSUPPORTED_FRAME', 'Frame geometry is unavailable');
        const token = `native-click-${this.nextNativePointerToken++}`;
        const preparation = {
            element,
            listener: (() => undefined) as EventListener,
            observed: false,
            before: verificationSnapshot(element, this.revisions),
            timeoutId: 0,
        };
        preparation.listener = () => { preparation.observed = true; };
        element.addEventListener('click', preparation.listener, { once: true, capture: true });
        this.nativePointerPreparations.set(token, preparation);
        preparation.timeoutId = window.setTimeout(() => {
            const expired = this.nativePointerPreparations.get(token);
            if (!expired) return;
            expired.element.removeEventListener('click', expired.listener, true);
            this.nativePointerPreparations.delete(token);
        }, 10_000);
        return {
            token,
            xCssPx: offset.left + rect.left + rect.width / 2,
            yCssPx: offset.top + rect.top + rect.height / 2,
            viewportWidthCssPx: window.innerWidth,
            viewportHeightCssPx: window.innerHeight,
            revisionBefore: preparation.before.revision,
        };
    }

    public verifyNativeClick(documentId: string, token: string): CommandReceipt {
        if (documentId !== this.activeDocumentId()) {
            throw new RuntimeHandlerError('STALE_DOCUMENT', 'Command belongs to an inactive document');
        }
        const preparation = this.nativePointerPreparations.get(token);
        if (!preparation) throw new RuntimeHandlerError('INVALID_ACTION', 'Native pointer token is unknown or expired');
        this.nativePointerPreparations.delete(token);
        window.clearTimeout(preparation.timeoutId);
        preparation.element.removeEventListener('click', preparation.listener, true);
        return {
            commandType: 'click',
            strategy: 'ANDROID_NATIVE_POINTER',
            documentId,
            revisionBefore: preparation.before.revision,
            revisionAfter: this.revisions.current,
            dispatched: true,
            verified: preparation.observed && hasObservableEffect(preparation.before, preparation.element, this.revisions),
            pageChanged: location.href !== preparation.before.url,
        };
    }

    public reset(): void {
        for (const preparation of this.nativePointerPreparations.values()) {
            window.clearTimeout(preparation.timeoutId);
            preparation.element.removeEventListener('click', preparation.listener, true);
        }
        this.nativePointerPreparations.clear();
    }

    private resolveTarget(documentId: string, candidate: unknown): Element {
        const target = parseElementReference(candidate);
        if (!this.validatesReference(documentId, target.frameId, target.documentId)) {
            throw new RuntimeHandlerError('STALE_ELEMENT', 'Element reference belongs to another document or frame');
        }
        const element = this.registry.resolve(target.elementId);
        if (!element) {
            throw new RuntimeHandlerError('STALE_ELEMENT', 'Element is detached or no longer registered', {
                elementId: target.elementId,
            });
        }
        if (target.observedAtRevision > this.revisions.current) {
            throw new RuntimeHandlerError('STALE_ELEMENT', 'Element reference has an invalid future revision', {
                observedAtRevision: target.observedAtRevision,
                currentRevision: this.revisions.current,
            });
        }
        return element;
    }
}

interface VerificationSnapshot {
    revision: number;
    url: string;
    activeElement: Element | null;
    connected: boolean;
    checked: boolean | undefined;
    selected: boolean | undefined;
    value: string | undefined;
    expanded: string | null;
    pressed: string | null;
    text: string;
}

function verificationSnapshot(element: Element, revisions: DocumentRevisionTracker): VerificationSnapshot {
    const stateful = element as Element & { checked?: boolean; selected?: boolean; value?: string };
    return {
        revision: revisions.current,
        url: location.href,
        activeElement: deepActiveElement(document),
        connected: element.isConnected,
        checked: typeof stateful.checked === 'boolean' ? stateful.checked : undefined,
        selected: typeof stateful.selected === 'boolean' ? stateful.selected : undefined,
        value: typeof stateful.value === 'string' ? stateful.value : undefined,
        expanded: element.getAttribute('aria-expanded'),
        pressed: element.getAttribute('aria-pressed'),
        text: element.textContent || '',
    };
}

function hasObservableEffect(
    before: VerificationSnapshot,
    element: Element,
    revisions: DocumentRevisionTracker,
): boolean {
    const after = verificationSnapshot(element, revisions);
    return after.revision !== before.revision ||
        after.url !== before.url ||
        after.activeElement !== before.activeElement ||
        after.connected !== before.connected ||
        after.checked !== before.checked ||
        after.selected !== before.selected ||
        after.value !== before.value ||
        after.expanded !== before.expanded ||
        after.pressed !== before.pressed ||
        after.text !== before.text;
}

function parseElementReference(candidate: unknown): ElementReferencePayload {
    const value = requireRecord(candidate, 'target');
    const observedAtRevision = requireFiniteNumber(value.observedAtRevision, 'target.observedAtRevision');
    if (!Number.isInteger(observedAtRevision) || observedAtRevision < 0) {
        throw new RuntimeHandlerError('INVALID_ACTION', 'target.observedAtRevision must be a non-negative integer');
    }
    return {
        documentId: requireString(value.documentId, 'target.documentId'),
        frameId: requireString(value.frameId, 'target.frameId'),
        elementId: requireString(value.elementId, 'target.elementId'),
        observedAtRevision,
    };
}

function requireEnabled(element: Element): void {
    const control = element as HTMLElement & { disabled?: boolean; readOnly?: boolean };
    if (control.disabled || element.getAttribute('aria-disabled') === 'true') {
        throw new RuntimeHandlerError('ELEMENT_NOT_ACTIONABLE', 'Element is disabled');
    }
    if (control.readOnly || element.hasAttribute('readonly')) {
        throw new RuntimeHandlerError('ELEMENT_NOT_ACTIONABLE', 'Element is read-only');
    }
}

function setText(element: Element, text: string, mode: string): string {
    const current = currentTextValue(element);
    const next = mode === 'CLEAR' ? ''
        : mode === 'APPEND' ? current + text
        : mode === 'INSERT_AT_SELECTION' ? insertAtSelection(element, text)
        : mode === 'REPLACE_ALL' ? text
        : (() => { throw new RuntimeHandlerError('INVALID_ACTION', `Unsupported text input mode: ${mode}`); })();

    const tagName = element.tagName.toLowerCase();
    if (tagName === 'input' || tagName === 'textarea') {
        const view = element.ownerDocument.defaultView ?? window;
        const prototype = tagName === 'input' ? view.HTMLInputElement.prototype : view.HTMLTextAreaElement.prototype;
        const setter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
        if (!setter) throw new RuntimeHandlerError('ELEMENT_NOT_ACTIONABLE', 'Native value setter is unavailable');
        setter.call(element, next);
        element.dispatchEvent(new view.InputEvent('input', { bubbles: true, inputType: 'insertText', data: text }));
        element.dispatchEvent(new view.Event('change', { bubbles: true }));
        return next;
    }
    const editable = element as HTMLElement;
    if (editable.isContentEditable) {
        editable.textContent = next;
        const view = element.ownerDocument.defaultView ?? window;
        editable.dispatchEvent(new view.InputEvent('input', { bubbles: true, inputType: 'insertText', data: text }));
        return next;
    }
    throw new RuntimeHandlerError('ELEMENT_NOT_ACTIONABLE', 'Target does not accept text input');
}

function currentTextValue(element: Element): string {
    const tagName = element.tagName.toLowerCase();
    if (tagName === 'input' || tagName === 'textarea') return (element as HTMLInputElement).value;
    if ((element as HTMLElement).isContentEditable) return element.textContent || '';
    return '';
}

function requireNotOccluded(element: Element): void {
    const ownerDocument = element.ownerDocument;
    const view = ownerDocument.defaultView ?? window;
    if (typeof ownerDocument.elementFromPoint !== 'function') return;
    const rect = element.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) {
        throw new RuntimeHandlerError('ELEMENT_NOT_ACTIONABLE', 'Element has no actionable geometry');
    }
    const x = Math.min(Math.max(rect.left + rect.width / 2, 0), Math.max(0, view.innerWidth - 1));
    const y = Math.min(Math.max(rect.top + rect.height / 2, 0), Math.max(0, view.innerHeight - 1));
    const top = ownerDocument.elementFromPoint(x, y);
    if (top && top !== element && !element.contains(top) && !top.contains(element)) {
        throw new RuntimeHandlerError('ELEMENT_OCCLUDED', 'Another element covers the target center point');
    }
}

function insertAtSelection(element: Element, text: string): string {
    const tagName = element.tagName.toLowerCase();
    if (tagName === 'input' || tagName === 'textarea') {
        const input = element as HTMLInputElement;
        const value = input.value;
        const start = input.selectionStart ?? value.length;
        const end = input.selectionEnd ?? start;
        return value.slice(0, start) + text + value.slice(end);
    }
    return currentTextValue(element) + text;
}

function selectOption(select: HTMLSelectElement, matcher: Record<string, unknown>): HTMLOptionElement {
    const type = requireString(matcher.type, 'command.option.type');
    let found: HTMLOptionElement | undefined;
    if (type === 'value') {
        const value = requireString(matcher.value, 'command.option.value', true);
        found = Array.from(select.options).find(option => option.value === value);
    } else if (type === 'label') {
        const label = requireString(matcher.label, 'command.option.label', true);
        found = Array.from(select.options).find(option => (option.label || option.textContent || '').trim() === label);
    } else if (type === 'index') {
        const index = requireFiniteNumber(matcher.index, 'command.option.index');
        if (!Number.isInteger(index) || index < 0) throw new RuntimeHandlerError('INVALID_ACTION', 'Option index must be non-negative');
        found = select.options.item(index) || undefined;
    } else {
        throw new RuntimeHandlerError('INVALID_ACTION', `Unsupported option matcher: ${type}`);
    }
    if (!found) throw new RuntimeHandlerError('OPTION_NOT_FOUND', 'No matching select option was found');
    if (found.disabled) throw new RuntimeHandlerError('ELEMENT_NOT_ACTIONABLE', 'Matching select option is disabled');
    return found;
}

function nearestScrollable(element: Element): Element {
    let current: Element | null = element;
    const ownerDocument = element.ownerDocument;
    const view = ownerDocument.defaultView ?? window;
    while (current && current !== ownerDocument.documentElement) {
        const style = view.getComputedStyle(current);
        if ((style.overflowY === 'auto' || style.overflowY === 'scroll') && current.scrollHeight > current.clientHeight) {
            return current;
        }
        current = current.parentElement;
    }
    return ownerDocument.scrollingElement || ownerDocument.documentElement;
}

function scrollElementIntoView(element: Element, block: ScrollLogicalPosition): void {
    const scrollable = element as Element & { scrollIntoView?: (options: ScrollIntoViewOptions) => void };
    scrollable.scrollIntoView?.({ block, inline: 'nearest', behavior: 'auto' });
}

function intersectsViewport(rect: DOMRect, view: Window): boolean {
    return rect.bottom >= 0 && rect.right >= 0 && rect.top <= view.innerHeight && rect.left <= view.innerWidth;
}

function pointerButton(value: unknown): number {
    const normalized = value === undefined ? 'PRIMARY' : requireString(value, 'command.button');
    if (normalized === 'PRIMARY') return 0;
    if (normalized === 'MIDDLE') return 1;
    if (normalized === 'SECONDARY') return 2;
    throw new RuntimeHandlerError('INVALID_ACTION', `Unsupported pointer button: ${normalized}`);
}

function dispatchPointerSequence(element: Element, button: number, longPress: boolean): void {
    dispatchPointerDown(element, button);
    dispatchPointerUp(element, button);
    const view = element.ownerDocument.defaultView ?? window;
    if (button === 0 && !longPress && typeof (element as HTMLElement).click === 'function') {
        (element as HTMLElement).click();
    } else {
        const type = button === 2 ? 'contextmenu' : 'click';
        element.dispatchEvent(new view.MouseEvent(type, {
            bubbles: true,
            cancelable: true,
            view,
            button,
        }));
    }
}

function dispatchPointerDown(element: Element, button: number): void {
    dispatchPointerEvent(element, 'pointerdown', button);
    dispatchPointerEvent(element, 'mousedown', button);
}

function dispatchPointerUp(element: Element, button: number): void {
    dispatchPointerEvent(element, 'pointerup', button);
    dispatchPointerEvent(element, 'mouseup', button);
}

function dispatchPointerEvent(element: Element, type: string, button: number): void {
    const view = element.ownerDocument.defaultView ?? window;
    const rect = element.getBoundingClientRect();
    const init: MouseEventInit = {
        bubbles: true,
        cancelable: true,
        view,
        button,
        clientX: rect.left + rect.width / 2,
        clientY: rect.top + rect.height / 2,
    };
    const Pointer = view.PointerEvent;
    element.dispatchEvent(Pointer ? new Pointer(type, init) : new view.MouseEvent(type, init));
}

function deepActiveElement(root: Document | ShadowRoot): Element | null {
    const active = root.activeElement;
    if (!active) return null;
    if (active.shadowRoot?.activeElement) return deepActiveElement(active.shadowRoot);
    if (active.tagName.toLowerCase() === 'iframe') {
        try {
            const childDocument = (active as HTMLIFrameElement).contentDocument;
            if (childDocument?.activeElement) return deepActiveElement(childDocument);
        } catch {
            return active;
        }
    }
    return active;
}

function dispatchKey(
    target: Element,
    type: 'keydown' | 'keyup',
    key: string,
    chord: Record<string, unknown>,
): void {
    const view = target.ownerDocument.defaultView ?? window;
    target.dispatchEvent(new view.KeyboardEvent(type, {
        key,
        code: key.length === 1 ? `Key${key.toUpperCase()}` : key,
        ctrlKey: chord.control === true,
        altKey: chord.alt === true,
        shiftKey: chord.shift === true,
        metaKey: chord.meta === true,
        bubbles: true,
        cancelable: true,
    }));
}

function delay(durationMs: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, durationMs));
}

function settleMutationDelivery(): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, 0));
}

async function waitForStableGeometry(element: Element, options: ActionExecutionOptions): Promise<void> {
    let previous = element.getBoundingClientRect();
    let stableCycles = 0;
    for (let attempt = 0; attempt < options.geometryStableCycles * 4; attempt++) {
        await settleMutationDelivery();
        const current = element.getBoundingClientRect();
        if (rectDistance(previous, current) <= options.geometryTolerancePx) {
            stableCycles++;
            if (stableCycles >= options.geometryStableCycles) return;
        } else {
            stableCycles = 0;
        }
        previous = current;
    }
    throw new RuntimeHandlerError('ELEMENT_NOT_ACTIONABLE', 'Element geometry did not stabilize');
}

function rectDistance(left: DOMRect, right: DOMRect): number {
    return Math.max(
        Math.abs(left.left - right.left),
        Math.abs(left.top - right.top),
        Math.abs(left.width - right.width),
        Math.abs(left.height - right.height),
    );
}

function actionOptions(candidate: Partial<ActionExecutionOptions>): ActionExecutionOptions {
    const options = { ...DEFAULT_ACTION_OPTIONS, ...candidate };
    if (!Number.isInteger(options.geometryStableCycles) || options.geometryStableCycles < 1 || options.geometryStableCycles > 20) {
        throw new RuntimeHandlerError('INVALID_ACTION', 'geometryStableCycles must be an integer within 1..20');
    }
    if (!Number.isFinite(options.geometryTolerancePx) || options.geometryTolerancePx < 0 || options.geometryTolerancePx > 100) {
        throw new RuntimeHandlerError('INVALID_ACTION', 'geometryTolerancePx must be within 0..100');
    }
    return options;
}

function requireRecord(value: unknown, field: string): Record<string, unknown> {
    if (typeof value !== 'object' || value === null || Array.isArray(value)) {
        throw new RuntimeHandlerError('INVALID_ACTION', `${field} must be an object`);
    }
    return value as Record<string, unknown>;
}

function requireString(value: unknown, field: string, allowEmpty: boolean = false): string {
    if (typeof value !== 'string' || (!allowEmpty && value.trim().length === 0)) {
        throw new RuntimeHandlerError('INVALID_ACTION', `${field} must be a ${allowEmpty ? '' : 'non-blank '}string`);
    }
    return value;
}

function requireFiniteNumber(value: unknown, field: string): number {
    if (typeof value !== 'number' || !Number.isFinite(value)) {
        throw new RuntimeHandlerError('INVALID_ACTION', `${field} must be a finite number`);
    }
    return value;
}
