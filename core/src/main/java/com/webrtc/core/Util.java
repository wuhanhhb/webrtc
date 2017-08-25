package com.webrtc.core;

import android.util.Log;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.webrtc.core.WebRtcClient.TAG;

/**
 * Created by Administrator on 2017/8/21.
 */

public class Util {

    /**
     * See(https://stackoverflow.com/questions/20068944/webrtc-stun-stun-l-google-com19302)
     * {url:'stun:stun01.sipphone.com'},
     * {url:'stun:stun.ekiga.net'},
     * {url:'stun:stun.fwdnet.net'},
     * {url:'stun:stun.ideasip.com'},
     * {url:'stun:stun.iptel.org'},
     * {url:'stun:stun.rixtelecom.se'},
     * {url:'stun:stun.schlund.de'},
     * {url:'stun:stun.l.google.com:19302'},
     * {url:'stun:stun1.l.google.com:19302'},
     * {url:'stun:stun2.l.google.com:19302'},
     * {url:'stun:stun3.l.google.com:19302'},
     * {url:'stun:stun4.l.google.com:19302'},
     * {url:'stun:stunserver.org'},
     * {url:'stun:stun.softjoys.com'},
     * {url:'stun:stun.voiparound.com'},
     * {url:'stun:stun.voipbuster.com'},
     * {url:'stun:stun.voipstunt.com'},
     * {url:'stun:stun.voxgratia.org'},
     * {url:'stun:stun.xten.com'},
     * {
     * url: 'turn:numb.viagenie.ca',
     * credential: 'muazkh',
     * username: 'webrtc@live.com'
     * },
     * {
     * url: 'turn:192.158.29.39:3478?transport=udp',
     * credential: 'JZEOEt2V3Qb0y27GRntt2u2PAYA=',
     * username: '28224511:1379330808'
     * },
     * {
     * url: 'turn:192.158.29.39:3478?transport=tcp',
     * credential: 'JZEOEt2V3Qb0y27GRntt2u2PAYA=',
     * username: '28224511:1379330808'
     * }
     */
    public static void configDefaultIceServers() {
        //public stun service
        WebRtcService.getInstance().getIceServers().add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        WebRtcService.getInstance().getIceServers().add(new PeerConnection.IceServer("stun:stunserver.org"));
        WebRtcService.getInstance().getIceServers().add(new PeerConnection.IceServer("stun:stun.softjoys.com"));
        WebRtcService.getInstance().getIceServers().add(new PeerConnection.IceServer("stun:stun.voiparound.com"));
        WebRtcService.getInstance().getIceServers().add(new PeerConnection.IceServer("stun:stun.voipbuster.com"));
        WebRtcService.getInstance().getIceServers().add(new PeerConnection.IceServer("stun:stun.voipstunt.com"));
        WebRtcService.getInstance().getIceServers().add(new PeerConnection.IceServer("stun:stun.voxgratia.org"));
        WebRtcService.getInstance().getIceServers().add(new PeerConnection.IceServer("stun:stun.xten.com"));
        //public turn service
        WebRtcService.getInstance().getIceServers().add(new PeerConnection.IceServer("turn:numb.viagenie.ca", "muazkh", "webrtc@live.com"));
        WebRtcService.getInstance().getIceServers().add(new PeerConnection.IceServer("turn:192.158.29.39:3478?transport=udp", "JZEOEt2V3Qb0y27GRntt2u2PAYA=", "28224511:1379330808"));
        WebRtcService.getInstance().getIceServers().add(new PeerConnection.IceServer("turn:192.158.29.39:3478?transport=tcp", "JZEOEt2V3Qb0y27GRntt2u2PAYA=", "28224511:1379330808"));
    }

    /**
     * Returns the line number containing "m=audio|video", or -1 if no such line exists.
     */
    public static int findMediaDescriptionLine(boolean isAudio, String[] sdpLines) {
        final String mediaDescription = isAudio ? "m=audio " : "m=video ";
        for (int i = 0; i < sdpLines.length; ++i) {
            if (sdpLines[i].startsWith(mediaDescription)) {
                return i;
            }
        }
        return -1;
    }

    public static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
        final String[] lines = sdpDescription.split("\r\n");
        final int mLineIndex = findMediaDescriptionLine(isAudio, lines);
        if (mLineIndex == -1) {
            Log.w(TAG, "No mediaDescription line, so can't prefer " + codec);
            return sdpDescription;
        }
        // A list with all the payload types with name |codec|. The payload types are integers in the
        // range 96-127, but they are stored as strings here.
        final List<String> codecPayloadTypes = new ArrayList<>();
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        final Pattern codecPattern = Pattern.compile("^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$");
        for (String line : lines) {
            Matcher codecMatcher = codecPattern.matcher(line);
            if (codecMatcher.matches()) {
                codecPayloadTypes.add(codecMatcher.group(1));
            }
        }
        if (codecPayloadTypes.isEmpty()) {
            Log.w(TAG, "No payload types with name " + codec);
            return sdpDescription;
        }

        final String newMLine = movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex]);
        if (newMLine == null) {
            return sdpDescription;
        }
        Log.d(TAG, "Change media description from: " + lines[mLineIndex] + " to " + newMLine);
        lines[mLineIndex] = newMLine;
        return joinString(Arrays.asList(lines), "\r\n", true /* delimiterAtEnd */);
    }

    private static String movePayloadTypesToFront(List<String> preferredPayloadTypes, String mLine) {
// The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
        final List<String> origLineParts = Arrays.asList(mLine.split(" "));
        if (origLineParts.size() <= 3) {
            Log.e(TAG, "Wrong SDP media description format: " + mLine);
            return null;
        }
        final List<String> header = origLineParts.subList(0, 3);
        final List<String> unpreferredPayloadTypes =
                new ArrayList<String>(origLineParts.subList(3, origLineParts.size()));
        unpreferredPayloadTypes.removeAll(preferredPayloadTypes);
        // Reconstruct the line with |preferredPayloadTypes| moved to the beginning of the payload
        // types.
        final List<String> newLineParts = new ArrayList<String>();
        newLineParts.addAll(header);
        newLineParts.addAll(preferredPayloadTypes);
        newLineParts.addAll(unpreferredPayloadTypes);
        return joinString(newLineParts, " ", false /* delimiterAtEnd */);
    }

    private static String joinString(
            Iterable<? extends CharSequence> s, String delimiter, boolean delimiterAtEnd) {
        Iterator<? extends CharSequence> iter = s.iterator();
        if (!iter.hasNext()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder(iter.next());
        while (iter.hasNext()) {
            buffer.append(delimiter).append(iter.next());
        }
        if (delimiterAtEnd) {
            buffer.append(delimiter);
        }
        return buffer.toString();
    }

    private static String setStartBitrate(
            String codec, boolean isVideoCodec, String sdpDescription, int bitrateKbps) {
        String[] lines = sdpDescription.split("\r\n");
        int rtpmapLineIndex = -1;
        boolean sdpFormatUpdated = false;
        String codecRtpMap = null;
        // Search for codec rtpmap in format
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                rtpmapLineIndex = i;
                break;
            }
        }
        if (codecRtpMap == null) {
            Log.w(TAG, "No rtpmap for " + codec + " codec");
            return sdpDescription;
        }
        Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex]);

        // Check if a=fmtp string already exist in remote SDP for this codec and
        // update it with new bitrate parameter.
        regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$";
        codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                Log.d(TAG, "Found " + codec + " " + lines[i]);
                if (isVideoCodec) {
                    lines[i] += "; " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    lines[i] += "; " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
                }
                Log.d(TAG, "Update remote SDP line: " + lines[i]);
                sdpFormatUpdated = true;
                break;
            }
        }

        StringBuilder newSdpDescription = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            newSdpDescription.append(lines[i]).append("\r\n");
            // Append new a=fmtp line if no such line exist for a codec.
            if (!sdpFormatUpdated && i == rtpmapLineIndex) {
                String bitrateSet;
                if (isVideoCodec) {
                    bitrateSet =
                            "a=fmtp:" + codecRtpMap + " " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    bitrateSet = "a=fmtp:" + codecRtpMap + " " + AUDIO_CODEC_PARAM_BITRATE + "="
                            + (bitrateKbps * 1000);
                }
                Log.d(TAG, "Add remote SDP line: " + bitrateSet);
                newSdpDescription.append(bitrateSet).append("\r\n");
            }
        }
        return newSdpDescription.toString();
    }

    private static final String VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate";
    private static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";

//    /**
//     * @param sdpDescription
//     * @param codec
//     * @param isAudio
//     * @return
//     */
//    private static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
//        String[] lines = sdpDescription.split("\r\n");
//        int mLineIndex = -1;
//        String codecRtpMap = null;
//        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
//        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
//        Pattern codecPattern = Pattern.compile(regex);
//        String mediaDescription = "m=video ";
//        if (isAudio) {
//            mediaDescription = "m=audio ";
//        }
//        for (int i = 0; (i < lines.length) && (mLineIndex == -1 || codecRtpMap == null); i++) {
//            if (lines[i].startsWith(mediaDescription)) {
//                mLineIndex = i;
//                continue;
//            }
//            Matcher codecMatcher = codecPattern.matcher(lines[i]);
//            if (codecMatcher.matches()) {
//                codecRtpMap = codecMatcher.group(1);
//                continue;
//            }
//        }
//        if (mLineIndex == -1) {
//            Log.w(TAG, "No " + mediaDescription + " line, so can't prefer " + codec);
//            return sdpDescription;
//        }
//        if (codecRtpMap == null) {
//            Log.w(TAG, "No rtpmap for " + codec);
//            return sdpDescription;
//        }
//        Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + ", prefer at " + lines[mLineIndex]);
//        String[] origMLineParts = lines[mLineIndex].split(" ");
//        if (origMLineParts.length > 3) {
//            StringBuilder newMLine = new StringBuilder();
//            int origPartIndex = 0;
//            // Format is: m=<media> <port> <proto> <fmt> ...
//            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
//            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
//            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
//            newMLine.append(codecRtpMap);
//            for (; origPartIndex < origMLineParts.length; origPartIndex++) {
//                if (!origMLineParts[origPartIndex].equals(codecRtpMap)) {
//                    newMLine.append(" ").append(origMLineParts[origPartIndex]);
//                }
//            }
//            lines[mLineIndex] = newMLine.toString();
//            Log.d(TAG, "Change media description: " + lines[mLineIndex]);
//        } else {
//            Log.e(TAG, "Wrong SDP media description format: " + lines[mLineIndex]);
//        }
//        StringBuilder newSdpDescription = new StringBuilder();
//        for (String line : lines) {
//            newSdpDescription.append(line).append("\r\n");
//        }
//        return newSdpDescription.toString();
//    }
}
