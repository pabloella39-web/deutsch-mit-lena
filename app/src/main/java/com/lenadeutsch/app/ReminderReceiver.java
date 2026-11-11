package com.lenadeutsch.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Daily practice reminder: one alarm per day, re-armed after every notification, reboot and update. */
public class ReminderReceiver extends BroadcastReceiver {
    static final String ACTION = "com.lenadeutsch.app.REMIND";
    static final String CHANNEL = "lena_daily";

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        try {
            if (ACTION.equals(a)) show(c);
        } catch (Exception ignored) { }
        schedule(c);
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("lena", Context.MODE_PRIVATE);
    }

    static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION);
        int f = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, 7, i, f);
    }

    static void schedule(Context c) {
        try {
            SharedPreferences p = prefs(c);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = alarmIntent(c);
            am.cancel(pi);
            if (!p.getBoolean("rem_on", false)) return;
            Calendar t = Calendar.getInstance();
            t.set(Calendar.HOUR_OF_DAY, p.getInt("rem_h", 19));
            t.set(Calendar.MINUTE, p.getInt("rem_m", 0));
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            if (t.getTimeInMillis() <= System.currentTimeMillis() + 1000) t.add(Calendar.DAY_OF_MONTH, 1);
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
            else am.set(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
        } catch (Exception ignored) { }
    }

    private static void show(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        int due = 0, streak = 0;
        String last = "";
        try {
            JSONObject m = new JSONObject(p.getString("lena.meta", "{}"));
            due = m.optInt("due", 0);
            streak = m.optInt("streak", 0);
            last = m.optString("last", "");
        } catch (Exception ignored) { }
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String today = f.format(new Date());
        if (today.equals(last)) return; // already practised today: no nagging
        String title = "Deutsch mit Lena";
        String text;
        if (streak > 0) {
            title = "No pierdas tu racha de " + streak + (streak == 1 ? " día" : " días");
        }
        if (due > 0) text = "Tienes " + due + (due == 1 ? " palabra" : " palabras") + " para repasar. Diez minutos y listo.";
        else text = "Hoy toca alemán: diez minutos con Lena.";

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Recordatorio diario", NotificationManager.IMPORTANCE_DEFAULT));
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c);
        }
        Intent open = new Intent(c, MainActivity.class).putExtra("open", "hoy")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pf = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        b.setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
            .setContentIntent(PendingIntent.getActivity(c, 8, open, pf)).setAutoCancel(true);
        nm.notify(1, b.build());
    }
}
