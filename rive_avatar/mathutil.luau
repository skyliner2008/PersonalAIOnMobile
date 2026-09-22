-- Math utilities for Rive Avatar animation and physics
local mathutil = {}

function mathutil.clamp(v: number, min: number, max: number): number
    if v < min then return min end
    if v > max then return max end
    return v
end

function mathutil.lerp(a: number, b: number, t: number): number
    return a + (b - a) * t
end

function mathutil.expDecay(a: number, b: number, decay: number, dt: number): number
    return b + (a - b) * math.exp(-decay * dt)
end

return mathutil