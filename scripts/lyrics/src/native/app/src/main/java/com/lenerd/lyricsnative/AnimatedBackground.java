package com.lenerd.lyricsnative;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import com.lenerd.spotifyplus.sdk.SpotifyPlusComponent;
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnimatedBackground extends SpotifyPlusComponent<AnimatedBackgroundView> {
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public String getName() {
        return "AnimatedBackground";
    }

    @Override
    public AnimatedBackgroundView createView(Context context, SpotifyPlusContext spotifyPlusContext) {
        AnimatedBackgroundView view = new  AnimatedBackgroundView(context);
        String url = spotifyPlusContext.getPlayer().getCurrentTrack().album.image;

        loadImageAsync(view, url);

        return view;
    }

    private void loadImageAsync(AnimatedBackgroundView view, String url) {
        executor.execute(() -> {
            try {
                URL imageUrl = new URL(url);

                HttpURLConnection connection = (HttpURLConnection) imageUrl.openConnection();
                connection.setDoInput(true);
                connection.connect();

                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);

                input.close();
                connection.disconnect();

                if (bitmap != null) {
                    view.post(() -> view.updateImage(bitmap));
                    Log.d("BeautifulLyrics", "Image downloaded!");
                }
            } catch(Exception e) {
                Log.e("BeautifulLyrics", e.getMessage(), e);
            }
        });
    }

}
