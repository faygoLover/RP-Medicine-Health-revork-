package faygolover.rpmedicine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import faygolover.rpmedicine.capability.MedicalNbt;
import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.Healing;
import faygolover.rpmedicine.core.InjuryProfile;
import faygolover.rpmedicine.core.Injuries;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Physiology;
import faygolover.rpmedicine.core.WoundType;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.network.Network;
import faygolover.rpmedicine.network.OpenHudEditorPacket;
import faygolover.rpmedicine.server.DownedService;
import faygolover.rpmedicine.server.Medical;
import faygolover.rpmedicine.server.MedicalReports;
import faygolover.rpmedicine.server.Profiler;
import faygolover.rpmedicine.server.StubRegistry;
import faygolover.rpmedicine.server.StubService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Команды ГМа {@code /rpmedicine …} (п. 10 ТЗ). Уровень оператора 2, кроме {@code hud}. */
public final class MedCommand {
    private MedCommand() {}

    private static final SimpleCommandExceptionType NOT_PATIENT = new SimpleCommandExceptionType(Component.translatable("rpmedicine.cmd.not_patient"));
    private static final SimpleCommandExceptionType BAD_TYPE = new SimpleCommandExceptionType(Component.translatable("rpmedicine.cmd.bad_type"));
    private static final SimpleCommandExceptionType BAD_PART = new SimpleCommandExceptionType(Component.translatable("rpmedicine.cmd.bad_part"));
    private static final SimpleCommandExceptionType BAD_DURATION = new SimpleCommandExceptionType(Component.translatable("rpmedicine.cmd.bad_duration"));
    private static final SuggestionProvider<CommandSourceStack> PARTS = (c, b) -> SharedSuggestionProvider.suggest(
            Arrays.stream(BodyPart.VALUES).map(p -> p.id), b);
    private static final SuggestionProvider<CommandSourceStack> PARTS_ALL = (c, b) -> SharedSuggestionProvider.suggest(
            java.util.stream.Stream.concat(Arrays.stream(BodyPart.VALUES).map(p -> p.id), java.util.stream.Stream.of("all")), b);
    private static final SuggestionProvider<CommandSourceStack> TYPES = (c, b) -> SharedSuggestionProvider.suggest(
            Arrays.stream(WoundType.VALUES).map(t -> t.id), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("rpmedicine");
        root.then(Commands.literal("hud").executes(c -> {
            Network.send(c.getSource().getPlayerOrException(), new OpenHudEditorPacket());
            return 1;
        }));
        root.then(op("reload").executes(MedCommand::reload));
        root.then(op("profile").executes(MedCommand::profile));
        root.then(op("inspect").then(Commands.argument("target", EntityArgument.entity()).executes(c -> {
            LivingEntity t = patient(EntityArgument.getEntity(c, "target"));
            for (Component line : MedicalReports.full(t)) c.getSource().sendSuccess(() -> line, false);
            return 1;
        })));
        root.then(op("injure").then(Commands.argument("targets", EntityArgument.entities())
                .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                        .then(Commands.argument("part", StringArgumentType.word()).suggests(PARTS)
                                .executes(c -> injure(c, 20))
                                .then(Commands.argument("severity", DoubleArgumentType.doubleArg(0, 100))
                                        .executes(c -> injure(c, DoubleArgumentType.getDouble(c, "severity"))))))));
        root.then(op("heal").then(Commands.argument("targets", EntityArgument.entities())
                .executes(c -> heal(c, "all"))
                .then(Commands.argument("part", StringArgumentType.word()).suggests(PARTS_ALL)
                        .executes(c -> heal(c, StringArgumentType.getString(c, "part"))))));
        root.then(op("revive").then(Commands.argument("targets", EntityArgument.entities()).executes(MedCommand::revive)));
        root.then(op("kill").then(Commands.argument("targets", EntityArgument.entities()).executes(MedCommand::kill)));
        root.then(op("set").then(Commands.argument("targets", EntityArgument.entities())
                .then(scalar("blood", 0, 20000, (m, v) -> m.bloodVolume = Math.min(v, m.normalBlood(MedicalSettings.get()))))
                .then(scalar("weight", 20, 300, (m, v) -> {
                    double f = m.bloodFraction(MedicalSettings.get());
                    m.weightKg = v;
                    m.bloodVolume = m.normalBlood(MedicalSettings.get()) * f;
                }))
                .then(scalar("height", 100, 250, (m, v) -> m.heightCm = v))
                .then(scalar("brain", 0, 100, (m, v) -> m.brain = Math.max(m.down == MedicalState.Down.CLINICAL ? 1 : 0.5, v)))
                .then(scalar("spo2", 0, 100, (m, v) -> m.spo2 = v))
                .then(scalar("concussion", 0, 100, (m, v) -> m.concussion = v))
                .then(scalar("pain_shock", 0, 1, (m, v) -> m.painShock = v >= 0.5))
                .then(scalar("sepsis", 0, 100, (m, v) -> m.sepsis = v))
                .then(scalar("temperature", 30, 43, (m, v) -> m.bodyTemp = v))
                .then(foreign("bullets", true))
                .then(foreign("fragments", false))));
        root.then(op("time").then(Commands.literal("add").then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("duration", StringArgumentType.word())
                        .executes(c -> timeAdd(c, false))
                        .then(Commands.literal("offline").executes(c -> timeAdd(c, true)))))));
        // Медкарта (второй этап, п. 10): рост, вес, группа, аллергии, хронические состояния.
        root.then(op("card").then(Commands.argument("player", net.minecraft.commands.arguments.GameProfileArgument.gameProfile())
                .then(Commands.argument("field", StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                java.util.List.of("height", "weight", "blood_type", "allergies", "chronic"), b))
                        .then(Commands.argument("value", StringArgumentType.greedyString()).executes(MedCommand::card)))));
        d.register(root);
    }

    private static int card(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var profiles = net.minecraft.commands.arguments.GameProfileArgument.getGameProfiles(c, "player");
        String field = StringArgumentType.getString(c, "field");
        String value = StringArgumentType.getString(c, "value");
        int n = 0;
        for (var gp : profiles) {
            try {
                Component msg = faygolover.rpmedicine.medcard.MedcardService.gmSet(c.getSource().getServer(), gp.getId(), gp.getName(), field, value);
                c.getSource().sendSuccess(() -> msg, true);
                n++;
            } catch (IllegalArgumentException e) {
                c.getSource().sendFailure(Component.translatable("rpmedicine.cmd.card_bad", field, e.getMessage()));
            }
        }
        return n;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(s -> s.hasPermission(2));
    }

    private static LivingEntity patient(Entity e) throws CommandSyntaxException {
        if (e instanceof LivingEntity le && Medical.isPatient(le)) return le;
        throw NOT_PATIENT.create();
    }

    private static List<LivingEntity> patients(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        List<LivingEntity> out = new ArrayList<>();
        // У мёртвого игрока на экране возрождения Forge уже снял capability — такие цели пропускаем.
        for (Entity e : EntityArgument.getEntities(c, "targets"))
            if (e instanceof LivingEntity le && Medical.isPatient(le) && Medical.state(le) != null) out.add(le);
        if (out.isEmpty()) throw NOT_PATIENT.create();
        return out;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        ServerConfig.reloadFromDisk();
        MinecraftServer server = c.getSource().getServer();
        server.reloadResources(server.getPackRepository().getSelectedIds()).thenRun(() ->
                c.getSource().sendSuccess(() -> Component.translatable("rpmedicine.cmd.reloaded"), true));
        return 1;
    }

    private static int profile(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        int stubs = StubRegistry.get(server).size();
        c.getSource().sendSuccess(() -> Component.translatable("rpmedicine.cmd.profile",
                String.format(Locale.ROOT, "%.1f", Profiler.microsPerTick()),
                String.format(Locale.ROOT, "%.2f", Profiler.stepsPerTick()),
                String.format(Locale.ROOT, "%.1f", Profiler.microsPerStep()),
                server.getPlayerCount(), stubs), false);
        return 1;
    }

    private static int injure(CommandContext<CommandSourceStack> c, double severity) throws CommandSyntaxException {
        String typeId = StringArgumentType.getString(c, "type");
        String partId = StringArgumentType.getString(c, "part");
        WoundType type = WoundType.byId(typeId).orElseThrow(() -> BAD_TYPE.create());
        BodyPart part = BodyPart.byId(partId).orElseThrow(() -> BAD_PART.create());
        MedicalSettings s = MedicalSettings.get();
        // Тяжесть задаётся напрямую: урон = тяжесть / 5; осложнения — по правилу датапака для этого типа.
        InjuryProfile prof = profileForType(type);
        double damage = severity / (s.severityPerDamage * Math.max(0.01, prof.severityMultiplier));
        int n = 0;
        for (LivingEntity t : patients(c)) {
            MedicalState m = Medical.state(t);
            InjuryProfile hp = copyAsHitPoint(prof, type);
            Injuries.Report rep = Injuries.apply(m, hp, damage, part, 0, Medical.traits(t), new Random(), s);
            Medical.changed(t);
            n++;
            String outcomes = rep.outcomes.toString().toLowerCase(Locale.ROOT);
            c.getSource().sendSuccess(() -> Component.translatable("rpmedicine.cmd.injured", t.getDisplayName(), typeId, partId,
                    String.format(Locale.ROOT, "%.0f", severity), outcomes), true);
        }
        return n;
    }

    private static InjuryProfile profileForType(WoundType type) {
        InjuryProfile p = faygolover.rpmedicine.data.DamageRules.INSTANCE.byId(
                new net.minecraft.resources.ResourceLocation("rpmedicine", "command/" + type.id));
        return p;
    }

    private static InjuryProfile copyAsHitPoint(InjuryProfile src, WoundType type) {
        InjuryProfile p = new InjuryProfile("command/" + type.id, type, InjuryProfile.Location.HIT_POINT);
        p.severityMultiplier = src.severityMultiplier;
        p.fracture = src.fracture;
        p.openFractureFraction = src.openFractureFraction;
        p.arterial = src.arterial;
        p.internal = src.internal;
        p.foreignBody = src.foreignBody;
        p.foreignBodyMin = src.foreignBodyMin;
        p.foreignBodyMax = src.foreignBodyMax;
        p.concussion = src.concussion;
        p.concussionPerSeverity = src.concussionPerSeverity;
        p.pneumothorax = src.pneumothorax;
        return p;
    }

    private static int heal(CommandContext<CommandSourceStack> c, String partId) throws CommandSyntaxException {
        int n = 0;
        for (LivingEntity t : patients(c)) {
            MedicalState m = Medical.state(t);
            if (partId.equalsIgnoreCase("all")) {
                boolean wasDown = m.isDown();
                m.reset(MedicalSettings.get());
                if (wasDown && t instanceof ServerPlayer sp) DownedService.onWokeUp(sp);
            } else {
                BodyPart part = BodyPart.byId(partId).orElseThrow(() -> BAD_PART.create());
                m.healPart(part);
            }
            Medical.changed(t);
            n++;
            c.getSource().sendSuccess(() -> Component.translatable("rpmedicine.cmd.healed", t.getDisplayName(), partId), true);
        }
        return n;
    }

    private static int revive(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int n = 0;
        MedicalSettings s = MedicalSettings.get();
        for (LivingEntity t : patients(c)) {
            MedicalState m = Medical.state(t);
            boolean wasDown = m.isDown();
            m.heart = MedicalState.Heart.NORMAL;
            m.respiratoryArrest = false;
            m.morphineOverdoseSeconds = 0;
            if (m.pneumo == MedicalState.Pneumo.TENSION) {
                m.pneumo = MedicalState.Pneumo.OPEN;
                m.tensionProgress = 0;
                m.pneumoTimer = s.pneumoSealMaxSeconds;
            }
            m.bloodVolume = Math.max(m.bloodVolume, m.normalBlood(s) * 0.7);
            m.spo2 = Math.max(m.spo2, 92);
            m.pressure = Math.max(m.pressure, Physiology.pressureFromVolume(m.bloodFraction(s), s));
            m.heartRate = Math.max(m.heartRate, s.normalHeartRate);
            m.brain = Math.max(m.brain, 30);
            m.painShock = false;
            m.shockAccum = 0;
            m.concussionKoSeconds = 0;
            m.consciousness = 100;
            m.down = MedicalState.Down.NONE;
            m.wakeSeconds = -1;
            m.knockdownNoTimer = false;
            Medical.changed(t);
            if (wasDown && t instanceof ServerPlayer sp) DownedService.onWokeUp(sp);
            n++;
            c.getSource().sendSuccess(() -> Component.translatable("rpmedicine.cmd.revived", t.getDisplayName()), true);
        }
        return n;
    }

    private static int kill(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int n = 0;
        Entity killer = c.getSource().getEntity();
        for (LivingEntity t : patients(c)) {
            if (t instanceof ServerPlayer sp) DownedService.kill(sp, DownedService.GM_KILL, null);
            else if (t instanceof BodyStubEntity stub) StubService.killStub(stub, DownedService.GM_KILL, killer);
            n++;
            c.getSource().sendSuccess(() -> Component.translatable("rpmedicine.cmd.killed", t.getDisplayName()), true);
        }
        return n;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> scalar(String name, double min, double max, BiConsumer<MedicalState, Double> setter) {
        return Commands.literal(name).then(Commands.argument("value", DoubleArgumentType.doubleArg(min, max)).executes(c -> {
            double v = DoubleArgumentType.getDouble(c, "value");
            int n = 0;
            for (LivingEntity t : patients(c)) {
                setter.accept(Medical.state(t), v);
                Medical.changed(t);
                n++;
                c.getSource().sendSuccess(() -> Component.translatable("rpmedicine.cmd.set", t.getDisplayName(), name,
                        String.format(Locale.ROOT, "%.1f", v)), true);
            }
            return n;
        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> foreign(String name, boolean bullets) {
        return Commands.literal(name).then(Commands.argument("part", StringArgumentType.word()).suggests(PARTS)
                .then(Commands.argument("count", IntegerArgumentType.integer(0, 20)).executes(c -> {
                    BodyPart part = BodyPart.byId(StringArgumentType.getString(c, "part")).orElseThrow(() -> BAD_PART.create());
                    int count = IntegerArgumentType.getInteger(c, "count");
                    int n = 0;
                    for (LivingEntity t : patients(c)) {
                        var ps = Medical.state(t).part(part);
                        if (bullets) ps.bullets = count;
                        else ps.fragments = count;
                        Medical.changed(t);
                        n++;
                        c.getSource().sendSuccess(() -> Component.translatable("rpmedicine.cmd.set", t.getDisplayName(), name + " " + part.id, count), true);
                    }
                    return n;
                })));
    }

    // ------------------------------------------------------------------ time add

    private static final Pattern DURATION = Pattern.compile("(\\d+(?:\\.\\d+)?)([hms])");

    /** Длительность вида 2h, 90m, 1h30m, 600s → секунды. */
    static double parseDuration(String s) throws CommandSyntaxException {
        Matcher mt = DURATION.matcher(s.toLowerCase(Locale.ROOT));
        double total = 0;
        int end = 0;
        while (mt.find()) {
            if (mt.start() != end) throw BAD_DURATION.create();
            double v = Double.parseDouble(mt.group(1));
            total += switch (mt.group(2)) {
                case "h" -> v * 3600;
                case "m" -> v * 60;
                default -> v;
            };
            end = mt.end();
        }
        if (end != s.length() || total <= 0) throw BAD_DURATION.create();
        return total;
    }

    private static int timeAdd(CommandContext<CommandSourceStack> c, boolean offline) throws CommandSyntaxException {
        double seconds = parseDuration(StringArgumentType.getString(c, "duration"));
        MedicalSettings s = MedicalSettings.get();
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
        int n = 0;
        for (ServerPlayer sp : players) {
            MedicalState m = Medical.state(sp);
            if (m == null) continue;
            Healing.fastForward(m, seconds, s);
            Medical.changed(sp);
            n++;
        }
        if (offline) n += timeAddOffline(c.getSource().getServer(), seconds, s);
        int count = n;
        c.getSource().sendSuccess(() -> Component.translatable("rpmedicine.cmd.time_added", count,
                String.format(Locale.ROOT, "%.0f", seconds / 60)), true);
        return n;
    }

    /** Офлайн-игроки с заглушкой: загруженные тела и снимки незагруженных. */
    private static int timeAddOffline(MinecraftServer server, double seconds, MedicalSettings s) {
        StubRegistry reg = StubRegistry.get(server);
        int n = 0;
        for (UUID owner : reg.owners()) {
            StubRegistry.Record r = reg.get(owner);
            if (r == null) continue;
            ServerLevel level = server.getLevel(r.dimension);
            Entity e = level != null ? level.getEntity(r.entityId) : null;
            if (e instanceof BodyStubEntity stub) {
                Healing.fastForward(stub.state(), seconds, s);
                stub.markChanged();
            } else {
                MedicalState m = new MedicalState(s);
                MedicalNbt.read(m, r.snapshot.getCompound("Medical"), s);
                Healing.fastForward(m, seconds, s);
                CompoundTag snap = r.snapshot.copy();
                snap.put("Medical", MedicalNbt.write(m));
                r.snapshot = snap;
                reg.setDirty();
            }
            n++;
        }
        return n;
    }
}
