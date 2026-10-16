/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.registry;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.item.CarvedBlockItem;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Files for the per material carved block items, made in memory at startup instead of shipped:
 * the list of items depends on the mods installed. On the client, one item model per item, all
 * pointing at the carved block's own; on the server, the item tag {@code #bitsmith:carved_blocks}
 * holding every carved block item.
 */
public final class CarvedItemsPack implements PackResources {
    private static final String ID = Bitsmith.MOD_ID + "_carved_items";

    private final PackLocationInfo location;
    private final PackType type;
    private final Map<ResourceLocation, byte[]> files;
    private final byte[] meta;

    private CarvedItemsPack(PackLocationInfo location, PackType type, Map<ResourceLocation, byte[]> files) {
        this.location = location;
        this.type = type;
        this.files = files;
        this.meta = ("{\"pack\":{\"pack_format\":" + SharedConstants.getCurrentVersion().getPackVersion(type)
                + ",\"description\":\"Bitsmith carved block items\"}}").getBytes(StandardCharsets.UTF_8);
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        PackType type = event.getPackType();
        PackLocationInfo location = new PackLocationInfo(ID, Component.literal("Bitsmith carved block items"),
                PackSource.BUILT_IN, Optional.empty());
        Map<ResourceLocation, byte[]> files = type == PackType.CLIENT_RESOURCES ? models() : tags();
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo info) {
                return new CarvedItemsPack(info, type, files);
            }

            @Override
            public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return new CarvedItemsPack(info, type, files);
            }
        };
        // Required and fixed: always on, under the user's own resource and data packs.
        Pack pack = Pack.readMetaAndCreate(location, resources, type,
                new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    private static Map<ResourceLocation, byte[]> models() {
        Map<ResourceLocation, byte[]> files = new LinkedHashMap<>();
        byte[] model = "{\"parent\":\"bitsmith:item/carved_block\"}".getBytes(StandardCharsets.UTF_8);
        for (CarvedBlockItem item : CarvedItems.all()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            files.put(id.withPath(path -> "models/item/" + path + ".json"), model);
        }
        return files;
    }

    private static Map<ResourceLocation, byte[]> tags() {
        StringBuilder values = new StringBuilder("{\"replace\":false,\"values\":[\"bitsmith:carved_block\"");
        for (CarvedBlockItem item : CarvedItems.all()) {
            values.append(",\"").append(BuiltInRegistries.ITEM.getKey(item)).append('"');
        }
        values.append("]}");
        Map<ResourceLocation, byte[]> files = new LinkedHashMap<>();
        files.put(Bitsmith.id("tags/item/carved_blocks.json"), values.toString().getBytes(StandardCharsets.UTF_8));
        return files;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
        if (elements.length == 1 && PACK_META.equals(elements[0])) {
            return () -> new ByteArrayInputStream(meta);
        }
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType packType, ResourceLocation file) {
        byte[] content = packType == type ? files.get(file) : null;
        return content == null ? null : () -> new ByteArrayInputStream(content);
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, ResourceOutput output) {
        if (packType != type) {
            return;
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<ResourceLocation, byte[]> entry : files.entrySet()) {
            ResourceLocation file = entry.getKey();
            if (file.getNamespace().equals(namespace) && file.getPath().startsWith(prefix)) {
                byte[] content = entry.getValue();
                output.accept(file, () -> new ByteArrayInputStream(content));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType packType) {
        return packType == type && !files.isEmpty() ? Set.of(Bitsmith.MOD_ID) : Set.of();
    }

    @Override
    public @Nullable <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        return AbstractPackResources.getMetadataFromStream(deserializer, new ByteArrayInputStream(meta));
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public void close() {
    }
}
