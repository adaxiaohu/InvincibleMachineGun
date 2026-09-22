package com.codigohasta.addon.utils.slimefunhelper.managers.input;

import java.util.HashSet;
import java.util.Set;
import org.lwjgl.glfw.GLFW;

/** Physical keyboard state, recorded before GUI handlers modify vanilla key bindings. */
public final class SimpleInputManager {
    private static final SimpleInputManager INSTANCE=new SimpleInputManager();
    private final Set<Integer> pressed=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static SimpleInputManager getInstance(){return INSTANCE;}
    public void onKeyInputPre(int code,int action){
        if(code==-1)return;
        if(action==GLFW.GLFW_RELEASE)pressed.remove(code);
        else if(action==GLFW.GLFW_PRESS||action==GLFW.GLFW_REPEAT)pressed.add(code);
    }
    public boolean isKeyPressed(int code){return pressed.contains(code);}
    public void clear(){pressed.clear();}
}
