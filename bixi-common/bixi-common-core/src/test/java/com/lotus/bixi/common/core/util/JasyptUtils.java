package com.lotus.bixi.common.core.util;

import com.ulisesbocchio.jasyptspringboot.encryptor.DefaultLazyEncryptor;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.core.env.StandardEnvironment;

import java.util.Scanner;

/**
 * @author bixi
 * @description 不依赖Spring容器
 * @date 2025-01-01
 */
public class JasyptUtils {

    public static void main(String[] args) {

        /**
         * 与配置文件保持一致=
         * # 配置文件加密根密码
         * jasypt:
         *  encryptor:
         *   # 密钥通过 JASYPT_ENCRYPTOR_PASSWORD 环境变量提供。
         *   password: ${JASYPT_ENCRYPTOR_PASSWORD}
         *   algorithm: PBEWithMD5AndDES
         *   iv-generator-classname: org.jasypt.iv.NoIvGenerator
         */

        String password = System.getenv("JASYPT_ENCRYPTOR_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("JASYPT_ENCRYPTOR_PASSWORD is required");
        }
        System.setProperty("jasypt.encryptor.password", password);
        System.setProperty("jasypt.encryptor.ivGeneratorClassName", "org.jasypt.iv.NoIvGenerator");
        System.setProperty("jasypt.encryptor.algorithm", "PBEWithMD5AndDES");
        StringEncryptor stringEncryptor = new DefaultLazyEncryptor(new StandardEnvironment());


        Scanner scanner = new Scanner(System.in);
        String type;

        do {
            String result = null;
            System.out.println("加密：0 解密：1，退出：9 请选择！");
            type = scanner.nextLine();

            switch (type){
                case "0":
                    System.out.println("加密字符串：");
                    result = stringEncryptor.encrypt(scanner.nextLine());
                    System.out.println("结果：" + result);
                    break;
                case "1":
                    System.out.println("解密字符串：");
                    result = stringEncryptor.decrypt(scanner.nextLine());
                    System.out.println("结果：" + result);
                    break;
                case "9":
                    System.out.println("退出！");
                    break;
                default:
                    System.out.println("输入有误，请重新输入！");
                    break;
            }

            System.out.println("\n");

        } while (!type.equals("9"));

    }
}
