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
                    requestAnimationFrame(() => requestAnimationFrame(resolve));
                }
            }, { threshold: 0.5 });

            observer.observe(el);

            setTimeout(() => {
                observer.disconnect();
                resolve();
            }, 2000);
        });
    }

    public setInputValue(el: HTMLElement, text: string): boolean {
        const dispatchFrameworkEvents = (target: HTMLElement, val: string) => {
            const beforeInputEvent = new InputEvent('beforeinput', {
                bubbles: true, cancelable: true, inputType: 'insertText', data: val
            });
            target.dispatchEvent(beforeInputEvent);

            const inputEvent = new InputEvent('input', {
                bubbles: true, cancelable: true, inputType: 'insertText', data: val
            });
            target.dispatchEvent(inputEvent);

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

    public sendKeys(keys: string): boolean {
        const keyParts = keys.split('+').map(k => k.trim());
        const modifiers = keyParts.slice(0, -1);
        const mainKey = keyParts[keyParts.length - 1];

        const modifierMap: Record<string, string> = {
            'ctrl': 'Control', 'control': 'Control',
            'shift': 'Shift',
            'alt': 'Alt',
            'meta': 'Meta', 'cmd': 'Meta', 'command': 'Meta',
        };

        const activeModifiers: string[] = [];
        for (const mod of modifiers) {
            const mapped = modifierMap[mod.toLowerCase()] || mod;
            activeModifiers.push(mapped);
            document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', {
                key: mapped, code: mapped === 'Control' ? 'ControlLeft' : mapped,
                bubbles: true, cancelable: true,
            }));
        }

        document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', {
            key: mainKey, code: mainKey, bubbles: true, cancelable: true,
        }));
        document.activeElement?.dispatchEvent(new KeyboardEvent('keyup', {
            key: mainKey, code: mainKey, bubbles: true, cancelable: true,
        }));

        for (const mod of activeModifiers.reverse()) {
            document.activeElement?.dispatchEvent(new KeyboardEvent('keyup', {
                key: mod, code: mod === 'Control' ? 'ControlLeft' : mod,
                bubbles: true, cancelable: true,
            }));
        }

        return true;
    }

    public scrollToPercent(yPercent: number, el?: Element): void {
        if (el) {
            const scrollable = this.findNearestScrollableElement(el);
            if (scrollable) {
                const maxScroll = scrollable.scrollHeight - scrollable.clientHeight;
                scrollable.scrollTo({ top: maxScroll * (yPercent / 100), behavior: 'smooth' });
                return;
            }
        }
        const maxScroll = document.documentElement.scrollHeight - window.innerHeight;
        window.scrollTo({ top: maxScroll * (yPercent / 100), behavior: 'smooth' });
    }

    public scrollToTop(el?: Element): void {
        if (el) {
            const scrollable = this.findNearestScrollableElement(el);
            if (scrollable) { scrollable.scrollTo({ top: 0, behavior: 'smooth' }); return; }
        }
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    public scrollToBottom(el?: Element): void {
        if (el) {
            const scrollable = this.findNearestScrollableElement(el);
            if (scrollable) { scrollable.scrollTo({ top: scrollable.scrollHeight, behavior: 'smooth' }); return; }
        }
        window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'smooth' });
    }

    public previousPage(el?: Element): void {
        if (el) {
            const scrollable = this.findNearestScrollableElement(el);
            if (scrollable) { scrollable.scrollBy({ top: -scrollable.clientHeight, behavior: 'smooth' }); return; }
        }
        window.scrollBy({ top: -window.innerHeight, behavior: 'smooth' });
    }

    public nextPage(el?: Element): void {
        if (el) {
            const scrollable = this.findNearestScrollableElement(el);
            if (scrollable) { scrollable.scrollBy({ top: scrollable.clientHeight, behavior: 'smooth' }); return; }
        }
        window.scrollBy({ top: window.innerHeight, behavior: 'smooth' });
    }

    public scrollToText(text: string, nth: number = 0): boolean {
        const walker = document.createTreeWalker(
            document.body,
            NodeFilter.SHOW_TEXT,
            {
                acceptNode: (node) =>
                    node.textContent?.toLowerCase().includes(text.toLowerCase())
                        ? NodeFilter.FILTER_ACCEPT
                        : NodeFilter.FILTER_REJECT,
            }
        );

        let count = 0;
        let node: Text | null;
        while ((node = walker.nextNode() as Text | null)) {
            if (count === nth) {
                const parent = node.parentElement;
                if (parent) {
                    parent.scrollIntoView({ behavior: 'auto', block: 'center' });
                    return true;
                }
            }
            count++;
        }
        return false;
    }

    public getDropdownOptions(el: Element): Array<{ value: string; text: string; index: number }> {
        if (!(el instanceof HTMLSelectElement)) return [];
        return Array.from(el.options).map((opt, i) => ({
            value: opt.value,
            text: opt.textContent?.trim() || opt.label || opt.value,
            index: i,
        }));
    }

    public selectDropdownOption(el: Element, text: string): boolean {
        if (!(el instanceof HTMLSelectElement)) return false;

        const lowerText = text.toLowerCase();
        for (const opt of Array.from(el.options)) {
            if (
                opt.value.toLowerCase() === lowerText ||
                (opt.textContent?.trim().toLowerCase() || '') === lowerText ||
                (opt.label?.toLowerCase() || '') === lowerText
            ) {
                el.value = opt.value;
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return true;
            }
        }
        return false;
    }

    public findNearestScrollableElement(el: Element): Element | null {
        let current: Element | null = el;
        while (current && current !== document.documentElement) {
            const style = window.getComputedStyle(current);
            const overflowY = style.overflowY;
            if (
                (overflowY === 'scroll' || overflowY === 'auto') &&
                current.scrollHeight > current.clientHeight
            ) {
                return current;
            }
            current = current.parentElement;
        }
        return document.scrollingElement || document.documentElement;
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
