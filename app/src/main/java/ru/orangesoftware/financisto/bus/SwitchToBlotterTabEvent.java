package ru.orangesoftware.financisto.bus;

public class SwitchToBlotterTabEvent {

    public final long accountId;
    public final String title;

    public SwitchToBlotterTabEvent(long accountId, String title) {
        this.accountId = accountId;
        this.title = title;
    }
}