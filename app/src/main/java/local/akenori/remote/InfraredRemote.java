package local.akenori.remote;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import local.homeremote.protocol.NecIr;
import org.json.*;

final class InfraredRemote {
    static boolean available(Context context) {
        try { ConsumerIrManager manager=(ConsumerIrManager)context.getSystemService(Context.CONSUMER_IR_SERVICE); return manager!=null && manager.hasIrEmitter(); }
        catch(RuntimeException e) { return false; }
    }
    static JSONObject profile(JSONObject diagnostics) throws JSONException {
        JSONArray files=diagnostics.optJSONArray("files"); if(files==null) return null;
        for(int i=0;i<files.length();i++) {
            int[] values=NecIr.parseAmlogic(files.getJSONObject(i).optString("content"));
            if(values!=null) return new JSONObject().put("address",values[0]).put("power",values[1]).put("home",values[2]);
        }
        return null;
    }
    static void send(Context context,JSONObject profile,String key) throws Exception {
        if(!key.equals("power") && !key.equals("home")) throw new IllegalArgumentException("Неизвестная ИК-кнопка");
        ConsumerIrManager manager=(ConsumerIrManager)context.getSystemService(Context.CONSUMER_IR_SERVICE);
        if(manager==null || !manager.hasIrEmitter()) throw new IllegalStateException("В телефоне нет ИК-передатчика");
        ConsumerIrManager.CarrierFrequencyRange[] ranges=manager.getCarrierFrequencies();
        if(ranges!=null) {
            boolean supported=false; for(ConsumerIrManager.CarrierFrequencyRange range:ranges) if(range.getMinFrequency()<=NecIr.CARRIER && range.getMaxFrequency()>=NecIr.CARRIER) supported=true;
            if(!supported) throw new IllegalStateException("ИК-передатчик не поддерживает частоту проектора");
        }
        manager.transmit(NecIr.CARRIER,NecIr.frame(profile.getInt("address"),profile.getInt(key)));
    }
}
