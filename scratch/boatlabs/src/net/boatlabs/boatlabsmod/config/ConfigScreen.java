/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.isxander.yacl3.api.ConfigCategory
 *  dev.isxander.yacl3.api.Option
 *  dev.isxander.yacl3.api.OptionDescription
 *  dev.isxander.yacl3.api.OptionGroup
 *  dev.isxander.yacl3.api.YetAnotherConfigLib
 *  dev.isxander.yacl3.api.controller.BooleanControllerBuilder
 *  dev.isxander.yacl3.api.controller.ColorControllerBuilder
 *  dev.isxander.yacl3.api.controller.EnumControllerBuilder
 *  dev.isxander.yacl3.api.controller.FloatFieldControllerBuilder
 *  dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder
 *  dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder
 *  dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder
 *  dev.isxander.yacl3.api.controller.TickBoxControllerBuilder
 *  dev.isxander.yacl3.gui.YACLScreen
 *  dev.isxander.yacl3.impl.controller.TickBoxControllerBuilderImpl
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.class_124
 *  net.minecraft.class_2561
 *  net.minecraft.class_437
 */
package net.boatlabs.boatlabsmod.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.impl.controller.TickBoxControllerBuilderImpl;
import java.awt.Color;
import java.util.function.Function;
import net.boatlabs.boatlabsmod.BoatlabsMod;
import net.boatlabs.boatlabsmod.ghost.GhostBoatType;
import net.boatlabs.boatlabsmod.region.render.RegionRenderMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_124;
import net.minecraft.class_2561;
import net.minecraft.class_437;

public final class ConfigScreen
extends YACLScreen {
    public ConfigScreen(class_437 parent) {
        super(ConfigScreen.generate(), parent);
    }

    static YetAnotherConfigLib generate() {
        return YetAnotherConfigLib.createBuilder().title((class_2561)class_2561.method_43470((String)"BoatlabsMod Config")).category(ConfigCategory.createBuilder().name((class_2561)class_2561.method_43470((String)"Boatlabs Servers")).option(ConfigScreen.createVisibleRegionOption()).group(ConfigScreen.createTimeTrialHudGroup()).group(ConfigScreen.createLineRenderGroup()).group(ConfigScreen.createGhostOptionGroup()).build()).category(ConfigCategory.createBuilder().name((class_2561)class_2561.method_43470((String)"Build Server")).option(ConfigScreen.ghostCollisionToggle()).build()).save(() -> BoatlabsMod.config.saveGuarded()).build();
    }

    static OptionGroup createTimeTrialHudGroup() {
        return OptionGroup.createBuilder().name((class_2561)class_2561.method_43470((String)"Time Trial Timer")).option(ConfigScreen.createTimeTrialTimerEnabledOption()).option(ConfigScreen.createTimeTrialTimerPositionOption()).option(ConfigScreen.createTimeTrialTimerBackgroundOpacity()).option(ConfigScreen.createTimeTrialTimerScaleOption()).build();
    }

    static Option<Boolean> createTimeTrialTimerEnabledOption() {
        return Option.createBuilder().name((class_2561)class_2561.method_43470((String)"Use Time Trial Timer")).description(ConfigScreen.lovelyOptionDescription("Enables the TT Timer", b -> b != false ? "Time Trial Timer is enabled." : "Time Trial Timer is disabled.")).binding((Object)true, () -> BoatlabsMod.config.useTimeTrialTimer, b -> {
            BoatlabsMod.config.useTimeTrialTimer = b;
        }).controller(TickBoxControllerBuilder::create).build();
    }

    static Option<Integer> createTimeTrialTimerPositionOption() {
        return Option.createBuilder().name((class_2561)class_2561.method_43470((String)"Y position")).description(ConfigScreen.lovelyOptionDescription("Sets the Y position of the TT Timer", value -> "The TT Timer is placed " + value + "% of the screen from the top")).controller(option -> ((IntegerSliderControllerBuilder)((IntegerSliderControllerBuilder)IntegerSliderControllerBuilder.create((Option)option).range((Number)0, (Number)100)).step((Number)1)).formatValue(val -> class_2561.method_43470((String)val.toString().concat("%")))).binding((Object)85, () -> (int)(BoatlabsMod.config.timeTrialTimerYPosition * 100.0f), val -> {
            BoatlabsMod.config.timeTrialTimerYPosition = (float)val.intValue() / 100.0f;
        }).build();
    }

    static Option<Integer> createTimeTrialTimerBackgroundOpacity() {
        return Option.createBuilder().name((class_2561)class_2561.method_43470((String)"Background Opacity")).description(ConfigScreen.lovelyOptionDescription("Sets how transparent the timer's background is.", val -> "The timer's background is " + val + "% opaque.")).controller(option -> ((IntegerSliderControllerBuilder)((IntegerSliderControllerBuilder)IntegerSliderControllerBuilder.create((Option)option).range((Number)0, (Number)100)).step((Number)1)).formatValue(val -> class_2561.method_43470((String)val.toString().concat("%")))).binding((Object)70, () -> (int)(BoatlabsMod.config.timeTrialTimerBackgroundOpacity * 100.0f), val -> {
            BoatlabsMod.config.timeTrialTimerBackgroundOpacity = (float)val.intValue() / 100.0f;
        }).build();
    }

    static Option<Float> createTimeTrialTimerScaleOption() {
        return Option.createBuilder().name((class_2561)class_2561.method_43470((String)"Scale")).description(ConfigScreen.lovelyOptionDescription("Sets how the timer should be sized.", val -> "The timer is " + val + "x the size of the default timer.")).controller(option -> ((FloatFieldControllerBuilder)FloatFieldControllerBuilder.create((Option)option).range((Number)Float.valueOf(Float.MIN_NORMAL), (Number)Float.valueOf(Float.MAX_VALUE))).formatValue(val -> class_2561.method_43470((String)String.format("%3.2fx", val)))).binding((Object)Float.valueOf(1.0f), () -> Float.valueOf(BoatlabsMod.config.timeTrialTimerScale), val -> {
            BoatlabsMod.config.timeTrialTimerScale = val.floatValue();
        }).build();
    }

    static Option<RegionRenderMode> createVisibleRegionOption() {
        return Option.createBuilder().name((class_2561)class_2561.method_43470((String)"Track Region Render Mode")).description(ConfigScreen.lovelyOptionDescription("Determines how track regions should be rendered", b -> b.behaviour)).binding((Object)RegionRenderMode.NONE, () -> BoatlabsMod.config.regionRenderMode, val -> {
            BoatlabsMod.config.regionRenderMode = val;
        }).controller(opt -> EnumControllerBuilder.create((Option)opt).enumClass(RegionRenderMode.class).formatValue(r -> class_2561.method_43470((String)r.name))).build();
    }

    static OptionGroup createLineRenderGroup() {
        return OptionGroup.createBuilder().name((class_2561)class_2561.method_43470((String)"Lines")).option(ConfigScreen.createPersonalBestLineRenderOption()).option(ConfigScreen.createPersonalBestLineColorOption()).build();
    }

    static Option<Boolean> createPersonalBestLineRenderOption() {
        return Option.createBuilder().name((class_2561)class_2561.method_43470((String)"Render Personal Best Line")).description(ConfigScreen.lovelyOptionDescription("Renders your personal best as a line", b -> b != false ? "Your personal best line will be visible." : "Your personal best line will be hidden.")).controller(TickBoxControllerBuilder::create).binding((Object)true, () -> BoatlabsMod.config.renderPersonalBestLine, val -> {
            BoatlabsMod.config.renderPersonalBestLine = val;
        }).build();
    }

    static Option<Color> createPersonalBestLineColorOption() {
        return Option.createBuilder().name((class_2561)class_2561.method_43470((String)"Personal Best Line Color")).description(ConfigScreen.lovelyOptionDescription("The color of your personal best line.", c -> "")).controller(opt -> ColorControllerBuilder.create((Option)opt).allowAlpha(true)).binding((Object)new Color(-1610547456), () -> new Color(BoatlabsMod.config.personalBestLineColor, true), color -> {
            BoatlabsMod.config.personalBestLineColor = color.getRGB();
        }).build();
    }

    static OptionGroup createGhostOptionGroup() {
        return OptionGroup.createBuilder().name((class_2561)class_2561.method_43470((String)"Ghosts")).option(ConfigScreen.createPersonalBestGhostEnabledOption()).option(ConfigScreen.createGhostOpacityOption()).option(ConfigScreen.createGhostPlayerToggle()).option(ConfigScreen.createGhostBoatTypeOption()).option(ConfigScreen.createGhostPlaybackOffsetOption()).build();
    }

    static Option<Boolean> createPersonalBestGhostEnabledOption() {
        return Option.createBuilder().name((class_2561)class_2561.method_43470((String)"Render Personal Best Ghost")).description(ConfigScreen.lovelyOptionDescription("Renders your personal best as a ghost driver", b -> b != false ? "Your personal best ghost will be visible." : "Your personal best ghost will be hidden.")).controller(TickBoxControllerBuilder::create).binding((Object)false, () -> BoatlabsMod.config.renderPersonalBestGhost, val -> {
            BoatlabsMod.config.renderPersonalBestGhost = val;
        }).build();
    }

    static Option<Float> createGhostOpacityOption() {
        return Option.createBuilder().name((class_2561)class_2561.method_43470((String)"Ghost Opacity")).description(ConfigScreen.lovelyOptionDescription("Sets how see-through ghosts are.", val -> "Ghosts will be " + val + "% opaque.")).controller(opt -> ((FloatSliderControllerBuilder)((FloatSliderControllerBuilder)FloatSliderControllerBuilder.create((Option)opt).range((Number)Float.valueOf(0.0f), (Number)Float.valueOf(100.0f))).step((Number)Float.valueOf(1.0f))).formatValue(val -> class_2561.method_43470((String)String.format("%2.0f", val).concat("%")))).binding((Object)Float.valueOf(50.0f), () -> Float.valueOf(BoatlabsMod.config.ghostOpacity * 100.0f), val -> {
            BoatlabsMod.config.ghostOpacity = val.floatValue() / 100.0f;
        }).build();
    }

    static Option<GhostBoatType> createGhostBoatTypeOption() {
        return Option.createBuilder().name((class_2561)class_2561.method_43470((String)"Ghost Boat Type")).description(ConfigScreen.lovelyOptionDescription("Sets the boat type of your ghost", v -> "Your ghost's boat type will be " + v.getDisplayName().getString())).controller(opt -> EnumControllerBuilder.create((Option)opt).enumClass(GhostBoatType.class).formatValue(GhostBoatType::getDisplayName)).binding((Object)GhostBoatType.CHERRY, () -> BoatlabsMod.config.ghostBoatType, v -> {
            BoatlabsMod.config.ghostBoatType = v;
        }).build();
    }

    static Option<Boolean> createGhostPlayerToggle() {
        return Option.createBuilder().name((class_2561)class_2561.method_43470((String)"Visible ghost player")).description(ConfigScreen.lovelyOptionDescription("Sets whether the ghost player is visible or not", b -> b != false ? "The ghost player will be visible" : "The ghost player will be invisible")).controller(TickBoxControllerBuilderImpl::new).binding((Object)true, () -> BoatlabsMod.config.ghostPlayerVisible, v -> {
            BoatlabsMod.config.ghostPlayerVisible = v;
        }).build();
    }

    static Option<Integer> createGhostPlaybackOffsetOption() {
        return Option.createBuilder().name((class_2561)class_2561.method_43470((String)"Ghost Playback Offset")).description(ConfigScreen.lovelyOptionDescription("Sets how early or late the ghost will begin playing", val -> {
            if (val > 0) {
                return "Ghost playback will be " + val * 50 + "ms ahead of you.";
            }
            if (val < 0) {
                return "Ghost playback will be " + -val.intValue() * 50 + "ms behind you.";
            }
            return "Ghost playback will be in time with you";
        })).controller(opt -> IntegerFieldControllerBuilder.create((Option)opt).formatValue(val -> class_2561.method_43470((String)(val + " ticks")))).binding((Object)0, () -> BoatlabsMod.config.ghostPlaybackOffset, val -> {
            BoatlabsMod.config.ghostPlaybackOffset = val;
        }).build();
    }

    static Option<Boolean> ghostCollisionToggle() {
        boolean available = !FabricLoader.getInstance().isModLoaded("openboatutils");
        String desc = "Sets whether you should be able to collide with your ghost or not. This is best used for testing boattaps.";
        return Option.createBuilder().name((class_2561)class_2561.method_43470((String)"Ghost Collisions")).description(available ? ConfigScreen.lovelyOptionDescription(desc, b -> b != false ? "Ghost collisions are enabled on the build server." : "Ghost collisions are disabled.") : ConfigScreen.optionDescriptionConditionUnmet(desc, "The OpenBoatUtils mod must not be installed for this feature to work.")).controller(opt -> BooleanControllerBuilder.create((Option)opt).onOffFormatter().coloured(true)).binding((Object)false, () -> BoatlabsMod.config.allowGhostCollision, b -> {
            BoatlabsMod.config.allowGhostCollision = b;
        }).available(available).build();
    }

    static <T> Function<T, OptionDescription> lovelyOptionDescription(String desc, Function<T, String> liveDescription) {
        return val -> OptionDescription.of((class_2561[])new class_2561[]{class_2561.method_43470((String)desc), class_2561.method_43473(), class_2561.method_43470((String)((String)liveDescription.apply(val))).method_27692(class_124.field_1056)});
    }

    static <T> Function<T, OptionDescription> optionDescriptionConditionUnmet(String desc, String requirement) {
        return val -> OptionDescription.of((class_2561[])new class_2561[]{class_2561.method_43470((String)requirement).method_54663(0xFF2222).method_27692(class_124.field_1067), class_2561.method_43473(), class_2561.method_43470((String)desc)});
    }
}

