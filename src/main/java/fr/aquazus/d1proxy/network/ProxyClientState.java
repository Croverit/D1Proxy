package fr.aquazus.d1proxy.network;

public enum ProxyClientState {
    INITIALIZING,
    SERVER_SELECT,
    SERVER_CONNECTING,
    INGAME,
    DISCONNECTED
}