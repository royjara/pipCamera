#include "sine_generator.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

SineGenerator::SineGenerator(int sample_rate, float frequency)
    : sample_rate_(sample_rate)
    , frequency_(frequency)
    , amplitude_(0.5f)  // Safe default amplitude
    , phase_(0.0) {
    updatePhaseIncrement();
}

void SineGenerator::generate(float* buffer, int frame_count) {
    if (!buffer || frame_count <= 0) {
        return;
    }

    for (int i = 0; i < frame_count; ++i) {
        // Generate sine wave sample
        buffer[i] = static_cast<float>(amplitude_ * std::sin(phase_));

        // Update phase for next sample
        phase_ += phase_increment_;

        // Wrap phase to prevent overflow
        if (phase_ >= 2.0 * M_PI) {
            phase_ -= 2.0 * M_PI;
        }
    }
}

void SineGenerator::setFrequency(float frequency) {
    frequency_ = frequency;
    updatePhaseIncrement();
}

void SineGenerator::setAmplitude(float amplitude) {
    // Clamp amplitude to safe range
    amplitude_ = std::max(0.0f, std::min(1.0f, amplitude));
}

void SineGenerator::updatePhaseIncrement() {
    phase_increment_ = 2.0 * M_PI * frequency_ / sample_rate_;
}