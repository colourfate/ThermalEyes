#include <jni.h>
#include <math.h>
#include <android/log.h>
#include <opencv2/opencv.hpp>

using namespace cv;

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

#define CAM_UV_K 0
#define THERM_UV_K 1
#define CAM_Y_K 0
#define THERM_Y_K 1

#define REF_LEN 4
#define RANGE 255

#define COLOR(v, c) (((v) >> (8 * (c))) & 0xff)

enum { Y, COLOR_MAX };

typedef struct {
    uint32_t cam_width, cam_height;
    uint32_t therm_width, therm_height;
    uint8_t *result;
    const uint8_t *cam;
    const uint8_t *therm;
} image_data;

static image_data g_image;

void color_map_fusion(uint8_t *fusion_data, const uint8_t *cam_data, const uint8_t *therm_data)
{
    const uint8_t *therm_y = therm_data;
    uint32_t width = g_image.cam_width;
    uint32_t height = g_image.cam_height;
    uint32_t i, j;

    uint8_t *cam_uv = (uint8_t *)cam_data +  width * height;
    uint8_t *fusion_uv = (uint8_t *)fusion_data +  width * height;
    for (i = 0; i < height / 2; i++) {
        for (j = 0; j < width / 2; j++) {
            uint32_t v = (i * width / 2 + j) * 2;
            uint32_t u = v + 1;
            int u_off, v_off;
            int u_val, v_val;
            uint8_t blue = therm_y[i * width * 2 * 3 + j * 2 * 3];
            uint8_t green = therm_y[i * width * 2 * 3 + j * 2 * 3 + 1];
            uint8_t red = therm_y[i * width * 2 * 3 + j * 2 * 3 + 2];

            //u_off = -0.1687 * (red - 128) - 0.3313 * (green - 128) + 0.5 * (blue - 128);
            //v_off = 0.5 * (red - 128) - 0.4187 * (green - 128) - 0.0813 * (blue - 128);
            //u_off = -0.148 * red - 0.291 * green + 0.439 * blue;
            //v_off = 0.439 * red - 0.368 * green - 0.071 * blue;
            u_off = -0.148 * red - 0.291 * green + 0.439 * blue + 128;
            v_off = 0.439 * red - 0.368 * green - 0.071 * blue + 128;

            u_val = (int)(cam_uv[u] * CAM_UV_K + u_off * THERM_UV_K);
            v_val = (int)(cam_uv[v] * CAM_UV_K + v_off * THERM_UV_K);
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
            uint8_t blue = therm_y[pos * 3];
            uint8_t green = therm_y[pos * 3 + 1];
            uint8_t red = therm_y[pos * 3 + 2];

            //int8_t y_off = 0.299 * (red - 128) + 0.587 * (green - 128) + 0.114 * (blue - 128);
            //int y_off = 0.257 * red + 0.504 * green + 0.098 * blue + 16 - 128;
            int y_off = 0.257 * red + 0.504 * green + 0.098 * blue + 16;

            y_val = (int)(cam_y[pos] * CAM_Y_K + y_off * THERM_Y_K);
            fusion_y[pos] = (uint8_t)CLIP(y_val, 0, 255);
        }
    }
}


void fusion_get_image(uint32_t *fusion_data, const uint32_t *cam_data, const uint32_t *therm_data)
{
    Mat im_cam(g_image.cam_height, g_image.cam_width, CV_8UC1, (uint8_t *)cam_data);
    Mat im_therm(g_image.therm_height, g_image.therm_width, CV_8UC1, (uint8_t *)therm_data);

    Mat im_therm_scale, im_therm_mirror;
    flip(im_therm, im_therm_mirror, 1);
    resize(im_therm_mirror, im_therm_scale, Size(im_cam.cols, im_cam.rows), 0, 0, INTER_LINEAR);

    Mat im_cam_l;
    GaussianBlur(im_cam, im_cam_l, Size(11, 11), 5, 5);
    Mat im_cam_h = im_cam - im_cam_l;
    Mat im_fusion = im_cam_h + im_therm_scale;
    Mat im_fusion_color;
    applyColorMap(im_fusion, im_fusion_color, COLORMAP_JET);

    color_map_fusion((uint8_t *)fusion_data, (uint8_t *)cam_data, im_fusion_color.data);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_thermaleyes_ImageFusion_getFusionImage(JNIEnv *env, jobject thiz,
                                                        jbyteArray fusionData, jbyteArray camData,
                                                        jbyteArray thermData, jint cam_width,
                                                        jint cam_height, jint therm_width,
                                                        jint therm_height) {
    jbyte *fusion_data = (jbyte *)env->GetByteArrayElements(fusionData, 0);
    jbyte *cam_data = (jbyte *)env->GetByteArrayElements(camData, 0);
    jbyte *therm_data = (jbyte *)env->GetByteArrayElements(thermData, 0);

    /* 数据融合 */
    LOGE("fusion start");
    g_image.cam_width = cam_width;
    g_image.cam_height = cam_height;
    g_image.therm_width = therm_width;
    g_image.therm_height = therm_height;
    fusion_get_image((uint32_t *)fusion_data, (uint32_t *)cam_data, (uint32_t *)therm_data);
    LOGE("fusion end");

    env->ReleaseByteArrayElements(fusionData, fusion_data, 0);
    env->ReleaseByteArrayElements(camData, cam_data, 0);
    env->ReleaseByteArrayElements(thermData, therm_data, 0);
}
