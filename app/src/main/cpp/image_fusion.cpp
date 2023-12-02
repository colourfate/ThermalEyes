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

#define CAM_UV_K 1
#define THERM_UV_K 0.7
#define CAM_Y_K 1
#define THERM_Y_K 0

#define REF_LEN 4
#define RANGE 255

#define COLOR(v, c) (((v) >> (8 * (c))) & 0xff)

enum { Y, COLOR_MAX };

typedef struct {
    uint32_t width, height;
    uint8_t *result;
    const uint8_t *cam;
    const uint8_t *therm;
    const uint8_t *ref_t[REF_LEN * 2 + 1];
    const uint8_t *ref_c[REF_LEN * 2 + 1];
    uint8_t last_t_row[REF_LEN * 2 + 1];
    uint8_t last_val[COLOR_MAX];
    float last_t_sum[COLOR_MAX];
    float last_sum[COLOR_MAX];
} image_data;

static image_data g_image;

void fusion_init(uint32_t width, uint32_t height)
{
    g_image.width = width;
    g_image.height = height;
}

static void get_ref_image(image_data *image, uint32_t y1, uint32_t y2, uint32_t x1)
{
    uint32_t i;

    for (i = y1; i <= y2; i++) {
        image->ref_c[i - y1] = &image->cam[i * image->width + x1];
        image->ref_t[i - y1] = &image->therm[i * image->width + x1];
    }
}

static inline void color_int_2_byte(uint8_t arr[COLOR_MAX], uint8_t color)
{
    uint32_t i;

    for (i = 0; i < COLOR_MAX; i++) {
        arr[i] = COLOR(color, i);
    }
}

static inline uint8_t color_byte_2_int(uint8_t arr[COLOR_MAX])
{
    uint32_t color = 0, i;

    for (i = 0; i < COLOR_MAX; i++) {
        color = color << 8;
        color |= arr[i];
    }

    return color;
}

static void calculate_fustion_image_all(image_data *image, uint32_t ref_w, uint32_t ref_h, uint32_t pos)
{
    float sum[COLOR_MAX] = { 0 };
    float avg[COLOR_MAX];
    uint8_t cam[COLOR_MAX];
    uint32_t i, j, n;

    color_int_2_byte(cam, image->cam[pos]);
    for (i = 0; i < ref_h; i++) {
        for (j = 0; j < ref_w; j++) {
            uint8_t ref_c[COLOR_MAX], ref_t[COLOR_MAX];
            int16_t delta_v[COLOR_MAX];

            color_int_2_byte(ref_c, image->ref_c[i][j]);
            color_int_2_byte(ref_t, image->ref_t[i][j]);
            for (n = 0; n < COLOR_MAX; n++) {
                delta_v[n] = cam[n] - ref_c[n];
                float t_hat = ref_t[n] + ((float)delta_v[n] / 255.0f) * RANGE;
                sum[n] += t_hat;
            }
        }
    }

    uint8_t rst[COLOR_MAX];
    for (n = 0; n < COLOR_MAX; n++) {
        avg[n] = sum[n] / (ref_w * ref_h);
        image->last_val[n] = cam[n];
        image->last_sum[n] = sum[n];

        if (avg[n] > 255) {
            avg[n] = 255;
        } else if (avg[n] < 0) {
            avg[n] = 0;
        }
        rst[n] = (uint8_t)avg[n];
    }

    for (i = 0; i < ref_h; i++) {
        image->last_t_row[i] = image->ref_t[i][0];
    }

    image->result[pos] = color_byte_2_int(rst);
}

static void calculate_fustion_image(image_data *image, uint32_t x1, uint32_t y1, uint32_t x2, uint32_t y2, uint32_t pos)
{
    uint8_t cam[COLOR_MAX];
    float sum[COLOR_MAX];
    float avg[COLOR_MAX];
    uint32_t ref_w = x2 - x1 + 1;
    uint32_t ref_h = y2 - y1 + 1;
    uint32_t i, n;

    color_int_2_byte(cam, image->cam[pos]);
    for (n = 0; n < COLOR_MAX; n++) {
        int ddelta_c, delta_t;
        uint32_t ddelta_c_middle_sum = (cam[n] - image->last_val[n]) * (ref_h * (ref_w - 1));
        uint32_t ddelta_c_right_sum = 0;
        uint32_t ddelta_c_left_sum = 0;
        uint32_t delta_t_right_sum = 0;
        uint32_t delta_t_left_sum = 0;

        for (i = 0; i < ref_h; i++) {
            // Reference area does not reach the right boundary
            if (x1 < image->width - REF_LEN * 2) {
                delta_t_right_sum += COLOR(image->ref_t[i][ref_w - 1], n);
                ddelta_c_right_sum += cam[n] - COLOR(image->ref_c[i][ref_w - 1], n);
            }
            // Rreference area is out of the left boundary
            if (x1 != 0) {
                delta_t_left_sum += COLOR(image->last_t_row[i], n);
                ddelta_c_left_sum += cam[n] - COLOR(image->ref_c[i][0], n);
            }
        }
        ddelta_c = ddelta_c_middle_sum + ddelta_c_right_sum - ddelta_c_left_sum;
        delta_t = delta_t_right_sum - delta_t_left_sum;

        sum[n] = image->last_sum[n] + delta_t + (float)ddelta_c / 255.0f * RANGE;
    }

    uint8_t rst[COLOR_MAX];
    for (n = 0; n < COLOR_MAX; n++) {
        avg[n] = sum[n] / (ref_w * ref_h);
        image->last_val[n] = cam[n];
        image->last_sum[n] = sum[n];

        if (avg[n] > 255) {
            avg[n] = 255;
        } else if (avg[n] < 0) {
            avg[n] = 0;
        }
        rst[n] = (uint8_t)avg[n];
    }

    for (i = 0; i < ref_h; i++) {
        image->last_t_row[i] = image->ref_t[i][0];
    }

    image->result[pos] = color_byte_2_int(rst);
}

void ref_search_fusion(uint32_t *fusion_data, const uint32_t *cam_data, const uint32_t *therm_data)
{
    int32_t i, j;
    uint32_t width = g_image.width;
    uint32_t height = g_image.height;

    g_image.cam = (uint8_t *)cam_data;
    g_image.therm = (uint8_t *)therm_data;
    g_image.result = (uint8_t *)fusion_data;

    for (i = 0; i < height; i++) {
        bool is_first = true;

        for (j = 0; j < width; j++) {
            uint32_t x1, x2, y1, y2;

            x1 = MAX(j - REF_LEN, 0);
            y1 = MAX(i - REF_LEN, 0);
            x2 = MIN(j + REF_LEN, width - 1);
            y2 = MIN(i + REF_LEN, height - 1);

            get_ref_image(&g_image, y1, y2, x1);
            if (is_first) {
                calculate_fustion_image_all(&g_image, x2 - x1 + 1, y2 - y1 + 1, i * width + j);
                is_first = false;
            } else {
                calculate_fustion_image(&g_image, x1, y1, x2, y2, i * width + j);
            }
        }
    }
}

void color_map_fusion(uint32_t *fusion_data, const uint32_t *cam_data, const uint32_t *therm_data)
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
            int8_t u_off, v_off;
            int u_val, v_val;
            uint8_t gray = therm_y[i * width * 2 + j * 2];
            uint8_t red = abs(0 - gray);
            uint8_t green = abs(127 - gray);
            uint8_t blue = abs(255 - gray);

            u_off = -0.1687 * (red - 128) - 0.3313 * (green - 128) + 0.5 * (blue - 128);
            v_off = 0.5 * (red - 128) - 0.4187 * (green - 128) - 0.0813 * (blue - 128);

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
            uint8_t gray = therm_y[pos];
            uint8_t red = abs(0 - gray);
            uint8_t green = abs(127 - gray);
            uint8_t blue = abs(255 - gray);
            int8_t y_off = 0.299 * (red - 128) - 0.587 * (green - 128) - 0.114 * (blue - 128);

            y_val = (int)(cam_y[pos] * CAM_Y_K + y_off * THERM_Y_K);
            fusion_y[pos] = (uint8_t)CLIP(y_val, 0, 255);
        }
    }
}

void fusion_get_image(uint32_t *fusion_data, const uint32_t *cam_data, const uint32_t *therm_data)
{
    uint32_t *therm_hat = (uint32_t *)malloc(g_image.width * g_image.width * 3 / 2);

    if (therm_hat == NULL) {
        printf("therm hat malloc failed\n");
        return;
    }

    ref_search_fusion(therm_hat, cam_data, therm_data);
    color_map_fusion(fusion_data, cam_data, therm_hat);

    //color_map_fusion(fusion_data, cam_data, therm_data);

    //ref_search_fusion(fusion_data, cam_data, therm_data);

    free(therm_hat);
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
    Mat grayMat;

    /* 数据融合 */
    LOGE("fusion start");
    fusion_init(width, height);
    fusion_get_image((uint32_t *)fusion_data, (uint32_t *)cam_data, (uint32_t *)therm_data);
    LOGE("fusion end");

    env->ReleaseByteArrayElements(fusionData, fusion_data, 0);
    env->ReleaseByteArrayElements(camData, cam_data, 0);
    env->ReleaseByteArrayElements(thermData, therm_data, 0);
}
