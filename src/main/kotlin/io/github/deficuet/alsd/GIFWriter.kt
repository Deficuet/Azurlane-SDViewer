package io.github.deficuet.alsd

import io.github.deficuet.unitykt.cast
import java.awt.image.RenderedImage
import java.io.Closeable
import javax.imageio.*
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.ImageOutputStream

class GIFWriter(
    out: ImageOutputStream, imageType: Int, fps: Int, loop: Boolean
): Closeable {
    private val writer = ImageIO.getImageWritersBySuffix("gif").next()
    private val params = writer.defaultWriteParam
    private val metadata = writer.getDefaultImageMetadata(
        ImageTypeSpecifier.createFromBufferedImageType(imageType), params
    )

    init {
        configureRootMetadata(100 / fps, loop)
        writer.output = out
        writer.prepareWriteSequence(null)
    }

    private fun configureRootMetadata(delay: Int, loop: Boolean) {
        val metaFormatName = metadata.nativeMetadataFormatName
        val root = metadata.getAsTree(metaFormatName) as IIOMetadataNode
        root.getNode("GraphicControlExtension").apply {
            setAttribute("disposalMethod", "none")
            setAttribute("userInputFlag", "FALSE")
            setAttribute("transparentColorFlag", "FALSE")
            setAttribute("delayTime", delay.toString())
            setAttribute("transparentColorIndex", "0")
        }
        root.getNode("CommentExtensions").apply {
            setAttribute("CommentExtension", "ReImpl by Deficuet")
        }
        val appExtNode = root.getNode("ApplicationExtensions")
        val child = IIOMetadataNode("ApplicationExtension").apply {
            setAttribute("applicationID", "NETSCAPE")
            setAttribute("authenticationCode", "2.0")
            val doLoop = if (loop) 0 else 1
            userObject = byteArrayOf(
                0x1, doLoop.and(0xFF).toByte(),
                doLoop.shr(8).and(0xFF).toByte()
            )
        }
        appExtNode.appendChild(child)
        metadata.setFromTree(metaFormatName, root)
    }

    fun writeToSequence(img: RenderedImage) {
        writer.writeToSequence(IIOImage(img, null, metadata), params)
    }

    override fun close() {
        writer.endWriteSequence()
        writer.output.cast<ImageOutputStream>().apply {
            flush()
            close()
        }
    }

    private fun IIOMetadataNode.getNode(nodeName: String): IIOMetadataNode {
        for (i in 0 until length) {
            val name = item(i)?.nodeName
            if (name != null && name.equals(nodeName, true)) {
                return item(i) as IIOMetadataNode
            }
        }
        val node = IIOMetadataNode(nodeName)
        appendChild(node)
        return node
    }
}
