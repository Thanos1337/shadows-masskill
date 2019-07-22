
package shadows;

import com.google.common.reflect.TypeToken;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.*;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLEmbeddedChannel;
import cpw.mods.fml.common.network.FMLOutboundHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;
import ru.batthert.common.network.packets.PacketShoot;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opengl.GL11.*;

public class ThanosHack implements ICommand {

    //специально для умников которые будут переписывать, держи быструю смену автора
    private static final String AUTHOR = "Thanos1337";
    //и эту хуйню тоже смени, ты же умненький у нас
    private static final String PREFIX = "thanos_";

    //если ебланище которое писало мод решит поменять channel'ы - сюда вбить новые названия
    //мне похуям как ты из обфусцированного мода будешь их дёргать
    //иди плати бабло кодерам читов чтоб они тебе это сделали
    //автор этого говночита обновлять нихуя не будет, соси
    //private static final String CH_SHADOWS = "sp";
    private static final String CH_WEAPONS = "mymodid";

    //а это специально для ебланищ-читописцев, чтоб быстро нашёл где пакет стрельбы создаётся
    private static IMessage createShootPacket(int entityId) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity location = null;
        if (enabled(NAME_R_T_L)) {
            location = mc.theWorld.getEntityByID(entityId);
        }
        if (location == null) {
            location = mc.thePlayer;
        }
        double posX = location.posX;
        double posY = location.posY;
        double posZ = location.posZ;
        return new PacketShoot(entityId, true, posX, posY, posZ);
    }

    private static final Map<String, Boolean> enabledFunctions = new HashMap<>();

    //чо, ебать, никогда в жизни не видел константы выкинутые сюда, а не въёбаные посреди кода?
    private static final String STATE_ENABLE = "ВКЛ";
    private static final String STATE_DISABLE = "ВЫКЛ";
    private static final String NAME_R_T_L = "Реальные координаты цели: ";
    private static final String NAME_SNAP = "SNAP: ";
    private static final String NAME_KILL = "KILL: ";
    //прикинь, это можно и с INTами делать
    private static final int HIT_KILL = 0;
    private static final int HIT_SNAP = 1;

    //а тут уже нахуй пошёл, ниже будут всего 2 коммента, переебёшься
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<String, Integer> lastSeenEntityID = new HashMap<>();
    private final Set<String> whitelist = new HashSet<>();
    private EntityClientPlayerMP player;
    private KeyBinding key_snap;
    private KeyBinding key_kill;
    private KeyBinding key_sh;
    private int packetsPerTick = 1;
    private int shMultiplier = 3;
    private int hit_timer;
    private int hit_type;

    public ThanosHack() {
        register(MinecraftForge.EVENT_BUS, this);
        register(FMLCommonHandler.instance().bus(), this);

        key_snap = new KeyBinding(PREFIX + "snap", Keyboard.KEY_C, AUTHOR);
        key_kill = new KeyBinding(PREFIX + "kill", Keyboard.KEY_B, AUTHOR);
        key_sh = new KeyBinding(PREFIX + "sh", Keyboard.KEY_LMETA, AUTHOR);

        ClientRegistry.registerKeyBinding(key_snap);
        ClientRegistry.registerKeyBinding(key_kill);
        ClientRegistry.registerKeyBinding(key_sh);
        ClientCommandHandler.instance.registerCommand(this);
    }

    private void readWhitelist() {
        whitelist.clear();
        whitelist.add(mc.getSession().getUsername().toLowerCase());
        try {
            List<String> whitelist = Files.readAllLines(Paths.get(System.getProperty("user.home"), "Desktop", "ThanosWhitelist.txt"));
            whitelist.removeIf(name -> !this.whitelist.add(name.toLowerCase()));
            log("Вайтлист перезагружен: " + whitelist);
        } catch (IOException e) {
            log("Создай на рабочем столе ThanosWhitelist(.txt) и впиши туда ники друзей.");
            log("НЕ ПИШИ РАСШИРЕНИЕ .txt ЕСЛИ У ТЕБЯ СКРЫТЫ РАСШИРЕНИЯ ФАЙЛОВ, ИДИОТИЩЕ!");
            log("/thanos reload перезагружает вайтлист");
        }
    }

    @SubscribeEvent
    public void shadows(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.START) {
            if (player != mc.thePlayer) {
                player = mc.thePlayer;
                enabledFunctions.clear();
            }
            if (player != null) {
                if (whitelist.isEmpty()) {
                    readWhitelist();
                }
                tick();
            }
            if (hit_timer > 0) {
                --hit_timer;
            }
        }
    }

    @SubscribeEvent
    public void project(TickEvent.RenderTickEvent event) {
        if (hit_timer > 0) {
            ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int x = res.getScaledWidth() / 2;
            int y = res.getScaledHeight() / 2;
            glLineWidth(2);
            glDisable(GL_TEXTURE_2D);
            if (hit_type == HIT_SNAP) {
                glColor4f(1, 0, 0, 1);
            } else {
                glColor4f(1, 1, 1, 1);
            }
            glBegin(GL_LINES);
            glVertex2i(x - 5, y - 5);
            glVertex2i(x + 5, y + 5);
            glVertex2i(x - 5, y + 5);
            glVertex2i(x + 5, y - 5);
            glEnd();
            glEnable(GL_TEXTURE_2D);
            glColor4f(1, 1, 1, 1);
        }
    }

    @SubscribeEvent
    public void sucks(RenderWorldLastEvent event) {
        if (player != null) {
            List<EntityPlayer> players = mc.theWorld.playerEntities;
            Vec3 look = player.getLook(event.partialTicks);
            glDisable(GL_LIGHTING);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_TEXTURE_2D);
            players.forEach(lox -> {
                double x = player.posX + look.xCoord;
                double y = player.posY + player.getEyeHeight() + look.yCoord;
                double z = player.posZ + look.zCoord;
                if (lox != player && lox.isEntityAlive()) {
                    glPushMatrix();
                    glTranslatef((float) -RenderManager.renderPosX, (float) -RenderManager.renderPosY, (float) -RenderManager.renderPosZ);
                    int color;
                    if (whitelist.contains(lox.getCommandSenderName().toLowerCase())) {
                        color = 0xFF55FF55;
                    } else {
                        color = 0xFFFF5555;
                    }
                    glLineWidth(1);
                    Tessellator tessellator = Tessellator.instance;
                    tessellator.startDrawing(GL_LINES);
                    tessellator.setColorOpaque_I(color);
                    tessellator.addVertex(x, y, z);
                    tessellator.addVertex(lox.posX, lox.posY + lox.getEyeHeight(), lox.posZ);
                    tessellator.draw();
                    RenderGlobal.drawOutlinedBoundingBox(lox.boundingBox, color);
                    glPopMatrix();
                }
            });
            glEnable(GL_LIGHTING);
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_TEXTURE_2D);
        }
    }

    private void tick() {
        updatePlayerIDs();
        //так, внимание, долбоёб, читай и внимай эту хуйню своим мелким котелком
        //функция isPressed() возвращает TRUE всего 1 СУКА раз за нажатие кнопки
        //функция getIsKeyPressed() возвращает TRUE всё время пока кнопка ЗАжата
        if (key_sh.getIsKeyPressed()) {
            for (int i = 0; i < shMultiplier; i++) {
                mc.thePlayer.onUpdate();
            }
        }
        if (key_snap.getIsKeyPressed() || enabled(NAME_SNAP)) {
            snap();
        }
        if (key_kill.getIsKeyPressed() || enabled(NAME_KILL)) {
            kill();
        }
        player.addPotionEffect(new PotionEffect(Potion.nightVision.id, 1000));
    }

    private void updatePlayerIDs() {
        List<GuiPlayerInfo> tabPlayers = mc.getNetHandler().playerInfoList;
        tabPlayers.forEach(info -> {
            if (!whitelist.contains(info.name.toLowerCase())) {
                EntityPlayer player = mc.theWorld.getPlayerEntityByName(info.name);
                if (player != null) {
                    lastSeenEntityID.put(info.name, player.getEntityId());
                }
            } else {
                lastSeenEntityID.remove(info.name);
            }
        });
        lastSeenEntityID.keySet().removeIf(name -> {
            boolean online = false;
            for (GuiPlayerInfo info : tabPlayers) {
                if (name.equals(info.name)) {
                    online = true;
                    break;
                }
            }
            return !online;
        });
    }

    private void kill() {
        List<EntityPlayer> players = mc.theWorld.playerEntities;
        players.sort(Comparator.comparingDouble(value -> value.getDistanceSqToEntity(player)));
        players.forEach(lox -> {
            if (lox.isEntityAlive() && !whitelist.contains(lox.getCommandSenderName().toLowerCase())) {
                IMessage slap = createShootPacket(lox.getEntityId());
                for (int i = 0; i < packetsPerTick; i++) {
                    send(CH_WEAPONS, slap);
                }
                hit(2, HIT_KILL);
            }
        });
    }

    private void snap() {
        lastSeenEntityID.values().forEach(entityID -> {
            for (int p = 0; p < packetsPerTick; p++) {
                send(CH_WEAPONS, createShootPacket(entityID));
            }
            hit(5, HIT_SNAP);
        });
    }

    private void hit(int ticks, int type) {
        hit_timer = ticks;
        hit_type = type;
    }

    private void log(Object x) {
        player.addChatMessage(new ChatComponentText(String.valueOf(x)));
    }

    private void send(String channel, IMessage message) {
        FMLEmbeddedChannel ch = NetworkRegistry.INSTANCE.getChannel(channel, Side.CLIENT);
        ch.attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.TOSERVER);
        ch.writeAndFlush(message).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    private void toggleAndLog(String function) {
        log(function + (toggle(function) ? STATE_ENABLE : STATE_DISABLE));
    }

    private static boolean toggle(String function) {
        return Boolean.TRUE == enabledFunctions.compute(function, (s, current) -> current != Boolean.TRUE);
    }

    private static boolean enabled(String function) {
        return Boolean.TRUE == enabledFunctions.get(function);
    }

    private void register(EventBus bus, Object target) {
        ConcurrentHashMap<Object, ArrayList<IEventListener>> listeners = ReflectionHelper.getPrivateValue(EventBus.class, bus, "listeners");
        Map<Object, ModContainer> listenerOwners = ReflectionHelper.getPrivateValue(EventBus.class, bus, "listenerOwners");
        if (listeners.containsKey(target)) {
            return;
        }
        ModContainer activeModContainer = Loader.instance().getMinecraftModContainer();
        listenerOwners.put(target, activeModContainer);
        ReflectionHelper.setPrivateValue(EventBus.class, bus, listenerOwners, "listenerOwners");
        Set<? extends Class<?>> supers = TypeToken.of(target.getClass()).getTypes().rawTypes();
        for (Method method : target.getClass().getMethods()) {
            for (Class<?> cls : supers) {
                try {
                    Method real = cls.getDeclaredMethod(method.getName(), method.getParameterTypes());
                    if (real.isAnnotationPresent(SubscribeEvent.class)) {
                        Class<?>[] parameterTypes = method.getParameterTypes();
                        Class<?> eventType = parameterTypes[0];
                        try {
                            int busID = ReflectionHelper.getPrivateValue(EventBus.class, bus, "busID");
                            ConcurrentHashMap<Object, ArrayList<IEventListener>> listeners1 = ReflectionHelper.getPrivateValue(EventBus.class, bus, "listeners");
                            Constructor<?> ctr = eventType.getConstructor();
                            ctr.setAccessible(true);
                            Event event = (Event) ctr.newInstance();
                            ASMEventHandler listener = new ASMEventHandler(target, method, activeModContainer);
                            event.getListenerList().register(busID, listener.getPriority(), listener);
                            ArrayList<IEventListener> others = listeners1.get(target);
                            if (others == null) {
                                others = new ArrayList<>();
                                listeners1.put(target, others);
                                ReflectionHelper.setPrivateValue(EventBus.class, bus, listeners1, "listeners");
                            }
                            others.add(listener);
                        } catch (Exception e) {
                        }
                        break;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
    }

    @Override
    public String getCommandName() {
        return "thanos";
    }

    @Override
    public String getCommandUsage(ICommandSender p_71518_1_) {
        return "/thanos snap\n/thanos kill\n/thanos ppt value";
    }

    @Override
    public List getCommandAliases() {
        return null;
    }

    @Override
    public void processCommand(ICommandSender p_71515_1_, String[] args) {
        if (args.length == 1) {
            if ("reload".equals(args[0])) {
                readWhitelist();
            } else if ("snap".equals(args[0])) {
                toggleAndLog(NAME_SNAP);
            } else if ("kill".equals(args[0])) {
                toggleAndLog(NAME_KILL);
            } else if ("rtl".equals(args[0])) {
                toggleAndLog(NAME_R_T_L);
            }
        } else if (args.length == 2) {
            if ("ppt".equals(args[0])) {
                packetsPerTick = MathHelper.clamp_int(Integer.parseUnsignedInt(args[1]), 1, 100);
                log("Пакетов за тик: " + packetsPerTick);
            } else if ("sh".equals(args[0])) {
                shMultiplier = MathHelper.clamp_int(Integer.parseUnsignedInt(args[1]), 1, 10);
                log("Ускорение спидхака: " + shMultiplier);
            }
        }
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender p_71519_1_) {
        return true;
    }

    @Override
    public List addTabCompletionOptions(ICommandSender p_71516_1_, String[] args) {
        return args.length == 0 ? Arrays.asList("snap", "kill", "ppt") : null;
    }

    @Override
    public boolean isUsernameIndex(String[] p_82358_1_, int p_82358_2_) {
        return false;
    }

    @Override
    public int compareTo(Object o) {
        return 0;
    }
}