package common;

import java.util.*;

public class Message {
    private Map<String, String> fields;

    public Message() {
        this.fields = new HashMap<>();
    }

    public Message(String messageType) {
        this();
        setField("MessageType", messageType);
    }

    public void setField(String key, String value) {
        fields.put(key, value);
    }

    public String getField(String key) {
        return fields.get(key);
    }

    public boolean hasField(String key) {
        return fields.containsKey(key);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!sb.isEmpty()) sb.append("|");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return sb.toString();
    }

    public static Message parse(String messageStr) {
        Message msg = new Message();
        String[] parts = messageStr.split("\\|");
        for (String part : parts) {
            int colonIndex = part.indexOf(":");
            if (colonIndex > 0) {
                String key = part.substring(0, colonIndex);
                String value = part.substring(colonIndex + 1);
                msg.setField(key, value);
            }
        }
        return msg;
    }

    public String getMessageType() {
        return getField("MessageType");
    }

    public String getMessageId() {
        return getField("MessageId");
    }
}