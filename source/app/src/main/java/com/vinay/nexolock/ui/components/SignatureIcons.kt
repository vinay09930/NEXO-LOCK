package com.vinay.nexolock.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Outline icon set used by the Signature design system. Material Icons Core
 * does not ship security/vault/file glyphs, so the missing ones are drawn here
 * with one consistent 24 dp grid and 1.7 dp rounded stroke.
 */
object NexoIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private fun ImageVector.Builder.stroke(block: PathBuilder.() -> Unit) {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            fill = null,
            pathBuilder = block,
        )
    }

    val Shield: ImageVector = icon("Shield") {
        stroke {
            moveTo(12f, 3f)
            lineTo(19f, 6f)
            lineTo(19f, 11.2f)
            curveTo(19f, 15.4f, 16.2f, 18.8f, 12f, 21f)
            curveTo(7.8f, 18.8f, 5f, 15.4f, 5f, 11.2f)
            lineTo(5f, 6f)
            close()
        }
    }

    val ShieldCheck: ImageVector = icon("ShieldCheck") {
        stroke {
            moveTo(12f, 3f)
            lineTo(19f, 6f)
            lineTo(19f, 11.2f)
            curveTo(19f, 15.4f, 16.2f, 18.8f, 12f, 21f)
            curveTo(7.8f, 18.8f, 5f, 15.4f, 5f, 11.2f)
            lineTo(5f, 6f)
            close()
            moveTo(8.8f, 11.8f)
            lineTo(11.1f, 14.1f)
            lineTo(15.4f, 9.5f)
        }
    }

    val Vault: ImageVector = icon("Vault") {
        stroke {
            moveTo(4.5f, 4.5f)
            lineTo(19.5f, 4.5f)
            lineTo(19.5f, 19.5f)
            lineTo(4.5f, 19.5f)
            close()
            moveTo(12f, 8f)
            curveTo(14.2f, 8f, 16f, 9.8f, 16f, 12f)
            curveTo(16f, 14.2f, 14.2f, 16f, 12f, 16f)
            curveTo(9.8f, 16f, 8f, 14.2f, 8f, 12f)
            curveTo(8f, 9.8f, 9.8f, 8f, 12f, 8f)
            close()
            moveTo(12f, 10.8f)
            lineTo(12f, 13.2f)
            moveTo(16f, 12f)
            lineTo(18.3f, 12f)
            moveTo(12f, 16f)
            lineTo(12f, 18.3f)
            moveTo(8f, 12f)
            lineTo(5.7f, 12f)
        }
    }

    val Folder: ImageVector = icon("Folder") {
        stroke {
            moveTo(3.5f, 7f)
            curveTo(3.5f, 6.2f, 4.1f, 5.5f, 5f, 5.5f)
            lineTo(9f, 5.5f)
            lineTo(10.8f, 7.5f)
            lineTo(19f, 7.5f)
            curveTo(19.8f, 7.5f, 20.5f, 8.2f, 20.5f, 9f)
            lineTo(20.5f, 17.5f)
            curveTo(20.5f, 18.3f, 19.8f, 19f, 19f, 19f)
            lineTo(5f, 19f)
            curveTo(4.1f, 19f, 3.5f, 18.3f, 3.5f, 17.5f)
            close()
        }
    }

    val Image: ImageVector = icon("Image") {
        stroke {
            moveTo(4.5f, 4.5f)
            lineTo(19.5f, 4.5f)
            lineTo(19.5f, 19.5f)
            lineTo(4.5f, 19.5f)
            close()
            moveTo(8.6f, 9.2f)
            curveTo(9.2f, 9.2f, 9.7f, 9.7f, 9.7f, 10.3f)
            curveTo(9.7f, 10.9f, 9.2f, 11.4f, 8.6f, 11.4f)
            curveTo(8f, 11.4f, 7.5f, 10.9f, 7.5f, 10.3f)
            curveTo(7.5f, 9.7f, 8f, 9.2f, 8.6f, 9.2f)
            close()
            moveTo(4.8f, 17.2f)
            lineTo(9.6f, 12.6f)
            lineTo(12.6f, 15.4f)
            lineTo(15.2f, 12.9f)
            lineTo(19.4f, 17.1f)
        }
    }

    val Video: ImageVector = icon("Video") {
        stroke {
            moveTo(3.5f, 6.5f)
            lineTo(15.5f, 6.5f)
            lineTo(15.5f, 17.5f)
            lineTo(3.5f, 17.5f)
            close()
            moveTo(15.5f, 10.2f)
            lineTo(20.5f, 7.5f)
            lineTo(20.5f, 16.5f)
            lineTo(15.5f, 13.8f)
        }
    }

    val Document: ImageVector = icon("Document") {
        stroke {
            moveTo(6f, 3.5f)
            lineTo(14f, 3.5f)
            lineTo(18f, 7.5f)
            lineTo(18f, 20.5f)
            lineTo(6f, 20.5f)
            close()
            moveTo(14f, 3.5f)
            lineTo(14f, 7.5f)
            lineTo(18f, 7.5f)
            moveTo(9f, 12f)
            lineTo(15f, 12f)
            moveTo(9f, 15.5f)
            lineTo(15f, 15.5f)
        }
    }

    val Audio: ImageVector = icon("Audio") {
        stroke {
            moveTo(9f, 17.5f)
            lineTo(9f, 6.5f)
            lineTo(18.5f, 4.5f)
            lineTo(18.5f, 15.5f)
            moveTo(9f, 17.5f)
            curveTo(9f, 18.6f, 7.9f, 19.5f, 6.5f, 19.5f)
            curveTo(5.1f, 19.5f, 4f, 18.6f, 4f, 17.5f)
            curveTo(4f, 16.4f, 5.1f, 15.5f, 6.5f, 15.5f)
            curveTo(7.9f, 15.5f, 9f, 16.4f, 9f, 17.5f)
            close()
            moveTo(18.5f, 15.5f)
            curveTo(18.5f, 16.6f, 17.4f, 17.5f, 16f, 17.5f)
            curveTo(14.6f, 17.5f, 13.5f, 16.6f, 13.5f, 15.5f)
            curveTo(13.5f, 14.4f, 14.6f, 13.5f, 16f, 13.5f)
            curveTo(17.4f, 13.5f, 18.5f, 14.4f, 18.5f, 15.5f)
            close()
        }
    }

    val Note: ImageVector = icon("Note") {
        stroke {
            moveTo(5.5f, 3.5f)
            lineTo(18.5f, 3.5f)
            lineTo(18.5f, 20.5f)
            lineTo(5.5f, 20.5f)
            close()
            moveTo(8.5f, 8f)
            lineTo(15.5f, 8f)
            moveTo(8.5f, 12f)
            lineTo(15.5f, 12f)
            moveTo(8.5f, 16f)
            lineTo(13f, 16f)
        }
    }

    val Fingerprint: ImageVector = icon("Fingerprint") {
        stroke {
            moveTo(12f, 4f)
            curveTo(8.7f, 4f, 6f, 6.7f, 6f, 10f)
            lineTo(6f, 12.5f)
            moveTo(12f, 4f)
            curveTo(15.3f, 4f, 18f, 6.7f, 18f, 10f)
            lineTo(18f, 12.5f)
            moveTo(9f, 10f)
            curveTo(9f, 8.3f, 10.3f, 7f, 12f, 7f)
            curveTo(13.7f, 7f, 15f, 8.3f, 15f, 10f)
            lineTo(15f, 13f)
            curveTo(15f, 14.7f, 13.7f, 16f, 12f, 16f)
            moveTo(9f, 12.5f)
            lineTo(9f, 14f)
            moveTo(12f, 10.5f)
            lineTo(12f, 15.5f)
            curveTo(12f, 17.4f, 10.4f, 19f, 8.5f, 19f)
            moveTo(6.5f, 16.5f)
            curveTo(6.8f, 17.5f, 7.2f, 18.4f, 7.8f, 19.2f)
        }
    }

    val Key: ImageVector = icon("Key") {
        stroke {
            moveTo(8.5f, 15.5f)
            curveTo(6.6f, 15.5f, 5f, 13.9f, 5f, 12f)
            curveTo(5f, 10.1f, 6.6f, 8.5f, 8.5f, 8.5f)
            curveTo(10.4f, 8.5f, 12f, 10.1f, 12f, 12f)
            lineTo(20f, 12f)
            moveTo(17f, 12f)
            lineTo(17f, 15f)
            moveTo(20f, 12f)
            lineTo(20f, 15f)
            moveTo(8.5f, 10.8f)
            lineTo(8.5f, 13.2f)
        }
    }

    val Camera: ImageVector = icon("Camera") {
        stroke {
            moveTo(3.5f, 8.5f)
            lineTo(7.2f, 8.5f)
            lineTo(8.7f, 6f)
            lineTo(15.3f, 6f)
            lineTo(16.8f, 8.5f)
            lineTo(20.5f, 8.5f)
            lineTo(20.5f, 19f)
            lineTo(3.5f, 19f)
            close()
            moveTo(12f, 10f)
            curveTo(14.2f, 10f, 16f, 11.6f, 16f, 13.7f)
            curveTo(16f, 15.8f, 14.2f, 17.4f, 12f, 17.4f)
            curveTo(9.8f, 17.4f, 8f, 15.8f, 8f, 13.7f)
            curveTo(8f, 11.6f, 9.8f, 10f, 12f, 10f)
            close()
        }
    }

    val Clock: ImageVector = icon("Clock") {
        stroke {
            moveTo(12f, 3.5f)
            curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
            curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
            curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f)
            curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
            close()
            moveTo(12f, 7f)
            lineTo(12f, 12.3f)
            lineTo(15.7f, 14.4f)
        }
    }

    val Sliders: ImageVector = icon("Sliders") {
        stroke {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17f)
            lineTo(20f, 17f)
            moveTo(8f, 7f)
            curveTo(8f, 8.1f, 7.1f, 9f, 6f, 9f)
            curveTo(4.9f, 9f, 4f, 8.1f, 4f, 7f)
            curveTo(4f, 5.9f, 4.9f, 5f, 6f, 5f)
            curveTo(7.1f, 5f, 8f, 5.9f, 8f, 7f)
            close()
            moveTo(18f, 12f)
            curveTo(18f, 13.1f, 17.1f, 14f, 16f, 14f)
            curveTo(14.9f, 14f, 14f, 13.1f, 14f, 12f)
            curveTo(14f, 10.9f, 14.9f, 10f, 16f, 10f)
            curveTo(17.1f, 10f, 18f, 10.9f, 18f, 12f)
            close()
            moveTo(11f, 17f)
            curveTo(11f, 18.1f, 10.1f, 19f, 9f, 19f)
            curveTo(7.9f, 19f, 7f, 18.1f, 7f, 17f)
            curveTo(7f, 15.9f, 7.9f, 15f, 9f, 15f)
            curveTo(10.1f, 15f, 11f, 15.9f, 11f, 17f)
            close()
        }
    }

    val Grid: ImageVector = icon("Grid") {
        stroke {
            moveTo(4.5f, 4.5f)
            lineTo(10.5f, 4.5f)
            lineTo(10.5f, 10.5f)
            lineTo(4.5f, 10.5f)
            close()
            moveTo(13.5f, 4.5f)
            lineTo(19.5f, 4.5f)
            lineTo(19.5f, 10.5f)
            lineTo(13.5f, 10.5f)
            close()
            moveTo(4.5f, 13.5f)
            lineTo(10.5f, 13.5f)
            lineTo(10.5f, 19.5f)
            lineTo(4.5f, 19.5f)
            close()
            moveTo(13.5f, 13.5f)
            lineTo(19.5f, 13.5f)
            lineTo(19.5f, 19.5f)
            lineTo(13.5f, 19.5f)
            close()
        }
    }

    val Import: ImageVector = icon("Import") {
        stroke {
            moveTo(12f, 3.5f)
            lineTo(12f, 14.5f)
            moveTo(8f, 10.5f)
            lineTo(12f, 14.5f)
            lineTo(16f, 10.5f)
            moveTo(4f, 16.5f)
            lineTo(4f, 20.5f)
            lineTo(20f, 20.5f)
            lineTo(20f, 16.5f)
        }
    }

    val Export: ImageVector = icon("Export") {
        stroke {
            moveTo(12f, 14.5f)
            lineTo(12f, 3.5f)
            moveTo(8f, 7.5f)
            lineTo(12f, 3.5f)
            lineTo(16f, 7.5f)
            moveTo(4f, 16.5f)
            lineTo(4f, 20.5f)
            lineTo(20f, 20.5f)
            lineTo(20f, 16.5f)
        }
    }

    val Eye: ImageVector = icon("Eye") {
        stroke {
            moveTo(2.8f, 12f)
            curveTo(5f, 7.8f, 8.3f, 5.8f, 12f, 5.8f)
            curveTo(15.7f, 5.8f, 19f, 7.8f, 21.2f, 12f)
            curveTo(19f, 16.2f, 15.7f, 18.2f, 12f, 18.2f)
            curveTo(8.3f, 18.2f, 5f, 16.2f, 2.8f, 12f)
            close()
            moveTo(12f, 9f)
            curveTo(13.7f, 9f, 15f, 10.3f, 15f, 12f)
            curveTo(15f, 13.7f, 13.7f, 15f, 12f, 15f)
            curveTo(10.3f, 15f, 9f, 13.7f, 9f, 12f)
            curveTo(9f, 10.3f, 10.3f, 9f, 12f, 9f)
            close()
        }
    }

    val LockOpen: ImageVector = icon("LockOpen") {
        stroke {
            moveTo(6f, 10.5f)
            lineTo(18f, 10.5f)
            lineTo(18f, 20f)
            lineTo(6f, 20f)
            close()
            moveTo(8.5f, 10.5f)
            lineTo(8.5f, 7.5f)
            curveTo(8.5f, 5.6f, 10.1f, 4f, 12f, 4f)
            curveTo(13.4f, 4f, 14.6f, 4.8f, 15.2f, 6f)
            moveTo(12f, 14f)
            lineTo(12f, 16.5f)
        }
    }

    val Album: ImageVector = icon("Album") {
        stroke {
            moveTo(3.5f, 6.5f)
            lineTo(20.5f, 6.5f)
            lineTo(20.5f, 18.5f)
            lineTo(3.5f, 18.5f)
            close()
            moveTo(7f, 6.5f)
            lineTo(7f, 4.5f)
            lineTo(17f, 4.5f)
            lineTo(17f, 6.5f)
            moveTo(3.8f, 15.5f)
            lineTo(8.5f, 11.5f)
            lineTo(11.5f, 14f)
            lineTo(14.5f, 11.5f)
            lineTo(20.2f, 16f)
        }
    }
}
