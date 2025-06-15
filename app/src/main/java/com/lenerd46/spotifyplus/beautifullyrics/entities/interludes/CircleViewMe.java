package com.lenerd46.spotifyplus.beautifullyrics.entities.interludes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CircleViewMe extends View {
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CircleViewMe(Context context) {
        super(context);
        init();
    }

    public CircleViewMe(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void init() {
        paint.setColor(Color.WHITE);
    }

    public void setOpacity(double opacity) {
        paint.setAlpha((int) (255 * opacity));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = canvas.getWidth();
        float height = canvas.getHeight();
        float radius = Math.min(getWidth(), getHeight()) / 2f;
        canvas.drawCircle((float) width / 2f, (float) height / 2f, radius, paint);
    }
}
