/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2022 aoju.org and other contributors.                      *
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
 *                                                                               *
 ********************************************************************************/
package org.aoju.bus.starter.bridge;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import org.aoju.bus.base.entity.Message;
import org.aoju.bus.core.lang.Header;
import org.aoju.bus.core.lang.MediaType;
import org.aoju.bus.core.toolkit.ObjectKit;
import org.aoju.bus.extra.json.JsonKit;
import org.aoju.bus.logger.Logger;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 服务端-配置中心
 *
 * @author Kimi Liu
 * @version 6.5.0
 * @since Java 17+
 */
public class BridgeVerticleService extends AbstractVerticle {

    private final BridgeProperties properties;
    @Autowired
    private Resolvable resolvable;
    @Autowired
    private Vertx vertx;

    public BridgeVerticleService(BridgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public void start() {
        if (this.properties.getPort() <= 0 || this.properties.getPort() > 0xFFFF) {
            return;
        }
        Router router = Router.router(vertx);
        router.route("/profile/get").handler(context -> {
            String result;
            try {
                BridgeProperties properties = JsonKit.toPojo(context.getBodyAsString(), BridgeProperties.class);
                Message message = Message.builder().data(this.resolvable.find(properties)).build();
                Logger.info("request:{},response:{}", properties, message);
                result = JsonKit.toJsonString(message);
            } catch (Exception e) {
                Logger.error("get error", e);
                result = JsonKit.toJsonString(Message.builder().errcode("-1").build());
            }
            context.response().putHeader(Header.CONTENT_TYPE, MediaType.APPLICATION_JSON).end(result);
        });

        vertx.createHttpServer().requestHandler(router).listen(this.properties.getPort());
        Logger.info("Vert.x is listening {}", this.properties.getPort());
    }

    @Override
    public void stop() {
        if (ObjectKit.isNotEmpty(this.vertx)) {
            this.vertx.close();
        }

    }

}
