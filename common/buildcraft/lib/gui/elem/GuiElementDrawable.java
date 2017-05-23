/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.elem;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiElementSimple;
import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiPosition;

public class GuiElementDrawable extends GuiElementSimple<GuiBC8<?>> {
    private final ISimpleDrawable drawable;
    private final boolean foreground;

    public GuiElementDrawable(GuiBC8<?> gui, IGuiPosition parent, GuiRectangle rectangle, ISimpleDrawable drawable, boolean foreground) {
        super(gui, parent, rectangle);
        this.drawable = drawable;
        this.foreground = foreground;
    }

    @Override
    public void drawBackground(float partialTicks) {
        if (!foreground) {
            draw();
        }
    }

    @Override
    public void drawForeground(float partialTicks) {
        if (foreground) {
            draw();
        }
    }

    private void draw() {
        drawable.drawAt(this);
    }
}
