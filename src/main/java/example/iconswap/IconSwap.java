package example.iconswap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.stream.Stream;

public class IconSwap implements ClientModInitializer {
    static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("icons");

    @Override
    public void onInitializeClient() {
        try { Files.createDirectories(DIR); } catch (Exception ignored) {}
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            apply();
            Thread t = new Thread(() -> watch(mc), "icon-watcher");
            t.setDaemon(true);
            t.start();
        });
    }

    static void watch(Minecraft mc) {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            DIR.register(ws,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
            while (true) {
                WatchKey k = ws.take();
                k.pollEvents();
                k.reset();
                Thread.sleep(300);
                mc.execute(IconSwap::apply);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    static Path find() throws Exception {
        Path pref = DIR.resolve("icon.png");
        if (Files.exists(pref)) return pref;
        try (Stream<Path> s = Files.list(DIR)) {
            return s.filter(p -> p.toString().toLowerCase().endsWith(".png"))
                    .sorted().findFirst().orElse(null);
        }
    }

    static void apply() {
        try {
            Path f = find();
            if (f == null) return;
            BufferedImage img = ImageIO.read(f.toFile());
            if (img == null) return;
            int w = img.getWidth(), h = img.getHeight();
            int[] px = img.getRGB(0, 0, w, h, null, 0, w);
            ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
            for (int p : px) {
                buf.put((byte) (p >> 16)).put((byte) (p >> 8))
                   .put((byte) p).put((byte) (p >>> 24));
            }
            buf.flip();
            try (MemoryStack st = MemoryStack.stackPush()) {
                GLFWImage.Buffer ib = GLFWImage.malloc(1, st);
                ib.position(0).width(w).height(h).pixels(buf);
                GLFW.glfwSetWindowIcon(GLFW.glfwGetCurrentContext(), ib);
            }
            MemoryUtil.memFree(buf);
        } catch (Exception e) { e.printStackTrace(); }
    }
}
