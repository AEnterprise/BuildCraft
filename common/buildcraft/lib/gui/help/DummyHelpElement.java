/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.help;

import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.help.ElementHelpInfo.HelpPosition;
import buildcraft.lib.gui.pos.IGuiArea;

/** A simple, non-drawing, gui element that holds an {@link ElementHelpInfo}. */
public class DummyHelpElement implements IGuiElement {
    public final IGuiArea area;
    public final ElementHelpInfo help;

    public DummyHelpElement(IGuiArea area, ElementHelpInfo help) {
        this.area = area;
        this.help = help;
    }

    @Override
    public int getX() {
        return area.getX();
    }

    @Override
    public int getY() {
        return area.getY();
    }

    @Override
    public int getWidth() {
        return area.getWidth();
    }

    @Override
    public int getHeight() {
        return area.getHeight();
    }

    @Override
    public HelpPosition getHelpInfo() {
        return help.target(area);
    }
}
