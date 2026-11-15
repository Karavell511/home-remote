package local.akenori.receiver;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.util.*;

/** Native focus first, with a validated selection for menus that do not expose input focus. */
final class ScreenNavigator {
    private AccessibilityNodeInfo selection;
    void clear() { if(selection!=null) selection.recycle(); selection=null; }
    JSONObject key(AccessibilityNodeInfo root,String key) throws Exception {
        if(root==null) return answer(false,"Открой меню проектора.");
        AccessibilityNodeInfo focus=null;
        try {
            if(selection!=null && selection.refresh() && selection.isVisibleToUser() && selection.getWindowId()==root.getWindowId()) focus=AccessibilityNodeInfo.obtain(selection);
            if(focus==null) focus=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if(focus==null) focus=root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if("ok".equals(key)) {
                if(focus==null) return answer(false,"Сначала выбери пункт стрелками или открой «Экран проектора».");
                boolean ok=click(focus); return answer(ok,ok?"Пункт открыт":"Этот пункт не принимает OK. Открой «Экран проектора».");
            }
            if(focus!=null && focus.getRangeInfo()!=null && (key.equals("left") || key.equals("right"))) {
                AccessibilityNodeInfo.RangeInfo r=focus.getRangeInfo(); float step=r.getType()==AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT?1:Math.max((r.getMax()-r.getMin())/100f,0.001f);
                float value=Math.max(r.getMin(),Math.min(r.getMax(),r.getCurrent()+(key.equals("right")?step:-step)));
                Bundle args=new Bundle(); args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,value);
                boolean ok=focus.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args);
                if(ok) return answer(true,"Значение: "+value);
            }
            int direction=key.equals("up")?View.FOCUS_UP:key.equals("down")?View.FOCUS_DOWN:key.equals("left")?View.FOCUS_LEFT:View.FOCUS_RIGHT;
            AccessibilityNodeInfo next=focus==null?null:focus.focusSearch(direction);
            if(next!=null && (!next.isVisibleToUser() || !next.isEnabled())) { next.recycle(); next=null; }
            if(next==null) next=nearest(root,focus,key);
            if(next==null) return answer(false,"Дальше нет доступного пункта. Попробуй прокрутить меню.");
            next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            clear(); selection=next;
            return answer(true,"Выбрано: "+describe(next));
        } finally { if(focus!=null) focus.recycle(); root.recycle(); }
    }
    static boolean click(AccessibilityNodeInfo source) {
        AccessibilityNodeInfo current=AccessibilityNodeInfo.obtain(source); int levels=0;
        while(current!=null) {
            boolean ok=current.isEnabled() && current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            AccessibilityNodeInfo parent=!ok && ++levels<6?current.getParent():null;
            current.recycle(); if(ok) return true; current=parent;
        }
        return false;
    }
    private AccessibilityNodeInfo nearest(AccessibilityNodeInfo root,AccessibilityNodeInfo focus,String key) {
        Rect origin=new Rect(); if(focus!=null) focus.getBoundsInScreen(origin);
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>(); queue.add(AccessibilityNodeInfo.obtain(root));
        AccessibilityNodeInfo best=null; double score=Double.MAX_VALUE; int count=0;
        while(!queue.isEmpty()) {
            AccessibilityNodeInfo node=queue.remove();
            if(++count<=400) {
                Rect b=new Rect(); node.getBoundsInScreen(b);
                if(node.isVisibleToUser() && node.isEnabled() && !node.isPassword() && !b.isEmpty() && (node.isClickable() || node.getRangeInfo()!=null || (node.isFocusable() && !node.isScrollable()))) {
                    double s=Double.MAX_VALUE;
                    if(origin.isEmpty()) s=Math.max(0,b.top)*10000.0+Math.max(0,b.left);
                    else {
                        float dx=b.centerX()-origin.centerX(),dy=b.centerY()-origin.centerY();
                        float forward=key.equals("up")?-dy:key.equals("down")?dy:key.equals("left")?-dx:dx;
                        float cross=(key.equals("up") || key.equals("down"))?Math.abs(dx):Math.abs(dy);
                        if(forward>1) s=forward+cross*2.5;
                    }
                    if(s<score) { if(best!=null) best.recycle(); best=AccessibilityNodeInfo.obtain(node); score=s; }
                }
                for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo child=node.getChild(i); if(child!=null) queue.add(child); }
            }
            node.recycle();
        }
        return best;
    }
    private String describe(AccessibilityNodeInfo node) {
        if(node.getText()!=null && node.getText().length()>0) return node.getText().toString();
        if(node.getContentDescription()!=null && node.getContentDescription().length()>0) return node.getContentDescription().toString();
        for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo child=node.getChild(i); if(child==null) continue; String text=child.getText()==null?"":child.getText().toString(); child.recycle(); if(!text.isEmpty()) return text; }
        return "пункт меню";
    }
    private JSONObject answer(boolean ok,String message) throws Exception { return new JSONObject().put("ok",ok).put("message",message); }
}
