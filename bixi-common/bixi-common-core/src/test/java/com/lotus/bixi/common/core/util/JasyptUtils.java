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
         *   # 指定密钥，非生产环境可以指明在配置文件中，生产环境请删除此配置项，通过启动命令添加 -Djasypt.encryptorpassword=bixi 实现。
         *   password: bixi
         *   algorithm: PBEWithMD5AndDES
         *   iv-generator-classname: org.jasypt.iv.NoIvGenerator
         */

        System.setProperty("jasypt.encryptor.password", "bixi");
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
