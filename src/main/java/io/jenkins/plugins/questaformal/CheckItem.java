package io.jenkins.plugins.questaformal;

public class CheckItem {
    private final String name;
    private final int count;
    private final String details;
    private final String id;

    public CheckItem(String name, int count, String details) {
        this.name = name;
        this.count = count;
        this.details = details;
        this.id = name.toLowerCase().replaceAll("\\s+", "-");
    }

    public String getName() {
        return name;
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
