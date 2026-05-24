export class InteractionHandler {
    public getPhysicalCenter(el: Element): { x: number; y: number } {
        const rect = el.getBoundingClientRect();
        const dpr = window.devicePixelRatio;
        const zoom = window.visualViewport?.scale ?? 1;
        return {
            x: (rect.left + rect.width / 2) * dpr * zoom,
            y: (rect.top + rect.height / 2) * dpr * zoom,
        };
    }

    public async scrollIntoViewAndWait(el: Element): Promise<void> {
        return new Promise(resolve => {
            el.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' });

            const observer = new IntersectionObserver(entries => {
                if (entries[0].isIntersecting) {
                    observer.disconnect();
                    // Wait for layout stabilization
                    requestAnimationFrame(() => requestAnimationFrame(resolve));
                }
            }, { threshold: 0.5 });

            observer.observe(el);

            // Safety timeout
            setTimeout(() => {
                observer.disconnect();
                resolve();
            }, 2000);
        });
    }

    public setInputValue(el: HTMLElement, text: string): boolean {
        const dispatchFrameworkEvents = (target: HTMLElement, val: string) => {
            // 1. Dispatch beforeinput (InputEvent)
            const beforeInputEvent = new InputEvent('beforeinput', {
                bubbles: true,
                cancelable: true,
                inputType: 'insertText',
                data: val
            });
            target.dispatchEvent(beforeInputEvent);

            // 2. Dispatch input (InputEvent)
            const inputEvent = new InputEvent('input', {
                bubbles: true,
                cancelable: true,
                inputType: 'insertText',
                data: val
            });
            target.dispatchEvent(inputEvent);

            // 3. Dispatch change (Generic Event)
            target.dispatchEvent(new Event('change', { bubbles: true }));
        };

        if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) {
            const nativeSetter = Object.getOwnPropertyDescriptor(
                el instanceof HTMLInputElement ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype,
                'value'
            )?.set;

            if (nativeSetter) {
                nativeSetter.call(el, text);
                dispatchFrameworkEvents(el, text);
                return true;
            }
        }

        // Fallback for contenteditable elements
        if (el.isContentEditable) {
            el.innerText = text;
            dispatchFrameworkEvents(el, text);
            return true;
        }

        return false;
    }

    public setSelectValue(el: HTMLSelectElement, value: string): boolean {
        el.value = value;
        el.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
    }

    public setupMutationObserver(callback: () => void, throttleMs: number = 300): MutationObserver {
        let timeout: any = null;
        const observer = new MutationObserver(() => {
            if (timeout) return;
            timeout = setTimeout(() => {
                callback();
                timeout = null;
            }, throttleMs);
        });

        observer.observe(document.body, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: [
                'class', 'style', 'hidden', 'disabled', 
                'aria-hidden', 'aria-disabled', 'readonly', 
                'checked', 'selected', 'src', 'href'
            ]
        });

        return observer;
    }
}
