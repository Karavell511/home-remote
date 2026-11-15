package local.akenori.receiver;
import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** No clickable nodes or range: models a projector menu that only handles physical keys. */
public class HardwareKeyFixtureActivity extends Activity {
    private TextView value; private int level=100;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setFocusableInTouchMode(true);
        value=new TextView(this); value.setText("Scale 100"); root.addView(value); setContentView(root); root.requestFocus();
    }
    @Override public boolean onKeyDown(int code,KeyEvent event) {
        if(code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_DPAD_RIGHT) { level+=code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1; value.setText("Scale "+level); return true; }
        if(code==KeyEvent.KEYCODE_DPAD_CENTER) { value.setText("OK received"); return true; }
        if(code==KeyEvent.KEYCODE_MENU) { value.setText("Menu received"); return true; }
        return super.onKeyDown(code,event);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) { return true; }
}
