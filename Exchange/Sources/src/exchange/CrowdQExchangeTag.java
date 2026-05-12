package exchange;

public enum CrowdQExchangeTag {
    LOAD(0),
    RESTART(1),
    COMMAND(2),
    SHOW(3),
    SHOWDATA(4);

    private final int value;

    CrowdQExchangeTag(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static CrowdQExchangeTag fromInt(int i) {
        for (CrowdQExchangeTag tag : values()) {
            if (tag.getValue() == i) return tag;
        }
        return null;
    }
}
