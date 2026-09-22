-- Tests for Avatar math and physics
local mathutil = require('mathutil')

function setup(test: Tester)
    test.group('clamp', function()
        test.case('clamps high', function(expect)
            expect(mathutil.clamp(10, -1, 1)).is(1)
        end)
        test.case('clamps low', function(expect)
            expect(mathutil.clamp(-5, -1, 1)).is(-1)
        end)
        test.case('passes in-bounds values', function(expect)
            expect(mathutil.clamp(0.5, -1, 1)).is(0.5)
        end)
    end)

    test.group('lerp', function()
        test.case('interpolates correctly', function(expect)
            expect(mathutil.lerp(0, 100, 0.5)).is(50)
            expect(mathutil.lerp(10, 20, 0.1)).is(11)
        end)
    end)
end

return function(): Tests
    return setup
end