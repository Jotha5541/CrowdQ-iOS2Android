package exchange;

import java.util.Base64;

public class CrowdQExchange {
    private int sequence;
    private CrowdQExchangeTag tag;
    private int argument;
    private String payload;

    // Optimization: zero allocation + deny byte array
    private static final int[] B64_INV = new int[128];
    static {
        for (int i = 0; i < 128; i++) B64_INV[i] = -1;
        String b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for (int i = 0; i < b64.length(); i++) B64_INV[b64.charAt(i)] = i;
        B64_INV['='] = 0;
    }

    public CrowdQExchange() {} // Empty constructor

    public CrowdQExchange(int sequence, CrowdQExchangeTag tag, int argument, String payload) {
        this.sequence = sequence;
        this.tag = tag;
        this.argument = argument;
        this.payload = payload;
    }

    // --- Optimization: Avoid Substring Churn ---
    public static CrowdQExchange parse(String packet) {
        if (packet == null || packet.length() < 8) return null;

        // Decode 4-char blocks directly to 24-bit integers without substring allocations
        int block1 = decodeFourChars(packet, 0);
        int block2 = decodeFourChars(packet, 4);

        if (block1 < 0 || block2 < 0) return null; // Invalid base64

        CrowdQExchange exchange = new CrowdQExchange();
        exchange.setSequence(block1 >> 4);
        exchange.setTag(CrowdQExchangeTag.fromInt(block1 & 0xF));
        exchange.setArgument(block2);

        // Only one substring allocation for the payload itself
        exchange.setPayload(packet.substring(8));
        return exchange;
    }

    private static int decodeFourChars(String s, int offset) {
        int c1 = B64_INV[s.charAt(offset)];
        int c2 = B64_INV[s.charAt(offset + 1)];
        int c3 = B64_INV[s.charAt(offset + 2)];
        int c4 = B64_INV[s.charAt(offset + 3)];
        if (c1 < 0 || c2 < 0 || c3 < 0 || c4 < 0) return -1;
        return (c1 << 18) | (c2 << 12) | (c3 << 6) | c4;
    }

    public String pack() {
        int block1 = (sequence << 4) | (tag != null ? tag.getValue() : 0);
        int block2 = argument;

        // Convert 24-bit ints to 3 bytes each
        byte[] bytes = new byte[]{
                (byte)(block1 >> 16), (byte)(block1 >> 8), (byte)block1,
                (byte)(block2 >> 16), (byte)(block2 >> 8), (byte)block2
        };

        String prefix = Base64.getEncoder().encodeToString(bytes);
        return prefix + (payload != null ? payload : "");
    }

    // Getters and Setters
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public CrowdQExchangeTag getTag() { return tag; }
    public void setTag(CrowdQExchangeTag tag) { this.tag = tag; }
    public int getArgument() { return argument; }
    public void setArgument(int argument) { this.argument = argument; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    @Override
    public String toString() {
        return "CrowdQExchange{" +
                "sequence=" + sequence +
                ", tag=" + tag +
                ", argument=" + argument +
                ", payload='" + payload + '\'' +
                '}';
    }
}