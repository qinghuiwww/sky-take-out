package com.sky.service.impl;

import com.sky.dto.DishDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.service.DishService;
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

}

