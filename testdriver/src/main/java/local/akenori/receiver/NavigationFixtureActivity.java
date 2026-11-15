package local.akenori.receiver;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;

/** Isolated emulator screen for actual focus, click and slider tests. */
public class NavigationFixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(32,32,32,32); setContentView(page);
        TextView result=new TextView(this); result.setText("No selection"); page.addView(result);
        for(String label:new String[]{"First","Second","Third"}) {
            Button button=new Button(this); button.setText(label); button.setAllCaps(false); button.setFocusableInTouchMode(true); page.addView(button);
            button.setOnClickListener(v -> result.setText("Clicked "+label)); if(label.equals("First")) button.requestFocus();
        }
        SeekBar slider=new SeekBar(this); slider.setMax(100); slider.setProgress(50); slider.setContentDescription("Test level"); page.addView(slider);
        FrameLayout card=new FrameLayout(this); TextView label=new TextView(this); label.setText("Parent action"); label.setPadding(20,30,20,30); card.addView(label); card.setOnClickListener(v -> result.setText("Clicked parent")); page.addView(card);
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) { return true; }
}
