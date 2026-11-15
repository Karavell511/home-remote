package local.akenori.remote;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.Button;

/** Immediate press feedback; repeating controls never wait for a release animation. */
public class RemoteButton extends Button {
    private final Handler timer=new Handler(Looper.getMainLooper());
    private Runnable action; private boolean repeat,held,fromTouch;
    private android.graphics.drawable.Drawable centeredIcon;
    private final Runnable repeater=new Runnable() { public void run() { if(held && isEnabled() && action!=null) { tick(); action.run(); timer.postDelayed(this,95); } } };
    public RemoteButton(Context context) { super(context); setAllCaps(false); setMinWidth(0); setMinimumWidth(0); setMinHeight(0); setMinimumHeight(0); }
    void centerIcon(android.graphics.drawable.Drawable icon) { centeredIcon=icon; invalidate(); }
    @Override protected void onDraw(android.graphics.Canvas canvas) { super.onDraw(canvas); if(centeredIcon!=null) { int size=centeredIcon.getIntrinsicWidth(); int x=(getWidth()-size)/2,y=(getHeight()-size)/2; centeredIcon.setBounds(x,y,x+size,y+size); centeredIcon.draw(canvas); } }
    private void tick() { if(getContext().getSharedPreferences("remote_ui",0).getBoolean("haptics",true)) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); }
    @Override public boolean performClick() { if(!fromTouch && isEnabled()) tick(); return super.performClick(); }
    void instant(Runnable operation,boolean repeating) { action=operation; repeat=repeating; setOnClickListener(v -> { if(!fromTouch) action.run(); }); }
    @Override public boolean onTouchEvent(android.view.MotionEvent event) {
        if(action==null || !isEnabled()) return super.onTouchEvent(event);
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: held=true; setPressed(true); getParent().requestDisallowInterceptTouchEvent(true); tick(); action.run(); if(repeat) timer.postDelayed(repeater,350); return true;
            case MotionEvent.ACTION_MOVE: if(event.getX()<0 || event.getY()<0 || event.getX()>getWidth() || event.getY()>getHeight()) cancelPress(); return true;
            case MotionEvent.ACTION_UP: boolean clicked=held; cancelPress(); getParent().requestDisallowInterceptTouchEvent(false); if(clicked) { fromTouch=true; performClick(); fromTouch=false; } return true;
            case MotionEvent.ACTION_CANCEL: cancelPress(); getParent().requestDisallowInterceptTouchEvent(false); return true;
            default: return true;
        }
    }
    void cancelPress() { held=false; setPressed(false); timer.removeCallbacks(repeater); }
    @Override protected void onDetachedFromWindow() { cancelPress(); super.onDetachedFromWindow(); }
}
