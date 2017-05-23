/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.font;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public enum FontManager {
    INSTANCE;
    private final Map<String, IFontRenderer> fonts = new HashMap<>();
    private final Map<String, Supplier<IFontRenderer>> potentialFonts = new HashMap<>();

    public IFontRenderer getOrLoadFont(String name) {
        if (fonts.containsKey(name)) {
            return fonts.get(name);
        }
        if (potentialFonts.containsKey(name)) {
            Supplier<IFontRenderer> supplier = potentialFonts.remove(name);
            if (supplier != null) {
                IFontRenderer render = supplier.get();
                if (render != null) {
                    fonts.put(name, render);
                    return render;
                }
            }
        }
        return MinecraftFont.INSTANCE;
    }

    public void registerFont(String name, IFontRenderer font) {
        if (font == null) throw new NullPointerException("font");
        if (fonts.containsKey(name)) {
            throw new IllegalStateException("Cannot register the font \"" + name + "\" twice!");
        }
        fonts.put(name, font);
    }

    public void registerGuideFont(String name, InputStream stream) throws Exception {
        // registerFont(name, new GuideFont(stream));
        throw new AbstractMethodError("TODO: implement this!");

    }
}
