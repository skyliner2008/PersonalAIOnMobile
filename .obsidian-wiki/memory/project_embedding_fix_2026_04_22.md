---
name: Embedding model cascade fix 2026-04-22
description: Swapped deprecated embedding-001/text-embedding-004 cascade for gemini-embedding-001 + Matryoshka-truncate-to-768 in agentOrchestrator.ts
type: project
originSessionId: 4e0a795a-c079-492a-9f0b-2aa2ea11be20
---
AutoEngine was logging `[Agent] All embedding models failed. Using zero vector.` because Google returned 404 for both `text-embedding-004` (v1) and `embedding-001` (v1beta). The zero-vector fallback silently disabled Layer-2 vector retrieval.

Fix applied in `mt5-core-server/src/services/auto/agentOrchestrator.ts`:
- New cascade: `gemini-embedding-001` (v1beta) → `text-embedding-004` (v1beta).
- Added `fitDimensions()` helper: slices first 768 components and L2-normalizes (Matryoshka reduction) so `gemini-embedding-001`'s native 3072-dim output fits the 768-dim HNSW index without losing retrieval quality.
- Cascade now only falls through on 404/400 (model-missing). Quota/transient errors break out and return the zero vector once, rather than hammering every fallback.
- `@google/generative-ai@0.24.1` has no `outputDimensionality` param — manual truncate+normalize is required.

**Why:** Google deprecated the older embedding endpoints, breaking vector recall in the AutoTrading loop.
**How to apply:** If vectorStore dimension ever changes, update `EMBEDDING_TARGET_DIMS` in agentOrchestrator.ts to match `DIMENSION` in vectorStore.ts. If migrating to `@google/genai` SDK, switch to passing `outputDimensionality: 768` on the request instead of post-hoc truncation.
