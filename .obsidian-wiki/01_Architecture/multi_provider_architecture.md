# Multi-Provider AI Architecture

> อัพเดท: 2026-05-13 | Phase 1-4 สำเร็จ (เพิ่ม MiniMax + Token Optimization + Vertex AI)

## Overview

ระบบ Multi-Provider AI ที่ทำให้ทั้ง Server และ Mobile สามารถสลับ AI Provider ได้อิสระ
โดยไม่ต้อง hardcode กับ Gemini SDK

## Architecture Diagram

```
┌──────────────────────────────────────────────────────────────┐
│  📱 Mobile App (KMP)                                         │
│                                                              │
│  JarvisOrchestrator                                          │
│    ├── GeminiService (text + live voice, legacy)             │
│    ├── LlmProviderRegistry                                   │
│    ├── GeminiLlmProvider (wrapper)                      │
│    │    ├── OpenAILlmProvider                                │
│    │    ├── ClaudeLlmProvider                                │
│    │    ├── OpenRouterLlmProvider ⭐ (free filter)           │
│    │    ├── MinimaxLlmProvider                               │
│    │    ├── VertexAILlmProvider ☁️ (via server proxy)        │
│    │    └── LiteLlmProvider (proxy → Ollama)                 │
│    └── EmbeddingProviderRegistry                             │
│         ├── LocalOnnxProvider (offline, Thai, 768d)          │
│         └── GeminiEmbeddingProvider (cloud)                  │
│                                                              │
│  Memory System → EmbeddingProvider interface                 │
│    searchRelevantFacts / archiveFactWithEmbedding             │
│    backfillEmbeddings / performSleepCycle                     │
├──────────────────────────────────────────────────────────────┤
│  🖥️ Server (mt5-core-server)                                │
│                                                              │
│  AgentOrchestrator → Dispatcher (no Gemini SDK!)             │
│    ├── generateForRole() → provider dispatcher               │
│    └── embedForRole()    → provider dispatcher               │
│                                                              │
│  Providers:                                                  │
│    Gemini | OpenAI | Claude | OpenRouter | MiniMax | Ollama   │
│    Vertex AI (ADC — enterprise GCP)  ☁️ NEW                   │
│                                                              │
│  Vertex AI Proxy Route:                                      │
│    /api/vertex/generate — mobile calls via server ADC         │
│    /api/vertex/embed    — embedding proxy                    │
│    /api/vertex/models   — list models                        │
│    /api/vertex/status   — check availability                 │
│                                                              │
│  Embeddings: fitDimensions() → 768d → HNSW index            │
│  Default: qwen3-embedding (Thai MTEB #1)                     │
│  Option:  bge-m3 (hybrid dense/sparse)                       │
└──────────────────────────────────────────────────────────────┘
```

## Provider Capabilities

| Provider | Text | Embedding | Stream | Tools | Free Models |
|----------|------|-----------|--------|-------|-------------|
| Gemini | ✅ | ✅ | ✅ | ✅ | ✅ (Free tier) |
| OpenAI | ✅ | ✅ | ✅ | ✅ | ❌ |
| Claude | ✅ | ❌ | ✅ | ✅ | ❌ |
| OpenRouter | ✅ | ✅ | ✅ | ✅ | ✅ (filter) |
| MiniMax | ✅ | ❌ | ✅ | ✅ | ❌ |
| Ollama | ✅ | ✅ | ✅ | ❌ | ✅ (local) |
| **Vertex AI** | **✅** | **✅** | **❌** | **❌** | **❌** (GCP) |
| LiteLLM | ✅ | ❌ | ✅ | ✅ | ✅ (proxy) |

## API Endpoints

### Model Listing
- `GET /api/auto/providers/:id/models` — ดึงรายการ models
- `GET /api/auto/providers/:id/models?free=true` — เฉพาะ models ฟรี
- `POST /api/auto/providers/:id/refresh` — รีเฟรช cache

### Provider Config
- `PUT /api/auto/providers/:id/key` — ตั้ง API key
- `POST /api/auto/providers/:id/test` — ทดสอบ connectivity

## Embedding Strategy

### Server (Ollama)
- **Primary**: `qwen3-embedding` — Thai MTEB อันดับ 1, 1024d → truncate to 768d
- **Alternative**: `bge-m3` — hybrid dense/sparse, 1024d, multi-lingual

### Mobile
- **Offline**: `LocalOnnxProvider` — `paraphrase-multilingual-MiniLM-L12-v2` (q4 ONNX, ~117MB), native 384d → `fitToTargetDimension(768)` (pad + L2-norm), รองรับภาษาไทย + 50+ ภาษา
- **Cloud**: `GeminiEmbeddingProvider` — cascade `gemini-embedding-001` (3072d → Matryoshka truncate to 768d) → fallback `text-embedding-004` (768d), L2-normalized
  - หมายเหตุ: deprecated `embedding-001` ถูกถอดออก (404'd ตั้งแต่ 2026-04)
- **Resolution**: Offline-first → Cloud fallback (ทุก provider คืน 768d L2-norm จึง interchangeable ใน HNSW เดียวกัน)

### Dimension Normalization
ทุก provider ผ่าน `fitDimensions()` / `fitToTargetDimension()` ก่อนเก็บลง HNSW:
- ถ้า > 768d → Matryoshka truncate + L2 normalize
- ถ้า < 768d → zero-pad + L2 normalize
- ถ้า = 768d → L2 normalize

## Key Files

### Server
- `providers/types.ts` — ProviderModel + pricing/isFree
- `providers/registry.ts` — providerRegistry + PROVIDER_DEFAULTS
- `providers/ollama.ts` — /api/embed compat + isFree
- `providers/vertexai.ts` — Vertex AI Provider (ADC auth)
- `routes/vertexProxy.ts` — Mobile-facing proxy endpoints
- `agentOrchestrator.ts` — provider-agnostic (no Gemini SDK)

### Mobile
- `data/embedding/EmbeddingProvider.kt` — interface
- `data/embedding/EmbeddingProviderRegistry.kt` — offline-first
- `data/providers/LlmProvider.kt` — interface + data classes
- `data/providers/LlmProviderRegistry.kt` — central registry
- `data/providers/OpenRouterLlmProvider.kt` — pricing + free filter
- `data/providers/VertexAILlmProvider.kt` — via server proxy (ADC)
- `ui/screen/SettingsDialog.kt` — OpenRouter key field

## Settings Keys (SQLite)
- `api_key` — Gemini API key
- `openai_api_key` — OpenAI key
- `claude_api_key` — Claude key  
- `openrouter_api_key` — OpenRouter key
- `minimax_api_key` — MiniMax key

## ข้อควรระวัง
- **Re-indexing**: เปลี่ยน embedding model = ต้องสร้าง HNSW index ใหม่
- **Mobile binary size**: LocalOnnxProvider เพิ่ม ~180MB (EmbeddingGemma model)
- **Ollama embed**: ต้อง Ollama v0.5+ สำหรับ `/api/embed` endpoint
- **Vertex AI**: ต้องตั้ง `VERTEX_PROJECT_ID` + `gcloud auth application-default login`
- **Vertex AI Mobile**: ต้องเชื่อมต่อกับ mt5-core-server เพราะ ADC ไม่ทำงานบนมือถือ
