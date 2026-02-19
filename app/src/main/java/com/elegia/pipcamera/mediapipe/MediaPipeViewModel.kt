package com.elegia.pipcamera.mediapipe

import androidx.lifecycle.ViewModel

class MediaPipeViewModel : ViewModel() {

    private var _delegate: Int = MediaPipeHelper.DELEGATE_CPU
    private var _minFaceDetectionConfidence: Float = MediaPipeHelper.DEFAULT_FACE_DETECTION_CONFIDENCE
    private var _minFaceTrackingConfidence: Float = MediaPipeHelper.DEFAULT_FACE_TRACKING_CONFIDENCE
    private var _minFacePresenceConfidence: Float = MediaPipeHelper.DEFAULT_FACE_PRESENCE_CONFIDENCE
    private var _maxFaces: Int = MediaPipeHelper.DEFAULT_NUM_FACES

    val currentDelegate: Int get() = _delegate
    val currentMinFaceDetectionConfidence: Float get() = _minFaceDetectionConfidence
    val currentMinFaceTrackingConfidence: Float get() = _minFaceTrackingConfidence
    val currentMinFacePresenceConfidence: Float get() = _minFacePresenceConfidence
    val currentMaxFaces: Int get() = _maxFaces

    fun setDelegate(delegate: Int) {
        _delegate = delegate
    }

    fun setMinFaceDetectionConfidence(confidence: Float) {
        _minFaceDetectionConfidence = confidence
    }

    fun setMinFaceTrackingConfidence(confidence: Float) {
        _minFaceTrackingConfidence = confidence
    }

    fun setMinFacePresenceConfidence(confidence: Float) {
        _minFacePresenceConfidence = confidence
    }

    fun setMaxFaces(maxResults: Int) {
        _maxFaces = maxResults
    }
}