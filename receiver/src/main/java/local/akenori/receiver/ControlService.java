package local.akenori.receiver;

import android.accessibilityservice.*;
import android.content.*;
import android.graphics.*;
import android.media.AudioManager;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.*;
import org.json.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ControlService extends AccessibilityService {
    static volatile ControlService active;
    volatile LocalServer server;
    private DiscoveryServer discovery;
    volatile String failure="";
    private String currentWindow="";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScreenNavigator navigator=new ScreenNavigator();
    @Override protected void onServiceConnected() {
        active=this;
        try { NativeKeys.enable(this); } catch(RuntimeException e) { NativeKeys.setup=e.getClass().getSimpleName(); }
        try { server=new LocalServer(this); server.start(); } catch(Exception e) { failure=e.toString(); }
        if(server!=null) try { discovery=new DiscoveryServer(this,server); discovery.start(); } catch(Exception ignored) {}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) { if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) { navigator.clear(); currentWindow=String.valueOf(e.getPackageName())+"/"+e.getClassName(); } }
    @Override public void onInterrupt() {}
    @Override public void onDestroy() { navigator.clear(); if(discovery!=null) discovery.close(); if(server!=null) server.close(); active=null; super.onDestroy(); }
    String wifiAddress() {
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        for(Network n:cm.getAllNetworks()) {
            NetworkCapabilities caps=cm.getNetworkCapabilities(n); LinkProperties links=cm.getLinkProperties(n);
            if(caps==null || links==null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue;
            for(LinkAddress a:links.getLinkAddresses()) if(a.getAddress() instanceof Inet4Address && a.getAddress().isSiteLocalAddress()) return a.getAddress().getHostAddress();
        }
        return "";
    }
    JSONObject call(String route,JSONObject request) throws Exception {
        FutureTask<JSONObject> task=new FutureTask<>(() -> perform(route,request)); main.post(task);
        try { return task.get(5,TimeUnit.SECONDS); } catch(TimeoutException e) { task.cancel(false); throw e; }
    }
    private JSONObject perform(String route,JSONObject request) throws Exception {
        JSONObject result=new JSONObject();
        if("/projector/state".equals(route)) return new JixinProjector(this).state();
        if("/projector/control".equals(route)) return new JixinProjector(this).command(request);
        if("/capabilities".equals(route)) return new ProjectorCapabilities(this).read();
        if("/open_focus".equals(route)) { navigator.clear(); return new ProjectorCapabilities(this).openFocus(); }
        if("/status".equals(route)) {
            JSONObject update=new ReceiverUpdater(this).info();
            return result.put("ok",true).put("model",Build.MODEL).put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT).put("accessibility",true).put("receiverVersion",update.getString("versionName")).put("update",update).put("nativeKeys",NativeKeys.available(this)).put("keyboard",NativeKeys.diagnostics(this)).put("window",currentWindow);
        }
        if("/keyboard/setup".equals(route)) {
            NativeKeys.enable(this);
            if(Build.VERSION.SDK_INT<33) startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return result.put("ok",true).put("keyboard",NativeKeys.diagnostics(this));
        }
        if("/keyboard/select".equals(route)) { ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker(); return result.put("ok",true); }
        if("/update/allow".equals(route)) { new ReceiverUpdater(this).allow(); return result.put("ok",true).put("message","На проекторе включи «Разрешить из этого источника», затем вернись в пульт."); }
        if("/ui".equals(route)) {
            AccessibilityNodeInfo root=getRootInActiveWindow(); JSONArray items=new JSONArray();
            if(root==null) return result.put("ok",false).put("error","Экран пока недоступен. Открой меню проектора.");
            result.put("package",String.valueOf(root.getPackageName()));
            ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>(); queue.add(root); int visited=0;
            while(!queue.isEmpty()) {
                AccessibilityNodeInfo n=queue.remove();
                if(++visited<=400 && items.length()<100) {
                    String label=nodeLabel(n);
                    Rect bounds=new Rect(); n.getBoundsInScreen(bounds);
                    if(!n.isPassword() && n.isVisibleToUser() && n.isEnabled() && !label.trim().isEmpty() && !bounds.isEmpty()) {
                        JSONObject item=new JSONObject().put("label",label.substring(0,Math.min(160,label.length()))).put("x",bounds.centerX()).put("y",bounds.centerY());
                        if(n.isFocused() || n.isAccessibilityFocused()) item.put("focused",true);
                        item.put("clickable",n.isClickable());
                        if(n.isCheckable()) item.put("checked",n.isChecked());
                        if(n.isSelected()) item.put("selected",true);
                        if(Build.VERSION.SDK_INT>=30 && n.getStateDescription()!=null) item.put("state",n.getStateDescription().toString());
                        AccessibilityNodeInfo.RangeInfo range=n.getRangeInfo();
                        if(range!=null) item.put("value",range.getCurrent()).put("min",range.getMin()).put("max",range.getMax()).put("step",range.getType()==AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT?1:Math.max((range.getMax()-range.getMin())/100f,0.001f));
                        items.put(item);
                    }
                    if(visited<=400) for(int i=0;i<n.getChildCount();i++) { AccessibilityNodeInfo child=n.getChild(i); if(child!=null) queue.add(child); }
                }
                n.recycle();
            }
            return result.put("ok",true).put("items",items);
        }
        if("/range".equals(route)) {
            AccessibilityNodeInfo target=findTarget(request.getString("label"),request.getInt("x"),request.getInt("y"));
            if(target==null) return result.put("ok",false).put("message","Обнови экран: регулятор изменился.");
            try {
                AccessibilityNodeInfo.RangeInfo range=target.getRangeInfo(); double value=request.getDouble("value");
                if(range==null || Double.isNaN(value) || Double.isInfinite(value) || value<range.getMin() || value>range.getMax()) throw new IllegalArgumentException("Значение вне диапазона регулятора");
                Bundle args=new Bundle(); args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,(float)value);
                boolean ok=target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args);
                return result.put("ok",ok).put("message",ok?"Значение передано регулятору":"Этот регулятор не принимает изменение значения. Используй кнопки меню.");
            } finally { target.recycle(); }
        }
        if("/tap".equals(route)) {
            int x=request.getInt("x"),y=request.getInt("y"); android.util.DisplayMetrics display=getResources().getDisplayMetrics();
            if(x<0 || y<0 || x>=display.widthPixels || y>=display.heightPixels) throw new IllegalArgumentException("Координаты за пределами экрана");
            JSONArray current=perform("/ui",new JSONObject()).optJSONArray("items"); boolean matches=false;
            if(current!=null) for(int i=0;i<current.length();i++) { JSONObject item=current.getJSONObject(i); if(item.getInt("x")==x && item.getInt("y")==y && item.getString("label").equals(request.optString("label"))) matches=true; }
            if(!matches) return result.put("ok",false).put("message","Экран изменился. Обнови список элементов перед нажатием.");
            navigator.clear();
            AccessibilityNodeInfo target=findTarget(request.getString("label"),x,y);
            if(target==null) return result.put("ok",false).put("message","Экран изменился. Обнови список элементов перед нажатием.");
            boolean clicked=false; int levels=0;
            while(target!=null) {
                if(target.isEnabled() && target.isClickable()) clicked=target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                AccessibilityNodeInfo parent=!clicked && ++levels<6?target.getParent():null;
                target.recycle(); target=parent;
            }
            if(clicked) return result.put("ok",true).put("method","accessibility_click").put("message","Элемент меню принял нажатие.");
            Path path=new Path(); path.moveTo(x,y);
            boolean accepted=dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,70)).build(),null,null);
            return result.put("ok",accepted).put("method","gesture").put("message",accepted?"Нажатие отправлено. Проверь реакцию проектора.":"Android не принял нажатие.");
        }
        if(!"/key".equals(route)) throw new IllegalArgumentException("Неизвестная команда");
        String key=request.getString("key"); boolean ok=false;
        if(Arrays.asList("home","back","settings","scroll_down","scroll_up").contains(key)) navigator.clear();
        if(key.equals("home")) ok=performGlobalAction(GLOBAL_ACTION_HOME);
        else if(key.equals("back")) ok=performGlobalAction(GLOBAL_ACTION_BACK);
        else if(key.equals("settings")) { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); ok=true; }
        else if(key.equals("volume_down") || key.equals("volume_up")) {
            ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC,key.equals("volume_up")?AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI); ok=true;
        } else if(key.equals("scroll_down") || key.equals("scroll_up")) {
            AccessibilityNodeInfo root=getRootInActiveWindow(); ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>(); if(root!=null) queue.add(root); int visited=0;
            while(!queue.isEmpty()) {
                AccessibilityNodeInfo node=queue.remove();
                if(!ok && node.isVisibleToUser() && node.isScrollable()) ok=node.performAction(key.equals("scroll_down")?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                if(!ok && ++visited<400) for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo child=node.getChild(i); if(child!=null) queue.add(child); }
                node.recycle();
            }
        } else if(Arrays.asList("up","down","left","right","ok","menu").contains(key)) {
            if(NativeKeys.global(this,key)) { navigator.clear(); return result.put("ok",true).put("method","system_key").put("message","Нажатие пульта передано системе"); }
            if(NativeKeys.send(this,key)) { navigator.clear(); return result.put("ok",true).put("method","native_key").put("message","Клавиша передана приложению проектора"); }
            if(key.equals("menu")) return result.put("ok",false).put("message","Текущее приложение не открыло канал клавиш");
            return navigator.key(getRootInActiveWindow(),key);
        } else throw new IllegalArgumentException("Кнопка не поддерживается");
        return result.put("ok",ok).put("message",ok?"Команда передана Android. Проверь реакцию проектора.":"Меню не приняло кнопку. Используй список «Элементы экрана».");
    }
    private AccessibilityNodeInfo findTarget(String label,int x,int y) {
        AccessibilityNodeInfo root=getRootInActiveWindow(),found=null; if(root==null) return null;
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>(); queue.add(root); int visited=0;
        while(!queue.isEmpty()) {
            AccessibilityNodeInfo node=queue.remove();
            if(found==null && ++visited<=400) {
                String text=nodeLabel(node);
                Rect bounds=new Rect(); node.getBoundsInScreen(bounds);
                if(!node.isPassword() && node.isEnabled() && node.isVisibleToUser() && bounds.centerX()==x && bounds.centerY()==y && text.substring(0,Math.min(160,text.length())).equals(label)) found=AccessibilityNodeInfo.obtain(node);
                if(found==null) for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo child=node.getChild(i); if(child!=null) queue.add(child); }
            }
            node.recycle();
        }
        return found;
    }
    private String nodeLabel(AccessibilityNodeInfo node) {
        String text=node.getText()!=null?node.getText().toString():node.getContentDescription()!=null?node.getContentDescription().toString():"";
        if(text.trim().isEmpty() && node.getRangeInfo()!=null) text="Регулятор";
        return text;
    }
}
