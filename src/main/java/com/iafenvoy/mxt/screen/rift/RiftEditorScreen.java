package com.iafenvoy.mxt.screen.rift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.data.rift.*;
import com.iafenvoy.mxt.network.payload.RiftEditC2SPayload;
import com.iafenvoy.mxt.network.payload.RiftEditorS2CPayload;
import com.iafenvoy.mxt.render.rift.RiftClientState;
import com.iafenvoy.mxt.runtime.rift.*;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.iafenvoy.mxt.screen.aui.AuiScreen;
import com.mojang.blaze3d.platform.Window;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.List;

public final class RiftEditorScreen extends AuiScreen {
    private final UUID previewId = UUID.randomUUID();
    private final UUID token;
    private final Identifier dimension;
    private final RiftEditSession session;
    private final RiftEditorInputs fields = new RiftEditorInputs();
    private final Map<String, Element> sections = new HashMap<>();
    private RiftTemplate accepted;
    private long revision, sentVersion = -1;
    private boolean pending, worldPreview, previewCamera, closed;
    private String tab = "shape", tool = "select", export = "";
    private Canvas canvas;
    private Element status, panel;
    private double viewScale = 48, viewU, viewV = 1.2;
    private int dragged = -1, selectedEdge = -1, ticks;
    private RiftTemplate beforeDrag;
    private RiftVertex dragFrom;
    private long paintedVersion = -1;
    private UUID portalId;
    private boolean panning, boxSelecting, turningCamera;
    private RiftVertex boxTo;
    private final List<Vec3> planePoints = new ArrayList<>();
    private int removeConfirmUntil;
    private long previewVersion = -1;
    private List<RiftClientState.Entry> previewCache = List.of();

    private RiftEditorScreen(RiftEditorS2CPayload payload, RiftTemplate initial) {
        super(AuiPages.riftPage());
        this.token = payload.token();
        this.dimension = payload.dimension();
        this.session = new RiftEditSession(initial);
        this.accepted = initial;
        this.revision = payload.record().map(RiftRecord::revision).orElse(0L);
        this.portalId = payload.record().map(RiftRecord::id).orElse(null);
    }

    public static void receive(RiftEditorS2CPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                || mc.level == null || !mc.level.dimension().identifier().equals(payload.dimension())) return;
        if (payload.action().equals("open")) {
            RiftTemplate initial = payload.record().map(RiftRecord::data).orElseGet(() -> RiftTemplate.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString(payload.body())).result().orElseThrow());
            mc.setScreen(new RiftEditorScreen(payload, initial));
            return;
        }
        if (!(mc.screen instanceof RiftEditorScreen screen) || !screen.token.equals(payload.token())) return;
        switch (payload.action()) {
            case "close" -> screen.onClose();
            case "error" -> {
                screen.pending = false;
                screen.message(Component.translatable("rift.mxt.failure.edit", payload.body()));
            }
            case "accepted" -> {
                screen.pending = false;
                RiftRecord record = payload.record().orElseThrow();
                screen.portalId = record.id();
                screen.revision = record.revision();
                screen.accepted = record.data();
                if (screen.sentVersion < 0 || screen.sentVersion == screen.session.version()) {
                    screen.session.apply(record.data());
                    screen.writeFields();
                }
                screen.message(Component.translatable("screen.mxt.rift.saved"));
            }
            case "export" -> {
                screen.pending = false;
                screen.export = payload.body();
                mc.keyboardHandler.setClipboard(screen.export);
                screen.message(Component.translatable("screen.mxt.rift.copied"));
            }
            case "blueprint" -> {
                screen.pending = false;
                screen.message(Component.translatable("screen.mxt.rift.blueprint_saved"));
            }
        }
    }

    public static List<RiftClientState.Entry> preview() {
        if (!(Minecraft.getInstance().screen instanceof RiftEditorScreen screen) || !screen.worldPreview)
            return List.of();
        if (screen.previewVersion == screen.session.version()) return screen.previewCache;
        screen.previewVersion = screen.session.version();
        RiftTemplate data = screen.session.draft();
        Optional<List<RiftGeometry.Triangle>> triangles = RiftGeometry.triangulate(data.shape()).result();
        Optional<RiftPlacement> placement = data.placement().normalized().result();
        if (triangles.isEmpty() || placement.isEmpty()) {
            screen.previewCache = List.of();
            return screen.previewCache;
        }
        RiftVisual visual = new RiftVisual(placement.get(), data.shape(), data.appearance(), data.appearance().color() == -1 ? 0xFF55FFFF : data.appearance().color(), true);
        screen.previewCache = List.of(new RiftClientState.Entry(screen.previewId, screen.session.version(), visual, RiftGeometry.mesh(data.shape(), placement.get(), triangles.get())));
        return screen.previewCache;
    }

    public static UUID previewedId() {
        return Minecraft.getInstance().screen instanceof RiftEditorScreen screen && screen.worldPreview ? screen.portalId : null;
    }

    @Override
    public void bindPage() {
        this.canvas = this.getOrThrow("canvas", Canvas.class);
        this.canvas.setWidth(320);
        this.canvas.setHeight(256);
        this.panel = this.getOrThrow("panel");
        this.status = this.getOrThrow("status");
        for (String name : List.of("shape", "placement", "target", "appearance", "blueprint")) {
            this.sections.put(name, this.getOrThrow("section_" + name));
            this.button("tab_" + name, () -> {
                if (this.readProperties()) {
                    this.tab = name;
                    this.showTab();
                }
            });
        }
        for (String name : FIELD_NAMES) {
            this.fields.bind(name, this.getOrThrow(name));
            if (!name.matches("(origin|normal|tangent|target_pos)_[xyz]"))
                this.text(this.getOrThrow("label_" + name), Component.translatable("screen.mxt.rift.field_" + name));
        }
        for (String name : List.of("origin", "normal", "tangent", "target_pos"))
            this.text(this.getOrThrow("label_" + name), Component.translatable("screen.mxt.rift.field_" + name));
        for (String name : List.of("position", "secret_realm", "return", "rift", "portal"))
            this.text(this.getOrThrow("option_" + name), Component.translatable("screen.mxt.rift.option_" + name));
        this.getOrThrow("target_type").addEventListener("change", _ -> this.showTargetFields());
        for (String name : List.of("select", "draw", "edge", "move", "plane", "fit"))
            this.button("tool_" + name, () -> {
                this.tool = name;
                this.planePoints.clear();
                this.showTab();
            });
        for (String name : List.of("rectangle", "ellipse", "polygon", "arch", "fissure", "rounded"))
            this.button("generate_" + name, () -> this.operation(() ->
                    this.changeShape(RiftEdits.generate(name, this.number("width"), this.number("height"), (int) this.number("count"), (long) this.number("seed")))));
        this.button("undo", () -> {
            this.session.undo();
            this.writeFields();
        });
        this.button("redo", () -> {
            this.session.redo();
            this.writeFields();
        });
        this.button("restore", () -> {
            this.session.apply(this.accepted);
            this.writeFields();
        });
        this.button("apply", () -> this.sendDraft("submit"));
        this.button("renew", () -> this.sendDraft("renew"));
        this.button("reload", () -> {
            this.sentVersion = -1;
            this.send("reload", "");
        });
        this.button("copy", () -> this.sendDraft("copy"));
        this.button("remove", () -> {
            if (this.removeConfirmUntil > this.ticks) this.send("remove", "");
            else {
                this.removeConfirmUntil = this.ticks + 60;
                this.message(Component.translatable("screen.mxt.rift.confirm_remove"));
            }
        });
        this.button("close", this::onClose);
        this.button("select_all", () -> {
            for (int i = 0; i < this.session.draft().shape().vertices().size(); i++) this.session.selected.add(i);
            this.redraw();
        });
        this.button("delete_vertex", () -> this.operation(() -> {
            List<RiftVertex> points = new ArrayList<>();
            List<RiftVertex> old = this.session.draft().shape().vertices();
            for (int i = 0; i < old.size(); i++) if (!this.session.selected.contains(i)) points.add(old.get(i));
            this.changeShape(new RiftShape(points));
            this.session.selected.clear();
        }));
        this.button("insert", () -> this.operation(() -> {
            ArrayList<RiftVertex> points = new ArrayList<>(this.session.draft().shape().vertices());
            int edge = this.edge();
            points.add(edge + 1, RiftEdits.lerp(points.get(edge), points.get((edge + 1) % points.size()), 0.5));
            this.changeShape(new RiftShape(points));
        }));
        this.button("transform", () -> this.operation(() -> this.changeShape(RiftEdits.transform(this.session.draft().shape(), this.session.selected,
                new RiftVertex(this.number("pivot_u"), this.number("pivot_v")), this.number("du"), this.number("dv"), this.number("angle"), this.number("scale_u"), this.number("scale_v"), this.number("shear")))));
        this.button("mirror", () -> this.operation(() -> this.changeShape(RiftEdits.transform(this.session.draft().shape(), this.session.selected,
                new RiftVertex(this.number("pivot_u"), this.number("pivot_v")), 0, 0, 0, -1, 1, 0))));
        this.button("chamfer", () -> this.operation(() -> this.changeShape(RiftEdits.chamfer(this.session.draft().shape(), this.session.selected, Math.clamp(this.number("amount"), 0.001, 0.49)))));
        this.button("offset", () -> this.operation(() -> this.changeShape(RiftEdits.offset(this.session.draft().shape(), this.number("amount")))));
        this.button("curve", () -> this.operation(() -> this.changeShape(RiftEdits.curve(this.session.draft().shape(), this.edge(),
                new RiftVertex(this.number("c1u"), this.number("c1v")), new RiftVertex(this.number("c2u"), this.number("c2v")), this.number("tolerance")))));
        for (String mode : List.of("horizontal", "vertical"))
            this.button("align_" + mode, () -> this.operation(() -> this.changeShape(RiftEdits.align(this.session.draft().shape(), this.session.selected, mode))));
        this.button("vertex_apply", () -> this.operation(() -> {
            ArrayList<RiftVertex> points = new ArrayList<>(this.session.draft().shape().vertices());
            for (int index : this.session.selected)
                points.set(index, new RiftVertex(this.number("vertex_u"), this.number("vertex_v")));
            this.changeShape(new RiftShape(points));
        }));
        this.button("vertex_select", () -> this.operation(() -> {
            int index = (int) this.number("vertex_index");
            if (index < 0 || index >= this.session.draft().shape().vertices().size())
                throw new IllegalArgumentException();
            this.session.selected.clear();
            this.session.selected.add(index);
        }));
        this.button("record_target", () -> {
            if (this.minecraft.player == null) return;
            this.value("dimension", this.minecraft.level.dimension().identifier().toString());
            this.fields.vector("target_pos", this.minecraft.player.position());
            this.fields.number("yaw", this.minecraft.player.getYRot());
            this.fields.number("pitch", this.minecraft.player.getXRot());
            this.value("target_type", "position");
            this.showTargetFields();
        });
        this.button("preview", () -> {
            if (!this.readProperties()) return;
            this.worldPreview = !this.worldPreview;
            if (!this.worldPreview) this.previewCamera = false;
            this.turningCamera = false;
            this.showTab();
        });
        this.button("camera", () -> {
            if (!this.readProperties()) return;
            this.worldPreview = true;
            this.previewCamera = !this.previewCamera;
            this.turningCamera = false;
            this.showTab();
        });
        this.button("read_json", () -> this.operation(() -> this.loadJson(this.minecraft.keyboardHandler.getClipboard())));
        this.button("copy_json", () -> this.operation(() -> this.minecraft.keyboardHandler.setClipboard(RiftExport.template(this.session.draft()))));
        this.button("blueprint", () -> this.sendDraft("blueprint"));
        for (String format : List.of("preset", "layout", "relative", "local", "world"))
            this.button("export_" + format, () -> this.sendDraft("export_" + format));
        this.button("save_file", this::saveFile);
        this.button("rebase", () -> this.operation(() -> {
            RiftTemplate data = this.session.draft();
            double u = this.number("pivot_u"), v = this.number("pivot_v");
            RiftPlacement p = data.placement();
            RiftAppearance a = data.appearance();
            double radians = Math.toRadians(a.textureRotation());
            double tx = (u * Math.cos(radians) - v * Math.sin(radians)) * a.textureScale();
            double ty = (u * Math.sin(radians) + v * Math.cos(radians)) * a.textureScale();
            this.session.apply(new RiftTemplate(data.key(), new RiftPlacement(p.world(new RiftVertex(u, v)), p.normal(), p.tangent()), false,
                    RiftEdits.transform(data.shape(), Set.of(), new RiftVertex(0, 0), -u, -v, 0, 1, 1, 0), data.destination(),
                    new RiftAppearance(a.style(), a.color(), a.edgeColor(), a.speed(), a.edgeWidth(), a.glow(), a.particles(), a.textureScale(), a.textureRotation(), a.textureU() + tx, a.textureV() + ty), data.durationTicks(), data.enabled()));
        }));
        this.writeFields();
        this.showTab();
        this.redraw();
    }

    private static final List<String> FIELD_NAMES = List.of("width", "height", "count", "seed", "snap", "pivot_u", "pivot_v", "du", "dv", "angle", "scale_u", "scale_v", "shear", "amount", "vertex_u", "vertex_v", "c1u", "c1v", "c2u", "c2v", "tolerance",
            "origin_x", "origin_y", "origin_z", "normal_x", "normal_y", "normal_z", "tangent_x", "tangent_y", "tangent_z", "target_type", "dimension", "target_pos_x", "target_pos_y", "target_pos_z", "realm", "yaw", "pitch", "nearby", "style", "color", "edge_color", "speed", "edge_width", "glow", "particles", "texture_scale", "texture_rotation", "texture_u", "texture_v", "duration", "enabled", "key", "filename", "vertex_index");

    private void button(String id, Runnable action) {
        Element element = this.getOrThrow(id);
        this.text(element, Component.translatable("screen.mxt.rift." + id));
        this.click(element, action);
    }

    private void message(Component message) {
        this.text(this.status, message);
    }

    private void value(String field, String value) {
        this.fields.value(field, value);
    }

    private String value(String field) {
        return this.fields.value(field);
    }

    private double number(String field) {
        return this.fields.number(field);
    }

    private OptionalDouble spacing() {
        try {
            double value = this.number("snap");
            if (value < 0 || value > RiftShape.MAX_SIZE) throw new IllegalArgumentException();
            return OptionalDouble.of(value);
        } catch (RuntimeException invalid) {
            this.message(Component.translatable("screen.mxt.rift.invalid_input"));
            return OptionalDouble.empty();
        }
    }

    private int color(String field) {
        return this.value(field).equals("auto") ? -1 : (int) Long.parseUnsignedLong(this.value(field).replace("#", ""), 16) | 0xFF000000;
    }

    private int edge() {
        int size = this.session.draft().shape().vertices().size();
        if (size == 0) throw new IllegalArgumentException();
        return Math.floorMod(this.selectedEdge < 0 ? this.session.selected.stream().findFirst().orElse(0) : this.selectedEdge, size);
    }

    private void operation(Runnable action) {
        try {
            if (!this.readProperties()) return;
            action.run();
            this.writeFields();
            this.redraw();
        } catch (RuntimeException invalid) {
            this.message(Component.translatable("screen.mxt.rift.invalid_input"));
        }
    }

    private void changeShape(RiftShape shape) {
        this.session.apply(RiftEdits.shape(this.session.draft(), shape));
    }

    private void writeFields() {
        if (this.fields.isEmpty()) return;
        RiftTemplate d = this.session.draft();
        RiftPlacement p = d.placement();
        RiftAppearance a = d.appearance();
        this.fields.vector("origin", p.origin());
        this.fields.vector("normal", p.normal());
        this.fields.vector("tangent", p.tangent());
        this.value("key", d.key());
        this.value("duration", Long.toString(d.durationTicks()));
        this.fields.checked("enabled", d.enabled());
        this.value("target_type", d.destination().type());
        if (d.destination() instanceof RiftDestination.Position(
                Optional<Identifier> dimension1, Vec3 pos, Optional<Float> yaw, Optional<Float> pitch, boolean nearby
        )) {
            this.value("dimension", dimension1.map(Object::toString).orElse(this.dimension.toString()));
            this.fields.vector("target_pos", pos);
            if (yaw.isPresent()) this.fields.number("yaw", yaw.get());
            else this.value("yaw", "");
            if (pitch.isPresent()) this.fields.number("pitch", pitch.get());
            else this.value("pitch", "");
            this.fields.checked("nearby", nearby);
        } else if (d.destination() instanceof RiftDestination.Realm(Identifier realm1)) this.value("realm", realm1.toString());
        this.value("style", a.style());
        this.value("color", a.color() == -1 ? "auto" : RiftColors.format(a.color()));
        this.value("edge_color", a.edgeColor() == -1 ? "auto" : RiftColors.format(a.edgeColor()));
        this.fields.number("speed", a.speed());
        this.fields.number("edge_width", a.edgeWidth());
        this.fields.number("glow", a.glow());
        this.fields.number("particles", a.particles());
        this.fields.number("texture_scale", a.textureScale());
        this.fields.number("texture_rotation", a.textureRotation());
        this.fields.number("texture_u", a.textureU());
        this.fields.number("texture_v", a.textureV());
        this.showTargetFields();
        if (!this.session.selected.isEmpty()) {
            int i = this.session.selected.iterator().next();
            if (i < d.shape().vertices().size()) {
                RiftVertex point = d.shape().vertices().get(i);
                this.fields.number("vertex_u", point.u());
                this.fields.number("vertex_v", point.v());
            }
        }
    }

    private boolean readProperties() {
        try {
            RiftDestination destination = switch (this.value("target_type")) {
                case "position" ->
                        new RiftDestination.Position(Optional.of(Identifier.parse(this.value("dimension"))), this.fields.vector("target_pos"),
                                this.value("yaw").isEmpty() ? Optional.empty() : Optional.of((float) this.number("yaw")), this.value("pitch").isEmpty() ? Optional.empty() : Optional.of((float) this.number("pitch")), this.fields.checked("nearby"));
                case "secret_realm" -> new RiftDestination.Realm(Identifier.parse(this.value("realm")));
                case "return" -> new RiftDestination.Return();
                default -> throw new IllegalArgumentException();
            };
            RiftPlacement placement = new RiftPlacement(this.fields.vector("origin"), this.fields.vector("normal"), this.fields.vector("tangent"));
            if (!placement.equals(this.session.draft().placement())) placement = placement.normalized().result().orElseThrow();
            this.session.apply(new RiftTemplate(this.value("key"), placement, false, this.session.draft().shape(), destination,
                    new RiftAppearance(this.value("style"), this.color("color"), this.color("edge_color"), this.number("speed"), this.number("edge_width"), this.number("glow"), this.number("particles"), this.number("texture_scale"), this.number("texture_rotation"), this.number("texture_u"), this.number("texture_v")),
                    Long.parseLong(this.value("duration")), this.fields.checked("enabled")));
            return true;
        } catch (RuntimeException invalid) {
            this.message(Component.translatable("screen.mxt.rift.invalid_input"));
            return false;
        }
    }

    private void sendDraft(String action) {
        if (!this.readProperties()) return;
        DataResult<JsonElement> encoded = RiftTemplate.CODEC.encodeStart(JsonOps.INSTANCE, this.session.draft());
        if (encoded.error().isPresent()) {
            this.message(Component.translatable("screen.mxt.rift.invalid_input"));
            return;
        }
        this.send(action, encoded.result().orElseThrow().toString());
    }

    private void send(String action, String body) {
        if (this.pending && !action.equals("close") && !action.equals("heartbeat")) return;
        if (!action.equals("close") && !action.equals("heartbeat")) {
            this.pending = true;
            this.sentVersion = action.equals("reload") ? -1 : this.session.version();
        }
        ClientPacketDistributor.sendToServer(new RiftEditC2SPayload(this.token, action, this.revision, body));
    }

    private void loadJson(String json) {
        JsonElement object = JsonParser.parseString(json);
        if (object.isJsonObject() && object.getAsJsonObject().has("portals")) {
            JsonArray array = object.getAsJsonObject().getAsJsonArray("portals");
            if (array.size() != 1) throw new IllegalArgumentException();
            object = array.get(0);
        }
        Optional<RiftTemplate> template = RiftTemplate.CODEC.parse(JsonOps.INSTANCE, object).result();
        if (template.isPresent()) {
            this.session.apply(template.get());
            return;
        }
        Optional<RiftPreset> preset = RiftPreset.DIRECT_CODEC.parse(JsonOps.INSTANCE, object).result();
        if (preset.isEmpty()) throw new IllegalArgumentException();
        RiftTemplate old = this.session.draft();
        this.session.apply(new RiftTemplate(old.key(), old.placement(), false, preset.get().shape(), old.destination(), preset.get().appearance(), old.durationTicks(), old.enabled()));
    }

    private void saveFile() {
        if (this.export.isEmpty()) {
            this.message(Component.translatable("screen.mxt.rift.export_first"));
            return;
        }
        String name = this.value("filename");
        if (!name.matches("[a-zA-Z0-9_-]{1,64}")) {
            this.message(Component.translatable("screen.mxt.rift.invalid_input"));
            return;
        }
        Path folder = this.minecraft.gameDirectory.toPath().resolve("exports/mxt/rifts");
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve(name + ".json"), this.export, StandardOpenOption.CREATE_NEW);
            this.message(Component.translatable("screen.mxt.rift.file_saved", name));
        } catch (IOException failure) {
            this.message(Component.translatable("screen.mxt.rift.file_failed"));
        }
    }

    private void showTargetFields() {
        String type = this.value("target_type");
        for (String name : List.of("position", "secret_realm"))
            AuiElements.style(this.getOrThrow("target_" + name), "display", name.equals(type) ? "flex" : "none");
    }

    private void showTab() {
        this.showTargetFields();
        this.sections.forEach((key, section) -> AuiElements.style(section, "display", key.equals(this.tab) ? "flex" : "none"));
        for (String name : this.sections.keySet())
            AuiElements.setClass(this.getOrThrow("tab_" + name), "selected", name.equals(this.tab));
        for (String name : List.of("select", "draw", "edge", "move", "plane", "fit"))
            AuiElements.setClass(this.getOrThrow("tool_" + name), "selected", name.equals(this.tool));
        AuiElements.style(this.getOrThrow("body"), "display", this.worldPreview ? "none" : "flex");
        AuiElements.setClass(this.panel, "world-preview", this.worldPreview);
        AuiElements.setClass(this.getOrThrow("camera"), "selected", this.previewCamera);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.minecraft.player == null || this.minecraft.level == null || !this.minecraft.level.dimension().identifier().equals(this.dimension)
                || !this.minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            this.onClose();
            return;
        }
        if (++this.ticks % 100 == 0) this.send("heartbeat", "");
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        if (this.auiPageWritable() && this.paintedVersion != this.session.version()) this.redraw();
        super.extractRenderState(graphics, x, y, partialTick);
    }

    private RiftVertex pointer(double x, double y) {
        if (this.worldPreview || this.canvas == null || this.getLinkedDocument() == null) return null;
        Position pointer = this.getLinkedDocument().screenToDocumentPosition(new Position(x, y));
        // The DOM query scopes cold layout measurements; raw Position.of can repeat nested flex work without caching.
        Element.DOMRect bounds = this.canvas.getBoundingClientRect();
        if (bounds.width <= 0 || bounds.height <= 0 || pointer.x < bounds.left || pointer.y < bounds.top || pointer.x >= bounds.right || pointer.y >= bounds.bottom)
            return null;
        return new RiftVertex(((pointer.x - bounds.left) / bounds.width * 320 - 160) / this.viewScale + this.viewU, (128 - (pointer.y - bounds.top) / bounds.height * 256) / this.viewScale + this.viewV);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!this.auiBound()) return super.mouseClicked(event, doubleClick);
        if (this.worldPreview) {
            if (!this.previewCamera || this.overPanel(event.x(), event.y()))
                return super.mouseClicked(event, doubleClick);
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.turningCamera = true;
                return true;
            }
            return this.worldClick(event);
        }
        RiftVertex point = this.pointer(event.x(), event.y());
        if (point == null || this.worldPreview) return super.mouseClicked(event, doubleClick);
        if (!this.readProperties()) return true;
        RiftShape shape = this.session.draft().shape();
        this.dragFrom = point;
        if (event.button() == 1 || event.button() == 2) {
            this.panning = true;
            return true;
        }
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        if (this.tool.equals("draw")) {
            if (shape.vertices().size() >= RiftShape.MAX_VERTICES) return true;
            OptionalDouble spacing = this.spacing();
            if (spacing.isEmpty()) return true;
            ArrayList<RiftVertex> points = new ArrayList<>(shape.vertices());
            points.add(new RiftVertex(RiftEdits.snap(point.u(), spacing.getAsDouble()), RiftEdits.snap(point.v(), spacing.getAsDouble())));
            this.changeShape(new RiftShape(points));
        } else {
            int best = -1;
            double distance = 8 / this.viewScale;
            for (int i = 0; i < shape.vertices().size(); i++) {
                RiftVertex p = shape.vertices().get(i);
                double d = Math.hypot(p.u() - point.u(), p.v() - point.v());
                if (d < distance) {
                    best = i;
                    distance = d;
                }
            }
            if (this.tool.equals("edge")) {
                this.session.selected.clear();
                double edgeDistance = Double.POSITIVE_INFINITY;
                for (int i = 0; i < shape.vertices().size(); i++) {
                    RiftVertex a = shape.vertices().get(i);
                    RiftVertex b = shape.vertices().get((i + 1) % shape.vertices().size());
                    double dx = b.u() - a.u(), dy = b.v() - a.v();
                    double t = Math.clamp(((point.u() - a.u()) * dx + (point.v() - a.v()) * dy) / Math.max(1e-12, dx * dx + dy * dy), 0, 1);
                    double d = Math.hypot(point.u() - a.u() - t * dx, point.v() - a.v() - t * dy);
                    if (d < edgeDistance) {
                        edgeDistance = d;
                        this.selectedEdge = i;
                    }
                }
                if (this.selectedEdge >= 0) {
                    this.session.selected.add(this.selectedEdge);
                    this.session.selected.add((this.selectedEdge + 1) % shape.vertices().size());
                    this.dragged = this.selectedEdge;
                }
            } else if (best >= 0) {
                if ((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
                    if (!this.session.selected.add(best)) this.session.selected.remove(best);
                } else if (!this.session.selected.contains(best)) {
                    this.session.selected.clear();
                    this.session.selected.add(best);
                }
                if (this.session.selected.contains(best)) this.dragged = best;
            } else {
                if ((event.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0) this.session.selected.clear();
                if (this.tool.equals("move")) this.dragged = 0;
                else {
                    this.boxSelecting = true;
                    this.boxTo = point;
                }
            }
            this.beforeDrag = this.session.draft();
        }
        this.writeFields();
        this.redraw();
        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        // MouseHandler.grabMouse() closes the screen; turn inside the editor without capturing it.
        if (this.turningCamera && this.worldPreview && this.previewCamera && event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (this.minecraft.player != null) {
                Window window = this.minecraft.getWindow();
                double sensitivity = this.minecraft.options.sensitivity().get() * 0.6 + 0.2;
                double scale = sensitivity * sensitivity * sensitivity * 8;
                double horizontal = dx * window.getScreenWidth() / window.getGuiScaledWidth() * scale;
                double vertical = dy * window.getScreenHeight() / window.getGuiScaledHeight() * scale;
                this.minecraft.player.turn(this.minecraft.options.invertMouseX().get() ? -horizontal : horizontal,
                        this.minecraft.options.invertMouseY().get() ? -vertical : vertical);
            }
            return true;
        }
        RiftVertex point = this.pointer(event.x(), event.y());
        if (this.panning && point != null) {
            this.viewU -= point.u() - this.dragFrom.u();
            this.viewV -= point.v() - this.dragFrom.v();
            this.redraw();
            return true;
        }
        if (this.boxSelecting && point != null) {
            this.boxTo = point;
            this.redraw();
            return true;
        }
        if (point == null || this.dragged < 0 || this.beforeDrag == null) return super.mouseDragged(event, dx, dy);
        OptionalDouble snap = this.spacing();
        if (snap.isEmpty()) return true;
        double u = point.u() - this.dragFrom.u(), v = point.v() - this.dragFrom.v(), spacing = snap.getAsDouble();
        if (this.session.selected.size() == 1) {
            RiftVertex initial = this.beforeDrag.shape().vertices().get(this.dragged);
            u = RiftEdits.snap(initial.u() + u, spacing) - initial.u();
            v = RiftEdits.snap(initial.v() + v, spacing) - initial.v();
        } else {
            u = RiftEdits.snap(u, spacing);
            v = RiftEdits.snap(v, spacing);
        }
        this.session.transientDraft(RiftEdits.shape(this.beforeDrag, RiftEdits.transform(this.beforeDrag.shape(), this.session.selected,
                new RiftVertex(0, 0), u, v, 0, 1, 1, 0)));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && this.turningCamera) {
            this.turningCamera = false;
            return true;
        }
        boolean interacted = this.boxSelecting || this.panning || this.beforeDrag != null;
        if (this.boxSelecting && this.boxTo != null) {
            List<RiftVertex> points = this.session.draft().shape().vertices();
            for (int i = 0; i < points.size(); i++) {
                RiftVertex p = points.get(i);
                if (p.u() >= Math.min(this.dragFrom.u(), this.boxTo.u()) && p.u() <= Math.max(this.dragFrom.u(), this.boxTo.u())
                        && p.v() >= Math.min(this.dragFrom.v(), this.boxTo.v()) && p.v() <= Math.max(this.dragFrom.v(), this.boxTo.v()))
                    this.session.selected.add(i);
            }
        }
        this.panning = this.boxSelecting = false;
        this.boxTo = null;
        if (this.beforeDrag != null) this.session.finishDrag(this.beforeDrag);
        this.beforeDrag = null;
        this.dragged = -1;
        if (interacted) {
            this.writeFields();
            this.redraw();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && this.previewCamera) {
            this.previewCamera = false;
            this.turningCamera = false;
            this.showTab();
            return true;
        }
        return super.keyPressed(event);
    }

    private boolean overPanel(double x, double y) {
        if (this.panel == null || this.getLinkedDocument() == null) return false;
        Position pointer = this.getLinkedDocument().screenToDocumentPosition(new Position(x, y));
        Element.DOMRect bounds = this.panel.getBoundingClientRect();
        return pointer.x >= bounds.left && pointer.y >= bounds.top && pointer.x < bounds.right && pointer.y < bounds.bottom;
    }

    private boolean worldClick(MouseButtonEvent event) {
        if (event.button() != 0 || this.minecraft.player == null || !this.readProperties()) return true;
        Vec3 from = this.minecraft.player.getEyePosition(), ray = this.minecraft.player.getLookAngle();
        RiftTemplate data = this.session.draft();
        BlockHitResult hit = this.minecraft.level.clip(new ClipContext(from, from.add(ray.scale(64)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this.minecraft.player));
        if (this.tool.equals("plane") || this.tool.equals("fit")) {
            if (hit.getType() != HitResult.Type.BLOCK) return true;
            if (this.tool.equals("plane")) {
                this.planePoints.add(hit.getLocation());
                if (this.planePoints.size() == 3) {
                    Optional<RiftPlacement> plane = RiftEdits.plane(this.planePoints.get(0), this.planePoints.get(1), this.planePoints.get(2)).normalized().result();
                    this.planePoints.clear();
                    if (plane.isPresent()) this.session.apply(RiftEdits.placement(data, plane.get()));
                    else this.message(Component.translatable("screen.mxt.rift.invalid_input"));
                }
            } else {
                Direction face = hit.getDirection();
                Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                Vec3 tangent = face.getAxis().isVertical() ? new Vec3(1, 0, 0) : new Vec3(normal.z, 0, -normal.x);
                this.session.apply(RiftEdits.placement(data, new RiftPlacement(hit.getLocation().add(normal.scale(0.002)), normal, tangent)));
            }
            this.writeFields();
            return true;
        }
        RiftPlacement plane = data.placement().normalized().result().orElse(null);
        if (plane == null) return true;
        double denominator = ray.dot(plane.normal());
        if (Math.abs(denominator) < 1e-8) return true;
        double distance = plane.origin().subtract(from).dot(plane.normal()) / denominator;
        if (distance < 0 || distance > 64) return true;
        RiftVertex point = plane.local(from.add(ray.scale(distance)));
        OptionalDouble snap = this.spacing();
        if (snap.isEmpty()) return true;
        double spacing = snap.getAsDouble();
        point = new RiftVertex(RiftEdits.snap(point.u(), spacing), RiftEdits.snap(point.v(), spacing));
        ArrayList<RiftVertex> points = new ArrayList<>(data.shape().vertices());
        if (this.tool.equals("draw") && points.size() < RiftShape.MAX_VERTICES) {
            points.add(point);
            this.changeShape(new RiftShape(points));
        } else if (this.tool.equals("move") && !this.session.selected.isEmpty()) {
            int index = this.session.selected.iterator().next();
            RiftVertex old = points.get(index);
            this.changeShape(RiftEdits.transform(data.shape(), this.session.selected, old, point.u() - old.u(), point.v() - old.v(), 0, 1, 1, 0));
        } else {
            int best = -1;
            double closest = 0.3;
            for (int i = 0; i < points.size(); i++) {
                double d = Math.hypot(points.get(i).u() - point.u(), points.get(i).v() - point.v());
                if (d < closest) {
                    closest = d;
                    best = i;
                }
            }
            this.session.selected.clear();
            if (best >= 0) this.session.selected.add(best);
        }
        this.writeFields();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (this.pointer(x, y) == null) return super.mouseScrolled(x, y, sx, sy);
        this.viewScale = Math.clamp(this.viewScale * Math.pow(1.15, sy), 4, 512);
        this.redraw();
        return true;
    }

    private void redraw() {
        if (this.canvas == null) return;
        this.paintedVersion = this.session.version();
        RiftShape shape = this.session.draft().shape();
        boolean valid = RiftGeometry.triangulate(shape).isSuccess();
        this.canvas.renderOperation(g -> {
            g.setComposite(AlphaComposite.Src);
            g.setColor(new Color(0xFF181818, true));
            g.fillRect(0, 0, 320, 256);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(0xFF303030, true));
            for (int i = -40; i <= 40; i++) {
                int x = (int) (160 + (i - this.viewU) * this.viewScale), y = (int) (128 - (i - this.viewV) * this.viewScale);
                g.drawLine(x, 0, x, 256);
                g.drawLine(0, y, 320, y);
            }
            Path2D path = new Path2D.Double();
            for (int i = 0; i < shape.vertices().size(); i++) {
                RiftVertex p = shape.vertices().get(i);
                double x = 160 + (p.u() - this.viewU) * this.viewScale, y = 128 - (p.v() - this.viewV) * this.viewScale;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            if (!shape.vertices().isEmpty()) path.closePath();
            g.setColor(new Color(valid ? 0x6055FFFF : 0x60FF5555, true));
            g.fill(path);
            g.setColor(new Color(valid ? 0xFF55FFFF : 0xFFFF5555, true));
            g.setStroke(new BasicStroke(1));
            g.draw(path);
            for (int i = 0; i < shape.vertices().size(); i++) {
                RiftVertex p = shape.vertices().get(i);
                int x = (int) (160 + (p.u() - this.viewU) * this.viewScale), y = (int) (128 - (p.v() - this.viewV) * this.viewScale);
                g.setColor(this.session.selected.contains(i) ? Color.YELLOW : Color.WHITE);
                g.fillRect(x - 2, y - 2, 5, 5);
            }
            if (this.boxSelecting && this.boxTo != null) {
                double x = 160 + (Math.min(this.dragFrom.u(), this.boxTo.u()) - this.viewU) * this.viewScale;
                double y = 128 - (Math.max(this.dragFrom.v(), this.boxTo.v()) - this.viewV) * this.viewScale;
                g.setColor(Color.YELLOW);
                g.draw(new Rectangle2D.Double(x, y,
                        Math.abs(this.dragFrom.u() - this.boxTo.u()) * this.viewScale, Math.abs(this.dragFrom.v() - this.boxTo.v()) * this.viewScale));
            }
        });
        this.message(Component.translatable("screen.mxt.rift.geometry", shape.vertices().size(), String.format(Locale.ROOT, "%.3f", Math.abs(RiftGeometry.area(shape.vertices()))),
                Component.translatable(valid ? "screen.mxt.rift.valid" : "screen.mxt.rift.invalid")));
    }

    @Override
    public void onBindingsCleared() {
        this.canvas = null;
        this.status = this.panel = null;
        this.fields.clear();
        this.sections.clear();
        this.turningCamera = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor g, int x, int y, float tick) {
        if (!this.worldPreview) super.extractBackground(g, x, y, tick);
    }

    @Override
    public void removed() {
        if (!this.closed) {
            this.closed = true;
            this.send("close", "");
        }
        this.worldPreview = this.previewCamera = this.turningCamera = false;
        this.previewCache = List.of();
        super.removed();
    }
}
