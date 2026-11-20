package local.akenori.receiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.Intent;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private final Handler timer=new Handler(Looper.getMainLooper());
    private TextView state,code,updateState;
    private Button updates;
    private final Runnable refresh=new Runnable() { public void run() {
        ReceiverService service=ReceiverService.active;
        if(service==null) { state.setText(ReceiverService.shouldRun(MainActivity.this)?"Приёмник запускается…":"1. Разреши управление\nВ специальных возможностях включи «Управление пультом» и вернись сюда."); code.setText(""); }
        else if(service.server==null) { state.setText("Не удалось запустить приёмник: "+service.failure); code.setText(""); }
        else { String ip=service.wifiAddress(); boolean paired=!getSharedPreferences("pairing",0).getString("token","").isEmpty(); state.setText((ip.isEmpty()?"Подключи устройство к домашней сети по Wi-Fi или Ethernet.":"IP устройства: "+ip)+(paired?"\nТелефон сохранён. Просто открой пульт на телефоне.\nПриёмник запускается автоматически после включения.":"\nНа телефоне: Пульт → Подключить → Найти устройства.\nКод ниже нужен только для первого подключения.")); String value=ip.isEmpty()?"":service.server.pairingCode(); code.setText(value); code.setTextSize(value.matches("[0-9]{6}")?60:24); }
        try { ReceiverUpdater updater=new ReceiverUpdater(MainActivity.this); org.json.JSONObject info=updater.info(); updateState.setText("Версия "+info.getString("versionName")+" · "+info.getString("message")); updates.setText(ReceiverUpdater.confirmation!=null?"Подтвердить обновление":updater.canInstall()?"Обновления по Wi-Fi разрешены":"Разрешить обновления по Wi-Fi"); } catch(Exception ignored) {}
        timer.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(0xfff3f2ec); LinearLayout page=column(); page.setPadding(dp(32),dp(24),dp(32),dp(24)); scroll.addView(page); setContentView(scroll);
        LinearLayout heading=new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL); ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.ic_remote); logo.setContentDescription("Приёмник пульта"); heading.addView(logo,new LinearLayout.LayoutParams(dp(58),dp(58)));
        TextView title=new TextView(this); title.setText("Приёмник пульта\nТЕЛЕВИЗОР · ПРОЕКТОР"); title.setTextColor(0xff192b24); title.setTextSize(25); title.setPadding(dp(16),0,0,0); heading.addView(title,new LinearLayout.LayoutParams(0,-2,1)); page.addView(heading);
        boolean wide=getResources().getConfiguration().screenWidthDp>=700;
        LinearLayout content=new LinearLayout(this); content.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL); page.addView(content,params(-1,-2,20));
        LinearLayout pairing=column(); pairing.setPadding(dp(20),dp(18),dp(20),dp(18)); pairing.setBackground(shape(0xfffcfdf8)); LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(wide?0:-1,-2,wide?1:0); if(wide)left.rightMargin=dp(24); content.addView(pairing,left);
        LinearLayout actions=column(); content.addView(actions,new LinearLayout.LayoutParams(wide?dp(310):-1,-2));
        state=new TextView(this); state.setTextColor(0xff192b24); state.setTextSize(20); state.setLineSpacing(dp(4),1); pairing.addView(state);
        code=new TextView(this); code.setTextSize(60); code.setTextColor(0xff192b24); code.setGravity(Gravity.CENTER); pairing.addView(code,params(-1,-2,16));
        TextView hint=new TextView(this); hint.setText("Код действует 5 минут. После сопряжения телефон запоминается."); hint.setTextColor(0xff69736b); hint.setTextSize(14); pairing.addView(hint,params(-1,-2,10));
        Button allow=button("Разрешить управление",actions);
        allow.setOnClickListener(v -> { try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch(RuntimeException e) { Toast.makeText(this,"Открой настройки Android → Специальные возможности → Управление пультом",Toast.LENGTH_LONG).show(); } });
        Button renew=button("Новый код подключения",actions);
        renew.setOnClickListener(v -> {
            Runnable reset=() -> { ReceiverService s=ReceiverService.active; if(s!=null && s.server!=null) s.server.newPairing(); };
            if(getSharedPreferences("pairing",0).getString("token","").isEmpty()) reset.run();
            else new AlertDialog.Builder(this).setTitle("Заменить подключённый телефон?").setMessage("Новый код отключит сохранённый телефон. Для обычного подключения достаточно открыть на нём пульт.").setNegativeButton("Отмена",null).setPositiveButton("Заменить телефон",(dialog,which) -> reset.run()).show();
        });
        Button stop=button("Остановить управление",actions);
        stop.setOnClickListener(v -> { getSharedPreferences("receiver",0).edit().putBoolean("enabled",false).commit(); getSharedPreferences("pairing",0).edit().clear().commit(); stopService(new Intent(this,ReceiverService.class)); if(ControlService.active!=null) { NativeKeys.restore(ControlService.active); ControlService.active.disableSelf(); } });
        updates=button("Разрешить обновления по Wi-Fi",actions);
        updates.setOnClickListener(v -> { try { if(ReceiverUpdater.confirmation!=null) startActivity(new Intent(ReceiverUpdater.confirmation)); else if(!new ReceiverUpdater(this).canInstall()) new ReceiverUpdater(this).allow(); } catch(RuntimeException e) { Toast.makeText(this,"Открой в настройках Android разрешение на установку для «Приёмника пульта»",Toast.LENGTH_LONG).show(); } });
        Button instructions=button("Инструкция и совместимость",actions); instructions.setOnClickListener(v -> help());
        updateState=new TextView(this); updateState.setTextColor(0xff69736b); updateState.setTextSize(14); page.addView(updateState,params(-1,-2,16));
        TextView help=new TextView(this); help.setText("Телефон и экран должны быть в одной домашней сети. Для первой настройки подойдёт USB-мышь или обычный пульт."); help.setTextColor(0xff69736b); help.setTextSize(15); page.addView(help,params(-1,-2,8));
        if(wide)allow.requestFocus();
    }
    private void help() {
        TextView info=new TextView(this); info.setTextSize(18); info.setTextColor(0xff192b24); info.setPadding(dp(24),dp(16),dp(24),dp(16));
        info.setText("1. Установи «Домашний пульт» на Android-телефон (Android 8+).\n\n2. Подключи оба устройства к одному роутеру. Экран может быть подключён кабелем Ethernet.\n\n3. Нажми «Разрешить управление», включи службу «Управление пультом» и вернись сюда. Если Android запрещает включение, открой сведения о приложении → меню ⋮ → «Разрешить ограниченные настройки», если такой пункт доступен.\n\n4. На телефоне открой «Подключить» и введи IP и код с этого экрана. Новый код отключает прежний телефон.\n\n5. Для следующих обновлений разреши приёмнику установку по Wi-Fi. Обновления поступают из новой версии телефонного пульта; Android может попросить подтверждение.\n\nНавигация и звук зависят от возможностей прошивки. Прямое управление линзой и геометрией доступно на совместимых Jixin-проекторах. На прочих моделях используются их меню.\n\nЭтот APK предназначен для Android 7+, Android TV и Google TV. Он не устанавливается на Samsung Tizen или LG webOS. Выключенное устройство приложение не включает.\n\nСлужба читает доступные элементы экрана и выполняет команды сопряжённого телефона. «Остановить управление» выключает службу и отзывает доступ.");
        ScrollView scroll=new ScrollView(this); scroll.addView(info); new AlertDialog.Builder(this).setTitle("Как подключить телефон").setView(scroll).setPositiveButton("Понятно",null).show();
    }
    private Button button(String text,LinearLayout target) { Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(17); b.setTextColor(0xff192b24); b.setPadding(dp(12),0,dp(12),0); StateListDrawable states=new StateListDrawable(); states.addState(new int[]{android.R.attr.state_pressed},shape(0xffc9e79a)); states.addState(new int[]{android.R.attr.state_focused},shape(0xffc9e79a)); states.addState(new int[]{},shape(0xffe4e8df)); b.setBackground(states); b.setFocusable(true); target.addView(b,params(-1,52,8)); return b; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private GradientDrawable shape(int color) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(16)); return d; }
    private LinearLayout.LayoutParams params(int w,int h,int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)); p.topMargin=dp(top); return p; }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    @Override protected void onResume() { super.onResume(); ReceiverService.start(this); timer.removeCallbacks(refresh); timer.post(refresh); }
    @Override protected void onPause() { timer.removeCallbacksAndMessages(null); super.onPause(); }
}
