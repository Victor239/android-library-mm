package com.github.axet.androidlibrary.app;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class PhoneStateChangeListener extends PhoneStateListener {
    public boolean wasRinging;

    @TargetApi(31)
    TelephonyCallback e;
    TelephonyManager tm;
    Context context;

    @TargetApi(31)
    public class TelephonyCallback extends android.telephony.TelephonyCallback implements android.telephony.TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int i) {
            PhoneStateChangeListener.this.onCallStateChanged(i, "");
        }
    }

    public PhoneStateChangeListener(Context context) {
        this.context = context;
        tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    public void create() {
        if (Build.VERSION.SDK_INT >= 31 && context.getApplicationInfo().targetSdkVersion >= 31) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                e = new TelephonyCallback();
                tm.registerTelephonyCallback(context.getMainExecutor(), e);
            }
        } else {
            tm.listen(this, android.telephony.PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    public void close() {
        if (Build.VERSION.SDK_INT >= 31 && context.getApplicationInfo().targetSdkVersion >= 31) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
                tm.unregisterTelephonyCallback(e);
        } else {
            tm.listen(this, android.telephony.PhoneStateListener.LISTEN_NONE);
        }
    }

    @Override
    public void onCallStateChanged(int s, String incomingNumber) {
        switch (s) {
            case TelephonyManager.CALL_STATE_RINGING:
                onRinging();
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                onAnswered();
                break;
            case TelephonyManager.CALL_STATE_IDLE:
                onIdle();
                break;
        }
    }

    public void onRinging() {
        wasRinging = true;
    }

    public void onAnswered() {
        wasRinging = true;
    }

    public void onIdle() {
        if (wasRinging)
            onHangup();
        wasRinging = false;
    }

    public void onHangup() { // missed call or finished answered call
    }
}
