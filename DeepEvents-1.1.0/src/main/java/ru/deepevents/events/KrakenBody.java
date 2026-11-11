package ru.deepevents.events;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.deepevents.core.Fx;
import ru.deepevents.core.Msg;

import org.bukkit.Bukkit;

/**
 * ТЕЛО КРАКЕНА (BlockDisplay-скульптура над боссом):
 *  - купол-голова (warp wart, масштаб 3.8x3.2x3.8) с вращением и «дыханием»;
 *  - корона/мантия сверху, повёрнутая на 45°;
 *  - ДВА СВЕТЯЩИХСЯ ГЛАЗА (sea lantern, максимальная яркость) с миганием;
 *  - клюв из тёмного призмарина;
 *  - ЯДРО: свечение под головой, открывается, когда щит пал (окно урона).
 *
 * Босс (ElderGuardian) находится ВНУТРИ головы — удары по боссу выглядят
 * как удары по телу Кракена. Бюджет: 5 display-сущностей, телепорты
 * каждые 2 тика с интерполяцией — дёшево.
 */
public final class KrakenBody {

    private final JavaPlugin plugin;
    private final Location center;      // центр арены (поверхность воды)
    private final BlockDisplay head;
    private final BlockDisplay crown;
    private final BlockDisplay eyeL;
    private final BlockDisplay eyeR;
    private final BlockDisplay beak;
    private final BlockDisplay core;
    private final BukkitTask task;

    private long t;
    private boolean coreOpen;
    private int blinkTimer = 100 + (int) (Math.random() * 60);

    private static final Vector3f HEAD_SCALE = new Vector3f(3.8f, 3.2f, 3.8f);
    private static final Vector3f CROWN_SCALE = new Vector3f(2.3f, 1.5f, 2.3f);
    private static final Vector3f EYE_SCALE = new Vector3f(0.62f, 0.62f, 0.62f);
    private static final Vector3f BEAK_SCALE = new Vector3f(1.0f, 0.8f, 1.0f);

    public KrakenBody(JavaPlugin plugin, Location center) {
        this.plugin = plugin;
        this.center = center.clone();
        World w = center.getWorld();

        BlockData skin = Material.WARPED_WART_BLOCK.createBlockData();
        BlockData crownData = Material.SHROOMLIGHT.createBlockData();
        BlockData eyeData = Material.SEA_LANTERN.createBlockData();
        BlockData beakData = Material.DARK_PRISMARINE.createBlockData();
        BlockData coreData = Material.GLOWSTONE.createBlockData();

        head = spawn(w, center, skin, HEAD_SCALE, 10);
        crown = spawn(w, center, crownData, CROWN_SCALE, 11);
        eyeL = spawn(w, center, eyeData, EYE_SCALE, 12);
        eyeR = spawn(w, center, eyeData, EYE_SCALE, 12);
        beak = spawn(w, center, beakData, BEAK_SCALE, 11);
        core = spawn(w, center, coreData, new Vector3f(0.001f, 0.001f, 0.001f), 10);

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 4L, 2L);
    }

    private BlockDisplay spawn(World w, Location loc, BlockData data, Vector3f scale, int brightness) {
        return w.spawn(loc, BlockDisplay.class, bd -> {
            bd.setBlock(data);
            bd.setPersistent(false);
            bd.setTeleportDuration(2);
            bd.setInterpolationDuration(2);
            bd.setInterpolationDelay(-1);
            bd.setBrightness(new Display.Brightness(brightness, brightness));
            bd.setShadowRadius(0);
            bd.setTransformation(new Transformation(new Vector3f(),
                    new Quaternionf(), scale, new Quaternionf()));
        });
    }

    /** Окно урона: показать раскалённое ядро под головой. */
    public void setCoreOpen(boolean open) {
        this.coreOpen = open;
    }

    private void tick() {
        t += 2;
        World w = center.getWorld();
        if (w == null) return;

        // «дыхание» + медленное вращение
        double yaw = Math.toRadians((t * 0.45) % 360);
        float breath = (float) (1.0 + 0.02 * Math.sin(t * 0.07));
        double headY = 2.4 + Math.sin(t * 0.05) * 0.35;

        // голова: вращение через Transformation (интерполяция — плавно)
        head.teleport(center);
        head.setTransformation(new Transformation(
                new Vector3f(0f, (float) headY, 0f),
                new Quaternionf().rotationY((float) -yaw),
                new Vector3f(HEAD_SCALE.x * breath, HEAD_SCALE.y * breath, HEAD_SCALE.z * breath),
                new Quaternionf()));

        // корона выше, противовращение
        crown.teleport(center);
        crown.setTransformation(new Transformation(
                new Vector3f(0f, (float) (headY + 2.1), 0f),
                new Quaternionf().rotationY((float) (yaw * 1.6)).rotationZ((float) Math.toRadians(45)),
                CROWN_SCALE, new Quaternionf()));

        // глаза на лицевой стороне (направление yaw), с миганием
        blinkTimer -= 2;
        boolean blinking = blinkTimer < 8;
        if (blinkTimer <= 0) blinkTimer = 120 + (int) (Math.random() * 80);
        float eyeY = (float) (blinking ? 0.10 : 0.62);
        double fx = Math.cos(yaw), fz = Math.sin(yaw);          // вперёд
        double px = -fz, pz = fx;                                // вбок
        placeEye(eyeL, fx, fz, px, pz, headY + 0.5, eyeY);
        placeEye(eyeR, fx, fz, -px, -pz, headY + 0.5, eyeY);

        // клюв снизу-спереди
        beak.teleport(center.clone().add(fx * 1.7, headY - 1.7, fz * 1.7));

        // ядро: видно только в окно урона
        core.teleport(center.clone().add(0, headY - 0.6, 0));
        float cs = coreOpen ? 1.15f : 0.001f;
        core.setTransformation(new Transformation(new Vector3f(),
                new Quaternionf(), new Vector3f(cs, cs * 0.7f, cs), new Quaternionf()));

        // аура: свечение вокруг головы + пузыри снизу (каждые 8 тиков)
        if (t % 8 == 0 && Fx.near(center, 120)) {
            w.spawnParticle(Particle.NAUTILUS, center.clone().add(0, headY, 0), 3, 2.6, 1.8, 2.6, 0.15);
            w.spawnParticle(Particle.BUBBLE_COLUMN_UP, center.clone(), 6, 2.2, 0.6, 2.2, 0.02);
            w.spawnParticle(Particle.END_ROD,
                    center.clone().add(fx * 1.7, headY + 0.5, fz * 1.7), 1, 0.1, 0.1, 0.1, 0.01);
            if (coreOpen) {
                Fx.dust(w, center.clone().add(0, headY - 0.6, 0), 4, 0.9, 0.5, 0.9, 0.05,
                        Color.fromRGB(94, 234, 212), 3.2f);
            }
        }
    }

    private void placeEye(BlockDisplay eye, double fx, double fz, double px, double pz,
                          double y, float scale) {
        eye.teleport(center.clone().add(fx * 1.45 + px * 1.05, y, fz * 1.45 + pz * 1.05));
        eye.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    public void stop() {
        task.cancel();
        BlockDisplay[] all = {head, crown, eyeL, eyeR, beak, core};
        for (BlockDisplay d : all) {
            if (d != null && d.isValid()) d.remove();
        }
        // финальный всплеск
        if (center.getWorld() != null && Fx.near(center, 96)) {
            center.getWorld().spawnParticle(Particle.POOF, center.clone().add(0, 2.5, 0), 40, 2, 1.5, 2, 0.05);
        }
    }
}
