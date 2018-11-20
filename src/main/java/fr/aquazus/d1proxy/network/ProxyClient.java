package fr.aquazus.d1proxy.network;

import fr.aquazus.d1proxy.Proxy;
import fr.aquazus.d1proxy.handlers.PacketHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
import simplenet.Client;
import simplenet.packet.Packet;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class ProxyClient {

    private Proxy proxy;
    @Getter @Setter
    private ProxyClientState state;
    @Getter
    private Client client;
    @Getter
    private Client server;
    @Getter
    private String ip;
    @Getter @Setter
    private int characterId;
    @Getter @Setter
    private String username;
    @Getter @Setter
    private int currentMap;
    @Getter @Setter
    private boolean autoSkip;

    public ProxyClient(Proxy proxy, Client client, String ip) {
        this.proxy = proxy;
        this.state = ProxyClientState.INITIALIZING;
        this.client = client;
        this.ip = ip;
        this.connectTunnel();
    }

    private void connectTunnel() {
        this.server = new Client(proxy.getConfiguration().getProxyBuffer());
        server.onConnect(() -> {
            this.log("tunnel opened!");
            ByteArrayOutputStream clientStream = new ByteArrayOutputStream(proxy.getConfiguration().getProxyBuffer());
            client.readByteAlways(data -> {
                if (data == (byte) 0) {
                    String packet = new String(clientStream.toByteArray(), StandardCharsets.UTF_8);
                    clientStream.reset();
                    if (proxy.isDebug()) this.log("--> " + packet);
                    if (server.getChannel().isOpen() && shouldForward(packet)) Packet.builder().putBytes(packet.getBytes()).putByte(0).writeAndFlush(server);
                    return;
                }
                clientStream.write(data);
            });
            ByteArrayOutputStream gameStream = new ByteArrayOutputStream(proxy.getConfiguration().getProxyBuffer());
            server.readByteAlways(data -> {
                if (data == (byte) 0) {
                    String packet = new String(gameStream.toByteArray(), StandardCharsets.UTF_8);
                    gameStream.reset();
                    if (proxy.isDebug()) this.log("<-- " + packet);
                    if (client.getChannel().isOpen() && shouldForward(packet)) Packet.builder().putBytes(packet.getBytes()).putByte(0).writeAndFlush(client);
                    return;
                }
                gameStream.write(data);
            });
        });
        client.postDisconnect(() -> {
            this.log("disconnected!");
            this.disconnect();
        });
        server.postDisconnect(() -> {
            this.log("tunnel closed!");
            this.disconnect();
        });
        if (proxy.getExchangeCache().containsKey(ip)) {
            this.log("Found game address in exchange cache, tunneling to the right server...");
            String address[] = proxy.getExchangeCache().get(ip).split(":");
            proxy.getExchangeCache().remove(ip);
            server.connect(address[0], Integer.parseInt(address[1]));
        } else {
            server.connect(proxy.getConfiguration().getDofusIp(), proxy.getConfiguration().getDofusPort());
        }
    }

    @Synchronized
    private void disconnect() {
        if (client.getChannel().isOpen()) client.close();
        if (server.getChannel().isOpen()) server.close();
        if (state != ProxyClientState.DISCONNECTED) {
            if (state == ProxyClientState.INGAME) {
                proxy.sendMessage("<b>" + username + "</b> vient de se déconnecter du proxy.");
            }
            state = ProxyClientState.DISCONNECTED;
            proxy.getClients().remove(this);
        }
    }

    private boolean shouldForward(String packet) {
        if (packet.length() < 2) {
            return true;
        }

        boolean forward = true;

        String id = packet.substring(0, 2);
        if (proxy.getHandlers().containsKey(id)) {
            for (PacketHandler handlers : proxy.getHandlers().get(id)) {
                if (!handlers.shouldForward(this, packet)) {
                    forward = false;
                }
            }
        }

        if (packet.length() > 2) {
            String longerId = packet.substring(0, 3);
            if (proxy.getHandlers().containsKey(longerId)) {
                for (PacketHandler handlers : proxy.getHandlers().get(longerId)) {
                    if (!handlers.shouldForward(this, packet)) {
                        forward = false;
                    }
                }
            }
        }
        return forward;
    }

    public boolean executeCommand(String command) {
        String prefix = command.split(" ")[0].toLowerCase();
        if (proxy.getCommands().containsKey(prefix)) {
            proxy.getCommands().get(prefix).execute(this, (command.length() >= prefix.length() + 1 ? command.substring(prefix.length() + 1) : command.substring(prefix.length())));
            return true;
        }
        return false;
    }

    public void sendMessage(String message) {
        if (state == ProxyClientState.INGAME && client.getChannel().isOpen()) Packet.builder().putBytes(("cs<font color='#2C49D7'>" + message + "</font>").getBytes(StandardCharsets.UTF_8)).putByte(0).writeAndFlush(client);
    }

    public void log(String message) {
        System.out.print("[" + ip + (username == null ? "" : " - " + username) + "] " + message + (message.startsWith("-->") ? "" : "\n"));
    }
}
