import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.generator.AutoGenerator;
import com.baomidou.mybatisplus.generator.config.*;
import com.baomidou.mybatisplus.generator.config.builder.Mapper;
import com.baomidou.mybatisplus.generator.config.builder.Service;
import com.baomidou.mybatisplus.generator.config.converts.MySqlTypeConvert;
import com.baomidou.mybatisplus.generator.config.querys.MySqlQuery;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import com.baomidou.mybatisplus.generator.keywords.MySqlKeyWordsHandler;

import java.util.Collections;
import java.util.Scanner;

public class MyBatisPlusGenerator {
    /**
     * 数据库名称
     */
    private String dbName;
    /**
     * 模块名称
     */
    private String module;
    /**
     * 父级包名称如appenv
     */
    private String parentPackage;
    /**
     * 数据库host和端口
     */
    private String dbHostAndPort;
    /**
     * 数据库用户名
     */
    private String dbUserName;
    /**
     * 数据库密码
     */
    private String dbPsw;
    /**
     * 表名称 ,string数组
     */
    private String[] tables;

    /**
     * 作者 author
     */
    private String author;

    private MyBatisPlusGenerator(String dbName,
                                 String module,
                                 String parentPackage,
                                 String dbHostAndPort,
                                 String dbUserName,
                                 String dbPsw,
                                 String tables,
                                 String author) {
        this.dbName = dbName;
        this.module = module;
        this.parentPackage = parentPackage;
        this.dbHostAndPort = dbHostAndPort;
        this.dbUserName = dbUserName;
        this.dbPsw = dbPsw;
        this.tables = tables.split(",");
        this.author = author;
    }

    /**
     * 代码生成
     */
    public void generate() {
        // 数据源配置
        String url = "jdbc:mysql://" + dbHostAndPort + "/" + dbName + "?serverTimezone=UTC&useUnicode=true&characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true";

        DataSourceConfig dsb = new DataSourceConfig.Builder(url, dbUserName, dbPsw)
                .dbQuery(new MySqlQuery())
                // 库名
                .schema(dbName)
                .typeConvert(new MySqlTypeConvert())
                .keyWordsHandler(new MySqlKeyWordsHandler()).build();

        /////////////////////////////////////////////////////////////////////////

        // 全局配置
        GlobalConfig gc = new GlobalConfig.Builder()
                // 输出目录
                .outputDir(module + "/src/main/java")
                .fileOverride()
                // 作者
                .author(author)
                // 启用swagger
                //.disableSwagger()
                .dateType(DateType.TIME_PACK)
                .commentDate("yyyy-MM-dd HH:mm:ss")
                .build();

        //////////////////////////////////////////////////////////////////////////////////

        // 包配置
        PackageConfig pc = new PackageConfig.Builder()
                .parent(parentPackage)
                // 设置实体包
                .entity("entity")
                // 服务类
                .service("service")
                // 服务类实现
                .serviceImpl("service.impl")
                .mapper("mapper")
                .xml("mapper.xml")
                .controller("controller")
                .other("other")
                // 路径信息
                .pathInfo(Collections.singletonMap(OutputFile.xml, module + "/src/main/resources/mapper/"))
                .build();

        //////////////////////////////////////////////////////////////////////////////

        // 模板配置 不配无法生成
        TemplateConfig tc = new TemplateConfig.Builder()
                .disable(TemplateType.ENTITY)
                .entity("/templates/entity.java")
                .service("/templates/service.java")
                .serviceImpl("/templates/serviceImpl.java")
                .mapper("/templates/mapper.java")
                .xml("/templates/mapper.xml")
                .controller("/templates/controller.java")
                .build();

        /////////////////////////////////////////////////////////////////////////////

        // 注入配置
        InjectionConfig ic = new InjectionConfig.Builder()
                .beforeOutputFile((tableInfo, objectMap) -> {
                    System.out.println("tableInfo: " + tableInfo.getEntityName() + " objectMap: " + objectMap.size());
                })
                // 没有自定义配置map对象
                // 没有模板文件
                .build();

        //////////////////////////////////////////////////////////////////////////////////

        // 策略配置
        StrategyConfig sc = new StrategyConfig.Builder()
                // 策略 开启大写命名
                .enableCapitalMode()
                .enableSkipView()
                // 禁用SQL过滤
                .disableSqlFilter()
                // 表名
                .addInclude(tables)
                //////////////////////////////////////////////////////////////////////
                // 配置实体策略
                .entityBuilder()
                // 父类
                //.superClass(BaseEntity.class)
                // 禁用序列化
                .disableSerialVersionUID()
                // 启用链式编程
                .enableChainModel()
                //启用Lombok
                .enableLombok()
                // 启用表字段注解
                .enableTableFieldAnnotation()
                // 乐观锁
                .versionColumnName("version")
                .versionPropertyName("version")
                // 逻辑删除
                .logicDeleteColumnName("deleted")
                .logicDeleteColumnName("deleted")
                .logicDeletePropertyName("deleted")
                // 表名驼峰
                .naming(NamingStrategy.underline_to_camel)
                // 列名驼峰命名
                .columnNaming(NamingStrategy.underline_to_camel)
                .idType(IdType.AUTO)
                // 格式化文件名称
                .formatFileName("%s")
                ///////////////////////////////////////////////////////////////////////
                // 配置controller 策略
                .controllerBuilder()
                // 设置父类，没有，不配
                //.superClass(BaseController.class)
                // RestController
                .enableRestStyle()
                .formatFileName("%sController")
                //////////////////////////////////////////////////////////////////////////////
                // 配置serveice 策略
                .serviceBuilder()
                // BaseRepository impl
                //.superServiceClass()
                //.superServiceImplClass(ServiceImpl.class)
                .formatServiceFileName("%sService")
                .formatServiceImplFileName("%sServiceImpl")
                //////////////////////////////////////////////////////////////////////////
                // 配置mapper策略
                .mapperBuilder()
                // 父类
                //.superClass(Mapper.class)
                // 启用mapper注解
                .enableMapperAnnotation()
                // 启用 BaseResultMap
                .enableBaseResultMap()
                .enableBaseColumnList()
                .formatMapperFileName("%sMapper")
                .formatXmlFileName("%sMapper")
                .build();
        // 执行生成
        new AutoGenerator(dsb)
                // 全局配置
                .global(gc)
                .packageInfo(pc)
                .injection(ic)
                .strategy(sc)
                .template(tc)
                .execute(new FreemarkerTemplateEngine());

    }

    public static class Builder {
        public Builder() {

        }
        private String dbName;
        private String module;
        private String parentPackage;
        private String dbHostAndPort;
        private String dbUserName;
        private String dbPsw;
        private String tables;

        public Builder setAuthor(String author) {
            this.author = author;
            return this;
        }

        private String author;

        /**
         * 设置数据库名称
         *
         * @param dbName 数据库名称
         * @return
         */
        public Builder setDbName(String dbName) {
            this.dbName = dbName;
            return this;
        }

        /**
         * 设置模块名称
         *
         * @param module 模块名称如 app-env
         * @return
         */
        public Builder setModule(String module) {
            this.module = module;
            return this;
        }

        /**
         * 设置父级包名称
         *
         * @param parentPackage 父级包名称 如 appenv
         * @return
         */
        public Builder setParentPackage(String parentPackage) {
            this.parentPackage = parentPackage;
            return this;
        }

        /**
         * 设置数据库host：port
         *
         * @param dbHostAndPort 数据库host:port
         * @return
         */
        public Builder setDbHostAndPort(String dbHostAndPort) {
            this.dbHostAndPort = dbHostAndPort;
            return this;
        }

        /**
         * 设置数据库用户名
         *
         * @param dbUserName 数据库用户名
         * @return
         */
        public Builder setDbUserName(String dbUserName) {
            this.dbUserName = dbUserName;
            return this;
        }

        /**
         * 设置数据库密码
         *
         * @param dbPsw 数据库密码
         * @return
         */
        public Builder setDbPsw(String dbPsw) {
            this.dbPsw = dbPsw;
            return this;
        }

        /**
         * 设置表名称
         *
         * @param tables 数据库表名称数组
         * @return
         */
        public Builder setTables(String tables) {
            this.tables = tables;
            return this;
        }

        public MyBatisPlusGenerator build() {
            return new MyBatisPlusGenerator(
                    dbName,
                    module,
                    parentPackage,
                    dbHostAndPort,
                    dbUserName,
                    dbPsw,
                    tables,
                    author);
        }
    }

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("请输入作者名:");
        String author = scanner.nextLine();

        System.out.println("请输入数据库名:");
        String dbName = scanner.nextLine();

        System.out.println("请输入文件输出目录的模块名:");
        String module = scanner.nextLine();

        System.out.println("请输入完整包路径:");
        String parentPackage = scanner.nextLine();

        System.out.println("请输入映射的表名，多个表名用英文逗号（,）分割:");
        String tables = scanner.nextLine();

        new Builder()
                .setDbName(dbName)
                .setModule(module)//没有分模块
                .setParentPackage(parentPackage)//当前项目的包名
                .setDbHostAndPort("rm-bp1c485wpa4uwapa8ho.mysql.rds.aliyuncs.com:3306")
                .setDbUserName("root")
                .setDbPsw("!QAZ4esz")
                .setTables(tables)
                .setAuthor(author)
                .build().generate();
    }

}
