package com.lotus.bixi.common.mybatis.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.mybatis.service.BaseService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 基础 Controller，封装通用的 CRUD 操作
 * <p>
 * 所有业务 Controller 应继承此类，获得基础 CRUD 能力。
 * 继承后自动获得以下能力：
 * <ul>
 *   <li>标准的 RESTful CRUD 接口（GET/POST/PUT/DELETE）</li>
 *   <li>分页查询接口</li>
 *   <li>批量操作接口</li>
 *   <li>统一的响应格式</li>
 * </ul>
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/user")
 * @RequiredArgsConstructor
 * public class SysUserController extends BaseController<SysUser> {
 *
 *     private final SysUserService userService;
 *
 *     @Override
 *     protected BaseService<SysUserMapper, SysUser> getBaseService() {
 *         return userService;
 *     }
 *
 *     // 可添加自定义的业务接口
 * }
 * }</pre>
 *
 * <p><strong>注意事项：</strong></p>
 * <ul>
 *   <li>继承后需要实现 {@link #getBaseService()} 方法返回服务实例</li>
 *   <li>如需权限控制，可在方法上添加 {@code @HasPermission} 注解</li>
 *   <li>如需操作日志，可在方法上添加 {@code @SysLog} 注解</li>
 *   <li>默认接口路径为：/{id}、/list、/page、/batch 等</li>
 * </ul>
 *
 * @param <T> 实体类型
 * @author Bixi
 * @since 0.0.3
 * @see com.lotus.bixi.common.mybatis.service.BaseService
 */
public abstract class BaseController<T> {

	/**
	 * 获取服务实例
	 * <p>
	 * 子类必须实现此方法，返回对应的服务实例。
	 * </p>
	 *
	 * @return 服务实例
	 */
	protected abstract BaseService<?, T> getBaseService();

	/**
	 * 通过 ID 查询单个实体
	 * <p>
	 * GET 请求：GET /{id}
	 * </p>
	 * <p>
	 * 示例：GET /user/1
	 * </p>
	 *
	 * @param id 实体的主键 ID，不能为空
	 * @return 返回操作结果，成功时 data 字段包含实体对象
	 * @see BaseService#getWithCache(Long)
	 */
	@GetMapping("/{id}")
	@Operation(summary = "通过ID查询", description = "根据主键 ID 查询实体详情")
	public R getById(@PathVariable Long id) {
		return R.ok(getBaseService().getById(id));
	}

	/**
	 * 查询所有实体列表
	 * <p>
	 * GET 请求：GET /list
	 * </p>
	 * <p>
	 * <strong>注意：</strong>此方法返回所有数据，对于大数据量表建议使用分页查询。
	 * </p>
	 *
	 * @return 返回操作结果，成功时 data 字段包含实体列表
	 * @see BaseService#listAll()
	 */
	@GetMapping("/list")
	@Operation(summary = "查询列表", description = "查询所有实体，不分页")
	public R list() {
		return R.ok(getBaseService().listAll());
	}

	/**
	 * 分页查询实体列表
	 * <p>
	 * GET 请求：GET /page?page=1&size=10
	 * </p>
	 * <p>
	 * 默认页码为 1，每页数量为 10。可根据实际情况调整。
	 * </p>
	 *
	 * @param page 页码，从 1 开始，默认为 1
	 * @param size 每页数量，默认为 10
	 * @return 返回操作结果，成功时 data 字段包含分页对象（包含 records、total、current、size 等字段）
	 * @see BaseService#pageEntity(IPage)
	 */
	@GetMapping("/page")
	@Operation(summary = "分页查询", description = "分页查询实体列表")
	public R page(@RequestParam(defaultValue = "1") Long page,
	              @RequestParam(defaultValue = "10") Long size) {
		IPage<T> pageable = new Page<>(page, size);
		return R.ok(getBaseService().pageEntity(pageable));
	}

	/**
	 * 新增实体
	 * <p>
	 * POST 请求：POST /
	 * </p>
	 * <p>
	 * 请求体为 JSON 格式的实体对象。
	 * </p>
	 * <p>
	 * 示例：
	 * <pre>{@code
	 * POST /user
	 * Content-Type: application/json
	 * {
	 *   "username": "test",
	 *   "password": "123456"
	 * }
	 * }</pre>
	 * </p>
	 *
	 * @param entity 要新增的实体对象，不能为空
	 * @return 返回操作结果，成功时 data 字段为 true
	 * @see BaseService#saveEntity(Object)
	 */
	@PostMapping
	@Operation(summary = "新增实体", description = "新增一个实体对象")
	public R save(@RequestBody T entity) {
		return R.ok(getBaseService().saveEntity(entity));
	}

	/**
	 * 修改实体
	 * <p>
	 * PUT 请求：PUT /
	 * </p>
	 * <p>
	 * 请求体为 JSON 格式的实体对象，必须包含主键 ID。
	 * </p>
	 * <p>
	 * 示例：
	 * <pre>{@code
	 * PUT /user
	 * Content-Type: application/json
	 * {
	 *   "id": 1,
	 *   "username": "test2"
	 * }
	 * }</pre>
	 * </p>
	 *
	 * @param entity 要修改的实体对象，不能为空，必须包含主键 ID
	 * @return 返回操作结果，成功时 data 字段为 true
	 * @see BaseService#updateEntity(Object)
	 */
	@PutMapping
	@Operation(summary = "修改实体", description = "根据主键 ID 修改实体")
	public R updateById(@RequestBody T entity) {
		return R.ok(getBaseService().updateEntity(entity));
	}

	/**
	 * 根据 ID 删除单个实体
	 * <p>
	 * DELETE 请求：DELETE /{id}
	 * </p>
	 * <p>
	 * 示例：DELETE /user/1
	 * </p>
	 * <p>
	 * <strong>注意：</strong>此操作会永久删除数据，请谨慎使用。
	 * 建议实现逻辑删除功能（软删除）而非物理删除。
	 * </p>
	 *
	 * @param id 实体的主键 ID，不能为空
	 * @return 返回操作结果，成功时 data 字段为 true
	 * @see BaseService#removeEntity(Long)
	 */
	@DeleteMapping("/{id}")
	@Operation(summary = "删除实体", description = "根据主键 ID 删除实体")
	public R removeById(@PathVariable Long id) {
		return R.ok(getBaseService().removeEntity(id));
	}

	/**
	 * 批量删除实体
	 * <p>
	 * DELETE 请求：DELETE /batch
	 * </p>
	 * <p>
	 * 请求体为 JSON 格式的主键 ID 数组。
	 * </p>
	 * <p>
	 * 示例：
	 * <pre>{@code
	 * DELETE /user/batch
	 * Content-Type: application/json
	 * [1, 2, 3]
	 * }</pre>
	 * </p>
	 *
	 * @param ids 要删除的主键 ID 列表，不能为空
	 * @return 返回操作结果，成功时 data 字段为 true
	 * @see BaseService#removeEntityBatch(List)
	 */
	@DeleteMapping("/batch")
	@Operation(summary = "批量删除", description = "根据主键 ID 列表批量删除实体")
	public R removeByIds(@RequestBody List<Long> ids) {
		return R.ok(getBaseService().removeEntityBatch(ids));
	}

}
