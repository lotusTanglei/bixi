package com.lotus.bixi.common.mybatis.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;

/**
 * 基础服务实现类，封装通用的 CRUD 操作
 * <p>
 * 提供标准的增删改查功能，支持缓存管理，所有业务服务实现类应继承此类。
 * 继承后自动获得以下能力：
 * <ul>
 *   <li>标准的 CRUD 操作（新增、查询、修改、删除）</li>
 *   <li>分页查询功能</li>
 *   <li>批量操作支持</li>
 *   <li>基于 Spring Cache 的缓存管理</li>
 * </ul>
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class UserServiceImpl extends BaseService<UserMapper, User> implements UserService {
 *     // 继承后拥有所有基础 CRUD 方法
 *     // 只需实现特定的业务方法即可
 * }
 * }</pre>
 *
 * @param <M> Mapper 接口类型，必须继承 {@link BaseMapper}
 * @param <T> 实体类型
 * @author Bixi
 * @since 0.0.3
 * @see com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper
 */
public abstract class BaseService<M extends BaseMapper<T>, T> extends ServiceImpl<M, T> {

	/**
	 * 根据 ID 查询实体（带缓存）
	 * <p>
	 * 缓存键格式：{@code entityClassName:id}
	 * 例如：User:1
	 * </p>
	 *
	 * @param id 主键 ID
	 * @return 实体对象，如果不存在返回 null
	 */
	@Cacheable(value = "entity", key = "#root.targetClass.getSimpleName() + ':' + #id")
	public T getWithCache(Long id) {
		return super.getById(id);
	}

	/**
	 * 查询所有实体（不带缓存）
	 * <p>
	 * 注意：此方法不使用缓存，直接查询数据库。
	 * 对于大数据量场景，建议使用分页查询。
	 * </p>
	 *
	 * @return 实体列表
	 */
	public List<T> listAll() {
		return super.list();
	}

	/**
	 * 查询所有实体（带缓存）
	 * <p>
	 * 缓存键格式：{@code entityClassName:all}
	 * </p>
	 *
	 * @return 实体列表
	 */
	@Cacheable(value = "entity", key = "#root.targetClass.getSimpleName() + ':all'")
	public List<T> listAllWithCache() {
		return super.list();
	}

	/**
	 * 保存单个实体（清除缓存）
	 * <p>
	 * 保存成功后会清除该实体的所有缓存
	 * </p>
	 *
	 * @param entity 实体对象
	 * @return 保存成功返回 true，失败返回 false
	 */
	@CacheEvict(value = "entity", allEntries = true)
	public boolean saveEntity(T entity) {
		return super.save(entity);
	}

	/**
	 * 批量保存实体（清除缓存）
	 * <p>
	 * 默认批次大小为 1000，可通过修改实现类调整
	 * </p>
	 *
	 * @param entityList 实体列表
	 * @return 全部保存成功返回 true，失败返回 false
	 */
	@CacheEvict(value = "entity", allEntries = true)
	public boolean saveEntityBatch(List<T> entityList) {
		return super.saveBatch(entityList, 1000);
	}

	/**
	 * 根据 ID 更新实体（清除缓存）
	 * <p>
	 * 更新成功后会清除该实体的所有缓存
	 * </p>
	 *
	 * @param entity 实体对象
	 * @return 更新成功返回 true，失败返回 false
	 */
	@CacheEvict(value = "entity", allEntries = true)
	public boolean updateEntity(T entity) {
		return super.updateById(entity);
	}

	/**
	 * 批量更新实体（清除缓存）
	 * <p>
	 * 默认批次大小为 1000
	 * </p>
	 *
	 * @param entityList 实体列表
	 * @return 全部更新成功返回 true，失败返回 false
	 */
	@CacheEvict(value = "entity", allEntries = true)
	public boolean updateEntityBatch(List<T> entityList) {
		return super.updateBatchById(entityList, 1000);
	}

	/**
	 * 根据 ID 删除实体（清除缓存）
	 * <p>
	 * 删除成功后会清除该实体的所有缓存
	 * </p>
	 *
	 * @param id 主键 ID
	 * @return 删除成功返回 true，失败返回 false
	 */
	@CacheEvict(value = "entity", allEntries = true)
	public boolean removeEntity(Long id) {
		return super.removeById(id);
	}

	/**
	 * 批量删除实体（清除缓存）
	 * <p>
	 * 删除成功后会清除该实体的所有缓存
	 * </p>
	 *
	 * @param ids 主键 ID 列表
	 * @return 全部删除成功返回 true，失败返回 false
	 */
	@CacheEvict(value = "entity", allEntries = true)
	public boolean removeEntityBatch(List<Long> ids) {
		return super.removeByIds(ids);
	}

	/**
	 * 分页查询（带缓存）
	 * <p>
	 * 缓存键格式：{@code entityClassName:page:pageNo:pageSize}
	 * 例如：User:page:1:10
	 * </p>
	 *
	 * @param page 分页对象
	 * @return 分页结果
	 */
	@Cacheable(value = "entity", key = "#root.targetClass.getSimpleName() + ':page:' + #page.current + ':' + #page.size")
	public IPage<T> pageWithCache(IPage<T> page) {
		return super.page(page);
	}

	/**
	 * 分页查询（不带缓存）
	 * <p>
	 * 当不需要缓存时使用此方法
	 * </p>
	 *
	 * @param page 分页对象
	 * @return 分页结果
	 */
	public IPage<T> pageEntity(IPage<T> page) {
		return super.page(page);
	}

}
