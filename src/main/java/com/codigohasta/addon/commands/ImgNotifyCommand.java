package com.codigohasta.addon.commands;

import com.codigohasta.addon.utils.notify.ImgNotifications;
import com.codigohasta.addon.utils.notify.ImgNotificationType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

/**
 * 测试通知系统。
 *
 * <p>用法：
 * <pre>
 *   img-notify                          发一条默认通知
 *   img-notify &lt;type&gt; &lt;text&gt;           指定类型与文字
 *   img-notify &lt;type&gt; &lt;text&gt; &lt;seconds&gt;  指定显示秒数
 * </pre>
 * 类型：SUCCESS / INFO / WARNING / ERROR / ENABLED / DISABLED（大小写不敏感）。
 *
 * <p>存在的意义：通知系统的触发点（模块开关、totem、range 等）分散在各处，
 * 有个手动入口才能单独验证「队列 / 动画 / 类型 / 图标 / 过期」是否正常。
 */
public class ImgNotifyCommand extends Command {

    public ImgNotifyCommand() {
        super("img-notify", "发送一条测试通知到通知 HUD。");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // 无参数：默认通知
        builder.executes(ctx -> {
            ImgNotifications.INSTANCE.post("", "测试通知", ImgNotificationType.INFO, 3);
            info("已发送测试通知（INFO）。");
            return SINGLE_SUCCESS;
        });

        // img-notify <type> <text> [seconds]
        builder.then(argument("type", StringArgumentType.word())
            .then(argument("text", StringArgumentType.greedyString())
                .executes(ctx -> {
                    String typeName = StringArgumentType.getString(ctx, "type");
                    String text = StringArgumentType.getString(ctx, "text");
                    ImgNotificationType type = ImgNotificationType.byName(typeName);
                    if (type == null) {
                        error("未知类型: %s（可用: SUCCESS/INFO/WARNING/ERROR/ENABLED/DISABLED）", typeName);
                        return SINGLE_SUCCESS;
                    }
                    // 支持「文字 秒数」的写法：末尾若是纯数字则当时长
                    int seconds = 3;
                    String body = text;
                    int sp = text.lastIndexOf(' ');
                    if (sp > 0) {
                        String tail = text.substring(sp + 1);
                        try {
                            int v = Integer.parseInt(tail);
                            if (v >= 1 && v <= 60) {
                                seconds = v;
                                body = text.substring(0, sp);
                            }
                        } catch (NumberFormatException ignored) {
                            // 末尾不是数字 -> 整串都是文字
                        }
                    }
                    ImgNotifications.INSTANCE.post("", body, type, seconds);
                    info("已发送 %s 通知: %s（%d 秒）", type.name(), body, seconds);
                    return SINGLE_SUCCESS;
                })));
    }
}
