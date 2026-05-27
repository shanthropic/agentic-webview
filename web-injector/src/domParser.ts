import { BuildDomTreeEngine, DomNodeData, BuildDomTreeResult } from './buildDomTree';

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
    xpath: string;
    isTopElement: boolean;
    isInteractive: boolean;
    highlightIndex: number | null;
}

export interface AccessibilityTreeResult {
    tree: AccessibilityNode[];
    truncated: boolean;
    selectorMap: Record<number, string>;
}

export class DomParser {
    private elementMap = new Map<string, Element>();
    private engine: BuildDomTreeEngine;
    private viewportExpansion: number;
    private subframeTrees: Map<string, AccessibilityNode[]> = new Map();

    constructor(viewportExpansion: number = 0) {
        this.viewportExpansion = viewportExpansion;
        this.engine = new BuildDomTreeEngine(this.viewportExpansion, this.elementMap);
    }

    public getAccessibilityTree(maxElements: number = 500): AccessibilityTreeResult {
        this.elementMap.clear();
        this.engine = new BuildDomTreeEngine(this.viewportExpansion, this.elementMap);

        const result = this.engine.build();
        const nodes: AccessibilityNode[] = [];
        const selectorMap: Record<number, string> = {};

        this.processNodeMap(result.map, result.rootId, nodes, selectorMap, false, maxElements);

        // Merge subframe trees
        for (const [url, subframeNodes] of this.subframeTrees) {
            for (const node of subframeNodes) {
                if (nodes.length >= maxElements) break;
                node.inIframe = true;
                nodes.push(node);
                if (node.highlightIndex !== null && node.highlightIndex !== undefined) {
                    selectorMap[node.highlightIndex] = node.id;
                }
            }
        }

        this.pruneElementMap();

        return {
            tree: nodes,
            truncated: nodes.length >= maxElements,
            selectorMap,
        };
    }

    private processNodeMap(
        map: Record<string, DomNodeData>,
        nodeId: string,
        nodes: AccessibilityNode[],
        selectorMap: Record<number, string>,
        inIframe: boolean,
        maxElements: number,
    ): void {
        if (nodes.length >= maxElements) return;

        const nodeData = map[nodeId];
        if (!nodeData) return;

        // Process element nodes
        if (nodeData.tagName && nodeData.type !== 'TEXT_NODE') {
            // Include interactive elements (ones with highlightIndex)
            if (nodeData.highlightIndex !== undefined && nodeData.highlightIndex !== null) {
                const agentId = nodeData.highlightIndex.toString();
                const el = this.elementMap.get(agentId);

                const node: AccessibilityNode = {
                    id: agentId,
                    tag: nodeData.tagName.toUpperCase(),
                    text: this.extractText(nodeData, map),
                    role: nodeData.attributes['role'] || '',
                    bounds: el ? this.getBounds(el) : { left: 0, top: 0, width: 0, height: 0 },
                    attributes: nodeData.attributes,
                    occluded: el ? !nodeData.isTopElement! : false,
                    inIframe,
                    xpath: nodeData.xpath,
                    isTopElement: nodeData.isTopElement || false,
                    isInteractive: nodeData.isInteractive || false,
                    highlightIndex: nodeData.highlightIndex,
                };

                nodes.push(node);
                selectorMap[nodeData.highlightIndex] = agentId;
            }
            // Also include visible, top-element nodes without highlightIndex
            // (for backward compatibility and non-interactive element visibility)
            else if (
                nodeData.isVisible &&
                nodeData.isTopElement &&
                !nodeData.isInteractive &&
                nodeData.children.length > 0
            ) {
                // Don't add to nodes list, but recurse into children
            }

            // Recurse into children
            for (const childId of nodeData.children) {
                this.processNodeMap(map, childId, nodes, selectorMap, inIframe, maxElements);
            }
        }
    }

    private extractText(nodeData: DomNodeData, map: Record<string, DomNodeData>): string {
        // Collect text from direct text-node children
        const textParts: string[] = [];
        for (const childId of nodeData.children) {
            const child = map[childId];
            if (child && child.type === 'TEXT_NODE' && child.text && child.isVisible) {
                textParts.push(child.text);
            }
        }
        if (textParts.length > 0) return textParts.join(' ').trim();

        // Fallback to attributes
        return nodeData.attributes['aria-label'] ||
            nodeData.attributes['placeholder'] ||
            nodeData.attributes['title'] ||
            nodeData.attributes['value'] ||
            '';
    }

    private getBounds(el: Element): ElementBounds {
        const rect = el.getBoundingClientRect();
        return {
            left: rect.left,
            top: rect.top,
            width: rect.width,
            height: rect.height,
        };
    }

    public getElementById(id: string): Element | undefined {
        return this.elementMap.get(id);
    }

    public getSelectorMap(): Map<number, string> {
        const result = new Map<number, string>();
        for (const [agentId, _] of this.elementMap) {
            const numId = parseInt(agentId, 10);
            if (!isNaN(numId)) result.set(numId, agentId);
        }
        return result;
    }

    public mergeSubframeTree(url: string, nodes: AccessibilityNode[]): void {
        this.subframeTrees.set(url, nodes);
    }

    private pruneElementMap(): void {
        for (const [id, el] of this.elementMap.entries()) {
            if (!document.body.contains(el) && !el.isConnected) {
                this.elementMap.delete(id);
            }
        }
    }
}
