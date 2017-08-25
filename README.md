# webrtc
a lib for android webrtc,only use for p2p,you maybe could change it for metting.

https://jitpack.io/#wuhanhhb/webrtc/-SNAPSHOT

# how to use it
First,you should had a service(such as signaling service or IM service) for exchange data before connected, and had STUN or TURN servers for ICE.   
Add jcenter library:   
```java
  compile 'com.follow:webrtc-android:1.1.4'   
```
  1. start service for webrtc:    
```java
    //start service first.
    WebRtcService.startServie(this);
```
    you can through next to juge the service is running:    
```java
    WebRtcService.isRunning();
```
  2. you should implement SimpleP2PSocket use for exchange data    
```java
    SimpleP2PSocket p2PSocket;
    p2PSocket = new SimpleP2PSocket() {
                @Override
                public void emit(String type, JSONObject payload) {
                    //you should send type & payload to others
                    //send message by service (such as signaling service or IM service)
            };
    //config network exchange relay
    WebRtcService.configNetWork(p2PSocket);
    //config ice service for nat
    WebRtcService.addIceService(new PeerConnection.IceServer("stun:stun.schlund.de"));
    WebRtcService.addIceService(new PeerConnection.IceServer("turn:numb.viagenie.ca", "muazkh", "webrtc@live.com"));
```
  3. when you receive data from other,you should process it for webrtc,is very simple:   
```java
    //get type & payload 
    final String type=xxxxx;
    final JSONObject payload=xxxx;
    //call
    p2PSocket.call(type, payload);
```
  4. how to start call ? it's very easy:   
```java
    //start call
    WebRtcService.startClient(getApplicationContext(), yourselfName, otherName, 0);
```
  5. it's Over!
