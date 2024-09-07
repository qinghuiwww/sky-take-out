package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;

    /**
     * 新增菜品和对应的口味
     *
     * @param dishDTO
     */
    @Transactional //涉及到多个表的数据操作，所以需要保证数据的一致性
    public void saveWithFlavor(DishDTO dishDTO) {

        //1.向菜品表插入1条数据，由页面原型可知一次只能插入一条
        //  不需要把整个DishDTO传进去，因为DishDTO包含菜品和菜品口味数据，
        //   现在只需要插入菜品数据，所以这个地方只需要传递dish菜品实体对象即可
        //   通过对象属性拷贝，前提是2者属性保持一致
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.insert(dish);//后绪步骤实现

        //2.向口味表插入n条数据，一条、多条、没有
        //获取insert语句生成的主键值
        //注意：此时前端不能传递dishId属性，因为当前是新增菜品，此时这个菜品还没有添加完，
        //     这个dishId根本没有值。它是菜品插入玩自动生产的id，也就是口味表所关联的外键dishId。
        //解决：上面已经向菜品表插入了一条数据，这个dishId已经分配好了，所以可以在sql上
        //     通过useGeneratedKeys开启获取插入数据时生成的主键值，赋值给keyProperty
        //     指定的属性值id
        Long dishId = dish.getId();


        //口味数据通过实体类的对象集合属性封装的，所以需要先把集合中的数据取出来
        List<DishFlavor> flavors = dishDTO.getFlavors();
        //口味不是必须的有可能用户没有提交口味数据，所以需要判断一下
        if (flavors != null && flavors.size() > 0) {
            //用户确实提交的有口味数据，此时插入口味数据才有意义
            //有了菜单表这个主键值id，就需要为dishFlavor里面的每一个dishId（关联外键）属性赋值，
            //   所以在批量插入数据之前需要遍历这个对象集合，为里面的每个对象DishFlavor的dishId赋上值
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishId);
            });

            /*
             * 口味数据flavors是一个对象类型的list集合来接收的，
             * 不需要遍历这个集合一条一条的插入数据，因为sql支持批量插入
             * 直接把这个集合对象传进去，通过动态sql标签foreach进行遍历获取。
             * */
            dishFlavorMapper.insertBatch(flavors);//后绪步骤实现
        }
    }

    @Override
    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO
     * @return
     */
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        //需要在查询功能之前开启分页功能：当前页的页码   每页显示的条数
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        //这个方法有返回值为Page对象，里面保存的是分页之后的相关数据
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);//后绪步骤实现
        //封装到PageResult中:总记录数  当前页数据集合
        return new PageResult(page.getTotal(), page.getResult());
    }



    /**
     * 菜品批量删除
     *
     * @param ids
     */
    @Transactional//事务
    @Override
    public void deleteBatch(List<Long> ids) {
        //判断当前菜品是否能够删除---是否存在起售中的菜品？？
        //思路：遍历获取传入的id，根据id查询菜品dish中的status字段，0 停售 1 起售，
        //    如果是1代表是起售状态不能删除
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);//后绪步骤实现
            if (dish.getStatus().equals(StatusConstant.ENABLE)) { //常量类方式
                //当前菜品处于起售中，不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }

        //判断当前菜品是否能够删除---是否被套餐关联了？？
        //思路：菜品表 套餐表是多对多关系，它们的关系表为菜品套餐关系表setmeal_dish（菜品id 对应 套餐id）
        //     当前要删除菜品此时是知道菜品的Id的，所以可以根据这个菜品id去查询套餐id，如果能查出来说明
        //     菜品被套餐关联了，不能删除。
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealIds != null && setmealIds.size() > 0) {
            //当前菜品被套餐关联了，不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        //删除菜品表中的菜品数据
        //这个地方是在业务层循环遍历一个个删除的。
        //缺点：这个地方循环遍历删除，写了2次sql性能比较差
        //解决：动态sql-foreach，只需要一条sql就可以实现批量删除。详情查看4.5
        for (Long id : ids) {
            dishMapper.deleteById(id);//后绪步骤实现
            //删除菜品关联的口味数据
            //思路：这个地方不需要先去查一下有没有这个口味在去删除，因为不管你有还是没有
            //     我都尝试进行删除，所以这个地方不需要在去查了。
            // 根据菜品的id去删除口味表：菜品表--》口味表 一对多，菜品的id 保存在口味表当中充当外键dish_id，
            //     删除口味表的sql条件为dish_id也就是这个传入的菜品id
            dishFlavorMapper.deleteByDishId(id);//后绪步骤实现
        }


    }

}

