package org.xast.kebab.utils;

import java.awt.image.BufferedImage;

import lombok.Getter;

public class FavIcon {
    @Getter
    private BufferedImage image;

    public FavIcon(BufferedImage image) {
        this.image = image;
    }
}
