package com.webrtc.io;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

/**
 * Created by Administrator on 2017/8/22.
 */

public abstract class SimpleP2PSocket implements P2PSocket {
    private static final String TAG = "SimpleP2PSocket";
    final private HashMap<String, Listener> maps = new HashMap<>();

    //send
    @Override
    public abstract void emit(String type, JSONObject payload);

    @Override
    public void on(String type, Listener listener) {
        maps.put(type, listener);
    }

    //recv
    public void callBytes(String type, byte[] data) {
        if (data == null) {
            call(type, null);
            return;
        }
        final String s = new String(data);
        try {
            JSONObject payload = new JSONObject(s);
            call(type, payload);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //recv
    public void call(String type, JSONObject payload) {
        P2PSocket.Listener listener = maps.get(type);
        if (listener != null) {
            listener.call(payload);
        } else {
            Log.e(TAG, "failed to find listener for type : " + type);
        }
    }
}
