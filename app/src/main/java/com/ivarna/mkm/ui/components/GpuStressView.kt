package com.ivarna.mkm.ui.components

import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ivarna.mkm.ui.opengl.GpuLoadRenderer

@Composable
fun GpuStressView(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setRenderer(GpuLoadRenderer())
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        },
        modifier = modifier
    )
}
