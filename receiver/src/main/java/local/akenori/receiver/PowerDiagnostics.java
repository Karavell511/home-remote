package local.akenori.receiver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Fixed, non-user hardware configuration files only; authenticated diagnostics. */
final class PowerDiagnostics {
    static JSONObject read() throws Exception {
        JSONArray files=new JSONArray(),available=new JSONArray();
        java.util.LinkedHashSet<String> paths=new java.util.LinkedHashSet<>();
        for(String base:new String[]{"/vendor/etc","/system/etc","/odm/etc","/vendor/etc/rc_keymaps"}) {
            File[] entries=new File(base).listFiles(); if(entries==null) continue;
            java.util.Arrays.sort(entries,java.util.Comparator.comparing(File::getName));
            for(File file:entries) if(file.getName().matches("remote[^/]*\\.(tab[0-9]*|conf|cfg)")) {
                available.put(new JSONObject().put("path",file.getPath()).put("readable",file.canRead()));
                if(!file.getName().equals("remote.conf")) paths.add(file.getPath());
            }
        }
        java.util.Collections.addAll(paths,"/vendor/etc/remote.tab","/vendor/etc/remote.cfg","/vendor/etc/remote.tab1","/vendor/etc/remote.tab2","/vendor/etc/remote.tab3","/vendor/etc/remote.conf","/system/etc/remote.conf","/system/etc/remote.cfg");
        for(String path:paths) {
            File file=new File(path);
            if(!file.isFile() || !file.canRead() || file.length()>32768) continue;
            try(InputStream input=new FileInputStream(file); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[1024]; int n;
                while((n=input.read(buffer))!=-1) { if(out.size()+n>32768) break; out.write(buffer,0,n); }
                files.put(new JSONObject().put("path",path).put("content",new String(out.toByteArray(),StandardCharsets.UTF_8)));
            } catch(IOException ignored) {}
        }
        return new JSONObject().put("ok",true).put("available",available).put("files",files);
    }
}
