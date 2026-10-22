package com.sammwy.retrocraft.server.console;

import com.sammwy.retrocraft.server.item.CartridgeItem;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;

public record ConsoleDefinition(Identifier id, String name, ConsoleType type, CartridgeItem cartridge) {
    public Text displayName() { return Text.translatableWithFallback("console." + id.getNamespace() + "." + id.getPath(), name); }
}
