import { db } from '../../db.js';

export interface KnowledgeNode {
  id: string;
  type: string;
  label: string;
  properties: Record<string, any>;
}

export interface KnowledgeEdge {
  sourceId: string;
  targetId: string;
  relation: string;
  weight: number;
}

class KnowledgeGraphService {
  /**
   * Adds or updates a node in the graph
   */
  public upsertNode(node: KnowledgeNode) {
    db.prepare(
      `INSERT INTO knowledge_nodes (id, type, label, properties_json)
       VALUES (?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         type = excluded.type,
         label = excluded.label,
         properties_json = excluded.properties_json`
    ).run(node.id, node.type, node.label, JSON.stringify(node.properties));
  }

  /**
   * Links two nodes with a relationship
   */
  public link(sourceId: string, targetId: string, relation: string, weight = 1.0) {
    db.prepare(
      `INSERT INTO knowledge_edges (source_id, target_id, relation, weight)
       VALUES (?, ?, ?, ?)
       ON CONFLICT(source_id, target_id, relation) DO UPDATE SET weight = excluded.weight`
    ).run(sourceId, targetId, relation, weight);
  }

  /**
   * Find paths or neighbors for context retrieval
   */
  public getContext(nodeId: string, depth = 1): any[] {
    // Basic implementation: get immediate neighbors
    return db.prepare(
      `SELECT n.id, n.type, n.label, n.properties_json, e.relation
       FROM knowledge_nodes n
       JOIN knowledge_edges e ON n.id = e.target_id
       WHERE e.source_id = ?`
    ).all(nodeId);
  }

  /**
   * High-level: Add a trade insight to the graph
   */
  public addTradeInsight(symbol: string, strategy: string, outcome: string, notes: string) {
    const strategyNodeId = `strat_${strategy}`;
    const symbolNodeId = `symbol_${symbol}`;
    
    this.upsertNode({ id: strategyNodeId, type: 'STRATEGY', label: strategy, properties: {} });
    this.upsertNode({ id: symbolNodeId, type: 'SYMBOL', label: symbol, properties: {} });
    
    this.link(symbolNodeId, strategyNodeId, 'WORKS_WITH', outcome === 'WIN' ? 1.2 : 0.8);
  }
}

export const knowledgeGraph = new KnowledgeGraphService();
