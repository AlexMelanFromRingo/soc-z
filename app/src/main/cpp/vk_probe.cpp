// Minimal Vulkan probe: creates a transient instance and enumerates the *device*
// extensions reported by the first physical device. We need this because Android
// Java/Kotlin has no Vulkan SDK — extension names like VK_KHR_ray_tracing_pipeline
// live entirely in Vulkan-land and aren't visible via PackageManager or GLES.

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <vulkan/vulkan.h>
#include <set>
#include <string>
#include <vector>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "SocZ", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "SocZ", __VA_ARGS__)

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_melan_socz_collectors_GpuCollector_probeVulkanExtensionsNative(JNIEnv* env, jclass) {
    jclass stringCls = env->FindClass("java/lang/String");

    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.pApplicationName = "SocZ-probe";
    app.apiVersion = VK_API_VERSION_1_1;   // ask for as much as possible; loader caps it

    VkInstanceCreateInfo ici{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    ici.pApplicationInfo = &app;

    VkInstance instance = VK_NULL_HANDLE;
    if (vkCreateInstance(&ici, nullptr, &instance) != VK_SUCCESS) {
        LOGE("vkCreateInstance failed in probe");
        return env->NewObjectArray(0, stringCls, nullptr);
    }

    uint32_t pdCount = 0;
    vkEnumeratePhysicalDevices(instance, &pdCount, nullptr);
    std::vector<std::string> exts;
    if (pdCount > 0) {
        std::vector<VkPhysicalDevice> pds(pdCount);
        vkEnumeratePhysicalDevices(instance, &pdCount, pds.data());

        // Concatenate extensions from all physical devices (typically there's just
        // one on Android: the discrete Adreno).
        for (auto pd : pds) {
            uint32_t n = 0;
            vkEnumerateDeviceExtensionProperties(pd, nullptr, &n, nullptr);
            std::vector<VkExtensionProperties> props(n);
            vkEnumerateDeviceExtensionProperties(pd, nullptr, &n, props.data());
            for (auto& p : props) exts.emplace_back(p.extensionName);
        }
    }

    // Also include INSTANCE extensions — though they don't usually contain RT,
    // they're useful to see (e.g. VK_KHR_surface, VK_KHR_get_physical_device_properties_2).
    {
        uint32_t n = 0;
        vkEnumerateInstanceExtensionProperties(nullptr, &n, nullptr);
        std::vector<VkExtensionProperties> props(n);
        vkEnumerateInstanceExtensionProperties(nullptr, &n, props.data());
        for (auto& p : props) exts.emplace_back(p.extensionName);
    }
    vkDestroyInstance(instance, nullptr);

    // Dedup and sort.
    std::set<std::string> uniq(exts.begin(), exts.end());
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(uniq.size()), stringCls, nullptr);
    jsize i = 0;
    for (auto& e : uniq) env->SetObjectArrayElement(out, i++, env->NewStringUTF(e.c_str()));
    return out;
}
