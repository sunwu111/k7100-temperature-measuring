#pragma version(1)
#pragma rs java_package_name(hikvision.zhanyun.com.hikvision)
#pragma rs_fp_relaxed

void gray(const uchar4 *in, uchar4 *out, uint32_t x, uint32_t y) {
    // a 是透明度，这里不修改透明度。
    out->a = in->a;

    // 快，但并不是真正意义的去色
    // out->r = out->g = out->b = (in->r + in->g + in->b) / 3;

    // 慢，但是是真正的去色
    out->r = out->g = out->b = (in->r * 299 + in->g * 587 + in->b * 114 + 500) / 1000;
}

static float brightM = 0.f;
static float brightC = 0.f;

void setBright(float v) {
    brightM = pow(2.f, v / 100.f);
    brightC = 127.f - brightM * 127.f;
}

void contrast(const uchar4 *in, uchar4 *out)
{
#if 0
    out->r = rsClamp((int)(brightM * in->r + brightC), 0, 255);
    out->g = rsClamp((int)(brightM * in->g + brightC), 0, 255);
    out->b = rsClamp((int)(brightM * in->b + brightC), 0, 255);
#else
    float3 v = convert_float3(in->rgb) * brightM + brightC;
    out->rgb = convert_uchar3(clamp(v, 0.f, 255.f));
#endif
}