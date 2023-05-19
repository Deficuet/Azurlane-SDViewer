package io.github.deficuet.alsd

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas

class DefaultTextureAtlas(
    private val skeletonFile: FileHandle,
    data: TextureAtlasData
): TextureAtlas(data) {
    override fun findRegion(name: String?): AtlasRegion {
        var region: AtlasRegion? = super.findRegion(name)
        if (region == null) {
            val file = skeletonFile.sibling("${name}.png")
            if (file.exists()) {
                val tex = Texture(file).apply {
                    setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                }
                region = AtlasRegion(tex, 0, 0, tex.width, tex.height).apply {
                    this.name = name
                }
            }
        }
        return region ?: DEFAULT_REGION
    }

    companion object {
        private val DEFAULT_REGION = Pixmap(32, 32, Pixmap.Format.RGBA8888).let {
            it.setColor(Color(1f, 1f, 1f, 0.33f))
            it.fill()
            val fake = AtlasRegion(Texture(it), 0, 0, 32, 32)
            it.dispose()
            fake
        }
    }
}