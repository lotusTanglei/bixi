package com.lotus.bixi.common.mq.reliable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for a dedicated Rabbit connection used by one reliable owner.
 * Each owner's business configuration maps these into a {@link Settings} value that the
 * endpoint uses to create its private connection factory. No beans are registered here.
 */
@ConfigurationProperties(prefix = "bixi.reliable.rabbit")
public class ReliableRabbitProperties {
    private String host;
    private int port = 5672;
    private String username;
    private String password;
    private String virtualHost = "/";
    private int connectionTimeout = 5_000;
    private int handshakeTimeout = 5_000;
    private int shutdownTimeout = 5_000;
    private int requestedHeartbeat = 30;
    private int channelCacheSize = 4;
    private int prefetch = 20;

    public Settings toSettings() {
        return new Settings(host, port, username, password, virtualHost,
                connectionTimeout, handshakeTimeout, shutdownTimeout,
                requestedHeartbeat, channelCacheSize, prefetch);
    }

    public record Settings(String host, int port, String username, String password, String virtualHost,
            int connectionTimeout, int handshakeTimeout, int shutdownTimeout,
            int requestedHeartbeat, int channelCacheSize, int prefetch) {
        public Settings {
            if (host == null || host.isBlank()) throw new IllegalArgumentException("Rabbit host is required");
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Rabbit port must be 1..65535");
            if (username == null || username.isBlank()) throw new IllegalArgumentException("Rabbit username is required");
            if (password == null) throw new IllegalArgumentException("Rabbit password is required");
            if (virtualHost == null) throw new IllegalArgumentException("Rabbit virtual host is required");
            if (connectionTimeout < 1_000 || connectionTimeout > 60_000)
                throw new IllegalArgumentException("Connection timeout must be 1s..60s");
            if (handshakeTimeout < 1_000 || handshakeTimeout > 60_000)
                throw new IllegalArgumentException("Handshake timeout must be 1s..60s");
            if (shutdownTimeout < 1_000 || shutdownTimeout > 60_000)
                throw new IllegalArgumentException("Shutdown timeout must be 1s..60s");
            if (requestedHeartbeat < 5 || requestedHeartbeat > 300)
                throw new IllegalArgumentException("Requested heartbeat must be 5s..300s");
            if (channelCacheSize < 1 || channelCacheSize > 50)
                throw new IllegalArgumentException("Channel cache size must be 1..50");
            if (prefetch < 1 || prefetch > 20)
                throw new IllegalArgumentException("Consumer prefetch must be 1..20");
        }
    }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getVirtualHost() { return virtualHost; }
    public void setVirtualHost(String virtualHost) { this.virtualHost = virtualHost; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    public int getHandshakeTimeout() { return handshakeTimeout; }
    public void setHandshakeTimeout(int handshakeTimeout) { this.handshakeTimeout = handshakeTimeout; }
    public int getShutdownTimeout() { return shutdownTimeout; }
    public void setShutdownTimeout(int shutdownTimeout) { this.shutdownTimeout = shutdownTimeout; }
    public int getRequestedHeartbeat() { return requestedHeartbeat; }
    public void setRequestedHeartbeat(int requestedHeartbeat) { this.requestedHeartbeat = requestedHeartbeat; }
    public int getChannelCacheSize() { return channelCacheSize; }
    public void setChannelCacheSize(int channelCacheSize) { this.channelCacheSize = channelCacheSize; }
    public int getPrefetch() { return prefetch; }
    public void setPrefetch(int prefetch) { this.prefetch = prefetch; }
}
