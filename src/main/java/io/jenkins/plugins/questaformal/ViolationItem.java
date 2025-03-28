package io.jenkins.plugins.questaformal;

public class ViolationItem {
    private final String type;
    private final int count;
    private final String details;
    private final String id;

    public ViolationItem(String type, int count, String details) {
        this.type = type;
        this.count = count;
        this.details = details;
        this.id = type.toLowerCase().replaceAll("\\s+", "-");
    }

    public String getType() {
        return type;
    }

    public int getCount() {
        return count;
    }

    public String getDetails() {
        return details;
    }

    public String getId() {
        return id;
    }
}