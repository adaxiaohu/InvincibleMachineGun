package com.codigohasta.addon.modules.slimefunhelper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.hacks.InvTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
import com.codigohasta.addon.utils.slimefunhelper.utils.Debug;
import com.codigohasta.addon.utils.slimefunhelper.utils.ItemStackUtils;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;

public class ClientSideCommand extends BaseModule {
    public static ClientSideCommand INSTANCE;
    private RootCommandNode<?> modifiedRoot;
    private final java.util.Set<String> addedNodes = new java.util.HashSet<>();
    public final ModulePath clientSideCommand = makePath("chat", "client-side-command");

    public ClientSideCommand() {
        super("ClientSideCommand");
        bindFlag(enable);
        INSTANCE = this;
    }

    public final FlagRef enable =
            flagBuilder(clientSideCommand.add("client-side-command-override")).build();

    public final FlagRef enableGive =
            flagBuilder(clientSideCommand.add("client-side-give")).updateListener(value -> refreshCommands()).build();

    private static final Predicate<CommandSource> requirement = (val) -> true;


    private <T extends CommandSource> void addOurCommandNodesInRoot(RootCommandNode<T> node) {
        // try add deop command
        // fix: plugin give commands
        if (enableGive.get()) {
            CommandNode<T> give = node.getChild("minecraft:give");
            LiteralCommandNode<T> giveCommand;
            if (give == null) {
                giveCommand =
                        new LiteralCommandNode<>("minecraft:give", null, requirement::test, null, null, false);
                node.addChild(giveCommand);
                addedNodes.add("minecraft:give");
            } else {
                giveCommand = (LiteralCommandNode<T>) give;
            }
            if (node.getChild("give") == null) {
                LiteralCommandNode<T> mcGiveCommand =
                        new LiteralCommandNode<>("give", null, requirement::test, giveCommand, null, false);
                node.addChild(mcGiveCommand);
                addedNodes.add("give");
            }
            if (give == null) {
                CommandRegistryAccess commandRegistryAccess =
                        CommandRegistryAccess.of(ItemStackUtils.registry(), FeatureFlags.DEFAULT_ENABLED_FEATURES);

                ArgumentCommandNode<T, EntitySelector> targetArgument = new ArgumentCommandNode<>(
                        "targets",
                        EntityArgumentType.players(),
                        null,
                        requirement::test,
                        null,
                        null,
                        false,
                        // use default because if "minecraft:give" node is absent, then we definitely have no permission
                        // of requesting this
                        null);
                giveCommand.addChild(targetArgument);
                ArgumentCommandNode<T, ItemStackArgument> itemArgument = new ArgumentCommandNode<>(
                        "item",
                        ItemStackArgumentType.itemStack(commandRegistryAccess),
                        context -> Command.SINGLE_SUCCESS,
                        requirement::test,
                        null,
                        null,
                        false,
                        null);
                targetArgument.addChild(itemArgument);
                ArgumentCommandNode<T, Integer> countAmount = new ArgumentCommandNode<>(
                        "count",
                        IntegerArgumentType.integer(1),
                        context -> Command.SINGLE_SUCCESS,
                        requirement::test,
                        null,
                        null,
                        false,
                        null);
                itemArgument.addChild(countAmount);
            }
        }
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(CommandTreeS2CPacket.class),
                (Consumer<Event<CommandTreeS2CPacket>>) this::onClientCommandReload);
        registerListener(com.codigohasta.addon.utils.slimefunhelper.chatcraft.SfChatCraftEvents.CHAT_SEND, this::onCommandSend, 1);
    }

    private void onClientCommandReload(Event<CommandTreeS2CPacket> reload) {
        CommandDispatcher<ClientCommandSource> clientTree =
                mc.getNetworkHandler().getCommandDispatcher();
        RootCommandNode<ClientCommandSource> root = clientTree.getRoot();
        if (root != null && enable.get()) {
            if (modifiedRoot != root) { addedNodes.clear(); modifiedRoot=root; }
            addOurCommandNodesInRoot(root);
        }
    }

    public List<String> supportedCommand = List.of("give", "minecraft:give");

    public void onCommandSend(Event<String> commandEvent) {
        String command = commandEvent.context();
        if (enable.get() && command.startsWith("/")) {
            command = command.substring(1);
            if (supportedCommand.contains(command.split(" ", 2)[0])) {

                if (dispatchVanillaCommand(command)) {
                    commandEvent.cancel();
                    return;
                }
            }
        }
    }

    private static ResultConsumer<ClientCommandSource> consumer = (c, s, r) -> {};

    private boolean dispatchVanillaCommand(String command) {
        // Debug.info(command);
        var permissionOwner = mc.player;
        if (permissionOwner == null) return false;
        var oldPermissions = permissionOwner.getPermissions();
        permissionOwner.setPermissions(PermissionPredicate.ALL);
        try {
            ParseResults<ClientCommandSource> parse =
                    mc.getNetworkHandler().getCommandDispatcher().parse(command, (ClientCommandSource)
                            mc.getNetworkHandler().getCommandSource());
            if (parse.getReader().canRead()) {
                if (parse.getExceptions().size() == 1) {
                    throw parse.getExceptions().values().iterator().next();
                } else if (parse.getContext().getRange().isEmpty()) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                            .dispatcherUnknownCommand()
                            .createWithContext(parse.getReader());
                } else {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                            .dispatcherUnknownArgument()
                            .createWithContext(parse.getReader());
                }
            }

            final String commandStr = parse.getReader().getString();
            final CommandContextBuilder<ClientCommandSource> originalBuilder = parse.getContext();
            // flatten this
            List<CommandContextBuilder<ClientCommandSource>> modifiers = new ArrayList<>();
            CommandContextBuilder<ClientCommandSource> contextData = originalBuilder;
            while (true) {
                CommandContextBuilder<ClientCommandSource> child = contextData.getChild();
                if (child == null) {
                    if (contextData.getCommand() == null) {
                        consumer.onCommandComplete(originalBuilder.build(commandStr), false, 0);
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                                .dispatcherUnknownCommand()
                                .createWithContext(parse.getReader());
                    }
                    break;
                }
                modifiers.add(contextData);
                contextData = child;
            }
            Map<String, ParsedArgument<ClientCommandSource, ?>> argsMap = contextData.getArguments();
            if (commandStr.startsWith("give") || commandStr.startsWith("minecraft:give")) {
                return handleClientSideGiveCommand(argsMap, command);
            }
        } catch (CommandSyntaxException e) {
            Debug.chat(getErrorMessage(e));
        } catch (Throwable e) {
            Debug.chat(Text.literal("客户端指令执行出错").formatted(Formatting.RED), e);
        } finally {
            permissionOwner.setPermissions(oldPermissions);
        }
        return false;
    }

    private boolean handleClientSideGiveCommand(
            Map<String, ParsedArgument<ClientCommandSource, ?>> argsMap, String command) throws CommandSyntaxException {
        if (enableGive.get()) {
            if (mc.interactionManager.getCurrentGameMode().isCreative()) {
                Debug.chat(Text.literal("尝试在客户端执行give指令").formatted(Formatting.GREEN));
                ParsedArgument<ClientCommandSource, ?> entityArgument = argsMap.get("targets");
                EntitySelector entitySelector = (EntitySelector) entityArgument.getResult();
                StringRange range = entityArgument.getRange();
                if (entitySelector.isSenderOnly()
                        || Objects.equals(
                                mc.player.getNameForScoreboard(),
                                command.substring(range.getStart(), range.getEnd()))) {
                    ItemStackArgument itemStack =
                            (ItemStackArgument) argsMap.get("item").getResult();
                    int count = argsMap.containsKey("count")
                            ? (Integer) argsMap.get("count").getResult()
                            : 1;
                    ItemStack itemStackToGive = itemStack.createStack(count, false);
                    com.codigohasta.addon.utils.slimefunhelper.chatcraft.CreativeGive.give(itemStackToGive, count);
                    Debug.chat(Text.literal("命令执行成功！").formatted(Formatting.GREEN));
                    return true;
                } else {
                    Debug.chat(Text.literal("你选中了其他生物,指令转向服务端执行!").formatted(Formatting.YELLOW));
                    return false;
                }
            } else {
                Debug.chat(Text.literal("你启用了客户端指令的功能,但是你并不是创造模式!").formatted(Formatting.YELLOW));
                return false;
            }
        } else {
            Debug.chat(Text.literal("尝试在客户端执行give指令,但是你没有启用客户端give指令").formatted(Formatting.RED));
            return false;
        }
    }

    private void refreshCommands() {
        if (!isActive()) return;
        onDisableModule();
        if (mc.getNetworkHandler()!=null) onClientCommandReload(null);
    }
    @Override public void onEnableModule() { refreshCommands(); }

    @Override public void onDisableModule() {
        if(modifiedRoot instanceof com.codigohasta.addon.mixin.slimefunhelper.ChatCraftCommandNodeAccessor access) {
            for(String name:addedNodes) { access.sfimg$children().remove(name); access.sfimg$literals().remove(name); access.sfimg$arguments().remove(name); }
        }
        modifiedRoot=null;addedNodes.clear();
    }
    private static Text getErrorMessage(CommandSyntaxException e) {
        Text message = Texts.toText(e.getRawMessage());
        String context = e.getContext();

        return context != null
                ? Text.translatable("command.context.parse_error", message, e.getCursor(), context)
                : message;
    }
}
