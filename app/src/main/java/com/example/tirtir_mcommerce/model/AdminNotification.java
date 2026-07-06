package com.example.tirtir_mcommerce.model;

public class AdminNotification {
    public enum Type {
        INVENTORY,
        SALES,
        SECURITY,
        FEEDBACK,
        SYSTEM
    }

    private String id;
    private Type type;
    private String time;
    private String message;
    private String primaryAction;
    private String secondaryAction;
    private boolean isRead;

    public AdminNotification(String id, Type type, String time, String message, String primaryAction, String secondaryAction) {
        this.id = id;
        this.type = type;
        this.time = time;
        this.message = message;
        this.primaryAction = primaryAction;
        this.secondaryAction = secondaryAction;
        this.isRead = false;
    }

    public String getId() { return id; }
    public Type getType() { return type; }
    public String getTime() { return time; }
    public String getMessage() { return message; }
    public String getPrimaryAction() { return primaryAction; }
    public String getSecondaryAction() { return secondaryAction; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}
