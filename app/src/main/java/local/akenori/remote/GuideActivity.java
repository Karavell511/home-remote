package local.akenori.remote;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** The same offline instructions are available before pairing and from the remote. */
public class GuideActivity extends Activity {
    private static final int BG=0xfff3f2ec,INK=0xff192b24,MUTED=0xff69736b,LIME=0xffc9e79a;
    private LinearLayout page;
    private Button tv,projector;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(BG); page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(24),dp(28),dp(24),dp(28)); scroll.addView(page); setContentView(scroll);
        ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.ic_remote); logo.setContentDescription("Логотип Домашнего пульта"); page.addView(logo,params(80,80,0));
        label("Домашний пульт",32,INK,16);label("Телефон управляет. Большой экран отвечает.",17,MUTED,8);
        label("Что подключаем?",21,INK,26);
        LinearLayout choices=new LinearLayout(this); choices.setOrientation(LinearLayout.HORIZONTAL); page.addView(choices,params(-1,58,12));
        tv=new Button(this); tv.setText("Телевизор"); projector=new Button(this); projector.setText("Проектор");
        for(Button b:new Button[]{tv,projector}) { b.setAllCaps(false); b.setTextSize(17); b.setTextColor(INK); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1); p.setMargins(0,0,dp(8),0); choices.addView(b,p); }
        tv.setOnClickListener(v -> choose("tv")); projector.setOnClickListener(v -> choose("projector")); refreshChoice();
        label("01  Одна домашняя сеть",21,INK,26);
        label("Подключи телефон к Wi-Fi. Телевизор или проектор подключи к тому же роутеру по Wi-Fi или сетевому кабелю. Гостевая сеть с изоляцией устройств не подойдёт.",16,MUTED,8);
        label("02  Установи приёмник",21,INK,24);
        label("На большом экране нужен Android 7 или новее, Android TV либо Google TV с возможностью установки APK. Установи сборку «Приёмник пульта» и открой её. Если родного пульта нет, можно использовать USB-мышь.",16,MUTED,8);
        action("Передать приёмник с телефона",() -> startActivity(new Intent(this,InstallReceiverActivity.class)),false);
        label("03  Разреши управление",21,INK,24);
        label("В приёмнике нажми «Разрешить управление». В специальных возможностях включи «Управление пультом» и подтверди. В старой версии служба называется «Управление проектором». Вернись в приёмник: появятся IP и код из 6 цифр.",16,MUTED,8);
        label("Если Android пишет «Ограниченная настройка», открой системные настройки → Приложения → Приёмник пульта → меню ⋮ → Разрешить ограниченные настройки. Названия зависят от прошивки. Включай доступ только для установленного тобой приёмника.",14,MUTED,10);
        label("04  Подключись один раз",21,INK,24);
        label("Нажми «Открыть пульт» → «Подключить». Приложение найдёт IP в домашней сети. Введи код с большого экрана один раз. Дальше пульт узнаёт устройство и подключается сам, даже если IP изменился. Код действует 5 минут. Новый код отзывает доступ прежнего телефона.",16,MUTED,8);
        action("Открыть пульт",() -> { getSharedPreferences("remote_ui",0).edit().putBoolean("onboarding_done",true).apply(); startActivity(new Intent(this,ReceiverActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); },true);
        label("Что будет доступно",21,INK,26);
        label("Стрелки, OK, назад, домой, громкость и чтение доступных меню Android. Реакция на кнопки зависит от приложения и прошивки. Фокус линзы, масштаб и коррекция напрямую доступны на совместимых проекторах Jixin; на других моделях используется их меню. У телевизора нет моторного фокуса.",16,MUTED,8);
        label("Samsung Tizen, LG webOS и устройства без Android не устанавливают этот APK. Для них можно проверить Bluetooth-клавиатуру, если её поддерживает сам телевизор и телефон; совместимость не гарантируется. Android-приставка управляется отдельно от телевизора.",16,MUTED,10);
        action("Bluetooth и другие способы",() -> startActivity(new Intent(this,MainActivity.class).putExtra("legacy",true)),false);
        label("Обновления и помощь",21,INK,26);
        label("В приёмнике один раз разреши обновления по Wi-Fi. Когда установишь новую версию пульта на телефон, он предложит или автоматически передаст новый приёмник при подключении. Android может потребовать подтверждение. Компьютер и USB после настройки не нужны.",16,MUTED,8);
        label("Нет связи: оставь пульт открытым, он повторит подключение сам. Проверь общую сеть и службу управления. Если VPN или гостевая сеть мешают поиску, разреши локальную сеть или введи IP вручную. Кнопка не действует: попробуй «Экран устройства». Выключенный телевизор приложение включать не умеет.",16,MUTED,10);
    }
    private void choose(String type) { getSharedPreferences("remote_ui",0).edit().putString("device_kind",type).apply(); refreshChoice(); }
    private void refreshChoice() { boolean isTv=getSharedPreferences("remote_ui",0).getString("device_kind","projector").equals("tv"); tv.setSelected(isTv); projector.setSelected(!isTv); tv.setBackground(shape(isTv?LIME:0xffe4e8df)); projector.setBackground(shape(isTv?0xffe4e8df:LIME)); }
    private void action(String text,Runnable run,boolean primary) { Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(17); b.setTextColor(primary?BG:INK); b.setBackground(shape(primary?INK:0xffe4e8df)); b.setOnClickListener(v -> run.run()); page.addView(b,params(-1,58,16)); }
    private void label(String text,int size,int color,int top) { TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(3),1); if(size>=21)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); page.addView(t,params(-1,-2,top)); }
    private GradientDrawable shape(int color) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(16)); return d; }
    private LinearLayout.LayoutParams params(int w,int h,int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)); p.topMargin=dp(top); return p; }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
}
