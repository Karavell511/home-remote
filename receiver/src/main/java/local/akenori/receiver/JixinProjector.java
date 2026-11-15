package local.akenori.receiver;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import org.json.*;
import java.io.*;

/** Contracts read from this projector's installed com.jixin.settings5 SDK. No arbitrary provider calls. */
final class JixinProjector {
    private final Context context;
    private static final java.util.concurrent.ConcurrentHashMap<String,String> authorities=new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile ClassLoader oemClasses;
    JixinProjector(Context context) { this.context=context; }
    private String authority(String service) {
        String known=authorities.get(service); if(known!=null) return known;
        Bundle query=new Bundle(); query.putString("serviceName",service);
        Bundle answer=context.getContentResolver().call(Uri.parse("content://jx.servicesmanager.provider/servicesManager"),"queryAuthority",null,query);
        String authority=answer==null?null:answer.getString("authority");
        if(authority==null || !authority.matches("[a-zA-Z0-9_.]+")) throw new IllegalStateException("Служба производителя недоступна: "+service);
        authorities.put(service,authority); return authority;
    }
    private Bundle call(String service,String path,String method,Bundle args) throws Exception {
        Bundle result=context.getContentResolver().call(Uri.parse("content://"+authority(service)+"/"+path),method,null,args);
        if(result==null) result=new Bundle();
        // The OEM returns its own Serializable ScreenPoint / Kotlin Pair. Read using its installed APK classes.
        if(oemClasses==null) oemClasses=context.createPackageContext("com.jixin.settings5",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY).getClassLoader();
        result.setClassLoader(oemClasses);
        return result;
    }
    private JSONObject point(Object value) throws Exception {
        if(value==null || !value.getClass().getName().equals("com.jixin.projector.keystone.ScreenPoint")) throw new IllegalStateException("Проектор не вернул координаты изображения");
        JSONObject point=new JSONObject();
        for(String name:new String[]{"ltX","ltY","rtX","rtY","rbX","rbY","lbX","lbY"}) point.put(name,((Number)value.getClass().getMethod("get"+Character.toUpperCase(name.charAt(0))+name.substring(1)).invoke(value)).doubleValue());
        return point;
    }
    File providerArchive(String service) throws Exception {
        if(!service.equals("FocusManualService") && !service.equals("KeystoneService")) throw new IllegalArgumentException("Неизвестная служба изображения");
        android.content.pm.ProviderInfo info=context.getPackageManager().resolveContentProvider(authority(service),0);
        if(info==null) throw new IllegalStateException("Не найден пакет службы");
        File apk=new File(info.applicationInfo.sourceDir);
        if(!apk.isFile() || apk.length()>64*1024*1024) throw new IllegalStateException("Пакет службы недоступен");
        return apk;
    }
    JSONObject state() throws Exception {
        JSONObject state=new JSONObject().put("ok",true).put("focusAuthority",authority("FocusManualService")).put("keystoneAuthority",authority("KeystoneService"));
        for(String field:new String[]{"angleByHorizontal","angleByVertical","angleByRotate"}) {
            Bundle value=call("KeystoneService","keystone",field,null);
            if(value.containsKey(field)) state.put(field,value.getInt(field));
        }
        try { state.put("points",point(call("KeystoneService","keystone","readCurrentScreenPoint",null).get("currentScreenPoint"))); }
        catch(Exception e) { state.put("pointsError",e.getClass().getSimpleName()+": "+e.getMessage()); }
        Bundle scale=call("KeystoneService","keystone","readCurrentScreenPercentage",null);
        if(scale.containsKey("key")) state.put("scale",scale.getFloat("key"));
        state.put("focusLimits",limits());
        for(String mode:new String[]{"OrthographicForward","HoistingForward","OrthographicRear","HoistingRear"}) {
            Bundle value=call("ProjectorTypeService","projectorType","is"+mode,null);
            if(value.getBoolean("is"+mode)) state.put("projection",Character.toLowerCase(mode.charAt(0))+mode.substring(1));
        }
        return state;
    }
    private JSONObject limits() throws Exception {
        Object limits=call("FocusManualService","manualfocus","isLimit",null).get("value");
        if(limits==null) return new JSONObject();
        return new JSONObject().put("minus",limits.getClass().getMethod("getFirst").invoke(limits)).put("plus",limits.getClass().getMethod("getSecond").invoke(limits));
    }
    JSONObject command(JSONObject request) throws Exception {
        String action=request.getString("action");
        JSONObject result=new JSONObject().put("ok",true);
        if(action.equals("focus_minus") || action.equals("focus_plus")) {
            if(limits().optBoolean(action.equals("focus_minus")?"minus":"plus")) return result.put("ok",false).put("message","Линза достигла края диапазона");
            Bundle answer=call("FocusManualService","manualfocus",action.equals("focus_minus")?"cw":"ccw",null);
            if(!answer.containsKey("value")) throw new IllegalStateException("Служба двигателя не ответила");
            // OEM value means limit reached, NOT success/failure (ManualFocusProvider.call).
            return result.put("limit",answer.getBoolean("value")).put("message",answer.getBoolean("value")?"Линза у края диапазона":"Шаг фокуса передан двигателю");
        }
        if(action.equals("focus_stop")) { call("FocusManualService","manualfocus","stopCWOrCCW",null); return result.put("message","Фокусировка остановлена"); }
        if(action.equals("scale_down") || action.equals("scale_up")) {
            JSONObject before=state();
            Bundle response=call("KeystoneService","keystone",action.equals("scale_down")?"reduceScreen":"enlargeScreen",null);
            // These methods perform one bounded OEM step; current points independently verify the change.
            JSONObject after=state(); result.put("before",before).put("after",after);
            boolean changed=before.has("points") && after.has("points") && !before.getJSONObject("points").toString().equals(after.getJSONObject("points").toString());
            return result.put("ok",changed).put("message",changed?"Размер изображения изменён":"Изменение не подтверждено: возможно, достигнут предел");
        }
        if(action.matches("angle_(vertical|horizontal|rotate)_(minus|plus)")) {
            String[] parts=action.split("_"); String axis=Character.toUpperCase(parts[1].charAt(0))+parts[1].substring(1);
            JSONObject before=state(); int old=before.getInt("angleBy"+axis), value=old+(parts[2].equals("plus")?1:-1);
            if(value< -45 || value>45) return result.put("ok",false).put("message","Достигнут предел регулировки 45°");
            Bundle args=new Bundle(); args.putInt("adjust"+axis,value); call("KeystoneService","keystone","adjust"+axis,args);
            JSONObject after=state(); boolean changed=after.getInt("angleBy"+axis)==value;
            return result.put("ok",changed).put("after",after).put("message",changed?"Угол: "+value+"°":"Изменение угла не подтверждено");
        }
        if(action.matches("corner_(lt|rt|rb|lb)_(left|right|up|down)")) {
            String[] parts=action.split("_"); String corner=Character.toUpperCase(parts[1].charAt(0))+parts[1].substring(1);
            String axis=parts[2].equals("left")||parts[2].equals("right")?"X":"Y";
            JSONObject before=state(); call("KeystoneService","keystone",parts[2]+"RegulaBy"+corner+axis,null); JSONObject after=state();
            boolean changed=!before.getJSONObject("points").toString().equals(after.getJSONObject("points").toString());
            return result.put("ok",changed).put("after",after).put("message",changed?"Угол изображения перемещён":"Достигнут край регулировки");
        }
        if(java.util.Arrays.asList("orthographicForward","hoistingForward","orthographicRear","hoistingRear").contains(action)) {
            call("ProjectorTypeService","projectorType",action,null); JSONObject after=state();
            return result.put("ok",action.equals(after.optString("projection"))).put("after",after).put("message","Положение изображения: "+after.optString("projection"));
        }
        throw new IllegalArgumentException("Неизвестная настройка проектора");
    }
}
