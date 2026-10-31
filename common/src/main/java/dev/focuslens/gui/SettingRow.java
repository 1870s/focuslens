package dev.focuslens.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One line of the settings list. Rows are drawn and hit-tested by {@link FocusLensScreen}; they are
 * deliberately not vanilla widgets so the look is consistent and the code stays version independent.
 */
public abstract class SettingRow {
    public static final int HEIGHT = 22;
    public static final int CONTROL_WIDTH = 132;

    public final String label;
    public final String description;
    /** When non-null the row is shown greyed out and this text explains why. */
    public Supplier<String> disabledReason = () -> null;

    protected int x;
    protected int y;
    protected int width;

    protected SettingRow(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public SettingRow disabledWhen(Supplier<String> reason) {
        this.disabledReason = reason;
        return this;
    }

    public boolean isEnabled() {
        return disabledReason.get() == null;
    }

    public int height() {
        return HEIGHT;
    }

    void setBounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height();
    }

    protected int controlX() {
        return x + width - CONTROL_WIDTH - 6;
    }

    protected boolean overControl(double mouseX, double mouseY) {
        return mouseX >= controlX() && mouseX < controlX() + CONTROL_WIDTH && mouseY >= y + 3 && mouseY < y + HEIGHT - 3;
    }

    void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, boolean hovered) {
        if (hovered) {
            g.fill(x, y, x + width, y + height(), Theme.ROW_HOVER);
        }
        String reason = disabledReason.get();
        g.text(font, label, x + 8, y + (HEIGHT - 8) / 2, reason == null ? Theme.TEXT : Theme.TEXT_DISABLED, false);
        extractControl(g, font, mouseX, mouseY, reason == null);
    }

    protected abstract void extractControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, boolean enabled);

    /** @return true when the click changed something */
    boolean click(double mouseX, double mouseY, int button) {
        return false;
    }

    void drag(double mouseX) {
    }

    boolean isDraggable() {
        return false;
    }

    protected static void drawButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String text, boolean hovered, boolean enabled, boolean accent) {
        int fill = !enabled ? Theme.CONTROL : accent ? (hovered ? Theme.ACCENT_HOVER : Theme.ACCENT) : (hovered ? Theme.CONTROL_HOVER : Theme.CONTROL);
        g.fill(x, y, x + w, y + h, fill);
        g.outline(x, y, w, h, accent && enabled ? Theme.ACCENT_LIGHT & 0x60FFFFFF : Theme.BORDER);
        int color = !enabled ? Theme.TEXT_DISABLED : Theme.TEXT;
        g.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, color, false);
    }

    // ------------------------------------------------------------------ row types

    public static final class Heading extends SettingRow {
        public Heading(String title) {
            super(title, null);
        }

        @Override
        public int height() {
            return 18;
        }

        @Override
        void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, boolean hovered) {
            g.text(font, label.toUpperCase(java.util.Locale.ROOT), x + 8, y + 7, Theme.ACCENT_LIGHT, false);
            g.fill(x + 8, y + 17, x + width - 6, y + 18, Theme.BORDER);
        }

        @Override
        protected void extractControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, boolean enabled) {
        }
    }

    public static final class Info extends SettingRow {
        private final Supplier<String> value;
        private final Supplier<Integer> color;

        public Info(String label, String description, Supplier<String> value, Supplier<Integer> color) {
            super(label, description);
            this.value = value;
            this.color = color;
        }

        @Override
        protected void extractControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, boolean enabled) {
            String text = value.get();
            g.text(font, text, x + width - 8 - font.width(text), y + (HEIGHT - 8) / 2, color.get(), false);
        }
    }

    public static final class Toggle extends SettingRow {
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;

        public Toggle(String label, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
            super(label, description);
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        protected void extractControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, boolean enabled) {
            boolean on = getter.getAsBoolean();
            int w = 26;
            int h = 12;
            int sx = x + width - w - 8;
            int sy = y + (HEIGHT - h) / 2;
            int track = !enabled ? Theme.CONTROL : on ? Theme.ACCENT : Theme.CONTROL_HOVER;
            g.fill(sx, sy, sx + w, sy + h, track);
            g.outline(sx, sy, w, h, Theme.BORDER);
            int knobX = on ? sx + w - h + 2 : sx + 2;
            g.fill(knobX, sy + 2, knobX + h - 4, sy + h - 2, enabled ? Theme.TEXT : Theme.TEXT_DISABLED);
            String state = on ? "On" : "Off";
            g.text(font, state, sx - 6 - font.width(state), y + (HEIGHT - 8) / 2, enabled ? Theme.TEXT_MUTED : Theme.TEXT_DISABLED, false);
        }

        @Override
        boolean click(double mouseX, double mouseY, int button) {
            setter.accept(!getter.getAsBoolean());
            return true;
        }
    }

    public static final class Slider extends SettingRow {
        private final double min;
        private final double max;
        private final double step;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final DoubleFunction<String> formatter;

        public Slider(String label, String description, double min, double max, double step,
                      DoubleSupplier getter, DoubleConsumer setter, DoubleFunction<String> formatter) {
            super(label, description);
            this.min = min;
            this.max = max;
            this.step = step;
            this.getter = getter;
            this.setter = setter;
            this.formatter = formatter;
        }

        @Override
        protected void extractControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, boolean enabled) {
            int tx = controlX();
            int ty = y + HEIGHT / 2;
            double t = (Math.max(min, Math.min(max, getter.getAsDouble())) - min) / (max - min);
            int filled = (int) Math.round(t * CONTROL_WIDTH);
            g.fill(tx, ty - 1, tx + CONTROL_WIDTH, ty + 1, Theme.CONTROL_HOVER);
            g.fill(tx, ty - 1, tx + filled, ty + 1, enabled ? Theme.ACCENT : Theme.TEXT_DISABLED);
            int knob = tx + filled;
            boolean hovered = enabled && overControl(mouseX, mouseY);
            g.fill(knob - 3, ty - 5, knob + 3, ty + 5, !enabled ? Theme.TEXT_DISABLED : hovered ? Theme.ACCENT_LIGHT : Theme.TEXT);
            String value = formatter.apply(getter.getAsDouble());
            g.text(font, value, tx - 8 - font.width(value), y + (HEIGHT - 8) / 2, enabled ? Theme.TEXT_MUTED : Theme.TEXT_DISABLED, false);
        }

        @Override
        boolean click(double mouseX, double mouseY, int button) {
            if (!overControl(mouseX, mouseY)) {
                return false;
            }
            drag(mouseX);
            return true;
        }

        @Override
        void drag(double mouseX) {
            double t = Math.max(0.0, Math.min(1.0, (mouseX - controlX()) / CONTROL_WIDTH));
            double value = min + t * (max - min);
            value = Math.round(value / step) * step;
            setter.accept(Math.max(min, Math.min(max, value)));
        }

        @Override
        boolean isDraggable() {
            return true;
        }
    }

    public static final class Cycle<E> extends SettingRow {
        private final E[] values;
        private final Supplier<E> getter;
        private final Consumer<E> setter;
        private final Function<E, String> namer;

        public Cycle(String label, String description, E[] values, Supplier<E> getter, Consumer<E> setter, Function<E, String> namer) {
            super(label, description);
            this.values = values;
            this.getter = getter;
            this.setter = setter;
            this.namer = namer;
        }

        @Override
        protected void extractControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, boolean enabled) {
            drawButton(g, font, controlX(), y + 3, CONTROL_WIDTH, HEIGHT - 6, namer.apply(getter.get()), enabled && overControl(mouseX, mouseY), enabled, false);
        }

        @Override
        boolean click(double mouseX, double mouseY, int button) {
            E current = getter.get();
            int index = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == current) index = i;
            }
            int direction = button == 1 ? -1 : 1;
            setter.accept(values[Math.floorMod(index + direction, values.length)]);
            return true;
        }
    }

    public static final class KeyBind extends SettingRow {
        final Supplier<KeyMapping> mapping;
        boolean listening;

        public KeyBind(String label, String description, Supplier<KeyMapping> mapping) {
            super(label, description);
            this.mapping = mapping;
        }

        @Override
        protected void extractControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, boolean enabled) {
            KeyMapping key = mapping.get();
            String text;
            if (key == null) {
                text = "-";
            } else if (listening) {
                text = "> press a key <";
            } else if (key.isUnbound()) {
                text = "Not bound";
            } else {
                text = key.getTranslatedKeyMessage().getString();
            }
            drawButton(g, font, controlX(), y + 3, CONTROL_WIDTH, HEIGHT - 6, text, enabled && overControl(mouseX, mouseY), enabled && key != null, listening);
        }

        @Override
        boolean click(double mouseX, double mouseY, int button) {
            if (mapping.get() == null) {
                return false;
            }
            listening = true;
            return true;
        }

        void bind(InputConstants.Key key) {
            KeyMapping target = mapping.get();
            if (target != null) {
                target.setKey(key);
                KeyMapping.resetMapping();
            }
            listening = false;
        }
    }

    public static final class Action extends SettingRow {
        private final String buttonText;
        private final Runnable action;

        public Action(String label, String description, String buttonText, Runnable action) {
            super(label, description);
            this.buttonText = buttonText;
            this.action = action;
        }

        @Override
        protected void extractControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, boolean enabled) {
            drawButton(g, font, controlX(), y + 3, CONTROL_WIDTH, HEIGHT - 6, buttonText, enabled && overControl(mouseX, mouseY), enabled, true);
        }

        @Override
        boolean click(double mouseX, double mouseY, int button) {
            if (!overControl(mouseX, mouseY)) {
                return false;
            }
            action.run();
            return true;
        }
    }
}
