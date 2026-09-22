package com.codigohasta.addon.utils.slimefunhelper.commands;

import com.codigohasta.addon.modules.slimefunhelper.TargetSelector;
import com.codigohasta.addon.utils.slimefunhelper.SfTranslations;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

/** Original friends list/add/remove/gui operations registered with Meteor's command dispatcher. */
public final class SfFriendsCommand extends Command {
    private final TargetSelector selector;
    public SfFriendsCommand(TargetSelector selector){
        super("sf-friends",SfTranslations.text("friend.command-description","管理 SF 好友名单和别名。"));
        this.selector=selector;
    }
    @Override public void build(LiteralArgumentBuilder<CommandSource> builder){
        builder.then(literal("list").executes(ctx->{
            for(var entry:selector.getPlayerList().toPairList())info("%s%s",entry.getFirst(),entry.getSecond().isEmpty()?"":" ("+entry.getSecond()+")");
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("add").then(argument("name",StringArgumentType.word())
            .suggests((ctx,suggestions)->{
                if(mc.getNetworkHandler()!=null)mc.getNetworkHandler().getPlayerList().forEach(entry->suggestions.suggest(entry.getProfile().name()));
                return suggestions.buildFuture();
            })
            .executes(ctx->{selector.addFriend(StringArgumentType.getString(ctx,"name"),"");return SINGLE_SUCCESS;})
            .then(argument("alias",StringArgumentType.greedyString()).executes(ctx->{selector.addFriend(StringArgumentType.getString(ctx,"name"),StringArgumentType.getString(ctx,"alias"));return SINGLE_SUCCESS;}))));
        builder.then(literal("remove").then(argument("name",StringArgumentType.word())
            .suggests((ctx,suggestions)->{selector.getPlayerList().stream().forEach(suggestions::suggest);return suggestions.buildFuture();})
            .executes(ctx->{selector.removeFriend(StringArgumentType.getString(ctx,"name"));return SINGLE_SUCCESS;})));
        builder.then(literal("gui").executes(ctx->{mc.execute(selector::openEditFriendsScreen);return SINGLE_SUCCESS;}));
    }
}
