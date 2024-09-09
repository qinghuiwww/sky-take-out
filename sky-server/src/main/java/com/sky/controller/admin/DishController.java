package com.sky.controller.admin;

import com.sky.annotation.AutoFill;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 菜品管理
 */
@RestController
@RequestMapping("/admin/dish")
@Api(tags="菜品相关接口")
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 新增菜品
     *
     * @param dishDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增菜品")
    public Result save(@RequestBody DishDTO dishDTO) {
        log.info("新增菜品：{}", dishDTO);
        dishService.saveWithFlavor(dishDTO);//后绪步骤开发

        //清理缓存数据：
        //注意不是一次性清除redis中的所有缓存数据，而是哪一份缓存数据受影响，那我们
        //   清理哪一份缓存数据就可以了。当前新增的这个菜品所属的分类这个key受到影响。
        String key = "dish_" + dishDTO.getCategoryId();
        cleanCache(key);

        return Result.success();
    }

    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询:{}", dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);//后绪步骤定义
        return Result.success(pageResult);
    }

    /**
     * 菜品批量删除
     *
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("菜品批量删除")
    public Result delete(@RequestParam List<Long> ids) {
        log.info("菜品批量删除：{}", ids);
        dishService.deleteBatch(ids);//后绪步骤实现

        //将所有的菜品缓存数据清理掉，所有以dish_开头的key：
        //批量删除有可能删除多个菜品，而这多个菜品可能属于同一个分类，也有可能是某些不同
        //  分类下面的菜品，也就是说可能会影响到多个key，具体影响几个key只能查询数据库才能知道，
        //  其实不需要那么复杂，只要你批量删除之后 直接把所有的缓存数据也就是dish_开头的缓存
        //  数据都清理掉就可以了。
        //注意：删除的时候不识别通配符，不能直接根据key删除，所以需要先把key查出来在进行删除（redisTemplate.keys(pattern)）
        //删除是支持集合collection的 即一次性把所有的key都删除，所以这个地方就没必要遍历set集合一个个的来删除了。
        cleanCache("dish_*");

        return Result.success();
    }

    /**
     * 根据id查询菜品
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询菜品")
    public Result<DishVO> getById(@PathVariable Long id) {
        log.info("根据id查询菜品：{}", id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);//后绪步骤实现
        return Result.success(dishVO);
    }

    /**
     * 修改菜品
     *
     * @param dishDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改菜品")
    public Result update(@RequestBody DishDTO dishDTO) {
        log.info("修改菜品：{}", dishDTO);
        dishService.updateWithFlavor(dishDTO);

        //将所有的菜品缓存数据清理掉，所有以dish_开头的key：
        //同样修改的逻辑也比较复杂：如果修的是名称价格这些普通属性，那么只需要修改一个对应的key即可，
        //                     如果修改的是分类，比如鸡蛋汤给它换一个分类，此时影响的是2个
        //                     分类中的数据，原先分类的菜品少一个，现在分类的菜品多一个。
        //              总结：修改菜品有可能是影响1份数据 也有可能影响2份数据。
        //    解决：修改操作并不是常规操作 一般是很少修改 所以没有必要吧代码写的太过复杂，去判断
        //        下有没有修改这个分类 如果修改类分类具体是那2份数据受到影响，还要一个个的去查询 太过繁琐。
        //        这个地方统一删除所有的缓存数据就可以了。
        cleanCache("dish_*");

        return Result.success();
    }

    /**
     * 菜品起售停售
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("菜品起售停售")
    public Result<String> startOrStop(@PathVariable Integer status, Long id){
        dishService.startOrStop(status,id);

        //将所有的菜品缓存数据清理掉，所有以dish_开头的key
        //   想要精确清理只清理某一个key：根据菜品的id把对应的菜品数据查询出来，菜品里面就有
        //   分类的id,之后动态的把key构造出来 然后清理某一个key就可以了。但是这样写需要
        //   额外的去查询数据 就有的得不偿失了。所以这里同样是删除所有的缓存数据。
        cleanCache("dish_*");

        return Result.success();
    }

    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<Dish>> list(Long categoryId){
        List<Dish> list = dishService.list(categoryId);
        return Result.success(list);
    }


    /**
     * 抽取清理缓存的方法
     *  只在当前类中使用，所以私有的就可以了。
     * @param pattern
     */
    private void cleanCache(String pattern){
        Set keys = redisTemplate.keys(pattern);
        redisTemplate.delete(keys);
    }


}
