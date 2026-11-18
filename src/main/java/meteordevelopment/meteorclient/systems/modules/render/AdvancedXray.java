/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */
package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/** Controls the separately installed Advanced XRay mod. */
public class AdvancedXray extends Module {
    private Method isActiveMethod;
    private Method toggleMethod;

    public AdvancedXray() {
        super(Categories.Render, "advanced-xray", "Controls Advanced XRay. Configure its ores with G.");
        serialize = false;
    }

    private boolean connect() {
        if (toggleMethod != null) return true;
        if (!ModList.get().isLoaded("xray")) return false;
        try {
            Class<?> controller = Class.forName("pro.mikey.xray.xray.Controller");
            isActiveMethod = controller.getMethod("isXRayActive");
            toggleMethod = controller.getMethod("toggleXRay");
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            error("Could not connect to Advanced XRay: %s", e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean state() throws ReflectiveOperationException {
        return (boolean) isActiveMethod.invoke(null);
    }

    private void setState(boolean desired) {
        try {
            if (state() != desired) toggleMethod.invoke(null);
        } catch (ReflectiveOperationException e) {
            error("Advanced XRay failed: %s", e.getClass().getSimpleName());
        }
    }

    @Override
    public void onActivate() {
        if (!connect()) {
            error("Install Advanced XRay 21.0.1 for NeoForge 1.21.1.");
            toggle();
            return;
        }
        setState(true);
    }

    @Override
    public void onDeactivate() {
        if (toggleMethod != null) setState(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        try {
            if (toggleMethod != null && !state()) toggle();
        } catch (ReflectiveOperationException e) {
            error("Advanced XRay failed: %s", e.getClass().getSimpleName());
            toggle();
        }
    }
}
