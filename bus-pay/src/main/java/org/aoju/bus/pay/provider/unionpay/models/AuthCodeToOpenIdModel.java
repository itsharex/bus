package org.aoju.bus.pay.provider.unionpay.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.aoju.bus.pay.magic.Property;

/**
 * 云闪付-授权码查询 openId
 */
@Data
@Builder
@AllArgsConstructor
public class AuthCodeToOpenIdModel extends Property {

    private String service;
    private String version;
    private String charset;
    private String sign_type;
    private String mch_id;
    private String sub_appid;
    private String auth_code;
    private String nonce_str;
    private String sign;
    private String sign_agentno;
    private String groupno;

}
