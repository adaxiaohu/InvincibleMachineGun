package com.codigohasta.addon.utils.slimefunhelper.hooks;

import com.codigohasta.addon.utils.slimefunhelper.versioned.SupportVersion;
import java.lang.reflect.Method;
import java.util.*;

/** Source protocol selection and packet wrapper, linked optionally through reflection. */
public final class ViaFabricPlusHooks {
    private static final ViaFabricPlusHooks INSTANCE = new ViaFabricPlusHooks();
    private Object base, lastProtocol;
    private Method targetVersion;
    private SupportVersion lastVersion;

    private ViaFabricPlusHooks() {
        try {
            base = call(Class.forName("com.viaversion.viafabricplus.ViaFabricPlus"), null, "getImpl");
            targetVersion = Class.forName("com.viaversion.viafabricplus.api.ViaFabricPlusBase").getMethod("getTargetVersion");
        } catch (ReflectiveOperationException | LinkageError error) {
            try {
                base = null;
                targetVersion = Class.forName("de.florianmichael.viafabricplus.protocoltranslator.ProtocolTranslator").getMethod("getTargetVersion");
            } catch (ReflectiveOperationException | LinkageError absent) { targetVersion = null; }
        }
    }

    public static ViaFabricPlusHooks getInstance() { return INSTANCE; }
    public boolean isViaEnabled() { return targetVersion != null; }
    public SupportVersion getCurrentVersion() {
        if (targetVersion == null) return SupportVersion.CURRENT;
        try {
            Object protocol = targetVersion.invoke(base);
            if (!Objects.equals(lastProtocol, protocol) || lastVersion == null) {
                lastProtocol = protocol;
                var versions = (Collection<?>) call(Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion"), protocol, "getIncludedVersions");
                lastVersion = SupportVersion.parse(versions.iterator().next().toString());
            }
            return lastVersion;
        } catch (ReflectiveOperationException | RuntimeException error) { return SupportVersion.CURRENT; }
    }

    public static boolean isSupportEndTick() { return INSTANCE.getCurrentVersion().isHigherOrEqualTo(21, 2); }
    public static boolean isSupportDupRot() { return INSTANCE.getCurrentVersion().isLowerOrEqualTo(20, 7); }
    public static boolean isSupportInstaSneak() { return INSTANCE.getCurrentVersion().isHigherOrEqualTo(21, 6); }
    public ViaPacketWrapper createViaPacket() {
        if (!isViaEnabled()) throw new UnsupportedOperationException("ViaFabricPlus is not installed");
        return new ViaPacketWrapper();
    }

    private static Object call(Class<?> type, Object receiver, String name, Object... args) throws ReflectiveOperationException {
        methods: for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            for (int i=0; i<args.length; i++) {
                if (args[i]!=null && !parameters[i].isInstance(args[i]) && !(parameters[i]==boolean.class && args[i] instanceof Boolean)) continue methods;
            }
            return method.invoke(receiver, args);
        }
        throw new NoSuchMethodException(type.getName()+"."+name);
    }

    public static final class ViaPacketWrapper {
        private static final Map<String,Map<String,Object>> types = new HashMap<>();
        private static final Map<String,Class<?>> protocols = new HashMap<>();
        private Object delegate;

        public ViaPacketWrapper writePacketType(String version, net.minecraft.network.packet.PacketType<?> packet) {
            return writePacketType(version, packet.id().getPath().toUpperCase(Locale.ROOT));
        }
        public ViaPacketWrapper writePacketType(String version, String name) {
            String key = (version.startsWith("v") ? version.substring(1) : version).toLowerCase(Locale.ROOT);
            try {
                Map<String,Object> packets = types.get(key);
                if (packets == null) {
                    packets = new HashMap<>();
                    String[] parts = key.split("to");
                    if (parts.length != 2) throw new IllegalArgumentException("Cannot parse protocol " + version);
                    for (String side : List.of("ServerboundPackets", "ClientboundPackets")) {
                        try {
                            Class<?> packetClass=Class.forName("com.viaversion.viaversion.protocols.v"+key+".packet."+side+parts[1]);
                            for(Object value:packetClass.getEnumConstants())packets.put(((Enum<?>)value).name(),value);
                        } catch (ClassNotFoundException ignored) { }
                    }
                    types.put(key, packets);
                }
                Object packet = Objects.requireNonNull(packets.get(name), name);
                Class<?> wrapper = Class.forName("com.viaversion.viaversion.api.protocol.packet.PacketWrapper");
                if (delegate == null) {
                    Object impl = call(Class.forName("com.viaversion.viafabricplus.ViaFabricPlus"), null, "getImpl");
                    Object connection = call(Class.forName("com.viaversion.viafabricplus.api.ViaFabricPlusBase"), impl, "getPlayNetworkUserConnection");
                    delegate = call(wrapper, null, "create", packet, connection);
                } else call(wrapper, delegate, "setPacketType", packet);
                return this;
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        public ViaPacketWrapper write(String name, Object value) {
            Objects.requireNonNull(delegate, "Set packet type before write");
            try {
                Object type=Class.forName("com.viaversion.viaversion.api.type.Types").getField(name).get(null);
                call(Class.forName("com.viaversion.viaversion.api.protocol.packet.PacketWrapper"),delegate,"write",type,value);
                return this;
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        public void scheduleSendToServer(String version, boolean skipPipeline) {
            Objects.requireNonNull(delegate, "Set packet type before send");
            String key=(version.startsWith("v")?version.substring(1):version).toLowerCase(Locale.ROOT);
            try {
                Class<?> protocol=protocols.get(key);
                if(protocol==null){protocol=Class.forName("com.viaversion.viaversion.protocols.v"+key+".Protocol"+key.replace("to","To"));protocols.put(key,protocol);}
                call(Class.forName("com.viaversion.viaversion.api.protocol.packet.PacketWrapper"),delegate,"scheduleSendToServer",protocol,skipPipeline);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        // These two entry points only check initialization in the supplied source.
        public void sendToServer(String version, boolean skipPipeline) { Objects.requireNonNull(delegate, "Set packet type before send"); }
        public void sendRaw(boolean currentThread) { Objects.requireNonNull(delegate, "Set packet type before send"); }
    }
}
