package net.nanaky.ultimate_minecarts.common.utils;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import java.util.UUID;

public final class XtraCodecs {
    private XtraCodecs() {}
    public static final Codec<UUID> UUID_CODEC = UUIDUtil.CODEC;
    public static final StreamCodec<FriendlyByteBuf, UUID> UUID_STREAM_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> buf.writeUUID(uuid),
                    buf -> buf.readUUID()
            );
}