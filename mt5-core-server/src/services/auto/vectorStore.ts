import path from 'path';
import fs from 'fs';
import hnsw from 'hnswlib-node';
import { atLog, atWarn, atError, getLogTime } from './utils.js';
import { EMBEDDING_TARGET_DIMS } from './providers/types.js';

const INDEX_FILE = path.join(process.cwd(), 'data', 'market_memory.bin');
// Single source of truth — must match the dim every provider truncates to.
const DIMENSION = EMBEDDING_TARGET_DIMS;
const MAX_ELEMENTS = 10000;

export interface VectorMetadata {
  id: string;
  symbol: string;
  timeframe: string;
  type: 'market_snapshot' | 'trade_outcome' | 'trade_lesson';
  timestamp: number;
  text?: string;
  outcome?: string;
  profitR?: number;
  // P3.4: comma-separated failure-pattern tags (e.g. "BUY_IN_PREMIUM,SCALE_UP_LOSER")
  // for targeted retrieval of "what went wrong" priors before LLM reasoning.
  failure_pattern?: string;
  side?: string;
  strategy?: string;
  regime?: string;
  zoneAtEntry?: string;
}

class VectorStoreService {
  private index: hnsw.HierarchicalNSW | null = null;
  private metadataMap: Map<number, VectorMetadata> = new Map();
  private lastId = 0;

  constructor() {
    this.init();
  }

  private init() {
    try {
      const dataDir = path.join(process.cwd(), 'data');
      if (!fs.existsSync(dataDir)) fs.mkdirSync(dataDir);
      const metaPath = INDEX_FILE + '.meta';
      const haveBoth = fs.existsSync(INDEX_FILE) && fs.existsSync(metaPath);
      this.index = new hnsw.HierarchicalNSW('l2', DIMENSION);
      if (haveBoth) {
        try {
          // Load capacity hint from disk; fallback to MAX_ELEMENTS on parse failure.
          this.index.readIndex(INDEX_FILE);
          const metaStr = fs.readFileSync(metaPath, 'utf8');
          const parsed = JSON.parse(metaStr);
          this.metadataMap = new Map(Object.entries(parsed).map(([k, v]) => [Number(k), v as VectorMetadata]));
          this.lastId = Math.max(0, ...Array.from(this.metadataMap.keys()));
          atLog(`[VectorStore] Loaded persistent memory: ${this.metadataMap.size} snapshots`);
        } catch (loadErr) {
          atError('[VectorStore] Failed to load persistent index — re-initializing fresh:', loadErr);
          this.index = new hnsw.HierarchicalNSW('l2', DIMENSION);
          this.index.initIndex(MAX_ELEMENTS);
          this.metadataMap.clear();
          this.lastId = 0;
        }
      } else {
        this.index.initIndex(MAX_ELEMENTS);
        atLog('[VectorStore] Initialized new vector memory');
      }
    } catch (error) {
      atError('[VectorStore] Failed to init index:', error);
    }
  }

  public add(vector: number[], metadata: VectorMetadata) {
    if (!this.index) return;
    if (!Array.isArray(vector) || vector.length !== DIMENSION) {
      atError(`[VectorStore] Refusing to add vector with wrong dim: got ${vector?.length} expected ${DIMENSION}`);
      return;
    }
    try {
      // Auto-grow capacity instead of throwing once we hit MAX_ELEMENTS.
      if (this.index.getCurrentCount() >= this.index.getMaxElements()) {
        const newMax = this.index.getMaxElements() * 2;
        atLog(`[VectorStore] Resizing index ${this.index.getMaxElements()} → ${newMax}`);
        this.index.resizeIndex(newMax);
      }
      const id = ++this.lastId;
      this.index.addPoint(vector, id);
      this.metadataMap.set(id, metadata);
      this.save();
    } catch (error) {
      atError('[VectorStore] Failed to add vector:', error);
    }
  }

  public search(vector: number[], topK = 5, filter?: { symbol?: string; type?: string }) {
    if (!this.index || this.metadataMap.size === 0) return [];
    if (!Array.isArray(vector) || vector.length !== DIMENSION) {
      atError(`[VectorStore] search() rejected vector with dim ${vector?.length} (expected ${DIMENSION})`);
      return [];
    }
    // Reject all-zero query — common when an upstream embedding call fails;
    // searching with zeros ranks results by raw vector norm instead of meaning.
    let nonZero = false;
    for (const v of vector) { if (v !== 0) { nonZero = true; break; } }
    if (!nonZero) {
      atWarn('[VectorStore] search() rejected zero vector — upstream embed likely failed');
      return [];
    }
    try {
      const result = this.index.searchKnn(vector, Math.min(topK * 3, this.metadataMap.size));
      let matches = result.neighbors.map((id, index) => ({
        metadata: this.metadataMap.get(id),
        distance: result.distances[index]
      }));
      if (filter) {
        if (filter.symbol) matches = matches.filter(m => m.metadata?.symbol === filter.symbol);
        if (filter.type)   matches = matches.filter(m => m.metadata?.type === filter.type);
      }
      return matches.slice(0, topK);
    } catch (error) {
      atError('[VectorStore] Search failed:', error);
      return [];
    }
  }

  public prune(olderThanMs: number) {
    if (!this.index) return;
    const now = Date.now();
    const threshold = now - olderThanMs;
    const initialSize = this.metadataMap.size;
    const remainingVectors: { vector: number[], id: number, meta: VectorMetadata }[] = [];
    for (const [id, meta] of this.metadataMap.entries()) {
      if (meta.timestamp >= threshold) {
        try {
          const vector = this.index.getPoint(id);
          remainingVectors.push({ vector, id, meta });
        } catch (err) { /* point missing */ }
      }
    }
    if (remainingVectors.length < initialSize * 0.7) {
      atLog(`[VectorStore] Pruning ${initialSize - remainingVectors.length} vectors...`);
      this.index = new hnsw.HierarchicalNSW('l2', DIMENSION);
      this.index.initIndex(MAX_ELEMENTS);
      this.metadataMap.clear();
      this.lastId = 0;
      for (const item of remainingVectors) {
        const newId = ++this.lastId;
        this.index.addPoint(item.vector, newId);
        this.metadataMap.set(newId, item.meta);
      }
      this.save();
    }
  }

  private save() {
    if (!this.index) return;
    try {
      this.index.writeIndex(INDEX_FILE);
      const metaPath = INDEX_FILE + '.meta';
      const metaObj = Object.fromEntries(this.metadataMap);
      fs.writeFileSync(metaPath, JSON.stringify(metaObj), 'utf8');
    } catch (error) {
      atError('[VectorStore] Failed to save index:', error);
    }
  }
}

export const vectorStore = new VectorStoreService();
