package local.akenori.receiver;

import android.accessibilityservice.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputBinding;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodInfo;
import android.provider.Settings;

/** Android 13 Accessibility input channel; only the current foreground application. */
final class NativeKeys {
    static boolean global(ControlService service,String key) {
        if(Build.VERSION.SDK_INT<33) return false;
        int action;
        switch(key) {
            case "up":action=AccessibilityService.GLOBAL_ACTION_DPAD_UP;break;
            case "down":action=AccessibilityService.GLOBAL_ACTION_DPAD_DOWN;break;
            case "left":action=AccessibilityService.GLOBAL_ACTION_DPAD_LEFT;break;
            case "right":action=AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT;break;
            case "ok":action=AccessibilityService.GLOBAL_ACTION_DPAD_CENTER;break;
            default:return false;
        }
        return service.performGlobalAction(action);
    }
    static String setup="not_started";
    static void enable(ControlService service) {
        if(Build.VERSION.SDK_INT<33) { setup="manual_enable_required_api_"+Build.VERSION.SDK_INT; return; }
        InputMethodManager manager=(InputMethodManager)service.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        for(InputMethodInfo info:manager.getInputMethodList()) if(info.getPackageName().equals(service.getPackageName()) && info.getServiceName().equals(RemoteInputService.class.getName())) {
            String previous=Settings.Secure.getString(service.getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);
            if(previous!=null && !previous.equals(info.getId())) service.getSharedPreferences("keyboard",0).edit().putString("previous",previous).apply();
            int result=service.getSoftKeyboardController().setInputMethodEnabled(info.getId(),true);
            setup="enable="+result;
            if(result==AccessibilityService.SoftKeyboardController.ENABLE_IME_SUCCESS) setup+=";switch="+service.getSoftKeyboardController().switchToInputMethod(info.getId());
        }
    }
    static org.json.JSONObject diagnostics(ControlService service) throws Exception {
        org.json.JSONObject result=new org.json.JSONObject().put("setup",setup).put("sdk",Build.VERSION.SDK_INT);
        result.put("selected",Settings.Secure.getString(service.getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD));
        RemoteInputService method=RemoteInputService.active;
        result.put("service",method!=null);
        if(method!=null) { InputBinding binding=method.getCurrentInputBinding(); EditorInfo editor=method.getCurrentInputEditorInfo(); result.put("connection",method.getCurrentInputConnection()!=null).put("boundUid",binding==null?-1:binding.getUid()).put("editorPackage",editor==null?"":editor.packageName); }
        return result;
    }
    static void restore(ControlService service) {
        String previous=service.getSharedPreferences("keyboard",0).getString("previous","");
        if(Build.VERSION.SDK_INT>=30 && !previous.isEmpty()) service.getSoftKeyboardController().switchToInputMethod(previous);
    }
    static boolean available(ControlService service) {
        RemoteInputService method=RemoteInputService.active;
        if(method==null || method.getCurrentInputConnection()==null) return false;
        InputBinding binding=method.getCurrentInputBinding(); AccessibilityNodeInfo root=service.getRootInActiveWindow();
        if(root==null) return false;
        try { return binding!=null && root.getPackageName()!=null && service.getPackageManager().getApplicationInfo(root.getPackageName().toString(),0).uid==binding.getUid(); }
        catch(Exception ignored) { return false; }
        finally { root.recycle(); }
    }
    static boolean send(ControlService service,String key) {
        if(!available(service)) return false;
        int code;
        switch(key) {
            case "up":code=KeyEvent.KEYCODE_DPAD_UP;break;
            case "down":code=KeyEvent.KEYCODE_DPAD_DOWN;break;
            case "left":code=KeyEvent.KEYCODE_DPAD_LEFT;break;
            case "right":code=KeyEvent.KEYCODE_DPAD_RIGHT;break;
            case "ok":code=KeyEvent.KEYCODE_DPAD_CENTER;break;
            case "menu":code=KeyEvent.KEYCODE_MENU;break;
            default:return false;
        }
        {
            InputConnection connection=RemoteInputService.active.getCurrentInputConnection();
            long now=SystemClock.uptimeMillis();
            boolean down=connection.sendKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0,0,KeyCharacterMap.VIRTUAL_KEYBOARD,0,KeyEvent.FLAG_SOFT_KEYBOARD,InputDevice.SOURCE_KEYBOARD));
            boolean up=connection.sendKeyEvent(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,code,0,0,KeyCharacterMap.VIRTUAL_KEYBOARD,0,KeyEvent.FLAG_SOFT_KEYBOARD,InputDevice.SOURCE_KEYBOARD));
            return down && up;
        }
    }
}
