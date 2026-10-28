package com.resizelab.app.data

enum class ResizeMode {
    FIT,
    CROP,
    STRETCH
}

enum class ResolutionPreset(val longEdge: Int?) {
    ORIGINAL(null),
    UHD_4K(3840),
    QHD_2K(2560),
    FHD_1080(1920),
    HD_720(1280),
    SD_480(854),
    CUSTOM(null)
}

enum class ImageFormat(val mimeType: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    val isLossy: Boolean
        get() = this != PNG
}

enum class AudioQuality(val bitrate: Int?) {
    AUTO(null),
    KBPS_96(96_000),
    KBPS_128(128_000),
    KBPS_192(192_000),
    KBPS_256(256_000),
    KBPS_320(320_000);

    val kbps: Int?
        get() = bitrate?.div(1000)
}
