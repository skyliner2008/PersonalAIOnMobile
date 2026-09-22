-- Avatar Living Controller for Rive
-- Drives eye tracking, blink cycle, micro-saccades, and speech reactivity.
local mathutil = require('mathutil')

type AvatarController = {
    size: Vector,
    -- ViewModel Property handles
    gazeX: Property<number>?,
    gazeY: Property<number>?,
    leftEyeX: Property<number>?,
    leftEyeY: Property<number>?,
    rightEyeX: Property<number>?,
    rightEyeY: Property<number>?,
    eyeScaleY: Property<number>?,
    pupilX: Property<number>?,
    pupilY: Property<number>?,
    mouthScaleY: Property<number>?,

    -- Input handles from Host / Android
    inputGazeX: Property<number>?,
    inputGazeY: Property<number>?,
    inputSpeaking: Property<boolean>?,
    inputMouth: Property<number>?,

    -- Internal physics & state
    curGazeX: number,
    curGazeY: number,
    targetGazeX: number,
    targetGazeY: number,

    -- Saccade & blink state
    blinkTimer: number,
    blinkDuration: number,
    isBlinking: boolean,
    saccadeTimer: number,

    -- Speech reactivity
    speechTimer: number,
}

function init(self: AvatarController, context: Context): boolean
    local vm = context:viewModel()
    if vm == nil then
        return true
    end
    self.gazeX = vm:getNumber('gazeX')
    self.gazeY = vm:getNumber('gazeY')
    self.leftEyeX = vm:getNumber('leftEyeX')
    self.leftEyeY = vm:getNumber('leftEyeY')
    self.rightEyeX = vm:getNumber('rightEyeX')
    self.rightEyeY = vm:getNumber('rightEyeY')
    self.eyeScaleY = vm:getNumber('eyeScaleY')
    self.pupilX = vm:getNumber('pupilX')
    self.pupilY = vm:getNumber('pupilY')
    self.mouthScaleY = vm:getNumber('mouthScaleY')

    self.inputGazeX = vm:getNumber('inputGazeX')
    self.inputGazeY = vm:getNumber('inputGazeY')
    self.inputSpeaking = vm:getBoolean('inputSpeaking')
    self.inputMouth = vm:getNumber('inputMouth')
    return true
end

function resize(self: AvatarController, size: Vector)
    self.size = size
end

function pointerMove(self: AvatarController, event: PointerEvent)
    local w = if self.size.x > 0 then self.size.x else 500
    local h = if self.size.y > 0 then self.size.y else 500
    -- Map pointer position to -1.0 .. +1.0 relative to center
    self.targetGazeX = mathutil.clamp((event.position.x / w) * 2 - 1, -1, 1)
    self.targetGazeY = mathutil.clamp((event.position.y / h) * 2 - 1, -1, 1)
end

function advance(self: AvatarController, seconds: number): boolean
    -- 0. Synchronize with external input coordinates from Android / Host
    if self.inputGazeX ~= nil and math.abs(self.inputGazeX.value) > 0.001 then
        self.targetGazeX = mathutil.clamp(self.inputGazeX.value, -1, 1)
    end
    if self.inputGazeY ~= nil and math.abs(self.inputGazeY.value) > 0.001 then
        self.targetGazeY = mathutil.clamp(self.inputGazeY.value, -1, 1)
    end

    -- 1. Smooth gaze following (Adaptive EMA damping)
    local k = 1 - math.exp(-12 * seconds)
    self.curGazeX = self.curGazeX + (self.targetGazeX - self.curGazeX) * k
    self.curGazeY = self.curGazeY + (self.targetGazeY - self.curGazeY) * k

    -- 2. Micro-saccades (subtle procedural drift every 2.8s)
    self.saccadeTimer = self.saccadeTimer + seconds
    if self.saccadeTimer > 2.8 then
        self.saccadeTimer = 0
        -- Add small jitter only if not actively pointing far
        if math.abs(self.targetGazeX) < 0.2 and math.abs(self.targetGazeY) < 0.2 then
            self.targetGazeX = (math.random() - 0.5) * 0.15
            self.targetGazeY = (math.random() - 0.5) * 0.12
        end
    end

    -- 3. Procedural Blink Cycle (Every ~3.5s)
    self.blinkTimer = self.blinkTimer + seconds
    local curEyeScaleY = 1.0
    if not self.isBlinking and self.blinkTimer > 3.5 then
        self.isBlinking = true
        self.blinkTimer = 0
        self.blinkDuration = 0
    end

    if self.isBlinking then
        self.blinkDuration = self.blinkDuration + seconds
        if self.blinkDuration < 0.08 then
            -- Squash down to 0.08
            curEyeScaleY = mathutil.lerp(1.0, 0.08, self.blinkDuration / 0.08)
        elseif self.blinkDuration < 0.16 then
            -- Spring back up to 1.0
            curEyeScaleY = mathutil.lerp(0.08, 1.0, (self.blinkDuration - 0.08) / 0.08)
        else
            self.isBlinking = false
            curEyeScaleY = 1.0
        end
    end

    -- 4. Audio-Reactive & Cyber-Breathing Mouth Movement
    self.speechTimer = self.speechTimer + seconds
    local curMouthScaleY = 1.0
    if self.inputSpeaking ~= nil and self.inputSpeaking.value then
        local vol = if self.inputMouth ~= nil then self.inputMouth.value else 0.5
        curMouthScaleY = 1.0 + vol * 1.5 + math.sin(self.speechTimer * 14.0) * 0.4
    else
        -- Gentle rhythmic breathing
        curMouthScaleY = 1.0 + math.sin(self.speechTimer * 2.8) * 0.18
    end

    -- 5. Push values into View Model properties
    local baseLeftX = 175.0
    local baseRightX = 325.0
    local baseEyeY = 230.0

    local eyeShiftX = self.curGazeX * 22.0
    local eyeShiftY = self.curGazeY * 16.0
    local pupilShiftX = self.curGazeX * 12.0
    local pupilShiftY = self.curGazeY * 10.0

    if self.gazeX ~= nil then self.gazeX.value = self.curGazeX end
    if self.gazeY ~= nil then self.gazeY.value = self.curGazeY end

    if self.leftEyeX ~= nil then self.leftEyeX.value = baseLeftX + eyeShiftX end
    if self.leftEyeY ~= nil then self.leftEyeY.value = baseEyeY + eyeShiftY end
    if self.rightEyeX ~= nil then self.rightEyeX.value = baseRightX + eyeShiftX end
    if self.rightEyeY ~= nil then self.rightEyeY.value = baseEyeY + eyeShiftY end

    if self.pupilX ~= nil then self.pupilX.value = pupilShiftX end
    if self.pupilY ~= nil then self.pupilY.value = pupilShiftY end

    if self.eyeScaleY ~= nil then self.eyeScaleY.value = curEyeScaleY end
    if self.mouthScaleY ~= nil then self.mouthScaleY.value = curMouthScaleY end

    return true
end

return function(context: Context): Layout<AvatarController>
    return {
        size = Vector.xy(500, 500),
        gazeX = nil,
        gazeY = nil,
        leftEyeX = nil,
        leftEyeY = nil,
        rightEyeX = nil,
        rightEyeY = nil,
        eyeScaleY = nil,
        pupilX = nil,
        pupilY = nil,
        mouthScaleY = nil,

        inputGazeX = nil,
        inputGazeY = nil,
        inputSpeaking = nil,
        inputMouth = nil,

        curGazeX = 0,
        curGazeY = 0,
        targetGazeX = 0,
        targetGazeY = 0,

        blinkTimer = 0,
        blinkDuration = 0,
        isBlinking = false,
        saccadeTimer = 0,
        speechTimer = 0,

        init = init,
        resize = resize,
        advance = advance,
        pointerMove = pointerMove,
    }
end