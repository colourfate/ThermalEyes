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

#define REF_LEN 8
#define RANGE 255

#define MIN(i, j) (((i) < (j)) ? (i) : (j))
#define MAX(i, j) (((i) > (j)) ? (i) : (j))
#define COLOR_ARGB(v, c) (((v)) >> (8 * (2 - (c))) & 0xff);

enum { R, G, B, COLOR_MAX };

typedef struct {
    uint32_t width, height;
    uint32_t *result;
    uint32_t *cam;
    uint32_t *therm;
    uint32_t *ref_t[REF_LEN * 2 + 1];
    uint32_t *ref_c[REF_LEN * 2 + 1];
    uint32_t last_t_row[REF_LEN * 2 + 1];
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

static void calculate_fustion_image_all(image_data *image, uint32_t ref_w, uint32_t ref_h, uint32_t pos)
{
    float sum[COLOR_MAX] = { 0 };
    float avg[COLOR_MAX];
    uint8_t cam[COLOR_MAX];
    uint32_t i, j, n;

    cam[R] = (image->cam[pos] >> 16) & 0xff;
    cam[G] = (image->cam[pos] >> 8) & 0xff;
    cam[B] = (image->cam[pos]) & 0xff;

    for (i = 0; i < ref_h; i++) {
        for (j = 0; j < ref_w; j++) {
            uint8_t ref_c[COLOR_MAX], ref_t[COLOR_MAX];
            int16_t delta_v[COLOR_MAX];

            ref_c[R] = (image->ref_c[i][j] >> 16) & 0xff;
            ref_c[G] = (image->ref_c[i][j] >> 8) & 0xff;
            ref_c[B] = (image->ref_c[i][j]) & 0xff;
            ref_t[R] = (image->ref_t[i][j] >> 16) & 0xff;
            ref_t[G] = (image->ref_t[i][j] >> 8) & 0xff;
            ref_t[B] = (image->ref_t[i][j]) & 0xff;

            for (n = 0; n < COLOR_MAX; n++) {
                delta_v[n] = cam[n] - ref_c[n];
                float t_hat = ref_t[n] + ((float)delta_v[n] / 255.0f) * RANGE;
                sum[n] += t_hat;
            }
        }
    }

    for (n = 0; n < COLOR_MAX; n++) {
        avg[n] = sum[n] / (ref_w * ref_h);
        image->last_val[n] = cam[n];
        image->last_sum[n] = sum[n];

        if (avg[n] > 255) {
            avg[n] = 255;
        } else if (avg[n] < 0) {
            avg[n] = 0;
        }
    }

    for (i = 0; i < ref_h; i++) {
        image->last_t_row[i] = image->ref_t[i][0];
    }

    image->result[pos] = (((uint8_t)avg[R]) << 16) | (((uint8_t)avg[G]) << 8) | ((uint8_t)avg[B]) | 0xff000000;
}

static void calculate_fustion_image(image_data *image, uint32_t x1, uint32_t y1, uint32_t x2, uint32_t y2, uint32_t pos)
{
    uint8_t cam[COLOR_MAX];
    float sum[COLOR_MAX];
    float avg[COLOR_MAX];
    uint32_t ref_w = x2 - x1 + 1;
    uint32_t ref_h = y2 - y1 + 1;
    uint32_t i, n;

    cam[R] = (image->cam[pos] >> 16) & 0xff;
    cam[G] = (image->cam[pos] >> 8) & 0xff;
    cam[B] = (image->cam[pos]) & 0xff;

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
                delta_t_right_sum += COLOR_ARGB(image->ref_t[i][ref_w - 1], n);
                ddelta_c_right_sum += cam[n] - COLOR_ARGB(image->ref_c[i][ref_w - 1], n);
            }
            // Rreference area is out of the left boundary
            if (x1 != 0) {
                delta_t_left_sum += COLOR_ARGB(image->last_t_row[i], n);
                ddelta_c_left_sum += cam[n] - COLOR_ARGB(image->ref_c[i][0], n);
            }
        }
        ddelta_c = ddelta_c_middle_sum + ddelta_c_right_sum - ddelta_c_left_sum;
        delta_t = delta_t_right_sum - delta_t_left_sum;

        sum[n] = image->last_sum[n] + delta_t + (float)ddelta_c / 255.0f * RANGE;
    }

    for (n = 0; n < COLOR_MAX; n++) {
        avg[n] = sum[n] / (ref_w * ref_h);
        image->last_val[n] = cam[n];
        image->last_sum[n] = sum[n];

        if (avg[n] > 255) {
            avg[n] = 255;
        } else if (avg[n] < 0) {
            avg[n] = 0;
        }
    }

    for (i = 0; i < ref_h; i++) {
        image->last_t_row[i] = image->ref_t[i][0];
    }

    image->result[pos] = (((uint8_t)avg[R]) << 16) | (((uint8_t)avg[G]) << 8) | ((uint8_t)avg[B]) | 0xff000000;
}

void fusion_get_image(uint32_t *fusion_data, uint32_t *cam_data, uint32_t *therm_data)
{
    int32_t i, j;
    uint32_t width = g_image.width;
    uint32_t height = g_image.height;

    g_image.cam = cam_data;
    g_image.therm = therm_data;
    g_image.result = fusion_data;

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

extern "C"
JNIEXPORT void JNICALL
Java_com_example_thermaleyes_ImageFusion_getFusionImage(JNIEnv *env, jobject thiz,
                                                        jintArray fusionData, jintArray camData,
                                                        jintArray thermData, jint width,
                                                        jint height) {
    jint *fusion_data = (jint *)env->GetIntArrayElements(fusionData, 0);
    jint *cam_data = (jint *)env->GetIntArrayElements(camData, 0);
    jint *therm_data = (jint *)env->GetIntArrayElements(thermData, 0);

    /* 数据融合 */
    LOGE("fusion start");
    fusion_init(width, height);
    fusion_get_image((uint32_t *)fusion_data, (uint32_t *)cam_data, (uint32_t *)therm_data);
    LOGE("fusion end");

    env->ReleaseIntArrayElements(fusionData, fusion_data, 0);
    env->ReleaseIntArrayElements(camData, cam_data, 0);
    env->ReleaseIntArrayElements(thermData, therm_data, 0);
}
