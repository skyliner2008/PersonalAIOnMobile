import { ProviderClient, ProviderId, ProviderModel, GenerateOptions, GenerateResult, EmbedResult, fitDimensions } from './types.js';
import { pipeline, env, FeatureExtractionPipeline } from '@huggingface/transformers';

// Set cache directory to local folder to avoid polluting user's global directories
env.cacheDir = './.cache/transformers';
// We are in Node, so allow local models (though we fetch them from HF Hub)
env.allowLocalModels = true;

/**
 * NativeLocalProvider runs inference natively in the Node.js process using @huggingface/transformers.
 * It does not require Ollama or an external server.
 * Currently optimized for embedding models like bge-m3.
 */
export class NativeLocalProvider implements ProviderClient {
  public id: ProviderId = 'native';
  public displayName = 'Local Transformers';
  public supportsEmbeddings = true;
  
  // We keep a cache of pipelines so we don't reload the model on every embed call
  private extractors: Map<string, FeatureExtractionPipeline> = new Map();

  async listModels(): Promise<ProviderModel[]> {
    // We statically define supported or recommended models here
    return [
      {
        id: 'Xenova/bge-m3',
        displayName: 'BGE-M3 (Transformers.js)',
        description: 'Multi-lingual dense/sparse model (1024d)',
        role: 'embedding',
        isFree: true
      },
      {
        id: 'Xenova/paraphrase-multilingual-MiniLM-L12-v2',
        displayName: 'MiniLM L12 v2',
        description: 'Fast lightweight multi-lingual model (384d)',
        role: 'embedding',
        isFree: true
      }
    ];
  }

  async testConnection(): Promise<{ ok: boolean; latencyMs: number; detail: string }> {
    return { ok: true, latencyMs: 0, detail: 'Native engine is built-in.' };
  }

  async generate(_apiKey: string, _model: string, _prompt: string, _options?: GenerateOptions): Promise<GenerateResult> {
    throw new Error('NativeProvider currently does not support text generation, only embeddings.');
  }

  async ping(_apiKey: string, _model: string, _baseUrl?: string): Promise<{ ok: boolean; latencyMs: number; detail?: string }> {
    return { ok: true, latencyMs: 0, detail: 'Local' };
  }

  async testGenerate(_apiKey: string, _model: string): Promise<boolean> {
    return false;
  }

  async embed(_apiKey: string, model: string, text: string): Promise<EmbedResult> {
    let extractor = this.extractors.get(model);
    if (!extractor) {
      // Lazy load model on first use. This will take time to download if not cached.
      try {
        extractor = await pipeline('feature-extraction', model);
        this.extractors.set(model, extractor);
      } catch (err: any) {
        throw new Error(`Failed to load native model ${model}: ${err.message}`);
      }
    }

    try {
      const output = await extractor(text, { pooling: 'mean', normalize: true });
      // Output data is a Float32Array
      const rawVector = Array.from(output.data as Float32Array);
      if (rawVector.length === 0) {
        throw new Error(`Native embed ${model} returned empty vector`);
      }
      return { 
        vector: fitDimensions(rawVector), 
        modelUsed: model, 
        nativeDims: rawVector.length 
      };
    } catch (err: any) {
      throw new Error(`Native embed execution failed: ${err.message}`);
    }
  }
}

export const nativeLocalProvider = new NativeLocalProvider();
