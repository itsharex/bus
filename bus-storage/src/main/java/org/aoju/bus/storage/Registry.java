/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2020 aoju.org and other contributors.                      *
 *                                                                               *
 * Permission is hereby granted, free of charge, to any person obtaining a copy  *
 * of this software and associated documentation files (the "Software"), to deal *
 * in the Software without restriction, including without limitation the rights  *
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell     *
 * copies of the Software, and to permit persons to whom the Software is         *
 * furnished to do so, subject to the following conditions:                      *
 *                                                                               *
 * The above copyright notice and this permission notice shall be included in    *
 * all copies or substantial portions of the Software.                           *
 *                                                                               *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR    *
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,      *
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE   *
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER        *
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, *
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN     *
 * THE SOFTWARE.                                                                 *
 ********************************************************************************/
package org.aoju.bus.storage;

/**
 * 平台类型
 *
 * @author Kimi Liu
 * @version 6.0.0
 * @since JDK 1.8+
 */
public enum Registry {

    /**
     * 阿里云 OSS
     */
    ALIYUN,
    /**
     * 百度云 BOS
     */
    BAIDU,
    /**
     * 华为云 OBS
     */
    HUAWEI,
    /**
     * 京东云 OBS
     */
    JD,
    /**
     * MINIO OSS
     */
    MINIO,
    /**
     * 七牛云 OSS
     */
    QINIU,
    /**
     * 腾讯云 COS
     */
    TENCENT,
    /**
     * 又拍云 OSS
     */
    UPYUN,

    /**
     * 本地 file
     */
    LOCAL
}
