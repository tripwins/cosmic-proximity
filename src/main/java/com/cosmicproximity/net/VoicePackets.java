package com.cosmicproximity.net;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Inner (post-decryption) UDP packet format. All multi-byte fields are big-endian.
 *
 * Wire layouts:
 *   HELLO:      [1 byte type=0][16 bytes session UUID]
 *   VOICE:      [1 byte type=1][16 bytes sender UUID][4 bytes seq][8 bytes timestampMs]
 *               [8 bytes x][8 bytes y][8 bytes z]
 *               [2 bytes audioLen][audioLen bytes encoded audio]
 *   KEEPALIVE:  [1 byte type=2][8 bytes timestampMs]
 *   DISCONNECT: [1 byte type=3]
 */
public final class VoicePackets {
    public static final byte HELLO = 0;
    public static final byte VOICE = 1;
    public static final byte KEEPALIVE = 2;
    public static final byte DISCONNECT = 3;
    public static final byte POSITION = 4;
    public static final byte REGISTER = 5;
    public static final byte MUTED_STATUS = 6;
    public static final byte PARTICIPANT_LIST = 7;
    public static final byte VISIBLE_PLAYERS = 8;

    /** Special senderId for relay TTS broadcasts — clients render at full volume regardless of distance. */
    public static final UUID BROADCAST_UUID = UUID.fromString("b0adca57-0000-0000-0000-000000000000");

    public record Hello(UUID sessionId) {}
    public record Voice(UUID senderId, int sequence, long timestampMs,
                        double x, double y, double z, byte[] audio) {}
    public record Keepalive(long timestampMs) {}
    public record Position(long timestampMs, double x, double y, double z, int dimensionHash, int roomHash) {}
    public record Register(UUID playerUuid, String playerName, int dimensionHash, int roomHash) {}
    /** expiryMillis: 0 = not muted, Long.MAX_VALUE = permanent, other positive = mute expiry timestamp */
    public record MutedStatus(long expiryMillis) {}
    /** List of player UUIDs currently connected to the relay (i.e., have the mod). */
    public record ParticipantList(java.util.List<UUID> uuids) {}
    /** UUIDs in the client's local entity list — used by the relay for realm isolation. */
    public record VisiblePlayers(java.util.List<UUID> uuids) {}

    public static byte peekType(byte[] data) {
        if (data.length < 1) throw new IllegalArgumentException("empty packet");
        return data[0];
    }

    // ------- HELLO -------
    public static byte[] encodeHello(UUID sessionId) {
        ByteBuffer bb = ByteBuffer.allocate(1 + 16);
        bb.put(HELLO);
        bb.putLong(sessionId.getMostSignificantBits());
        bb.putLong(sessionId.getLeastSignificantBits());
        return bb.array();
    }

    public static Hello decodeHello(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        byte type = bb.get();
        if (type != HELLO) throw new IllegalArgumentException("not a HELLO");
        long hi = bb.getLong();
        long lo = bb.getLong();
        return new Hello(new UUID(hi, lo));
    }

    // ------- VOICE -------
    public static byte[] encodeVoice(UUID sender, int sequence, long timestampMs,
                                     double x, double y, double z, byte[] audio) {
        if (audio.length > 0xFFFF) throw new IllegalArgumentException("audio too large");
        ByteBuffer bb = ByteBuffer.allocate(1 + 16 + 4 + 8 + 8 + 8 + 8 + 2 + audio.length);
        bb.put(VOICE);
        bb.putLong(sender.getMostSignificantBits());
        bb.putLong(sender.getLeastSignificantBits());
        bb.putInt(sequence);
        bb.putLong(timestampMs);
        bb.putDouble(x);
        bb.putDouble(y);
        bb.putDouble(z);
        bb.putShort((short) audio.length);
        bb.put(audio);
        return bb.array();
    }

    public static Voice decodeVoice(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        byte type = bb.get();
        if (type != VOICE) throw new IllegalArgumentException("not a VOICE");
        long hi = bb.getLong();
        long lo = bb.getLong();
        UUID sender = new UUID(hi, lo);
        int seq = bb.getInt();
        long ts = bb.getLong();
        double x = bb.getDouble();
        double y = bb.getDouble();
        double z = bb.getDouble();
        int len = Short.toUnsignedInt(bb.getShort());
        byte[] audio = new byte[len];
        bb.get(audio);
        return new Voice(sender, seq, ts, x, y, z, audio);
    }

    // ------- KEEPALIVE -------
    public static byte[] encodeKeepalive(long timestampMs) {
        ByteBuffer bb = ByteBuffer.allocate(1 + 8);
        bb.put(KEEPALIVE);
        bb.putLong(timestampMs);
        return bb.array();
    }

    public static Keepalive decodeKeepalive(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        byte type = bb.get();
        if (type != KEEPALIVE) throw new IllegalArgumentException("not a KEEPALIVE");
        return new Keepalive(bb.getLong());
    }

    // ------- DISCONNECT -------
    public static byte[] encodeDisconnect() {
        return new byte[]{ DISCONNECT };
    }

    // ------- POSITION (relay mode only) -------
    public static byte[] encodePosition(long timestampMs, double x, double y, double z,
                                        int dimensionHash, int roomHash) {
        ByteBuffer bb = ByteBuffer.allocate(1 + 8 + 8 + 8 + 8 + 4 + 4);
        bb.put(POSITION);
        bb.putLong(timestampMs);
        bb.putDouble(x);
        bb.putDouble(y);
        bb.putDouble(z);
        bb.putInt(dimensionHash);
        bb.putInt(roomHash);
        return bb.array();
    }

    public static Position decodePosition(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        byte type = bb.get();
        if (type != POSITION) throw new IllegalArgumentException("not a POSITION");
        return new Position(bb.getLong(), bb.getDouble(), bb.getDouble(), bb.getDouble(),
                bb.getInt(), bb.getInt());
    }

    // ------- REGISTER (relay mode only) -------
    public static byte[] encodeRegister(UUID playerUuid, String playerName,
                                         int dimensionHash, int roomHash) {
        byte[] nameBytes = playerName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (nameBytes.length > 255) throw new IllegalArgumentException("name too long");
        ByteBuffer bb = ByteBuffer.allocate(1 + 16 + 1 + nameBytes.length + 4 + 4);
        bb.put(REGISTER);
        bb.putLong(playerUuid.getMostSignificantBits());
        bb.putLong(playerUuid.getLeastSignificantBits());
        bb.put((byte) nameBytes.length);
        bb.put(nameBytes);
        bb.putInt(dimensionHash);
        bb.putInt(roomHash);
        return bb.array();
    }

    public static Register decodeRegister(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        byte type = bb.get();
        if (type != REGISTER) throw new IllegalArgumentException("not a REGISTER");
        UUID playerUuid = new UUID(bb.getLong(), bb.getLong());
        int nameLen = Byte.toUnsignedInt(bb.get());
        byte[] nameBytes = new byte[nameLen];
        bb.get(nameBytes);
        String name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
        int dim = bb.getInt();
        int room = bb.getInt();
        return new Register(playerUuid, name, dim, room);
    }

    // ------- VISIBLE_PLAYERS (client → server, relay mode only) -------
    public static byte[] encodeVisiblePlayers(java.util.Collection<UUID> uuids) {
        int count = Math.min(uuids.size(), 200);
        ByteBuffer bb = ByteBuffer.allocate(1 + 2 + count * 16);
        bb.put(VISIBLE_PLAYERS);
        bb.putShort((short) count);
        int i = 0;
        for (UUID id : uuids) {
            if (i++ >= count) break;
            bb.putLong(id.getMostSignificantBits());
            bb.putLong(id.getLeastSignificantBits());
        }
        return bb.array();
    }

    public static VisiblePlayers decodeVisiblePlayers(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        byte type = bb.get();
        if (type != VISIBLE_PLAYERS) throw new IllegalArgumentException("not a VISIBLE_PLAYERS");
        int count = Short.toUnsignedInt(bb.getShort());
        java.util.List<UUID> list = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new UUID(bb.getLong(), bb.getLong()));
        }
        return new VisiblePlayers(list);
    }

    // ------- PARTICIPANT_LIST (server → client, relay mode only) -------
    public static byte[] encodeParticipantList(java.util.Collection<UUID> uuids) {
        int count = Math.min(uuids.size(), 200); // cap to keep within ~3.5 KB
        ByteBuffer bb = ByteBuffer.allocate(1 + 2 + count * 16);
        bb.put(PARTICIPANT_LIST);
        bb.putShort((short) count);
        int i = 0;
        for (UUID id : uuids) {
            if (i++ >= count) break;
            bb.putLong(id.getMostSignificantBits());
            bb.putLong(id.getLeastSignificantBits());
        }
        return bb.array();
    }

    public static ParticipantList decodeParticipantList(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        byte type = bb.get();
        if (type != PARTICIPANT_LIST) throw new IllegalArgumentException("not a PARTICIPANT_LIST");
        int count = Short.toUnsignedInt(bb.getShort());
        java.util.List<UUID> list = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new UUID(bb.getLong(), bb.getLong()));
        }
        return new ParticipantList(list);
    }

    // ------- MUTED_STATUS (server → client, relay mode only) -------
    public static byte[] encodeMutedStatus(long expiryMillis) {
        ByteBuffer bb = ByteBuffer.allocate(1 + 8);
        bb.put(MUTED_STATUS);
        bb.putLong(expiryMillis);
        return bb.array();
    }

    public static MutedStatus decodeMutedStatus(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        byte type = bb.get();
        if (type != MUTED_STATUS) throw new IllegalArgumentException("not a MUTED_STATUS");
        return new MutedStatus(bb.getLong());
    }

    private VoicePackets() {}
}
