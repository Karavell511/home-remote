package local.akenori.receiver;

import android.inputmethodservice.InputMethodService;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

/** Uses Android's regular keyboard channel for DPAD, including non-editable vendor menus. */
public class RemoteInputService extends InputMethodService {
    static volatile RemoteInputService active;
    @Override public void onCreate() { super.onCreate(); active=this; }
    @Override public void onDestroy() { active=null; super.onDestroy(); }
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    @Override public View onCreateInputView() {
        Button switcher=new Button(this); switcher.setText("Выбрать экранную клавиатуру");
        switcher.setOnClickListener(v -> ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker()); return switcher;
    }
}
