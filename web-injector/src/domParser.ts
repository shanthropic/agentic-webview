import { BuildDomTreeEngine, DomNodeData, BuildDomTreeResult } from './buildDomTree';
import { enhancedCssSelectorForElement } from './cssSelector';

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
    cssSelector: string;
    isNew: boolean;
    depth: number;
}

export interface AccessibilityTreeResult {
    tree: AccessibilityNode[];
    truncated: boolean;
    selectorMap: Record<number, string>;
    maxNodeId: number;
    maxHighlightIndex: number;
}

function quickHash(node: AccessibilityNode): string {
    const branchPath = (node.xpath || '').replace(/\[\d+\]/g, '');
    const attrs = Object.entries(node.attributes)
        .filter(([k]) => k !== 'data-agent-id')
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([k, v]) => `${k}=${v}`)
        .join('|');
    let h1 = 0, h2 = 0;
    for (let i = 0; i < branchPath.length; i++) {
        h1 = ((h1 << 5) - h1 + branchPath.charCodeAt(i)) | 0;
    }
    for (let i = 0; i < attrs.length; i++) {
        h2 = ((h2 << 5) - h2 + attrs.charCodeAt(i)) | 0;
    }
    return `${h1}-${h2}-${node.xpath || ''}`;
}

export class DomParser {
    private elementMap = new Map<string, Element>();
    private engine: BuildDomTreeEngine;
    private viewportExpansion: number;
    private subframeTrees: Map<string, AccessibilityNode[]> = new Map();
    private previousElementHashes: Map<number, string> = new Map();

    constructor(viewportExpansion: number = 0) {
        this.viewportExpansion = viewportExpansion;
        this.engine = new BuildDomTreeEngine(this.viewportExpansion, this.elementMap);
    }

    public getAccessibilityTree(maxElements: number = 500): AccessibilityTreeResult {
        this.subframeTrees.clear();
        this.elementMap.clear();
        this.engine = new BuildDomTreeEngine(this.viewportExpansion, this.elementMap);

        const result = this.engine.build();
        const nodes: AccessibilityNode[] = [];
        const selectorMap: Record<number, string> = {};

        this.processNodeMap(result.map, result.rootId, nodes, selectorMap, false, maxElements, 0);

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

        // Compute isNew flag via hash diffing
        const currentHashes = new Map<number, string>();
        for (const node of nodes) {
            if (node.highlightIndex !== null && node.highlightIndex !== undefined) {
                const hash = quickHash(node);
                currentHashes.set(node.highlightIndex, hash);
                node.isNew = !this.previousElementHashes.has(node.highlightIndex)
                    || this.previousElementHashes.get(node.highlightIndex) !== hash;
            }
        }
        this.previousElementHashes = currentHashes;

        this.pruneElementMap();

        // Compute max IDs for cross-frame offset management
        let maxNodeId = 0;
        for (const id of Object.keys(result.map)) {
            const numId = parseInt(id, 10);
            if (!isNaN(numId) && numId > maxNodeId) maxNodeId = numId;
        }
        const maxHighlightIndex = result.highlightIndexCount;

        return {
            tree: nodes,
            truncated: nodes.length >= maxElements,
            selectorMap,
            maxNodeId,
            maxHighlightIndex,
        };
    }

    private processNodeMap(
        map: Record<string, DomNodeData>,
        nodeId: string,
        nodes: AccessibilityNode[],
        selectorMap: Record<number, string>,
        inIframe: boolean,
        maxElements: number,
        depth: number,
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

                const cssSelector = enhancedCssSelectorForElement(
                    nodeData.tagName, nodeData.xpath, nodeData.attributes, nodeData.highlightIndex
                );

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
                    cssSelector,
                    isNew: false,
                    depth,
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
                this.processNodeMap(map, childId, nodes, selectorMap, inIframe, maxElements, depth + 1);
            }
        }
    }

    private extractText(nodeData: DomNodeData, map: Record<string, DomNodeData>): string {
        const textParts: string[] = [];
        const collectText = (data: DomNodeData) => {
            for (const childId of data.children) {
                const child = map[childId];
                if (!child) continue;
                if (child.type === 'TEXT_NODE' && child.text && child.isVisible) {
                    textParts.push(child.text);
                } else if (child.tagName && child.type !== 'TEXT_NODE') {
                    if (child.highlightIndex === undefined || child.highlightIndex === null) {
                        collectText(child);
                    }
                }
            }
        };
        collectText(nodeData);
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
