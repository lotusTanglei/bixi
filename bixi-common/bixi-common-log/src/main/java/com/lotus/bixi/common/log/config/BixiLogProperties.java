/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & www.dreamlu.net).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lotus.bixi.common.log.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 日志配置类
 *
 * @author 唐磊
 */
@Getter
@Setter
@ConfigurationProperties(BixiLogProperties.PREFIX)
public class BixiLogProperties {

    public static final String PREFIX = "security.log";

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "newpassword", "oldpassword", "confirmpassword", "code", "smscode", "verifycode",
            "clientsecret", "token", "accesstoken", "refreshtoken", "authorization", "mobile", "idcard", "phone");

    /**
     * 开启日志记录
     */
    private boolean enabled = true;

    /**
     * 额外不记录的字段；凭证和个人敏感字段的基础保护不可通过配置取消。
     */
    private List<String> excludeFields = new ArrayList<>();

    /**
     * 请求报文最大存储长度
     */
    private Integer maxLength = 2000;

    public boolean shouldExcludeField(String fieldName) {
        String normalized = normalizeFieldName(fieldName);
        return SENSITIVE_FIELDS.contains(normalized) || excludeFields != null && excludeFields.stream()
                .filter(Objects::nonNull)
                .map(BixiLogProperties::normalizeFieldName)
                .anyMatch(normalized::equals);
    }

    private static String normalizeFieldName(String fieldName) {
        return fieldName.replace("_", "").replace("-", "").trim().toLowerCase(Locale.ROOT);
    }

}
