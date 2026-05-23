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
        if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) {
            const nativeSetter = Object.getOwnPropertyDescriptor(
                el instanceof HTMLInputElement ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype,
                'value'
            )?.set;

            if (nativeSetter) {
                nativeSetter.call(el, text);
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return true;
            }
        }

        // Fallback for other contenteditable elements
        if (el.isContentEditable) {
            el.innerText = text;
            el.dispatchEvent(new Event('input', { bubbles: true }));
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
            attributes: true
        });

        return observer;
    }
}
