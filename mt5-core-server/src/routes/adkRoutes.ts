import { Router, Request, Response } from 'express';
import { runAdkAgent } from '../adk/adkAgent.js';
import { atLog, atError } from '../services/auto/utils.js';

const router = Router();


/**
 * GET /api/adk/models
 * Returns the curated list of models and their capabilities for ADK.
 */
router.get('/models', async (_req, res) => {
  try {
    const models = [
      { id: 'gemini-3.1-flash-live-preview', name: 'Gemini 3.1 Flash Live (ADK Primary)', supportsVision: true, supportsLive: true },
      { id: 'gemini-2.5-flash-native-audio-preview-09-2025', name: 'Gemini 2.5 Flash Native Audio (ADK Secondary)', supportsVision: true, supportsLive: true },
      { id: 'gemini-3.5-live-translate-preview', name: 'Gemini 3.5 Live Translate (ADK)', supportsVision: true, supportsLive: true },
      { id: 'gemini-2.0-flash-exp', name: 'Gemini 2.0 Flash (ADK)', supportsVision: true, supportsLive: false },
      { id: 'gemini-2.5-flash-lite', name: 'Gemini 2.5 Flash Lite (ADK)', supportsVision: true, supportsLive: false }
    ];
    res.json({ success: true, models });
  } catch (error: any) {
    atError(`[adkRoutes] Error in /models: ${error.message}`);
    res.status(500).json({ error: error.message });
  }
});


/**
 * POST /api/adk/chat
 * Route dedicated to processing requests via the ADK Graph / Agent
 */
router.post('/chat', async (req: Request, res: Response) => {
  const { model, messages, systemInstruction } = req.body;

  if (!model || !messages || !Array.isArray(messages) || messages.length === 0) {
    return res.status(400).json({ error: 'Missing or invalid model or messages' });
  }

  atLog(`[adkRoutes] Incoming /chat request: model=${model}, messages.length=${messages.length}`);
  atLog(`[adkRoutes] req.body: ${JSON.stringify(req.body, null, 2)}`);

  // Format messages for the agent (using the latest user message as the prompt)
  const latestMessage = messages[messages.length - 1];
  let prompt = '';
  
  if (typeof latestMessage.content === 'string') {
    prompt = latestMessage.content;
  } else if (Array.isArray(latestMessage.content)) {
    // Basic extraction if it's an array of parts
    prompt = latestMessage.content.map((p: any) => p.text || '').join('');
  } else if (Array.isArray(latestMessage.parts)) {
    // Fallback if parts array is sent directly (Gemini format)
    prompt = latestMessage.parts.map((p: any) => p.text || '').join('');
  }

  try {
    const result = await runAdkAgent({
      prompt,
      model,
      systemInstruction: systemInstruction?.parts?.[0]?.text,
      messages,
      tools: req.body.tools
    });

    let parts: any[] = [];
    if (result.functionCalls && result.functionCalls.length > 0) {
      parts = result.functionCalls.map((fc: any) => ({
        functionCall: {
          id: fc.id,
          name: fc.name,
          args: typeof fc.arguments === 'string' ? JSON.parse(fc.arguments) : fc.arguments
        }
      }));
    } else {
      parts = [{ text: result.response || '' }];
    }

    // Match the expected format for the mobile app's LlmProvider
    res.json({
      candidates: [
        {
          content: {
            role: 'model',
            parts
          }
        }
      ],
      usageMetadata: {
        candidatesTokenCount: 0,
        promptTokenCount: 0,
        totalTokenCount: 0
      },
      agentTraceId: result.agentTraceId // Custom field for ADK observability
    });
  } catch (error: any) {
    atError(`[adkRoutes] Error in /chat: ${error.message}`);
    res.status(500).json({ error: error.message });
  }
});

export default router;
