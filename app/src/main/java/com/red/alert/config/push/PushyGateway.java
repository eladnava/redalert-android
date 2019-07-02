package com.red.alert.config.push;

public class PushyGateway {
    // Set custom heartbeat interval (in ms) for the underlying MQTT socket
    public static int SOCKET_HEARTBEAT_INTERVAL = 60 * 3;

    // Pushy Topic ID for PubSub push notifications
    public static String ALERTS_TOPIC = "alerts";
}
