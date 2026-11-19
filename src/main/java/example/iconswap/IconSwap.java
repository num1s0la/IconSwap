package example.iconswap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Surface;
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
            apply(mc);
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
                mc.execute(() -> apply(mc));
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

    static long windowHandle(Minecraft mc) {
        try {
            Object w = mc.getWindow();
            for (String name : new String[]{"handle", "getHandle", "getWindow"}) {
                try {
                    Object r = w.getClass().getMethod(name).invoke(w);
                    if (r instanceof Long l && l != 0L) return l;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception e) { e.printStackTrace(); }
        return SDLKeyboard.SDL_GetKeyboardFocus();
    }

    static void apply(Minecraft mc) {
        try {
            Path f = find();
            if (f == null) return;
            BufferedImage img = ImageIO.read(f.toFile());
            if (img == null) return;
            long win = windowHandle(mc);
            if (win == 0L) return;
            int w = img.getWidth(), h = img.getHeight();
            int[] px = img.getRGB(0, 0, w, h, null, 0, w);
            ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
            for (int p : px) {
                buf.put((byte) (p >> 16)).put((byte) (p >> 8))
                   .put((byte) p).put((byte) (p >>> 24));
            }
            buf.flip();
            SDL_Surface surf = SDLSurface.SDL_CreateSurfaceFrom(
                w, h, SDLPixels.SDL_PIXELFORMAT_RGBA32, buf, w * 4);
            if (surf != null) {
                SDLVideo.SDL_SetWindowIcon(win, surf);
                SDLSurface.SDL_DestroySurface(surf);
            }
            MemoryUtil.memFree(buf);
        } catch (Exception e) { e.printStackTrace(); }
    }
}
