#include <jni.h>
#include <math.h>
#include <android/log.h>

// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("img_algo");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("img_algo")
//      }
//    }
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "jni_c", __VA_ARGS__)
#define MIN(i, j) (((i) < (j)) ? (i) : (j))
#define MAX(i, j) (((i) > (j)) ? (i) : (j))
#define CLIP(x, a, b) ((x) < (a) ? (a) : MIN(x, b))

#define CAM_UV_K 1
#define THERM_UV_K 1
#define CAM_Y_K 1
#define THERM_Y_K 1

typedef struct {
    uint32_t width, height;
} image_data;

static image_data g_image;

void fusion_init(uint32_t width, uint32_t height)
{
    g_image.width = width;
    g_image.height = height;
}

void fusion_get_image(uint32_t *fusion_data, const uint32_t *cam_data, const uint32_t *therm_data)
{
    uint8_t *therm_y = (uint8_t *)therm_data;
    uint32_t width = g_image.width;
    uint32_t height = g_image.height;
    uint32_t i, j;

    uint8_t *cam_uv = (uint8_t *)cam_data +  width * height;
    uint8_t *fusion_uv = (uint8_t *)fusion_data +  width * height;
    for (i = 0; i < height / 2; i++) {
        for (j = 0; j < width / 2; j++) {
            uint32_t v = (i * width / 2 + j) * 2;
            uint32_t u = v + 1;
            int8_t t_val;
            int u_val, v_val;

            t_val = therm_y[i * width * 2 + j * 2] - 128;
            u_val = (int)(cam_uv[u] * CAM_UV_K - t_val * THERM_UV_K);
            v_val = (int)(cam_uv[v] * CAM_UV_K + t_val * THERM_UV_K);
            fusion_uv[u] = (uint8_t)CLIP(u_val, 0, 255);
            fusion_uv[v] = (uint8_t)CLIP(v_val, 0, 255);
        }
    }

    uint8_t *cam_y = (uint8_t *)cam_data;
    uint8_t *fusion_y = (uint8_t *)fusion_data;
    for (i = 0; i < height; i++) {
        for (j = 0; j < width; j++) {
            uint32_t pos = i * width + j;
            int y_val;

            y_val = (int)(cam_y[pos] * CAM_Y_K + (therm_y[pos] - 128) * THERM_Y_K);
            fusion_y[pos] = (uint8_t)CLIP(y_val, 0, 255);
        }
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_thermaleyes_ImageFusion_getFusionImage(JNIEnv *env, jobject thiz,
                                                        jbyteArray fusionData, jbyteArray camData,
                                                        jbyteArray thermData, jint width,
                                                        jint height) {
    jbyte *fusion_data = (jbyte *)env->GetByteArrayElements(fusionData, 0);
    jbyte *cam_data = (jbyte *)env->GetByteArrayElements(camData, 0);
    jbyte *therm_data = (jbyte *)env->GetByteArrayElements(thermData, 0);

    /* 数据融合 */
    LOGE("fusion start");
    fusion_init(width, height);
    fusion_get_image((uint32_t *)fusion_data, (uint32_t *)cam_data, (uint32_t *)therm_data);
    LOGE("fusion end");

    env->ReleaseByteArrayElements(fusionData, fusion_data, 0);
    env->ReleaseByteArrayElements(camData, cam_data, 0);
    env->ReleaseByteArrayElements(thermData, therm_data, 0);
}
