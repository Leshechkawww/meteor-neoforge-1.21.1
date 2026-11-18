/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.scanners.Scanners;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class ReflectInit {
    private static final List<Reflections> reflections = new ArrayList<>();
    // NeoForge's union: URLs cannot be scanned by Reflections. Keep core init
    // classes explicit so the same initialization runs in dev and packaged jars.
    private static final Class<?>[] CORE_INIT_CLASSES = {
        meteordevelopment.meteorclient.pathing.PathManagers.class,
        meteordevelopment.meteorclient.commands.Commands.class,
        meteordevelopment.meteorclient.renderer.PostProcessRenderer.class,
        meteordevelopment.meteorclient.renderer.GL.class,
        meteordevelopment.meteorclient.renderer.Fonts.class,
        meteordevelopment.meteorclient.utils.world.BlockUtils.class,
        meteordevelopment.meteorclient.utils.world.BlockIterator.class,
        meteordevelopment.meteorclient.renderer.Renderer2D.class,
        meteordevelopment.meteorclient.utils.Utils.class,
        meteordevelopment.meteorclient.renderer.Shaders.class,
        meteordevelopment.meteorclient.utils.render.RenderUtils.class,
        meteordevelopment.meteorclient.utils.network.MeteorExecutor.class,
        meteordevelopment.meteorclient.utils.network.Capes.class,
        meteordevelopment.meteorclient.gui.tabs.Tabs.class,
        meteordevelopment.meteorclient.utils.render.postprocess.PostProcessShaders.class,
        meteordevelopment.meteorclient.utils.render.color.RainbowColors.class,
        meteordevelopment.meteorclient.utils.render.postprocess.ChamsShader.class,
        meteordevelopment.meteorclient.utils.render.PlayerHeadUtils.class,
        meteordevelopment.meteorclient.utils.misc.Names.class,
        meteordevelopment.meteorclient.utils.misc.MeteorStarscript.class,
        meteordevelopment.meteorclient.utils.player.Rotations.class,
        meteordevelopment.meteorclient.utils.player.EChestMemory.class,
        meteordevelopment.meteorclient.utils.player.ChatUtils.class,
        meteordevelopment.meteorclient.utils.misc.FakeClientPlayer.class,
        meteordevelopment.meteorclient.utils.misc.CPSUtils.class,
        meteordevelopment.meteorclient.gui.renderer.GuiRenderer.class,
        meteordevelopment.meteorclient.gui.GuiThemes.class
    };

    private ReflectInit() {
    }

    public static void registerPackages() {
        for (MeteorAddon addon : AddonManager.ADDONS) {
            try {
                add(addon);
            } catch (AbstractMethodError e) {
                throw new RuntimeException("Addon \"%s\" is too old and cannot be ran.".formatted(addon.name), e);
            }
        }
    }

    private static void add(MeteorAddon addon) {
        String pkg = addon.getPackage();
        if (pkg == null || pkg.isBlank()) return;

        // ModLauncher's module class loader is not part of the conventional Java
        // class path, so Reflections cannot infer the mod's URL on NeoForge.
        // Supplying the addon's own code source keeps annotation discovery working
        // both in the development classes directory and in the built mod jar.
        ConfigurationBuilder configuration = new ConfigurationBuilder()
            .forPackages(pkg)
            .setScanners(Scanners.MethodsAnnotated);

        if (addon.getClass().getProtectionDomain().getCodeSource() != null) {
            configuration.addUrls(addon.getClass().getProtectionDomain().getCodeSource().getLocation());
        }

        reflections.add(new Reflections(configuration));
    }

    public static void init(Class<? extends Annotation> annotation) {
        Set<Method> coreTasks = new HashSet<>();
        for (Class<?> clazz : CORE_INIT_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(annotation)) coreTasks.add(method);
            }
        }
        runTasks(annotation, coreTasks);
        for (Reflections reflection : reflections) {
            Set<Method> initTasks = reflection.getMethodsAnnotatedWith(annotation);
            if (initTasks != null) runTasks(annotation, initTasks);
        }
    }

    private static void runTasks(Class<? extends Annotation> annotation, Set<Method> initTasks) {
        Map<Class<?>, List<Method>> byClass = initTasks.stream().collect(Collectors.groupingBy(Method::getDeclaringClass));
        Set<Method> left = new HashSet<>(initTasks);
        for (Method m; (m = left.stream().findAny().orElse(null)) != null; ) {
            reflectInit(m, annotation, left, byClass);
        }
    }

    private static <T extends Annotation> void reflectInit(Method task, Class<T> annotation, Set<Method> left, Map<Class<?>, List<Method>> byClass) {
        left.remove(task);

        for (Class<?> clazz : getDependencies(task, annotation)) {
            for (Method m : byClass.getOrDefault(clazz, Collections.emptyList())) {
                if (left.contains(m)) {
                    reflectInit(m, annotation, left, byClass);
                }
            }
        }

        try {
            task.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Error running @%s task '%s.%s'".formatted(annotation.getSimpleName(), task.getDeclaringClass().getSimpleName(), task.getName()), e);
        } catch (NullPointerException e) {
            throw new RuntimeException("Method \"%s\" using Init annotations from non-static context".formatted(task.getName()), e);
        }
    }

    private static <T extends Annotation> Class<?>[] getDependencies(Method task, Class<T> annotation) {
        T init = task.getAnnotation(annotation);

        return switch (init) {
            case PreInit pre -> pre.dependencies();
            case PostInit post -> post.dependencies();
            default -> new Class<?>[]{};
        };
    }
}
