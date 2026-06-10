package io.melan.socz.collectors

import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES20

data class GpuInfo(
    val openGlVendor: String,
    val openGlRenderer: String,
    val openGlVersion: String,
    val glslVersion: String,
    val openGlExtensions: List<String>,
    val vulkanExtensions: List<String>,
    /** Common compute / RT extensions surfaced as flags. */
    val supportsRayTracingPipeline: Boolean,
    val supportsAccelerationStructure: Boolean,
    val supportsRayQuery: Boolean,
    val supportsMeshShader: Boolean,
    val supportsBindlessTextures: Boolean,
    /** Adreno-specific. From GL_RENDERER e.g. "Adreno (TM) 740" → 740. */
    val adrenoModel: Int?,
    /** Vulkan API version we managed to query, or null if vk not loadable. */
    val vulkanApiVersion: String?,
)

object GpuCollector {

    init {
        // libsocz.so contains the native Vulkan extension probe. Loading is lazy
        // at first call to the native method, but we kick it off here to surface
        // any link errors early.
        runCatching { System.loadLibrary("socz") }
    }

    // Implemented in cpp/vk_probe.cpp. Creates a transient VkInstance and returns
    // all device + instance extensions of the first physical device.
    @JvmStatic external fun probeVulkanExtensionsNative(): Array<String>

    fun read(ctx: Context): GpuInfo {
        val gl = readOpenGl()
        val glSet = gl.extensions.toSet()
        // Real probe — must hit the Vulkan loader, NOT search GLES extensions for
        // Vulkan names (that's a bug that made every device look RT-incapable).
        val vkExts = runCatching { probeVulkanExtensionsNative().toList() }
            .getOrDefault(emptyList())
        val vkSet = vkExts.toSet()

        return GpuInfo(
            openGlVendor = gl.vendor,
            openGlRenderer = gl.renderer,
            openGlVersion = gl.version,
            glslVersion = gl.glsl,
            openGlExtensions = gl.extensions,
            vulkanExtensions = vkExts.sorted(),
            supportsRayTracingPipeline = "VK_KHR_ray_tracing_pipeline" in vkSet,
            supportsAccelerationStructure = "VK_KHR_acceleration_structure" in vkSet,
            supportsRayQuery = "VK_KHR_ray_query" in vkSet,
            supportsMeshShader = "VK_EXT_mesh_shader" in vkSet || "GL_EXT_mesh_shader" in glSet,
            supportsBindlessTextures = "VK_EXT_descriptor_indexing" in vkSet ||
                "GL_NV_bindless_texture" in glSet,
            adrenoModel = parseAdreno(gl.renderer),
            vulkanApiVersion = readVulkanApiVersion(ctx),
        )
    }

    private data class GlInfo(
        val vendor: String, val renderer: String, val version: String, val glsl: String,
        val extensions: List<String>,
    )

    /** Headless EGL14 + pbuffer context, just to query the GL info strings. */
    private fun readOpenGl(): GlInfo {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return blankGl()
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return blankGl()

        val attribs = intArrayOf(
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            // EGL_RENDERABLE_TYPE = 0x3040 (named in EGLExt); bitmask: ES2=4, ES3=0x40
            EGL14.EGL_RENDERABLE_TYPE, 0x0040,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val num = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) || num[0] == 0) {
            EGL14.eglTerminate(display); return blankGl()
        }
        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        if (context == EGL14.EGL_NO_CONTEXT) {
            EGL14.eglTerminate(display); return blankGl()
        }
        val pbAttribs = intArrayOf(EGL14.EGL_WIDTH, 4, EGL14.EGL_HEIGHT, 4, EGL14.EGL_NONE)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], pbAttribs, 0)
        if (surface == EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroyContext(display, context); EGL14.eglTerminate(display); return blankGl()
        }
        EGL14.eglMakeCurrent(display, surface, surface, context)
        val info = try {
            val ext = (GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: "").split(' ').filter { it.isNotBlank() }
            GlInfo(
                vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "?",
                renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "?",
                version = GLES20.glGetString(GLES20.GL_VERSION) ?: "?",
                glsl = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION) ?: "?",
                extensions = ext.sorted(),
            )
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        return info
    }

    private fun blankGl() = GlInfo("?", "?", "?", "?", emptyList())

    /**
     * Quick Vulkan API version probe through PackageManager — checks the device
     * feature `android.hardware.vulkan.version` reported by Android's loader.
     * No JNI required.
     */
    private fun readVulkanApiVersion(ctx: Context): String? {
        val features = ctx.packageManager.systemAvailableFeatures
            .filter { it.name == "android.hardware.vulkan.version" || it.name == "android.hardware.vulkan.level" }
        val ver = features.firstOrNull { it.name == "android.hardware.vulkan.version" } ?: return null
        val v = ver.version
        val major = (v ushr 22) and 0x3FF
        val minor = (v ushr 12) and 0x3FF
        val patch = v and 0xFFF
        val level = features.firstOrNull { it.name == "android.hardware.vulkan.level" }?.version ?: 0
        return "$major.$minor.$patch (level $level)"
    }

    private fun parseAdreno(renderer: String): Int? {
        val m = Regex("Adreno.*?(\\d{3,})").find(renderer) ?: return null
        return m.groupValues[1].toIntOrNull()
    }
}
