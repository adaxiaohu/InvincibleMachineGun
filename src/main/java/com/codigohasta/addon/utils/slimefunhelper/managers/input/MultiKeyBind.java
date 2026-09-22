package com.codigohasta.addon.utils.slimefunhelper.managers.input;

/** Default binding adapted to Meteor's key editor, with a separate mouse modifier. */
public final class MultiKeyBind {
    public final meteordevelopment.meteorclient.utils.misc.Keybind key;
    public final int modifier;
    public MultiKeyBind(){this(meteordevelopment.meteorclient.utils.misc.Keybind.none(),-1);}
    private MultiKeyBind(meteordevelopment.meteorclient.utils.misc.Keybind key,int modifier){this.key=key;this.modifier=modifier;}
    public static MultiKeyBind mouse(int modifier,int button){return new MultiKeyBind(meteordevelopment.meteorclient.utils.misc.Keybind.fromButton(button),modifier);}
}
