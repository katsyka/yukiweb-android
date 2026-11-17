package lol.yukichat.web;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.PanZoomController;

/**
 * GeckoView with Chrome-style pull to refresh: a downward drag that starts while the page
 * (or the scroll box under the finger) is already at the top and the page didn't take the touch itself.
 */
final class PullView extends GeckoView {
    interface Listener {
        void pull(float dy);
        void release(boolean commit);
    }

    Listener listener;
    private final int slop;
    private float x0, y0;
    private boolean atTop, pulling;

    PullView(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                x0 = e.getX();
                y0 = e.getY();
                atTop = false;
                pulling = false;
                onTouchEventForDetailResult(e).accept(d -> atTop = d != null
                        && d.handledResult() != PanZoomController.INPUT_RESULT_HANDLED_CONTENT
                        && (d.scrollableDirections() & PanZoomController.SCROLLABLE_FLAG_TOP) == 0);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dy = e.getY() - y0;
                if (!pulling && atTop && e.getPointerCount() == 1 && dy > slop && dy > 1.5f * Math.abs(e.getX() - x0)) {
                    pulling = true;
                    y0 = e.getY();
                    dy = 0;
                }
                if (pulling && listener != null) listener.pull(Math.max(0, dy));
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (pulling && listener != null) listener.release(false);
                pulling = false;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (pulling && listener != null) listener.release(e.getActionMasked() == MotionEvent.ACTION_UP);
                pulling = false;
                break;
        }
        return super.onTouchEvent(e);
    }
}
