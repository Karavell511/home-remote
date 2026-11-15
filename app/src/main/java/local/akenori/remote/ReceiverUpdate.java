package local.akenori.remote;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import java.io.*;

final class ReceiverUpdate {
    final byte[] apk;
    final long versionCode;
    final String versionName;
    ReceiverUpdate(Context context) throws IOException {
        try(InputStream in=context.getAssets().open("projector-receiver.apk"); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[16384]; int n;
            while((n=in.read(buffer))!=-1) { out.write(buffer,0,n); if(out.size()>8*1024*1024) throw new IOException("APK слишком большой"); }
            apk=out.toByteArray();
        }
        File archive=File.createTempFile("receiver-version-",".apk",context.getCacheDir());
        try {
            try(OutputStream out=new FileOutputStream(archive)) { out.write(apk); }
            PackageInfo info=context.getPackageManager().getPackageArchiveInfo(archive.getAbsolutePath(),0);
            if(info==null || !"local.akenori.receiver".equals(info.packageName)) throw new IOException("В пульте нет корректного APK приёмника");
            versionCode=Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode; versionName=info.versionName;
        } finally { archive.delete(); }
    }
}
