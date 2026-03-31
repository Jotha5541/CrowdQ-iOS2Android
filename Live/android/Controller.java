package icu.luxcedia.crowdq.live.controller;

import android.graphics.Bitmap;

public interface ShowEffects {
    void flash(double intensity, int durationMs);

    void renderImage(Bitmap image, int durationMs);

    void playSound(byte[] soundData, int durationMs);

    void buzz(int pattern, int durationMs);

    void setBackground(Bitmap image);

    void showMessage(String message);

    void playVideo(String url);

    void sync();
}
