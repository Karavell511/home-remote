package local.akenori.receiver;

import android.app.PendingIntent;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only accepts a newer, identically signed APK of this receiver from a paired phone. */
final class ReceiverUpdater {
    static final int MAX_APK=8*1024*1024;
    private static final AtomicBoolean uploading=new AtomicBoolean();
    static volatile Intent confirmation;
    private final Context context;
    ReceiverUpdater(Context context) { this.context=context.getApplicationContext(); }
    private android.content.SharedPreferences prefs() { return context.getSharedPreferences("updates",0); }
    static long version(PackageInfo info) { return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode; }
    JSONObject info() throws Exception {
        PackageInfo own=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
        return new JSONObject().put("versionCode",version(own)).put("versionName",own.versionName)
            .put("canInstall",canInstall()).put("state",prefs().getString("state","ready"))
            .put("message",prefs().getString("message","Обновления поступают с подключённого телефона"));
    }
    boolean canInstall() { return Build.VERSION.SDK_INT<26 || context.getPackageManager().canRequestPackageInstalls(); }
    void allow() {
        if(Build.VERSION.SDK_INT>=26) context.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    void state(String state,String message) { prefs().edit().putString("state",state).putString("message",message).commit(); }
    JSONObject receive(InputStream input,int length,String expectedHash) throws Exception {
        if(length<=0 || length>MAX_APK) throw new IllegalArgumentException("Неверный размер APK");
        if(expectedHash==null || !expectedHash.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("Нужна контрольная сумма APK");
        if(!uploading.compareAndSet(false,true)) throw new IllegalStateException("Обновление уже передаётся");
        File archive=null; int sessionId=-1;
        PackageInstaller installer=context.getPackageManager().getPackageInstaller();
        try {
            int active=prefs().getInt("session",-1);
            if(active>=0 && installer.getSessionInfo(active)!=null) throw new IllegalStateException("Дождись завершения предыдущего обновления");
            archive=File.createTempFile("receiver-update-",".apk",context.getCacheDir());
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(OutputStream file=new FileOutputStream(archive)) {
                byte[] buffer=new byte[16384]; int remaining=length;
                while(remaining>0) { int n=input.read(buffer,0,Math.min(buffer.length,remaining)); if(n<0) throw new EOFException("Передача APK оборвалась"); file.write(buffer,0,n); digest.update(buffer,0,n); remaining-=n; }
            }
            StringBuilder hash=new StringBuilder(); for(byte b:digest.digest()) hash.append(String.format(Locale.US,"%02x",b&255));
            if(!MessageDigest.isEqual(hash.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII),expectedHash.toLowerCase(Locale.US).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) throw new SecurityException("APK повреждён при передаче");
            PackageManager pm=context.getPackageManager(); int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
            PackageInfo update=pm.getPackageArchiveInfo(archive.getAbsolutePath(),flags), own=pm.getPackageInfo(context.getPackageName(),flags);
            if(update==null || !context.getPackageName().equals(update.packageName)) throw new SecurityException("Это не обновление приёмника");
            Signature[] a=signers(own), b=signers(update);
            if(a==null || b==null || a.length==0 || a.length!=b.length || !new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b)))) throw new SecurityException("Подпись обновления не совпадает");
            if(version(update)<version(own)) throw new SecurityException("Более старая версия не устанавливается");
            if(version(update)==version(own)) return new JSONObject().put("ok",true).put("state","current").put("message","Уже установлена эта версия");
            if(!canInstall()) return new JSONObject().put("ok",false).put("state","permission").put("message","Разреши обновления в приёмнике на проекторе один раз");
            PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(context.getPackageName()); params.setSize(length);
            if(Build.VERSION.SDK_INT>=31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            sessionId=installer.createSession(params);
            try(PackageInstaller.Session session=installer.openSession(sessionId); InputStream file=new FileInputStream(archive); OutputStream out=session.openWrite("base.apk",0,length)) {
                byte[] buffer=new byte[16384]; int n; while((n=file.read(buffer))!=-1) out.write(buffer,0,n); session.fsync(out);
            }
            prefs().edit().putInt("session",sessionId).putLong("targetVersion",version(update)).putString("state","installing").putString("message","Приёмник обновляется. Связь скоро восстановится.").commit();
            final int readySession=sessionId;
            // Let the authenticated HTTP acknowledgement reach the phone before replacing this process.
            new Handler(Looper.getMainLooper()).postDelayed(() -> commit(readySession),700);
            sessionId=-1;
            return new JSONObject().put("ok",true).put("state","installing").put("versionCode",version(update)).put("message","Обновление передано. Подключение восстановится автоматически.");
        } finally {
            if(archive!=null) archive.delete(); if(sessionId>=0) try { installer.abandonSession(sessionId); } catch(Exception ignored) {}
            uploading.set(false);
        }
    }
    private static Signature[] signers(PackageInfo info) { return Build.VERSION.SDK_INT>=28?(info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()):info.signatures; }
    private void commit(int sessionId) {
        try(PackageInstaller.Session session=context.getPackageManager().getPackageInstaller().openSession(sessionId)) {
            Intent callback=new Intent(context,UpdateResultReceiver.class).setAction("local.akenori.receiver.UPDATE_RESULT");
            int flags=PendingIntent.FLAG_UPDATE_CURRENT; if(Build.VERSION.SDK_INT>=31) flags|=PendingIntent.FLAG_MUTABLE;
            PendingIntent result=PendingIntent.getBroadcast(context,sessionId,callback,flags);
            session.commit(result.getIntentSender());
        } catch(Exception e) {
            try { context.getPackageManager().getPackageInstaller().abandonSession(sessionId); } catch(Exception ignored) {}
            prefs().edit().remove("session").commit(); state("failed","Не удалось установить обновление. Повтори из пульта.");
        }
    }
    void result(Intent intent) {
        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            prefs().edit().remove("session").commit(); state("installed","Приёмник обновлён по Wi-Fi"); return;
        }
        if(!"local.akenori.receiver.UPDATE_RESULT".equals(intent.getAction()) || intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-2)!=prefs().getInt("session",-1)) return;
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION) {
            confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            state("confirmation","Android просит подтвердить обновление на проекторе");
            if(confirmation!=null) try { context.startActivity(new Intent(confirmation).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch(RuntimeException ignored) {}
        } else {
            confirmation=null; prefs().edit().remove("session").commit();
            state(status==PackageInstaller.STATUS_SUCCESS?"installed":"failed",status==PackageInstaller.STATUS_SUCCESS?"Приёмник обновлён по Wi-Fi":"Android отклонил обновление (код "+status+"). Можно повторить из пульта.");
        }
    }
}
