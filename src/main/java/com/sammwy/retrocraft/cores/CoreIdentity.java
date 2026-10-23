package com.sammwy.retrocraft.cores;

public record CoreIdentity(String id, String name, String version, String platform, String sha256) {
    public boolean compatible(CoreIdentity other) {
        if (!id.equals(other.id) || !name.equals(other.name) || !version.equals(other.version))
            return false;
        return platform.equals(other.platform)
                ? sha256.equals(other.sha256)
                : KnownCores.get(id).portableSnapshots();
    }
}
