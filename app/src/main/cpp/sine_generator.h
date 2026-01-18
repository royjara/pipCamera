#pragma once

#include <cmath>

/**
 * Simple sine wave generator for audio mock input
 */
class SineGenerator {
public:
    SineGenerator(int sample_rate, float frequency);
    ~SineGenerator() = default;

    /**
     * Generate sine wave samples into buffer
     * @param buffer Output buffer for audio samples
     * @param frame_count Number of frames to generate
     */
    void generate(float* buffer, int frame_count);

    /**
     * Set the frequency of the sine wave
     * @param frequency New frequency in Hz
     */
    void setFrequency(float frequency);

    /**
     * Set the amplitude of the sine wave
     * @param amplitude New amplitude (0.0 to 1.0)
     */
    void setAmplitude(float amplitude);

private:
    int sample_rate_;
    float frequency_;
    float amplitude_;
    double phase_;
    double phase_increment_;

    void updatePhaseIncrement();
};