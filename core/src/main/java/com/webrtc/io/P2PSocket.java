package com.webrtc.io;

import org.json.JSONObject;

public interface P2PSocket {

    String INIT = "init";
    String OFFER = "offer";
    String ANSWER = "answer";
    String CANDIDATE = "candidate";

    String SEND_CALL = "send";
    String RECV_CALL = "receive";
    String EJECT_CALL = "eject";
    String ACCEPT_CALL = "accept";
    String CANCEL_CALL = "cancel";

    String MESSAGE_CALL = "message";
    /**
     * A -> SEND_CALL ->  B -> RECV_CALL
     * 1.   B -> EJECT_CALL -> A -> EJECT_CALL -> Over
     * 2.   B -> ACCEPT_CALL -> A -> ACCEPT_CALL -> change data -> (A\B) CANCEL_CALL -> Over
     */

    /**
     * try to send message
     *
     * @param type    the type of message
     * @param payload message content
     */
    public void emit(String type, JSONObject payload);

    public void on(String type, Listener listener);

    interface Listener {
        void call(Object... args);
    }
}