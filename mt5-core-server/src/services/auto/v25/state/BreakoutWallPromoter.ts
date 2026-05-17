import type { PriceWall } from '../../analyzers/smc/types.js';
import { wallProximityIndex } from './WallProximityIndex.js';
import { atLog } from '../../utils.js';

export class BreakoutWallPromoter {
  public promote(symbol: string, brokenWall: PriceWall, fromSide: 'ABOVE' | 'BELOW'): PriceWall {
    const flippedSide = fromSide === 'ABOVE' ? 'SUPPORT' : 'RESISTANCE';
    
    const newWall: PriceWall = {
      ...brokenWall,
      side: flippedSide,
      label: `${brokenWall.label} (FLIPPED)`,
      confluenceStars: Math.max(1, brokenWall.confluenceStars - 1), // Decrease 1 star for being broken
    };

    atLog(`[V25] BreakoutWallPromoter: Flipped ${brokenWall.price.toFixed(2)} to ${flippedSide} for retest candidate`);
    
    // Inject into proximity index manually as a temporary wall?
    // Actually, we can just return it, and WallStateMachine can track it directly for RETEST state!
    return newWall;
  }
}

export const breakoutWallPromoter = new BreakoutWallPromoter();
