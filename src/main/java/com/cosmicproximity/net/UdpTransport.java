package com.cosmicproximity.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin wrapper around a UDP socket. Spawns one daemon thread that receives
 * packets and dispatches them synchronously to the handler.
 *
 * The handler runs on the receive thread — keep it fast or hand work off
 * to a queue / executor. Calls into Minecraft state must hop to the server
 * thread (use server.execute(...)).
 */
public final class UdpTransport implements AutoCloseable {
    public static final int MAX_PACKET = 4096;
    private static final Logger LOG = Logger.getLogger(UdpTransport.class.getName());

    public interface PacketHandler {
        void handle(InetSocketAddress from, byte[] data);
    }

    private final DatagramSocket socket;
    private final Thread receiveThread;
    private volatile boolean running = true;

    public UdpTransport(int bindPort, String threadName, PacketHandler handler) throws SocketException {
        this.socket = bindPort > 0 ? new DatagramSocket(bindPort) : new DatagramSocket();
        this.socket.setReceiveBufferSize(1 << 18);
        this.socket.setSendBufferSize(1 << 18);
        this.receiveThread = new Thread(() -> receiveLoop(handler), threadName);
        this.receiveThread.setDaemon(true);
        this.receiveThread.start();
    }

    public int localPort() {
        return socket.getLocalPort();
    }

    public void send(InetSocketAddress to, byte[] data) throws IOException {
        socket.send(new DatagramPacket(data, data.length, to));
    }

    private void receiveLoop(PacketHandler handler) {
        byte[] buf = new byte[MAX_PACKET];
        while (running) {
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(pkt);
                byte[] payload = new byte[pkt.getLength()];
                System.arraycopy(pkt.getData(), 0, payload, 0, payload.length);
                try {
                    handler.handle((InetSocketAddress) pkt.getSocketAddress(), payload);
                } catch (Throwable t) {
                    LOG.log(Level.WARNING, "UDP handler threw", t);
                }
            } catch (IOException e) {
                if (running) LOG.log(Level.WARNING, "UDP receive error", e);
            }
        }
    }

    @Override
    public void close() {
        running = false;
        socket.close();
    }
}
