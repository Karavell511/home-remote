package local.akenori.receiver;
import android.content.*;
import android.content.pm.*;
import android.provider.Settings;
import java.util.*;
import org.json.*;

/** Inspect only the projector's settings application; no arbitrary package launching. */
final class ProjectorCapabilities {
    private final Context context;
    ProjectorCapabilities(Context context) { this.context=context; }
    private PackageInfo settingsPackage() throws Exception {
        PackageManager pm=context.getPackageManager(); ResolveInfo info=pm.resolveActivity(new Intent(Settings.ACTION_SETTINGS),PackageManager.MATCH_DEFAULT_ONLY);
        if(info==null || info.activityInfo==null) throw new IllegalStateException("Приложение настроек не найдено");
        return pm.getPackageInfo(info.activityInfo.packageName,PackageManager.GET_ACTIVITIES);
    }
    JSONObject read() throws Exception {
        PackageInfo info=settingsPackage(); JSONArray activities=new JSONArray();
        if(info.activities!=null) for(ActivityInfo a:info.activities) { String name=a.name.toLowerCase(Locale.ROOT); if(name.contains("focus") || name.contains("zoom") || name.contains("keystone") || name.contains("projection") || name.contains("trapez") || name.contains("display")) activities.put(new JSONObject().put("name",a.name).put("exported",a.exported).put("enabled",a.enabled).put("permission",a.permission==null?"":a.permission)); }
        return new JSONObject().put("ok",true).put("settingsPackage",info.packageName).put("activities",activities);
    }
    java.io.File settingsArchive() throws Exception {
        String name=settingsPackage().packageName;
        java.io.File archive=new java.io.File(context.getPackageManager().getApplicationInfo(name,0).sourceDir);
        if(!archive.isFile() || archive.length()>64*1024*1024) throw new IllegalStateException("Пакет настроек недоступен для диагностики");
        return archive;
    }
    JSONObject openFocus() throws Exception {
        PackageInfo info=settingsPackage(); ArrayList<ActivityInfo> matches=new ArrayList<>();
        if(info.activities!=null) for(ActivityInfo a:info.activities) {
            String name=a.name.toLowerCase(Locale.ROOT);
            if(a.enabled && a.exported && name.contains("focus") && !name.contains("test") && !name.contains("factory") && !name.contains("calib") && (a.permission==null || context.checkSelfPermission(a.permission)==PackageManager.PERMISSION_GRANTED)) matches.add(a);
        }
        if(matches.size()!=1) return new JSONObject().put("ok",false).put("message","Отдельный экран фокусировки не найден. Проверяем меню проектора.");
        ActivityInfo activity=matches.get(0); context.startActivity(new Intent().setComponent(new ComponentName(info.packageName,activity.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return new JSONObject().put("ok",true).put("message","Открыт экран фокусировки проектора");
    }
}
