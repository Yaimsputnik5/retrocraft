package com.sammwy.retrocraft.cores.worker;

import java.util.*;

/**
 * Hardware plugged into a console: core option overrides, controller port devices and the pad that
 * aims pointer devices. Travels as one worker argument and one packet string; the worker still
 * accepts only option values and devices advertised by the core.
 */
public record HardwareProfile(
        Map<String, String> options, Map<Integer, Integer> devices, int pointerPort) {
    public static final HardwareProfile NONE = new HardwareProfile(Map.of(), Map.of(), -1);
    public static final int MAX_ENCODED = 2048;

    public HardwareProfile {
        options = Collections.unmodifiableMap(new TreeMap<>(options));
        devices = Collections.unmodifiableMap(new TreeMap<>(devices));
        for (var option : options.entrySet())
            if (!option.getKey().matches("[A-Za-z0-9_.\\-]{1,64}") || !value(option.getValue()))
                throw new IllegalArgumentException("Invalid core option " + option.getKey());
        for (var device : devices.entrySet())
            if (device.getKey() < 0
                    || device.getKey() >= WorkerProtocol.PORTS
                    || device.getValue() < 0
                    || device.getValue() > 0xffff)
                throw new IllegalArgumentException("Invalid port device");
        if (pointerPort < -1 || pointerPort >= WorkerProtocol.PORTS)
            throw new IllegalArgumentException("Invalid pointer port");
    }

    private static boolean value(String value) {
        return value != null
                && !value.isEmpty()
                && value.length() <= 64
                && value.chars().allMatch(c -> c >= 0x20 && c < 0x7f && c != ';' && c != '=');
    }

    /** Options as key/value pairs, e.g. {@code options("a", "on", "b", "off")}. */
    public static HardwareProfile options(String... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Unpaired option");
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) options.put(pairs[i], pairs[i + 1]);
        return new HardwareProfile(options, Map.of(), -1);
    }

    public HardwareProfile device(int port, int device) {
        Map<Integer, Integer> devices = new HashMap<>(this.devices);
        devices.put(port, device);
        return new HardwareProfile(options, devices, pointerPort);
    }

    public HardwareProfile pointerFrom(int port) {
        return new HardwareProfile(options, devices, port);
    }

    /** {@code other} wins on conflicts. */
    public HardwareProfile merge(HardwareProfile other) {
        if (other == null || other.isEmpty()) return this;
        Map<String, String> options = new HashMap<>(this.options);
        options.putAll(other.options);
        Map<Integer, Integer> devices = new HashMap<>(this.devices);
        devices.putAll(other.devices);
        return new HardwareProfile(
                options, devices, other.pointerPort >= 0 ? other.pointerPort : pointerPort);
    }

    public boolean isEmpty() {
        return options.isEmpty() && devices.isEmpty() && pointerPort < 0;
    }

    /** Whether a pad's pointer (stylus, light gun) drives the core. */
    public boolean aims() {
        return pointerPort >= 0;
    }

    public String encode() {
        StringJoiner out = new StringJoiner(";");
        options.forEach((key, value) -> out.add("o:" + key + "=" + value));
        devices.forEach((port, device) -> out.add("d:" + port + "=" + device));
        if (pointerPort >= 0) out.add("p:" + pointerPort);
        String text = out.toString();
        if (text.length() > MAX_ENCODED) throw new IllegalArgumentException("Hardware too large");
        return text;
    }

    public static HardwareProfile decode(String text) {
        if (text == null || text.isEmpty()) return NONE;
        if (text.length() > MAX_ENCODED) throw new IllegalArgumentException("Hardware too large");
        Map<String, String> options = new HashMap<>();
        Map<Integer, Integer> devices = new HashMap<>();
        int pointer = -1;
        for (String entry : text.split(";")) {
            if (entry.length() < 3 || entry.charAt(1) != ':')
                throw new IllegalArgumentException("Invalid hardware entry");
            String body = entry.substring(2);
            int separator = body.indexOf('=');
            try {
                switch (entry.charAt(0)) {
                    case 'o' -> {
                        if (separator <= 0) throw new IllegalArgumentException("Invalid option");
                        options.put(body.substring(0, separator), body.substring(separator + 1));
                    }
                    case 'd' -> {
                        if (separator <= 0) throw new IllegalArgumentException("Invalid device");
                        devices.put(
                                Integer.parseInt(body.substring(0, separator)),
                                Integer.parseInt(body.substring(separator + 1)));
                    }
                    case 'p' -> pointer = Integer.parseInt(body);
                    default -> throw new IllegalArgumentException("Invalid hardware entry");
                }
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid hardware number", ex);
            }
        }
        return new HardwareProfile(options, devices, pointer);
    }
}
